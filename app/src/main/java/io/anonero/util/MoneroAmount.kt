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

import java.math.BigInteger

/**
 * Exact conversion between a decimal XMR string and atomic units (10^-12 XMR).
 * Deliberately independent of java.lang.Double and of the native wallet parser so the
 * pay-to-many screen can reject malformed or overflowing input before any transaction
 * object is built.
 */
object MoneroAmount {
    const val DECIMALS = 12
    private val PATTERN = Regex("^(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,$DECIMALS})?$")
    private val SCALE = BigInteger.TEN.pow(DECIMALS)

    fun parseExact(text: String): Long? {
        val value = text.trim()
        if (!PATTERN.matches(value)) return null
        val parts = value.split('.', limit = 2)
        val whole = parts[0]
        val fraction = parts.getOrNull(1).orEmpty().padEnd(DECIMALS, '0')
        val atomic = runCatching {
            BigInteger(whole).multiply(SCALE).add(BigInteger(fraction))
        }.getOrNull() ?: return null
        if (atomic.signum() <= 0) return null
        // Reject anything that cannot be represented by wallet2's uint64_t / jlong path.
        if (atomic.bitLength() > 63) return null
        return atomic.toLong()
    }

    fun format(atomic: Long): String = Formats.getExactDisplayAmount(atomic)
}
