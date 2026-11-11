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

package io.anonero.ui.home

import AnonOutlineButton
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.sparrowwallet.hummingbird.ResultType
import com.sparrowwallet.hummingbird.URDecoder
import io.anonero.AnonConfig
import io.anonero.R
import io.anonero.icons.AnonIcons
import io.anonero.model.AnonUrRegistryTypes
import io.anonero.model.CoinsInfo
import io.anonero.model.PendingTransaction
import io.anonero.model.Wallet
import io.anonero.model.WalletManager
import io.anonero.services.WalletState
import io.anonero.ui.components.WalletProgressIndicator
import io.anonero.ui.home.graph.routes.ReviewTransactionRoute
import io.anonero.ui.home.graph.routes.SendScreenRoute
import io.anonero.ui.home.spend.qr.ExportType
import io.anonero.ui.home.spend.qr.ImportEvents
import io.anonero.ui.home.spend.qr.QRExchangeScreen
import io.anonero.ui.home.spend.qr.SpendQRExchangeParam
import io.anonero.ui.home.spend.qr.URQRScanner
import io.anonero.util.Formats
import io.anonero.util.MoneroAmount
import io.anonero.util.backup.BackupHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File

private const val TAG = "Send"

enum class SpendType {
    NORMAL,
    SWEEP,
}

enum class SendError(
    @StringRes val messageRes: Int,
) {
    NOT_CONNECTED(R.string.wallet_not_connected_to_node_please_check_network),
    CONSTRUCTION_FAILED(R.string.transaction_construction_failed),
}

/**
 * Screen state and transaction construction for a single send.
 *
 * The ViewModel validates connection state, parses the amount, builds a wallet2 pending
 * transaction (including sweep and coin-control selections), and writes the unsigned
 * transaction to the cache for review/airgapped export. It does not broadcast: the review
 * screen owns the final commit/submit step.
 *
 * The amount path uses [io.anonero.util.MoneroAmount.parseExact], like pay-to-many, so
 * malformed, zero, over-precise, or overflowing input is rejected before a transaction
 * object exists. URI amounts are carried as atomic Long values, never Double.
 */
class SendViewModel : ViewModel() {
    private val walletState: WalletState by inject(WalletState::class.java)
    private val _balance = walletState.unLockedBalance.asLiveData()
    private val _paymentUri = MutableLiveData<SendScreenRoute?>(null)
    private val _coinsSelected = MutableLiveData<Set<CoinsInfo>>(emptySet())
    private val _txComposeError = MutableLiveData<SendError?>(null)
    private val _spendType = MutableLiveData(SpendType.NORMAL)
    val paymentUri = _paymentUri as LiveData<SendScreenRoute?>
    val spendType = _spendType as LiveData<SpendType>
    val coins = _coinsSelected as LiveData<Set<CoinsInfo>>
    val txComposeError = _txComposeError as LiveData<SendError?>

    private fun writeUnsignedTransaction(pendingTx: PendingTransaction) {
        val cacheDir =
            AnonConfig.context?.cacheDir
                ?: throw IllegalStateException("cache directory unavailable")
        val unsignedTxFile = File(cacheDir, AnonConfig.EXPORT_UNSIGNED_TX_FILE)
        // A fixed cache name means a failed write could leave the previous transaction
        // in place and a later QR export would sign a different payment. Clear it first
        // and fail closed if the write does not succeed.
        if (unsignedTxFile.exists() && !unsignedTxFile.delete()) {
            throw IllegalStateException("stale unsigned transaction could not be removed")
        }
        if (!pendingTx.commit(unsignedTxFile.path, true)) {
            throw IllegalStateException("unsigned transaction could not be written")
        }
    }

