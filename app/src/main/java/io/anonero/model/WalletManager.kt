package io.anonero.model

import io.anonero.AnonConfig
import io.anonero.model.node.Node
import timber.log.Timber
import java.io.File
import java.util.Locale

private const val TAG = "WalletManager"

/**
 * Process-wide owner of the currently open wallet.
 *
 * A [WalletManager] creates and opens wallet2 objects through JNI and keeps at most one
 * *managed* wallet in [wallet]. The managed wallet is the one the UI observes through
 * `WalletState`. Explicitly unmanaged wallets are used for work that
 * must not replace the live wallet: creating the decoy and re-keying a backup copy.
 *
 * Native wallet calls can block on disk or network I/O. Call this class off the Android
 * main thread. The singleton is created lazily by [instance]; [AnonConfig] decides the
 * network type at process start.
 */
class WalletManager {
    var networkType = NetworkType.NetworkType_Mainnet
    var wallet: Wallet? = null
        private set
    private var daemonAddress: String? = null
    var daemonUsername = ""
        private set
    var daemonPassword = ""
        private set
    var proxy = ""
        private set

    fun init() {
        this.networkType = AnonConfig.getNetworkType()
    }

    private fun manageWallet(wallet: Wallet) {
        Timber.tag(TAG).i("Managing %s", wallet.name)
        this.wallet = wallet
    }

    private fun unmanageWallet(wallet: Wallet?) {
        requireNotNull(wallet) { "Cannot unmanage null!" }
        checkNotNull(this.wallet) { "No wallet under management!" }
        check(this.wallet === wallet) { wallet.name + " not under management!" }
        Timber.tag(TAG).i("Unmanaging ${wallet.name}")
        this.wallet = null
    }

    fun createWallet(
        aFile: File,
        password: String,
        passphrase: String,
        language: String,
        height: Long,
    ): Wallet {
        val walletHandle =
            createWalletJ(aFile.absolutePath, password, passphrase, language, networkType.value)
        if (walletHandle == 0L) throw IllegalStateException("Wallet creation failed")
        val wallet = Wallet(walletHandle)
        manageWallet(wallet)
        if (wallet.status.isOk) {
//            // (Re-)Estimate restore height based on what we know
//            val oldHeight = wallet.getRestoreHeight()
//            // Go back 4 days if we don't have a precise restore height
//            val restoreDate = Calendar.getInstance()
//            restoreDate.add(Calendar.DAY_OF_MONTH, 0)
//            val restoreHeight =
//                if (height > -1) height else RestoreHeight.instance?.getHeight(restoreDate.time)
//            if (restoreHeight != null) {
//                wallet.setRestoreHeight(restoreHeight)
//            }
//            Log.d("WalletManager.kt", "Changed Restore Height from $oldHeight to ${wallet.getRestoreHeight()}")
            // This rewrites the keys file (which contains the restore height). If it fails,
            // the caller must not proceed with a wallet whose keys file is inconsistent.
            if (!wallet.setPassword(password)) {
                throw IllegalStateException("Wallet password update failed")
            }
        } else {
            throw IllegalStateException("Wallet creation failed: ${wallet.status.errorString}")
        }
        return wallet
    }

    /*
     * Creates a wallet without making it the managed live one. Used to provision the
     * decoy wallet while the legitimate wallet stays open and untouched.
     */
    fun createWalletUnmanaged(
        aFile: File,
        password: String,
        passphrase: String,
        language: String,
        height: Long,
    ): Wallet {
        val handle =
            createWalletJ(
                aFile.absolutePath,
                password,
                passphrase,
                language,
                networkType.value,
            )
        if (handle == 0L) throw IllegalStateException("Wallet creation failed")
        return Wallet(handle)
    }

    private external fun createWalletJ(
        path: String,
        password: String,
        passphrase: String,
        language: String,
        networkType: Int,
    ): Long

    fun openWallet(
        path: String,
        password: String,
    ): Wallet {
        val walletHandle = openWalletJ(path, password, networkType.value, AnonConfig.viewOnly)
        if (walletHandle == 0L) throw IllegalStateException("Wallet open failed")
        val wallet = Wallet(walletHandle)
        manageWallet(wallet)
        return wallet
    }

