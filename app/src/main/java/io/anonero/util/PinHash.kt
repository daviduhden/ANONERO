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

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A real hash for the lock-screen PIN check.
 *
 * PREFS_PIN_HASH historically held CrazyPassEncoder.encode(pin) -- reversible base-32,
 * not a hash. Anyone who could read the preferences decoded the wallet password
 * outright, turning "attacker has the encrypted wallet file" into "attacker has the
 * funds". This restores the lock: the stored value reveals nothing and cannot be run
 * backwards.
 *
 * Stored format: v2:<iterations>:<salt b64>:<hash b64>. Legacy values (anything without
 * the v2 prefix) still verify against the old encoding, and the caller replaces them in
 * place on the next successful unlock -- the one moment the PIN is in hand. 210k
 * iterations of PBKDF2-HMAC-SHA256 matches the OWASP 2023 floor.
 */
object PinHash {

    private const val PREFIX = "v2"
    private const val ITERATIONS = 210_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val KEY_BYTES = KEY_BITS / 8

    fun hash(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = derive(pin, salt, ITERATIONS)
        return listOf(
            PREFIX,
            ITERATIONS.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(derived),
        ).joinToString(":")
    }

    fun matches(pin: String, stored: String?): Boolean {
        if (stored.isNullOrEmpty()) return false
        if (!isLegacy(stored)) {
            val parts = stored.split(":")
            if (parts.size != 4) return false
            val iterations = parts[1].toIntOrNull() ?: return false
            if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) return false
            val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
            val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
            if (salt.size != SALT_BYTES || expected.size != KEY_BYTES) return false
            return constantTimeEquals(derive(pin, salt, iterations), expected)
        }
        // legacy: the reversible encoding, compared the way the lock screen always did
        return constantTimeEquals(legacyEncode(pin).toByteArray(), stored.toByteArray())
    }

    /* True for values in the old reversible format, which verify but must be replaced. */
    fun isLegacy(stored: String?): Boolean =
        !stored.isNullOrEmpty() && !stored.startsWith("$PREFIX:")

    /*
     * Verifies a PIN, or -- when no credential is configured -- performs the same PBKDF2
     * work anyway and returns false. This keeps the unlock path from revealing, through
     * timing, whether the optional decoy credential exists.
     */
    fun matchesOrDummy(pin: String, stored: String?): Boolean {
        if (!stored.isNullOrEmpty()) return matches(pin, stored)
        derive(pin, DUMMY_SALT, ITERATIONS)
        return false
    }

    private val DUMMY_SALT = ByteArray(SALT_BYTES) { (it + 1).toByte() }

    private fun legacyEncode(pin: String): String = CrazyPassEncoder.encode(
        pin.toByteArray().let { bytes ->
            if (bytes.size < 32) bytes + ByteArray(32 - bytes.size) else bytes
        }
    )

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS))
            .encoded

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
