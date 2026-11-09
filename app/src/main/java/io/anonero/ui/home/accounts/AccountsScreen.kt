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

package io.anonero.ui.home.accounts

import AnonNeroTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.anonero.R
import io.anonero.model.AccountInfo
import io.anonero.services.WalletState
import io.anonero.util.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import timber.log.Timber

private const val TAG = "AccountsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit = {},
) {
    val walletState = koinInject<WalletState>()
    val accounts by walletState.accounts.collectAsState(emptyList())
    val active by walletState.activeAccount.collectAsState(0)
    val hideAmounts by walletState.hideAmountsFlow.collectAsState(false)
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AccountInfo?>(null) }
    val locked by walletState.backgroundSyncFlow.collectAsState(walletState.backgroundSync)

    BackHandler { onBackPress() }

    LaunchedEffect(locked) {
        if (locked) {
            showAdd = false
            renameTarget = null
        }
    }

    if (showAdd) {
        AccountLabelDialog(
            title = stringResource(R.string.new_account),
            initial = "",
            onDismiss = { showAdd = false },
            onConfirm = { label ->
                showAdd = false
                scope.launch(Dispatchers.IO) {
                    if (!walletState.addAccount(label)) {
                        Timber.tag(TAG).e("could not create account")
                    }
                }
            }
        )
    }
    renameTarget?.let { target ->
        AccountLabelDialog(
            title = stringResource(R.string.rename_account),
            initial = target.label,
            onDismiss = { renameTarget = null },
            onConfirm = { label ->
                renameTarget = null
                scope.launch(Dispatchers.IO) {
                    walletState.updateAccountLabel(target.index, label)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts)) },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_account))
            }
        }
    ) { padding ->
        if (accounts.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(R.string.no_accounts),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(accounts.size) { index ->
                val account = accounts[index]
                ListItem(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable {
                            scope.launch(Dispatchers.IO) {
                                walletState.selectAccount(account.index)
                            }
                        },
                    leadingContent = {
                        if (account.index == active) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.selected),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    headlineContent = {
                        Text(
                            account.label,
                            fontWeight = if (account.index == active) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                account.address,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                            Text(
                                stringResource(
                                    R.string.available_balance_amount,
                                    if (hideAmounts) Formats.maskAmount(account.balance)
                                    else Formats.getDisplayAmount(account.balance)
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (hideAmounts) Formats.maskAmount(account.balance) else Formats.getDisplayAmount(account.balance),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = { renameTarget = account }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename_account))
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AccountLabelDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.length <= 32) value = it },
                singleLine = true,
                label = { Text(stringResource(R.string.account_label)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = { onConfirm(value.trim()) }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Preview(device = "id:pixel_5")
@Composable
private fun AccountsPreview() {
    AnonNeroTheme { AccountsScreen() }
}
