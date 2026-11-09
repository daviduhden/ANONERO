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
import android.content.SharedPreferences
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import io.anonero.AnonConfig
import io.anonero.R
import io.anonero.model.NeroKeyPayload
import io.anonero.model.WalletManager
import io.anonero.services.WalletState
import io.anonero.ui.components.qr.QrCodeColors
import io.anonero.ui.components.qr.QrCodeView
import io.anonero.util.KeyStoreHelper
import io.anonero.util.PREFS_PASSPHRASE_HASH
import io.anonero.util.WalletProfileStore
import io.anonero.util.ShakeConfig
import io.anonero.util.WALLET_PREFERENCES
import io.anonero.util.rememberShakeController
import io.anonero.util.shake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import kotlin.random.Random


fun getWalletSeed(passPhrase: String): String? {
    return WalletManager.instance?.wallet?.getSeed(passPhrase)
}

fun getLegacySeed(passPhrase: String): String? {
    return WalletManager.instance?.wallet?.getLegacySeed(passPhrase)
}

fun generatePlaceHolderSeed(): List<String> {
    return (1..16).map {
        (1..Random.nextInt(6, 8)).joinToString("") { "*" }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SeedSettingsPage(onBackPress: () -> Unit = {}) {

    val prefs = koinInject<SharedPreferences>(named(WALLET_PREFERENCES))
    val placeholderSeed = remember { generatePlaceHolderSeed() }

    var seedWords by remember {
        mutableStateOf(
            generatePlaceHolderSeed()
        )
    }
    var legacySeed by remember {
        mutableStateOf(
            generatePlaceHolderSeed()
        )
    }
    val focusRequester = remember { FocusRequester() }
    var passPhraseDialog by remember { mutableStateOf(true) }
    var passPhrase by remember { mutableStateOf("") }
    var neroPayload by remember { mutableStateOf("") }
    // nothing secret is composed until the passphrase checks out: the blur behind the
    // dialog is a no-op below Android 12
    var verified by remember { mutableStateOf(false) }
    var viewKeyText by remember { mutableStateOf("") }
    var spendKeyText by remember { mutableStateOf("") }
    var restoreHeightText by remember { mutableStateOf("") }

    val walletState = koinInject<WalletState>()
    val locked by walletState.backgroundSyncFlow.collectAsState(walletState.backgroundSync)
    val errorShake = rememberShakeController()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var seedJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(locked) {
        if (locked) {
            seedJob?.cancel()
            seedJob = null
            verified = false
            seedWords = placeholderSeed
            legacySeed = placeholderSeed
            passPhrase = ""
            neroPayload = ""
            viewKeyText = ""
            spendKeyText = ""
            restoreHeightText = ""
            // Dialogs have their own window and must not cover the PIN overlay.
            passPhraseDialog = false
        } else if (!verified) {
            passPhraseDialog = true
        }
    }

    LaunchedEffect(verified, locked) {
        if (!verified || locked) {
            viewKeyText = ""
            spendKeyText = ""
            restoreHeightText = ""
            return@LaunchedEffect
        }
        // Never read wallet2 keys during composition or on the main thread.
        val values = withContext(Dispatchers.IO) {
            val w = WalletManager.instance?.wallet
            if (w == null) {
                Triple("", "", "")
            } else {
                Triple(
                    runCatching { w.getSecretViewKey() }.getOrDefault(""),
                    runCatching { w.getSecretSpendKey() }.getOrDefault(""),
                    runCatching { w.getRestoreHeight().toString() }.getOrDefault(""),
                )
            }
        }
        viewKeyText = values.first
        spendKeyText = values.second
        restoreHeightText = values.third
    }

    val backgroundBlur: Float by animateFloatAsState(
        if (passPhraseDialog) 6f else 0f,
        label = "blur-radius"
    )


    fun exportForNero() {
        if (walletState.backgroundSync) return
        val wallet = WalletManager.instance?.wallet ?: return
        if (!verified) {
            passPhraseDialog = true
            return
        }
        val payload = Json.encodeToString(NeroKeyPayload.fromWallet(wallet))
        neroPayload = payload
    }


    LaunchedEffect(passPhraseDialog, locked) {
        if (passPhraseDialog && !locked) {
            delay(100)
            focusRequester.requestFocus()
        }
    }
    fun getSeed() {
        if (walletState.backgroundSync) return
        seedJob?.cancel()
        val enteredPassphrase = passPhrase
        seedJob = scope.launch {
            val seeds = withContext(Dispatchers.IO) {
                val hash = prefs.getString(WalletProfileStore.activePassphraseHashKey(), "")
                val appContext = AnonConfig.context?.applicationContext
                val hashedPass = appContext?.let {
                    KeyStoreHelper.getCrazyPass(it, enteredPassphrase)
                } ?: ""
                currentCoroutineContext().ensureActive()
                if (hash == hashedPass && !walletState.backgroundSync) {
                    val seed = getWalletSeed(enteredPassphrase)?.split(" ")
                    currentCoroutineContext().ensureActive()
                    val legacy = getLegacySeed(enteredPassphrase)?.split(" ")
                    seed to legacy
                } else null
            }
            currentCoroutineContext().ensureActive()
            if (walletState.backgroundSync) return@launch
            if (seeds != null) {
                val (seed, legacy) = seeds
                if (seed != null) {
                    seedWords = seed
                    verified = true
                    passPhraseDialog = false
                    passPhrase = ""
                }
                if (legacy != null) {
                    legacySeed = legacy
                }
            } else {
                errorShake.shake(
                    ShakeConfig(
                        6, translateX = 5f
                    )
                )
                repeat(6) {
                    delay(50)
                    view.performHapticFeedback(
                        HapticFeedbackConstants.CONTEXT_CLICK
                    )
                }
                delay(100)
            }

        }
    }
    if (neroPayload.isNotEmpty() && !locked) {
        ModalBottomSheet(
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
            ),
            scrimColor = MaterialTheme.colorScheme.background.copy(
                alpha = 0.5f
            ),
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = {
                neroPayload = ""
            }
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.padding(8.dp))
                Text(
                    stringResource(id = R.string.nero_keys),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
                Box(modifier = Modifier.padding(8.dp))
                QrCodeView(
                    data = neroPayload,
                    colors = QrCodeColors(
                        background = MaterialTheme.colorScheme.background,
                        foreground = MaterialTheme.colorScheme.onBackground,
                    ),
                    modifier = Modifier
                        .size(320.dp)
                        .padding(20.dp)
                )
                Box(modifier = Modifier.padding(16.dp))
            }
        }
    }


    if (passPhraseDialog && !locked)
        AlertDialog(
            modifier = Modifier
                .shake(errorShake)
                .border(
                    1.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(
                        alpha = .2f
                    ),
                    shape = MaterialTheme.shapes.medium,
                ),
            containerColor = MaterialTheme.colorScheme.secondary,
            properties = DialogProperties(
                securePolicy = SecureFlagPolicy.SecureOn,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = {
                Text(
                    text = stringResource(id = R.string.enter_seed_phrase),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 18.sp
                    )
                )
            },
            text = {
                OutlinedTextField(
                    value = passPhrase,
                    shape = MaterialTheme.shapes.small,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .padding(
                            top = 8.dp
                        ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            getSeed()
                        }
                    ),
                    onValueChange = {
                        passPhrase = it
                    },

                    )
            },
            onDismissRequest = {
                passPhraseDialog = false
            },
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
                    onClick = {
                        getSeed()
                    }) { Text(stringResource(id = R.string.view)) }
            },
            dismissButton = {
                Button(
                    onClick = {
                        passPhraseDialog = false
                        onBackPress.invoke()
                    },
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
                        stringResource(id = R.string.cancel),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(
                                alpha = 0.8f
                            )
                        )
                    )
                }
            }
        )

    var showLegacy by remember { mutableStateOf(false) }

    val wallet = WalletManager.instance?.wallet
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBackPress
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = {
                    Text("")
                }
            )
        },
    ) {
        Box(
            modifier = Modifier
                .padding(it)
                .blur(backgroundBlur.dp)
                .padding(
                    horizontal = 8.dp
                )
        ) {
            LazyColumn {
                item {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.primary_address),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        },
                        supportingContent = {
                            SelectionContainer {
                                Text(
                                    text = "${wallet?.address ?: ""}",
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    )
                }
                item {
                    HorizontalDivider(
                        thickness = 1.dp
                    )
                }
                item {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = if (showLegacy) stringResource(id = R.string.legacy_seed) else stringResource(id = R.string.polyseed),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .animateContentSize()
                            )
                        },
                        supportingContent = {
                            FlowRow(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .clickable {
                                        showLegacy = !showLegacy
                                    },
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                val seed = if (!verified || locked) placeholderSeed
                                    else if (showLegacy) legacySeed else seedWords
                                seed.forEach {
                                    Text(
                                        it, modifier = Modifier
                                            .padding(
                                                4.dp
                                            )
                                            .background(
                                                Color.Gray.copy(
                                                    alpha = 0.3f
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(
                                                vertical = 4.dp,
                                                horizontal = 8.dp
                                            )
                                    )
                                }
                            }
                        }

                    )
                }

                item {
                    HorizontalDivider(
                        thickness = 1.dp
                    )
                }
                item {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.view_key),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        },
                        supportingContent = {
                            // private keys are not selectable: a copy lands in the clipboard and its history
                            Text(
                                text = if (verified && !locked) viewKeyText else "••••••••",
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    )
                }
                item {
                    HorizontalDivider(
                        thickness = 1.dp
                    )
                }
                item {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.spend_key),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        },
                        supportingContent = {
                            Text(
                                text = if (verified && !locked) spendKeyText else "••••••••",
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    )
                }

                item {
                    HorizontalDivider(
                        thickness = 1.dp
                    )
                }
                item {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.restore_height),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        },
                        supportingContent = {
                            SelectionContainer {
                                Text(
                                    text = restoreHeightText,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    )
                }
                item {
                    HorizontalDivider(
                        thickness = 1.dp,
                        modifier = Modifier.padding(
                            bottom = 24.dp
                        )
                    )
                }
                item {
                    AnonOutlineButton(
                        modifier = Modifier
                            .padding(
                                horizontal = 8.dp,
                            )
                            .fillMaxWidth(),
                        onClick = {
                            exportForNero()
                        }) {
                        Text(
                            stringResource(id = R.string.export_nero_keys), style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onBackground.copy(
                                    alpha = 0.8f
                                )
                            )
                        )
                    }
                }
                item {
                    Box(
                        Modifier.height(
                            44.dp
                        )
                    )
                }
            }
        }
    }
}


@Preview(device = "id:pixel_5")
@Composable
private fun SeedSettingsPre() {
    AnonNeroTheme {
        SeedSettingsPage()
    }
}
