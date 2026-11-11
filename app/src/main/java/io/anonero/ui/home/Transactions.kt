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

import AnonNeroTheme
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dokar.sonner.ToastType
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.anonero.AnonConfig
import io.anonero.R
import io.anonero.icons.AnonIcons
import io.anonero.model.TransactionInfo
import io.anonero.model.WalletManager
import io.anonero.services.TorService
import io.anonero.services.WalletState
import io.anonero.ui.components.WalletProgressIndicator
import io.anonero.ui.home.graph.routes.AccountsRoute
import io.anonero.ui.home.graph.routes.CoinsScreenRoute
import io.anonero.ui.home.graph.routes.PayToManyRoute
import io.anonero.ui.home.graph.routes.ReviewTransactionRoute
import io.anonero.ui.home.graph.routes.SendScreenRoute
import io.anonero.ui.home.graph.routes.SettingsNodeRoute
import io.anonero.ui.home.graph.routes.SignVerifyRoute
import io.anonero.ui.home.graph.routes.TransactionsRoute
import io.anonero.ui.home.spend.qr.ExportType
import io.anonero.ui.home.spend.qr.ImportEvents
import io.anonero.ui.home.spend.qr.QRExchangeScreen
import io.anonero.ui.home.spend.qr.SpendQRExchangeParam
import io.anonero.ui.home.spend.qr.URQRScanner
import io.anonero.util.Formats
import io.anonero.util.StealthMode
import io.anonero.util.WALLET_PREFERENCES
import io.anonero.util.WALLET_USE_TOR
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class TransactionsViewModel : ViewModel() {
    private val walletState: WalletState by inject(WalletState::class.java)

    val balance =
        walletState.balanceInfo
            .map {
                it ?: 0L
            }.asLiveData()

    val transactions =
        walletState.transactions
            .asLiveData()
}

