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
import io.anonero.model.Wallet
import io.anonero.model.WalletBackup
import io.anonero.model.WalletManager
import io.anonero.model.node.Node
import io.anonero.model.node.NodeFields
import io.anonero.services.WalletState
import io.anonero.store.NodesRepository
import io.anonero.util.KeyStoreHelper
import io.anonero.util.MAX_RESTORE_HEIGHT
import io.anonero.util.PREFS_PASSPHRASE_HASH
import io.anonero.util.PREFS_PIN_HASH
import io.anonero.util.PinHash
import io.anonero.util.RESTORE_HEIGHT
import io.anonero.util.RestoreTarget
import io.anonero.util.RestoreTargetNotEmptyException
import io.anonero.util.WALLET_PREFERENCES
import io.anonero.util.WalletKey
import io.anonero.util.WalletProfile
import io.anonero.util.WalletProfileStore
import io.anonero.util.WalletSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NetworkMismatchException : Exception()

/**
 * Encrypted backup export and restore.
 *
 * Export writes the wallet files and `anon.json` inside an `ANONBAK2` envelope. The payload
 * version describes the credential needed to reopen the copied wallet files. A copy of a
 * Keystore-bound wallet is re-keyed to the seed passphrase (v3). A copy of a legacy
 * PIN-keyed wallet keeps its original PIN (v2). Restore also accepts the older
 * `Salted__` CBC envelope.
 *
 * The decoy profile is intentionally excluded from export: a backup restores into MAIN, and
 * exporting the decoy would either overwrite the legitimate wallet or leak decoy history.
 */
object BackupHelper {
    private val backupMutex = Mutex()

    // The preview keeps the payload alive. The process-wide helper needs only its identity.
    private var activePreview: Pair<Job, WeakReference<BackupPayload>>? = null

    // 3: wallet files inside the zip are keyed to the seed passphrase (portable).
    // 2 and below: keyed to the PIN of the phone that made the backup -- which is also
    // what a wallet that has not migrated to the Keystore still exports.
    private const val BACKUP_VERSION = 3
    private const val BACKUP_VERSION_PIN_KEYED = 2
    const val BUFFER = 2048
    private const val TAG = "BackUpHelper"
    val walletPrefs: SharedPreferences by inject(
        SharedPreferences::class.java,
        named(WALLET_PREFERENCES),
    )
    val nodeRepository: NodesRepository by inject(NodesRepository::class.java)
    private val walletState: WalletState by inject(WalletState::class.java)

    /**
     * Exports the managed MAIN wallet in the full-wallet build.
     *
     * Pauses refresh and stores the wallet before copying profile files. A Keystore-bound
     * copy is re-keyed to [seedPassphrase] and must close successfully before the archive
     * is published. [seedPassphrase] also encrypts the archive. The returned path is in
     * `cache/backup/`.
     */
    suspend fun createBackUp(
        seedPassphrase: String,
        context: Context,
    ): String = backupMutex.withLock { createBackupLocked(seedPassphrase, context) }

