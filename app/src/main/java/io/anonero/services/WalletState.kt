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
import io.anonero.ui.home.LockScreenShortCut
import io.anonero.ui.util.getAllUsedSubAddresses
import io.anonero.ui.util.getLatestSubAddress
import io.anonero.util.SecureClipboard
import io.anonero.util.WalletKey
import io.anonero.util.WalletPinSession
import io.anonero.util.WalletProfile
import io.anonero.util.WalletProfileStore
import io.anonero.util.WalletSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

data class SyncProgress(
    val progress: Float,
    val left: Long,
)

private const val TAG = "WalletState"

private enum class NativeBackgroundSync {
    NOT_ATTEMPTED,
    RUNNING,
    FAULT,
}

/**
 * Process-wide, UI-facing state derived from the managed wallet.
 *
 * It reads balances, history, accounts, subaddresses and outputs from the wallet owned by
 * [WalletManager]. The service and refresh listener supply transport status and progress.
 * Views and ViewModels observe the resulting flows as the current session's UI snapshot.
 *
 * Account and output mutations synchronize with snapshot reads. The lock transitions use
 * a dedicated mutex because they may suspend, and unlock requires prior PIN verification.
 */
class WalletState {
    @Volatile
    internal var isTransportApplied: (Wallet) -> Boolean = { false }

    private var _blockUpdates = AtomicBoolean(false)
    val hideAmountsFlow = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(false)
    private var _isSyncing = AtomicBoolean(false)
    private val _backgroundSync = MutableStateFlow(false)

    @Volatile
    private var nativeBackgroundSync = NativeBackgroundSync.NOT_ATTEMPTED

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
    private val _unlockShortcut = Channel<LockScreenShortCut>(capacity = 1)
    val unlockShortcut = _unlockShortcut.receiveAsFlow()
    private val bgSyncMutex = Mutex()

    val transactions: Flow<List<TransactionInfo>> = _transactions

    val balanceInfo: Flow<Long?> = _balanceInfo
    val unLockedBalance: Flow<Long?> = _unLockedBalance
    val isLoading: Flow<Boolean> = _isLoading
    val isSyncing get(): Boolean = _isSyncing.get()
    val backgroundSync get(): Boolean = _backgroundSync.value
    internal val backgroundSyncFaulted get() = nativeBackgroundSync == NativeBackgroundSync.FAULT
    val backgroundSyncRunning get(): Boolean = nativeBackgroundSync == NativeBackgroundSync.RUNNING
    val backgroundSyncFlow: Flow<Boolean> = _backgroundSync
    val offline get(): Boolean = _offline.value
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

    val walletConnectionStatus: Flow<Wallet.ConnectionStatus?> = _connectionStatus

    val daemonInfo: Flow<DaemonInfo?> = _connectedDaemon
    val connectionStatus: Flow<Wallet.ConnectionStatus?> = _connectionStatus
    val connectionStatusValue: Wallet.ConnectionStatus? get() = _connectionStatus.value
    val incomingTx = _incomingTx.asSharedFlow()

