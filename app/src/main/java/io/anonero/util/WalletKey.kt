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
import java.security.SecureRandom
import timber.log.Timber

/**
 * The wallet2 password is no longer the PIN. It is a high-entropy random secret W,
 * wrapped by a non-exportable Android Keystore key (KeyStoreHelper's per-wallet key).
 * Extracted keys files are useless without this device's Keystore, since W cannot be
 * brute-forced (256-bit) and cannot be unwrapped off-device.
 *
 * The PIN gates access to W in software (verified via PinHash) -- app-PIN, not device
 * biometric, by design. This closes the at-rest-extraction threat; it does not defend a
 * live, app-context-compromised device (which could reach W in memory).
 */
object WalletKey {

    private const val LEN = 32 // 256-bit

    /* A fresh random secret, base64 so it is a plain wallet2 password string. */
    fun newSecret(): String =
        Base64.encodeToString(ByteArray(LEN).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)

    /* Wraps [secret] under the wallet's AES-256-GCM Keystore key (StrongBox where available). */
    fun store(context: Context, wallet: String, secret: String) =
        KeyStoreHelper.saveWalletSecret(context, wallet, secret)

    /* Unwraps the wallet's secret; throws if missing/unreadable (e.g. key invalidated). */
    fun load(context: Context, wallet: String): String =
        KeyStoreHelper.loadWalletSecret(context, wallet)

    /* True once a wallet has been bound to the Keystore (created or migrated). */
    fun exists(context: Context, wallet: String): Boolean =
        KeyStoreHelper.walletSecretExists(context, wallet)

    fun remove(context: Context, wallet: String) =
        KeyStoreHelper.removeWalletSecret(context, wallet)

    /* Where the secret actually lives: "strongbox" | "tee" | "software" | "unknown". */
    fun securityLevel(wallet: String): String =
        KeyStoreHelper.walletSecretSecurityLevel(wallet)

    /*
     * Recovers from a migration interrupted by a crash. AnonWalletHandler.migrateToKeystore
     * snapshots <wallet>.keys to <wallet>.keys.premigrate before re-encrypting. Call this
     * at startup, before any wallet open:
     *  - snapshot present and the key is wrapped -> migration finished, drop the snapshot.
     *  - snapshot present and no wrapped key -> crash before wrap; the keys file may be
     *    encrypted with a secret that was never stored, so restore the legacy keys.
     */
    fun recoverInterruptedMigration(context: Context, walletBasePath: String, wallet: String) {
        val snapshots = migrationSnapshots(walletBasePath)
        val keysPair = snapshots[0]
        val cachePair = snapshots[1]
        val anySnapshot = keysPair.first.exists() || cachePair.first.exists()
        if (!anySnapshot) return
        if (exists(context, wallet)) {
            snapshots.forEach { (snapshot, _) -> snapshot.delete() }
            Timber.tag("WalletKey").i("migration confirmed; stale snapshot removed")
            return
        }
        // No wrapped key: the migration did not finish. The keys file may be encrypted
        // with a secret that was never stored. Restore the keys snapshot if it exists;
        // restore the cache only when its snapshot also exists, otherwise delete the
        // possibly re-keyed cache and let wallet2 rebuild it from the restore height.
        if (!keysPair.first.exists()) {
            Timber.tag("WalletKey")
                .e("migration recovery incomplete: keys snapshot missing; seed recovery required")
            return
        }
        keysPair.first.copyTo(keysPair.second, overwrite = true)
        if (cachePair.first.exists()) {
            cachePair.first.copyTo(cachePair.second, overwrite = true)
        } else {
            cachePair.second.delete()
        }
        snapshots.forEach { (snapshot, _) -> snapshot.delete() }
        Timber.tag("WalletKey").w("migration interrupted; restored legacy keys and cache")
    }

    /* (snapshot, live file) for the keys file and the cache, both password-keyed. */
    fun migrationSnapshots(walletBasePath: String): List<Pair<File, File>> = listOf(
        File("$walletBasePath.keys.premigrate") to File("$walletBasePath.keys"),
        File("$walletBasePath.premigrate") to File(walletBasePath),
    )
}