private const val TAG = "Transactions"

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun TransactionScreen(
    modifier: Modifier = Modifier,
    onItemClick: (TransactionInfo) -> Unit = {},
    navigateTo: (route: Any) -> Unit = {},
    navigateToShortCut: (shortcut: LockScreenShortCut) -> Unit = {},
    animatedContentScope: AnimatedContentScope,
    sharedTransitionScope: SharedTransitionScope,
) {
    val transactionsViewModel = viewModel<TransactionsViewModel>()
    val balance by transactionsViewModel.balance.observeAsState()
    val transactions by transactionsViewModel.transactions.observeAsState(listOf())
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior { true }
    var showExitDialog by remember { mutableStateOf(false) }
    var scanFailure by remember { mutableStateOf<String?>(null) }
    var spendDialog by remember { mutableStateOf<String?>(null) }
    var broadcastSignedTxPath by remember { mutableStateOf<String?>(null) }
    var broadcastProgree by remember { mutableStateOf(false) }
    var settingBSync by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var qrScannerParam by remember { mutableStateOf<SpendQRExchangeParam?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val walletState = koinInject<WalletState>()
    val torService = koinInject<TorService>()
    val anonPrefs = koinInject<android.content.SharedPreferences>(named(WALLET_PREFERENCES))
    val showLockScreen by walletState.backgroundSyncFlow.asLiveData().observeAsState(walletState.backgroundSync)
    val streetMode by walletState.hideAmountsFlow.asLiveData().observeAsState(false)
    val offline by walletState.offlineFlow.asLiveData().observeAsState(walletState.offline)
    // the offline signer cannot know its balance
    val hideAmounts = streetMode || offline
    val scope = rememberCoroutineScope()
    val toastState = rememberToasterState()
    val activity = LocalActivity.current
    val context = LocalContext.current
    val resources = LocalResources.current

    if (broadcastSignedTxPath != null) {
        AlertDialog(
            modifier =
                Modifier
                    .border(
                        1.dp,
                        color =
                            MaterialTheme.colorScheme.onBackground.copy(
                                alpha = .2f,
                            ),
                        shape = MaterialTheme.shapes.medium,
                    ),
            containerColor = MaterialTheme.colorScheme.background,
            properties =
                DialogProperties(
                    securePolicy = SecureFlagPolicy.SecureOn,
                    dismissOnBackPress = false,
                ),
            title = {
                Text(
                    text = stringResource(R.string.app_name),
                    textAlign = TextAlign.Center,
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontSize = 18.sp,
                        ),
                )
            },
            text = {
                Text(stringResource(R.string.broadcast_question))
            },
            onDismissRequest = {
                scanFailure = null
                broadcastSignedTxPath = null
            },
            dismissButton = {
                Button(
                    onClick = {
                        broadcastSignedTxPath = null
                    },
                    shape = MaterialTheme.shapes.small,
                    border =
                        BorderStroke(
                            1.dp,
                            color = MaterialTheme.colorScheme.onSecondary,
                        ),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                ) {
                    Text(
                        stringResource(R.string.cancel),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                color =
                                    MaterialTheme.colorScheme.onBackground.copy(
                                        alpha = 0.8f,
                                    ),
                            ),
                    )
                }
            },
            confirmButton = {
                Button(
                    shape = MaterialTheme.shapes.small,
                    border =
                        BorderStroke(
                            1.dp,
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    onClick = {
                        if (!broadcastProgree) {
                            val path = broadcastSignedTxPath
                            if (path != null) {
                                broadcastProgree = true
                                scope.launch(Dispatchers.IO) {
                                    var success = false
                                    try {
                                        val wallet =
                                            WalletManager.instance?.wallet
                                                ?: throw IllegalStateException("wallet unavailable")
                                        val error = wallet.submitTransaction(path)
                                        if (error != "Transaction submitted!") {
                                            throw IllegalStateException(error ?: "broadcast failed")
                                        }
                                        wallet.refreshHistory()
                                        wallet.store()
                                        success = true
                                    } catch (ex: CancellationException) {
                                        throw ex
                                    } catch (ex: Exception) {
                                        Timber.tag(TAG).e(ex, "signed transaction broadcast failed")
                                    } finally {
                                        withContext(NonCancellable + Dispatchers.Main) {
                                            broadcastProgree = false
                                            broadcastSignedTxPath = null
                                            if (!success) {
                                                toastState.show(
                                                    resources.getString(R.string.broadcast_failed_generic),
                                                    type = ToastType.Warning,
                                                    duration = 6.seconds,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                ) { Text(stringResource(R.string.yes)) }
            },
        )
    }
    if (showExitDialog) {
        AlertDialog(
            modifier =
                Modifier
                    .border(
                        1.dp,
                        color =
                            MaterialTheme.colorScheme.onBackground.copy(
                                alpha = .2f,
                            ),
                        shape = MaterialTheme.shapes.medium,
                    ).fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.background,
            properties =
                DialogProperties(
                    securePolicy = SecureFlagPolicy.SecureOn,
                    dismissOnBackPress = false,
                ),
            title = {
                Text(
                    text = stringResource(R.string.app_name),
                    textAlign = TextAlign.Center,
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontSize = 18.sp,
                        ),
                )
            },
            text = {
                Text(stringResource(R.string.exit_question, stringResource(R.string.app_name)))
            },
            onDismissRequest = {
                showExitDialog = false
            },
            dismissButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                    },
                    shape = MaterialTheme.shapes.small,
                    border =
                        BorderStroke(
                            1.dp,
                            color = MaterialTheme.colorScheme.onSecondary,
                        ),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                ) {
                    Text(
                        stringResource(R.string.cancel),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                color =
                                    MaterialTheme.colorScheme.onBackground.copy(
                                        alpha = 0.8f,
                                    ),
                            ),
                    )
                }
            },
            confirmButton = {
                Button(
                    shape = MaterialTheme.shapes.small,
                    border =
                        BorderStroke(
                            1.dp,
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    onClick = {
                        activity?.finishAffinity()
                    },
                ) { Text(stringResource(R.string.exit)) }
            },
        )
    }

    if (qrScannerParam != null) {
        QRExchangeScreen(
            params = qrScannerParam!!,
            onNavigateToHome = {
                navigateTo.invoke(TransactionsRoute)
            },
            onBackPressed = {
                qrScannerParam = null
            },
            onCtaCalled = {
                qrScannerParam = null
                showScanner = true
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showScanner) {
        URQRScanner(
            onQRCodeScanned = {
                if (it.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        SendScreenRoute.parse(it)?.let { paymentUri ->
                            withContext(Dispatchers.Main) {
                                navigateTo(paymentUri)
                            }
                        }
                    }
                }
                showScanner = false
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

                    null -> {
                        it.exceptionOrNull()?.message?.let { error ->
                            toastState.show(error, type = ToastType.Warning, duration = 6.seconds)
                        }
                    }

                    ImportEvents.IMPORT_KEY_IMAGES -> {
                        toastState.show(
                            resources.getString(R.string.key_images_imported),
                            type = ToastType.Success,
                            duration = 4.seconds,
                        )
                    }

                    ImportEvents.IMPORT_UNSIGNED_TX -> {
                        if (!AnonConfig.viewOnly) {
                            navigateTo.invoke(
                                ReviewTransactionRoute(
                                    "",
                                ),
                            )
                        }
                    }

                    ImportEvents.IMPORT_SIGNED_TX -> {}
                }
                showScanner = false
            },
            showScanner = showScanner,
        )
    }

    if (spendDialog != null) {
        AlertDialog(
            modifier =
                Modifier
                    .border(
                        1.dp,
                        color =
                            MaterialTheme.colorScheme.onBackground.copy(
                                alpha = .2f,
                            ),
                        shape = MaterialTheme.shapes.medium,
                    ),
            containerColor = MaterialTheme.colorScheme.background,
            properties =
                DialogProperties(
                    securePolicy = SecureFlagPolicy.SecureOn,
                    dismissOnBackPress = false,
                ),
            title = {
                Text(
                    text = stringResource(R.string.app_name),
                    textAlign = TextAlign.Center,
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontSize = 18.sp,
                        ),
                )
            },
            text = {
                Text("$spendDialog")
            },
            onDismissRequest = {
                spendDialog = null
            },
            dismissButton = {
                Button(
                    onClick = {
                        spendDialog = null
                    },
                    shape = MaterialTheme.shapes.small,
                    border =
                        BorderStroke(
                            1.dp,
                            color = MaterialTheme.colorScheme.onSecondary,
                        ),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                ) {
                    Text(
                        stringResource(R.string.ok),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                color =
                                    MaterialTheme.colorScheme.onBackground.copy(
                                        alpha = 0.8f,
                                    ),
                            ),
                    )
                }
            },
            confirmButton = {
            },
        )
    }
    val showIndefiniteLoading by walletState.isLoading.asLiveData().observeAsState(false)
    val refreshState = rememberPullToRefreshState()
    val view = LocalView.current
    val torSocket by torService.socksFlow.asLiveData().observeAsState(torService.socks)
    val torConnected = torSocket != null
    val useTor = anonPrefs.getBoolean(WALLET_USE_TOR, true)
    val stealthEnabled by StealthMode.enabled.collectAsState()
    val hazeState = rememberHazeState()
    val hazeSurface = MaterialTheme.colorScheme.surface
    val hazeStyle =
        remember(hazeSurface) {
            HazeBlurStyle {
                backgroundColor(hazeSurface.copy(alpha = 0.95f))
                blurRadius(26.dp)
            }
        }

    BackHandler {
        showExitDialog = true
    }

    LaunchedEffect(true) {
        scope.launch {
            refreshState.snapTo(0f)
        }
        if (StealthMode.isEnabled(anonPrefs)) walletState.setHideAmounts(true)
    }

    Toaster(
        state = toastState,
        maxVisibleToasts = 4,
        alignment = Alignment.BottomCenter,
        darkTheme = true,
        richColors = true,
        containerPadding =
            PaddingValues(
                bottom = 84.dp,
            ),
    )
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors =
                    TopAppBarDefaults.topAppBarColors().copy(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                modifier =
                    Modifier
                        .hazeBlur(
                            input = HazeInput.Sources(hazeState),
                            style = hazeStyle,
                        ).fillMaxWidth(),
                title = {
                    Text(
                        if (stealthEnabled) {
                            stringResource(R.string.wallet_title)
                        } else if (AnonConfig.viewOnly) {
                            "[ИΞR0]"
                        } else {
                            "[ΛИ0И]"
                        },
                    )
                },
                actions = {
                    IconButton(
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        onClick = {
                            showScanner = true
                        },
                    ) {
                        Icon(AnonIcons.Scan, contentDescription = stringResource(R.string.scan))
                    }
                    if (!offline) {
                        IconButton(
                            onClick = {
                                navigateTo(SettingsNodeRoute)
                            },
                        ) {
                            val torIconColor = if (useTor && torConnected == false) Color.Red else MaterialTheme.colorScheme.onBackground
                            Icon(
                                painterResource(R.drawable.ic_tor),
                                contentDescription = stringResource(R.string.tor_status),
                                tint = torIconColor,
                            )
                        }
                    }
//                    LockButton(
//                        onLock = {
//                            scope.launch {
//                                try {
//                                    settingBSync = true
//                                    walletState.blockUpdates(true)
//                                    withContext(Dispatchers.IO) {
//                                        WalletManager.instance?.wallet?.let { wallet: Wallet ->
//                                            if (wallet.startBackgroundSync()) {
//                                                walletState.setBackGroundSync(true)
//                                            }
//                                        }
//                                    }
//                                } finally {
//                                    walletState.blockUpdates(false)
//                                    settingBSync = false
//                                }
//                            }
//                        }, loading = settingBSync
//                    )
                    IconButton(
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        onClick = {
                            showMenu = !showMenu
                        },
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                        DropdownMenu(
                            expanded = showMenu,
                            containerColor = MaterialTheme.colorScheme.background,
                            modifier =
                                Modifier
                                    .border(
                                        1.dp,
                                        color =
                                            MaterialTheme.colorScheme.onBackground.copy(
                                                alpha = .2f,
                                            ),
                                        shape = MaterialTheme.shapes.medium,
                                    ),
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.accounts)) },
                                onClick = {
                                    showMenu = false
                                    navigateTo(AccountsRoute)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sign_verify_title)) },
                                onClick = {
                                    showMenu = false
                                    navigateTo(SignVerifyRoute)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pay_to_many)) },
                                onClick = {
                                    showMenu = false
                                    navigateTo(PayToManyRoute)
                                },
                            )
                            if (!offline) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.coin_control)) },
                                    onClick = {
                                        navigateTo(CoinsScreenRoute)
                                    },
                                )
                            }
                            if (!offline) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.refresh)) },
                                    onClick = {
                                        showMenu = false
                                        scope.launch(Dispatchers.IO) {
                                            runCatching { walletState.refresh() }
                                                .onFailure { failure ->
                                                    Timber
                                                        .tag(TAG)
                                                        .e(failure, "wallet refresh failed")
                                                    withContext(Dispatchers.Main) {
                                                        toastState.show(
                                                            resources.getString(R.string.connection_failed),
                                                            type = ToastType.Warning,
                                                            duration = 6.seconds,
                                                        )
                                                    }
                                                }
                                        }
                                    },
                                )
                            }
                            if (!offline) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.resync_blockchain)) },
                                    onClick = {
                                        showMenu = false
                                        val result = walletState.resyncBlockchain()
                                        if (result.isFailure) {
                                            scope.launch {
                                                toastState.show(
                                                    resources.getString(R.string.resync_failed),
                                                    type = ToastType.Warning,
                                                    duration = 6.seconds,
                                                )
                                            }
                                        } else {
                                            scope.launch {
                                                toastState.show(
                                                    resources.getString(R.string.resync_initiated_this_may_take_a_while),
                                                    type = ToastType.Success,
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                            if (AnonConfig.viewOnly) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.show_outputs)) },
                                    onClick = {
                                        qrScannerParam =
                                            SpendQRExchangeParam(
                                                exportType = ExportType.OUTPUT,
                                                title = resources.getString(R.string.outputs),
                                                ctaText = resources.getString(R.string.scan_key_images),
                                            )
                                    },
                                )
                            }
                            if (!AnonConfig.viewOnly) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_key_images)) },
                                    onClick = {
                                        qrScannerParam =
                                            SpendQRExchangeParam(
                                                exportType = ExportType.IMAGE,
                                                title = resources.getString(R.string.key_images),
                                                ctaText = "",
                                            )
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        PullToRefreshBox(
            isRefreshing = false,
            state = refreshState,
            modifier =
                Modifier
                    .hazeSource(
                        state = hazeState,
                    ),
            onRefresh = {
                scope.launch {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    } else {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    walletState.refresh()
                    refreshState.animateToHidden()
                }
            },
            indicator = {},
        ) {
            LazyColumn(
                contentPadding = contentPadding,
            ) {
                stickyHeader(key = "progress") {
                    Column {
                        WalletProgressIndicator(
                            refreshIndicatorProgress = refreshState.distanceFraction,
                        )
                        AnimatedVisibility(
                            visible = refreshState.distanceFraction > .2f && !showIndefiniteLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.pull_to_start_refresh),
                                textAlign = TextAlign.Center,
                                modifier =
                                    Modifier.padding(
                                        top = 16.dp,
                                        bottom = 8.dp,
                                    ),
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                    ),
                            )
                        }
                    }
                }
                item(key = "balance") {
                    Box(
                        modifier =
                            Modifier
                                .padding(
                                    vertical = 32.dp,
                                ).combinedClickable(
                                    onClick = { walletState.toggleHideAmounts() },
                                    onLongClick = {
                                        if (!offline) navigateTo(CoinsScreenRoute)
                                    },
                                ).fillParentMaxWidth(),
                    ) {
                        Text(
                            if (hideAmounts) {
                                Formats.maskAmount(balance ?: 0)
                            } else {
                                Formats.getDisplayAmount(balance ?: 0)
                            },
                            style =
                                MaterialTheme.typography
                                    .displaySmall,
                            modifier = Modifier.fillParentMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                items(transactions.size, key = { transactions[it].getListKey() }) {
                    with(sharedTransitionScope) {
                        TransactionItem(
                            transactions[it],
                            hideAmounts = hideAmounts,
                            modifier =
                                Modifier
                                    .clickable {
                                        onItemClick(transactions[it])
                                    }.sharedElement(
                                        sharedTransitionScope.rememberSharedContentState(
                                            key = "${transactions[it].hash}",
                                        ),
                                        animatedVisibilityScope = animatedContentScope,
                                    ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    tx: TransactionInfo,
    modifier: Modifier = Modifier,
    hideAmounts: Boolean = false,
) {
    val isIncoming = tx.direction == TransactionInfo.Direction.Direction_In
    val amount = if (isIncoming) tx.amount else tx.amount
    val confirmations = tx.confirmations
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 20.dp,
                ).border(
                    border =
                        BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        ),
                    shape = MaterialTheme.shapes.medium,
                ).padding(
                    horizontal = 12.dp,
                    vertical = 12.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(modifier = Modifier.padding(top = 2.dp)) {
            if (confirmations >= 10) {
                Icon(
                    if (isIncoming) AnonIcons.ArrowDownLeft else AnonIcons.ArrowUpRight,
                    modifier = Modifier.size(32.dp),
                    tint = if (isIncoming) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    contentDescription = "",
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(28.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        progress = {
                            ((confirmations.toFloat()) / (10f))
                        },
                    )
                    Text(
                        text = "$confirmations",
                        modifier = Modifier.align(Alignment.Center),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 9.sp,
                            ),
                    )
                }
            }
        }
        Text(
            if (hideAmounts) {
                Formats.maskAmount(amount)
            } else {
                Formats.getDisplayAmount(amount)
            },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            Formats.formatTransactionTime(tx.timestamp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun LockButton(
    onLock: () -> Unit,
    loading: Boolean = false,
) {
    val walletState = koinInject<WalletState>()
    val walletLoading by walletState.isLoading.asLiveData().observeAsState(false)
    IconButton(
        modifier = Modifier.alpha(if (walletLoading) 0.2f else 1.0f),
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
        onClick = {
            if (walletLoading || loading) {
                return@IconButton
            }
            onLock()
        },
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.lock))
        }
    }
}

@Preview(device = "id:pixel_7_pro")
@Composable
private fun TransactionScreenReview() {
    AnonNeroTheme {
//        TransactionScreen(
//            animatedContentScope = this@composable,
//            sharedTransitionScope = this@SharedTransitionLayout
//        )
    }
}
