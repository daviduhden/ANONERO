package io.anonero.util.backup

import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptUtilTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun file(name: String, bytes: ByteArray): File =
        tmp.newFile(name).apply { writeBytes(bytes) }

    @Test
    fun `v2 roundtrips`() {
        val payload = "the seed and everything else".toByteArray()
        val input = file("in", payload)
        val enc = tmp.newFile("enc"); val dec = tmp.newFile("dec")
        EncryptUtil.encryptFile("passphrase", input, enc)
        assertTrue(enc.readBytes().decodeToString().startsWith("ANONBAK2"))
        EncryptUtil.decryptFile("passphrase", enc, dec)
        assertArrayEquals(payload, dec.readBytes())
    }

    @Test
    fun `v2 refuses a wrong passphrase instead of yielding garbage`() {
        val enc = tmp.newFile("enc")
        EncryptUtil.encryptFile("right", file("in", ByteArray(64) { it.toByte() }), enc)
        try {
            EncryptUtil.decryptFile("wrong", enc, tmp.newFile("dec"))
            fail("a wrong passphrase must not produce output")
        } catch (expected: Exception) {
        }
    }

    @Test
    fun `v2 refuses a tampered file`() {
        val enc = tmp.newFile("enc")
        EncryptUtil.encryptFile("passphrase", file("in", "irreplaceable".toByteArray()), enc)
        val bytes = enc.readBytes()
        bytes[bytes.size - 20] = (bytes[bytes.size - 20].toInt() xor 1).toByte()
        enc.writeBytes(bytes)
        try {
            EncryptUtil.decryptFile("passphrase", enc, tmp.newFile("dec"))
            fail("a tampered backup must fail, not restore silently altered data")
        } catch (expected: Exception) {
        }
    }

    /** A file produced by the OLD code (v1: Salted__ + 11k CBC) must restore forever. */
    @Test
    fun `v1 backups still restore`() {
        val payload = "legacy backup content, PKCS5 padded".toByteArray()
        val salt = ByteArray(8) { (it + 1).toByte() }
        val generator = PKCS5S2ParametersGenerator(SHA256Digest())
        generator.init("passphrase".toByteArray(), salt, EncryptUtil.DEFAULT_ITR)
        val secret = (generator.generateDerivedMacParameters(48 * 8) as KeyParameter).key
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(secret.copyOfRange(0, 32), "AES"),
            IvParameterSpec(secret.copyOfRange(32, 48)),
        )
        val legacy = file("legacy", "Salted__".toByteArray() + salt + cipher.doFinal(payload))
        val dec = tmp.newFile("dec")
        EncryptUtil.decryptFile("passphrase", legacy, dec)
        assertArrayEquals(payload, dec.readBytes())
    }
}
