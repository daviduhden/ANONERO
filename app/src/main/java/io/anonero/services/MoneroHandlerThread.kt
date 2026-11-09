package io.anonero.services

import io.anonero.model.PendingTransaction
import io.anonero.model.Wallet
import io.anonero.model.WalletListener
import io.anonero.model.WalletManager
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Handy class for starting a new thread that has a looper. The looper can then be
 * used to create handler classes. Note that start() must still be called.
 * The started Thread has a stck size of STACK_SIZE (=5MB)
 */

class MoneroHandlerThread(private val wallet: Wallet, private val walletState: WalletState) :
    Thread(null, null, "MoneroHandler", THREAD_STACK_SIZE), WalletListener {


    @Synchronized
    override fun start() {
        super.start()
    }

    override fun run() {

    }

    override fun moneySpent(txId: String?, amount: Long) {

    }

    override fun moneyReceived(txId: String?, amount: Long) {
        Timber.tag(name).d("moneyReceived: %s", amount)
        WalletManager.instance?.wallet?.store()
    }

    override fun unconfirmedMoneyReceived(txId: String?, amount: Long) {}

    override fun newBlock(height: Long) {
        Timber.tag(name).d("newBlock: %s", height)
        updateSyncProgress(height)
    }

    private fun updateSyncProgress(height: Long) {
        val syncHeight = wallet.getBlockChainHeight()
        val deamonHeight = wallet.getDaemonBlockChainHeight()
        val left = deamonHeight - syncHeight
        if (syncHeight < 0 || left < 0) {
            return
        }
        val progress = if (wallet.getDaemonBlockChainTargetHeight().toDouble() == 0.0) {
            1f
        } else {
            (height.toDouble() / wallet.getDaemonBlockChainTargetHeight().toDouble()).toFloat()
        }
        walletState.syncUpdate(SyncProgress(progress, left))
    }

    override fun updated() {
        refresh(false)
        Timber.tag(name).i("updated()")
        walletState.update()
    }

    override fun refreshed() {
        val status = wallet.fullStatus.connectionStatus
        val daemonHeight = wallet.getDaemonBlockChainHeight()
        val chainHeight = wallet.getBlockChainHeight()
        /// height
        // runs every ~10s: routine passes are debug-only, reconnects stay in release logs
        val summary = "refreshed() status:${status} daemonHeight:$daemonHeight chainHeight:$chainHeight"
        Timber.tag(name).d(summary)
        if (wallet.isLightWallet) {
            // LWS scan height legitimately trails the chain; the normal height-difference
            // reconnect would re-login on every refresh.
            if (status === Wallet.ConnectionStatus.ConnectionStatus_Disconnected || status == null) {
                tryRestartConnection()
            } else {
                wallet.setSynchronized()
                wallet.store()
                refresh(true)
                walletState.setLoading(false)
            }
            walletState.update()
            return
        }
        if (walletState.offline) {
            //no node to reconnect to
            walletState.setLoading(false)
        } else if (status === Wallet.ConnectionStatus.ConnectionStatus_Disconnected || status == null) {
            Timber.tag(name).i("restarting connection: $summary")
            tryRestartConnection()
        } else {
            val heightDiff = daemonHeight - chainHeight
            if (heightDiff >= 2) {
                Timber.tag(name).i("restarting connection: $summary")
                tryRestartConnection()
            } else {
                if (!wallet.isSynchronized) {
                    updateSyncProgress(wallet.getBlockChainHeight())
                }
                wallet.setSynchronized()
                wallet.store()
                refresh(true)
                walletState.setLoading(false)
            }

        }
        walletState.update()
    }

    private fun tryRestartConnection() {
        val lightWallet = wallet.isLightWallet
        try {
            if (lightWallet) wallet.setTrustedDaemon(false)
            check(wallet.init(0, lightWallet = lightWallet)) { "Wallet initialization failed" }
            if (lightWallet) {
                // Login must succeed after every init before refresh is restarted.
                wallet.setTrustedDaemon(false)
                check(wallet.lightWalletLogin()) { "LWS login failed" }
            } else {
                // Preserve the existing full-node trust policy after init.
                wallet.setTrustedDaemon(true)
            }
            wallet.startRefresh()
            walletState.update()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            wallet.pauseRefresh()
            if (lightWallet) wallet.setTrustedDaemon(false)
            walletState.setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
            walletState.setLoading(false)
            Timber.tag(name).w("Wallet reconnect failed; refresh paused")
        }
    }

    private fun refresh(walletSynced: Boolean) {
        wallet.refreshHistory()
        if (walletSynced) {
            wallet.refreshCoins()
        }
        walletState.update()
    }

    fun sendTx(pendingTx: PendingTransaction): Boolean {
        return pendingTx.commit("", true)
    }

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