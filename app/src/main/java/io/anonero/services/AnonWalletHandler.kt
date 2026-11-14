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

package io.anonero.services

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import io.anonero.AnonConfig
import io.anonero.model.Wallet
import io.anonero.model.WalletManager
import io.anonero.model.node.DaemonInfo
import io.anonero.model.node.Node
import io.anonero.model.node.NodeFields
import io.anonero.network.NodeSelectionPolicy
import io.anonero.network.ProxyEndpoint
import io.anonero.network.TransportController
import io.anonero.network.TransportEndpoint
import io.anonero.network.TransportMode
import io.anonero.store.NodesRepository
import io.anonero.util.LwsConfig
import io.anonero.util.PREFS_PIN_HASH
import io.anonero.util.PinHash
import io.anonero.util.WALLET_PROXY
import io.anonero.util.WALLET_PROXY_PORT
import io.anonero.util.WalletKey
import io.anonero.util.WalletProfile
import io.anonero.util.WalletProfileStore
import io.anonero.util.WalletSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.json.JSONObject
import timber.log.Timber
import java.io.File

class InvalidPin : Exception("invalid pin")

private const val TAG = "AnonWalletHandler"

class UnableToCloseWallet : Exception()

class UnableToWipeWallet : Exception()

/**
 * Coordinates wallet profile selection and the managed wallet's lifecycle.
 *
 * This class is the boundary between UI intent and wallet2: it resolves a PIN to a
 * [WalletProfile], opens the corresponding wallet (or the decoy), applies the proxy,
 * configures the daemon and LWS, starts refresh/background sync, and wipes the active
 * profile. It deliberately keeps the live wallet in [WalletManager] rather than owning a
 * second handle.
 *
 * See the repository `AGENTS.md` for the security invariants. The important local rules
 * are: fail closed on a missing Keystore secret, never cross profiles, and never
 * initialize/connect a wallet without applying its network policy first.
 */
