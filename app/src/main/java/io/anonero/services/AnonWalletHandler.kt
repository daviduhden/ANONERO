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
import androidx.core.content.edit
import io.anonero.AnonConfig
import io.anonero.model.Wallet
import io.anonero.model.WalletManager
import io.anonero.model.node.DaemonInfo
import io.anonero.model.node.Node
import io.anonero.model.node.NodeFields
import io.anonero.network.NodeSelectionPolicy
import io.anonero.network.TransportAuthorization
import io.anonero.network.TransportController
import io.anonero.network.TransportEndpoint
import io.anonero.store.NodesRepository
import io.anonero.util.KeyStoreHelper
import io.anonero.util.LwsConfig
import io.anonero.util.PREFS_PIN_HASH
import io.anonero.util.PinHash
import io.anonero.util.WalletKey
import io.anonero.util.WalletProfile
import io.anonero.util.WalletProfileStore
import io.anonero.util.WalletSession
import io.anonero.util.commitEdit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Network transitions may take transportApplyMutex while holding walletNetworkMutex.
    // Provider-loss observers take only transportApplyMutex.
    private val transportApplyMutex = Mutex()
    private val walletNetworkMutex = Mutex()

    private data class AppliedTransport(
        val wallet: Wallet,
        val profile: WalletProfile,
        val authorization: TransportAuthorization,
    )

    @Volatile
    private var appliedTransport: AppliedTransport? = null

    val scope get() = _scope

    init {
        walletState.isTransportApplied = ::hasAppliedTransport
        scope.launch {
            torService.socksFlow.collect {
                if (transportController.config.value.mode.usesEmbeddedTor) pauseAfterTransportLoss()
            }
        }
        scope.launch {
            transportController.state.collect { pauseAfterTransportLoss() }
        }
    }

    private suspend fun pauseAfterTransportLoss() {
        transportApplyMutex.withLock {
            val applied = appliedTransport ?: return
            if (transportController.isCurrent(applied.authorization)) return
            appliedTransport = null
            val wallet = applied.wallet
            if (WalletManager.instance?.wallet !== wallet || WalletSession.profile != applied.profile) return
            wallet.pauseRefresh()
            wallet.setOffline(true)
            walletState.setOffline(true)
            walletState.setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
            walletState.setLoading(false)
        }
    }

    private fun hasAppliedTransport(wallet: Wallet): Boolean {
        if (walletState.backgroundSyncFaulted) return false
        val applied = appliedTransport ?: return false
        return applied.wallet === wallet && WalletManager.instance?.wallet === wallet &&
            WalletSession.profile == applied.profile && transportController.isCurrent(applied.authorization)
    }

    /** Reject new network operations when the wallet has no current applied endpoint. */
    fun requireAppliedTransport(wallet: Wallet) {
        check(!walletState.offline) { "Wallet transport is unavailable" }
        requireAppliedProxy(wallet)
    }

    private fun requireAppliedProxy(wallet: Wallet) {
        check(hasAppliedTransport(wallet)) { "Wallet transport is unavailable" }
    }

    private fun activateAppliedTransport(wallet: Wallet) {
        requireAppliedProxy(wallet)
        walletState.setOffline(false)
        requireAppliedTransport(wallet)
    }

    @Synchronized
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

        handler = null
        appliedTransport = null
        walletState.resetSession(profile)

        if (profile == WalletProfile.MAIN) {
            val recovered =
                WalletManager.instance?.withWalletPathClosed(walletFile.path) {
                    WalletKey.recoverInterruptedMigration(context, walletFile.path, walletName)
                    true
                }
            if (recovered != true) throw InvalidPin()
        }
        val bound = WalletKey.exists(context, walletName)
        val anonWallet =
            if (bound) {
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
                WalletManager.instance?.openWallet(walletFile.path, pin)
            } else {
                Timber.tag(TAG).e("decoy wallet key unavailable")
                throw InvalidPin()
            }

        if (anonWallet?.status?.isOk != true) {
            runCatching { anonWallet?.close() }
                .onFailure { Timber.tag(TAG).e(it, "could not close failed wallet") }
            Timber.tag(TAG).e("Wallet open failed")
            throw InvalidPin()
        }
        if (profile == WalletProfile.MAIN) {
            try {
                WalletKey.discardMigrationSnapshots(walletFile.path)
                if (!bound) migrateToKeystore(walletFile, walletName, anonWallet, pin)
            } catch (failure: Exception) {
                anonWallet.close()
                throw failure
            }
        }
        WalletSession.select(profile)
        return true
    }

    /*
     * One-time migration of a legacy PIN-encrypted wallet to a Keystore-wrapped random
     * key. Snapshot the keys file, re-encrypt with the new secret, wrap it, then drop the
     * snapshots. If restoring the PIN in memory fails, refuse this unlock and retain
     * the snapshots for recovery with the wallet closed.
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
                throw Exception("Wallet password change failed")
            }
            if (!wallet.store()) {
                throw Exception("store failed after re-key")
            }
            WalletKey.store(context, walletName, key)
            check(WalletKey.load(context, walletName) == key) { "Wallet credential verification failed" }
            // the gate must match this exact PIN, which just opened the wallet; always
            // rewrite it rather than trusting whatever was there
            val committed =
                prefs
                    .edit()
                    .putString(PREFS_PIN_HASH, PinHash.hash(pin))
                    .commit()
            if (!committed) throw Exception("could not persist the migrated PIN hash")
            Timber.tag(TAG).i("wallet migrated to a keystore-bound key")
        } catch (_: Exception) {
            Timber.tag(TAG).e("Keystore migration failed; rolling back")
            WalletKey.remove(context, walletName)
            // Put the open wallet back on the PIN first: it keeps storing on every refresh,
            // and a store keyed to the abandoned secret would undo a file-level rollback.
            // That re-writes keys and cache from memory, current and PIN-keyed.
            val restoredInMemory =
                !rekeyed ||
                    runCatching { wallet.setPassword(pin) && wallet.store() }.getOrDefault(false)
            if (!restoredInMemory) {
                // A live wallet with an uncertain password must never overwrite recovery files.
                // Keep both snapshots until a subsequent open can restore them after close.
                throw InvalidPin()
            }
        }
        WalletKey.discardMigrationSnapshots(walletFile.path)
    }

    suspend fun startService() = walletNetworkMutex.withLock { startCurrentWalletService() }

    private suspend fun startCurrentWalletService() {
        val manager = WalletManager.instance ?: return
        val wallet = manager.wallet ?: return
        handler =
            MoneroHandlerThread(
                wallet,
                walletState,
            ) {
                hasAppliedTransport(wallet)
            }

        wallet.setListener(handler)
        wallet.refreshHistory()
        handler?.start()

        val profile = WalletSession.profile
        val transport = transportController.config.value
        val lws = LwsConfig.load(prefs, profile)
        val lwsActive = lws.enabled
        val host =
            (if (lwsActive) lws.host else prefs.getString(NodeFields.RPC_HOST.value, "")).orEmpty()
        val rpcPort = if (lwsActive) lws.port else prefs.getInt(NodeFields.RPC_PORT.value, Node.defaultRpcPort)
        val rpcUsername =
            (if (lwsActive) "" else prefs.getString(NodeFields.RPC_USERNAME.value, "")).orEmpty()
        val rpcPassphrase =
            (if (lwsActive) "" else prefs.getString(NodeFields.RPC_PASSWORD.value, "")).orEmpty()
        walletState.setOffline(true)
        wallet.setOffline(true)
        // nothing to load without a node/LWS
        if (host.isNotEmpty()) walletState.setLoading(true)
        walletState.update()
        try {
            if (lwsActive) {
                wallet.setTrustedDaemon(false)
                // The LWS receives the view key. Its destination must match the
                // selected transport network and clearnet servers must use TLS unless
                // they are local. This is the same fail-closed policy as RPC.
                val compatibility = LwsConfig.validateTransport(lws, transport)
                if (compatibility != null) throw Exception(compatibility.technicalMessage)
                val validation = LwsConfig.validate(lws.host, lws.port, lws.useTls)
                if (validation != null) throw Exception(validation.technicalMessage)
            }
            // no node (offline signer): nothing to route, leave the transport off
            if (host.isNotEmpty()) applyProxy(wallet, profile)
            // A profile switch or close can replace this session while Tor is starting.
            if (WalletManager.instance !== manager || manager.wallet !== wallet || WalletSession.profile != profile) return
            check(transportController.config.value == transport && LwsConfig.load(prefs, profile) == lws) {
                "Wallet network settings changed during startup"
            }
            if (host.isNotEmpty()) {
                var node =
                    checkNotNull(
                        nodeFromPrefs(
                            host,
                            rpcPort,
                            rpcUsername,
                            rpcPassphrase,
                            lwsActive,
                            if (lwsActive) lws.useTls else prefs.getBoolean(NodeFields.NODE_TLS.value, false),
                        ),
                    ) { "Invalid saved endpoint" }
                if (!lwsActive &&
                    !NodeSelectionPolicy.accepts(transport, node, AnonConfig.getNetworkType())
                ) {
                    val replacement =
                        if (transport.autoSelectNode) {
                            NodeSelectionPolicy.select(
                                nodesRepository.getAll(),
                                transport,
                                AnonConfig.getNetworkType(),
                            )
                        } else {
                            null
                        }
                    if (replacement == null) {
                        throw Exception("No compatible node for the selected transport")
                    }
                    if (WalletManager.instance !== manager || manager.wallet !== wallet || WalletSession.profile != profile) return
                    check(transportController.config.value == transport && LwsConfig.load(prefs, profile) == lws) {
                        "Wallet network settings changed during node selection"
                    }
                    check(persistNode(replacement)) { "Cannot persist selected node" }
                    node = replacement
                }
                updateDaemon(wallet, node, lightWallet = lwsActive)
                walletState.setLoading(true)
            }
            walletState.update()
            if (wallet.isSynchronized) {
                wallet.refreshHistory()
            }
            if (host.isNotEmpty()) requireAppliedProxy(wallet)
            check(wallet.init(0, lightWallet = lwsActive)) { "Wallet initialization failed" }
            if (WalletManager.instance !== manager || manager.wallet !== wallet || WalletSession.profile != profile) return
            check(transportController.config.value == transport && LwsConfig.load(prefs, profile) == lws) {
                "Wallet network settings changed during initialization"
            }
            if (!lwsActive && prefs.getLong(
                    WalletProfileStore.restoreHeightKey(profile),
                    0L,
                ) != 0L
            ) {
                wallet.setRestoreHeight(
                    prefs.getLong(WalletProfileStore.restoreHeightKey(profile), 0L),
                )
            }
            if (wallet.isInitialized) {
                if (host.isNotEmpty()) requireAppliedProxy(wallet)
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
                if (host.isNotEmpty()) {
                    activateAppliedTransport(wallet)
                    wallet.startRefresh()
                }
                walletState.update()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (WalletManager.instance !== manager || manager.wallet !== wallet || WalletSession.profile != profile) return
            wallet.pauseRefresh()
            wallet.setOffline(true)
            if (lwsActive) wallet.setTrustedDaemon(false)
            walletState.setOffline(true)
            walletState.setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
            Timber.tag(TAG).e("Wallet service start failed")
            walletState.setLoading(false)
        }
    }

    /**
     * Pauses the current wallet before acquiring a route. Publishes an applied
     * authorization only after both native proxy updates and the session rechecks.
     * The caller can then initialize the backend and resume refresh on success.
     */
    private suspend fun applyProxy(
        wallet: Wallet,
        profile: WalletProfile,
    ) {
        transportApplyMutex.withLock {
            check(!walletState.backgroundSyncFaulted) { "Wallet session requires reopening" }
            val manager = checkNotNull(WalletManager.instance) { "Wallet manager unavailable" }
            val context = checkNotNull(AnonConfig.context) { "Wallet context unavailable" }
            if (WalletSession.profile != profile || manager.managedWalletAt(WalletProfileStore.walletFile(context, profile)) !== wallet) {
                throw CancellationException("Wallet session changed before acquiring transport")
            }
            appliedTransport = null
            wallet.pauseRefresh()
            wallet.setOffline(true)
            walletState.setOffline(true)
            val authorization = transportController.authorize()
            if (WalletManager.instance !== manager || manager.wallet !== wallet || WalletSession.profile != profile) {
                throw CancellationException("Wallet session changed while acquiring transport")
            }
            check(!walletState.backgroundSyncFaulted && transportController.isCurrent(authorization)) {
                "Wallet transport changed before applying the proxy"
            }
            applyEndpoint(manager, wallet, authorization.endpoint)
            if (WalletManager.instance !== manager || manager.wallet !== wallet || WalletSession.profile != profile) {
                throw CancellationException("Wallet session changed while applying transport")
            }
            check(transportController.isCurrent(authorization)) { "Wallet transport changed while applying the proxy" }
            appliedTransport = AppliedTransport(wallet, profile, authorization)
        }
    }

    /**
     * Re-applies the selected transport to an active LWS backend. This is required after
     * a transport change: the old HTTP client must not keep using the previous proxy.
     * The caller marks the wallet offline if this throws.
     */
    internal suspend fun reapplyLightWallet(
        wallet: Wallet,
        profile: WalletProfile,
    ) = walletNetworkMutex.withLock { reapplyCurrentLightWallet(wallet, profile) }

    private suspend fun reapplyCurrentLightWallet(
        wallet: Wallet,
        profile: WalletProfile,
    ) {
        val manager = checkNotNull(WalletManager.instance) { "Wallet manager unavailable" }
        val context = checkNotNull(AnonConfig.context) { "Wallet context unavailable" }
        if (WalletSession.profile != profile || manager.managedWalletAt(WalletProfileStore.walletFile(context, profile)) !== wallet) {
            throw CancellationException("Wallet session changed before LWS configuration")
        }
        check(wallet.isLightWallet) { "LWS settings require a wallet restart" }
        val transport = transportController.config.value
        val lws = LwsConfig.load(prefs, profile)
        if (!lws.enabled) throw IllegalStateException("LWS is not enabled")
        val compatibility = LwsConfig.validateTransport(lws, transport)
        if (compatibility != null) throw IllegalStateException(compatibility.technicalMessage)
        val validation = LwsConfig.validate(lws.host, lws.port, lws.useTls)
        if (validation != null) throw IllegalStateException(validation.technicalMessage)
        wallet.pauseRefresh()
        wallet.setOffline(true)
        walletState.setOffline(true)
        wallet.setTrustedDaemon(false)
        applyProxy(wallet, profile)
        if (WalletManager.instance !== manager || manager.wallet !== wallet || WalletSession.profile != profile) {
            throw CancellationException("Wallet session changed while preparing LWS")
        }
        check(transportController.config.value == transport && LwsConfig.load(prefs, profile) == lws) {
            "Wallet network settings changed while preparing LWS"
        }
        val node =
            nodeFromPrefs(
                lws.host,
                lws.port,
                "",
                "",
                lws = true,
                useTls = lws.useTls,
            ) ?: throw IllegalStateException("Invalid LWS endpoint")
        updateDaemon(wallet, node, lightWallet = true)
        requireAppliedProxy(wallet)
        check(wallet.init(0, lightWallet = true)) { "LWS initialization failed" }
        if (WalletManager.instance !== manager || manager.wallet !== wallet || WalletSession.profile != profile) {
            throw CancellationException("Wallet session changed during LWS initialization")
        }
        check(transportController.config.value == transport && LwsConfig.load(prefs, profile) == lws) {
            "Wallet network settings changed during LWS initialization"
        }
        requireAppliedProxy(wallet)
        wallet.setTrustedDaemon(false)
        check(wallet.lightWalletLogin()) { "LWS login failed" }
        wallet.setTrustedDaemon(false)
        wallet.setOffline(false)
        activateAppliedTransport(wallet)
        wallet.startRefresh()
    }

    internal suspend fun connectRpcNode(
        node: Node,
        wallet: Wallet?,
        profile: WalletProfile,
    ) = walletNetworkMutex.withLock { connectCurrentRpcNode(node, wallet, profile) }

    private suspend fun connectCurrentRpcNode(
        node: Node,
        wallet: Wallet?,
        profile: WalletProfile,
    ) {
        if (WalletSession.profile != profile || WalletManager.instance?.wallet !== wallet) {
            throw CancellationException("Wallet session changed before RPC configuration")
        }
        val transport = transportController.config.value
        check(!LwsConfig.load(prefs, profile).enabled) { "LWS settings require a wallet restart" }
        require(NodeSelectionPolicy.accepts(transport, node, AnonConfig.getNetworkType())) {
            "Node network does not match the selected transport"
        }
        if (wallet != null) {
            check(WalletManager.instance?.wallet === wallet && !wallet.isLightWallet) {
                "Current wallet cannot change RPC node"
            }
            applyProxy(wallet, profile)
            check(
                WalletManager.instance?.wallet === wallet && WalletSession.profile == profile &&
                    transportController.config.value == transport && !wallet.isLightWallet &&
                    !LwsConfig.load(prefs, profile).enabled,
            ) { "Wallet session changed while preparing RPC" }
            updateDaemon(wallet, node, lightWallet = false)
            requireAppliedProxy(wallet)
            check(wallet.init(0, lightWallet = false)) { "Node initialization failed" }
            requireAppliedProxy(wallet)
            wallet.setTrustedDaemon(true)
        }
        check(
            WalletManager.instance?.wallet === wallet && WalletSession.profile == profile &&
                transportController.config.value == transport && !LwsConfig.load(prefs, profile).enabled,
        ) {
            "Wallet session changed before saving RPC"
        }
        check(persistNode(node)) { "Cannot persist selected node" }
        if (wallet != null) {
            activateAppliedTransport(wallet)
            wallet.startRefresh()
        }
    }

    private fun applyEndpoint(
        manager: WalletManager,
        wallet: Wallet,
        endpoint: TransportEndpoint,
    ) {
        val previous = manager.proxy
        try {
            when (endpoint) {
                is TransportEndpoint.Socks5 -> {
                    val address = endpoint.endpoint.formatted
                    check(manager.setProxy(address)) { "Transport proxy was rejected" }
                    if (!wallet.setProxy(address)) {
                        throw IllegalStateException("Wallet proxy was rejected")
                    }
                }

                TransportEndpoint.Direct -> {
                    check(manager.clearProxy()) { "Clearing the proxy was rejected" }
                    if (!wallet.clearProxy()) {
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

    private fun persistNode(node: Node): Boolean =
        prefs.commitEdit {
            putString(NodeFields.RPC_HOST.value, node.host)
            putBoolean(NodeFields.NODE_TLS.value, node.useTls)
            putInt(NodeFields.RPC_PORT.value, node.rpcPort)
            putString(NodeFields.RPC_USERNAME.value, node.username)
            putString(NodeFields.RPC_PASSWORD.value, node.password)
            putString(NodeFields.RPC_NETWORK.value, AnonConfig.getNetworkType().toString())
            putString(NodeFields.NODE_NAME.value, node.name)
        }

    private fun updateDaemon(
        wallet: Wallet,
        node: Node,
        lightWallet: Boolean,
    ) {
        val manager = checkNotNull(WalletManager.instance) { "Wallet manager unavailable" }
        requireAppliedProxy(wallet)
        walletState.setLoading(true)
        wallet.pauseRefresh()
        walletState.setOffline(true)
        // Callers state the backend explicitly: startup applies the pending mode before
        // wallet.init(), and a pending enable/disable must not change trust or skip the
        // node-network check for the current RPC wallet.
        val lwsActive = lightWallet
        try {
            if (!lwsActive) {
                require(NodeSelectionPolicy.accepts(transportController.config.value, node, AnonConfig.getNetworkType())) {
                    "Node network does not match the selected transport"
                }
            }
            if (lwsActive) wallet.setTrustedDaemon(false)
            manager.setDaemon(node)
            requireAppliedProxy(wallet)
            wallet.setOffline(false)
            walletState.updateDaemon(
                DaemonInfo(
                    daemon = node.hostPort,
                    Wallet.ConnectionStatus.ConnectionStatus_Disconnected,
                    daemonHeight = 0L,
                ),
            )
            wallet.setTrustedDaemon(!lwsActive)
            walletState.setLoading(false)
        } catch (e: Exception) {
            if (manager.wallet !== wallet) throw e
            wallet.pauseRefresh()
            wallet.setOffline(true)
            walletState.setOffline(true)
            walletState.updateDaemon(
                DaemonInfo(
                    daemon = null,
                    Wallet.ConnectionStatus.ConnectionStatus_Disconnected,
                    daemonHeight = 0L,
                ),
            )
            walletState.setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
            if (lwsActive) wallet.setTrustedDaemon(false)
            walletState.setLoading(false)
            throw e
        }
    }

    @Synchronized
    internal fun wipe(
        wallet: Wallet,
        profile: WalletProfile,
        passPhrase: String,
    ): Boolean {
        val context = AnonConfig.context ?: throw UnableToWipeWallet()
        val manager = WalletManager.instance ?: throw UnableToWipeWallet()
        val walletFile = WalletProfileStore.walletFile(context, profile)

        fun isAuthorizedSession() =
            WalletSession.profile == profile &&
                manager.managedWalletAt(walletFile) === wallet &&
                !walletState.backgroundSync && !walletState.backgroundSyncRunning
        if (!isAuthorizedSession()) throw UnableToWipeWallet()
        val hashKey = WalletProfileStore.passphraseHashKey(profile)
        val hash = prefs.getString(hashKey, null)
        if (hash.isNullOrEmpty() || KeyStoreHelper.getCrazyPass(context, passPhrase) != hash) return false
        if (!isAuthorizedSession() || prefs.getString(hashKey, null) != hash) throw UnableToWipeWallet()

        wallet.pauseRefresh()
        if (!isAuthorizedSession() || !wallet.close()) throw UnableToCloseWallet()

        // Deletion uses the authenticated profile even if WalletSession changes.
        if (!WalletProfileStore.removeProfile(context, prefs, profile)) throw UnableToWipeWallet()
        return true
    }
}
