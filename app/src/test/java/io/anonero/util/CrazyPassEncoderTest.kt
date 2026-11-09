package io.anonero.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class CrazyPassEncoderTest {

    @Test
    fun `encode preserves legacy alphabet padding and grouping`() {
        val encoded = CrazyPassEncoder.encode(ByteArray(32))
        assertEquals(
            "2222 2222 2222 2222 2222 2222 2222 2222 2222 2222 2222 2222 2222",
            encoded
        )
        assertEquals(52, encoded.replace(" ", "").length)
        assertEquals(13, encoded.split(" ").size)
    }

    @Test
    fun `encode has a stable compatibility vector`() {
        val bytes = ByteArray(32) { it.toByte() }
        assertEquals(
            "ZJ9U 3GE5 UA8J KDS4 P278 3B64 JS5W J8J3 DJ4L 26W2 8A3A J3A2 3222",
            CrazyPassEncoder.encode(bytes)
        )
    }

    @Test
    fun `reformat canonicalizes only valid crazypasses`() {
        val canonical = CrazyPassEncoder.encode(ByteArray(32) { it.toByte() })
        assertEquals(canonical, CrazyPassEncoder.reformat(canonical.lowercase()))
        assertEquals(canonical, CrazyPassEncoder.reformat(canonical.replace(" ", "")))
        assertNull(CrazyPassEncoder.reformat("too short"))
        assertNull(CrazyPassEncoder.reformat("0".repeat(52)))
    }

    @Test
    fun `encode rejects incorrect input length`() {
        try {
            CrazyPassEncoder.encode(ByteArray(31))
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