class AnonWalletHandler(
    private val prefs: SharedPreferences,
    private val walletState: WalletState,
    private val torService: TorService,
    private val transportController: TransportController,
    private val nodesRepository: NodesRepository,
) {
    // startService/reapply perform blocking wallet2, file, and proxy operations. Use the
    // IO pool, not Default, so they cannot starve CPU-bound coroutines.
    private val _scope = CoroutineScope(Dispatchers.IO) + SupervisorJob()

    private var handler: MoneroHandlerThread? = null

    val scope get() = _scope

    init {
        scope.launch {
            torService.socksFlow.collect { socket ->
                if (!transportController.config.value.mode.usesEmbeddedTor) return@collect
                val wallet = WalletManager.instance?.wallet
                if (socket == null) {
                    // Tor restarted or stopped. Never reuse the old port; pause until a
                    // fresh endpoint is published.
                    if (wallet?.isInitialized == true) {
                        wallet.pauseRefresh()
                        walletState.setConnectionStatus(
                            Wallet.ConnectionStatus.ConnectionStatus_Disconnected,
                        )
                        walletState.setLoading(false)
                    }
                } else if (wallet?.isInitialized == true) {
                    try {
                        applyEndpoint(
                            TransportEndpoint.Socks5(
                                ProxyEndpoint(socket.address.value, socket.port.value),
                            ),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        wallet.pauseRefresh()
                        walletState.setConnectionStatus(
                            Wallet.ConnectionStatus.ConnectionStatus_Disconnected,
                        )
                        walletState.setLoading(false)
                        Timber.tag(TAG).e("Could not update Tor proxy; refresh paused")
                    }
                }
            }
        }
        scope.launch {
            transportController.state.collect { state ->
                if (state.mode != io.anonero.network.TransportMode.SYSTEM_VPN) return@collect
                if (state.status != io.anonero.network.TransportStatus.UNAVAILABLE) return@collect
                val wallet = WalletManager.instance?.wallet ?: return@collect
                wallet.pauseRefresh()
                wallet.setOffline(true)
                // Drop the live HTTP client connection so an in-flight request cannot
                // finish over the default network after the VPN is gone.
                runCatching { wallet.setProxy("") }
                walletState.setOffline(true)
                walletState.setConnectionStatus(
                    Wallet.ConnectionStatus.ConnectionStatus_Disconnected,
                )
                walletState.setLoading(false)
            }
        }
    }

    fun openWallet(pin: String): Boolean {
        val context = AnonConfig.context!!
        val profile = WalletProfileStore.resolvePin(context, prefs, pin) ?: throw InvalidPin()
        val walletFile = WalletProfileStore.walletFile(context, profile)
        val walletName = walletFile.name

        // Opening any profile must not leave a previous wallet, its listener, or its keys
        // referenced. This also covers a retry after a failed open: the failed wallet may
        // still be managed with a non-OK status, and replacing it would leak its native
        // handle. A close failure is fatal; do not leave two profiles reachable.
        val current = WalletManager.instance?.wallet
        if (current != null) {
            val closed =
                runCatching {
                    current.pauseRefresh()
                    current.close()
                }.onFailure { Timber.tag(TAG).e(it, "could not close previous wallet") }
                    .getOrDefault(false)
            if (!closed) {
                Timber.tag(TAG).e("could not close previous wallet")
                throw InvalidPin()
            }
            WalletSession.reset()
        }

        val anonWallet =
            if (WalletKey.exists(context, walletName)) {
                // Keystore-bound: the PIN never encrypts the wallet, it gates access to the
                // wrapped secret. Verify it in software, then open with that secret.
                val key =
                    try {
                        WalletKey.load(context, walletName)
                    } catch (e: Exception) {
                        // key gone (device change, Keystore reset) -- the wallet cannot be opened
                        // on this device; the seed/backup is the only recovery
                        Timber.tag(TAG).e(e, "wallet key unavailable")
                        throw InvalidPin()
                    }
                WalletManager.instance?.openWallet(walletFile.path, key)
            } else if (profile == WalletProfile.MAIN) {
                // Legacy PIN-encrypted wallet: opening with the PIN verifies it. On success,
                // migrate it to a Keystore-bound key. The decoy is only ever created through
                // the Keystore path and must never fall back to a legacy open.
                val opened = WalletManager.instance?.openWallet(walletFile.path, pin)
                if (opened?.status?.isOk == true) {
                    migrateToKeystore(walletFile, walletName, opened, pin)
                }
                opened
            } else {
                Timber.tag(TAG).e("decoy wallet key unavailable")
                throw InvalidPin()
            }

        if (anonWallet?.status?.isOk != true) {
            val error = anonWallet?.status?.errorString
            runCatching { anonWallet?.close() }
                .onFailure { Timber.tag(TAG).e(it, "could not close failed wallet") }
            Timber.tag(TAG).e("openWallet error: %s", error)
            throw InvalidPin()
        }
        WalletSession.select(profile)
        return true
    }

    /*
     * One-time migration of a legacy PIN-encrypted wallet to a Keystore-wrapped random
     * key. Snapshot the keys file, re-encrypt with the new secret, wrap it, then drop the
     * snapshot. Any failure rolls back to the snapshot and leaves the wallet
     * PIN-encrypted -- still openable. A crash between re-encrypt and wrap is caught at
     * startup by WalletKey.recoverInterruptedMigration. The seed is the ultimate net.
     */
    private fun migrateToKeystore(
        walletFile: File,
        walletName: String,
        wallet: Wallet,
        pin: String,
    ) {
        val context = AnonConfig.context!!
        // wallet2 keys the cache with the password as well as the keys file, so both are
        // snapshotted and both are restored together
        val snapshots = WalletKey.migrationSnapshots(walletFile.path)
        var rekeyed = false
        try {
            snapshots.forEach { (snapshot, live) ->
                // copy to a temp file, then rename it to the snapshot
                val partial = File(snapshot.path + ".tmp")
                live.copyTo(partial, overwrite = true)
                if (!partial.renameTo(snapshot)) throw Exception("snapshot failed: ${snapshot.name}")
            }
            val key = WalletKey.newSecret()
            rekeyed = true // from here the files on disk may already be keyed to `key`
            if (!wallet.setPassword(key)) {
                throw Exception("setPassword failed: ${wallet.status.errorString}")
            }
            if (!wallet.store()) {
                throw Exception("store failed after re-key")
            }
            WalletKey.store(context, walletName, key)
            // the gate must match this exact PIN, which just opened the wallet; always
            // rewrite it rather than trusting whatever was there
            val committed =
                prefs
                    .edit()
                    .putString(PREFS_PIN_HASH, PinHash.hash(pin))
                    .commit()
            if (!committed) throw Exception("could not persist the migrated PIN hash")
            snapshots.forEach { (snapshot, _) -> snapshot.delete() }
            Timber.tag(TAG).i("wallet migrated to a keystore-bound key")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "keystore migration failed -- rolling back")
            WalletKey.remove(context, walletName)
            // Put the open wallet back on the PIN first: it keeps storing on every refresh,
            // and a store keyed to the abandoned secret would undo a file-level rollback.
            // That re-writes keys and cache from memory, current and PIN-keyed.
            val restoredInMemory =
                !rekeyed ||
                    runCatching { wallet.setPassword(pin) && wallet.store() }.getOrDefault(false)
            if (!restoredInMemory) {
                runCatching {
                    snapshots.forEach { (snapshot, live) ->
                        if (snapshot.exists()) snapshot.copyTo(live, overwrite = true)
                    }
                }
                Timber.tag(TAG).e("could not re-key the open wallet to the PIN; restored snapshot, restart required")
            }
            snapshots.forEach { (snapshot, _) -> snapshot.delete() }
        }
    }

    suspend fun startService() {
        val manager = WalletManager.instance ?: return
        val wallet = manager.wallet ?: return
        handler =
            MoneroHandlerThread(
                wallet,
                walletState,
            ) {
                // Never reconnect directly after losing the only clearnet transport, and
                // never re-init through a stale embedded-Tor port.
                when (transportController.config.value.mode) {
                    TransportMode.SYSTEM_VPN -> transportController.isVpnActive()
                    TransportMode.EMBEDDED_TOR -> torService.socks != null
                    else -> true
                }
            }

        wallet.setListener(handler)
        wallet.refreshHistory()
        handler?.start()

        val lws = LwsConfig.load(prefs)
        val lwsActive = lws.enabled
        val host = if (lwsActive) lws.host else prefs.getString(NodeFields.RPC_HOST.value, "")
        val rpcPort = if (lwsActive) lws.port else prefs.getInt(NodeFields.RPC_PORT.value, Node.defaultRpcPort)
        val rpcUsername = if (lwsActive) "" else prefs.getString(NodeFields.RPC_USERNAME.value, "")
        val rpcPassphrase = if (lwsActive) "" else prefs.getString(NodeFields.RPC_PASSWORD.value, "")
        val offline = host.isNullOrEmpty()
        walletState.setOffline(offline)
        wallet.setOffline(offline)
        // nothing to load without a node/LWS
        if (!host.isNullOrEmpty()) walletState.setLoading(true)
        walletState.update()
        try {
            if (lwsActive) {
                wallet.setTrustedDaemon(false)
                // The LWS receives the view key. Its destination must match the
                // selected transport network and clearnet servers must use TLS unless
                // they are local. This is the same fail-closed policy as RPC.
                val transport = transportController.config.value
                val compatibility = LwsConfig.validateTransport(lws, transport)
                if (compatibility != null) throw Exception(compatibility.technicalMessage)
                val validation = LwsConfig.validate(lws.host, lws.port, lws.useTls)
                if (validation != null) throw Exception(validation.technicalMessage)
            }
            // no node (offline signer): nothing to route, leave the transport off
            if (host?.isNotEmpty() == true) applyProxy()
            // A profile switch or close can replace this session while Tor is starting.
            if (WalletManager.instance !== manager || manager.wallet !== wallet) return
            if (host?.isNotEmpty() == true) {
                var node =
                    nodeFromPrefs(
                        host,
                        rpcPort,
                        rpcUsername,
                        rpcPassphrase,
                        lwsActive,
                        if (lwsActive) lws.useTls else prefs.getBoolean(NodeFields.NODE_TLS.value, false),
                    )
                if (!lwsActive && node != null &&
                    !NodeSelectionPolicy.accepts(transportController.config.value, node)
                ) {
                    val replacement =
                        if (transportController.config.value.autoSelectNode) {
                            NodeSelectionPolicy.select(
                                nodesRepository.getAll(),
                                transportController.config.value,
                                AnonConfig.getNetworkType(),
                            )
                        } else {
                            null
                        }
                    if (replacement == null) {
                        throw Exception("No compatible node for the selected transport")
                    }
                    persistNode(replacement)
                    node = replacement
                }
                updateDaemon(node, lightWallet = lwsActive)
                walletState.setLoading(true)
            }
            walletState.update()
            if (wallet.isSynchronized) {
                wallet.refreshHistory()
            }
            check(wallet.init(0, lightWallet = lwsActive)) { "Wallet initialization failed" }
            if (!lwsActive && prefs.getLong(
                    WalletProfileStore.restoreHeightKey(WalletSession.profile),
                    0L,
                ) != 0L
            ) {
                wallet.setRestoreHeight(
                    prefs.getLong(WalletProfileStore.restoreHeightKey(WalletSession.profile), 0L),
                )
            }
            if (wallet.isInitialized) {
                if (lwsActive) {
                    // Keep the remote index untrusted even when login fails or throws.
                    wallet.setTrustedDaemon(false)
                    // Registers the public address + view key with the LWS and asks it to
                    // create the account if needed. wallet2 sends only these two values.
                    if (!wallet.lightWalletLogin()) {
                        throw Exception("LWS login failed")
                    }
                    // A light server is a remote index, not a trusted daemon.
                    wallet.setTrustedDaemon(false)
                } else {
                    wallet.setTrustedDaemon(true)
                }
                wallet.refreshHistory()
                wallet.startRefresh()
                walletState.update()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (WalletManager.instance !== manager || manager.wallet !== wallet) return
            wallet.pauseRefresh()
            wallet.setOffline(true)
            if (lwsActive) wallet.setTrustedDaemon(false)
            walletState.setOffline(true)
            walletState.setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
            Timber.tag(TAG).e("Wallet service start failed")
            walletState.setLoading(false)
        }
    }

    /*
     * Applies the endpoint selected by TransportController to the manager and live wallet.
     * Call it before the wallet first talks to a node/LWS, or that traffic goes out
     * unproxied.
     */
    suspend fun applyProxy() {
        val manager = checkNotNull(WalletManager.instance) { "Wallet manager unavailable" }
        val wallet = manager.wallet
        val endpoint = transportController.authorize()
        if (WalletManager.instance !== manager || manager.wallet !== wallet) {
            throw CancellationException("Wallet session changed while acquiring transport")
        }
        applyEndpoint(endpoint)
    }

    /**
     * Re-applies the selected transport to an active LWS backend. This is required after
     * a transport change: the old HTTP client must not keep using the previous proxy.
     * The caller marks the wallet offline if this throws.
     */
    suspend fun reapplyLightWallet() {
        val manager = checkNotNull(WalletManager.instance) { "Wallet manager unavailable" }
        val wallet = manager.wallet ?: return
        val lws = LwsConfig.load(prefs)
        if (!lws.enabled) throw IllegalStateException("LWS is not enabled")
        val compatibility = LwsConfig.validateTransport(lws, transportController.config.value)
        if (compatibility != null) throw IllegalStateException(compatibility.technicalMessage)
        val validation = LwsConfig.validate(lws.host, lws.port, lws.useTls)
        if (validation != null) throw IllegalStateException(validation.technicalMessage)
        wallet.pauseRefresh()
        wallet.setTrustedDaemon(false)
        applyProxy()
        val node =
            nodeFromPrefs(
                lws.host,
                lws.port,
                "",
                "",
                lws = true,
                useTls = lws.useTls,
            ) ?: throw IllegalStateException("Invalid LWS endpoint")
        updateDaemon(node, lightWallet = true)
        check(wallet.init(0, lightWallet = true)) { "LWS initialization failed" }
        wallet.setTrustedDaemon(false)
        check(wallet.lightWalletLogin()) { "LWS login failed" }
        wallet.setTrustedDaemon(false)
        wallet.setOffline(false)
        wallet.startRefresh()
    }

    private fun applyEndpoint(endpoint: TransportEndpoint) {
        val manager = checkNotNull(WalletManager.instance) { "Wallet manager unavailable" }
        val wallet = manager.wallet
        val previous = manager.proxy
        try {
            when (endpoint) {
                is TransportEndpoint.Socks5 -> {
                    val address = endpoint.endpoint.formatted
                    check(manager.setProxy(address)) { "Transport proxy was rejected" }
                    if (wallet != null && !wallet.setProxy(address)) {
                        throw IllegalStateException("Wallet proxy was rejected")
                    }
                }

                TransportEndpoint.Direct -> {
                    check(manager.clearProxy()) { "Clearing the proxy was rejected" }
                    if (wallet != null && !wallet.clearProxy()) {
                        throw IllegalStateException("Clearing the wallet proxy was rejected")
                    }
                }
            }
        } catch (e: Exception) {
            // Do not leave the manager on a half-applied endpoint.
            if (previous.isNotEmpty()) manager.setProxy(previous) else manager.clearProxy()
            throw e
        }
    }

    private fun nodeFromPrefs(
        host: String,
        rpcPort: Int,
        username: String,
        password: String,
        lws: Boolean,
        useTls: Boolean,
    ): Node? =
        Node.fromJson(
            JSONObject()
                .apply {
                    put(NodeFields.RPC_HOST.value, host)
                    put(NodeFields.NODE_TLS.value, useTls)
                    put(NodeFields.RPC_PORT.value, rpcPort)
                    put(NodeFields.RPC_USERNAME.value, username)
                    put(NodeFields.RPC_PASSWORD.value, password)
                    put(NodeFields.RPC_NETWORK.value, AnonConfig.getNetworkType().toString())
                    put(NodeFields.NODE_NAME.value, if (lws) "lws" else "anon")
                },
        )

    private fun persistNode(node: Node) {
        prefs.edit(commit = true) {
            putString(NodeFields.RPC_HOST.value, node.host)
            putBoolean(NodeFields.NODE_TLS.value, node.useTls)
            putInt(NodeFields.RPC_PORT.value, node.rpcPort)
            putString(NodeFields.RPC_USERNAME.value, node.username)
            putString(NodeFields.RPC_PASSWORD.value, node.password)
            putString(NodeFields.RPC_NETWORK.value, AnonConfig.getNetworkType().toString())
            putString(NodeFields.NODE_NAME.value, node.name)
        }
    }

    fun updateDaemon(
        node: Node?,
        lightWallet: Boolean = WalletManager.instance?.wallet?.isLightWallet == true,
    ) {
        walletState.setLoading(true)
        val wallet = WalletManager.instance?.wallet
        val walletManager = WalletManager.instance
        wallet?.pauseRefresh()
        // Callers state the backend explicitly: startup applies the pending mode before
        // wallet.init(), and a pending enable/disable must not change trust or skip the
        // node-network check for the current RPC wallet.
        val lwsActive = lightWallet
        try {
            if (!lwsActive && node != null) {
                require(NodeSelectionPolicy.accepts(transportController.config.value, node)) {
                    "Node network does not match the selected transport"
                }
            }
            if (transportController.config.value.mode == io.anonero.network.TransportMode.SYSTEM_VPN &&
                !transportController.isVpnActive()
            ) {
                throw IllegalStateException("System VPN is not active")
            }
            if (lwsActive) wallet?.setTrustedDaemon(false)
            if (node != null) {
                walletState.setOffline(false)
                wallet?.setOffline(false)
                walletState.updateDaemon(
                    DaemonInfo(
                        daemon = node.hostPort,
                        Wallet.ConnectionStatus.ConnectionStatus_Disconnected,
                        daemonHeight = 0L,
                    ),
                )
            } else {
                walletState.updateDaemon(
                    DaemonInfo(
                        daemon = null,
                        Wallet.ConnectionStatus.ConnectionStatus_Disconnected,
                        daemonHeight = 0L,
                    ),
                )
            }
            checkNotNull(walletManager) { "Wallet manager unavailable" }.setDaemon(node)
            wallet?.setTrustedDaemon(!lwsActive)
            walletState.setLoading(false)
        } catch (e: Exception) {
            wallet?.pauseRefresh()
            wallet?.setOffline(true)
            walletState.setOffline(true)
            walletState.updateDaemon(
                DaemonInfo(
                    daemon = null,
                    Wallet.ConnectionStatus.ConnectionStatus_Disconnected,
                    daemonHeight = 0L,
                ),
            )
            walletState.setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
            if (lwsActive) wallet?.setTrustedDaemon(false)
            walletState.setLoading(false)
            throw e
        }
    }

    private fun proxyAddress(
        host: String,
        port: Int,
    ): String {
        require(host.isNotBlank() && port in 1..65535) { "Invalid proxy endpoint" }
        val formattedHost = if (':' in host && !host.startsWith("[")) "[$host]" else host
        return "socks5://$formattedHost:$port"
    }

    fun setProxy(
        proxy: String?,
        port: Int?,
        persist: Boolean = true,
    ) {
        Timber.tag(TAG).d("setProxy: updating endpoint")
        // LWS traffic must follow the selected transport. The legacy proxy screen must not
        // be able to install or clear a second proxy that bypasses TransportController.
        val lwsActive = LwsConfig.load(prefs).enabled || WalletManager.instance?.wallet?.isLightWallet == true
        check(!lwsActive) { "LWS proxy is managed by Network & Nodes" }
        // Disable the proxy in both native clients, not only in preferences.
        if (proxy == null && port == null) {
            val manager = checkNotNull(WalletManager.instance) { "Wallet manager unavailable" }
            check(manager.clearProxy()) { "Clearing the proxy was rejected" }
            manager.wallet?.clearProxy()
            if (persist) {
                prefs.edit(commit = true) {
                    remove(WALLET_PROXY)
                    remove(WALLET_PROXY_PORT)
                }
            }
        } else {
            val proxyHost = requireNotNull(proxy) { "Proxy host required" }
            val proxyPort = requireNotNull(port) { "Proxy port required" }
            val proxyStr = proxyAddress(proxyHost, proxyPort)
            val manager = checkNotNull(WalletManager.instance) { "Wallet manager unavailable" }
            check(manager.setProxy(proxyStr)) { "Proxy was rejected" }
            val wallet = manager.wallet
            if (wallet != null && !wallet.setProxy(proxyStr)) {
                wallet.pauseRefresh()
                throw IllegalStateException("Wallet proxy was rejected")
            }
            prefs.edit(commit = true) {
                if (persist) {
                    putString(WALLET_PROXY, proxyHost)
                    putInt(WALLET_PROXY_PORT, proxyPort)
                }
            }
        }
    }

    fun getProxy(): Pair<String, Int>? {
        val proxyHost = prefs.getString(WALLET_PROXY, "")
        val proxyPort = prefs.getInt(WALLET_PROXY_PORT, -1)

        if (proxyHost?.isNotEmpty() == true && proxyPort != -1) {
            return Pair(proxyHost, proxyPort)
        }
        return null
    }

    fun wipe(passPhrase: String): Boolean {
        val context = AnonConfig.context ?: throw UnableToWipeWallet()
        val manager = WalletManager.instance ?: throw UnableToWipeWallet()
        val wallet = manager.wallet ?: throw UnableToWipeWallet()
        val walletName = WalletProfileStore.activeWalletName(context)
        val password =
            try {
                if (WalletKey.exists(context, walletName)) {
                    WalletKey.load(context, walletName)
                } else {
                    passPhrase
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "wipe: wallet password unavailable")
                throw UnableToWipeWallet()
            }
        wallet.pauseRefresh()
        if (walletState.backgroundSyncRunning && !wallet.stopBackgroundSync(password)) {
            // Do not remove profile data while the spend key may still be live.
            Timber.tag(TAG).e("wipe: stopBackgroundSync failed")
            throw UnableToWipeWallet()
        }
        manager.setDaemon(null)
        if (!wallet.close()) {
            throw UnableToCloseWallet()
        }
        // Remove only the active profile. Wiping from the decoy must not destroy the
        // legitimate wallet's files, keys, or PIN. A failed removal is not success.
        if (!WalletProfileStore.removeProfile(context, prefs, WalletSession.profile)) {
            throw UnableToWipeWallet()
        }
        return true
    }
}