    private suspend fun createBackupLocked(
        seedPassphrase: String,
        context: Context,
    ): String {
        check(!AnonConfig.viewOnly) { "Encrypted backup export is unavailable in the view-only build" }
        if (WalletSession.isDecoy) {
            // A backup restores into the MAIN wallet path; exporting the decoy here
            // would produce a file that either replaces the legitimate wallet or leaks
            // decoy history into it. The decoy is recovered from its seed instead.
            throw Exception("Encrypted backup is not available for the decoy wallet; use its seed")
        }
        val manager = checkNotNull(WalletManager.instance) { "Wallet manager unavailable" }
        val scratch = File(context.cacheDir, "tmp_backup")
        check(manager.retryUnmanagedClose(File(scratch, WalletProfile.MAIN.walletName).path)) {
            "Previous backup wallet could not be closed"
        }
        val wallet = WalletManager.instance?.wallet ?: throw Exception("Wallet not found")
        val prefs = provideWalletSharedPrefs(context)

        val walletPayload =
            WalletBackup(
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
            )

        val nodeBackup =
            NodeBackup(
                host = prefs.getString(NodeFields.RPC_HOST.value, "") ?: "",
                password = prefs.getString(NodeFields.RPC_PASSWORD.value, "") ?: "",
                username = prefs.getString(NodeFields.RPC_USERNAME.value, "") ?: "",
                rpcPort = prefs.getInt(NodeFields.RPC_PORT.value, Node.defaultRpcPort),
                networkType = AnonConfig.getNetworkType().toString(),
                isOnion = false,
            )
        val backup =
            Backup(
                meta =
                    BackupMeta(
                        timestamp = System.currentTimeMillis(),
                        network = AnonConfig.getNetworkType().toStringForBackUp(),
                    ),
                node = nodeBackup,
                wallet = walletPayload,
                nodes = nodeRepository.getAll(),
            )

        // The version tells restore which key opens the wallet files. A keystore-bound
        // wallet's copy is re-keyed to the passphrase below (v3); a wallet that never
        // migrated is still PIN-keyed and must say so (v2), or restore asks for a new PIN
        // instead of the one the files are locked with. If the device secret exists but
        // cannot be loaded, fail the export rather than write a backup nothing can open.
        val walletName = WalletProfileStore.activeWalletName(context)
        val w = if (WalletKey.exists(context, walletName)) WalletKey.load(context, walletName) else null
        val backUpPayload =
            BackupPayload(
                version = if (w != null) BACKUP_VERSION else BACKUP_VERSION_PIN_KEYED,
                backup = backup,
            )
        val json = Json.encodeToString(backUpPayload)

        // Clear only transient backup/airgapped artifacts. Deleting the whole cache would
        // also remove the live Tor cache and the cache log file while Tor may be running.
        AnonConfig.clearSpendCacheFiles(context)
        activePreview = null
        listOf("tmp_backup", "backup", "backup.zip", "backup.anon", "tmp_extract").forEach { name ->
            File(context.cacheDir, name).deleteRecursively()
        }

        val tmpBackupDir = File(context.cacheDir, "tmp_backup")
        if (tmpBackupDir.exists()) {
            tmpBackupDir.deleteRecursively()
        }
        val date = Date()
        val sdf = SimpleDateFormat("dd_MM_yyyy' 'HH_mm_a", Locale.getDefault())
        val timeStamp: String = sdf.format(date)
        // Keep the unencrypted ZIP inside the transient tmp_backup directory so a crash
        // between zip() and encryptFile() is cleaned by the startup purge. Never leave the
        // seed-bearing archive in the cache root.
        val backupFile = File(tmpBackupDir, "backup.zip")
        tmpBackupDir.mkdirs()
        try {
            val tmpBackupFile = File(tmpBackupDir, "anon.json")
            tmpBackupFile.writeText(json)
            val walletDir = File(context.filesDir, "wallets")
            // Pause refresh before storing the latest sync state to disk. Keep refresh
            // paused while the copy is made and any auxiliary wallet holds it open.
            wallet.pauseRefresh()
            try {
                if (!wallet.store()) throw java.io.IOException("Could not store the wallet")
                // Copy only the active wallet's files. Copying the whole directory would
                // put the decoy wallet (if configured) inside a backup of the legitimate
                // wallet, which destroys the separation between the two profiles.
                val walletFiles =
                    walletDir.listFiles()
                        ?: throw java.io.IOException("Could not list wallet files")
                walletFiles
                    .filter {
                        it.isFile && (
                            it.name == walletName ||
                                it.name == "$walletName.keys" ||
                                it.name == "$walletName.address.txt"
                        )
                    }.forEach { it.copyTo(File(tmpBackupDir, it.name), overwrite = true) }
                // The live wallet is keyed to the device secret W, which exists only in this
                // phone's Keystore, so a raw copy opens nowhere else. Re-key the COPY to the seed
                // passphrase -- the same one that decrypts the zip -- so the backup restores on
                // any device, with its full cache (history, sync state). The live wallet is never
                // touched: nothing here can leave it in a state the Keystore cannot open. A legacy
                // wallet that never migrated has no W and is still PIN-keyed; it is left as is.
                if (w != null) {
                    val copy = manager.openWalletUnmanaged(File(tmpBackupDir, walletName).path, w)
                    var copyFailure: Throwable? = null
                    try {
                        if (!copy.status.isOk) {
                            throw Exception("backup copy did not open")
                        }
                        if (!copy.setPassword(seedPassphrase)) {
                            throw Exception("backup copy could not be re-keyed")
                        }
                        if (!copy.store()) {
                            throw Exception("backup copy could not be stored")
                        }
                    } catch (failure: Throwable) {
                        copyFailure = failure
                        throw failure
                    } finally {
                        try {
                            check(manager.closeUnmanaged(copy)) { "Backup wallet could not be closed" }
                        } catch (cleanup: Exception) {
                            val failure = copyFailure
                            if (failure == null) throw cleanup
                            failure.addSuppressed(cleanup)
                        }
                    }
                }
            } finally {
                if (manager.wallet === wallet && WalletSession.profile == WalletProfile.MAIN &&
                    !walletState.offline && !walletState.backgroundSync &&
                    walletState.isTransportApplied(wallet) && wallet.isInitialized
                ) {
                    wallet.startRefresh()
                }
            }
            if (!testBackUp(tmpBackupDir)) {
                throw java.io.IOException("Backup is missing required wallet files")
            }
            val files =
                tmpBackupDir.listFiles()?.map { it.absolutePath }?.toTypedArray()
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
            // wallet2 may still write this copy after a failed close. Preserve its
            // files inside tmp_backup until an explicit close retry or process restart.
            // The seed-bearing manifest/archive are not native backing files.
            File(tmpBackupDir, "anon.json").delete()
            backupFile.delete()
            if (!manager.hasUnmanagedWalletIn(tmpBackupDir)) {
                tmpBackupDir.deleteRecursively()
            }
        }
    }

