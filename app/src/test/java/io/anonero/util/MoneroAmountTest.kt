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
    fun `invalid, negative, zero and over-precise values are rejected`() {
        assertNull(MoneroAmount.parseExact(""))
        assertNull(MoneroAmount.parseExact("0"))
        assertNull(MoneroAmount.parseExact("-1"))
        assertNull(MoneroAmount.parseExact("1."))
        assertNull(MoneroAmount.parseExact(".1"))
        assertNull(MoneroAmount.parseExact("1.0000000000001"))
        assertNull(MoneroAmount.parseExact("1,5"))
        assertNull(MoneroAmount.parseExact("1e3"))
        assertNull(MoneroAmount.parseExact(" 1"))
    }

    @Test
    fun `values that overflow the wallet atomic range are rejected`() {
        // 2^63 atomic units would sign-wrap the jlong JNI boundary.
        assertNull(MoneroAmount.parseExact("9223372.036854775808"))
    }
}
