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

package io.anonero.ui.home.settings

import AnonNeroTheme
import android.content.SharedPreferences
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.edit
import com.dokar.sonner.ToastType
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import io.anonero.AnonConfig
import io.anonero.BuildConfig
import io.anonero.R
import io.anonero.model.WalletManager
import io.anonero.services.WalletState
import io.anonero.ui.components.SettingsActionItem
import io.anonero.ui.components.SettingsSectionHeader
import io.anonero.ui.components.SettingsSwitchItem
import io.anonero.ui.home.LockScreen
import io.anonero.ui.home.LockScreenMode
import io.anonero.ui.home.graph.routes.AccountsRoute
import io.anonero.ui.home.graph.routes.DecoySettingsRoute
import io.anonero.ui.home.graph.routes.LwsSettingsRoute
import io.anonero.ui.home.graph.routes.ProxySettingsRoute
import io.anonero.ui.home.graph.routes.SecureWipeRoute
import io.anonero.ui.home.graph.routes.SettingsExportBackUp
import io.anonero.ui.home.graph.routes.SettingsLogs
import io.anonero.ui.home.graph.routes.SettingsNodeRoute
import io.anonero.ui.home.graph.routes.SettingsViewSeedRoute
import io.anonero.ui.home.graph.routes.SignVerifyRoute
import io.anonero.ui.onboard.PinSetup
import io.anonero.ui.theme.ThemeMode
import io.anonero.ui.theme.ThemeStore
import io.anonero.util.AppLanguage
import io.anonero.util.LocaleController
import io.anonero.util.PREFS_PIN_HASH
import io.anonero.util.PinHash
import io.anonero.util.StealthMode
import io.anonero.util.WALLET_PREFERENCES
import io.anonero.util.WalletKey
import io.anonero.util.WalletSession
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import timber.log.Timber

typealias NavigateTo<T> = (to: T) -> Unit

