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
import android.content.SharedPreferences
import io.anonero.AnonConfig
import timber.log.Timber
import java.io.File

/**
 * Which wallet file the current process has open.
 *
 * The profile is deliberately not persisted: every process start begins on MAIN and the
 * PIN entered on the lock screen decides which wallet is opened. A duress PIN therefore
 * opens the decoy for that session only; a later cold start with the duress PIN opens it
 * again. Nothing in the UI or in the persisted preferences says which profile was last
 * used.
 *
 * LIMITATION: this is separation of two wallet files with separate Keystore-bound
 * secrets, not plausible deniability. The decoy file, its Keystore alias, and its PIN
 * hash are all discoverable by an adversary with root/forensic access to the device.
 */
enum class WalletProfile(
    val walletName: String,
) {
    MAIN("anon"),
    DECOY("anon-alt"),
    ;

    val isDecoy: Boolean get() = this == DECOY
}

object WalletSession {
    @Volatile
    var profile: WalletProfile = WalletProfile.MAIN
        private set

    val isDecoy: Boolean get() = profile.isDecoy

    fun select(profile: WalletProfile) {
        this.profile = profile
    }

    fun reset() {
        profile = WalletProfile.MAIN
    }
}

/**
 * File, Keystore-alias, and preference-key layout for both profiles.
 *
 * The two wallets live in the same app-private directory but have different file names,
 * different Keystore-wrapped wallet secrets, different PIN hashes, and different seed
 * passphrase hashes. No key or secret is shared between them.
 */
object WalletProfileStore {
    private const val TAG = "WalletProfileStore"
    private const val DECOY_RESTORE_HEIGHT = "decoyRestoreHeight"

    fun walletFile(
        context: Context,
        profile: WalletProfile,
    ): File = File(AnonConfig.getDefaultWalletDir(context), profile.walletName)

    fun activeWalletFile(context: Context): File = walletFile(context, WalletSession.profile)

    fun activeWalletName(context: Context): String = activeWalletFile(context).name

    fun pinHashKey(profile: WalletProfile): String = if (profile.isDecoy) PREFS_DURESS_PIN_HASH else PREFS_PIN_HASH

    fun activePinHashKey(): String = pinHashKey(WalletSession.profile)

    fun passphraseHashKey(profile: WalletProfile): String = if (profile.isDecoy) PREFS_DECOY_PASSPHRASE_HASH else PREFS_PASSPHRASE_HASH

    fun activePassphraseHashKey(): String = passphraseHashKey(WalletSession.profile)

    fun restoreHeightKey(profile: WalletProfile): String = if (profile.isDecoy) DECOY_RESTORE_HEIGHT else RESTORE_HEIGHT

    /*
     * A decoy is usable only when all three pieces are present: the configured flag,
     * the encrypted wallet file, and the Keystore-wrapped wallet secret. If the file is
     * missing the duress PIN must not fall through to the legitimate wallet.
     */
    fun isDecoyConfigured(
        context: Context,
        prefs: SharedPreferences,
    ): Boolean {
        if (!prefs.getBoolean(PREFS_DECOY_CONFIGURED, false)) return false
        if (prefs.getString(PREFS_DURESS_PIN_HASH, "").isNullOrEmpty()) return false
        val file = walletFile(context, WalletProfile.DECOY)
        return file.exists() && WalletKey.exists(context, file.name)
    }

    // Files belonging to one wallet file: base cache, .keys, .address.txt, snapshots.
    fun walletFiles(
        context: Context,
        profile: WalletProfile,
    ): List<File> {
        val dir = AnonConfig.getDefaultWalletDir(context)
        val name = profile.walletName
        return dir
            .listFiles()
            ?.filter { it.isFile && (it.name == name || it.name.startsWith("$name.")) }
            ?: emptyList()
    }

    // True if the legitimate wallet, a usable decoy, or both exist.
    fun anyWalletExists(
        context: Context,
        prefs: SharedPreferences,
    ): Boolean =
        walletFile(context, WalletProfile.MAIN).exists() ||
            isDecoyConfigured(context, prefs)

    /*
     * Removes exactly one profile. It never deletes the other wallet's files, which is
     * what makes a wipe performed from the decoy profile non-destructive to the real
     * wallet.
     */
    fun removeProfile(
        context: Context,
        prefs: SharedPreferences,
        profile: WalletProfile,
    ): Boolean {
        var ok = true
        val keyName = walletFile(context, profile).name
        walletFiles(context, profile).forEach { file ->
            if (!file.delete() && file.exists()) {
                Timber.tag(TAG).w("could not delete %s", file.name)
                ok = false
            }
        }
        runCatching { WalletKey.remove(context, keyName) }
            .onFailure {
                Timber.tag(TAG).e(it, "could not remove wallet key")
                ok = false
            }
        val editor = prefs.edit()
        if (profile.isDecoy) {
            editor.remove(PREFS_DURESS_PIN_HASH)
            editor.remove(PREFS_DECOY_PASSPHRASE_HASH)
            editor.remove(PREFS_DECOY_CONFIGURED)
            editor.remove(DECOY_RESTORE_HEIGHT)
        } else {
            editor.remove(PREFS_PIN_HASH)
            editor.remove(PREFS_PASSPHRASE_HASH)
            editor.remove(RESTORE_HEIGHT)
        }
        if (!editor.commit()) {
            Timber.tag(TAG).e("could not commit profile removal for %s", profile.walletName)
            ok = false
        }
        return ok
    }

    /*
     * Resolves the profile for a PIN, checking both gates with the same work profile.
     * Returns null when neither matches. Callers must not reveal which check failed.
     *
     * PinHash.matchesOrDummy is used for the duress gate even when no decoy exists, so
     * unlock timing does not disclose whether a second credential is configured.
     */
    fun resolvePin(
        context: Context,
        prefs: SharedPreferences,
        pin: String,
    ): WalletProfile? {
        val mainHash = prefs.getString(PREFS_PIN_HASH, "")
        val decoyHash = prefs.getString(PREFS_DURESS_PIN_HASH, "")
        val decoyUsable = isDecoyConfigured(context, prefs)

        // Always perform both derivations. Short-circuiting on the main match would make
        // a correct main PIN noticeably faster than a correct duress PIN.
        val mainMatches = PinHash.matches(pin, mainHash)
        val decoyMatches =
            if (decoyUsable) {
                PinHash.matches(pin, decoyHash)
            } else {
                PinHash.matchesOrDummy(pin, decoyHash)
                false
            }

        return when {
            mainMatches -> WalletProfile.MAIN
            decoyMatches -> WalletProfile.DECOY
            else -> null
        }
    }
}
