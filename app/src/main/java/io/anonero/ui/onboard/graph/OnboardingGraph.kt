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

package io.anonero.ui.onboard.graph

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import android.content.Context
import androidx.activity.compose.BackHandler
import io.anonero.AnonConfig
import io.anonero.model.node.NodeFields
import io.anonero.R
import io.anonero.services.AnonWalletHandler
import io.anonero.services.WalletState
import io.anonero.services.startAnonService
import io.anonero.util.RestoreTargetNotEmptyException
import io.anonero.util.WalletSession
import io.anonero.ui.home.graph.routes.Home
import io.anonero.ui.home.settings.LogViewer
import io.anonero.ui.home.settings.ProxySettings
import io.anonero.ui.onboard.Mode
import io.anonero.ui.onboard.OnboardLoadingComposable
import io.anonero.ui.onboard.OnboardViewModel
import io.anonero.ui.onboard.OnboardingWelcome
import io.anonero.ui.onboard.PinSetup
import io.anonero.ui.onboard.RestoreWallet
import io.anonero.ui.onboard.SeedSetup
import io.anonero.ui.onboard.SetupNodeComposable
import io.anonero.ui.onboard.SetupPassphrase
import io.anonero.ui.onboard.restore.RestorePreview
import io.anonero.ui.onboard.viewonly.RestoreFromKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject


@Serializable
data object LandingScreenRoute

@Serializable
data object OnboardingWelcomeScreen

@Serializable
data object OnboardingProxyScreen

@Serializable
data object OnboardPassPhraseScreen

@Serializable
data object OnboardLogsScreen

@Serializable
data class OnboardLoading(val message: String)

@Serializable
data object OnboardSeedScreen

@Serializable
data object OnboardPinScreen

@Serializable
data object OnboardNodeSetupScreen

@Serializable
data object OnboardRestoreScreen

@Serializable
data object OnboardRestoreNodeScreen

@Serializable
data class OnboardRestorePreviewScreen(val backUpPath: String)

@Serializable
data object OnboardImportKeysScreen

//onboarding
/**
 * Navigation graph for first-run setup, seed restore, backup restore, and view-only key
 * import.
 *
 * The graph is entered only when no usable wallet exists. It shares [OnboardViewModel] so
 * the passphrase/seed/restore payload survives screen changes; the final destination is
 * either the home graph or the node setup screen for an offline/airgapped flow.
 */
