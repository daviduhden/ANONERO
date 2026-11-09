package io.anonero.util

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import androidx.core.content.edit
import timber.log.Timber

/**
 * Android Keystore integration for the legacy CrAzYpass gate and the modern
 * Keystore-wrapped wallet secret.
 *
 * Historical compatibility is intentional and must not be "cleaned up":
 * - the RSA alias is exactly `MonerujoRSA`;
 * - the signature algorithm is `SHA256withRSA`;
 * - CrAzYpass uses the native `slowHash` with its ARM32 broken-variant policy;
 * - the AES-GCM wallet secret uses prefs file `wallet`, prefix `walletSecret-`;
 * - the wrapped secret is IV || ciphertext||tag, Base64 without wrapping.
 *
 * Kotlin migration note: this remains an `object` with the same binary class name and
 * the same native `slowHash` symbol, so the existing JNI export
 * `Java_io_anonero_util_KeyStoreHelper_slowHash` stays valid.
 */
object KeyStoreHelper {
    private const val TAG = "KeyStoreHelper"
    private const val RSA_ALIAS = "MonerujoRSA"
    private const val WALLET_SECRET_PREFIX = "walletSecret-"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    init {
        System.loadLibrary("anonero")
    }

    /*
     * CryptoNight slow hash. The native side returns null for invalid broken-variant
     * inputs or unsupported cases; callers must not assume a result.
     *
     * JNI symbol naming does not distinguish a static from an instance native method;
     * the native implementation ignores its second JNI argument. Keeping this as an
     * object member avoids a Java companion while preserving the symbol
     * `Java_io_anonero_util_KeyStoreHelper_slowHash`.
     */
    external fun slowHash(data: ByteArray, brokenVariant: Int): ByteArray?

    class BrokenPasswordStoreException : Exception {
        constructor() : super()
        constructor(cause: Throwable?) : super(cause)
    }

    fun getCrazyPass(context: Context, password: String): String =
        getCrazyPass(context, password, 0)

    /*
     * Historical ARM32 builds used a broken prehash/variant. Keep accepting those values
     * when the ABI matches, but never produce them on other architectures.
     */
    fun getBrokenCrazyPass(context: Context, password: String, brokenVariant: Int): String? {
        if (isArm32() && brokenVariant != 2) return null
        return getCrazyPass(context, password, brokenVariant)
    }

    @Volatile
    private var arm32: Boolean? = null

    fun isArm32(): Boolean {
        arm32?.let { return it }
        return synchronized(this) {
            arm32 ?: run {
                val value = Build.SUPPORTED_ABIS.firstOrNull() == "armeabi-v7a"
                arm32 = value
                value
            }
        }
    }

    private fun getCrazyPass(context: Context, password: String, brokenVariant: Int): String {
        val data = password.toByteArray(StandardCharsets.UTF_8)
        try {
            createKeys(context, RSA_ALIAS)
            val signature = signData(data)
                ?: throw IllegalStateException("RSA signature is null")
            try {
                val hash = slowHash(signature, brokenVariant)
                    ?: throw IllegalStateException("Slow Hash is null!")
                try {
                    return CrazyPassEncoder.encode(hash)
                } finally {
                    hash.fill(0)
                }
            } finally {
                signature.fill(0)
            }
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException(e)
        } finally {
            data.fill(0)
        }
    }

    fun saveWalletUserPass(context: Context, wallet: String, password: String) {
        val walletKeyAlias = SecurityConstants.WALLET_PASS_KEY_PREFIX + wallet
        val data = password.toByteArray(StandardCharsets.UTF_8)
        try {
            createKeys(context, walletKeyAlias)
            val encrypted = encrypt(walletKeyAlias, data)
            val editor = context.getSharedPreferences(
                SecurityConstants.WALLET_PASS_PREFS_NAME,
                Context.MODE_PRIVATE
            ).edit()
            if (encrypted == null) {
                editor.remove(wallet).apply()
            } else {
                try {
                    editor.putString(
                        wallet,
                        Base64.encodeToString(encrypted, Base64.DEFAULT)
                    ).apply()
                } finally {
                    encrypted.fill(0)
                }
            }
        } catch (e: GeneralSecurityException) {
            Timber.tag(TAG).e(e)
        } finally {
            data.fill(0)
        }
    }

    fun hasStoredPasswords(context: Context): Boolean =
        context.getSharedPreferences(
            SecurityConstants.WALLET_PASS_PREFS_NAME,
            Context.MODE_PRIVATE
        ).all.isNotEmpty()

