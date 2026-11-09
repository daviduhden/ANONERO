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

package io.anonero.ui.onboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedRestoreInputTest {
    @Test
    fun normalizesUnicodeSeparatorsWithoutChangingWords() {
        assertEquals(listOf("Dummy", "word", "placeholder", "item"),
            SeedRestoreInput.words("  Dummy\tword\nplaceholder\u00a0item\u3000"))
    }

    @Test
    fun acceptsOnlySupportedCounts() {
        for (count in listOf(0, 1, 15, 17, 24, 26)) {
            assertFalse(SeedRestoreInput.hasSupportedCount(List(count) { "dummy" }))
        }
        assertTrue(SeedRestoreInput.hasSupportedCount(List(16) { "dummy" }))
        assertTrue(SeedRestoreInput.hasSupportedCount(List(25) { "dummy" }))
    }

    @Test
    fun rejectsMalformedNegativeAndOverflowHeights() {
        for (value in listOf("-1", "+1", "1.5", "one", "9223372036854775808")) {
            assertFalse(SeedRestoreInput.isValidHeightInput(value))
        }
        for (value in listOf("", " ", "0", " 123 ", Long.MAX_VALUE.toString())) {
            assertTrue(SeedRestoreInput.isValidHeightInput(value))
        }
    }

    @Test
    fun rejectsInvalidPayloadBeforeRecovery() {
        assertThrows(IllegalArgumentException::class.java) {
            SeedRestoreInput.requireValid(List(24) { "dummy" }, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SeedRestoreInput.requireValid(List(16) { "dummy" }, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SeedRestoreInput.requireValid(List(25) { "dummy" }, null)
        }
    }

    @Test
    fun preservesOptionalPolyseedAndExplicitLegacyHeights() {
        val words = List(16) { "dummy" }
        assertEquals(words, SeedRestoreInput.requireValid(words, null))
        assertEquals(words, SeedRestoreInput.requireValid(words, Long.MAX_VALUE))
        val legacy = List(25) { "dummy" }
        assertEquals(legacy, SeedRestoreInput.requireValid(legacy, 0))
    }
}
