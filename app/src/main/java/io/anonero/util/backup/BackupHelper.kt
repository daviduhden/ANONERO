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
import io.anonero.model.WalletBackup
import io.anonero.model.WalletManager
import io.anonero.model.node.Node
import io.anonero.model.node.NodeFields
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

/**
 * Encrypted backup export and restore.
 *
 * The current writer emits a zip of the active wallet files plus `anon.json`, encrypted
 * with the v2/v3 AEAD envelope. The wallet copy is re-keyed to the seed passphrase so it
 * restores on another device; the live wallet is never re-keyed. Older backups remain
 * readable: v1 is the legacy `Salted__` CBC format, v2 is AEAD with PIN-keyed wallet files,
 * and v3 is AEAD with passphrase-keyed wallet files.
 *
 * The decoy profile is intentionally excluded from export: a backup restores into MAIN, and
 * exporting the decoy would either overwrite the legitimate wallet or leak decoy history.
 */
object BackupHelper {
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

    /**
     * Creates a v3 encrypted backup of the active MAIN wallet.
     *
     * Preconditions: a managed wallet exists and the active profile is not the decoy.
     * Side effects: pauses refresh, stores the wallet, copies only the active wallet's
     * files, re-keys the copy to [seedPassphrase], clears the cache, and leaves the live
     * wallet untouched. The returned file is in `cache/backup/`.
     */
    suspend fun createBackUp(
        seedPassphrase: String,
        context: Context,
    ): String {
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
            // Quiesce background sync before copying: MoneroHandlerThread calls store() on
            // every refresh, and a store() landing mid-copy throws. Pause + settle, copy,
            // then resume. The store() is what puts the live sync state into the cache file.
            wallet.pauseRefresh()
            // keep the live wallet paused until the copy is re-keyed and closed
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
                    val manager = WalletManager.instance ?: throw Exception("Wallet manager not found")
                    val copy = manager.openWalletUnmanaged(File(tmpBackupDir, walletName).path, w)
                    try {
                        if (!copy.status.isOk) {
                            throw Exception("backup copy did not open: ${copy.status.errorString}")
                        }
                        if (!copy.setPassword(seedPassphrase)) {
                            throw Exception("backup copy could not be re-keyed: ${copy.status.errorString}")
                        }
                        if (!copy.store()) {
                            throw Exception("backup copy could not be stored: ${copy.status.errorString}")
                        }
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
            // delete the unencrypted copies
            tmpBackupDir.deleteRecursively()
            backupFile.delete()
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
        passPhrase: String,
        pin: String,
    ): Boolean {
        val context = AnonConfig.context!!
        val walletFile = AnonConfig.getDefaultWalletFile(context)
        val walletName = walletFile.name
        val prefs = provideWalletSharedPrefs(context)
        val duress = prefs.getString(io.anonero.util.PREFS_DURESS_PIN_HASH, "")
        if (!duress.isNullOrEmpty() && PinHash.matches(pin, duress)) {
            Timber.tag(TAG).e("restore: PIN collides with the configured duress PIN")
            return false
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
            return false
        }
        val manager = WalletManager.instance ?: return false
        // openWallet throws when wallet2 cannot open the file. A failed passphrase must
        // still fall back to the PIN for legacy v2 backups, so do not let that exception
        // skip the second attempt.
        var openedWithPin = false
        var opened = runCatching { manager.openWallet(walletFile.path, passPhrase) }.getOrNull()
        if (opened?.status?.isOk != true) {
            opened?.let { runCatching { manager.close(it) } }
            openedWithPin = true
            opened = runCatching { manager.openWallet(walletFile.path, pin) }.getOrNull()
        }
        if (opened?.status?.isOk != true) {
            Timber.tag(TAG).e("restore: neither passphrase nor PIN opened the restored wallet")
            opened?.let { runCatching { manager.close(it) } }
            return false
        }
        val wallet = opened
        var bound = false
        return try {
            val w = WalletKey.newSecret()
            if (!wallet.setPassword(w)) {
                throw Exception("setPassword failed: ${wallet.status.errorString}")
            }
            if (!wallet.store()) {
                throw Exception("store failed after re-key")
            }
            WalletKey.store(context, walletName, w)
            val committed =
                prefs
                    .edit()
                    .putString(PREFS_PIN_HASH, PinHash.hash(pin))
                    .commit()
            if (!committed) throw Exception("could not persist the restored PIN hash")
            bound = true
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "restore: failed to bind wallet to device")
            // Best effort: put the in-memory wallet back on the credential that opened it
            // so a later store cannot leave the file keyed to an unrecorded secret.
            val original = if (openedWithPin) pin else passPhrase
            runCatching {
                wallet.setPassword(original)
                wallet.store()
            }
            false
        } finally {
            if (!bound) runCatching { manager.close(wallet) }
        }
    }

