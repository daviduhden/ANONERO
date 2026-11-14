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
import AnonOutlineButton
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.anonero.AnonConfig
import io.anonero.R
import io.anonero.model.PendingTransaction
import io.anonero.model.Wallet
import io.anonero.model.WalletManager
import io.anonero.network.TransportController
import io.anonero.services.WalletState
import io.anonero.ui.home.graph.routes.ReviewTransactionRoute
import io.anonero.util.AddressValidationSnapshot
import io.anonero.util.Formats
import io.anonero.util.MoneroAmount
import io.anonero.util.MultiPaymentPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File

private const val TAG = "PayToMany"

/*
 * Stable error identifiers so the UI can localize failures. The wallet2 error text is
 * still logged for diagnosis, but it is never shown to the user untranslated.
 */
enum class PayToManyError(
    @StringRes val messageRes: Int,
) {
    VIEW_ONLY(R.string.pay_to_many_view_only),
    WALLET_NOT_OPEN(R.string.wallet_not_open),
    NOT_CONNECTED(R.string.wallet_not_connected_to_node_please_check_network),
    EXPORT_FAILED(R.string.could_not_export_unsigned),
    CONSTRUCTION_FAILED(R.string.transaction_construction_failed),
}

data class RecipientInput(
    val address: String = "",
    val amount: String = "",
)

/*
 * Validates an exact XMR decimal string and converts it to atomic units. Floating point
 * is never used: amounts in Monero must be exact to 10^-12 XMR.
 */
fun parseExactAmount(text: String): Long? = MoneroAmount.parseExact(text)

class PayToManyViewModel : ViewModel() {
    private val transportController: TransportController by inject(TransportController::class.java)
    private val walletState: WalletState by inject(WalletState::class.java)
    private val _error = MutableStateFlow<PayToManyError?>(null)
    val error: StateFlow<PayToManyError?> = _error
    private val _preparing = MutableStateFlow(false)
    val preparing: StateFlow<Boolean> = _preparing

