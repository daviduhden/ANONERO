package io.anonero.util.backup

import android.content.Context
import android.content.SharedPreferences
import android.icu.text.SimpleDateFormat
import androidx.core.content.edit
import androidx.core.net.toUri
import io.anonero.AnonConfig
import io.anonero.R
import io.anonero.di.provideWalletSharedPrefs
import io.anonero.model.Backup
import io.anonero.model.BackupMeta
import io.anonero.model.BackupPayload
import io.anonero.model.NeroKeyPayload
import io.anonero.model.NodeBackup
import io.anonero.model.WalletBackup
import io.anonero.model.WalletManager
import io.anonero.model.node.Node
import io.anonero.model.node.NodeFields
import io.anonero.store.NodesRepository
import io.anonero.util.KeyStoreHelper
import io.anonero.util.PREFS_PIN_HASH
import io.anonero.util.WalletProfile
import io.anonero.util.WalletProfileStore
import io.anonero.util.WalletSession
import io.anonero.util.WalletKey
import io.anonero.util.PinHash
import io.anonero.util.PREFS_PASSPHRASE_HASH
import io.anonero.util.RESTORE_HEIGHT
import io.anonero.util.RestoreTarget
import io.anonero.util.RestoreTargetNotEmptyException
import io.anonero.util.WALLET_PREFERENCES
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NetworkMismatchException : Exception()
object BackupHelper {
    // 3: wallet files inside the zip are keyed to the seed passphrase (portable).
    // 2 and below: keyed to the PIN of the phone that made the backup -- which is also
    // what a wallet that has not migrated to the Keystore still exports.
    private const val BACKUP_VERSION = 3
    private const val BACKUP_VERSION_PIN_KEYED = 2
    const val BUFFER = 2048
    private const val TAG = "BackUpHelper"
    val walletPrefs: SharedPreferences by inject(
        SharedPreferences::class.java, named(WALLET_PREFERENCES)
    )
    val nodeRepository: NodesRepository by inject(NodesRepository::class.java)