private const val TAG = "SettingsPage"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit = {},
    navigateTo: (param: Any) -> Unit = {},
) {
    val prefs = koinInject<SharedPreferences>(named(WALLET_PREFERENCES))
    val toastState = rememberToasterState()
    var showLockScreen by remember { mutableStateOf(false) }
    var newPinDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var stealthEnabled by remember { mutableStateOf(StealthMode.isEnabled(prefs)) }
    var stealthAutoLock by remember { mutableStateOf(StealthMode.autoLock(prefs)) }
    val themeMode by ThemeStore.mode.collectAsState()
    val language = remember { LocaleController.load(prefs) }
    val activity = LocalActivity.current
    val pinChangedMessage = stringResource(R.string.pin_changed_success)
    val pinChangeErrorMessage = stringResource(R.string.pin_change_error)

    // Dialogs are windows of their own and draw above the lock screen overlay. If the
    // wallet locks while one is open, close it: otherwise whoever picks the phone up
    // next finds the new-PIN prompt on top of the lock and sets a PIN of their own.
    val walletState = koinInject<WalletState>()
    val locked by walletState.backgroundSyncFlow.collectAsState(walletState.backgroundSync)
    val offline by walletState.offlineFlow.collectAsState(walletState.offline)
    LaunchedEffect(locked) {
        if (locked) {
            showLockScreen = false
            newPinDialog = false
            showThemeDialog = false
            showLanguageDialog = false
        }
    }

    if (showLockScreen && !locked) {
        Dialog(
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true,
                    decorFitsSystemWindows = false,
                    securePolicy = SecureFlagPolicy.SecureOn,
                ),
            onDismissRequest = {
                showLockScreen = false
            },
        ) {
            LockScreen(
                mode = LockScreenMode.VERYFY_PIN,
                modifier = Modifier.fillMaxHeight(0.95f),
                onUnLocked = { _, shortCut ->
                    showLockScreen = false
                    newPinDialog = true
                },
            )
        }
    }
    if (newPinDialog && !locked) {
        Dialog(
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true,
                    decorFitsSystemWindows = false,
                    securePolicy = SecureFlagPolicy.SecureOn,
                ),
            onDismissRequest = {
                newPinDialog = false
            },
        ) {
            PinSetup(
                changePin = true,
                onNext = {
                    try {
                        // A keystore-bound wallet is encrypted with the device secret W, so
                        // changing the PIN only re-writes the gate hash and W is untouched.
                        // A wallet whose migration has not happened yet is still encrypted
                        // with the PIN itself: re-key it first, or the next open with the
                        // new PIN fails and the user is locked out.
                        val duressHash =
                            prefs.getString(
                                io.anonero.util.PREFS_DURESS_PIN_HASH,
                                "",
                            )
                        if (!WalletSession.isDecoy && !duressHash.isNullOrEmpty() &&
                            PinHash.matches(it, duressHash)
                        ) {
                            throw Exception("The normal PIN must differ from the duress PIN")
                        }
                        if (WalletSession.isDecoy &&
                            PinHash.matches(it, prefs.getString(PREFS_PIN_HASH, ""))
                        ) {
                            throw Exception("The duress PIN must differ from the normal PIN")
                        }
                        val context = AnonConfig.context!!
                        val walletName =
                            io.anonero.util.WalletProfileStore
                                .activeWalletName(context)
                        if (!WalletKey.exists(context, walletName)) {
                            val wallet =
                                WalletManager.instance?.wallet
                                    ?: throw Exception("wallet not open")
                            if (!wallet.setPassword(it)) {
                                throw Exception("setPassword failed: ${wallet.status.errorString}")
                            }
                        }
                        prefs.edit(commit = true) {
                            putString(
                                io.anonero.util.WalletProfileStore
                                    .activePinHashKey(),
                                PinHash.hash(it),
                            )
                        }
                        toastState.show(pinChangedMessage, type = ToastType.Success)
                    } catch (e: Exception) {
                        toastState.show(pinChangeErrorMessage, type = ToastType.Error)
                        Timber.tag(TAG).e(e)
                    } finally {
                        newPinDialog = false
                    }
                },
            )
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.theme)) },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        ThemeStore.set(prefs, mode)
                                        showThemeDialog = false
                                    }.padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    ThemeStore.set(prefs, mode)
                                    showThemeDialog = false
                                },
                            )
                            Text(themeLabel(mode))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    AppLanguage.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showLanguageDialog = false
                                        if (activity != null && option != language) {
                                            LocaleController.setLanguage(activity, prefs, option)
                                        }
                                    }.padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = language == option,
                                onClick = {
                                    showLanguageDialog = false
                                    if (activity != null && option != language) {
                                        LocaleController.setLanguage(activity, prefs, option)
                                    }
                                },
                            )
                            Text(stringResource(option.labelRes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    Toaster(
        state = toastState,
        maxVisibleToasts = 2,
        alignment = Alignment.TopCenter,
        darkTheme = true,
    )
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBackPress,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = {
                    Text("")
                },
            )
        },
    ) {
        val settingsMenuHeaderStyle =
            MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.primary,
            )

        Box(
            modifier =
                Modifier
                    .padding(it)
                    .fillMaxSize(),
        ) {
            Column {
                LazyColumn(
                    modifier =
                        Modifier
                            .padding(
                                horizontal = 12.dp,
                            ),
                ) {
                    item {
                        SettingsSectionHeader(stringResource(R.string.settings_appearance_privacy))
                    }
                    item {
                        SettingsSwitchItem(
                            title = stringResource(R.string.stealth_mode),
                            checked = stealthEnabled,
                            supporting = stringResource(R.string.stealth_mode_supporting),
                            onCheckedChange = { enabled ->
                                stealthEnabled = enabled
                                StealthMode.setEnabled(prefs, enabled)
                                if (enabled) walletState.setHideAmounts(true)
                            },
                        )
                    }
                    item {
                        SettingsSwitchItem(
                            title = stringResource(R.string.auto_lock_stealth),
                            checked = stealthAutoLock,
                            supporting = stringResource(R.string.auto_lock_stealth_supporting),
                            onCheckedChange = { enabled ->
                                stealthAutoLock = enabled
                                StealthMode.setAutoLock(prefs, enabled)
                            },
                        )
                    }
                    item {
                        SettingsActionItem(
                            title = stringResource(R.string.theme),
                            supporting = themeLabel(themeMode),
                            onClick = { showThemeDialog = true },
                        )
                    }
                    item {
                        SettingsActionItem(
                            title = stringResource(R.string.language),
                            supporting = stringResource(language.labelRes),
                            onClick = { showLanguageDialog = true },
                        )
                    }
                    if (!offline) {
                        item {
                            Text(
                                stringResource(R.string.settings_connection),
                                style = settingsMenuHeaderStyle,
                                modifier =
                                    Modifier.padding(
                                        vertical = 12.dp,
                                        horizontal = 8.dp,
                                    ),
                            )
                        }
                    }
                    if (!offline) {
                        item {
                            HorizontalDivider(
                                thickness = 2.5.dp,
                            )
                            SettingsMenuItem(
                                title = stringResource(R.string.settings_node_settings),
                                onClick = {
                                    navigateTo(SettingsNodeRoute)
                                },
                            )
                            HorizontalDivider(
                                thickness = 2.5.dp,
                            )
                        }
                    }
                    if (!offline) {
                        item {
                            SettingsMenuItem(title = stringResource(R.string.settings_proxy_settings), onClick = {
                                navigateTo(ProxySettingsRoute)
                            })
                            HorizontalDivider(
                                thickness = 2.5.dp,
                            )
                        }
                    }
                    item {
                        SettingsActionItem(
                            title = stringResource(R.string.lws_settings_title),
                            supporting = stringResource(R.string.lws_settings_supporting),
                            onClick = { navigateTo(LwsSettingsRoute) },
                        )
                        HorizontalDivider(thickness = 2.5.dp)
                    }
                    item {
                        Text(
                            stringResource(R.string.settings_security),
                            style = settingsMenuHeaderStyle,
                            modifier =
                                Modifier.padding(
                                    vertical = 12.dp,
                                    horizontal = 8.dp,
                                ),
                        )
                    }
                    item {
                        SettingsActionItem(
                            title = stringResource(R.string.accounts_settings_title),
                            supporting = stringResource(R.string.accounts_settings_supporting),
                            onClick = { navigateTo(AccountsRoute) },
                        )
                        HorizontalDivider(thickness = 2.5.dp)
                    }
                    item {
                        SettingsActionItem(
                            title = stringResource(R.string.sign_verify_settings_title),
                            supporting = stringResource(R.string.sign_verify_settings_supporting),
                            onClick = { navigateTo(SignVerifyRoute) },
                        )
                        HorizontalDivider(thickness = 2.5.dp)
                    }
                    if (!AnonConfig.viewOnly) {
                        item {
                            SettingsMenuItem(title = stringResource(R.string.settings_seed), onClick = {
                                navigateTo(SettingsViewSeedRoute)
                            })
                            HorizontalDivider(
                                thickness = 2.5.dp,
                            )
                        }
                    }
                    item {
                        SettingsMenuItem(title = stringResource(R.string.settings_change_pin), onClick = {
                            showLockScreen = true
                        })
                        HorizontalDivider(
                            thickness = 2.5.dp,
                        )
                    }
                    if (!WalletSession.isDecoy) {
                        item {
                            SettingsMenuItem(title = stringResource(R.string.settings_export_backup), onClick = {
                                navigateTo(SettingsExportBackUp)
                            })
                            HorizontalDivider(
                                thickness = 2.5.dp,
                            )
                        }
                    }
                    if (!WalletSession.isDecoy && !AnonConfig.viewOnly) {
                        item {
                            SettingsActionItem(
                                title = stringResource(R.string.decoy_settings_title),
                                supporting = stringResource(R.string.decoy_settings_supporting),
                                onClick = { navigateTo(DecoySettingsRoute) },
                            )
                            HorizontalDivider(thickness = 2.5.dp)
                        }
                    }
                    item {
                        SettingsMenuItem(
                            title = stringResource(R.string.settings_secure_wipe),
                            onClick = {
                                navigateTo(SecureWipeRoute)
                            },
                        )
                        HorizontalDivider(
                            thickness = 2.5.dp,
                        )
                    }
                }
            }
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .padding(bottom = 64.dp),
            ) {
                Text(
                    stringResource(R.string.settings_app_version, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
                    style =
                        MaterialTheme.typography.labelLarge.copy(
                            color =
                                MaterialTheme.colorScheme.onBackground.copy(
                                    alpha = 0.6f,
                                ),
                            fontSize = 12.sp,
                        ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable { navigateTo(SettingsLogs) },
                )
            }
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
    }

@Composable
fun SettingsMenuItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    title: String = "",
) {
    val settingsMenuStyle = MaterialTheme.typography.titleMedium.copy()

    ListItem(
        modifier =
            Modifier
                .padding(horizontal = 4.dp)
                .clickable(
                    onClick = onClick,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                ),
        headlineContent = {
            Text(
                title,
                style = settingsMenuStyle,
            )
        },
    )
}

@Preview(device = "id:pixel_5")
@Composable
private fun SettingsPagePrev() {
    AnonNeroTheme {
        SettingsPage()
    }
}
