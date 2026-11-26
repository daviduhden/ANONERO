/*
 * Copyright (c) 2026, David Uhden Collado
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
import org.junit.Assert.assertNull
import org.junit.Test

class WalletExtensionsTest {
    @Test
    fun `successive requests append once each before either address receives payment`() {
        var count = 1
        var stores = 0
        val issued =
            List(2) {
                appendStoredSubaddress(0, { count }, { index ->
                    assertEquals(count, index)
                    count++
                }, {
                    stores++
                    true
                })
            }
        assertEquals(listOf(1, 2), issued)
        assertEquals(2, stores)
    }

    @Test
    fun `ahead of count observations and invalid counts cannot issue an address`() {
        for ((used, count) in listOf(5 to 3, 0 to 0, 0 to Int.MAX_VALUE)) {
            assertNull(appendStoredSubaddress(used, { count }, { error("unexpected append") }, { error("unexpected store") }))
        }
    }

    @Test
    fun `void append and failed store cannot publish an index`() {
        assertNull(appendStoredSubaddress(0, { 1 }, {}, { error("unexpected store") }))
        var count = 1
        assertNull(appendStoredSubaddress(0, { count }, { count++ }, { false }))
    }
}
