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

package io.anonero.ui.home.sign

import AnonNeroTheme
import AnonOutlineButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import io.anonero.R
import io.anonero.model.WalletManager
import io.anonero.services.WalletState
import io.anonero.ui.home.LockScreen
import io.anonero.ui.home.LockScreenMode
import io.anonero.util.SecureClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private const val TAG = "SignVerify"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignVerifyScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit = {},
) {
    val wallet = WalletManager.instance?.wallet
    var message by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("") }
    var verifyAddress by remember { mutableStateOf(wallet?.address ?: "") }
    var verifyMessage by remember { mutableStateOf("") }
    var verifySignature by remember { mutableStateOf("") }
    var verifyResult by remember { mutableStateOf<Boolean?>(null) }
    var signing by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var confirmSign by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val walletState = koinInject<WalletState>()
    val locked by walletState.backgroundSyncFlow.collectAsState(walletState.backgroundSync)

    BackHandler { onBackPress() }

    LaunchedEffect(locked) {
        if (locked) confirmSign = false
    }

    fun doSign() {
        if (message.isEmpty() || signing) return
        signing = true
        errorRes = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    WalletManager.instance?.wallet?.signMessage(message)
                }
                if (result.isNullOrEmpty()) {
                    errorRes = R.string.signing_failed
                } else {
                    signature = result
                }
            } catch (e: Exception) {
                errorRes = R.string.signing_failed
            } finally {
                signing = false
            }
        }
    }

    fun doVerify() {
        if (verifyMessage.isEmpty() || verifyAddress.isEmpty() || verifySignature.isEmpty()) return
        verifying = true
        verifyResult = null
        errorRes = null
        scope.launch {
            try {
                verifyResult = withContext(Dispatchers.IO) {
                    WalletManager.instance?.wallet?.verifySignedMessage(
                        verifyMessage, verifyAddress, verifySignature
                    ) ?: false
                }
            } catch (e: Exception) {
                errorRes = R.string.signing_failed
            } finally {
                verifying = false
            }
        }
    }

    if (confirmSign) {
        Dialog(
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = true,
                dismissOnBackPress = true,
                decorFitsSystemWindows = false,
                securePolicy = SecureFlagPolicy.SecureOn
            ),
            onDismissRequest = { confirmSign = false }
        ) {
            LockScreen(
                mode = LockScreenMode.VERYFY_PIN,
                modifier = Modifier.fillMaxSize(),
                onUnLocked = { _, _ ->
                    confirmSign = false
                    doSign()
                }
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sign_verify_title)) },
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
                    stringResource(R.string.sign_verify_explanation),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Text(
                    stringResource(
                        R.string.label_value,
                        stringResource(R.string.primary_address),
                        wallet?.address ?: "-"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            item {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.message_to_sign)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                AnonOutlineButton(
                    enabled = message.isNotEmpty() && !signing,
                    onClick = { confirmSign = true }
                ) {
                    if (signing) CircularProgressIndicator() else Text(stringResource(R.string.sign))
                }
            }
            if (signature.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.signature), style = MaterialTheme.typography.titleSmall)
                    SelectionContainer {
                        Text(signature, fontFamily = FontFamily.Monospace)
                    }
                }
                item {
                    IconButton(onClick = {
                        SecureClipboard.copy(context, signature)
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_signature))
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.verify_signature),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            item {
                OutlinedTextField(
                    value = verifyAddress,
                    onValueChange = { verifyAddress = it.trim() },
                    label = { Text(stringResource(R.string.address)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = verifyMessage,
                    onValueChange = { verifyMessage = it },
                    label = { Text(stringResource(R.string.message)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = verifySignature,
                    onValueChange = { verifySignature = it.trim() },
                    label = { Text(stringResource(R.string.signature)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                AnonOutlineButton(
                    enabled = !verifying && verifyMessage.isNotEmpty() &&
                        verifyAddress.isNotEmpty() && verifySignature.isNotEmpty(),
                    onClick = { doVerify() }
                ) {
                    if (verifying) CircularProgressIndicator() else Text(stringResource(R.string.verify))
                }
            }
            if (verifyResult != null) {
                item {
                    Text(
                        if (verifyResult == true) stringResource(R.string.signature_valid)
                        else stringResource(R.string.signature_invalid),
                        color = if (verifyResult == true) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            errorRes?.let { messageRes ->
                item {
                    Text(
                        stringResource(messageRes),
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

@Preview(device = "id:pixel_5")
@Composable
private fun SignVerifyPreview() {
    AnonNeroTheme { SignVerifyScreen() }
}
