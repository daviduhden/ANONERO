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

package io.anonero.ui.onboard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import io.anonero.AnonConfig
import io.anonero.model.NeroKeyPayload
import io.anonero.model.Wallet
import io.anonero.model.WalletManager
import io.anonero.util.KeyStoreHelper
import io.anonero.util.PREFS_PASSPHRASE_HASH
import io.anonero.util.PREFS_PIN_HASH
import io.anonero.util.PinHash
import io.anonero.util.RESTORE_HEIGHT
import io.anonero.util.RestoreTarget
import io.anonero.util.WalletKey
import io.anonero.util.WalletProfile
import io.anonero.util.WalletProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

enum class Mode {
    RESTORE,
    CREATE,
}

private const val TAG = "OnboardViewModel"

/**
 * Onboarding state and wallet provisioning.
 *
 * Holds the mode (create/restore), the seed passphrase, the newly created wallet, and the
 * restore/view-only payload between screens. Creation and restore are deliberately
 * transactional: credentials and the Keystore secret are written before the wallet file,
 * and a failed wallet creation deletes the partial file. Before writing MAIN, it refuses a
 * PIN that matches the duress PIN, a passphrase that matches the decoy passphrase, or a
 * non-empty MAIN target.
 */
class OnboardViewModel(
    private val prefs: SharedPreferences,
) : ViewModel() {
    private var mode = Mode.CREATE
    private var passPhrase = ""
    private var walllet: Wallet? = null
    private var neroKeyPayload: NeroKeyPayload? = null
    private var restorePayload: RestorePayload? = null

    fun setMode(mode: Mode) = run { this.mode = mode }

    // A new main PIN must never collide with a configured duress PIN.
    private fun requirePinNotDuress(pin: String) {
        val duress = prefs.getString(io.anonero.util.PREFS_DURESS_PIN_HASH, "")
        if (!duress.isNullOrEmpty() && PinHash.matches(pin, duress)) {
            throw IllegalArgumentException("PIN collides with the configured duress PIN")
        }
    }

    // The legitimate and decoy wallets must not share a seed passphrase.
    private fun requirePassphraseNotDecoy(passphrase: String) {
        val decoy = prefs.getString(io.anonero.util.PREFS_DECOY_PASSPHRASE_HASH, "")
        if (decoy.isNullOrEmpty()) return
        val context = AnonConfig.context ?: return
        val candidate = KeyStoreHelper.getCrazyPass(context, passphrase)
        if (candidate == decoy) {
            throw IllegalArgumentException("Passphrase collides with the decoy wallet")
        }
    }

    private fun requireEmptyMainTarget(context: Context) {
        RestoreTarget.requireEmpty(
            directory = File(context.filesDir, "wallets"),
            walletName = WalletProfile.MAIN.walletName,
            hasWrappedSecret = WalletKey.exists(context, WalletProfile.MAIN.walletName),
            hasCredentials =
                prefs.contains(PREFS_PIN_HASH) ||
                    prefs.contains(PREFS_PASSPHRASE_HASH) || prefs.contains(RESTORE_HEIGHT),
        )
    }

    /*
     * A failed create/restore must not leave a wrapped secret or credentials behind:
     * RestoreTarget.requireEmpty treats either as an existing wallet and would block every
     * later attempt. Close a partially managed native wallet first, then remove only MAIN.
     */
    private fun cleanupFailedMain(context: Context) {
        runCatching { WalletManager.instance?.wallet?.close() }
            .onFailure { Timber.tag(TAG).w(it, "Unable to close partial wallet") }
        runCatching { WalletProfileStore.removeProfile(context, prefs, WalletProfile.MAIN) }
            .onFailure { Timber.tag(TAG).w(it, "Unable to remove partial MAIN profile") }
    }

    private fun commitCredentials(action: android.content.SharedPreferences.Editor.() -> Unit) {
        val editor = prefs.edit()
        editor.action()
        if (!editor.commit()) throw IllegalStateException("credential commit failed")
    }

    fun getMode() = mode

    fun getSeed() = walllet?.getSeed(passPhrase) ?: ""

    suspend fun create(pin: String) {
        if (AnonConfig.context == null) return
        withContext(Dispatchers.IO) {
            requirePinNotDuress(pin)
            requirePassphraseNotDecoy(passPhrase)
            val context = AnonConfig.context!!.applicationContext
            requireEmptyMainTarget(context)
            val walletFile = AnonConfig.getDefaultWalletFile(context)
            AnonConfig.getDefaultWalletDir(context).mkdirs()
            // The wallet2 password is a random Keystore-wrapped secret, not the PIN.
            // The PIN gates access to it (PinHash) but never encrypts the wallet.
            val walletKey = WalletKey.newSecret()
            // store the key and the hashes before the wallet is written
            try {
                WalletKey.store(context, walletFile.name, walletKey)
                val crazyPass: String = KeyStoreHelper.getCrazyPass(context, passPhrase)
                commitCredentials {
                    putString(PREFS_PASSPHRASE_HASH, crazyPass)
                    putString(PREFS_PIN_HASH, PinHash.hash(pin))
                }
            } catch (e: Exception) {
                cleanupFailedMain(context)
                throw e
            }
            val anonWallet =
                try {
                    WalletManager.instance
                        ?.createWallet(
                            walletFile,
                            walletKey,
                            passPhrase,
                            "English",
                            1,
                        )?.also {
                            if (!it.store()) throw IllegalStateException("wallet store failed")
                        }
                } catch (e: Exception) {
                    cleanupFailedMain(context)
                    throw e
                }
            delay(100)
            if (anonWallet?.status?.isOk != true) {
                cleanupFailedMain(context)
                throw IllegalStateException("Wallet creation failed")
            }
            walllet = anonWallet
            delay(500)
        }
    }

    suspend fun createViewOnly(pin: String) {
        val payload =
            neroKeyPayload
                ?: throw IllegalStateException("view-only key payload missing")
        if (payload.restoreHeight !in 0..io.anonero.util.MAX_RESTORE_HEIGHT) {
            throw IllegalArgumentException("restore height must be within the supported range")
        }
        if (AnonConfig.context == null) return
        withContext(Dispatchers.IO) {
            requirePinNotDuress(pin)
            requirePassphraseNotDecoy(passPhrase)
            val context = AnonConfig.context!!.applicationContext
            requireEmptyMainTarget(context)
            val walletFile = AnonConfig.getDefaultWalletFile(context)
            AnonConfig.getDefaultWalletDir(context).mkdirs()
            val walletKey = WalletKey.newSecret()
            // store the key and the hashes before the wallet is written
            try {
                WalletKey.store(context, walletFile.name, walletKey)
                val crazyPass: String = KeyStoreHelper.getCrazyPass(context, passPhrase)
                commitCredentials {
                    putString(PREFS_PASSPHRASE_HASH, crazyPass)
                    putString(PREFS_PIN_HASH, PinHash.hash(pin))
                }
            } catch (e: Exception) {
                cleanupFailedMain(context)
                throw e
            }
            val anonWallet =
                try {
                    WalletManager.instance
                        ?.createWalletWithKeys(
                            walletFile,
                            walletKey,
                            language = "English",
                            restoreHeight = payload.restoreHeight,
                            viewKeyString = payload.privateViewKey,
                            addressString = payload.primaryAddress,
                            spendKeyString = "",
                        )?.also {
                            if (!it.store()) throw IllegalStateException("wallet store failed")
                        }
                } catch (e: Exception) {
                    cleanupFailedMain(context)
                    throw e
                }
            delay(100)
            if (anonWallet?.status?.isOk != true) {
                cleanupFailedMain(context)
                throw IllegalStateException("Wallet creation failed")
            }
            walllet = anonWallet
            delay(500)
        }
    }

    fun setPassPhrase(it: String) {
        this.passPhrase = it
    }

    fun setNeroPayload(it: NeroKeyPayload) {
        neroKeyPayload = it
    }

    fun setRestorePayload(it: RestorePayload) {
        restorePayload = it
    }

    fun getRestorePayload(): RestorePayload? = restorePayload

    suspend fun restoreFromSeed(pin: String) {
        val payload =
            restorePayload
                ?: throw IllegalStateException("restore payload missing")
        // Reject structural input errors before replacing MAIN files or credentials.
        // wallet2 remains responsible for mnemonic language and checksum validation.
        val seedWords = SeedRestoreInput.requireValid(payload.seed, payload.restoreHeight)

        requirePinNotDuress(pin)
        requirePassphraseNotDecoy(passPhrase)
        val context = AnonConfig.context!!.applicationContext
        requireEmptyMainTarget(context)
        val walletFile = AnonConfig.getDefaultWalletFile(context)
        AnonConfig.getDefaultWalletDir(context).mkdirs()
        delay(100)
        // wallet2 password is a Keystore-wrapped random secret, not the PIN
        val walletKey = WalletKey.newSecret()
        // store the key and the hashes before the wallet is written
        try {
            WalletKey.store(context, walletFile.name, walletKey)
            val crazyPass: String = KeyStoreHelper.getCrazyPass(context, passPhrase)
            commitCredentials {
                putString(PREFS_PASSPHRASE_HASH, crazyPass)
                putString(PREFS_PIN_HASH, PinHash.hash(pin))
                payload.restoreHeight?.let {
                    putLong(RESTORE_HEIGHT, it)
                }
            }
        } catch (e: Exception) {
            cleanupFailedMain(context)
            throw e
        }
        val anonWallet =
            try {
                if (seedWords.size == 25) {
                    WalletManager.instance?.recoveryWallet(
                        walletFile,
                        walletKey,
                        seedWords.joinToString(" "),
                        passPhrase,
                        payload.restoreHeight ?: 0,
                    )
                } else if (seedWords.size == 16) {
                    WalletManager.instance?.recoveryWalletPolyseed(
                        walletFile,
                        walletKey,
                        seedWords.joinToString(" "),
                        passPhrase,
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                cleanupFailedMain(context)
                throw e
            }
        if (anonWallet == null) {
            cleanupFailedMain(context)
            throw Exception("unable to create wallet from seed, invalid seed length ${seedWords.size}")
        }
        try {
            delay(1000)
            payload.restoreHeight?.let {
                anonWallet.setRestoreHeight(it)
            }
            if (!anonWallet.store()) throw IllegalStateException("wallet store failed")
            if (!anonWallet.store(walletFile.path)) throw IllegalStateException("wallet store failed")
            delay(100)
            if (anonWallet.status.isOk != true) {
                cleanupFailedMain(context)
                throw IllegalStateException("Wallet recovery failed")
            }
        } catch (e: Exception) {
            cleanupFailedMain(context)
            throw e
        }
        // Keep the restored wallet managed; the onboarding flow starts the sync service
        // immediately after this returns. Closing it here left Home without a wallet.
        walllet = anonWallet
        delay(500)
    }
}