    fun testBackUp(destinationDir: File): Boolean = BackupArchive.isValidDirectory(destinationDir, WalletProfile.MAIN.walletName)

    private fun zip(
        files: Array<String>,
        zipFileName: String,
    ) {
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

    fun unZip(
        toUnzip: File,
        destinationDir: File,
    ) {
        BackupArchive.extract(toUnzip, destinationDir, WalletProfile.MAIN.walletName)
    }

    /*
     * Binds freshly-restored wallet files to this device. A version-3 backup's files are
     * keyed to the seed passphrase; an older backup's files are keyed to the PIN of the
     * phone that made it, which the caller has prompted for. Open with whichever works,
     * re-encrypt to a fresh device secret, wrap it in the Keystore, and set the PIN gate.
     * The full snapshot (tx history, sync state) is preserved -- no re-scan.
     */
    private fun bindRestoredWallet(
        manager: WalletManager,
        passPhrase: String,
        pin: String,
        restoreHeight: Long,
    ): Wallet? {
        val context = AnonConfig.context!!
        val walletFile = AnonConfig.getDefaultWalletFile(context)
        val walletName = walletFile.name
        val prefs = provideWalletSharedPrefs(context)
        val duress = prefs.getString(io.anonero.util.PREFS_DURESS_PIN_HASH, "")
        if (!duress.isNullOrEmpty() && PinHash.matches(pin, duress)) {
            Timber.tag(TAG).e("restore: PIN collides with the configured duress PIN")
            return null
        }
        val decoyPass =
            prefs.getString(
                io.anonero.util.PREFS_DECOY_PASSPHRASE_HASH,
                "",
            )
        if (!decoyPass.isNullOrEmpty() &&
            KeyStoreHelper.getCrazyPass(context, passPhrase) == decoyPass
        ) {
            Timber.tag(TAG).e("restore: passphrase collides with the decoy wallet")
            return null
        }
        // An unrecognized credential may leave a non-OK wallet under management.
        // Confirm its close before trying a legacy PIN.
        var openedWithPin = false
        var opened = openRestoredWallet(manager, walletFile, passPhrase)
        if (opened?.status?.isOk != true) {
            if (opened != null && !closeRestoredWallet(manager, opened)) return null
            openedWithPin = true
            opened = openRestoredWallet(manager, walletFile, pin)
        }
        if (opened?.status?.isOk != true) {
            Timber.tag(TAG).e("restore: neither passphrase nor PIN opened the restored wallet")
            if (opened != null) closeRestoredWallet(manager, opened)
            return null
        }
        val wallet = opened
        var bound = false
        return try {
            val w = WalletKey.newSecret()
            if (!wallet.setPassword(w)) {
                throw Exception("setPassword failed")
            }
            if (!wallet.store()) {
                throw Exception("store failed after re-key")
            }
            WalletKey.store(context, walletName, w)
            val committed =
                prefs
                    .edit()
                    .putString(PREFS_PIN_HASH, PinHash.hash(pin))
                    .putString(PREFS_PASSPHRASE_HASH, KeyStoreHelper.getCrazyPass(context, passPhrase))
                    .putLong(RESTORE_HEIGHT, restoreHeight)
                    .commit()
            if (!committed) throw Exception("could not persist the restored PIN hash")
            bound = true
            wallet
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Timber.tag(TAG).e("restore: failed to bind wallet to device")
            // Best effort: put the in-memory wallet back on the credential that opened it
            // so a later store cannot leave the file keyed to an unrecorded secret.
            val original = if (openedWithPin) pin else passPhrase
            val restored = runCatching { wallet.setPassword(original) && wallet.store() }.getOrDefault(false)
            if (!restored) Timber.tag(TAG).e("Restore credential rollback failed")
            null
        } finally {
            if (!bound) closeRestoredWallet(manager, wallet)
        }
    }

    private fun openRestoredWallet(
        manager: WalletManager,
        walletFile: File,
        credential: String,
    ): Wallet? {
        check(manager.isManagedWalletIdle()) { "Another wallet is already managed" }
        return try {
            manager.openWallet(walletFile.path, credential)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            if (manager.wallet != null) throw e
            // WalletManager reports a missing native handle with IllegalStateException.
            null
        }
    }

    private fun closeRestoredWallet(
        manager: WalletManager,
        wallet: Wallet,
    ): Boolean {
        val closed =
            try {
                manager.close(wallet)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        if (!closed) Timber.tag(TAG).e("Restore wallet close failed")
        return closed
    }

    suspend fun extractBackUp(
        previewOwner: Job,
        backupPath: String,
        passPhrase: String,
    ): BackupPayload =
        backupMutex.withLock {
            previewOwner.ensureActive()
            val context = requireNotNull(AnonConfig.context)
            val extractDestination = File(context.cacheDir, "tmp_extract")
            activePreview = null
            try {
                // A retry must not combine files from different backups.
                BackupArchive.removeExtractionDirectory(extractDestination)
                BackupArchive.withTemporaryRestoreArchive(context.cacheDir) { scratch ->
                    val destFile = File(scratch, "backup.anon")
                    val decryptedDestFile = File(scratch, "backup.zip")
                    val input =
                        context.contentResolver.openInputStream(backupPath.toUri())
                            ?: throw java.io.IOException("Could not open backup")
                    input.use { source ->
                        destFile.outputStream().use { source.copyTo(it) }
                    }
                    EncryptUtil.decryptFile(passPhrase, destFile, decryptedDestFile)
                    unZip(decryptedDestFile, extractDestination)
                }
                val json = File(extractDestination, "anon.json").readText()
                val backup = Json.decodeFromString<BackupPayload>(json)
                if (backup.backup.meta.network != AnonConfig.getNetworkType().toStringForBackUp()) {
                    throw NetworkMismatchException()
                }
                currentCoroutineContext().ensureActive()
                previewOwner.ensureActive()
                activePreview = previewOwner to WeakReference(backup)
                backup
            } catch (failure: Throwable) {
                try {
                    BackupArchive.removeExtractionDirectory(extractDestination)
                } catch (cleanup: Exception) {
                    failure.addSuppressed(cleanup)
                }
                // Parse errors can contain decrypted metadata.
                if (failure !is CancellationException) Timber.tag(TAG).e("Backup extraction failed")
                throw failure
            }
        }

    suspend fun discardRestorePreview(previewOwner: Job) {
        backupMutex.withLock {
            if (activePreview?.first !== previewOwner) return
            val context = requireNotNull(AnonConfig.context)
            BackupArchive.removeExtractionDirectory(File(context.cacheDir, "tmp_extract"))
            activePreview = null
        }
    }

    /**
     * Restores an already-decrypted backup payload into MAIN.
     *
     * Requires an empty MAIN target. Rollback touches only MAIN and keeps its files
     * and credentials while the failed restore wallet is still under management.
     * The restored wallet is re-keyed to a fresh device secret and bound to [pin].
     */
    suspend fun restoreBackUp(
        backupPayload: BackupPayload,
        passPhrase: String,
        pin: String,
    ): Boolean =
        WalletProfileStore.provisioning.withLock {
            backupMutex.withLock {
                // A newer preview can replace the staged files between preview and restore.
                val preview = activePreview ?: return false
                if (preview.second.get() !== backupPayload) return false
                preview.first.ensureActive()
                restoreBackupLocked(backupPayload, passPhrase, pin)
            }
        }

    private suspend fun restoreBackupLocked(
        backupPayload: BackupPayload,
        passPhrase: String,
        pin: String,
    ): Boolean {
        try {
            val context = requireNotNull(AnonConfig.context)
            val manager = checkNotNull(WalletManager.instance) { "Wallet manager unavailable" }
            if (!manager.isManagedWalletIdle()) return false
            RestoreTarget.requireEmpty(
                directory = File(context.filesDir, "wallets"),
                walletName = WalletProfile.MAIN.walletName,
                hasWrappedSecret = WalletKey.exists(context, WalletProfile.MAIN.walletName),
                hasCredentials =
                    walletPrefs.contains(PREFS_PIN_HASH) ||
                        walletPrefs.contains(PREFS_PASSPHRASE_HASH) || walletPrefs.contains(RESTORE_HEIGHT),
            )

            val extractDestination = File(context.cacheDir, "tmp_extract")
            if (!testBackUp(extractDestination)) {
                return false
            }

            val anonDir = AnonConfig.getDefaultWalletDir(context)

            val wallet = backupPayload.backup.wallet
            val restoreHeight = wallet.restoreHeight ?: 0L
            require(restoreHeight in 0..MAX_RESTORE_HEIGHT) { "Invalid restore height" }
            val v1Node = backupPayload.backup.node
            val nodes = backupPayload.backup.nodes
            // Backup JSON is untrusted. Do not let a malformed host or port reach wallet2
            // or SharedPreferences; drop invalid records and keep the wallet restore.
            val v1Host = BackupNodePolicy.normalizeHost(v1Node.host)
            val v1Port = BackupNodePolicy.normalizePort(v1Node.rpcPort)
            val safeNodes =
                nodes.mapNotNull { node ->
                    val host = BackupNodePolicy.normalizeHost(node.host) ?: return@mapNotNull null
                    val port = BackupNodePolicy.normalizePort(node.rpcPort) ?: return@mapNotNull null
                    node.host = host
                    node.rpcPort = port
                    node.networkType = AnonConfig.getNetworkType()
                    node
                }
            if (safeNodes.size != nodes.size) {
                Timber.tag(TAG).w("restore: dropped malformed backup node records")
            }

            val main = WalletProfile.MAIN.walletName
            val installed =
                manager.withWalletPathClosed(File(anonDir, main).path) {
                    BackupArchive.installWalletFiles(extractDestination, anonDir, main)
                    true
                }
            if (!installed) return false
            val restored =
                try {
                    bindRestoredWallet(manager, passPhrase, pin, restoreHeight)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Timber.tag(TAG).e("restore: failed to bind restored wallet")
                    null
                }
            if (restored != null) {
                if (manager.wallet !== restored) return false
                val expectedAddress = wallet.primaryAddress.ifEmpty { wallet.address }
                val identityMatches =
                    (expectedAddress.isEmpty() || restored.address == expectedAddress) &&
                        restored.isWatchOnly() == AnonConfig.viewOnly
                if (!identityMatches) {
                    Timber.tag(TAG).e("restore: restored wallet identity does not match backup")
                    if (closeRestoredWallet(manager, restored) && manager.isManagedWalletIdle()) {
                        WalletProfileStore.removeProfile(context, walletPrefs, WalletProfile.MAIN)
                    }
                    return false
                }
                // Persist node settings only after the wallet is bound. A failed restore must
                // not leave a malicious or stale v1 node as the active configuration.
                try {
                    if (v1Host != null && v1Port != null) {
                        walletPrefs.edit(commit = true) {
                            putString(NodeFields.RPC_HOST.value, v1Host)
                            putInt(NodeFields.RPC_PORT.value, v1Port)
                            putString(NodeFields.RPC_USERNAME.value, v1Node.username)
                            putString(NodeFields.RPC_PASSWORD.value, v1Node.password)
                            putString(NodeFields.RPC_NETWORK.value, AnonConfig.getNetworkType().toString())
                        }
                    }
                    safeNodes.forEach { nodeRepository.addItem(it) }
                    if (safeNodes.isEmpty() && v1Host != null && v1Port != null) {
                        val node =
                            Node().apply {
                                host = v1Host
                                rpcPort = v1Port
                                username = v1Node.username
                                password = v1Node.password
                                networkType = AnonConfig.getNetworkType()
                            }
                        nodeRepository.addItem(node)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The wallet is already restored and bound; a node-list failure must not
                    // remove it. The user can configure a node from Settings.
                    Timber.tag(TAG).w("restore: failed to persist backup node settings")
                }
                return true
            }
            // A failed close leaves the native wallet owned. Keep its backing files
            // and any recorded credentials so a later close can complete.
            if (manager.isManagedWalletIdle()) {
                WalletProfileStore.removeProfile(context, walletPrefs, WalletProfile.MAIN)
            }
            return false
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestoreTargetNotEmptyException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e("Backup restore failed")
            return false
        }
    }

    fun cleanCacheDir(preserveTransport: Boolean = false) {
        check(backupMutex.tryLock()) { "Backup operation is active" }
        try {
            val cache = checkNotNull(AnonConfig.context) { "Application context unavailable" }.cacheDir
            check(WalletManager.instance?.hasUnmanagedWalletIn(cache) != true) {
                "Auxiliary wallet must be closed before clearing cache"
            }
            val targets =
                if (preserveTransport) {
                    cache.listFiles()?.filter { it.name != "tor" }
                        ?: throw java.io.IOException("Could not list cache files")
                } else {
                    listOf(cache)
                }
            activePreview = null
            var removed = true
            targets.forEach { if (!it.deleteRecursively()) removed = false }
            check(removed) { "Could not clear temporary files" }
        } finally {
            backupMutex.unlock()
        }
    }
}
