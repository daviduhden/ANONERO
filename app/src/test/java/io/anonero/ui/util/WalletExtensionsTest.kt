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

package io.anonero.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletExtensionsTest {
    @Test
    fun `next index follows created subaddresses even without payments`() {
        // Only the primary address exists.
        assertEquals(1, nextSubaddressIndex(lastUsed = 0, count = 1))
        // Three addresses exist (indices 0, 1, 2) but index 2 never received funds.
        assertEquals(3, nextSubaddressIndex(lastUsed = 0, count = 3))
    }

    @Test
    fun `next index follows the highest used index when it is ahead`() {
        assertEquals(6, nextSubaddressIndex(lastUsed = 5, count = 3))
    }
}
