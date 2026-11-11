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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneroAmountTest {
    @Test
    fun `exact decimal strings convert to atomic units`() {
        assertEquals(1_000_000_000_000L, MoneroAmount.parseExact("1"))
        assertEquals(1L, MoneroAmount.parseExact("0.000000000001"))
        assertEquals(12_345_678_901_234L, MoneroAmount.parseExact("12.345678901234"))
        assertEquals(150_000_000_000L, MoneroAmount.parseExact("0.15"))
    }

    @Test
    fun `trailing zeros and exact maximum precision are accepted`() {
        assertEquals(100_000_000_000L, MoneroAmount.parseExact("0.100"))
        assertEquals(123_456_789_012L, MoneroAmount.parseExact("0.123456789012"))
    }

    @Test
    fun `surrounding whitespace is trimmed before parsing`() {
        assertEquals(1_000_000_000_000L, MoneroAmount.parseExact(" 1 "))
        assertEquals(1_000_000_000_000L, MoneroAmount.parseExact("\t1\n"))
    }

    @Test
    fun `invalid, negative, zero and over-precise values are rejected`() {
        assertNull(MoneroAmount.parseExact(""))
        assertNull(MoneroAmount.parseExact("0"))
        assertNull(MoneroAmount.parseExact("-1"))
        assertNull(MoneroAmount.parseExact("+1"))
        assertNull(MoneroAmount.parseExact("1."))
        assertNull(MoneroAmount.parseExact(".1"))
        assertNull(MoneroAmount.parseExact("01"))
        assertNull(MoneroAmount.parseExact("1 000"))
        assertNull(MoneroAmount.parseExact("1.0000000000001"))
        assertNull(MoneroAmount.parseExact("1,5"))
        assertNull(MoneroAmount.parseExact("1e3"))
    }

    @Test
    fun `formatter is exact and round-trips every atomic unit`() {
        assertEquals("1", MoneroAmount.format(1_000_000_000_000L))
        assertEquals("0.000000000001", MoneroAmount.format(1L))
        assertEquals("12.345678901234", MoneroAmount.format(12_345_678_901_234L))
        for (value in listOf(
            1L,
            999_999_999_999L,
            1_000_000_000_000L,
            12_345_678_901_234L,
            Long.MAX_VALUE / 2,
        )) {
            assertEquals(value, MoneroAmount.parseExact(MoneroAmount.format(value)))
        }
    }

    @Test
    fun `values that overflow the wallet atomic range are rejected`() {
        // 2^63 atomic units would sign-wrap the jlong JNI boundary.
        assertNull(MoneroAmount.parseExact("9223372.036854775808"))
    }
}
