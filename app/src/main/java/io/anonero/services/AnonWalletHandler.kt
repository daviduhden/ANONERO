package io.anonero.services

import android.content.SharedPreferences
import android.util.Log
import io.anonero.AnonConfig
import io.anonero.model.Wallet
import io.anonero.model.WalletManager
import io.anonero.model.node.DaemonInfo
import io.anonero.model.node.Node
import io.anonero.model.node.NodeFields
import io.anonero.util.LwsConfig
import io.anonero.util.PREFS_PIN_HASH
import io.anonero.util.PinHash
import io.anonero.util.WalletKey
import io.anonero.util.WalletProfile
import io.anonero.util.WalletProfileStore
import io.anonero.util.WalletSession
import io.anonero.util.WALLET_PROXY
import io.anonero.util.WALLET_PROXY_PORT
import io.anonero.util.WALLET_USE_TOR
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.json.JSONObject
import java.io.File
import timber.log.Timber
import androidx.core.content.edit


class InvalidPin : Exception("invalid pin")

private const val TAG = "AnonWalletHandler"

class UnableToCloseWallet : Exception()

class AnonWalletHandler(
    private val prefs: SharedPreferences,
    private val walletState: WalletState,
    private val torService: TorService
) {

    private val _scope = CoroutineScope(Dispatchers.Default) + SupervisorJob()

    private var handler: MoneroHandlerThread? = null

    val scope get() = _scope

    init {
        scope.launch {
            torService.socksFlow.collect {
                val wallet = WalletManager.instance?.wallet
                if (prefs.getBoolean(WALLET_USE_TOR, true) && wallet?.isInitialized == true) {
                    try {
                        setProxy(it.address.value, it.port.value)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        wallet.pauseRefresh()
                        walletState.setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
                        walletState.setLoading(false)
                        Timber.tag(TAG).e("Could not update Tor proxy; refresh paused")
                    }
                }
            }
        }
    }

    fun openWallet(pin: String): Boolean {
        val context = AnonConfig.context!!
        val profile = WalletProfileStore.resolvePin(context, prefs, pin) ?: throw InvalidPin()
        val walletFile = WalletProfileStore.walletFile(context, profile)
        val walletName = walletFile.name

        // Opening the decoy after the legitimate wallet was already open must not leave
        // the legitimate keys, listeners, or history in memory. The normal app flow opens
        // only once per process; this guard covers a retry after a failed unlock.
        val current = WalletManager.instance?.wallet
        if (current != null && WalletSession.profile != profile) {
            val closed = runCatching {
                current.pauseRefresh()
                current.close()
            }.onFailure { Timber.tag(TAG).e(it, "could not close previous profile wallet") }
                .getOrDefault(false)
            if (!closed) {
                // Refuse to leave one profile's keys and history referenced while opening
                // another; a restart is safer than a half-switched process.
                Timber.tag(TAG).e("could not close previous profile wallet")
                throw InvalidPin()
            }
            WalletSession.reset()
        }

        val anonWallet = if (WalletKey.exists(context, walletName)) {
            // Keystore-bound: the PIN never encrypts the wallet, it gates access to the
            // wrapped secret. Verify it in software, then open with that secret.
            val key = try {
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
            Timber.tag(TAG).e("openWallet error: %s", anonWallet?.status?.errorString)
            throw InvalidPin()
        }
        WalletSession.select(profile)
        return true
    }

    /**
     * One-time migration of a legacy PIN-encrypted wallet to a Keystore-wrapped random
     * key. Snapshot the keys file, re-encrypt with the new secret, wrap it, then drop the
     * snapshot. Any failure rolls back to the snapshot and leaves the wallet
     * PIN-encrypted -- still openable. A crash between re-encrypt and wrap is caught at
     * startup by WalletKey.recoverInterruptedMigration. The seed is the ultimate net.
     */
    private fun migrateToKeystore(walletFile: File, walletName: String, wallet: Wallet, pin: String) {
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
            rekeyed = true  // from here the files on disk may already be keyed to `key`
            if (!wallet.setPassword(key)) {
                throw Exception("setPassword failed: ${wallet.status.errorString}")
            }
            wallet.store()
            WalletKey.store(context, walletName, key)
            // the gate must match this exact PIN, which just opened the wallet; always
            // rewrite it rather than trusting whatever was there
            prefs.edit(commit = true) { putString(PREFS_PIN_HASH, PinHash.hash(pin)) }
            snapshots.forEach { (snapshot, _) -> snapshot.delete() }
            Timber.tag(TAG).i("wallet migrated to a keystore-bound key")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "keystore migration failed -- rolling back")
            WalletKey.remove(context, walletName)
            // Put the open wallet back on the PIN first: it keeps storing on every refresh,
            // and a store keyed to the abandoned secret would undo a file-level rollback.
            // That re-writes keys and cache from memory, current and PIN-keyed.
            val restoredInMemory = !rekeyed || runCatching { wallet.setPassword(pin) }.getOrDefault(false)
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
        handler = MoneroHandlerThread(
            wallet,
            walletState
        )

        wallet.setListener(handler)
        wallet.refreshHistory()
        handler?.start()

        val lws = LwsConfig.load(prefs)
        val lwsActive = lws.enabled
        val host = if (lwsActive) lws.host else prefs.getString(NodeFields.RPC_HOST.value, "")
        val rpcPort = if (lwsActive) lws.port else prefs.getInt(NodeFields.RPC_PORT.value, Node.defaultRpcPort)
        val rpcUsername = if (lwsActive) "" else prefs.getString(NodeFields.RPC_USERNAME.value, "")
        val rpcPassphrase = if (lwsActive) "" else prefs.getString(NodeFields.RPC_PASSWORD.value, "")
        val offline = !AnonConfig.viewOnly && host.isNullOrEmpty()
        walletState.setOffline(offline)
        wallet.setOffline(offline)
        //nothing to load without a node/LWS
        if (!host.isNullOrEmpty()) walletState.setLoading(true)
        walletState.update()
        try {
            if (lwsActive) {
                wallet.setTrustedDaemon(false)
                // The LWS receives the view key. Sending that or the address outside Tor
                // would hand the server the user's IP as well; refuse rather than silently
                // falling back to a manual/clearnet proxy.
                if (!prefs.getBoolean(WALLET_USE_TOR, true)) {
                    throw Exception("LWS requires Tor; enable built-in Tor in Proxy settings")
                }
                val validation = LwsConfig.validate(lws.host, lws.port)
                if (validation != null) throw Exception(validation.technicalMessage)
            }
            // no node (offline signer): nothing to route, leave Tor off
            if (host?.isNotEmpty() == true) applyProxy()
            // A profile switch or close can replace this session while Tor is starting.
            if (WalletManager.instance !== manager || manager.wallet !== wallet) return
            if (host?.isNotEmpty() == true) {
                val nodeObj = JSONObject()
                    .apply {
                        put(NodeFields.RPC_HOST.value, host)
                        put(NodeFields.RPC_PORT.value, rpcPort)
                        put(NodeFields.RPC_USERNAME.value, rpcUsername)
                        put(NodeFields.RPC_PASSWORD.value, rpcPassphrase)
                        put(NodeFields.RPC_NETWORK.value, AnonConfig.getNetworkType().toString())
                        put(NodeFields.NODE_NAME.value, if (lwsActive) "lws" else "anon")
                    }
                val node = Node.fromJson(nodeObj)
                updateDaemon(node)
                walletState.setLoading(true)
            }
            walletState.update()
            if (wallet.isSynchronized) {
                wallet.refreshHistory()
            }
            check(wallet.init(0, lightWallet = lwsActive)) { "Wallet initialization failed" }
            if (!lwsActive && prefs.getLong(
                    WalletProfileStore.restoreHeightKey(WalletSession.profile), 0L
                ) != 0L
            ) {
                wallet.setRestoreHeight(
                    prefs.getLong(WalletProfileStore.restoreHeightKey(WalletSession.profile), 0L)
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
            if (lwsActive) wallet.setTrustedDaemon(false)
            walletState.setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
            Timber.tag(TAG).e("Wallet service start failed")
            walletState.setLoading(false)
        }

    }

    /**
     * Routes the wallet through Tor (waiting for its socks port) or the manual proxy. Call it
     * before the wallet first talks to a node, or that traffic goes out unproxied.
     */
    suspend fun applyProxy() {
        val manager = checkNotNull(WalletManager.instance) { "Wallet manager unavailable" }
        val wallet = manager.wallet
        val proxyHost = prefs.getString(WALLET_PROXY, "")
        val proxyPort = prefs.getInt(WALLET_PROXY_PORT, -1)
        val useTor = prefs.getBoolean(WALLET_USE_TOR, true)
        val lwsActive = LwsConfig.load(prefs).enabled || WalletManager.instance?.wallet?.isLightWallet == true
        check(!lwsActive || useTor) { "LWS requires built-in Tor" }
        if (useTor || proxyHost.isNullOrBlank()) {
            torService.start()
            var waited = 0L
            while (torService.socks == null) {
                if (waited >= 60_000L) {
                    // Never fall through to a direct connection: failing closed is the
                    // only safe behavior when Tor does not come up.
                    throw Exception("Tor proxy did not start; refusing an unproxied connection")
                }
                delay(200)
                waited += 200
            }
            val socket = torService.socks
                ?: throw IllegalStateException("Tor proxy stopped before it could be applied")
            if (WalletManager.instance !== manager || manager.wallet !== wallet) {
                throw CancellationException("Wallet session changed while waiting for Tor")
            }
            check(manager.setProxy(socket.value)) { "Tor proxy was rejected" }
        } else if (proxyHost.isNotEmpty() && proxyPort in 1..65535) {
            if (WalletManager.instance !== manager || manager.wallet !== wallet) {
                throw CancellationException("Wallet session changed before applying proxy")
            }
            check(manager.setProxy(proxyAddress(proxyHost, proxyPort))) { "Manual proxy was rejected" }
        } else {
            throw Exception("no proxy")
        }
    }

    fun updateDaemon(node: Node?) {
        walletState.setLoading(true)
        val wallet = WalletManager.instance?.wallet
        val walletManager = WalletManager.instance
        wallet?.pauseRefresh()
        // Every caller must preserve LWS trust, including startup before light mode is set.
        val lwsActive = LwsConfig.load(prefs).enabled || wallet?.isLightWallet == true
        if (lwsActive) wallet?.setTrustedDaemon(false)
        try {
            if (node != null) {
                walletState.setOffline(false)
                wallet?.setOffline(false)
                walletState.updateDaemon(
                    DaemonInfo(
                        daemon = "${node.host}:${node.rpcPort}",
                        Wallet.ConnectionStatus.ConnectionStatus_Disconnected,
                        daemonHeight = 0L
                    )
                )
            } else {
                walletState.updateDaemon(
                    DaemonInfo(
                        daemon = null,
                        Wallet.ConnectionStatus.ConnectionStatus_Disconnected,
                        daemonHeight = 0L
                    )
                )
            }
            checkNotNull(walletManager) { "Wallet manager unavailable" }.setDaemon(node)
            wallet?.setTrustedDaemon(!lwsActive)
            walletState.setLoading(false)
        } catch (e: Exception) {
            walletState.updateDaemon(
                DaemonInfo(
                    daemon = null,
                    Wallet.ConnectionStatus.ConnectionStatus_Disconnected,
                    daemonHeight = 0L
                )
            )
            if (lwsActive) wallet?.setTrustedDaemon(false)
            walletState.setLoading(false)
            throw e
        }

    }

    private fun proxyAddress(host: String, port: Int): String {
        require(host.isNotBlank() && port in 1..65535) { "Invalid proxy endpoint" }
        val formattedHost = if (':' in host && !host.startsWith("[")) "[$host]" else host
        return "$formattedHost:$port"
    }

    fun setProxy(proxy: String?, port: Int?) {
        Timber.tag(TAG).d("setProxy %s%s", proxy, port.toString())
        //disable proxy
        if (proxy == null && port == null) {
            prefs.edit(commit = true) {
                remove(WALLET_PROXY)
                remove(WALLET_PROXY_PORT)
            }
        } else {
            val proxyHost = requireNotNull(proxy) { "Proxy host required" }
            val proxyPort = requireNotNull(port) { "Proxy port required" }
            val proxyStr = proxyAddress(proxyHost, proxyPort)
            val manager = checkNotNull(WalletManager.instance) { "Wallet manager unavailable" }
            val lwsActive = LwsConfig.load(prefs).enabled || manager.wallet?.isLightWallet == true
            if (lwsActive) {
                check(prefs.getBoolean(WALLET_USE_TOR, true)) { "LWS requires built-in Tor" }
                val socket = checkNotNull(torService.socks) { "Tor proxy unavailable" }
                check(proxyHost == socket.address.value && proxyPort == socket.port.value) {
                    "LWS requires the built-in Tor endpoint"
                }
            }
            check(manager.setProxy(proxyStr)) { "Proxy was rejected" }
            val wallet = manager.wallet
            if (wallet != null && !wallet.setProxy(proxyStr)) {
                wallet.pauseRefresh()
                throw IllegalStateException("Wallet proxy was rejected")
            }
            prefs.edit(commit = true) {
                putString(WALLET_PROXY, proxyHost)
                putInt(WALLET_PROXY_PORT, proxyPort)
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
        WalletManager.instance?.wallet?.pauseRefresh()
        WalletManager.instance?.wallet?.stopBackgroundSync(passPhrase)
        WalletManager.instance?.setDaemon(null)
        if (WalletManager.instance?.wallet?.close() == true) {
            AnonConfig.context?.let { context ->
                // Remove only the active profile. Wiping from the decoy must not destroy
                // the legitimate wallet's files, keys, or PIN.
                WalletProfileStore.removeProfile(context, prefs, WalletSession.profile)
            }
            return true
        } else {
            throw UnableToCloseWallet()
        }

    }

}