    @Synchronized
    fun update() {
        if (_blockUpdates.get()) return
        getWallet?.let { wallet ->
            if (wallet.isInitialized) {
                _balanceInfo.update {
                    if (AnonConfig.viewOnly) viewOnlyTotal(wallet) else wallet.balance
                }
                _unLockedBalance.update {
                    if (AnonConfig.viewOnly) {
                        wallet.viewOnlyBalanceFor(wallet.getAccountIndex())
                    } else {
                        wallet.unlockedBalance
                    }
                }
                val status = wallet.status
                status.connectionStatus = _connectionStatus.value
                _walletStatus.value = status
                if (status.errorString.isNotEmpty()) {
                    // Do not persist wallet2's raw error text: it can include a node or
                    // file path. The UI still receives the structured status.
                    Timber.tag(TAG).i("Wallet status reports an error")
                }
            }
            val oldTxCount = _transactions.value.size
            val updatedTxs =
                (
                    wallet.history?.all?.sortedWith(comparator = { o1, o2 ->
                        o2.timestamp.compareTo(o1.timestamp)
                    }) ?: listOf()
                ).fastDistinctBy {
                    it.getListKey()
                }
            _transactions.update { updatedTxs }
            // Compare against an existing snapshot to avoid treating the initial
            // history load as a batch of incoming notifications.
            if (oldTxCount > 0 && updatedTxs.size > oldTxCount) {
                val hasNewIncoming =
                    updatedTxs.take(updatedTxs.size - oldTxCount).any {
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

    private fun viewOnlyTotal(wallet: Wallet): Long =
        runCatching {
            (0 until wallet.getNumAccounts()).sumOf { index ->
                wallet.viewOnlyBalanceFor(index)
            }
        }.getOrDefault(0L)

    private fun readAccounts(wallet: Wallet): List<AccountInfo> {
        val count = runCatching { wallet.getNumAccounts() }.getOrDefault(0)
        if (count <= 0) return emptyList()
        return (0 until count).mapNotNull { index ->
            runCatching {
                AccountInfo(
                    index = index,
                    label = wallet.getAccountLabelFor(index),
                    address = wallet.getAddress(index),
                    balance =
                        if (AnonConfig.viewOnly) {
                            wallet.viewOnlyBalanceFor(index)
                        } else {
                            wallet.getBalanceFor(index)
                        },
                    unlockedBalance =
                        if (AnonConfig.viewOnly) {
                            wallet.viewOnlyBalanceFor(index)
                        } else {
                            wallet.getUnlockedBalanceFor(index)
                        },
                )
            }.getOrNull()
        }
    }

    // Every open starts a fresh UI session, even when reopening the same profile.
    @Synchronized
    fun resetSession(profile: WalletProfile) {
        _profile.value = profile
        _transactions.value = emptyList()
        _subAddresses.value = emptyList()
        _coins.value = emptyList()
        _accounts.value = emptyList()
        _activeAccount.value = 0
        _nextAddress.value = null
        _balanceInfo.value = null
        _unLockedBalance.value = null
        _walletStatus.value = null
        _syncProgress.value = null
        _connectedDaemon.value = null
        _connectionStatus.value = null
        _backgroundSync.value = false
        nativeBackgroundSync = NativeBackgroundSync.NOT_ATTEMPTED
        _isLoading.value = false
        _isSyncing.set(false)
        _offline.value = false
        while (_unlockShortcut.tryReceive().isSuccess) {
            // Discard actions queued for the previous wallet session.
        }
    }

    @Synchronized
    fun setProfile(profile: WalletProfile) {
        if (nativeBackgroundSync == NativeBackgroundSync.FAULT) return
        if (_profile.value != profile) resetSession(profile)
    }

    /*
     * Selects a subaddress account. This is not a wallet switch: all accounts share the
     * same keys and wallet file. History and coins are re-read for the new major index
     * so balances from one account never appear under another.
     */
    @Synchronized
    fun selectAccount(index: Int): Boolean {
        if (backgroundSync || backgroundSyncRunning) return false
        val wallet = getWallet ?: return false
        if (index < 0 || index >= wallet.getNumAccounts()) return false
        // Selection can change the local wallet index before native snapshot refresh
        // fails. Never leave old-account amounts/outputs beneath the current index.
        _transactions.value = emptyList()
        _subAddresses.value = emptyList()
        _coins.value = emptyList()
        _nextAddress.value = null
        _balanceInfo.value = null
        _unLockedBalance.value = null
        return try {
            wallet.setAccountIndex(index)
            wallet.refreshHistory()
            wallet.coins?.refresh()
            update()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Account selection failed")
            false
        } finally {
            _activeAccount.value = wallet.getAccountIndex()
        }
    }

    @Synchronized
    fun addAccount(label: String): Boolean {
        if (backgroundSync || backgroundSyncRunning) return false
        val wallet = getWallet ?: return false
        return try {
            wallet.addAccount(label.ifBlank { "Account ${wallet.getNumAccounts()}" })
            if (!wallet.store()) return false
            update()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "addAccount failed")
            false
        }
    }

    @Synchronized
    fun updateAccountLabel(
        accountIndex: Int,
        label: String,
    ): Boolean {
        if (backgroundSync || backgroundSyncRunning) return false
        val wallet = getWallet ?: return false
        return try {
            if (accountIndex !in 0 until wallet.getNumAccounts()) return false
            wallet.setAccountLabelFor(accountIndex, label)
            if (!wallet.store()) return false
            update()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "updateAccountLabel failed")
            false
        }
    }

    /** Verifies an active-account output through [io.anonero.model.Coins] before storing. */
    @Synchronized
    fun setOutputFrozen(
        publicKey: String,
        frozen: Boolean,
    ): Boolean {
        if (backgroundSync || backgroundSyncRunning) return false
        val wallet = getWallet ?: return false
        return try {
            val coins = wallet.coins ?: return false
            if (coins.all.none { it.pub_key == publicKey && !it.spent && it.subaddrAccount == wallet.getAccountIndex() }) return false
            if (frozen) coins.freeze(publicKey) else coins.thaw(publicKey)
            _coins.update {
                (wallet.coins?.all ?: listOf()).fastFilter {
                    !it.spent && it.subaddrAccount == wallet.getAccountIndex()
                }
            }
            wallet.store()
        } catch (cancelled: CancellationException) {
            throw cancelled
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

    suspend fun enterBackgroundSync(): Boolean =
        bgSyncMutex.withLock {
            val wallet = getWallet ?: return false
            if (backgroundSync) return false
            // A copied address or signature must not survive the lock transition.
            AnonConfig.context?.applicationContext?.let { SecureClipboard.clear(it) }
            emitUnlockShortcut(LockScreenShortCut.HOME)
            _backgroundSync.value = true
            nativeBackgroundSync = NativeBackgroundSync.NOT_ATTEMPTED
            if (AnonConfig.viewOnly) return true
            _blockUpdates.set(true)
            try {
                if (wallet.isInitialized && wallet.startBackgroundSync()) {
                    nativeBackgroundSync = NativeBackgroundSync.RUNNING
                    Timber.tag(TAG).i("Entered background sync")
                    return true
                }
                // Failure may leave spend keys live. Keep the overlay locked and stop
                // network activity; recovery requires a fresh wallet session.
                markBackgroundSyncFault(wallet)
                Timber.tag(TAG).e("Native background sync did not start")
                return false
            } catch (cancelled: CancellationException) {
                markBackgroundSyncFault(wallet)
                throw cancelled
            } catch (_: Exception) {
                markBackgroundSyncFault(wallet)
                Timber.tag(TAG).e("startBackgroundSync error")
                return false
            } finally {
                _blockUpdates.set(false)
            }
        }

    private fun markBackgroundSyncFault(wallet: Wallet) {
        if (getWallet !== wallet) return
        recordBackgroundSyncFault()
        try {
            try {
                wallet.pauseRefresh()
            } finally {
                wallet.setOffline(true)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Timber.tag(TAG).e("Could not pause background sync after lock fault")
        }
    }

    internal fun recordBackgroundSyncFault() {
        nativeBackgroundSync = NativeBackgroundSync.FAULT
        _backgroundSync.value = true
        setOffline(true)
        setConnectionStatus(Wallet.ConnectionStatus.ConnectionStatus_Disconnected)
        setLoading(false)
    }

    /*
     * Leaves background sync and restores the spend key. [pin] must already have been
     * verified against the PIN hash by the lock screen. wallet2 wants the wallet password,
     * which for a keystore-bound wallet is the device secret, not the PIN; only a legacy
     * wallet that never migrated is still keyed to the PIN.
     */
    internal suspend fun exitBackgroundSync(
        pin: String,
        owner: WalletPinSession?,
    ): Boolean =
        bgSyncMutex.withLock {
            if (nativeBackgroundSync == NativeBackgroundSync.FAULT) return false
            val context = AnonConfig.context ?: return false
            WalletProfileStore.tryProfileMutation(onBusy = { false }) {
                if (owner == null || !owner.current()) return false
                val wallet = owner.wallet
                // An overlay can be active without native background sync, such as while a
                // session is still starting. A recorded native fault is rejected above.
                if (!backgroundSyncRunning) {
                    _backgroundSync.value = false
                    update()
                    return true
                }
                if (AnonConfig.viewOnly) {
                    _backgroundSync.value = false
                    nativeBackgroundSync = NativeBackgroundSync.NOT_ATTEMPTED
                    update()
                    return true
                }
                _blockUpdates.set(true)
                try {
                    val walletName = owner.profile.walletName
                    val password =
                        if (WalletKey.exists(context, walletName)) WalletKey.load(context, walletName) else pin
                    if (wallet.stopBackgroundSync(password)) {
                        _backgroundSync.value = false
                        nativeBackgroundSync = NativeBackgroundSync.NOT_ATTEMPTED
                        update()
                        if (!offline && isTransportApplied(wallet)) wallet.startRefresh()
                        Timber.tag(TAG).i("Exited background sync")
                        return true
                    }
                    Timber.tag(TAG).e("stopBackgroundSync returned false")
                    return false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Timber.tag(TAG).e("stopBackgroundSync error")
                    return false
                } finally {
                    _blockUpdates.set(false)
                }
            }
        }

    fun setConnectionStatus(status: Wallet.ConnectionStatus) {
        _connectionStatus.value = status
        _connectedDaemon.update { it?.copy(connectionStatus = status) }
    }

    @Synchronized
    internal fun recordDaemonStatus(
        wallet: Wallet,
        status: Wallet.ConnectionStatus,
        height: Long,
    ) {
        if (getWallet !== wallet || offline || !isTransportApplied(wallet)) return
        _connectionStatus.value = status
        _connectedDaemon.update { it?.copy(connectionStatus = status, daemonHeight = height) }
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
        _offline.update { offline || backgroundSyncFaulted }
    }

    fun getNewAddress(): Boolean {
        val wallet = getWallet ?: return false
        wallet.addSubaddress(wallet.getAccountIndex(), "Subaddress #${wallet.numSubAddresses}")
        if (!wallet.store()) return false
        wallet.getLatestSubAddress().let { subAddresses ->
            _nextAddress.update { subAddresses }
        }
        wallet.getAllUsedSubAddresses().let { allItems ->
            _subAddresses.update { allItems.reversed() }
        }
        update()
        return true
    }

    @Synchronized
    internal fun setTransactionNote(
        wallet: Wallet,
        profile: WalletProfile,
        transactionInfo: TransactionInfo,
        note: String,
    ): Boolean {
        val hash = transactionInfo.hash ?: return false
        if (backgroundSync || backgroundSyncRunning || getWallet !== wallet ||
            WalletSession.profile != profile || _transactions.value.none { it === transactionInfo }
        ) {
            return false
        }
        return try {
            if (!wallet.setUserNote(hash, note) || !wallet.store()) return false
            wallet.refreshHistory()
            if (getWallet === wallet && WalletSession.profile == profile) update()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    fun updateAddressLabel(
        label: String,
        addressIndex: Int,
    ): Boolean {
        val wallet = getWallet ?: return false
        wallet.setSubaddressLabel(addressIndex, label)
        if (!wallet.store()) return false
        wallet.refreshHistory()
        wallet.getAllUsedSubAddresses().let { allItems ->
            _subAddresses.update { allItems.reversed() }
        }
        wallet.getLatestSubAddress().let { subAddresses ->
            _nextAddress.update { subAddresses }
        }
        return true
    }

    fun refresh() {
        val wallet = getWallet ?: return
        if (!wallet.isInitialized) return
        if (!offline && isTransportApplied(wallet) &&
            connectionStatusValue == Wallet.ConnectionStatus.ConnectionStatus_Connected
        ) {
            setLoading(true)
            wallet.startRefresh()
        }
        wallet.refreshHistory()
    }

    fun resyncBlockchain(): Result<Boolean> {
        try {
            val wallet = getWallet
            if (wallet == null || offline || !isTransportApplied(wallet) ||
                connectionStatusValue != Wallet.ConnectionStatus.ConnectionStatus_Connected
            ) {
                return Result.failure(Exception("Please connect to daemon for resync"))
            }
            setLoading(true)
            wallet.rescanBlockchainAsync()
            return Result.success(true)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
            return Result.failure(e)
        } finally {
            setLoading(false)
        }
    }

    private val getWallet get() = WalletManager.instance?.wallet
}