    @Throws(BrokenPasswordStoreException::class)
    fun loadWalletUserPass(context: Context, wallet: String): String {
        val walletKeyAlias = SecurityConstants.WALLET_PASS_KEY_PREFIX + wallet
        val encoded = context.getSharedPreferences(
            SecurityConstants.WALLET_PASS_PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(wallet, "").orEmpty()
        if (encoded.isEmpty()) throw BrokenPasswordStoreException()
        val data = Base64.decode(encoded, Base64.DEFAULT)
        try {
            val decrypted = decrypt(walletKeyAlias, data)
                ?: throw BrokenPasswordStoreException()
            try {
                return String(decrypted, StandardCharsets.UTF_8)
            } finally {
                decrypted.fill(0)
            }
        } finally {
            data.fill(0)
        }
    }

    fun removeWalletUserPass(context: Context, wallet: String) {
        val walletKeyAlias = SecurityConstants.WALLET_PASS_KEY_PREFIX + wallet
        try {
            deleteKeys(walletKeyAlias)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
        }
        context.getSharedPreferences(
            SecurityConstants.WALLET_PASS_PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit { remove(wallet) }
    }

    @Synchronized
    private fun getOrCreateWalletSecretKey(alias: String): SecretKey {
        val ks = KeyStore.getInstance(SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE)
        try {
            ks.load(null)
        } catch (e: Exception) {
            throw GeneralSecurityException(e)
        }
        if (ks.containsAlias(alias)) {
            return ks.getKey(alias, null) as SecretKey
        }

        // Prefer StrongBox when the device has it. A failed attempt must not leave a
        // partially-created alias behind for the TEE/software fallback.
        try {
            return generateWalletSecretKey(alias, strongBox = true)
        } catch (e: Exception) {
            Timber.tag(TAG).i("StrongBox unavailable, using TEE/software key")
            runCatching { ks.deleteEntry(alias) }
        }
        return generateWalletSecretKey(alias, strongBox = false)
    }

    private fun generateWalletSecretKey(alias: String, strongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (strongBox) builder.setIsStrongBoxBacked(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            builder.setUnlockedDeviceRequired(true)
        }
        val kg = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE
        )
        kg.init(builder.build())
        return kg.generateKey()
    }

    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun saveWalletSecret(context: Context, wallet: String, secret: String) {
        val key = getOrCreateWalletSecretKey(WALLET_SECRET_PREFIX + wallet)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val plaintext = secret.toByteArray(StandardCharsets.UTF_8)
        val ciphertext = try {
            cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
        val output = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, output, 0, iv.size)
        System.arraycopy(ciphertext, 0, output, iv.size, ciphertext.size)
        val written = context.getSharedPreferences(
            SecurityConstants.WALLET_PASS_PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit().putString(
            WALLET_SECRET_PREFIX + wallet,
            Base64.encodeToString(output, Base64.NO_WRAP)
        ).commit()
        output.fill(0)
        if (!written) throw GeneralSecurityException("wrapped wallet secret was not written")
    }

    @Throws(GeneralSecurityException::class)
    fun loadWalletSecret(context: Context, wallet: String): String {
        val encoded = context.getSharedPreferences(
            SecurityConstants.WALLET_PASS_PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(WALLET_SECRET_PREFIX + wallet, "").orEmpty()
        if (encoded.isEmpty()) throw GeneralSecurityException("no wallet secret stored")
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        if (all.size < GCM_IV_BYTES) throw GeneralSecurityException("wrapped wallet secret is too short")
        val iv = all.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = all.copyOfRange(GCM_IV_BYTES, all.size)
        val ks = KeyStore.getInstance(SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE)
        try {
            ks.load(null)
        } catch (e: Exception) {
            throw GeneralSecurityException(e)
        }
        val key = ks.getKey(WALLET_SECRET_PREFIX + wallet, null) as? SecretKey
            ?: throw GeneralSecurityException("wallet key missing")
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = try {
            cipher.doFinal(ciphertext)
        } finally {
            all.fill(0)
            ciphertext.fill(0)
        }
        try {
            return String(plaintext, StandardCharsets.UTF_8)
        } finally {
            plaintext.fill(0)
        }
    }

    fun walletSecretExists(context: Context, wallet: String): Boolean =
        !context.getSharedPreferences(
            SecurityConstants.WALLET_PASS_PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(WALLET_SECRET_PREFIX + wallet, "").isNullOrEmpty()

    // commit() is required to fail closed on a failed removal; the KTX edit helper
    // returns Unit and would discard that result.
    @Suppress("UseKtx")
    @Synchronized
    fun removeWalletSecret(context: Context, wallet: String) {
        val prefs = context.getSharedPreferences(
            SecurityConstants.WALLET_PASS_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        // Remove the reference first: if the alias deletion fails, the wrapper is not
        // left pointing at a key that no longer exists, and a retry can delete it.
        if (!prefs.edit().remove(WALLET_SECRET_PREFIX + wallet).commit()) {
            throw GeneralSecurityException("could not remove wallet secret reference")
        }
        val ks = KeyStore.getInstance(SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE)
        ks.load(null)
        ks.deleteEntry(WALLET_SECRET_PREFIX + wallet)
    }

    /* What actually protects W: `strongbox` | `tee` | `software` | `unknown`. */
    @Suppress("DEPRECATION")
    fun walletSecretSecurityLevel(wallet: String): String {
        return try {
            val ks = KeyStore.getInstance(SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE)
            ks.load(null)
            val key = ks.getKey(WALLET_SECRET_PREFIX + wallet, null) as? SecretKey
                ?: return "unknown"
            val factory = SecretKeyFactory.getInstance(
                key.algorithm,
                SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE
            )
            val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (info.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> "strongbox"
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "tee"
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> "software"
                    else -> "unknown"
                }
            } else {
                if (info.isInsideSecureHardware) "tee" else "software"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    @Throws(BrokenPasswordStoreException::class)
    fun copyWalletUserPass(context: Context, srcWallet: String, dstWallet: String) {
        val pass = loadWalletUserPass(context, srcWallet)
        saveWalletUserPass(context, dstWallet, pass)
    }

    private fun createKeys(context: Context, alias: String) {
        val keyStore = KeyStore.getInstance(
            SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE
        )
        try {
            keyStore.load(null)
        } catch (e: Exception) {
            throw IllegalStateException("Could not load KeyStore", e)
        }
        if (!keyStore.containsAlias(alias)) {
            createKeysM(alias)
        }
    }

    private fun createKeysM(alias: String) {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE
        )
        keyPairGenerator.initialize(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or
                    KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .build()
        )
        keyPairGenerator.generateKeyPair()
    }

    private fun deleteKeys(alias: String) {
        val keyStore = KeyStore.getInstance(
            SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE
        )
        try {
            keyStore.load(null)
            keyStore.deleteEntry(alias)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
        }
    }

    @Throws(BrokenPasswordStoreException::class)
    fun keyExists(wallet: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(
                SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE
            )
            keyStore.load(null)
            keyStore.containsAlias(SecurityConstants.WALLET_PASS_KEY_PREFIX + wallet)
        } catch (e: Exception) {
            throw BrokenPasswordStoreException(e)
        }
    }

    private fun getPrivateKey(alias: String): PrivateKey? {
        return try {
            val ks = KeyStore.getInstance(
                SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE
            )
            ks.load(null)
            val privateKey = ks.getKey(alias, null) as? PrivateKey
            if (privateKey == null) {
                Timber.tag("createKeysM").i("No key found under alias: %s", alias)
            }
            privateKey
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }

    private fun getPublicKey(alias: String): PublicKey {
        val ks = KeyStore.getInstance(
            SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE
        )
        ks.load(null)
        return ks.getCertificate(alias).publicKey
    }

    private fun encrypt(alias: String, data: ByteArray): ByteArray? {
        return try {
            val publicKey = getPublicKey(alias)
            val cipher = Cipher.getInstance(SecurityConstants.CIPHER_RSA_ECB_PKCS1)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            cipher.doFinal(data)
        } catch (e: GeneralSecurityException) {
            Timber.tag(TAG).e(e)
            null
        }
    }

    private fun decrypt(alias: String, data: ByteArray): ByteArray? {
        return try {
            val privateKey = getPrivateKey(alias) ?: return null
            val cipher = Cipher.getInstance(SecurityConstants.CIPHER_RSA_ECB_PKCS1)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            cipher.doFinal(data)
        } catch (e: GeneralSecurityException) {
            Timber.tag(TAG).e(e)
            null
        }
    }

    @Throws(GeneralSecurityException::class)
    private fun signData(data: ByteArray): ByteArray? {
        val privateKey = getPrivateKey(RSA_ALIAS) ?: return null
        val signature = Signature.getInstance(SecurityConstants.SIGNATURE_SHA256withRSA)
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    object SecurityConstants {
        const val KEYSTORE_PROVIDER_ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TYPE_RSA = "RSA"
        const val SIGNATURE_SHA256withRSA = "SHA256withRSA"
        const val CIPHER_RSA_ECB_PKCS1 = "RSA/ECB/PKCS1Padding"
        const val WALLET_PASS_PREFS_NAME = "wallet"
        const val WALLET_PASS_KEY_PREFIX = "walletKey-"
    }
}