    /*
     * Opens a second wallet without making it the managed live one -- for working on a
     * copy while the real wallet stays open. Close it with [closeUnmanaged].
     */
    fun openWalletUnmanaged(
        path: String,
        password: String,
    ): Wallet {
        val handle = openWalletJ(path, password, networkType.value, AnonConfig.viewOnly)
        if (handle == 0L) throw IllegalStateException("Wallet open failed")
        return Wallet(handle)
    }

    fun closeUnmanaged(wallet: Wallet): Boolean = closeJ(wallet)

    private external fun openWalletJ(
        path: String,
        password: String,
        networkType: Int,
        isViewOnly: Boolean,
    ): Long

    fun recoveryWallet(
        aFile: File,
        password: String,
        mnemonic: String,
        offset: String,
        restoreHeight: Long,
    ): Wallet {
        val walletHandle =
            recoveryWalletJ(
                aFile.absolutePath,
                password,
                mnemonic,
                offset,
                networkType.value,
                restoreHeight,
            )
        if (walletHandle == 0L) throw IllegalStateException("Wallet recovery failed")
        val wallet = Wallet(walletHandle)
        manageWallet(wallet)
        return wallet
    }

    private external fun recoveryWalletJ(
        path: String,
        password: String,
        mnemonic: String,
        offset: String,
        networkType: Int,
        restoreHeight: Long,
    ): Long

    fun recoveryWalletPolyseed(
        aFile: File,
        password: String,
        mnemonic: String,
        offset: String,
    ): Wallet {
        val walletHandle =
            recoveryWalletPolyseedJ(
                aFile.absolutePath,
                password,
                mnemonic,
                offset,
                networkType.value,
            )
        if (walletHandle == 0L) throw IllegalStateException("Wallet recovery failed")
        val wallet = Wallet(walletHandle)
        manageWallet(wallet)
        return wallet
    }

    private external fun recoveryWalletPolyseedJ(
        path: String,
        password: String,
        mnemonic: String,
        offset: String,
        networkType: Int,
    ): Long

    fun createWalletWithKeys(
        aFile: File,
        password: String,
        language: String,
        restoreHeight: Long,
        addressString: String,
        viewKeyString: String,
        spendKeyString: String,
    ): Wallet {
        val walletHandle =
            createWalletFromKeysJ(
                aFile.absolutePath,
                password,
                language,
                networkType.value,
                restoreHeight,
                addressString,
                viewKeyString,
                spendKeyString,
            )
        if (walletHandle == 0L) throw IllegalStateException("Wallet creation failed")
        val wallet = Wallet(walletHandle)
        manageWallet(wallet)
        return wallet
    }

    private external fun createWalletFromKeysJ(
        path: String,
        password: String,
        language: String,
        networkType: Int,
        restoreHeight: Long,
        addressString: String,
        viewKeyString: String,
        spendKeyString: String,
    ): Long

    fun createWalletFromDevice(
        aFile: File,
        password: String,
        restoreHeight: Long,
        deviceName: String,
    ): Wallet {
        val walletHandle =
            createWalletFromDeviceJ(
                aFile.absolutePath,
                password,
                networkType.value,
                deviceName,
                restoreHeight,
                "5:20",
            )
        if (walletHandle == 0L) throw IllegalStateException("Wallet creation failed")
        val wallet = Wallet(walletHandle)
        manageWallet(wallet)
        return wallet
    }

    private external fun createWalletFromDeviceJ(
        path: String,
        password: String,
        networkType: Int,
        deviceName: String,
        restoreHeight: Long,
        subaddressLookahead: String,
    ): Long

    external fun closeJ(wallet: Wallet?): Boolean

    fun close(wallet: Wallet): Boolean {
        unmanageWallet(wallet)
        return try {
            val closed = closeJ(wallet)
            if (!closed) {
                // in case we could not close it
                // we manage it again
                manageWallet(wallet)
            }
            closed
        } catch (e: Exception) {
            // A native exception must not leave the wallet unmanaged and unreachable.
            manageWallet(wallet)
            throw e
        }
    }

