/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2022-2026, r4v3r23
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.anonero.ui.home.spend

import AnonNeroTheme
import AnonOutlineButton
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.anonero.AnonConfig
import io.anonero.R
import io.anonero.model.PendingTransaction
import io.anonero.model.SigningSummary
import io.anonero.model.UnsignedTransaction
import io.anonero.model.WalletManager
import io.anonero.ui.home.graph.routes.ReviewTransactionRoute
import io.anonero.ui.home.spend.qr.ExportType
import io.anonero.ui.home.spend.qr.ImportEvents
import io.anonero.ui.home.spend.qr.QRExchangeScreen
import io.anonero.ui.home.spend.qr.SpendQRExchangeParam
import io.anonero.ui.home.spend.qr.URQRScanner
import io.anonero.ui.theme.DangerColor
import io.anonero.ui.theme.SuccessColor
import io.anonero.util.Formats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

enum class BroadcastState {
    STAGING,
    IN_PROGRESS,
    SUCCESS,
    ERROR,
}

data class ReviewModel(
    val address: String?,
    val amount: Long,
    val fee: Long,
    val total: Long,
    val txId: String,
    // Rows parsed from wallet2's confirmation sentence; imported unsigned sets only.
    val summary: SigningSummary? = null,
    // The raw sentence, shown only when it could not be parsed into rows.
    val confirmationMessage: String? = null,
)

private const val TAG = "ReviewTransaction"

/**
 * Review and broadcast state for a pending or imported transaction.
 *
 * For a pending transaction, amount/fee come from wallet2 and the destination list comes
 * from the navigation route. For an imported unsigned transaction, rows come from
 * [UnsignedTransaction.confirmationMessage] parsed by [SigningSummary]; the advisory
 * scalar getters are not a review source. If the summary cannot be parsed, the raw sentence
 * is shown instead.
 *
 * Broadcasting is deliberately separate from construction. A successful commit/submit must
 * be reflected as [BroadcastState.SUCCESS] only after wallet2 returns success; callers must
 * not navigate back to a prefilled send form after a broadcast.
 */
class ReviewTransactionViewModel : ViewModel() {
    private val broadcastInProgress =
        java.util.concurrent.atomic
            .AtomicBoolean(false)
    private val reviewModel = MutableLiveData<ReviewModel?>(null)
    private val broadcastingTx = MutableLiveData(BroadcastState.STAGING)
    private var _broadcastError: Exception? = null

    val broadCastError get() = _broadcastError
    val getBroadcastingTxState get() = broadcastingTx
    val reviewModelLive: LiveData<ReviewModel?> get() = reviewModel

    var pendingTransaction: PendingTransaction? = null
    var unsignedTransaction: UnsignedTransaction? = null

    public fun isUnsignedTransaction(): Boolean = unsignedTransaction != null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val wallet = WalletManager.instance?.wallet
            val pendingTransaction = wallet?.getPendingTx()
            val unsignedTransaction = wallet?.getUnsginedTx()

