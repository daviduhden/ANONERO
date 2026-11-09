package io.anonero.ui.onboard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import io.anonero.AnonConfig
import io.anonero.model.NeroKeyPayload
import io.anonero.model.Wallet
import io.anonero.model.WalletManager
import io.anonero.util.PinHash
import io.anonero.util.KeyStoreHelper
import io.anonero.util.WalletKey
import io.anonero.util.WalletProfile
import io.anonero.util.PREFS_PASSPHRASE_HASH
import io.anonero.util.PREFS_PIN_HASH
import io.anonero.util.RESTORE_HEIGHT
import io.anonero.util.RestoreTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File


enum class Mode {
    RESTORE,
    CREATE
}

private const val TAG = "OnboardViewModel"

class OnboardViewModel(private val prefs: SharedPreferences) : ViewModel() {

    private var mode = Mode.CREATE
    private var passPhrase = ""
    private var walllet: Wallet? = null
    private var neroKeyPayload: NeroKeyPayload? = null
    private var restorePayload: RestorePayload? = null

    fun setMode(mode: Mode) = run { this.mode = mode }

    /** A new main PIN must never collide with a configured duress PIN. */
    private fun requirePinNotDuress(pin: String) {
        val duress = prefs.getString(io.anonero.util.PREFS_DURESS_PIN_HASH, "")
        if (!duress.isNullOrEmpty() && PinHash.matches(pin, duress)) {
            throw IllegalArgumentException("PIN collides with the configured duress PIN")
        }
    }

    /** The legitimate and decoy wallets must not share a seed passphrase. */
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
            hasCredentials = prefs.contains(PREFS_PIN_HASH) ||
                prefs.contains(PREFS_PASSPHRASE_HASH) || prefs.contains(RESTORE_HEIGHT),
        )
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
            WalletKey.store(context, walletFile.name, walletKey)
            val crazyPass: String = KeyStoreHelper.getCrazyPass(AnonConfig.context, passPhrase)
            prefs.edit(commit = true) {
                putString(PREFS_PASSPHRASE_HASH, crazyPass)
                putString(PREFS_PIN_HASH, PinHash.hash(pin))
            }
            val anonWallet = WalletManager.instance?.createWallet(
                walletFile,
                walletKey,
                passPhrase,
                "English",
                1,
            )
            anonWallet?.store()
            delay(100)
            if (anonWallet?.status?.isOk != true) {
                walletFile.delete()
                throw IllegalStateException("Wallet creation failed")
            }
            walllet = anonWallet
            delay(500)
        }
    }


    suspend fun createViewOnly(pin: String) {
        if (neroKeyPayload == null) return
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
            WalletKey.store(context, walletFile.name, walletKey)
            val crazyPass: String = KeyStoreHelper.getCrazyPass(AnonConfig.context, passPhrase)
            prefs.edit(commit = true) {
                putString("passPhraseHash", crazyPass)
                putString(PREFS_PIN_HASH, PinHash.hash(pin))
            }
            val anonWallet = WalletManager.instance?.createWalletWithKeys(
                walletFile,
                walletKey,
                language = "English",
                restoreHeight = neroKeyPayload!!.restoreHeight,
                viewKeyString = neroKeyPayload!!.privateViewKey,
                addressString = neroKeyPayload!!.primaryAddress,
                spendKeyString = ""
            )
            anonWallet?.setRestoreHeight(3460000);
            anonWallet?.store()
            delay(100)
            if (anonWallet?.status?.isOk != true) {
                walletFile.delete()
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

    fun getRestorePayload(): RestorePayload? {
        return restorePayload
    }

    suspend fun restoreFromSeed(pin: String) {
        val payload = restorePayload ?: return
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
        WalletKey.store(context, walletFile.name, walletKey)
        val crazyPass: String = KeyStoreHelper.getCrazyPass(AnonConfig.context, passPhrase)
        prefs.edit(commit = true) {
            putString(PREFS_PASSPHRASE_HASH, crazyPass)
            putString(PREFS_PIN_HASH, PinHash.hash(pin))
            payload.restoreHeight?.let {
                putLong(RESTORE_HEIGHT, it)
            }
        }
        val anonWallet = if (seedWords.size == 25) {
            WalletManager.instance?.recoveryWallet(
                walletFile,
                walletKey,
                seedWords.joinToString(" "),
                passPhrase,
                payload.restoreHeight ?: 0
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
        if (anonWallet == null) {
            throw Exception("unable to create wallet from seed, invalid seed length ${seedWords.size}")
        }
        delay(1000)
        payload.restoreHeight?.let {
            anonWallet?.setRestoreHeight(it)
        }
        anonWallet?.store()
        anonWallet?.store(walletFile.path)
        delay(100)
        if (anonWallet?.status?.isOk != true) {
            walletFile.delete()
            throw IllegalStateException("Wallet recovery failed")
        }
        try {
            anonWallet.close()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Unable to close wallet")
        }

        delay(500)
    }

}
