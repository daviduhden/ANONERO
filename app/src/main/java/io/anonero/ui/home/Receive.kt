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
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.anonero.R
import io.anonero.icons.AnonIcons
import io.anonero.model.Subaddress
import io.anonero.services.WalletState
import io.anonero.ui.components.SubAddressLabelDialog
import io.anonero.ui.components.qr.QrCodeColors
import io.anonero.ui.components.qr.QrCodeView
import io.anonero.util.SecureClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit = {},
    navigateToSubAddresses: () -> Unit = {},
) {
    val walletState: WalletState by inject(WalletState::class.java)
    val account by walletState.receiveAccount.collectAsState(null)
    val locked by walletState.backgroundSyncFlow.collectAsState(walletState.backgroundSync)
    val owner = account.takeUnless { locked }
    var address by remember(owner) { mutableStateOf<Subaddress?>(null) }
    var labelDialog by remember(owner) { mutableStateOf(false) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(owner) {
        if (owner != null) {
            address = withContext(Dispatchers.IO) { walletState.issueReceiveAddress(owner) }
            if (address == null) Toast.makeText(context, R.string.wallet_operation_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val issued = address
    if (labelDialog && issued != null) {
        SubAddressLabelDialog(
            label = issued.displayLabel,
            onSave = { label ->
                labelDialog = false
                scope.launch {
                    if (withContext(Dispatchers.IO) { walletState.updateAddressLabel(label, issued) }) {
                        address = Subaddress(issued.accountIndex, issued.addressIndex, issued.address, label)
                    }
                }
            },
            onCancel = {
                labelDialog = false
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackPress,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = navigateToSubAddresses,
                    ) {
                        Icon(AnonIcons.History, contentDescription = null)
                    }
                },
            )
        },
    ) {
        if (issued != null) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize(),
                contentPadding = it,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                item {
                    Text(
                        text = issued.displayLabel,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    labelDialog = true
                                },
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                }
                item {
                    Spacer(Modifier.height(24.dp))
                }
                item {
                    QrCodeView(
                        data = issued.address,
                        colors =
                            QrCodeColors(
                                background = MaterialTheme.colorScheme.background,
                                foreground = MaterialTheme.colorScheme.onBackground,
                            ),
                        modifier =
                            Modifier
                                .size(300.dp)
                                .padding(18.dp),
                    )
                }
                item {
                    SelectionContainer(
                        modifier =
                            Modifier.clickable {
                                SecureClipboard.copy(context, issued.address)
                                Toast
                                    .makeText(
                                        context,
                                        resources.getString(R.string.copied_to_clipboard),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            },
                    ) {
                        Text(
                            issued.address,
                            modifier = Modifier.width(300.dp),
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Preview(device = "id:pixel")
@Composable
private fun ReceiveScreenPreview() {
    AnonNeroTheme {
        ReceiveScreen()
    }
}