    suspend fun createBackUp(seedPassphrase: String, context: Context): String {
        if (AnonConfig.viewOnly) {
            require(seedPassphrase.length >= 8) { "Backup passphrase is too short" }
        }
        if (WalletSession.isDecoy) {
            // A backup restores into the MAIN wallet path; exporting the decoy here
            // would produce a file that either replaces the legitimate wallet or leaks
            // decoy history into it. The decoy is recovered from its seed instead.
            throw Exception("Encrypted backup is not available for the decoy wallet; use its seed")
        }
        val wallet = WalletManager.instance?.wallet ?: throw Exception("Wallet not found")
        val prefs = provideWalletSharedPrefs(context)


        val walletPayload = WalletBackup(
            address = wallet.address,
            seed = wallet.getSeed(seedPassphrase),
            restoreHeight = wallet.getRestoreHeight(),
            balanceAll = wallet.getBalanceAll(),
            numSubaddresses = wallet.numSubAddresses,
            numAccounts = wallet.getNumAccounts(),
            primaryAddress = wallet.address,
            isWatchOnly = wallet.isWatchOnly(),
            isSynchronized = wallet.isSynchronized,
            neroPayload = NeroKeyPayload.fromWallet(wallet),
        );


        val nodeBackup = NodeBackup(
            host = prefs.getString(NodeFields.RPC_HOST.value, "") ?: "",
            password = prefs.getString(NodeFields.RPC_PASSWORD.value, "") ?: "",
            username = prefs.getString(NodeFields.RPC_USERNAME.value, "") ?: "",
            rpcPort = prefs.getInt(NodeFields.RPC_PORT.value, Node.defaultRpcPort),
            networkType = AnonConfig.getNetworkType().toString(),
            isOnion = false
        )
        val backup = Backup(
            meta = BackupMeta(
                timestamp = System.currentTimeMillis(),
                network = AnonConfig.getNetworkType().toStringForBackUp(),
            ), node = nodeBackup, wallet = walletPayload,
            nodes = nodeRepository.getAll()
        )

        // The version tells restore which key opens the wallet files. A keystore-bound
        // wallet's copy is re-keyed to the passphrase below (v3); a wallet that never
        // migrated is still PIN-keyed and must say so (v2), or restore asks for a new PIN
        // instead of the one the files are locked with. If the device secret exists but
        // cannot be loaded, fail the export rather than write a backup nothing can open.
        val walletName = WalletProfileStore.activeWalletName(context)
        val w = if (WalletKey.exists(context, walletName)) WalletKey.load(context, walletName) else null
        val backUpPayload = BackupPayload(
            version = if (w != null) BACKUP_VERSION else BACKUP_VERSION_PIN_KEYED,
            backup = backup,
        )
        val json = Json.encodeToString(backUpPayload)

        context.cacheDir.deleteRecursively()

        val tmpBackupDir = File(context.cacheDir, "tmp_backup")
        if (tmpBackupDir.exists()) {
            tmpBackupDir.deleteRecursively()
        }
        val date = Date()
        val sdf = SimpleDateFormat("dd_MM_yyyy' 'HH_mm_a", Locale.getDefault())
        val timeStamp: String = sdf.format(date)
        val backupFile = File(context.cacheDir, "${R.string.app_name}_backup_$timeStamp.zip")
        tmpBackupDir.mkdirs()
        try {
            val tmpBackupFile = File(tmpBackupDir, "anon.json")
            tmpBackupFile.writeText(json)
            val walletDir = File(context.filesDir, "wallets")
            // Quiesce background sync before copying: MoneroHandlerThread calls store() on
            // every refresh, and a store() landing mid-copy throws. Pause + settle, copy,
            // then resume. The store() is what puts the live sync state into the cache file.
            wallet.pauseRefresh()
            wallet.store()
            // keep the live wallet paused until the copy is re-keyed and closed
            try {
                // Copy only the active wallet's files. Copying the whole directory would
                // put the decoy wallet (if configured) inside a backup of the legitimate
                // wallet, which destroys the separation between the two profiles.
                val walletFiles = walletDir.listFiles()
                    ?: throw java.io.IOException("Could not list wallet files")
                walletFiles.filter {
                    it.isFile && (it.name == walletName || it.name.startsWith("$walletName."))
                }.forEach { it.copyTo(File(tmpBackupDir, it.name), overwrite = true) }
                // The live wallet is keyed to the device secret W, which exists only in this
                // phone's Keystore, so a raw copy opens nowhere else. Re-key the COPY to the seed
                // passphrase -- the same one that decrypts the zip -- so the backup restores on
                // any device, with its full cache (history, sync state). The live wallet is never
                // touched: nothing here can leave it in a state the Keystore cannot open. A legacy
                // wallet that never migrated has no W and is still PIN-keyed; it is left as is.
                if (w != null) {
                    val manager = WalletManager.instance ?: throw Exception("Wallet manager not found")
                    val copy = manager.openWalletUnmanaged(File(tmpBackupDir, walletName).path, w)
                    try {
                        if (!copy.status.isOk) {
                            throw Exception("backup copy did not open: ${copy.status.errorString}")
                        }
                        if (!copy.setPassword(seedPassphrase)) {
                            throw Exception("backup copy could not be re-keyed: ${copy.status.errorString}")
                        }
                        copy.store()
                    } finally {
                        manager.closeUnmanaged(copy)
                    }
                }
            } finally {
                if (wallet.isInitialized) wallet.startRefresh()
            }
            if (!testBackUp(tmpBackupDir)) {
                throw java.io.IOException("Backup is missing required wallet files")
            }
            val files = tmpBackupDir.listFiles()?.map { it.absolutePath }?.toTypedArray()
                ?: throw java.io.IOException("Could not list backup files")
            val backupCacheDir = File(context.cacheDir, "backup")
            if (!backupCacheDir.exists()) {
                backupCacheDir.mkdirs()
            }
            val backupFileEncrypted =
                File(backupCacheDir, "${R.string.app_name}_backup_$timeStamp.anon")

            zip(files, backupFile.absolutePath)
            EncryptUtil.encryptFile(seedPassphrase, backupFile, backupFileEncrypted)
            return backupFileEncrypted.absolutePath
        } finally {
            // delete the unencrypted copies
            tmpBackupDir.deleteRecursively()
            backupFile.delete()
        }
    }

