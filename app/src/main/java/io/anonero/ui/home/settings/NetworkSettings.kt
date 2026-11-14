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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.anonero.R
import io.anonero.model.Wallet
import io.anonero.model.WalletManager
import io.anonero.model.node.Node
import io.anonero.model.node.NodeFields
import io.anonero.network.NodeNetwork
import io.anonero.network.NodeSelectionPolicy
import io.anonero.network.TransportConfig
import io.anonero.network.TransportController
import io.anonero.network.TransportFailure
import io.anonero.network.TransportMode
import io.anonero.network.TransportState
import io.anonero.network.TransportStatus
import io.anonero.services.AnonWalletHandler
import io.anonero.services.SyncProgress
import io.anonero.services.WalletState
import io.anonero.store.NodesRepository
import io.anonero.ui.components.SettingsSectionHeader
import io.anonero.ui.components.SettingsSwitchItem
import io.anonero.util.LwsConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import java.io.IOException

enum class NetworkSettingsError(
    @StringRes val messageRes: Int,
) {
    INVALID_CONFIG(R.string.network_error_config),
    APPLY_FAILED(R.string.network_error_apply),
    NO_COMPATIBLE_NODE(R.string.network_error_no_node),
    VPN_INACTIVE(R.string.network_error_vpn),
    I2P_APP_MISSING(R.string.network_error_i2p_app),
    LWS_INCOMPATIBLE(R.string.lws_transport_incompatible),
    LWS_RESTART_REQUIRED(R.string.network_lws_restart_required),
    LWS_ACTIVE(R.string.disable_lws_restart),
}

