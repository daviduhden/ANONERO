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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGesturesPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.annotation.StringRes
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.dokar.sonner.ToastType
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import io.anonero.AnonConfig
import io.anonero.R
import io.anonero.model.WalletManager
import io.anonero.model.node.Node
import io.anonero.model.node.NodeFields
import io.anonero.services.AnonWalletHandler
import io.anonero.services.TorService
import io.anonero.services.WalletState
import io.anonero.store.NodesRepository
import io.anonero.ui.components.DaemonStatus
import io.anonero.ui.components.WalletProgressIndicator
import io.anonero.ui.theme.DangerColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

private const val TAG = "NodeSettings"

enum class NodeValidationError(@StringRes val messageRes: Int) {
    EMPTY_URL(R.string.url_cannot_be_empty),
    INVALID_URL(R.string.invalid_url),
    LWS_ACTIVE(R.string.disable_lws_restart),
    CONNECT_FAILED(R.string.connection_failed),
}

class NodeSettingsViewModel(
    private val prefs: SharedPreferences,
    private val nodesRepository: NodesRepository
) : ViewModel() {

    private val walletState: WalletState by inject(WalletState::class.java)

    private val torService: TorService by inject(TorService::class.java)
    private val walletHandler: AnonWalletHandler by inject(AnonWalletHandler::class.java)
    private val uriValidationError = MutableLiveData<NodeValidationError?>()
    private val _nodes = MutableStateFlow<List<Node>>(emptyList())
    private val daemonInfo = walletState.daemonInfo
    val nodes: StateFlow<List<Node>> = _nodes


    val activeNode: StateFlow<Node?> = combine(nodes, daemonInfo) { nodeList, daemonInfo ->
        if (daemonInfo?.daemon == null) {
            return@combine null
        }
        val uri = "http://${daemonInfo.daemon}".toUri()
        nodeList.find { node ->
            node.host == uri.host &&
                    node.rpcPort == uri.port
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = null
    )

    init {
        // Observe items from the repository
        viewModelScope.launch {
            nodesRepository.nodesFlow.collect { itemList ->
                _nodes.value = itemList
            }
        }
    }


    fun getCurrentDaemonLive() = walletState.daemonInfo.asLiveData()

    val connectionError: LiveData<NodeValidationError?> get() = uriValidationError

    fun validate(rpcUrl: String, rpcUsername: String, rpcPassPhrase: String): Node? {
        uriValidationError.postValue(null)
        if( rpcUrl.isBlank()) {
            uriValidationError.postValue(NodeValidationError.EMPTY_URL)
            return null
        }
        try {
            val cleanURL = rpcUrl.trim().lowercase()
            val urlForParsing = if (cleanURL.startsWith("http")) {
                cleanURL
            } else {
                if (cleanURL.startsWith("https")) {
                    cleanURL
                } else {
                    "http://$cleanURL"
                }
            }
            val validatedUrl = urlForParsing.toUri()
            if (validatedUrl.host == null) {
                uriValidationError.postValue(NodeValidationError.INVALID_URL)
                return null
            }
            val nodeJson = JSONObject()
                .apply {
                    put(NodeFields.RPC_HOST.value, validatedUrl.host)
                    put(
                        NodeFields.RPC_PORT.value,
                        if (validatedUrl.port == -1) Node.defaultRpcPort else validatedUrl.port
                    )
                    put(NodeFields.RPC_USERNAME.value, rpcUsername)
                    put(NodeFields.RPC_PASSWORD.value, rpcPassPhrase)
                    put(
                        NodeFields.RPC_NETWORK.value,
                        AnonConfig.getNetworkType().toString()
                    )
                    put(NodeFields.NODE_NAME.value, "anon")
                }

            return Node.fromJson(nodeJson)

        } catch (_: Exception) {
            Timber.tag(TAG).w("Invalid node configuration")
            uriValidationError.postValue(NodeValidationError.INVALID_URL)
            return null
        }
    }

    suspend fun connect(node: Node) {
        withContext(Dispatchers.IO) {
            val manager = WalletManager.instance
            val wallet = manager?.wallet
            if (prefs.getBoolean(io.anonero.util.PREFS_LWS_ENABLED, false) ||
                wallet?.isLightWallet == true
            ) {
                // Changing backends requires a restart; do not mutate the active LWS.
                uriValidationError.postValue(NodeValidationError.LWS_ACTIVE)
                return@withContext
            }
            try {
                checkNotNull(wallet) { "Wallet unavailable" }
                walletHandler.applyProxy()
                if (WalletManager.instance !== manager || manager?.wallet !== wallet) {
                    return@withContext
                }
                // Waiting for Tor can suspend while the user changes LWS settings.
                if (prefs.getBoolean(io.anonero.util.PREFS_LWS_ENABLED, false) || wallet.isLightWallet) {
                    uriValidationError.postValue(NodeValidationError.LWS_ACTIVE)
                    return@withContext
                }
                wallet.pauseRefresh()
                prefs.edit(commit = true) {
                    putString(NodeFields.RPC_HOST.value, node.host)
                    putInt(NodeFields.RPC_PORT.value, node.rpcPort)
                    putString(NodeFields.RPC_USERNAME.value, node.username)
                    putString(NodeFields.RPC_PASSWORD.value, node.password)
                }
                walletHandler.updateDaemon(node)
                check(wallet.init(0, lightWallet = false)) { "Node initialization failed" }
                if (prefs.getBoolean(io.anonero.util.PREFS_LWS_ENABLED, false) || wallet.isLightWallet) {
                    wallet.setTrustedDaemon(false)
                    wallet.pauseRefresh()
                    uriValidationError.postValue(NodeValidationError.LWS_ACTIVE)
                    return@withContext
                }
                wallet.setTrustedDaemon(true)
                wallet.startRefresh()
                walletState.setLoading(false)
                walletState.update()
                uriValidationError.postValue(null)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (WalletManager.instance !== manager || manager?.wallet !== wallet) {
                    return@withContext
                }
                wallet?.pauseRefresh()
                if (prefs.getBoolean(io.anonero.util.PREFS_LWS_ENABLED, false) || wallet?.isLightWallet == true) {
                    wallet?.setTrustedDaemon(false)
                }
                walletState.setConnectionStatus(io.anonero.model.Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
                walletState.setLoading(false)
                Timber.tag(TAG).w("Node connection failed")
                uriValidationError.postValue(NodeValidationError.CONNECT_FAILED)
            }
        }
    }

    // Add a new item
    fun addItem(item: Node) {
        viewModelScope.launch {
            nodesRepository.addItem(item)
        }
    }

    // Remove an item
    fun removeItem(nodeString: String) {
        viewModelScope.launch {
            nodesRepository.removeItemByNodeString(nodeString)
        }
    }

}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeSettings(onBackPress: () -> Unit = {}) {
    val nodeSettingsVM = koinViewModel<NodeSettingsViewModel>()
    var showNodeDetails by remember { mutableStateOf(false) }
    val walletState = koinInject<WalletState>()
    var showMenu by remember { mutableStateOf(false) }
    val availableNodes by nodeSettingsVM.nodes.collectAsState(arrayListOf())
    val activeNode by nodeSettingsVM.activeNode.collectAsState(null)
    val scope = rememberCoroutineScope()
    val toastState = rememberToasterState()
    val context = LocalContext.current

    if (showNodeDetails)
        Dialog(
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = true,
                dismissOnBackPress = true,
                decorFitsSystemWindows = false,
                securePolicy = SecureFlagPolicy.SecureOn,
            ),
            onDismissRequest = {
                showNodeDetails = false
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column {
                    TopAppBar(
                        title = {

                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    showNodeDetails = false
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                        }
                    )
                    NodeForm(
                        onBackPress = {
                            scope.launch {
                                showNodeDetails = false
                            }
                        }, onConnect = {
                            nodeSettingsVM.addItem(it)
                            nodeSettingsVM.viewModelScope
                                .launch {
                                    nodeSettingsVM.connect(it)
                                }
                        }, nodeSettingsVM
                    )
                }
            }
        }


    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = onBackPress
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                showNodeDetails = true
                            }
                        ) { Text(stringResource(R.string.add_node)) }
                        IconButton(
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onBackground
                            ),
                            onClick = {
                                showMenu = !showMenu
                            }
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                            DropdownMenu(
                                expanded = showMenu,
                                containerColor = MaterialTheme.colorScheme.background,
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(
                                            alpha = .2f
                                        ),
                                        shape = MaterialTheme.shapes.medium,
                                    ),
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.resync_blockchain)) },
                                    onClick = {
                                        showMenu = false
                                        val result = walletState.resyncBlockchain()
                                        if (result.isFailure) {
                                            scope.launch {
                                                toastState.show(
                                                    context.getString(R.string.resync_failed),
                                                    type = ToastType.Warning,
                                                    duration = 6.seconds
                                                )
                                            }
                                        } else {
                                            scope.launch {
                                                toastState.show(
                                                    context.getString(R.string.resync_initiated_this_may_take_a_while),
                                                    type = ToastType.Success,
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }

                    },
                    title = {
                        Text(stringResource(R.string.nodes))
                    },
                )
                WalletProgressIndicator()
            }
        }
    ) {

        LazyColumn(modifier = Modifier.padding(it)) {
            if (activeNode != null)
                item(key = activeNode?.toNodeString() ?: "active") {
                    NodeListItem(
                        activeNode!!,
                        modifier = Modifier.animateItem(),
                        active = true,
                        nodeSettingsVM = nodeSettingsVM)
                }
            if (availableNodes.isNotEmpty())
                item(key = "header") {
                    Column(
                        modifier = Modifier
                            .padding(
                                vertical = 12.dp,
                                horizontal = 12.dp
                            )
                            .background(color = MaterialTheme.colorScheme.background)
                    ) {
                        Text(stringResource(R.string.available_nodes), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.padding(4.dp))
                        HorizontalDivider()
                    }
                }
            if (availableNodes.isNotEmpty())
                items(
                    availableNodes.size,
                    key = { i -> "${availableNodes[i].toNodeString()} :${i}" }) { index ->
                    val node = availableNodes[index]
                    if (activeNode?.toNodeString() != node.toNodeString()) {
                        NodeListItem(
                            node,
                            modifier = Modifier.animateItem(),
                            active = false,
                            nodeSettingsVM = nodeSettingsVM,
                            onConnect = {
                                nodeSettingsVM.viewModelScope
                                    .launch {
                                        try {
                                            nodeSettingsVM.connect(node)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            Timber.tag(TAG).w("Node connection failed")
                                        }
                                    }
                            },
                            onRemove = { nodeSettingsVM.removeItem(node.toNodeString()) })
                    }
                }
        }
    }
    Toaster(
        state = toastState,
        maxVisibleToasts = 4,
        alignment = Alignment.BottomCenter,
        darkTheme = true,
        richColors = true,
        containerPadding = PaddingValues(
            bottom = 64.dp,
        ),
    )
}

