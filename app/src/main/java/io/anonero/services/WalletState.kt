package io.anonero.services

import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import io.anonero.AnonConfig
import io.anonero.model.AccountInfo
import io.anonero.model.CoinsInfo
import io.anonero.model.Subaddress
import io.anonero.model.TransactionInfo
import io.anonero.model.Wallet
import io.anonero.model.WalletManager
import io.anonero.model.node.DaemonInfo
import io.anonero.ui.util.getAllUsedSubAddresses
import io.anonero.ui.util.getLatestSubAddress
import io.anonero.ui.home.LockScreenShortCut
import io.anonero.util.WalletKey
import io.anonero.util.WalletProfile
import io.anonero.util.WalletProfileStore
import io.anonero.util.WalletSession
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class SyncProgress(val progress: Float, val left: Long)

private const val TAG = "WalletState"

class WalletState {
    private var _blockUpdates = AtomicBoolean(false)
    val hideAmountsFlow = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(false)
    private var _isSyncing = AtomicBoolean(false)
    private val _backgroundSync = MutableStateFlow(false)
    // ANON with no node set: the offline signer
    private val _offline = MutableStateFlow(false)
    private val _incomingTx = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    private val _transactions = MutableStateFlow<List<TransactionInfo>>(listOf())
    private val _subAddresses = MutableStateFlow<List<Subaddress>>(listOf())
    private val _balanceInfo = MutableStateFlow<Long?>(null)
    private val _unLockedBalance = MutableStateFlow<Long?>(null)
    private val _walletStatus = MutableStateFlow<Wallet.Status?>(null)
    private val _nextAddress = MutableStateFlow<Subaddress?>(null)
    private val _coins = MutableStateFlow<List<CoinsInfo>>(arrayListOf())
    private val _accounts = MutableStateFlow<List<AccountInfo>>(emptyList())
    private val _activeAccount = MutableStateFlow(0)
    private val _profile = MutableStateFlow(WalletSession.profile)
    private val _syncProgress = MutableStateFlow<SyncProgress?>(null)
    private val _connectedDaemon = MutableStateFlow<DaemonInfo?>(null)
    private val _connectionStatus = MutableStateFlow<Wallet.ConnectionStatus?>(null)
    private val _previousConnectionStatus = AtomicReference<Wallet.ConnectionStatus?>(null)
    private val _unlockShortcut = Channel<LockScreenShortCut>(capacity = 1)
    val unlockShortcut = _unlockShortcut.receiveAsFlow()
    private val bgSyncMutex = Mutex()

    val transactions: Flow<List<TransactionInfo>> = _transactions

    val balanceInfo: Flow<Long?> = _balanceInfo
    val unLockedBalance: Flow<Long?> = _unLockedBalance
    val isLoading: Flow<Boolean> = _isLoading
    val isSyncing get():Boolean = _isSyncing.get()
    val backgroundSync get():Boolean = _backgroundSync.value
    val backgroundSyncFlow: Flow<Boolean> = _backgroundSync
    val offline get():Boolean = _offline.value
    val offlineFlow: Flow<Boolean> = _offline

    val walletStatus: Flow<Wallet.Status?> = _walletStatus
    val syncProgress: Flow<SyncProgress?> = _syncProgress

    val nextAddress: Flow<Subaddress?> = _nextAddress
    val coins: Flow<List<CoinsInfo>> = _coins
    val subAddresses: Flow<List<Subaddress>> = _subAddresses
    val accounts: Flow<List<AccountInfo>> = _accounts
    val activeAccount: Flow<Int> = _activeAccount
    val profile: Flow<WalletProfile> = _profile
    val isDecoySession: Boolean get() = _profile.value.isDecoy

    val walletConnectionStatus: Flow<Wallet.ConnectionStatus?> = _walletStatus.map {
        it?.connectionStatus
    }

    val daemonInfo: Flow<DaemonInfo?> = _connectedDaemon
    val connectionStatus: Flow<Wallet.ConnectionStatus?> = _connectionStatus
    val incomingTx = _incomingTx.asSharedFlow()

