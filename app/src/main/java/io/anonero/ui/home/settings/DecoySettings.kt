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
import AnonOutlineButton
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import io.anonero.AnonConfig
import io.anonero.R
import io.anonero.model.WalletManager
import io.anonero.ui.onboard.PinSetup
import io.anonero.util.KeyStoreHelper
import io.anonero.util.PREFS_DECOY_CONFIGURED
import io.anonero.util.PREFS_DECOY_PASSPHRASE_HASH
import io.anonero.util.PREFS_DURESS_PIN_HASH
import io.anonero.util.PREFS_PIN_HASH
import io.anonero.util.PinHash
import io.anonero.util.WalletKey
import io.anonero.util.WalletProfile
import io.anonero.util.WalletProfileStore
import io.anonero.util.WalletSession
import io.anonero.services.WalletState
import io.anonero.util.WALLET_PREFERENCES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import timber.log.Timber

private const val TAG = "DecoySettings"

/* Localizable decoy failures. Internal wallet2 text is logged, never displayed. */
private class DecoyException(val error: DecoyError) : Exception(error.name)

enum class DecoyError(@StringRes val messageRes: Int) {
    DURESS_PIN_DIFFERS(R.string.duress_pin_differs_normal),
    CONTEXT_UNAVAILABLE(R.string.app_context_unavailable),
    EXISTS(R.string.decoy_exists),
    CREATION_FAILED(R.string.decoy_creation_failed),
    WRITE_FAILED(R.string.decoy_creation_failed),
    SEED_FAILED(R.string.decoy_seed_failed),
    CREDENTIALS_FAILED(R.string.decoy_credentials_failed),
    PASSPHRASE_DIFFERS(R.string.decoy_passphrase_differs),
}

