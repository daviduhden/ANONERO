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

package io.anonero.util

import android.content.Context
import android.util.Base64
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
import java.security.SecureRandom

/**
 * A bound wallet uses 32 random bytes encoded as its wallet2 password. Android
 * Keystore wraps that password under a per-wallet key. This protects against copying
 * wallet files and preferences without also obtaining access to the Keystore key.
 *
 * Callers enforce the app PIN before loading the password. Code executing in the app's
 * context can request the unwrap and receive the password in memory.
 */
object WalletKey {
    private const val LEN = 32 // 256-bit

    // A fresh random secret, base64 so it is a plain wallet2 password string.
    fun newSecret(): String = Base64.encodeToString(ByteArray(LEN).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)

    // Wraps [secret] under the wallet's AES-256-GCM Keystore key (StrongBox where available).
    fun store(
        context: Context,
        wallet: String,
        secret: String,
    ) = KeyStoreHelper.saveWalletSecret(context, wallet, secret)

    // Unwraps the wallet's secret; throws if missing/unreadable (e.g. key invalidated).
    fun load(
        context: Context,
        wallet: String,
    ): String = KeyStoreHelper.loadWalletSecret(context, wallet)

    // Checks for a stored ciphertext. Only load() verifies that it can be unwrapped.
    fun exists(
        context: Context,
        wallet: String,
    ): Boolean = KeyStoreHelper.walletSecretExists(context, wallet)

    fun remove(
        context: Context,
        wallet: String,
    ) = KeyStoreHelper.removeWalletSecret(context, wallet)

    // Where the secret actually lives: "strongbox" | "tee" | "software" | "unknown".
    fun securityLevel(wallet: String): String = KeyStoreHelper.walletSecretSecurityLevel(wallet)

    /** The caller must hold the wallet path closed while restoring its legacy files. */
    fun recoverInterruptedMigration(
        context: Context,
        walletBasePath: String,
        wallet: String,
    ) {
        recoverLegacyFiles(walletBasePath, exists(context, wallet))
    }

    internal fun recoverLegacyFiles(
        walletBasePath: String,
        wrappedSecretPresent: Boolean,
    ) {
        // Ciphertext presence selects the normal unwrap/open path. Only a successful
        // authenticated open can retire a fallback if the Keystore key is unavailable.
        if (wrappedSecretPresent) return
        val (keys, cache) = migrationSnapshots(walletBasePath)
        if (!isSnapshotPresent(keys.first)) return
        keys.first.copyTo(keys.second, overwrite = true)
        // The writer publishes the keys snapshot first and changes the password only
        // after both snapshots exist. A crash before the second copy leaves the live
        // legacy cache intact, including metadata a chain rescan cannot reconstruct.
        if (isSnapshotPresent(cache.first)) cache.first.copyTo(cache.second, overwrite = true)
    }

    private fun isSnapshotPresent(file: File): Boolean =
        try {
            check(Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).isRegularFile) {
                "Invalid wallet migration snapshot"
            }
            true
        } catch (_: NoSuchFileException) {
            false
        }

    internal fun discardMigrationSnapshots(walletBasePath: String) {
        // The keys snapshot is the replay marker. A lone cache sidecar after a
        // cleanup interruption must never overwrite the current wallet cache.
        for ((snapshot, _) in migrationSnapshots(walletBasePath)) {
            Files.deleteIfExists(snapshot.toPath())
            Files.deleteIfExists(File(snapshot.path + ".tmp").toPath())
        }
    }

    // (snapshot, live file) for the keys file and the cache, both password-keyed.
    fun migrationSnapshots(walletBasePath: String): List<Pair<File, File>> =
        listOf(
            File("$walletBasePath.keys.premigrate") to File("$walletBasePath.keys"),
            File("$walletBasePath.premigrate") to File(walletBasePath),
        )
}