    fun update() {
        if (_blockUpdates.get()) return
        getWallet?.let { wallet ->
            if (wallet.isInitialized) {
                _balanceInfo.update { wallet.balance }
                _unLockedBalance.update {
                    if (AnonConfig.viewOnly) {
                        wallet.viewOnlyBalanceFor(wallet.getAccountIndex())
                    } else {
                        wallet.unlockedBalance
                    }
                }
                _walletStatus.update { wallet.fullStatus }
                if (wallet.status.errorString.isNotEmpty()) {
                    Timber.tag(TAG).i("StatusError %s", wallet.status.errorString)
                }
                val address = try {
                    WalletManager.instance?.getDaemonAddress()
                } catch (_: Exception) {
                    null
                }
                address?.let {
                    _connectedDaemon.update {
                        DaemonInfo(
                            address,
                            connectionStatus = wallet.fullStatus.connectionStatus
                                ?: Wallet.ConnectionStatus.ConnectionStatus_Disconnected,
                            WalletManager.instance?.getBlockchainHeight() ?: -1L
                        )
                    }
                }
            }
            val oldTxCount = _transactions.value.size
            val updatedTxs = (wallet.history?.all?.sortedWith(comparator = { o1, o2 ->
                o2.timestamp.compareTo(o1.timestamp)
            }) ?: listOf()).fastDistinctBy {
                it.getListKey()
            }
            _transactions.update { updatedTxs }
            if (oldTxCount > 0 && updatedTxs.size > oldTxCount) {
                val hasNewIncoming = updatedTxs.take(updatedTxs.size - oldTxCount).any {
                    it.direction == TransactionInfo.Direction.Direction_In
                }
                if (hasNewIncoming) {
                    _incomingTx.tryEmit(Unit)
                }
            }
            if (!backgroundSync) {
                _nextAddress.update { (wallet.getLatestSubAddress()) }
                _subAddresses.update { (wallet.getAllUsedSubAddresses()).reversed() }
                val account = wallet.getAccountIndex()
                _activeAccount.update { account }
                _coins.update {
                    (wallet.coins?.all ?: listOf()).fastFilter {
                        !it.spent && it.subaddrAccount == account
                    }
                }
                _accounts.update { readAccounts(wallet) }
            }
        }
    }

    private fun readAccounts(wallet: Wallet): List<AccountInfo> {
        val count = runCatching { wallet.getNumAccounts() }.getOrDefault(0)
        if (count <= 0) return emptyList()
        return (0 until count).mapNotNull { index ->
            runCatching {
                AccountInfo(
                    index = index,
                    label = wallet.getAccountLabelFor(index),
                    address = wallet.getAddress(index),
                    balance = if (AnonConfig.viewOnly) {
                        wallet.viewOnlyBalanceFor(index)
                    } else {
                        wallet.getBalanceFor(index)
                    },
                    unlockedBalance = if (AnonConfig.viewOnly) {
                        wallet.viewOnlyBalanceFor(index)
                    } else {
                        wallet.getUnlockedBalanceFor(index)
                    },
                )
            }.getOrNull()
        }
    }

    /** Called after a PIN has selected a wallet profile, before the service starts. */
    fun setProfile(profile: WalletProfile) {
        if (_profile.value != profile) {
            _profile.value = profile
            _transactions.value = emptyList()
            _subAddresses.value = emptyList()
            _coins.value = emptyList()
            _accounts.value = emptyList()
            _nextAddress.value = null
            _balanceInfo.value = null
            _unLockedBalance.value = null
            _walletStatus.value = null
            _syncProgress.value = null
            _connectedDaemon.value = null
            _connectionStatus.value = null
            _previousConnectionStatus.set(null)
            _backgroundSync.value = false
        }
    }

    /**
     * Selects a subaddress account. This is not a wallet switch: all accounts share the
     * same keys and wallet file. History and coins are re-read for the new major index
     * so balances from one account never appear under another.
     */
    @Synchronized
    fun selectAccount(index: Int): Boolean {
        val wallet = getWallet ?: return false
        if (index < 0 || index >= wallet.getNumAccounts()) return false
        wallet.setAccountIndex(index)
        wallet.refreshHistory()
        runCatching { wallet.coins?.refresh() }
        _activeAccount.value = index
        update()
        return true
    }