    fun walletExists(aFile: File): Boolean = walletExists(aFile.absolutePath)

    private external fun walletExists(path: String?): Boolean

    external fun verifyWalletPassword(
        keysFileName: String?,
        password: String?,
        watchOnly: Boolean,
    ): Boolean

    fun verifyWalletPasswordOnly(
        keysFileName: String,
        password: String,
    ): Boolean = queryWalletDeviceJ(keysFileName, password) >= 0

    private external fun queryWalletDeviceJ(
        keysFileName: String,
        password: String,
    ): Int

    // this should not be called on the main thread as it connects to the node (and takes a long time)
    fun setDaemon(node: Node?) {
        if (node != null) {
            require(networkType === node.networkType) { "network type does not match" }
            daemonAddress = node.address
            daemonUsername = node.username
            daemonPassword = node.password
            daemonAddress?.let { addr -> setDaemonAddressJ(addr) }
            // The node address can identify the user's chosen server; do not persist it in logs.
            Timber.tag(TAG).i("setDaemon: configured")
        } else {
            daemonAddress = null
            daemonUsername = ""
            daemonPassword = ""
            // setDaemonAddressJ(""); // don't disconnect as monero code blocks for many seconds!
            // TODO: need to do something about that later
        }
    }

    fun getDaemonAddress(): String? = daemonAddress

    private external fun setDaemonAddressJ(address: String)

    external fun getDaemonVersion(): Int

    external fun getBlockchainHeight(): Long

    external fun getBlockchainTargetHeight(): Long

    external fun getNetworkDifficulty(): Long

    external fun getMiningHashRate(): Double

    external fun getBlockTarget(): Long

    external fun isMining(): Boolean

    external fun startMining(
        address: String?,
        backgroundMining: Boolean,
        ignoreBattery: Boolean,
    ): Boolean

    external fun stopMining(): Boolean

    external fun resolveOpenAlias(
        address: String?,
        dnssec_valid: Boolean,
    ): String?

    fun setProxy(address: String): Boolean {
        val accepted = setProxyJ(address)
        if (accepted) proxy = address
        return accepted
    }

    private external fun setProxyJ(address: String?): Boolean

    inner class WalletInfo(
        wallet: File,
    ) : Comparable<WalletInfo> {
        private val path: File? = wallet.parentFile
        private val name: String = wallet.name

        override fun compareTo(other: WalletInfo): Int =
            name
                .lowercase(Locale.getDefault())
                .compareTo(other.name.lowercase(Locale.getDefault()))
    }

    companion object {
        const val LOGLEVEL_SILENT = -1
        const val LOGLEVEL_WARN = 0
        const val LOGLEVEL_INFO = 1
        const val LOGLEVEL_DEBUG = 2
        const val LOGLEVEL_TRACE = 3
        const val LOGLEVEL_MAX = 4

        // no need to keep a reference to the REAL WalletManager (we get it every tvTime we need it)
        @get:Synchronized
        var instance: WalletManager? = null
            get() {
                if (field == null) {
                    field = WalletManager()
                }
                return field
            }
            private set

        init {
            System.loadLibrary("anonero")
        }

        fun addressPrefix(networkType: NetworkType): String =
            when (networkType) {
                NetworkType.NetworkType_Testnet -> "9A-"
                NetworkType.NetworkType_Mainnet -> "4-"
                NetworkType.NetworkType_Stagenet -> "5-"
            }

        fun resetInstance() {
            instance = null
        }

        @JvmStatic
        external fun initLogger(
            argv0: String?,
            defaultLogBaseName: String?,
        )

        @JvmStatic
        external fun setLogLevel(level: Int)

        @JvmStatic
        external fun logDebug(
            category: String?,
            message: String?,
        )

        @JvmStatic
        external fun logInfo(
            category: String?,
            message: String?,
        )

        @JvmStatic
        external fun logWarning(
            category: String?,
            message: String?,
        )

        @JvmStatic
        external fun logError(
            category: String?,
            message: String?,
        )

        @JvmStatic
        external fun moneroVersion(): String?
    }
}