    suspend fun prepare(recipients: List<Pair<String, Long>>): PendingTransaction? =
        withContext(Dispatchers.IO) {
            if (!_preparing.compareAndSet(false, true)) return@withContext null
            val request = recipients.toList()
            _error.value = null
            try {
                if (AnonConfig.viewOnly) {
                    _error.value = PayToManyError.VIEW_ONLY
                    return@withContext null
                }
                if (MultiPaymentPolicy.totalOrNull(request) == null) {
                    _error.value = PayToManyError.CONSTRUCTION_FAILED
                    return@withContext null
                }
                val transport = transportController.config.value
                if (transport.validationError() != null) {
                    _error.value = PayToManyError.NOT_CONNECTED
                    return@withContext null
                }
                if (transport.mode == io.anonero.network.TransportMode.SYSTEM_VPN &&
                    !transportController.isVpnActive()
                ) {
                    _error.value = PayToManyError.NOT_CONNECTED
                    return@withContext null
                }
                val wallet =
                    WalletManager.instance?.wallet
                        ?: run {
                            _error.value = PayToManyError.WALLET_NOT_OPEN
                            return@withContext null
                        }
                if (wallet.fullStatus.connectionStatus != Wallet.ConnectionStatus.ConnectionStatus_Connected) {
                    _error.value = PayToManyError.NOT_CONNECTED
                    return@withContext null
                }
                if ((walletState.backgroundSync || walletState.backgroundSyncRunning) ||
                    request.any { !wallet.validateAddress(it.first) }
                ) {
                    _error.value = PayToManyError.CONSTRUCTION_FAILED
                    return@withContext null
                }
                val pending =
                    wallet.createTransactionMultDest(
                        destinations = request.map { it.first },
                        amounts = request.map { it.second },
                    )
                if (!pending.getErrorString().isNullOrEmpty()) {
                    throw Exception(pending.getErrorString())
                }
                val unsignedFile =
                    File(
                        AnonConfig.context?.cacheDir,
                        AnonConfig.EXPORT_UNSIGNED_TX_FILE,
                    )
                if (!pending.commit(unsignedFile.path, true)) {
                    Timber.tag(TAG).e("unsigned export failed: %s", pending.getErrorString())
                    _error.value = PayToManyError.EXPORT_FAILED
                    return@withContext null
                }
                if (WalletManager.instance?.wallet !== wallet || (walletState.backgroundSync || walletState.backgroundSyncRunning)) {
                    _error.value = PayToManyError.WALLET_NOT_OPEN
                    return@withContext null
                }
                pending
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Timber.tag(TAG).e(e)
                _error.value = PayToManyError.CONSTRUCTION_FAILED
                null
            } finally {
                _preparing.value = false
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayToManyScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit = {},
    navigateToReview: (ReviewTransactionRoute) -> Unit = {},
) {
    val walletState = koinInject<WalletState>()
    val viewModel = viewModel<PayToManyViewModel>()
    val scope = rememberCoroutineScope()
    val error by viewModel.error.collectAsState()
    val preparing by viewModel.preparing.collectAsState()
    val unlockedBalanceState by walletState.unLockedBalance.collectAsState(null)
    val unlockedBalance = unlockedBalanceState ?: 0L
    val hideAmounts by walletState.hideAmountsFlow.collectAsState(false)
    val recipients = remember { mutableStateListOf(RecipientInput(), RecipientInput()) }
    var submitted by remember { mutableStateOf(false) }

    BackHandler { onBackPress() }

    val inputSnapshot = recipients.toList()
    val addresses = inputSnapshot.map { it.address }
    val parsed = inputSnapshot.map { parseExactAmount(it.amount) }
    val validation by produceState<AddressValidationSnapshot?>(initialValue = null, addresses) {
        val checked =
            withContext(Dispatchers.IO) {
                val wallet = WalletManager.instance?.wallet
                addresses.map { address -> address.isNotBlank() && wallet?.validateAddress(address) == true }
            }
        value = AddressValidationSnapshot(addresses, checked)
    }
    val amountsValid = parsed.all { it != null }
    val request = inputSnapshot.mapIndexed { index, recipient -> recipient.address to (parsed[index] ?: 0L) }
    val total = MultiPaymentPolicy.totalOrNull(request)
    val valid = validation?.allValidFor(addresses) == true && amountsValid && total != null && total <= unlockedBalance

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pay_to_many)) },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.a11y_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.pay_to_many_description),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (AnonConfig.viewOnly) {
                    Text(
                        stringResource(R.string.pay_to_many_view_only),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(recipients.size) { index ->
                val recipient = recipients[index]
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.pay_to_many_recipient, index + 1),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (recipients.size > 1) {
                            IconButton(enabled = !preparing && !submitted, onClick = { recipients.removeAt(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_recipient))
                            }
                        }
                    }
                    OutlinedTextField(
                        enabled = !preparing && !submitted,
                        value = recipient.address,
                        onValueChange = {
                            recipients[index] = recipient.copy(address = it.trim())
                        },
                        label = { Text(stringResource(R.string.address)) },
                        isError = recipient.address.isNotBlank() && validation?.isValidAt(addresses, index) != true,
                        supportingText = {
                            if (recipient.address.isNotBlank() && validation?.isValidAt(addresses, index) != true) {
                                Text(stringResource(R.string.invalid_address))
                            }
                        },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        enabled = !preparing && !submitted,
                        value = recipient.amount,
                        onValueChange = {
                            recipients[index] = recipient.copy(amount = it.trim())
                        },
                        label = { Text(stringResource(R.string.amount)) },
                        isError = recipient.amount.isNotBlank() && parsed[index] == null,
                        supportingText = {
                            if (recipient.amount.isNotBlank() && parsed[index] == null) {
                                Text(stringResource(R.string.positive_amount))
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Next,
                                keyboardType = KeyboardType.Decimal,
                            ),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.total), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (hideAmounts) Formats.maskAmount(total ?: 0L) else Formats.getExactDisplayAmount(total ?: 0L),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                Text(
                    "${stringResource(R.string.available_balance)} ${
                        if (hideAmounts) {
                            Formats.maskAmount(unlockedBalance)
                        } else {
                            Formats.getDisplayAmount(unlockedBalance)
                        }
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                if (total != null && total > unlockedBalance) {
                    Text(
                        stringResource(R.string.total_exceeds_balance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (error != null) {
                item {
                    Text(
                        error?.let { stringResource(it.messageRes) } ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        enabled = !preparing && !submitted && recipients.size < MultiPaymentPolicy.MAX_RECIPIENTS,
                        onClick = {
                            if (recipients.size < MultiPaymentPolicy.MAX_RECIPIENTS) recipients.add(RecipientInput())
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_recipient))
                    }
                    AnonOutlineButton(
                        enabled = valid && !preparing && !submitted && !AnonConfig.viewOnly,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (!valid) return@AnonOutlineButton
                            val clickInputs = recipients.toList()
                            val clickAddresses = clickInputs.map { it.address }
                            if (validation?.allValidFor(clickAddresses) != true) return@AnonOutlineButton
                            val destinations =
                                clickInputs.map { recipient ->
                                    recipient.address to (parseExactAmount(recipient.amount) ?: return@AnonOutlineButton)
                                }
                            val clickTotal = MultiPaymentPolicy.totalOrNull(destinations) ?: return@AnonOutlineButton
                            if (clickTotal > unlockedBalance) return@AnonOutlineButton
                            submitted = true
                            scope.launch {
                                val pending =
                                    try {
                                        viewModel.prepare(destinations)
                                    } finally {
                                        submitted = false
                                    }
                                if (pending != null) {
                                    navigateToReview(
                                        ReviewTransactionRoute(
                                            toAddress = "",
                                            addresses = destinations.map { it.first },
                                            amounts = destinations.map { it.second },
                                        ),
                                    )
                                }
                            }
                        },
                    ) {
                        if (preparing) {
                            CircularProgressIndicator(modifier = Modifier.width(24.dp))
                        } else {
                            Text(stringResource(R.string.next))
                        }
                    }
                }
            }
        }
    }
}

@Preview(device = "id:pixel_5")
@Composable
private fun PayToManyPreview() {
    AnonNeroTheme { PayToManyScreen() }
}