@Composable
fun NodeListItem(
    node: Node,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    nodeSettingsVM: NodeSettingsViewModel,
    onConnect: (node: Node) -> Unit = {},
    onRemove: (node: Node) -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    val daemonStatus by nodeSettingsVM.getCurrentDaemonLive().observeAsState(null)

    ListItem(
        modifier = modifier
            .padding(
                vertical = 8.dp,
                horizontal = 8.dp
            )
            .border(
                1.dp,
                color = MaterialTheme.colorScheme.onBackground.copy(
                    alpha = .2f
                ),
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(enabled = !active) {
                menu = !menu
            },
        headlineContent = {
            Text(
                node.toNodeString(), style = MaterialTheme
                    .typography.bodySmall
            )
        },
        supportingContent = {
            if (active)
                Text(
                    stringResource(R.string.daemon_height, daemonStatus?.daemonHeight?.toString() ?: ""),
                    style = MaterialTheme
                        .typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (active) {
                    DaemonStatus(daemonStatus?.connectionStatus)
                } else {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Gray, shape = CircleShape)
                    )

                }
                DropdownMenu(
                    expanded = menu,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(
                        1.dp,
                        color = MaterialTheme.colorScheme.onBackground.copy(
                            alpha = .2f
                        ),
                    ),
                    containerColor = MaterialTheme.colorScheme.background,
                    onDismissRequest = { menu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.connect)) },
                        onClick = {
                            onConnect(node)
                            menu = false
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_node)) },
                        onClick = {
                            onRemove(node)
                            menu = false
                        },
                    )
                }
            }
        }
    )
}