/**
 * Main-profile-only configuration for the decoy wallet and duress PIN.
 *
 * The screen requires a fresh PIN verification before destructive actions, hides itself in
 * the decoy session and the view-only build, and rolls back a failed decoy creation. It is
 * not a plausible-deniability feature; see `WalletProfiles.kt` and the security model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoySettingsScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit = {},
) {
    val prefs = koinInject<android.content.SharedPreferences>(named(WALLET_PREFERENCES))
    val walletState = koinInject<WalletState>()
    val locked by walletState.backgroundSyncFlow.collectAsState(walletState.backgroundSync)
    val context = AnonConfig.context
    val scope = rememberCoroutineScope()
    var unlocked by remember { mutableStateOf(false) }
    val initialConfigured = context?.let { WalletProfileStore.isDecoyConfigured(it, prefs) } ?: false
    var configured by remember { mutableStateOf(initialConfigured) }
    var showPassphrase by remember { mutableStateOf(false) }
    var showDuressPin by remember { mutableStateOf(false) }
    var showChangeDuress by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<DecoyError?>(null) }
    var creating by remember { mutableStateOf(false) }
    var seed: List<String>? by remember { mutableStateOf(null) }

    BackHandler { onBackPress() }

    LaunchedEffect(locked) {
        if (locked) {
            // Dialog windows can sit above the lock overlay; never leave a destructive
            // action reachable without re-entering the main PIN.
            unlocked = false
            showPassphrase = false
            showDuressPin = false
            showChangeDuress = false
            showRemoveConfirm = false
            passphrase = ""
            confirmPassphrase = ""
            seed = null
            error = null
        }
    }

    if (WalletSession.isDecoy || AnonConfig.viewOnly) {
        // Never expose decoy configuration from the decoy session itself. The NERO
        // view-only build has no spend key, so provisioning a second full wallet there
        // would contradict the build's security model.
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.decoy_not_available))
        }
        return
    }

    if (!unlocked) {
        io.anonero.ui.home.LockScreen(
            mode = io.anonero.ui.home.LockScreenMode.VERYFY_PIN,
            modifier = modifier.fillMaxSize(),
            onUnLocked = { _, _ -> unlocked = true }
        )
        return
    }

    if (seed != null) {
        io.anonero.ui.onboard.SeedSetup(
            seed = seed!!,
            oNextPressed = { seed = null; configured = true },
            onBackPressed = { seed = null; configured = true }
        )
        return
    }

    if (showDuressPin) {
        PinSetup(
            enterLabel = stringResource(R.string.enter_duress_pin),
            onBackPress = { showDuressPin = false; showPassphrase = true; creating = false },
            onNext = { pin ->
                scope.launch(Dispatchers.IO) {
                    val sameAsMain = PinHash.matches(
                        pin, prefs.getString(PREFS_PIN_HASH, "")
                    )
                    withContext(Dispatchers.Main) {
                        if (sameAsMain) {
                            error = DecoyError.DURESS_PIN_DIFFERS
                            showDuressPin = false
                            showPassphrase = true
                        } else {
                            creating = true
                            createDecoy(pin, passphrase, context, prefs, scope) { result, message ->
                                showDuressPin = false
                                creating = false
                                if (result == null) {
                                    error = message
                                    showPassphrase = true
                                } else {
                                    seed = result
                                }
                            }
                        }
                    }
                }
            }
        )
        return
    }

    if (showChangeDuress) {
        PinSetup(
            enterLabel = stringResource(R.string.new_duress_pin),
            onBackPress = { showChangeDuress = false },
            onNext = { pin ->
                scope.launch(Dispatchers.IO) {
                    val sameAsMain = PinHash.matches(
                        pin, prefs.getString(PREFS_PIN_HASH, "")
                    )
                    val newHash = if (sameAsMain) null else PinHash.hash(pin)
                    withContext(Dispatchers.Main) {
                        if (sameAsMain || newHash == null) {
                            error = DecoyError.DURESS_PIN_DIFFERS
                        } else {
                            prefs.edit(commit = true) {
                                putString(PREFS_DURESS_PIN_HASH, newHash)
                            }
                            showChangeDuress = false
                        }
                    }
                }
            }
        )
        return
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text(stringResource(R.string.decoy_remove_title)) },
            text = {
                Text(stringResource(R.string.decoy_remove_warning))
            },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    val ctx = context ?: return@TextButton
                    WalletProfileStore.removeProfile(ctx, prefs, WalletProfile.DECOY)
                    configured = false
                    error = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.decoy_duress_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.decoy_intro),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Text(
                    stringResource(R.string.decoy_limits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (creating) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.creating_decoy_wallet), modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
            if (!configured && !showPassphrase) {
                item {
                    AnonOutlineButton(onClick = { error = null; showPassphrase = true }) {
                        Text(stringResource(R.string.setup_decoy_wallet))
                    }
                }
            }
            if (!configured && showPassphrase) {
                item {
                    Text(
                        stringResource(R.string.decoy_passphrase_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                item {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(stringResource(R.string.decoy_passphrase_min)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = confirmPassphrase,
                        onValueChange = { confirmPassphrase = it },
                        label = { Text(stringResource(R.string.confirm_decoy_passphrase)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirmPassphrase.isNotEmpty() && confirmPassphrase != passphrase,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    AnonOutlineButton(
                        enabled = passphrase.length >= 8 && passphrase == confirmPassphrase && !creating,
                        onClick = {
                            val context = context
                            if (context == null) {
                                error = DecoyError.CONTEXT_UNAVAILABLE
                                return@AnonOutlineButton
                            }
                            scope.launch(Dispatchers.IO) {
                                val mainPassHash = prefs.getString(
                                    io.anonero.util.PREFS_PASSPHRASE_HASH, ""
                                )
                                val candidate = KeyStoreHelper.getCrazyPass(context, passphrase)
                                withContext(Dispatchers.Main) {
                                    if (mainPassHash == candidate) {
                                        error = DecoyError.PASSPHRASE_DIFFERS
                                    } else {
                                        error = null
                                        showDuressPin = true
                                    }
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.continue_action)) }
                }
            }
            if (configured) {
                item {
                    Text(
                        stringResource(R.string.decoy_configured_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    AnonOutlineButton(onClick = { error = null; showChangeDuress = true }) {
                        Text(stringResource(R.string.change_duress_pin))
                    }
                }
                item {
                    AnonOutlineButton(onClick = { showRemoveConfirm = true }) {
                        Text(stringResource(R.string.remove_decoy_wallet))
                    }
                }
            }
            error?.let { decoyError ->
                item {
                    Text(
                        stringResource(decoyError.messageRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun createDecoy(
    duressPin: String,
    passphrase: String,
    context: android.content.Context?,
    prefs: android.content.SharedPreferences,
    scope: kotlinx.coroutines.CoroutineScope,
    onDone: (List<String>?, DecoyError?) -> Unit,
) {
    if (context == null) {
        onDone(null, DecoyError.CONTEXT_UNAVAILABLE)
        return
    }
    scope.launch(Dispatchers.IO) {
        val file = WalletProfileStore.walletFile(context, WalletProfile.DECOY)
        if (file.exists() || WalletKey.exists(context, file.name)) {
            onDone(null, DecoyError.EXISTS)
            return@launch
        }
        val walletKey = WalletKey.newSecret()
        var created: io.anonero.model.Wallet? = null
        try {
            WalletKey.store(context, file.name, walletKey)
            val wallet = WalletManager.instance?.createWalletUnmanaged(
                aFile = file,
                password = walletKey,
                passphrase = passphrase,
                language = "English",
                height = 1,
            ) ?: throw DecoyException(DecoyError.CREATION_FAILED)
            created = wallet
            if (wallet.status.isOk != true) {
                Timber.tag(TAG).e("decoy creation status: %s", wallet.status.errorString)
                throw DecoyException(DecoyError.CREATION_FAILED)
            }
            // A brand-new decoy has no history. Start scanning near the current tip so it
            // is usable quickly; the user can restore from the seed at an earlier height.
            val chainHeight = runCatching { WalletManager.instance?.getBlockchainHeight() }
                .getOrNull() ?: 0L
            if (chainHeight > 1L) wallet.setRestoreHeight(chainHeight)
            if (!wallet.store()) {
                Timber.tag(TAG).e("decoy store failed: %s", wallet.status.errorString)
                throw DecoyException(DecoyError.WRITE_FAILED)
            }
            val words = wallet.getSeed(passphrase)?.split(" ").orEmpty()
            if (words.isEmpty()) throw DecoyException(DecoyError.SEED_FAILED)
            val restoreHeight = wallet.getRestoreHeight()
            prefs.edit(commit = true) {
                putString(PREFS_DURESS_PIN_HASH, PinHash.hash(duressPin))
                putString(
                    PREFS_DECOY_PASSPHRASE_HASH,
                    KeyStoreHelper.getCrazyPass(context, passphrase)
                )
                putBoolean(PREFS_DECOY_CONFIGURED, true)
                putLong(WalletProfileStore.restoreHeightKey(WalletProfile.DECOY), restoreHeight)
            }
            if (!WalletProfileStore.isDecoyConfigured(context, prefs)) {
                throw DecoyException(DecoyError.CREDENTIALS_FAILED)
            }
            withContext(Dispatchers.Main) { onDone(words, null) }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "decoy creation failed; rolling back")
            runCatching { created?.let { WalletManager.instance?.closeUnmanaged(it) } }
            created = null
            WalletProfileStore.removeProfile(context, prefs, WalletProfile.DECOY)
            val mapped = (e as? DecoyException)?.error ?: DecoyError.CREATION_FAILED
            withContext(Dispatchers.Main) { onDone(null, mapped) }
        } finally {
            runCatching { created?.let { WalletManager.instance?.closeUnmanaged(it) } }
        }
    }
}

@Preview(device = "id:pixel_5")
@Composable
private fun DecoyPreview() {
    AnonNeroTheme { DecoySettingsScreen() }
}
