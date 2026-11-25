package io.anonero.services

import io.anonero.model.PendingTransaction
import io.anonero.model.Wallet
import io.anonero.model.WalletListener
import io.anonero.model.WalletManager
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Wallet2 listener and refresh coordinator for the managed wallet.
 *
 * JNI invokes the listener on the calling wallet2 thread. This class retains a legacy
 * [Thread] wrapper whose [run] enqueues no work and does not dispatch the callbacks.
 * [updated] reloads local history, while [refreshed] can reconnect only for the current
 * wallet with an applied transport. An LWS reconnect repeats login with daemon trust disabled.
 */
class MoneroHandlerThread(
    private val wallet: Wallet,
    private val walletState: WalletState,
    private val transportReady: () -> Boolean,
) : Thread(null, null, "MoneroHandler", THREAD_STACK_SIZE),
    WalletListener {
    // A listener belongs to one native wallet, never to whichever wallet replaced it.
    private val isCurrentSession: Boolean
        get() = WalletManager.instance?.wallet === wallet

    private val canUseTransport: Boolean
        get() = isCurrentSession && !walletState.offline && transportReady()

    @Volatile
    private var lastDaemonHeight: Long? = null

    @Synchronized
    override fun start() {
        super.start()
    }

    override fun run() {
    }

    override fun moneySpent(
        txId: String?,
        amount: Long,
    ) {
    }

    override fun moneyReceived(
        txId: String?,
        amount: Long,
    ) {
        // Do not log the amount; logs are persisted and may be shared by the user.
        if (!isCurrentSession) return
        wallet.store()
    }

    override fun unconfirmedMoneyReceived(
        txId: String?,
        amount: Long,
    ) {}

    // The last observed daemon height is a progress target for full-node scans only.
    override fun newBlock(height: Long) {
        if (!canUseTransport || wallet.isLightWallet) return
        val target = lastDaemonHeight ?: wallet.getDaemonBlockChainHeight().also { lastDaemonHeight = it }
        if (height >= 0 && target > 0 && canUseTransport) {
            val left = (target - height).coerceAtLeast(0)
            val progress = (height.toDouble() / target).toFloat().coerceIn(0f, 1f)
            walletState.syncUpdate(SyncProgress(progress, left))
        }
    }

    override fun updated() {
        if (!isCurrentSession) return
        refresh(false)
    }

    override fun refreshed() {
        if (!isCurrentSession) return
        if (!canUseTransport) {
            walletState.setLoading(false)
            return
        }
        val status = wallet.fullStatus.connectionStatus ?: Wallet.ConnectionStatus.ConnectionStatus_Disconnected
        if (!canUseTransport) return
        if (status != Wallet.ConnectionStatus.ConnectionStatus_Connected) {
            walletState.recordDaemonStatus(wallet, status, -1L)
            if (status == Wallet.ConnectionStatus.ConnectionStatus_Disconnected) tryRestartConnection()
            walletState.setLoading(false)
            walletState.update()
            return
        }

        val daemonHeight = wallet.getDaemonBlockChainHeight()
        if (!canUseTransport) return
        lastDaemonHeight = daemonHeight
        walletState.recordDaemonStatus(wallet, status, daemonHeight)
        // LWS scan height may trail the chain without requiring reconnect.
        if (!wallet.isLightWallet) {
            val height = wallet.getBlockChainHeight()
            if (daemonHeight - height >= 2) {
                tryRestartConnection()
                return
            }
            if (!wallet.isSynchronized && height >= 0 && daemonHeight >= height) {
                val progress = if (daemonHeight == 0L) 1f else (height.toDouble() / daemonHeight).toFloat()
                walletState.syncUpdate(SyncProgress(progress, daemonHeight - height))
            }
        }
        wallet.setSynchronized()
        if (!wallet.store()) {
            Timber.tag(name).w("could not persist refreshed wallet state")
        }
        refresh(true)
        walletState.setLoading(false)
    }

    private fun tryRestartConnection() {
        if (!isCurrentSession || walletState.offline) return
        val lightWallet = wallet.isLightWallet
        // System VPN is the only direct/clearnet mode, and embedded Tor must have a live
        // SOCKS endpoint. If the transport is unavailable, do not re-init or re-login:
        // the wallet must stay offline instead of reconnecting directly or through a
        // stale endpoint.
        if (!transportReady()) {
            wallet.pauseRefresh()
            wallet.setOffline(true)
            if (lightWallet) wallet.setTrustedDaemon(false)
            walletState.setOffline(true)
            walletState.setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
            walletState.setLoading(false)
            return
        }
        try {
            if (lightWallet) wallet.setTrustedDaemon(false)
            check(wallet.init(0, lightWallet = lightWallet)) { "Wallet initialization failed" }
            check(canUseTransport) { "Wallet transport changed during reconnect" }
            if (lightWallet) {
                // Login must succeed after every init before refresh is restarted.
                wallet.setTrustedDaemon(false)
                check(wallet.lightWalletLogin()) { "LWS login failed" }
                // LWS stays untrusted after a successful login as well.
                wallet.setTrustedDaemon(false)
            } else {
                // Preserve the existing full-node trust policy after init.
                wallet.setTrustedDaemon(true)
            }
            check(canUseTransport) { "Wallet transport changed before restarting refresh" }
            wallet.startRefresh()
            walletState.update()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            wallet.pauseRefresh()
            wallet.setOffline(true)
            walletState.setOffline(true)
            if (lightWallet) wallet.setTrustedDaemon(false)
            walletState.setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
            walletState.setLoading(false)
            Timber.tag(name).w("Wallet reconnect failed; refresh paused")
        }
    }

    private fun refresh(walletSynced: Boolean) {
        if (!isCurrentSession) return
        wallet.refreshHistory()
        if (walletSynced) {
            wallet.refreshCoins()
        }
        walletState.update()
    }

    fun sendTx(pendingTx: PendingTransaction): Boolean = pendingTx.commit("", true)

    interface Listener {
        fun onRefresh(walletSynced: Boolean)

        fun onConnectionFail()

        fun onNewBlockFound(block: Long)
    }

    companion object {
        // from src/cryptonote_config.h
        const val THREAD_STACK_SIZE = (5 * 1024 * 1024).toLong()
    }
}