    @Synchronized
    fun addAccount(label: String): Boolean {
        val wallet = getWallet ?: return false
        return try {
            wallet.addAccount(label.ifBlank { "Account ${wallet.getNumAccounts()}" })
            wallet.store()
            update()
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "addAccount failed")
            false
        }
    }

    @Synchronized
    fun updateAccountLabel(accountIndex: Int, label: String): Boolean {
        val wallet = getWallet ?: return false
        return try {
            wallet.setAccountLabelFor(accountIndex, label)
            wallet.store()
            update()
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "updateAccountLabel failed")
            false
        }
    }

    /** Freeze/thaw a wallet output. The native coin-control layer also enforces this. */
    @Synchronized
    fun setOutputFrozen(publicKey: String, frozen: Boolean): Boolean {
        val wallet = getWallet ?: return false
        return try {
            val coins = wallet.coins ?: return false
            if (frozen) coins.freeze(publicKey) else coins.thaw(publicKey)
            wallet.store()
            _coins.update {
                (wallet.coins?.all ?: listOf()).fastFilter {
                    !it.spent && it.subaddrAccount == wallet.getAccountIndex()
                }
            }
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "setOutputFrozen failed")
            false
        }
    }

    fun setLoading(b: Boolean) {
        this._isLoading.update { b }
    }

    fun emitUnlockShortcut(shortcut: LockScreenShortCut) {
        _unlockShortcut.trySend(shortcut)
    }
    
    suspend fun enterBackgroundSync(): Boolean = bgSyncMutex.withLock {
        val wallet = getWallet ?: return false
        if (!wallet.isInitialized || backgroundSync) return false
        emitUnlockShortcut(LockScreenShortCut.HOME)
        _backgroundSync.value = true
        if (AnonConfig.viewOnly) return true
        _blockUpdates.set(true)
        try {
            if (wallet.startBackgroundSync()) {
                Timber.tag(TAG).i("Entered background sync")
                return true
            }
            // This state also gates the lock screen. Native sync failure must not
            // expose the wallet; only the PIN-verified exit path may unlock it.
            Timber.tag(TAG).e("startBackgroundSync returned false")
            return false
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "startBackgroundSync error")
            return false
        } finally {
            _blockUpdates.set(false)
        }
    }

    /**
     * Leaves background sync and restores the spend key. [pin] must already have been
     * verified against the PIN hash by the lock screen. wallet2 wants the wallet password,
     * which for a keystore-bound wallet is the device secret, not the PIN; only a legacy
     * wallet that never migrated is still keyed to the PIN.
     */
    suspend fun exitBackgroundSync(pin: String): Boolean = bgSyncMutex.withLock {
        val wallet = getWallet ?: return false
        if (AnonConfig.viewOnly) {
            _backgroundSync.value = false
            update()
            return true
        }
        _blockUpdates.set(true)
        try {
            val context = AnonConfig.context ?: return false
            val walletName = WalletProfileStore.activeWalletName(context)
            val password =
                if (WalletKey.exists(context, walletName)) WalletKey.load(context, walletName) else pin
            if (wallet.stopBackgroundSync(password)) {
                _backgroundSync.value = false
                update()
                wallet.startRefresh()
                Timber.tag(TAG).i("Exited background sync")
                return true
            }
            Timber.tag(TAG).e("stopBackgroundSync returned false")
            return false
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "stopBackgroundSync error")
            return false
        } finally {
            _blockUpdates.set(false)
        }
    }

    fun setConnectionStatus(status: Wallet.ConnectionStatus) {
        val previous = _previousConnectionStatus.getAndSet(status)
        _connectionStatus.update { status }
        if (previous == Wallet.ConnectionStatus.ConnectionStatus_Disconnected &&
            status == Wallet.ConnectionStatus.ConnectionStatus_Connected) {
            setLoading(true)
            getWallet?.startRefresh()
        }
    }

    fun updateDaemon(daemonInfo: DaemonInfo) {
        this._connectedDaemon.update { daemonInfo }
    }

    fun syncUpdate(syncProgress: SyncProgress) {
        val done = syncProgress.progress == 1f || syncProgress.left == 0L
        _syncProgress.update { if (done) null else syncProgress }
        _isSyncing.set(!done)
        if (done) {
            _connectionStatus.update { Wallet.ConnectionStatus.ConnectionStatus_Connected }
        }
    }

    fun toggleHideAmounts() {
        hideAmountsFlow.update { !it }
    }

    fun setHideAmounts(hidden: Boolean) {
        hideAmountsFlow.value = hidden
    }

    fun setOffline(offline: Boolean) {
        _offline.update { offline }
    }

    fun setBackGroundSync(startBackgroundSync: Boolean) {
        _backgroundSync.update { startBackgroundSync }
    }

    fun getNewAddress() {
        getWallet?.let {
            it.addSubaddress(it.getAccountIndex(), "Subaddress #${it.numSubAddresses}")
            it.store()
            it.getLatestSubAddress().let { subAddresses ->
                _nextAddress.update { subAddresses }
            }
            it.getAllUsedSubAddresses().let { allItems ->
                _subAddresses.update { allItems.reversed() }
            }
            update()
        }
    }

    fun setTransactionNote(note: String, transactionInfo: TransactionInfo) {
        getWallet?.let {
            it.setUserNote(transactionInfo.hash, note)
            it.store()
            it.refreshHistory()
            update()
        }
    }

    fun updateAddressLabel(label: String, addressIndex: Int) {
        getWallet?.let {
            it.setSubaddressLabel(addressIndex, label)
            it.store()
            it.refreshHistory()
            it.getAllUsedSubAddresses().let { allItems ->
                _subAddresses.update { allItems.reversed() }
            }
            it.getLatestSubAddress().let { subAddresses ->
                _nextAddress.update { subAddresses }
            }
        }
    }

    fun refresh() {
        if(getWallet?.isInitialized != true) {
            return;
        }
        if (getWallet?.fullStatus?.connectionStatus == Wallet.ConnectionStatus.ConnectionStatus_Connected) {
            setLoading(true);
            getWallet?.startRefresh();
        };
        getWallet?.refreshHistory()
    }

    fun resyncBlockchain(): Result<Boolean> {
        setLoading(true);
        try {
            if (getWallet?.fullStatus?.connectionStatus != Wallet.ConnectionStatus.ConnectionStatus_Connected) {
                return Result.failure(Exception("Please connect to daemon for resync"))
            }
            getWallet?.rescanBlockchainAsync()
            return Result.success(true)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
            return Result.failure(e)
        } finally {
            setLoading(false);
        }
    }

    private val getWallet get() = WalletManager.instance?.wallet
}