    fun extractBackUp(
        backupPath: String,
        passPhrase: String,
    ): BackupPayload? {
        val context = requireNotNull(AnonConfig.context)
        val destFile = File(context.cacheDir, "backup.anon")
        val decryptedDestFile = File(context.cacheDir, "backup.zip")
        val extractDestination = File(context.cacheDir, "tmp_extract")
        var extracted = false
        val backup: BackupPayload
        try {
            // A retry must not combine files from an earlier or partially extracted backup.
            BackupArchive.removeExtractionDirectory(extractDestination)
            val input =
                context.contentResolver.openInputStream(backupPath.toUri())
                    ?: throw java.io.IOException("Could not open backup")
            input.use { source ->
                destFile.outputStream().use { source.copyTo(it) }
            }
            EncryptUtil.decryptFile(passPhrase, destFile, decryptedDestFile)
            unZip(decryptedDestFile, extractDestination)

            val anonJson = File(extractDestination, "anon.json")
            val json = anonJson.readText()
            backup = Json.decodeFromString<BackupPayload>(json)
            if (backup.backup.meta.network != AnonConfig.getNetworkType().toStringForBackUp()) {
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
        return backup
    }

    /**
     * Restores an already-decrypted backup payload into MAIN.
     *
     * Requires an empty MAIN target. A failure after copying files removes only MAIN so a
     * configured decoy survives. The restored wallet is re-keyed to a fresh device secret
     * and bound to [pin]; the caller must have verified the passphrase and PIN.
     */
    suspend fun restoreBackUp(
        backupPayload: BackupPayload,
        passPhrase: String,
        pin: String,
    ): Boolean {
        try {
            val context = requireNotNull(AnonConfig.context)
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

            walletPrefs.edit(commit = true) {
                putString(
                    PREFS_PASSPHRASE_HASH,
                    KeyStoreHelper.getCrazyPass(context, passPhrase),
                )
                putLong(RESTORE_HEIGHT, restoreHeight)
            }
            val decoyName = WalletProfile.DECOY.walletName
            extractDestination.listFiles()?.forEach { entry ->
                if (entry.name == "anon.json") return@forEach
                // Never let an archive overwrite the decoy profile's files.
                if (entry.name == decoyName || entry.name.startsWith("$decoyName.")) return@forEach
                entry.copyTo(File(anonDir, entry.name), true)
            }
            val bound =
                try {
                    bindRestoredWallet(passPhrase, pin)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "restore: failed to bind restored wallet")
                    false
                }
            if (bound) {
                val restored = WalletManager.instance?.wallet
                val expectedAddress = wallet.primaryAddress.ifEmpty { wallet.address }
                val identityMatches =
                    restored != null &&
                        (expectedAddress.isEmpty() || restored.address == expectedAddress) &&
                        restored.isWatchOnly() == AnonConfig.viewOnly
                if (!identityMatches) {
                    Timber.tag(TAG).e("restore: restored wallet identity does not match backup")
                    restored?.let { runCatching { WalletManager.instance?.close(it) } }
                    WalletProfileStore.removeProfile(context, walletPrefs, WalletProfile.MAIN)
                    return false
                }
                // Persist node settings only after the wallet is bound. A failed restore must
                // not leave a malicious or stale v1 node as the active configuration.
                try {
                    walletPrefs.edit(commit = true) {
                        putString(NodeFields.RPC_HOST.value, v1Node.host)
                        putInt(NodeFields.RPC_PORT.value, v1Node.rpcPort)
                        putString(NodeFields.RPC_USERNAME.value, v1Node.username)
                        putString(NodeFields.RPC_PASSWORD.value, v1Node.password)
                        putString(NodeFields.RPC_NETWORK.value, v1Node.networkType)
                    }
                    nodes.forEach { nodeRepository.addItem(it) }
                    if (nodes.isEmpty() && v1Node.host.isNotEmpty()) {
                        val node =
                            Node().apply {
                                host = v1Node.host
                                rpcPort = v1Node.rpcPort
                                username = v1Node.username
                                password = v1Node.password
                                networkType = AnonConfig.getNetworkType()
                            }
                        nodeRepository.addItem(node)
                    }
                } catch (e: Exception) {
                    // The wallet is already restored and bound; a node-list failure must not
                    // remove it. The user can configure a node from Settings.
                    Timber.tag(TAG).w(e, "restore: failed to persist backup node settings")
                }
                return true
            }
            // Copied but unbound files open nowhere. Remove only the main profile: a
            // configured decoy wallet must survive a failed main restore.
            WalletProfileStore.removeProfile(
                AnonConfig.context!!,
                walletPrefs,
                WalletProfile.MAIN,
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