fun NavGraphBuilder.onboardingGraph(
    navController: NavHostController,
    onboardViewModel: OnboardViewModel
) {


    navigation<LandingScreenRoute>(
        startDestination = OnboardingWelcomeScreen,
    ) {

        dialog<OnboardLoading>(
            dialogProperties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            OnboardLoadingComposable(loadingState = it.toRoute())
        }
        composable<OnboardingWelcomeScreen> {
            OnboardingWelcome(
                onRestoreClick = {
                    onboardViewModel.setMode(Mode.RESTORE)
                    if (it != null) {
                        navController.navigate(OnboardRestorePreviewScreen(it))
                    } else {
                        navController.navigate(OnboardRestoreScreen)
                    }
                },
                onCreateClick = {
                    onboardViewModel.setMode(Mode.CREATE)
                    navController.navigate(OnboardNodeSetupScreen)
                },
                onRestoreFromKeys = {
                    navController.navigate(OnboardImportKeysScreen)
                },
                onProxySettings = {
                    navController.navigate(OnboardingProxyScreen)
                },
                onLogsScreen = {
                    navController.navigate(OnboardLogsScreen)
                }
            )
        }
        composable<OnboardingProxyScreen> {
            ProxySettings(
                onBackPress = {
                    navController.navigateUp()
                },
            )
        }
        composable<OnboardNodeSetupScreen> {
            SetupNodeComposable(
                onBackPressed = {
                    navController.navigateUp()
                },
                oNextPressed = {
                    if (AnonConfig.viewOnly) {
                        navController.navigate(OnboardPinScreen)
                    } else {
                        navController.navigate(OnboardPassPhraseScreen)
                    }
                }
            )
        }
        composable<OnboardRestoreScreen> {
            RestoreWallet(
                onboardViewModel = onboardViewModel,
                onBackPressed = {
                    navController.navigateUp()
                },
                oNextPressed = {
                    onboardViewModel.setRestorePayload(it)
                    navController
                        .navigate(OnboardNodeSetupScreen)
                }
            )
        }
        composable<OnboardRestorePreviewScreen> { navBackStackEntry ->
            val backUpPath = navBackStackEntry.toRoute<OnboardRestorePreviewScreen>()
            val context = LocalContext.current
            val walletState = koinInject<WalletState>()
            val walletHandler = koinInject<AnonWalletHandler>()
            RestorePreview(
                backUpPath = backUpPath.backUpPath,
                onBackPressed = {
                    navController.navigateUp()
                },
                navigateTo = {
                    navController.navigate(it)
                },
                oNextPressed = {
                    // The restore left the re-keyed wallet managed. Start the normal
                    // wallet pipeline before leaving onboarding.
                    walletState.setProfile(WalletSession.profile)
                    walletHandler.scope.launch { walletHandler.startService() }
                    runCatching { startAnonService(context) }
                    val prefs = AnonConfig.context?.getSharedPreferences(AnonConfig.PREFS, Context.MODE_PRIVATE)
                    //a backup of an offline signer has no node
                    if (prefs?.getString(NodeFields.RPC_HOST.value, "").isNullOrEmpty()) {
                        navController.navigate(OnboardRestoreNodeScreen)
                    } else {
                        navController.navigate(
                            Home
                        ) {
                            popUpTo(LandingScreenRoute)
                        }
                    }
                }
            )
        }
        composable<OnboardRestoreNodeScreen> {
            val toHome = {
                navController.navigate(
                    Home
                ) {
                    popUpTo(LandingScreenRoute)
                }
            }
            BackHandler { toHome() }
            SetupNodeComposable(
                onBackPressed = toHome,
                oNextPressed = toHome
            )
        }
        composable<OnboardPassPhraseScreen> {
            SetupPassphrase(
                onBackPressed = {
                    navController.navigateUp()
                },
                oNextPressed = {
                    onboardViewModel.setPassPhrase(it)
                    navController.navigate(OnboardPinScreen)
                }
            )
        }

        composable<OnboardLogsScreen>() {
            LogViewer(
                onBackPress = {
                    navController.navigateUp()
                }
            )
        }

        composable<OnboardPinScreen> {
            var showErrorMessage by remember { mutableStateOf<String?>(null) }
            val context = LocalContext.current
            val walletState = koinInject<WalletState>()
            val walletHandler = koinInject<AnonWalletHandler>()
            OnboardErrorDialog(
                showErrorMessage = showErrorMessage,
                isRestoreMode = onboardViewModel.getMode() == Mode.RESTORE,
                onClose = {
                    showErrorMessage = null
                    navController.popBackStack()
                },
                onShowLogs = {
                    showErrorMessage = null
                    navController.popBackStack()
                    navController.navigate(OnboardLogsScreen)
                }
            )
            PinSetup(
                onNext = { pin ->
                    var loadingMessage = context.getString(R.string.creating_wallet)
                    if (onboardViewModel.getMode() == Mode.RESTORE) {
                        loadingMessage = context.getString(R.string.restoring_wallet)
                    }
                    navController.navigate(OnboardLoading(loadingMessage))
                    onboardViewModel.viewModelScope.launch {
                        try {
                            if (AnonConfig.viewOnly) {
                                onboardViewModel.createViewOnly(pin)
                            } else if (onboardViewModel.getMode() == Mode.RESTORE) {
                                withContext(Dispatchers.IO) {
                                    onboardViewModel.restoreFromSeed(pin)
                                }
                            } else {
                                onboardViewModel.create(pin)
                            }
                            // The newly created/restored wallet is managed. Publish the
                            // profile and start the normal proxy/init/refresh pipeline
                            // before Home renders; otherwise the first session is inert.
                            walletState.setProfile(WalletSession.profile)
                            walletHandler.scope.launch { walletHandler.startService() }
                            runCatching { startAnonService(context) }
                            delay(600)
                            if (AnonConfig.viewOnly || onboardViewModel.getMode() == Mode.RESTORE) {
                                navController.navigate(Home) {
                                    popUpTo(LandingScreenRoute)
                                }
                            } else {
                                navController.navigate(OnboardSeedScreen)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            navController.popBackStack()
                            showErrorMessage = context.getString(
                                if (failure is RestoreTargetNotEmptyException) R.string.wallet_target_not_empty
                                else if (onboardViewModel.getMode() == Mode.RESTORE) R.string.restore_failed
                                else R.string.unable_to_create_wallet
                            )
                        }
                    }
                }
            )
        }
        composable<OnboardSeedScreen> {
            val seed by produceState(initialValue = emptyList<String>()) {
                value = withContext(Dispatchers.IO) {
                    onboardViewModel.getSeed()
                        .trim()
                        .split(Regex("\\s+"))
                        .filter { it.isNotEmpty() }
                }
            }
            SeedSetup(
                seed = seed,
                oNextPressed = {
                    navController.navigate(
                        Home
                    ) {
                        popUpTo(LandingScreenRoute)
                    }
                },
                // The wallet already exists. Do not navigate back to the PIN screen or
                // reload the seed from a saved-state route; require the explicit Next.
                onBackPressed = { }
            )
        }
        composable<OnboardImportKeysScreen> {
            RestoreFromKeys(
                oNextPressed = {
                    onboardViewModel.setNeroPayload(it)
                    navController.navigate(OnboardNodeSetupScreen)
                },
                onBackPressed = {
                    navController.navigateUp()
                }
            )
        }
    }
}


@Composable
fun OnboardErrorDialog(
    showErrorMessage: String?,
    isRestoreMode: Boolean,
    onClose: () -> Unit,
    onShowLogs: () -> Unit
) {
    if (showErrorMessage != null) {
        AlertDialog(
            onDismissRequest = onClose,
            confirmButton = {
                Button(
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(
                        1.dp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    onClick = onClose
                ) { Text(stringResource(R.string.close)) }
            },
            modifier = Modifier
                .border(
                    1.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .2f),
                    shape = MaterialTheme.shapes.medium,
                ),
            dismissButton = {
                Button(
                    onClick = onShowLogs,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(
                        1.dp,
                        color = MaterialTheme.colorScheme.onSecondary
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    )
                ) {
                    Text(
                        stringResource(R.string.logs),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.secondary,
            title = {
                Text(
                    text = stringResource(
                        R.string.wallet,
                        stringResource(R.string.unable_to),
                        if (isRestoreMode) stringResource(
                            R.string.restore
                        ) else stringResource(R.string.create)
                    ),
                )
            },
            text = {
                Text(
                    text = showErrorMessage,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = MaterialTheme.colorScheme.error,
                    )
                )
            }
        )
    }
}