            if (unsignedTransaction != null) {
                this@ReviewTransactionViewModel.unsignedTransaction = unsignedTransaction
                // Rows come from wallet2's own summary of the outputs sign() will construct,
                // never from the set's advisory destination list. If the sentence cannot be
                // parsed it is shown raw; the advisory getters are not a fallback.
                val message = unsignedTransaction.confirmationMessage
                val summary = SigningSummary.parse(message)
                reviewModel.postValue(
                    ReviewModel(
                        address = summary?.destinations?.firstOrNull()?.address ?: "",
                        amount = summary?.amount ?: 0L,
                        fee = summary?.fee ?: 0L,
                        total = summary?.let { it.amount + it.fee } ?: 0L,
                        txId = unsignedTransaction.firstTxId,
                        summary = summary,
                        confirmationMessage = if (summary == null) message else null,
                    ),
                )
            } else if (pendingTransaction != null) {
                val txId = pendingTransaction.getFirstTxIdJ()
                // A multi-destination set can be valid before wallet2 assigns the first txid;
                // the amount/fee are still authoritative, and the reviewed destinations come
                // from the navigation route. Do not blank the review screen in that case.
                if (txId != null || pendingTransaction.getAmount() > 0L) {
                    this@ReviewTransactionViewModel.pendingTransaction = pendingTransaction
                    reviewModel.postValue(
                        ReviewModel(
                            address = null,
                            amount = pendingTransaction.getAmount(),
                            fee = pendingTransaction.getFee(),
                            total = pendingTransaction.getFee() + pendingTransaction.getAmount(),
                            txId = txId ?: "",
                        ),
                    )
                } else {
                    Timber.tag(TAG).e("Pending transaction has no txId")
                }
            }
        }
    }

    fun broadCast(): Job? {
        if (broadcastingTx.value == BroadcastState.SUCCESS ||
            broadcastingTx.value == BroadcastState.IN_PROGRESS
        ) {
            return null
        }
        // LiveData.postValue is asynchronous; an AtomicBoolean is the authoritative guard
        // against a double tap broadcasting the same pending transaction twice.
        if (!broadcastInProgress.compareAndSet(false, true)) {
            return null
        }
        val signedTxFile =
            File(
                AnonConfig.context?.cacheDir,
                AnonConfig.IMPORT_SIGNED_TX_FILE,
            )
        _broadcastError = null
        broadcastingTx.postValue(BroadcastState.IN_PROGRESS)

        return viewModelScope.launch(Dispatchers.IO) {
            var submitted = false
            try {
                val wallet = WalletManager.instance?.wallet
                if (wallet == null) {
                    throw Exception("Wallet is unavailable")
                }
                if (!AnonConfig.viewOnly) {
                    val tx =
                        pendingTransaction
                            ?: throw IllegalStateException("No pending transaction to broadcast")
                    if (!wallet.send(tx)) {
                        throw Exception("Failed to send transaction")
                    }
                    submitted = true
                } else {
                    if (!signedTxFile.exists()) {
                        throw Exception("Signed transaction file not found")
                    }
                    // LIMITATION: wallet2 exposes no parser for the signed file here, so
                    // this path cannot yet compare its txids/outputs with the unsigned
                    // object the user reviewed. A compromised external signer can return a
                    // different transaction. Do not treat this as verified fund safety
                    // until the native signed-file binding API exists and is tested.
                    val error = wallet.submitTransaction(signedTxFile.absolutePath)
                    if (error != "Transaction submitted!") {
                        throw Exception(error)
                    }
                    signedTxFile.delete()
                    submitted = true
                    Timber.tag(TAG).d("Transaction broadcasted...")
                }
                // The transaction is already broadcast at this point. A later refresh/store
                // failure must not turn the UI into an error state that invites a retry and a
                // second payment.
                broadcastingTx.postValue(BroadcastState.SUCCESS)
                try {
                    wallet.startRefresh()
                    wallet.refreshHistory()
                    wallet.store()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "post-broadcast refresh failed")
                }
            } catch (cancelled: CancellationException) {
                // If nothing was submitted, do not leave the screen stuck in IN_PROGRESS:
                // STAGING is the only state that allows a deliberate retry.
                if (!submitted) {
                    broadcastingTx.postValue(BroadcastState.STAGING)
                }
                throw cancelled
            } catch (ex: Exception) {
                _broadcastError = ex
                broadcastingTx.postValue(BroadcastState.ERROR)
            } finally {
                broadcastInProgress.set(false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewTransactionScreen(
    reviewParams: ReviewTransactionRoute,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
    navigateToHome: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    var qrScannerParam by remember { mutableStateOf<SpendQRExchangeParam?>(null) }
    val viewModel = viewModel<ReviewTransactionViewModel>()
    val reviewModel by viewModel.reviewModelLive.observeAsState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val broadcastState by viewModel.getBroadcastingTxState.observeAsState(BroadcastState.STAGING)
    var showScanner by remember { mutableStateOf(false) }
    var signing by remember { mutableStateOf(false) }
    var readyToBroadcast by remember { mutableStateOf(!AnonConfig.viewOnly) }

    val titleStyle =
        MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
        )
    val subTitleStyle =
        MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
        )

    // After a broadcast, back must leave the flow: popping to Send showed the filled-in
    // form again, one tap from sending the same payment twice. Mid-broadcast it waits.
    val leave = {
        when (broadcastState) {
            BroadcastState.SUCCESS -> onFinished()
            BroadcastState.IN_PROGRESS -> Unit
            else -> onBackPressed()
        }
    }
    BackHandler { leave() }

    if (qrScannerParam != null) {
        QRExchangeScreen(
            params = qrScannerParam!!,
            onBackPressed = {
                qrScannerParam = null
            },
            onNavigateToHome = {
                navigateToHome.invoke()
            },
            onCtaCalled = {
                val wasSignedTx = qrScannerParam?.exportType == ExportType.SIGNED_TX
                qrScannerParam = null
                showScanner = true
                if (wasSignedTx) {
                    navigateToHome.invoke()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showScanner) {
        URQRScanner(
            onQRCodeScanned = {
            },
            onDismiss = {
                showScanner = false
            },
            onURResult = {
                when (it.getOrNull()) {
                    ImportEvents.IMPORT_OUTPUTS -> {
                    }

                    ImportEvents.IMPORT_KEY_IMAGES -> {
                    }

                    ImportEvents.IMPORT_UNSIGNED_TX -> {
                    }

                    ImportEvents.IMPORT_SIGNED_TX -> {
                        qrScannerParam = null
                        readyToBroadcast = true
                    }

                    null -> {
                    }
                }
                showScanner = false
            },
            showScanner = showScanner,
        )
    }

    val context = LocalContext.current
    val resources = LocalResources.current
    val ctaText =
        if (AnonConfig.viewOnly) {
            if (!viewModel.isUnsignedTransaction() && !readyToBroadcast) {
                stringResource(R.string.confirm).replaceFirstChar { it.titlecase() }
            } else {
                stringResource(R.string.broadcast_tx)
            }
        } else {
            if (viewModel.isUnsignedTransaction()) {
                stringResource(R.string.confirm).replaceFirstChar { it.titlecase() }
            } else {
                stringResource(R.string.broadcast_tx)
            }
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text("")
                },
                navigationIcon = {
                    IconButton(
                        onClick = { leave() },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                },
            )
        },
    ) {
        Box(
            modifier =
                Modifier
                    .padding(it)
                    .padding(bottom = 0.dp),
        ) {
            if (reviewModel != null) {
                AnimatedVisibility(
                    visible = broadcastState == BroadcastState.ERROR,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.broadcast_failed_generic),
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    color = DangerColor,
                                ),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = broadcastState == BroadcastState.SUCCESS,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = SuccessColor,
                            modifier = Modifier.size(44.dp),
                        )
                        Text(stringResource(R.string.success))
                    }
                }
                AnimatedVisibility(
                    visible = broadcastState == BroadcastState.IN_PROGRESS,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_anon),
                                contentDescription = stringResource(R.string.app_logo),
                                modifier =
                                    Modifier
                                        .size(110.dp),
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.size(300.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(
                            stringResource(R.string.broadcast_in_progress),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = broadcastState == BroadcastState.STAGING,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                    ) {
                        LazyColumn {
                            val summary = reviewModel!!.summary
                            val rawSummary = reviewModel!!.confirmationMessage
                            if (rawSummary != null) {
                                // wallet2's sentence could not be parsed into rows; show it as-is and
                                // nothing else -- the advisory getters are never a fallback
                                item {
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.what_will_be_signed), style = titleStyle) },
                                        supportingContent = { Text(rawSummary, style = subTitleStyle) },
                                    )
                                }
                            } else {
                                val destinations = summary?.destinations
                                if (reviewParams.addresses.isNotEmpty()) {
                                    // Multi-destination send: the reviewed values are the
                                    // validated destinations and exact atomic amounts, not
                                    // a re-derived float representation.
                                    reviewParams.addresses.forEachIndexed { index, address ->
                                        item {
                                            ListItem(
                                                headlineContent = {
                                                    Text(
                                                        stringResource(
                                                            R.string.address_index_of_count,
                                                            index + 1,
                                                            reviewParams.addresses.size,
                                                        ),
                                                        style = titleStyle,
                                                    )
                                                },
                                                supportingContent = {
                                                    Column {
                                                        Text(address, style = subTitleStyle)
                                                        Text(
                                                            Formats.getExactDisplayAmount(
                                                                reviewParams.amounts.getOrElse(index) { 0L },
                                                            ),
                                                            style = subTitleStyle,
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                } else if (destinations != null) {
                                    // every output wallet2 will sign, from its own summary
                                    destinations.forEachIndexed { index, destination ->
                                        item {
                                            ListItem(
                                                headlineContent = {
                                                    Text(
                                                        if (destinations.size == 1) {
                                                            stringResource(R.string.address)
                                                        } else {
                                                            "${stringResource(R.string.address)} ${index + 1} of ${destinations.size}"
                                                        },
                                                        style = titleStyle,
                                                    )
                                                },
                                                supportingContent = {
                                                    Text(
                                                        if (destinations.size == 1) {
                                                            destination.address
                                                        } else {
                                                            "${destination.address}\n${Formats.getExactDisplayAmount(destination.amount)}"
                                                        },
                                                        style = subTitleStyle,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                } else {
                                    item {
                                        ListItem(
                                            headlineContent = { Text(stringResource(R.string.address), style = titleStyle) },
                                            supportingContent = {
                                                Text(
                                                    reviewModel!!.address ?: reviewParams.toAddress,
                                                    style = subTitleStyle,
                                                )
                                            },
                                        )
                                    }
                                }
                                item {
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.amount), style = titleStyle) },
                                        supportingContent = {
                                            Text(
                                                Formats.getExactDisplayAmount(
                                                    reviewModel!!.amount,
                                                ),
                                                style = subTitleStyle,
                                            )
                                        },
                                    )
                                }
                                item {
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.fee), style = titleStyle) },
                                        supportingContent = {
                                            Text(
                                                Formats.getExactDisplayAmount(
                                                    reviewModel!!.fee,
                                                ),
                                                style = subTitleStyle,
                                            )
                                        },
                                    )
                                }
                                item {
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.total), style = titleStyle) },
                                        supportingContent = {
                                            Text(
                                                Formats.getExactDisplayAmount(
                                                    reviewModel!!.total,
                                                ),
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.titleLarge,
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            vertical = 24.dp,
                                                        ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        AnonOutlineButton(
                            onClick = {
                                if (viewModel.isUnsignedTransaction()) {
                                    if (signing) return@AnonOutlineButton
                                    signing = true
                                    scope
                                        .launch {
                                            val signedTransaction =
                                                File(
                                                    AnonConfig.context?.cacheDir,
                                                    AnonConfig.EXPORT_SIGNED_TX_FILE,
                                                )
                                            Timber
                                                .tag(TAG)
                                                .i("Signing reviewed transaction set to ${signedTransaction.path}")
                                            // sign the object that was reviewed, not a re-read of the
                                            // file; ring signatures are CPU-heavy, so off the main thread
                                            val tx = viewModel.unsignedTransaction
                                            val signed =
                                                try {
                                                    withContext(Dispatchers.Default) {
                                                        val ok = tx?.sign(signedTransaction.absolutePath) == true
                                                        // save the wallet after signing
                                                        if (ok) WalletManager.instance?.wallet?.store()
                                                        ok
                                                    }
                                                } finally {
                                                    // always release the button, or a failed sign leaves
                                                    // it disabled with no way to retry
                                                    signing = false
                                                }
                                            if (signed) {
                                                readyToBroadcast = true
                                                Timber
                                                    .tag(TAG)
                                                    .i(
                                                        "Signed transaction written: exists ? ${signedTransaction.exists()} | " +
                                                            " size: ${signedTransaction.length()}",
                                                    )
                                                qrScannerParam =
                                                    SpendQRExchangeParam(
                                                        exportType = ExportType.SIGNED_TX,
                                                        title = resources.getString(R.string.signed_tx),
                                                        ctaText = resources.getString(R.string.finish),
                                                    )
                                            } else {
                                                readyToBroadcast = false
                                                val reason =
                                                    tx?.errorString?.takeIf { it.isNotBlank() }
                                                        ?: "unknown error"
                                                Timber.tag(TAG).e("Signing failed: %s", reason)
                                                // tell the user how to resync outputs
                                                val hint =
                                                    if (reason.contains("omit more outputs")) {
                                                        "\n" + resources.getString(R.string.signing_hint_omit_outputs)
                                                    } else {
                                                        ""
                                                    }
                                                android.widget.Toast
                                                    .makeText(
                                                        context,
                                                        resources.getString(R.string.signing_failed) + hint,
                                                        android.widget.Toast.LENGTH_LONG,
                                                    ).show()
                                            }
                                        }.invokeOnCompletion { error ->
                                            if (error != null) {
                                                Timber
                                                    .tag(TAG)
                                                    .i("Signing Unsigned Transaction failed: $error")
                                            }
                                        }
                                } else {
                                    if (AnonConfig.viewOnly && !readyToBroadcast) {
                                        qrScannerParam =
                                            SpendQRExchangeParam(
                                                exportType = ExportType.UN_SIGNED_TX,
                                                title = resources.getString(R.string.unsigned_tx),
                                                ctaText = resources.getString(R.string.scan_signed_tx),
                                            )
                                    } else {
                                        viewModel.broadCast()?.invokeOnCompletion { error ->
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                if (error == null) {
                                                    view.performHapticFeedback(
                                                        HapticFeedbackConstants.CONFIRM,
                                                    )
                                                } else {
                                                    view.performHapticFeedback(
                                                        HapticFeedbackConstants.REJECT,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                        ) {
                            if (signing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(ctaText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ReviewTransactionScreenPreview() {
    AnonNeroTheme {
        ReviewTransactionScreen(
            ReviewTransactionRoute("address"),
        )
    }
}
