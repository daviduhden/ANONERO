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
import io.anonero.AnonConfig
import io.anonero.R
import io.anonero.icons.AnonIcons
import io.anonero.model.CoinsInfo
import io.anonero.model.PendingTransaction
import io.anonero.model.Wallet
import io.anonero.model.WalletManager
import io.anonero.network.TransportController
import io.anonero.services.AnonWalletHandler
import io.anonero.services.WalletState
import io.anonero.ui.components.WalletProgressIndicator
import io.anonero.ui.home.graph.routes.ReviewTransactionRoute
import io.anonero.ui.home.graph.routes.SendScreenRoute
import io.anonero.ui.home.spend.prepareUnsignedExport
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

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
    private val walletHandler: AnonWalletHandler by inject(AnonWalletHandler::class.java)
    private val transportController: TransportController by inject(TransportController::class.java)
    private val _balance = walletState.unLockedBalance.asLiveData()
    private val _paymentUri = MutableLiveData<SendScreenRoute?>(null)
    private val _coinsSelected = MutableLiveData<Set<CoinsInfo>?>(emptySet())
    private var coinsJob: Job? = null
    private val _txComposeError = MutableLiveData<SendError?>(null)
    private val _spendType = MutableLiveData(SpendType.NORMAL)
    val paymentUri = _paymentUri as LiveData<SendScreenRoute?>
    val spendType = _spendType as LiveData<SpendType>
    val coins: LiveData<Set<CoinsInfo>?> = _coinsSelected
    val txComposeError = _txComposeError as LiveData<SendError?>

    private fun writeUnsignedTransaction(pendingTx: PendingTransaction) {
        val cacheDir =
            AnonConfig.context?.cacheDir
                ?: throw IllegalStateException("cache directory unavailable")
        check(prepareUnsignedExport(cacheDir) { pendingTx.commit(it.path, true) }) {
            "unsigned transaction could not be written"
        }
    }

    suspend fun prepareTransaction(
        wallet: Wallet,
        addressField: String,
        amount: String,
        isSweep: Boolean,
        selectedCoins: Set<CoinsInfo>,
    ): PendingTransaction? {
        if (walletState.backgroundSync || _coinsSelected.value != selectedCoins) {
            _txComposeError.postValue(SendError.CONSTRUCTION_FAILED)
            return null
        }
        val transport = transportController.config.value
        if (transport.validationError() != null) {
            _txComposeError.postValue(SendError.CONSTRUCTION_FAILED)
            return null
        }
        if (transport.mode == io.anonero.network.TransportMode.SYSTEM_VPN &&
            !transportController.isVpnActive()
        ) {
            _txComposeError.postValue(SendError.NOT_CONNECTED)
            return null
        }
        return withContext(Dispatchers.IO) {
            try {
                if (walletState.backgroundSync || WalletManager.instance?.wallet !== wallet ||
                    !wallet.validateAddress(addressField)
                ) {
                    _txComposeError.postValue(SendError.CONSTRUCTION_FAILED)
                    return@withContext null
                }
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
                walletHandler.requireAppliedTransport(wallet)
                if (isSweep && selectedCoins.isEmpty()) {
                    if (wallet.fullStatus.connectionStatus != Wallet.ConnectionStatus.ConnectionStatus_Connected) {
                        _txComposeError.postValue(SendError.NOT_CONNECTED)
                        return@withContext null
                    }
                    walletHandler.requireAppliedTransport(wallet)
                    val pendingTx =
                        wallet.createSweepTransaction(
                            dstAddr = addressField,
                            priority = PendingTransaction.Priority.Priority_Default,
                            keyImages = arrayListOf(),
                        )
                    if (pendingTx == null || !pendingTx.getErrorString().isNullOrEmpty()) {
                        _txComposeError.postValue(SendError.CONSTRUCTION_FAILED)
                        return@withContext null
                    }

                    writeUnsignedTransaction(pendingTx)
                    pendingTx
                } else {
                    if (wallet.fullStatus.connectionStatus != Wallet.ConnectionStatus.ConnectionStatus_Connected) {
                        _txComposeError.postValue(SendError.NOT_CONNECTED)
                        return@withContext null
                    }
                    walletHandler.requireAppliedTransport(wallet)
                    val pendingTx =
                        wallet.createTransaction(
                            dst_addr = addressField,
                            amount = amountAtomic,
                            sweepAll = isSweep,
                            selectedUtxos = selectedCoins.toList(),
                        )

                    if (!pendingTx.getErrorString().isNullOrEmpty()) {
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
                Timber.tag(TAG).e("send transaction construction failed")
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

    fun setSpendType(type: SpendType) {
        _spendType.value = type
    }

    // null means an explicit selection is unresolved or invalid. An empty set
    // allows the wallet to select outputs from the account's unlocked balance.
    val balance =
        MediatorLiveData<Long?>().apply {
            addSource(_balance) { updateComputedBalance() }
            addSource(_coinsSelected) { updateComputedBalance() }
        }

    private fun MediatorLiveData<Long?>.updateComputedBalance() {
        val selectedCoins = _coinsSelected.value
        val unlockedBal = _balance.value
        value =
            when {
                selectedCoins == null -> {
                    null
                }

                selectedCoins.isEmpty() -> {
                    unlockedBal
                }

                else -> {
                    try {
                        selectedCoins.fold(0L) { total, coin -> Math.addExact(total, coin.amount) }
                    } catch (_: ArithmeticException) {
                        null
                    }
                }
            }
    }

    fun setCoins(coins: List<String>) {
        coinsJob?.cancel()
        _txComposeError.value = null
        if (coins.isEmpty()) {
            _coinsSelected.value = emptySet()
            return
        }

        // An unresolved or invalid selection must not become a wallet-wide sweep.
        _coinsSelected.value = null
        val requested = coins.toSet()
        coinsJob =
            viewModelScope.launch {
                try {
                    val selected =
                        withContext(Dispatchers.IO) {
                            val wallet = WalletManager.instance?.wallet ?: return@withContext emptySet()
                            val account = wallet.getAccountIndex()
                            wallet
                                .getUtxos()
                                .filter { it.pub_key in requested && !it.frozen && !it.spent && it.subaddrAccount == account }
                                .toSet()
                        }
                    if (selected.map { it.pub_key }.toSet() == requested) {
                        _coinsSelected.value = selected
                    } else {
                        _txComposeError.value = SendError.CONSTRUCTION_FAILED
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Timber.tag(TAG).e("coin selection failed")
                    _txComposeError.value = SendError.CONSTRUCTION_FAILED
                }
            }
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
    var preparingTx by remember { mutableStateOf(false) }
    var showCoinSelection by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var qrScannerParam by remember { mutableStateOf<SpendQRExchangeParam?>(null) }
    var checkedAddress by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val labelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
    val scope = rememberCoroutineScope()
    val sendViewModel = koinViewModel<SendViewModel>()
    val unlockedBalance by sendViewModel.balance.observeAsState(0L)
    val paymentUriFromScanner by sendViewModel.paymentUri.observeAsState(null)
    val spendType by sendViewModel.spendType.observeAsState(SpendType.NORMAL)
    val coinSelection by sendViewModel.coins.observeAsState(emptySet())
    val coins = coinSelection.orEmpty()
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

    LaunchedEffect(addressField) {
        val address = addressField
        if (address.isBlank()) {
            checkedAddress = null
        } else {
            val valid =
                withContext(Dispatchers.IO) {
                    try {
                        WalletManager.instance?.wallet?.validateAddress(address) == true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        Timber.tag(TAG).e("send address validation failed")
                        false
                    }
                }
            checkedAddress = address to valid
        }
    }

    // A result can still belong to the previous edit while validation is running.
    val addressValid = checkedAddress?.takeIf { it.first == addressField }?.second
    val amountAtomic = MoneroAmount.parseExact(amountField)
    val availableBalance = unlockedBalance
    val validSpend =
        addressValid == true && coinSelection != null && availableBalance != null && !showIndefiniteLoading &&
            (sweep || (amountAtomic != null && amountAtomic < availableBalance))
    val resources = LocalResources.current

    fun prepare(checkKeyImages: Boolean = false) {
        if (!validSpend || preparingTx) return
        val address = addressField
        val amount = amountField
        val isSweep = sweep
        val selectedCoins = coinSelection ?: return
        val spendAmount = if (isSweep) 0L else amountAtomic ?: return
        val wallet = WalletManager.instance?.wallet ?: return
        preparingTx = true
        scope.launch {
            try {
                if (checkKeyImages) {
                    val needsKeyImages =
                        withContext(Dispatchers.IO) {
                            if (isSweep) {
                                wallet.hasUnknownKeyImages()
                            } else {
                                // Reserve 0.001 XMR for this key-image preflight.
                                // The pending transaction supplies the actual fee.
                                val withFee =
                                    runCatching {
                                        Math.addExact(spendAmount, 1_000_000_000L)
                                    }.getOrDefault(Long.MAX_VALUE)
                                withFee > wallet.viewOnlyBalanceFor(wallet.getAccountIndex())
                            }
                        }
                    if (WalletManager.instance?.wallet !== wallet) return@launch
                    if (needsKeyImages) {
                        qrScannerParam =
                            SpendQRExchangeParam(
                                exportType = ExportType.OUTPUT,
                                title = resources.getString(R.string.outputs),
                                ctaText = resources.getString(R.string.scan_key_images),
                            )
                        return@launch
                    }
                }

                val pendingTx = sendViewModel.prepareTransaction(wallet, address, amount, isSweep, selectedCoins)
                if (pendingTx != null && WalletManager.instance?.wallet === wallet) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    navigateToReview.invoke(ReviewTransactionRoute(address))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Timber.tag(TAG).e("send preparation failed")
            } finally {
                preparingTx = false
            }
        }
    }

    val context = LocalContext.current

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
                                isError = addressValid == false,
                                supportingText = {
                                    if (addressValid == false) {
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
                                modifier = Modifier.fillMaxWidth(),
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
                    onClick = { prepare(checkKeyImages = AnonConfig.viewOnly) },
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