    suspend fun prepareTransaction(
        addressField: String,
        amount: String,
    ): PendingTransaction? {
        val wallet = WalletManager.instance?.wallet
        val isSweep = spendType.value == SpendType.SWEEP
        return withContext(Dispatchers.IO) {
            try {
                // Sweep ignores the amount; a normal send must never pass through Double or
                // the native parser, which can accept values the review screen did not show.
                val amountAtomic =
                    if (isSweep) {
                        0L
                    } else {
                        MoneroAmount.parseExact(amount)
                            ?: throw IllegalArgumentException("invalid amount")
                    }
                _txComposeError.postValue(null)
                if (isSweep && _coinsSelected.value.isNullOrEmpty()) {
                    if (wallet?.fullStatus?.connectionStatus != Wallet.ConnectionStatus.ConnectionStatus_Connected) {
                        _txComposeError.postValue(SendError.NOT_CONNECTED)
                        return@withContext null
                    }
                    val pendingTx =
                        wallet.createSweepTransaction(
                            dstAddr = addressField,
                            priority = PendingTransaction.Priority.Priority_Default,
                            keyImages = arrayListOf(),
                        )
                    if (!pendingTx?.getErrorString().isNullOrEmpty()) {
                        throw Exception(pendingTx.getErrorString())
                    }

                    pendingTx?.let { writeUnsignedTransaction(it) }
                    pendingTx
                } else {
                    if (wallet?.fullStatus?.connectionStatus != Wallet.ConnectionStatus.ConnectionStatus_Connected) {
                        _txComposeError.postValue(SendError.NOT_CONNECTED)
                        return@withContext null
                    }
                    val pendingTx =
                        wallet.createTransaction(
                            dst_addr = addressField,
                            amount = amountAtomic,
                            sweepAll = isSweep,
                            selectedUtxos = _coinsSelected.value?.toList() ?: listOf(),
                        )

                    if (!pendingTx.getErrorString().isNullOrEmpty()) {
                        Timber.tag(TAG).e("transaction error: %s", pendingTx.getErrorString())
                        _txComposeError.postValue(SendError.CONSTRUCTION_FAILED)
                        return@withContext null
                    }
                    writeUnsignedTransaction(pendingTx)
                    pendingTx
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _txComposeError.postValue(SendError.CONSTRUCTION_FAILED)
                Timber.tag(TAG).e(e)
                null
            }
        }
    }

    fun handleScan(data: String) {
        viewModelScope.launch(Dispatchers.Default) {
            SendScreenRoute.parse(data)?.let {
                _paymentUri.postValue(it)
            }
        }
    }

    fun processUrCode(
        urResult: URDecoder.Result,
        onSuccess: (path: String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (urResult.type == ResultType.SUCCESS) {
                var destinationFile: File? = null
                val ur = urResult.ur
                when (ur.type) {
                    AnonUrRegistryTypes.XMR_TX_UNSIGNED.type -> {
                        destinationFile =
                            File(AnonConfig.context?.cacheDir, AnonConfig.IMPORT_UNSIGNED_TX_FILE)
                    }

                    AnonUrRegistryTypes.XMR_TX_SIGNED.type -> {
                        destinationFile =
                            File(AnonConfig.context?.cacheDir, AnonConfig.IMPORT_SIGNED_TX_FILE)
                    }

                    AnonUrRegistryTypes.XMR_OUTPUT.type -> {
                        destinationFile =
                            File(AnonConfig.context?.cacheDir, AnonConfig.IMPORT_OUTPUT_FILE)
                    }

                    AnonUrRegistryTypes.XMR_KEY_IMAGE.type -> {
                        destinationFile =
                            File(AnonConfig.context?.cacheDir, AnonConfig.IMPORT_KEY_IMAGE_FILE)
                    }

                    else -> {
                        Timber.tag(TAG).e("Unknown UR type")
                    }
                }
                destinationFile?.writeBytes(ur.toBytes())
                if (destinationFile != null) {
                    onSuccess(destinationFile.path)
                }
            }
        }
    }

    fun setSpendType(type: SpendType) {
        _spendType.postValue(type)
    }

    // Computed balance based on selected coins or unlocked balance
    val balance =
        MediatorLiveData<Long>().apply {
            addSource(walletState.unLockedBalance.asLiveData()) { updateComputedBalance() }
            addSource(_coinsSelected) { updateComputedBalance() }
        }

    private fun MediatorLiveData<Long>.updateComputedBalance() {
        val selectedCoins = _coinsSelected.value
        val unlockedBal = _balance.value ?: 0L
        value =
            if (selectedCoins.isNullOrEmpty()) {
                unlockedBal
            } else {
                selectedCoins.sumOf { it.amount }
            }
    }

    fun setCoins(coins: List<String>) {
        val walletCoins =
            WalletManager.instance
                ?.wallet
                ?.coins
                ?.all ?: listOf()
        // Frozen outputs must never be preselected, even if a stale route or QR payload
        // names one. wallet2 also refuses them, but silently letting the UI show a
        // spendable selection would be misleading.
        val selectedCoins =
            walletCoins
                .filter {
                    coins.contains(it.pub_key) && !it.frozen && !it.spent &&
                        it.subaddrAccount == (WalletManager.instance?.wallet?.getAccountIndex() ?: 0)
                }.toSet()
        _coinsSelected.postValue(
            selectedCoins,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit = {},
    navigateToReview: (route: ReviewTransactionRoute) -> Unit = {},
    navigateToHome: () -> Unit = {},
    onPayToMany: () -> Unit = {},
    paymentUri: SendScreenRoute? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var addressField by rememberSaveable { mutableStateOf("") }
    var amountField by rememberSaveable { mutableStateOf("") }
    var validSpend by rememberSaveable { mutableStateOf(false) }
    var preparingTx by remember { mutableStateOf(false) }
    var showCoinSelection by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var qrScannerParam by remember { mutableStateOf<SpendQRExchangeParam?>(null) }
    var inValidAddress by remember { mutableStateOf<Boolean?>(null) }
    val labelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
    val scope = rememberCoroutineScope()
    val sendViewModel = koinViewModel<SendViewModel>()
    val unlockedBalance by sendViewModel.balance.observeAsState(0L)
    val paymentUriFromScanner by sendViewModel.paymentUri.observeAsState(null)
    val spendType by sendViewModel.spendType.observeAsState(SpendType.NORMAL)
    val coins by sendViewModel.coins.observeAsState(emptySet())
    val txComposeError by sendViewModel.txComposeError.observeAsState()
    val view = LocalView.current
    val walletState: WalletState by inject(WalletState::class.java)

    val showIndefiniteLoading by walletState.isLoading.asLiveData().observeAsState(false)

    val unLockedAmount =
        Formats.getDisplayAmount(
            unlockedBalance ?: 0L,
        )
    val offline by walletState.offlineFlow.asLiveData().observeAsState(walletState.offline)
    val hideAmounts by walletState.hideAmountsFlow.asLiveData().observeAsState(false)
    val shownAmount =
        if (offline || hideAmounts) {
            Formats.maskAmount(unlockedBalance ?: 0L)
        } else {
            unLockedAmount
        }

    LaunchedEffect(paymentUri) {
        if (paymentUri != null && paymentUri.coins.isNotEmpty()) {
            sendViewModel.setCoins(paymentUri.coins)
        }
    }

    LaunchedEffect(paymentUriFromScanner) {
        paymentUriFromScanner?.let {
            if (it.address.isNotEmpty()) {
                addressField = it.address
            }
            if (it.amount > 0L) {
                amountField = MoneroAmount.format(it.amount)
            }
        }
    }

    LaunchedEffect(paymentUri) {
        paymentUri?.let {
            if (it.address.isNotEmpty()) {
                addressField = it.address
            }
            if (it.amount > 0L) {
                amountField = MoneroAmount.format(it.amount)
            }
        }
    }
    val sweep = spendType == SpendType.SWEEP

    LaunchedEffect(addressField, amountField) {
        scope
            .launch {
                unlockedBalance?.let { maxSpendableBalance ->
                    val amountAtomic = MoneroAmount.parseExact(amountField)

                    validSpend =
                        when {
                            inValidAddress == true -> false
                            spendType == SpendType.SWEEP -> true
                            amountAtomic == null || maxSpendableBalance == 0L -> false
                            amountAtomic >= maxSpendableBalance -> false
                            else -> true
                        }
                }
            }.invokeOnCompletion {
                if (it != null) {
                    Timber.tag(TAG).e(it)
                }
            }
    }

    fun prepare() {
        scope.launch {
            try {
                preparingTx = true
                val pendingTx =
                    sendViewModel.prepareTransaction(addressField, amountField)
                if (pendingTx != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        view.performHapticFeedback(
                            HapticFeedbackConstants.CONFIRM,
                        )
                    }
                    if (!pendingTx.getErrorString().isNullOrEmpty()) {
                        return@launch
                    }
                    navigateToReview.invoke(ReviewTransactionRoute(addressField))
                } else {
                    Timber.tag(TAG).i("prepare: Pending tx is null")
                    // show error
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Timber.tag(TAG).e(e)
            } finally {
                preparingTx = false
            }
        }
    }

    val context = LocalContext.current
    val resources = LocalResources.current

    if (showCoinSelection) {
        ModalBottomSheet(
            scrimColor = MaterialTheme.colorScheme.background,
            contentColor = Color.Transparent,
            sheetState =
                rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                ),
            contentWindowInsets = {
                WindowInsets(
                    left = 0.dp,
                    top = 0.dp,
                    right = 0.dp,
                    bottom = 0.dp,
                )
            },
            shape = MaterialTheme.shapes.large,
            dragHandle = {
                Box(modifier = Modifier.height(0.dp))
            },
            onDismissRequest = {
                showCoinSelection = false
            },
        ) {
            CoinsScreen(
                // CoinsScreen and setCoins identify coins by pub_key; passing the key image
                // matched nothing, silently cleared the selection and could turn the send
                // into a sweep of every output
                selected = coins.map { it.pub_key }.toSet(),
                onBackPress = {
                    showCoinSelection = false
                },
                navigateToSpend = {
                    showCoinSelection = false
                    sendViewModel.setCoins(it.coins)
                },
            )
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
                        onClick = onBackPress,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = onPayToMany) {
                        Text(stringResource(R.string.multi))
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier.padding(
                        bottom = 12.dp,
                    ),
            )
        },
    ) { padding ->
        if (preparingTx) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = CenterHorizontally,
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
                    stringResource(R.string.constructing_transaction),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .padding(padding)
                        .padding(bottom = 0.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    WalletProgressIndicator()
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.address),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        },
                        supportingContent = {
                            OutlinedTextField(
                                value = addressField,
                                shape = MaterialTheme.shapes.medium,
                                minLines = 4,
                                isError = inValidAddress == true,
                                supportingText = {
                                    if (inValidAddress == true) {
                                        Text(stringResource(R.string.invalid_address))
                                    }
                                },
                                enabled = !showIndefiniteLoading,
                                onValueChange = {
                                    addressField = it.trim()
                                },
                                keyboardOptions =
                                    KeyboardOptions(
                                        imeAction = ImeAction.Next,
                                    ),
                                placeholder = {
                                    Text(
                                        text = "",
                                        color = labelColor,
                                    )
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged {
                                            if (!it.hasFocus && addressField.isNotEmpty()) {
                                                scope.launch(Dispatchers.Default) {
                                                    inValidAddress =
                                                        WalletManager.instance?.wallet?.validateAddress(
                                                            addressField,
                                                        ) != true
                                                }
                                            } else {
                                                if (addressField.isEmpty()) {
                                                    inValidAddress = null
                                                }
                                            }
                                        }.focusable(),
                            )
                        },
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.amount),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        },
                        supportingContent = {
                            OutlinedTextField(
                                value = amountField,
                                shape = MaterialTheme.shapes.medium,
                                placeholder = {
                                    Text(
                                        text = "",
                                        color = labelColor,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions =
                                    KeyboardOptions(
                                        imeAction = ImeAction.Next,
                                        keyboardType = KeyboardType.Decimal,
                                    ),
                                enabled = !showIndefiniteLoading,
                                onValueChange = {
                                    amountField = it.trim()
                                },
                            )
                        },
                    )
                    Text(
                        if (sweep) {
                            stringResource(R.string.sweeping_balance_amount, shownAmount)
                        } else {
                            stringResource(R.string.available_balance_amount, shownAmount)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !offline) {
                                    amountField =
                                        if (!sweep) {
                                            sendViewModel.setSpendType(SpendType.SWEEP)
                                            unLockedAmount
                                        } else {
                                            sendViewModel.setSpendType(SpendType.NORMAL)
                                            ""
                                        }
                                },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    IconButton(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onClick = {
                            showScanner = true
                            qrScannerParam = null
                        },
                    ) {
                        Icon(AnonIcons.Scan, contentDescription = "")
                    }

                    txComposeError?.let { sendError ->
                        Text(
                            text = stringResource(sendError.messageRes),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (coins.isNotEmpty()) {
                        Text(
                            text =
                                pluralStringResource(
                                    R.plurals.coins_selected_count,
                                    coins.size,
                                    coins.size,
                                ),
                            modifier =
                                Modifier
                                    .align(CenterHorizontally)
                                    .padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp,
                                    ).clickable {
                                        showCoinSelection = true
                                    }.padding(horizontal = 16.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xfffb8500),
                        )
                    }
                }
                AnonOutlineButton(
                    enabled = validSpend,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                            ),
                    onClick = {
                        val wallet = WalletManager.instance?.wallet ?: return@AnonOutlineButton
                        val isSweep = spendType == SpendType.SWEEP
                        val spendAmount =
                            if (isSweep) {
                                0L
                            } else {
                                MoneroAmount.parseExact(amountField) ?: return@AnonOutlineButton
                            }
                        if (AnonConfig.viewOnly) {
                            val needsKeyImages =
                                if (isSweep) {
                                    wallet.hasUnknownKeyImages()
                                } else {
                                    // Reserve a fee for the key-image check without letting a
                                    // near-Long.MAX amount wrap negative.
                                    val withFee =
                                        runCatching {
                                            Math.addExact(spendAmount, 1_000_000_000L)
                                        }.getOrDefault(Long.MAX_VALUE)
                                    withFee > wallet.viewOnlyBalanceFor(wallet.getAccountIndex())
                                }

                            if (needsKeyImages) {
                                qrScannerParam =
                                    SpendQRExchangeParam(
                                        exportType = ExportType.OUTPUT,
                                        title = resources.getString(R.string.outputs),
                                        ctaText = resources.getString(R.string.scan_key_images),
                                    )
                            } else {
                                prepare()
                            }

                            return@AnonOutlineButton
                        } else {
                            prepare()
                        }
                    },
                ) {
                    Text(stringResource(R.string.next))
                }
            }
        }
    }

    if (showScanner) {
        URQRScanner(
            onQRCodeScanned = {
                showScanner = false
                sendViewModel.handleScan(it)
            },
            onDismiss = {
                showScanner = false
            },
            onURResult = {
                when (it.getOrNull()) {
                    ImportEvents.IMPORT_OUTPUTS -> {
                        showScanner = false
                        qrScannerParam =
                            SpendQRExchangeParam(
                                exportType = ExportType.IMAGE,
                                title = resources.getString(R.string.key_images),
                                ctaText = resources.getString(R.string.scan_unsigned_tx),
                            )
                    }

                    ImportEvents.IMPORT_KEY_IMAGES -> {
                        prepare()
                    }

                    ImportEvents.IMPORT_UNSIGNED_TX -> {
                        if (!AnonConfig.viewOnly) {
                            navigateToReview.invoke(
                                ReviewTransactionRoute(
                                    addressField,
                                ),
                            )
                        }
                    }

                    ImportEvents.IMPORT_SIGNED_TX -> {
                        if (AnonConfig.viewOnly) {
                            navigateToReview.invoke(
                                ReviewTransactionRoute(
                                    addressField,
                                ),
                            )
                        }
                    }

                    null -> {
                    }
                }
                showScanner = false
            },
            showScanner = showScanner,
        )
    }

    if (qrScannerParam != null) {
        QRExchangeScreen(
            params = qrScannerParam!!,
            onBackPressed = {
                qrScannerParam = null
            },
            onNavigateToHome = {
                qrScannerParam = null
                showScanner = false
                navigateToHome.invoke()
            },
            onCtaCalled = {
                qrScannerParam = null
                showScanner = true
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(device = "id:pixel_5")
@Composable
private fun SendScreenPrev() {
    SendScreen(modifier = Modifier)
}
