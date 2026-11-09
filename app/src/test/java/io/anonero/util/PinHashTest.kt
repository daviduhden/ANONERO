/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2023-2026 r4v3r23
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHashTest {

    @Test
    fun `a hashed pin verifies and a wrong one does not`() {
        val stored = PinHash.hash("1234")
        assertTrue(PinHash.matches("1234", stored))
        assertFalse(PinHash.matches("1235", stored))
        assertFalse(PinHash.matches("", stored))
    }

    @Test
    fun `hashes are salted`() {
        assertTrue(PinHash.hash("1234") != PinHash.hash("1234"))
    }

    @Test
    fun `legacy encoded values still verify, and are flagged for replacement`() {
        // exactly what every install upgrading from the old code has in prefs
        val legacy = CrazyPassEncoder.encode("1234".toByteArray() + ByteArray(28))
        assertTrue(PinHash.isLegacy(legacy))
        assertTrue(PinHash.matches("1234", legacy))
        assertFalse(PinHash.matches("9999", legacy))
        assertFalse(PinHash.isLegacy(PinHash.hash("1234")))
    }

    @Test
    fun `empty or mangled stored values never verify`() {
        assertFalse(PinHash.matches("1234", null))
        assertFalse(PinHash.matches("1234", ""))
        assertFalse(PinHash.matches("1234", "v2:garbage"))
        assertFalse(PinHash.matches("1234", "v2:210000:!!!:!!!"))
    }

    @Test
    fun `dummy verification never accepts an absent credential`() {
        assertFalse(PinHash.matchesOrDummy("1234", null))
        assertFalse(PinHash.matchesOrDummy("1234", ""))
        val stored = PinHash.hash("1234")
        assertTrue(PinHash.matchesOrDummy("1234", stored))
        assertFalse(PinHash.matchesOrDummy("1235", stored))
    }

    @Test
    fun `stored format is versioned`() {
        val stored = PinHash.hash("1234")
        assertEquals("v2", stored.split(":")[0])
        assertEquals("210000", stored.split(":")[1])
    }
}
