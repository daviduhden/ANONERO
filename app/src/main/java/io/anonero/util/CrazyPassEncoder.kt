package io.anonero.util

import java.math.BigInteger
import java.util.Locale

/**
 * Legacy CrAzYpass encoding. This is a compatibility format, not a general-purpose
 * password hash: [KeyStoreHelper] feeds it a native CryptoNight-derived 32-byte value.
 *
 * Output is exactly 52 alphabet characters in 13 groups of four, padded with `'2'`.
 * Do not change the alphabet, padding, grouping, or BigInteger arithmetic: existing
 * wallet files and PIN hashes depend on byte-for-byte equality.
 */
object CrazyPassEncoder {
    const val BASE = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    const val PW_CHARS = 52
    private val base = BigInteger.valueOf(BASE.length.toLong())

    @JvmStatic
    fun encode(data: ByteArray): String {
        require(data.size == 32) { "data[] is not 32 bytes long" }
        var rest = BigInteger(1, data)
        val result = StringBuilder()
        var i = 0
        do {
            if (i > 0 && i % 4 == 0) result.append(' ')
            i++
            val remainder = rest.mod(base)
            rest = rest.divide(base)
            result.append(BASE[remainder.toInt()])
        } while (rest != BigInteger.ZERO)
        while (i < PW_CHARS) {
            if (i > 0 && i % 4 == 0) result.append(' ')
            result.append('2')
            i++
        }
        return result.toString()
    }

    /**
     * Accepts a 52-character CrAzYpass with any case/spacing and returns the canonical
     * blank-separated form, or null if the input is not a valid CrAzYpass.
     */
    @JvmStatic
    fun reformat(password: String): String? {
        val noBlanks = password.uppercase(Locale.getDefault()).replace(" ", "")
        if (noBlanks.length != PW_CHARS) return null
        val result = StringBuilder(PW_CHARS + 12)
        for (i in 0 until PW_CHARS) {
            if (i > 0 && i % 4 == 0) result.append(' ')
            val c = noBlanks[i]
            if (!BASE.contains(c)) return null
            result.append(c)
        }
        return result.toString()
    }
}