    fun testBackUp(destinationDir: File): Boolean {
        return BackupArchive.isValidDirectory(destinationDir, WalletProfile.MAIN.walletName)
    }

    private fun zip(files: Array<String>, zipFileName: String) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFileName))).use { output ->
            files.forEach { path ->
                File(path).inputStream().buffered(BUFFER).use { input ->
                    output.putNextEntry(ZipEntry(File(path).name))
                    input.copyTo(output, BUFFER)
                    output.closeEntry()
                }
            }
        }
    }

    fun unZip(toUnzip: File, destinationDir: File) {
        BackupArchive.extract(toUnzip, destinationDir, WalletProfile.MAIN.walletName)
    }


    /**
     * Binds freshly-restored wallet files to this device. A version-3 backup's files are
     * keyed to the seed passphrase; an older backup's files are keyed to the PIN of the
     * phone that made it, which the caller has prompted for. Open with whichever works,
     * re-encrypt to a fresh device secret, wrap it in the Keystore, and set the PIN gate.
     * The full snapshot (tx history, sync state) is preserved -- no re-scan.
     */
    private fun bindRestoredWallet(passPhrase: String, pin: String): Boolean {
        val context = AnonConfig.context!!
        val walletFile = AnonConfig.getDefaultWalletFile(context)
        val walletName = walletFile.name
        val prefs = provideWalletSharedPrefs(context)
        val duress = prefs.getString(io.anonero.util.PREFS_DURESS_PIN_HASH, "")
        if (!duress.isNullOrEmpty() && PinHash.matches(pin, duress)) {
            Timber.tag(TAG).e("restore: PIN collides with the configured duress PIN")
            return false
        }
        val decoyPass = prefs.getString(
            io.anonero.util.PREFS_DECOY_PASSPHRASE_HASH, ""
        )
        if (!decoyPass.isNullOrEmpty() &&
            KeyStoreHelper.getCrazyPass(context, passPhrase) == decoyPass
        ) {
            Timber.tag(TAG).e("restore: passphrase collides with the decoy wallet")
            return false
        }
        val manager = WalletManager.instance ?: return false
        var opened = manager.openWallet(walletFile.path, passPhrase)
        if (!opened.status.isOk) {
            runCatching { manager.close(opened) }
            opened = manager.openWallet(walletFile.path, pin)
        }
        if (!opened.status.isOk) {
            Timber.tag(TAG).e("restore: neither passphrase nor PIN opened the restored wallet")
            runCatching { manager.close(opened) }
            return false
        }
        return try {
            val w = WalletKey.newSecret()
            if (!opened.setPassword(w)) {
                throw Exception("setPassword failed: ${opened.status?.errorString}")
            }
            opened.store()
            WalletKey.store(context, walletName, w)
            prefs.edit(commit = true) { putString(PREFS_PIN_HASH, PinHash.hash(pin)) }
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e("restore: failed to bind wallet to device")
            false
        } finally {
            runCatching { manager.close(opened) }
        }
    }

    fun extractBackUp(backupPath: String, passPhrase: String): BackupPayload? {
        val context = requireNotNull(AnonConfig.context)
        val destFile = File(context.cacheDir, "backup.anon")
        val decryptedDestFile = File(context.cacheDir, "backup.zip")
        val extractDestination = File(context.cacheDir, "tmp_extract")
        var extracted = false
        val backup: BackupPayload
        try {
            // A retry must not combine files from an earlier or partially extracted backup.
            BackupArchive.removeExtractionDirectory(extractDestination)
            val input = context.contentResolver.openInputStream(backupPath.toUri())
                ?: throw java.io.IOException("Could not open backup")
            input.use { source ->
                destFile.outputStream().use { source.copyTo(it) }
            }
            EncryptUtil.decryptFile(passPhrase, destFile, decryptedDestFile)
            unZip(decryptedDestFile, extractDestination)

            val anonJson = File(extractDestination, "anon.json")
            val json = anonJson.readText()
            backup = Json.decodeFromString<BackupPayload>(json)
            if(backup.backup.meta.network != AnonConfig.getNetworkType().toStringForBackUp()){
                 throw NetworkMismatchException()
            }
            extracted = true
        } catch (e: Exception) {
            // Parse errors can contain decrypted metadata; never persist their text.
            Timber.tag(TAG).e("Backup extraction failed")
            throw e
        } finally {
            // the decrypted zip holds the seed in the clear; the extracted copy that the
            // restore needs is removed when the preview screen goes away
            destFile.delete()
            decryptedDestFile.delete()
            if (!extracted) BackupArchive.removeExtractionDirectory(extractDestination)
        }
        return backup;
    }

    suspend fun restoreBackUp(backupPayload: BackupPayload, passPhrase: String, pin: String): Boolean {
        try {
            val context = requireNotNull(AnonConfig.context)
            RestoreTarget.requireEmpty(
                directory = File(context.filesDir, "wallets"),
                walletName = WalletProfile.MAIN.walletName,
                hasWrappedSecret = WalletKey.exists(context, WalletProfile.MAIN.walletName),
                hasCredentials = walletPrefs.contains(PREFS_PIN_HASH) ||
                    walletPrefs.contains(PREFS_PASSPHRASE_HASH) || walletPrefs.contains(RESTORE_HEIGHT),
            )

            val extractDestination = File(context.cacheDir, "tmp_extract")
            if (!testBackUp(extractDestination)) {
                return false
            }

            val anonDir = AnonConfig.getDefaultWalletDir(context)

            val v1Node = backupPayload.backup.node
            walletPrefs.edit(commit = true) {
                putString(NodeFields.RPC_HOST.value, v1Node.host)
                putInt(NodeFields.RPC_PORT.value, v1Node.rpcPort)
                putString(NodeFields.RPC_USERNAME.value, v1Node.username)
                putString(NodeFields.RPC_PASSWORD.value, v1Node.password)
                putString(NodeFields.RPC_NETWORK.value, v1Node.networkType)
            }

            val wallet = backupPayload.backup.wallet
            val nodes = backupPayload.backup.nodes;

            nodes.forEach {
                nodeRepository.addItem(it)
            }
            if (nodes.isEmpty() && v1Node.host.isNotEmpty()) {
                val node = Node().apply {
                    host = v1Node.host
                    rpcPort = v1Node.rpcPort
                    username = v1Node.username
                    password = v1Node.password
                    networkType = AnonConfig.getNetworkType()
                }
                nodes.forEach {
                    nodeRepository.addItem(node)
                }
            }
            walletPrefs.edit(commit = true) {
                putString(
                    PREFS_PASSPHRASE_HASH,
                    KeyStoreHelper.getCrazyPass(AnonConfig.context, passPhrase)
                )
                putLong(RESTORE_HEIGHT, wallet.restoreHeight ?: 0L)
            }
            val decoyName = WalletProfile.DECOY.walletName
            extractDestination.listFiles()?.forEach { entry ->
                if (entry.name == "anon.json") return@forEach
                // Never let an archive overwrite the decoy profile's files.
                if (entry.name == decoyName || entry.name.startsWith("$decoyName.")) return@forEach
                entry.copyTo(File(anonDir, entry.name), true)
            }
            if (bindRestoredWallet(passPhrase, pin)) return true
            // Copied but unbound files open nowhere. Remove only the main profile: a
            // configured decoy wallet must survive a failed main restore.
            WalletProfileStore.removeProfile(
                AnonConfig.context!!, walletPrefs, WalletProfile.MAIN
            )
            return false
        } catch (e: RestoreTargetNotEmptyException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e("Backup restore failed")
            return false
        }
    }

    fun cleanCacheDir() {
        AnonConfig.context?.cacheDir?.deleteRecursively()
    }
}