@Composable
fun NodeForm(
    onBackPress: () -> Unit = {},
    onConnect: (node: Node) -> Unit = {},
    nodeSettingsVM: NodeSettingsViewModel
) {
    val connectionError by nodeSettingsVM.connectionError.observeAsState(null)
    var rpcHost by remember { mutableStateOf("") }
    var rpcUsername by remember { mutableStateOf("") }
    var rpcPassPhrase by remember { mutableStateOf("") }
    val labelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(8f)
                .verticalScroll(
                    rememberScrollState()
                )
            ,
            verticalArrangement = Arrangement.Top,
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.node_label),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                },
                supportingContent = {
                    OutlinedTextField(
                        value = rpcHost,
                        shape = MaterialTheme.shapes.medium,
                        onValueChange = {
                            rpcHost = it
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next,
                        ),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.onion_address_example),
                                color = labelColor
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusable()
                    )
                },
            )
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.username),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                },
                supportingContent = {
                    OutlinedTextField(
                        value = rpcUsername,
                        shape = MaterialTheme.shapes.medium,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.optional),
                                color = labelColor
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next,
                        ),
                        onValueChange = {
                            rpcUsername = it
                        },

                        )
                },
            )
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.password),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                },
                supportingContent = {
                    OutlinedTextField(
                        value = rpcPassPhrase,
                        shape = MaterialTheme.shapes.medium,
                        onValueChange = {
                            rpcPassPhrase = it
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                        ),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.optional),
                                color = labelColor
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                connectionError?.let { validationError ->
                    Text(
                        stringResource(validationError.messageRes),
                        modifier = Modifier.padding(
                            vertical = 12.dp,
                            horizontal = 8.dp
                        ),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = DangerColor
                        )
                    )
                }

            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.5f)
                .padding(bottom = 8.dp)
            ,
            contentAlignment = Alignment.Center
        ) {
            AnonOutlineButton(
                modifier = Modifier
                    .fillMaxWidth(
                        .9f
                    ),
                onClick = {
                    val node = nodeSettingsVM.validate(rpcHost, rpcUsername, rpcPassPhrase)
                    if (node != null) {
                        onConnect(node)
                        onBackPress.invoke()
                    }
                },
            ) {
                Text(stringResource(R.string.connect))
            }
        }
    }
}


@Preview(device = "id:pixel_5")
@Composable
private fun SeedSettingsPre() {
    AnonNeroTheme {
        NodeSettings()
    }
}