class NetworkSettingsViewModel(
    private val prefs: SharedPreferences,
    private val transportController: TransportController,
    private val nodesRepository: NodesRepository,
    private val walletState: WalletState,
    private val walletHandler: AnonWalletHandler,
) : ViewModel() {
    private val _nodes = MutableStateFlow<List<Node>>(emptyList())
    val nodes: StateFlow<List<Node>> = _nodes

    val config: StateFlow<TransportConfig> = transportController.config
    val transportState: StateFlow<TransportState> = transportController.state
    val walletConnection: StateFlow<Wallet.ConnectionStatus?> =
        walletState.connectionStatus.stateIn(viewModelScope, SharingStarted.Lazily, null)
    val syncProgress: StateFlow<SyncProgress?> =
        walletState.syncProgress.stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val daemonInfo = walletState.daemonInfo
    val activeNode: StateFlow<Node?> =
        combine(_nodes, daemonInfo) { nodes, daemon ->
            val address = daemon?.daemon ?: return@combine null
            nodes.firstOrNull { it.hostPort == address || "${it.host}:${it.rpcPort}" == address }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _error = MutableLiveData<NetworkSettingsError?>()
    val error: LiveData<NetworkSettingsError?> get() = _error

    private val _busy = MutableLiveData(false)
    val busy: LiveData<Boolean> get() = _busy

    init {
        viewModelScope.launch {
            nodesRepository.nodesFlow.collect { _nodes.value = it }
        }
    }

    fun save(config: TransportConfig) {
        if (config.validationError() != null) {
            _error.postValue(NetworkSettingsError.INVALID_CONFIG)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _busy.postValue(true)
            _error.postValue(null)
            val manager = WalletManager.instance
            val wallet = manager?.wallet
            try {
                wallet?.pauseRefresh()
                wallet?.setOffline(true)
                transportController.saveConfig(config).getOrThrow()
                val lws = LwsConfig.load(prefs)
                val lwsActive = lws.enabled && wallet?.isLightWallet == true
                val lwsPendingDisable = !lws.enabled && wallet?.isLightWallet == true
                // The transport is now saved. If the active LWS endpoint belongs to a
                // different network, keep the wallet offline instead of reusing the old
                // transport or connecting directly.
                if (lwsActive && LwsConfig.validateTransport(lws, config) != null) {
                    wallet?.pauseRefresh()
                    wallet?.setOffline(true)
                    walletState.setOffline(true)
                    walletState.setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
                    walletState.setLoading(false)
                    _error.postValue(NetworkSettingsError.LWS_INCOMPATIBLE)
                    return@launch
                }
                // Do not switch an active light wallet back to RPC implicitly. The LWS
                // toggle takes effect on the next wallet start.
                if (lwsPendingDisable) {
                    wallet?.pauseRefresh()
                    wallet?.setOffline(true)
                    walletState.setOffline(true)
                    walletState.setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
                    walletState.setLoading(false)
                    _error.postValue(NetworkSettingsError.LWS_RESTART_REQUIRED)
                    return@launch
                }
                if (wallet != null) {
                    if (lwsActive) {
                        walletHandler.reapplyLightWallet()
                    } else {
                        walletHandler.applyProxy()
                        val selected =
                            NodeSelectionPolicy.select(
                                _nodes.value,
                                config,
                                io.anonero.AnonConfig.getNetworkType(),
                                activeNode.value,
                            )
                        if (selected == null) {
                            throw IllegalStateException("No compatible node")
                        }
                        walletHandler.updateDaemon(selected, lightWallet = false)
                        wallet.setOffline(false)
                        if (wallet.isInitialized) wallet.startRefresh()
                    }
                }
                walletState.setLoading(false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                wallet?.pauseRefresh()
                wallet?.setOffline(true)
                walletState.setOffline(true)
                walletState.setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
                walletState.setLoading(false)
                Timber.tag("NetworkSettings").w(e, "Transport change failed")
                _error.postValue(errorFor(e))
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun selectNode(node: Node) {
        // An active light wallet must not be pointed at a full node. The LWS toggle takes
        // effect on restart, so refuse instead of changing trust or the backend.
        if (LwsConfig.load(prefs).enabled && WalletManager.instance?.wallet?.isLightWallet == true) {
            _error.postValue(NetworkSettingsError.LWS_ACTIVE)
            return
        }
        if (!NodeSelectionPolicy.accepts(config.value, node)) {
            _error.postValue(NetworkSettingsError.NO_COMPATIBLE_NODE)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _busy.postValue(true)
            try {
                walletHandler.updateDaemon(node, lightWallet = false)
                prefs.edit(commit = true) {
                    putString(NodeFields.RPC_HOST.value, node.host)
                    putBoolean(NodeFields.NODE_TLS.value, node.useTls)
                    putInt(NodeFields.RPC_PORT.value, node.rpcPort)
                    putString(NodeFields.RPC_USERNAME.value, node.username)
                    putString(NodeFields.RPC_PASSWORD.value, node.password)
                    putString(
                        NodeFields.RPC_NETWORK.value,
                        io.anonero.AnonConfig
                            .getNetworkType()
                            .toString(),
                    )
                    putString(NodeFields.NODE_NAME.value, node.name)
                }
                WalletManager.instance?.wallet?.startRefresh()
                _error.postValue(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("NetworkSettings").w(e, "Node selection failed")
                _error.postValue(NetworkSettingsError.APPLY_FAILED)
            } finally {
                _busy.postValue(false)
            }
        }
    }

    fun savePreferences(config: TransportConfig) {
        // Preference writes use commit=true; keep them off the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            transportController.saveConfig(config)
        }
    }

    fun isI2pJavaInstalled(): Boolean = transportController.isI2pJavaInstalled()

    fun lwsConfig(): LwsConfig = LwsConfig.load(prefs)

    private fun errorFor(e: Exception): NetworkSettingsError =
        when (e) {
            is io.anonero.network.TransportUnavailableException -> {
                when (e.failure) {
                    TransportFailure.VPN_INACTIVE -> NetworkSettingsError.VPN_INACTIVE
                    TransportFailure.I2P_APP_MISSING -> NetworkSettingsError.I2P_APP_MISSING
                    else -> NetworkSettingsError.APPLY_FAILED
                }
            }

            is IOException -> {
                NetworkSettingsError.APPLY_FAILED
            }

            else -> {
                NetworkSettingsError.APPLY_FAILED
            }
        }
}

private val TransportMode.labelRes: Int
    get() =
        when (this) {
            TransportMode.EMBEDDED_TOR -> R.string.network_embedded_tor
            TransportMode.ORBOT -> R.string.network_orbot
            TransportMode.INVIZIBLE_TOR -> R.string.network_invizible_tor
            TransportMode.SYSTEM_VPN -> R.string.network_system_vpn
            TransportMode.I2PD -> R.string.network_i2pd
            TransportMode.INVIZIBLE_I2P -> R.string.network_invizible_i2p
            TransportMode.I2P_JAVA -> R.string.network_i2p_java
            TransportMode.CUSTOM_PROXY -> R.string.network_custom_proxy
        }

private fun TransportStatus.labelRes(): Int =
    when (this) {
        TransportStatus.STOPPED -> R.string.network_status_stopped
        TransportStatus.READY -> R.string.network_status_ready
        TransportStatus.UNAVAILABLE -> R.string.network_status_unavailable
        TransportStatus.ERROR -> R.string.network_status_error
    }

private fun NodeNetwork.labelRes(): Int =
    when (this) {
        NodeNetwork.CLEARNET -> R.string.network_clearnet
        NodeNetwork.ONION -> R.string.network_onion
        NodeNetwork.I2P -> R.string.network_i2p
    }

private fun io.anonero.util.LwsValidationError.messageResForUi(): Int =
    when (this) {
        io.anonero.util.LwsValidationError.HOST_EMPTY -> R.string.lws_error_host_empty
        io.anonero.util.LwsValidationError.HOST_TOO_LONG -> R.string.lws_error_host_too_long
        io.anonero.util.LwsValidationError.HOST_HAS_SCHEME_OR_PORT -> R.string.lws_error_host_format
        io.anonero.util.LwsValidationError.HOST_INVALID -> R.string.lws_error_host_invalid
        io.anonero.util.LwsValidationError.PORT_RANGE -> R.string.lws_error_port_range
        io.anonero.util.LwsValidationError.NETWORK_MISMATCH -> R.string.lws_network_mismatch
        io.anonero.util.LwsValidationError.CLEARNET_REQUIRES_TLS -> R.string.lws_clearnet_requires_tls
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettings(
    onBackPress: () -> Unit,
    onManageNodes: () -> Unit,
    onManageLws: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<NetworkSettingsViewModel>()
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val transport by viewModel.transportState.collectAsState()
    val walletConnection by viewModel.walletConnection.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val nodes by viewModel.nodes.collectAsState()
    val activeNode by viewModel.activeNode.collectAsState()
    val error by viewModel.error.observeAsState(null)
    val busy by viewModel.busy.observeAsState(false)
    var selectedMode by remember(config.mode) { mutableStateOf(config.mode) }
    var host by remember(config.proxyHost) { mutableStateOf(config.proxyHost) }
    var port by remember(config.proxyPort) { mutableStateOf(config.proxyPort.takeIf { it > 0 }?.toString() ?: "") }
    var customNetwork by remember(config.customNodeNetwork) { mutableStateOf(config.customNodeNetwork) }
    var autoSelect by remember(config.autoSelectNode) { mutableStateOf(config.autoSelectNode) }
    var preferTrusted by remember(config.preferTrustedNode) { mutableStateOf(config.preferTrustedNode) }
    val i2pJavaInstalled = viewModel.isI2pJavaInstalled()
    val lws = viewModel.lwsConfig()
    val lwsCompatibility =
        if (lws.enabled) LwsConfig.validateTransport(lws, config) else null

    Scaffold(
        modifier = modifier.systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_network_nodes)) },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(R.string.network_status),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${stringResource(transport.mode.labelRes)} · ${stringResource(transport.status.labelRes())}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    activeNode?.let {
                        Text(
                            text = stringResource(R.string.network_active_node, it.name ?: it.hostPort),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = stringResource(R.string.network_node_network, stringResource(config.nodeNetwork.labelRes())),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text =
                            stringResource(
                                if (lws.enabled) R.string.network_lws_status else R.string.network_rpc_status,
                                if (walletConnection == Wallet.ConnectionStatus.ConnectionStatus_Connected) {
                                    stringResource(R.string.network_rpc_connected)
                                } else {
                                    stringResource(R.string.disconnected)
                                },
                            ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    error?.let {
                        Text(
                            text = stringResource(it.messageRes),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item { HorizontalDivider() }
            item { SettingsSectionHeader(stringResource(R.string.network_transport)) }
            items(TransportMode.entries.toList()) { mode ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selectedMode = mode }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(stringResource(mode.labelRes), style = MaterialTheme.typography.bodyLarge)
                    if (mode == selectedMode) {
                        Text(
                            text = "✓",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        viewModel.save(
                            config.copy(
                                mode = selectedMode,
                                proxyHost = host,
                                proxyPort = port.toIntOrNull() ?: 0,
                                customNodeNetwork = customNetwork,
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.network_apply))
                }
            }
            if (selectedMode.requiresProxy && !selectedMode.usesEmbeddedTor) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text(stringResource(R.string.network_proxy_host)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter(Char::isDigit) },
                            label = { Text(stringResource(R.string.network_proxy_port)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (selectedMode == TransportMode.CUSTOM_PROXY) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NodeNetwork.entries.forEach { network ->
                                    FilterChip(
                                        selected = customNetwork == network,
                                        onClick = { customNetwork = network },
                                        label = { Text(stringResource(network.labelRes())) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (selectedMode == TransportMode.SYSTEM_VPN) {
                item {
                    Text(
                        text = stringResource(R.string.network_warning_vpn_clearnet),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (transport.status == TransportStatus.UNAVAILABLE) {
                    item {
                        Text(
                            text = stringResource(R.string.network_warning_vpn),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            if (selectedMode.isI2p) {
                item {
                    Text(
                        text = stringResource(R.string.network_warning_i2p),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            if (selectedMode == TransportMode.I2P_JAVA) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.network_i2p_java_instructions))
                        if (!i2pJavaInstalled) {
                            Text(
                                text = stringResource(R.string.network_i2p_java_missing),
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(
                                onClick = {
                                    val uri = Uri.parse("https://geti2p.net/en/download")
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }.onFailure { e ->
                                        if (e is ActivityNotFoundException) {
                                            Timber.tag("NetworkSettings").w(e, "No browser for I2P download")
                                        }
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.network_i2p_java_open))
                            }
                        }
                    }
                }
            }
            if (selectedMode == TransportMode.CUSTOM_PROXY) {
                item {
                    Text(
                        text = stringResource(R.string.network_warning_custom),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item { HorizontalDivider() }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.network_auto_select),
                    checked = autoSelect,
                    onCheckedChange = {
                        autoSelect = it
                        viewModel.savePreferences(config.copy(autoSelectNode = it))
                    },
                )
            }
            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.network_prefer_trusted),
                    checked = preferTrusted,
                    onCheckedChange = {
                        preferTrusted = it
                        viewModel.savePreferences(config.copy(preferTrustedNode = it))
                    },
                )
            }
            item { SettingsSectionHeader(stringResource(R.string.network_lws)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text =
                            stringResource(
                                if (lws.enabled) R.string.network_lws_enabled else R.string.network_lws_disabled,
                            ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (lws.enabled) {
                        Text(lws.address, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = stringResource(R.string.network_node_network, stringResource(lws.network.labelRes())),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (lwsCompatibility != null) {
                            Text(
                                text = stringResource(lwsCompatibility.messageResForUi()),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        syncProgress?.let {
                            Text(
                                text = stringResource(R.string.syncing_blocks_left, it.left.toString()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.lws_privacy_warning),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onManageLws) {
                        Text(stringResource(R.string.network_lws_configure))
                    }
                }
            }
            item { SettingsSectionHeader(stringResource(R.string.network_nodes)) }
            val visibleNodes = nodes.filter { it.network == config.nodeNetwork }
            if (visibleNodes.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.network_no_nodes),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(visibleNodes) { node ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) { viewModel.selectNode(node) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = node.name ?: node.hostPort,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${node.hostPort} · ${stringResource(node.network.labelRes())}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (node.trusted) {
                        Text(stringResource(R.string.network_trusted), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                TextButton(onClick = onManageNodes) {
                    Text(stringResource(R.string.network_manage_nodes))
                }
            }
        }
    }
}
