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
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.anonero.AnonConfig
import io.anonero.R
import io.anonero.model.Wallet
import io.anonero.model.WalletManager
import io.anonero.model.node.NodeFields
import io.anonero.services.AnonWalletHandler
import io.anonero.store.LogRepository
import io.anonero.store.NodesRepository
import io.anonero.ui.MainActivity
import io.anonero.util.SecureClipboard
import io.anonero.util.ShakeConfig
import io.anonero.util.WALLET_PROXY
import io.anonero.util.WALLET_PROXY_PORT
import io.anonero.util.WalletProfile
import io.anonero.util.WalletSession
import io.anonero.util.backup.BackupHelper
import io.anonero.util.commitEdit
import io.anonero.util.rememberShakeController
import io.anonero.util.shake
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

private const val TAG = "SecureWipe"

/**
 * Executes a profile-scoped secure wipe after the handler verifies the profile passphrase.
 *
 * It stops the foreground service, closes the active wallet, removes only that profile's
 * files/Keystore secret/hashes, clears shared non-credential state (nodes, logs, cache), and
 * resets the PIN throttle. A failed wallet close aborts before removing profile data.
 */
class SecureWipeViewModel(
    private val anonWalletHandler: AnonWalletHandler,
    private val sharedPreferences: SharedPreferences,
    private val nodesRepository: NodesRepository,
    private val logRepository: LogRepository,
) : ViewModel() {
    private val _wipeProgressMessage = MutableLiveData<Int?>()
    val wipeProgressMessage: LiveData<Int?> = _wipeProgressMessage

    private val _wipeProgress = MutableLiveData(0.1f)
    val wipeProgress: LiveData<Float> = _wipeProgress

    internal enum class WipeResult { REJECTED, FAILED, COMPLETED }

    internal fun wipe(
        wallet: Wallet,
        profile: WalletProfile,
        passPhrase: String,
        activity: MainActivity,
    ): Deferred<WipeResult> =
        viewModelScope.async(Dispatchers.IO) {
            try {
                withContext(NonCancellable) {
                    val manager = WalletManager.instance ?: return@withContext WipeResult.FAILED
                    _wipeProgressMessage.postValue(R.string.wiping_wallet)
                    if (!anonWalletHandler.wipe(wallet, profile, passPhrase)) {
                        _wipeProgressMessage.postValue(null)
                        return@withContext WipeResult.REJECTED
                    }

                    fun requireNoReplacement() {
                        check(manager.isManagedWalletIdle() && WalletSession.profile == profile) { "Wallet session changed during wipe" }
                    }
                    _wipeProgressMessage.postValue(R.string.wallet_cleared)
                    requireNoReplacement()
                    activity.stopNotificationService()
                    AnonConfig.context?.applicationContext?.let { SecureClipboard.clear(it) }
                    _wipeProgress.postValue(.6f)
                    _wipeProgressMessage.postValue(R.string.clearing_preferences)
                    // AnonWalletHandler.wipe removed only this profile's keys. Clearing the whole
                    // preference file here would delete the other profile's PIN gate and strand
                    // its wallet files, so only shared, non-credential state is cleared.
                    val cleared =
                        sharedPreferences.commitEdit {
                            remove("pin_attempts")
                            remove("pin_last_attempt")
                            // Network identity is shared by both profiles. Clear it on every
                            // wipe so the decoy cannot inherit the main node endpoint.
                            // Profile-specific LWS settings were removed with that profile.
                            remove(NodeFields.RPC_HOST.value)
                            remove(NodeFields.RPC_PORT.value)
                            remove(NodeFields.RPC_USERNAME.value)
                            remove(NodeFields.RPC_PASSWORD.value)
                            remove(NodeFields.RPC_NETWORK.value)
                            remove(NodeFields.NODE_NAME.value)
                            remove(NodeFields.NODE_TLS.value)
                            remove(io.anonero.network.PREFS_TRANSPORT_MODE)
                            remove(io.anonero.network.PREFS_TRANSPORT_HOST)
                            remove(io.anonero.network.PREFS_TRANSPORT_PORT)
                            remove(io.anonero.network.PREFS_TRANSPORT_NETWORK)
                            remove(io.anonero.network.PREFS_NODE_AUTO_SELECT)
                            remove(io.anonero.network.PREFS_NODE_PREFER_TRUSTED)
                            remove(WALLET_PROXY)
                            remove(WALLET_PROXY_PORT)
                        }
                    check(cleared) { "Could not clear shared preferences" }
                    requireNoReplacement()
                    _wipeProgress.postValue(.7f)
                    _wipeProgressMessage.postValue(R.string.clearing_nodes)
                    nodesRepository.clearAll()
                    requireNoReplacement()
                    _wipeProgressMessage.postValue(R.string.clearing_logs)
                    logRepository.clear()
                    _wipeProgressMessage.postValue(R.string.logs_cleared)
                    requireNoReplacement()
                    BackupHelper.cleanCacheDir()
                    AnonConfig.disposeState()
                    _wipeProgress.postValue(1f)
                    _wipeProgressMessage.postValue(R.string.wallet_wiped_successfully)
                    WipeResult.COMPLETED
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Timber.tag(TAG).e("Secure wipe failed")
                _wipeProgressMessage.postValue(R.string.wipe_failed)
                WipeResult.FAILED
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureWipe(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit = {},
    requestClearScreen: (bool: Boolean) -> Unit = {},
    goToHome: () -> Unit = {},
) {
    val secureWipeViewModel = koinViewModel<SecureWipeViewModel>()
    val focusRequester = remember { FocusRequester() }
    var passPhraseDialog by remember { mutableStateOf(true) }
    val passPhraseState = remember { TextFieldState() }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var wipeStarted by remember { mutableStateOf(false) }
    val errorShake = rememberShakeController()
    val scope = rememberCoroutineScope()
    val progressMessage by secureWipeViewModel.wipeProgressMessage.observeAsState(null)
    val wipeProgress by secureWipeViewModel.wipeProgress.observeAsState(.1f)
    val view = LocalView.current
    val activity = LocalActivity.current

    BackHandler(enabled = wipeStarted) {}

    val animatedProgress by animateFloatAsState(
        targetValue = wipeProgress,
        animationSpec =
            tween(
                durationMillis = 300, // animation duration
                delayMillis = 50, // delay before animation start
                easing = LinearOutSlowInEasing,
            ),
        label = "animatedProgress",
    )

    val backgroundBlur: Float by animateFloatAsState(
        if (passPhraseDialog) 6f else 0f,
        label = "blur-radius",
    )

    LaunchedEffect(passPhraseDialog, wipeStarted) {
        if (passPhraseDialog && !wipeStarted) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    fun clearWallet() {
        if (wipeStarted) return
        val mainActivity = activity as? MainActivity ?: return
        val wallet = WalletManager.instance?.wallet ?: return
        val profile = WalletSession.profile
        val candidate = passPhraseState.text.toString()
        wipeStarted = true
        errorRes = null
        requestClearScreen(false)
        scope.launch {
            try {
                when (secureWipeViewModel.wipe(wallet, profile, candidate, mainActivity).await()) {
                    SecureWipeViewModel.WipeResult.REJECTED -> {
                        errorShake.shake(ShakeConfig(6, translateX = 5f))
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    }

                    SecureWipeViewModel.WipeResult.FAILED -> {
                        passPhraseDialog = false
                        passPhraseState.clearText()
                        passPhraseState.undoState.clearHistory()
                        errorRes = R.string.wipe_failed
                    }

                    SecureWipeViewModel.WipeResult.COMPLETED -> {
                        passPhraseDialog = false
                        passPhraseState.clearText()
                        passPhraseState.undoState.clearHistory()
                        val idle = withContext(Dispatchers.IO) { WalletManager.instance?.isManagedWalletIdle() == true }
                        if (idle && WalletManager.instance?.wallet == null && WalletSession.profile == profile) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            goToHome()
                        } else {
                            errorRes = R.string.wipe_failed
                        }
                    }
                }
            } finally {
                wipeStarted = false
                requestClearScreen(true)
            }
        }
    }

    if (passPhraseDialog && !wipeStarted) {
        AlertDialog(
            modifier =
                Modifier
                    .shake(errorShake)
                    .border(
                        1.dp,
                        color =
                            MaterialTheme.colorScheme.onBackground.copy(
                                alpha = .2f,
                            ),
                        shape = MaterialTheme.shapes.medium,
                    ),
            containerColor = MaterialTheme.colorScheme.secondary,
            properties =
                DialogProperties(
                    securePolicy = SecureFlagPolicy.SecureOn,
                    dismissOnClickOutside = false,
                    dismissOnBackPress = false,
                ),
            title = {
                Column {
                    Text(
                        text = stringResource(id = R.string.securely_wipe_title),
                        style =
                            MaterialTheme.typography.titleSmall.copy(
                                fontSize = 18.sp,
                            ),
                    )
                    Spacer(Modifier.padding(12.dp))
                    Text(
                        text = stringResource(id = R.string.securely_wipe_warning),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(),
                    )
                    Spacer(Modifier.padding(8.dp))
                    Text(
                        text = stringResource(id = R.string.securely_wipe_passphrase_prompt),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Light,
                    )
                }
            },
            text = {
                OutlinedSecureTextField(
                    state = passPhraseState,
                    shape = MaterialTheme.shapes.small,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    textObfuscationMode = TextObfuscationMode.Hidden,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                        ),
                    onKeyboardAction = { },
                )
            },
            onDismissRequest = {
                passPhraseDialog = false
            },
            confirmButton = {
                Button(
                    shape = MaterialTheme.shapes.small,
                    border =
                        BorderStroke(
                            1.dp,
                            color = MaterialTheme.colorScheme.onError,
                        ),
                    colors =
                        ButtonDefaults.buttonColors(
                            contentColor = MaterialTheme.colorScheme.onBackground,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    onClick = {
                        clearWallet()
                    },
                ) { Text(stringResource(id = R.string.wipe)) }
            },
            dismissButton = {
                Button(
                    onClick = {
                        scope.launch {
                            passPhraseDialog = false
                            delay(300)
                            onBackPress.invoke()
                        }
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
                        stringResource(id = R.string.cancel),
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
        )
    }

    Scaffold(
        Modifier.blur(
            backgroundBlur.dp,
        ),
    ) {
        Column(
            Modifier
                .padding(it)
                .padding(0.dp)
                .fillMaxSize(),
            Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (progressMessage != null && errorRes == null) {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(320.dp),
                    )
                    Text(
                        progressMessage?.let { stringResource(it) } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        modifier =
                            Modifier.align(
                                Alignment.Center,
                            ),
                    )
                }
            }
            errorRes?.let { wipeError ->
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(wipeError),
                        style =
                            MaterialTheme.typography.labelSmall
                                .copy(
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                ),
                        textAlign = TextAlign.Justify,
                        modifier =
                            Modifier.align(
                                Alignment.Center,
                            ),
                    )
                }
            }
        }
    }
}

@Preview(device = "id:pixel_5")
@Composable
private fun SecureWipePrev() {
    AnonNeroTheme {
        SecureWipe()
    }
}
