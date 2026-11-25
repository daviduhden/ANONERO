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

package io.anonero.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiPaymentPolicyTest {
    @Test
    fun `reject empty excessive nonpositive and overflowing requests`() {
        assertNull(MultiPaymentPolicy.totalOrNull(emptyList()))
        assertNull(MultiPaymentPolicy.totalOrNull(List(17) { "synthetic-address" to 1L }))
        assertNull(MultiPaymentPolicy.totalOrNull(listOf("" to 1L)))
        assertNull(MultiPaymentPolicy.totalOrNull(listOf("synthetic-address" to 0L)))
        assertNull(MultiPaymentPolicy.totalOrNull(listOf("synthetic-address" to -1L)))
        assertNull(MultiPaymentPolicy.totalOrNull(listOf("a" to Long.MAX_VALUE, "b" to 1L)))
        assertEquals(3L, MultiPaymentPolicy.totalOrNull(listOf("a" to 1L, "b" to 2L)))
    }

    @Test
    fun `adding removing replacing or reordering inputs invalidates old checks`() {
        val checked = AddressValidationSnapshot(listOf("a", "b"), listOf(true, true))
        assertTrue(checked.allValidFor(listOf("a", "b")))
        listOf(listOf("a", "b", "c"), listOf("a"), listOf("a", "c"), listOf("b", "a"))
            .forEach { current ->
                assertFalse(checked.allValidFor(current))
                assertFalse(checked.isValidAt(current, 2))
            }
        assertFalse(AddressValidationSnapshot(listOf("a"), emptyList()).allValidFor(listOf("a")))
        assertFalse(AddressValidationSnapshot(emptyList(), emptyList()).allValidFor(emptyList()))
    }

    @Test
    fun `last permitted recipient is accepted but the next is rejected`() {
        val maximum = List(MultiPaymentPolicy.MAX_RECIPIENTS) { "synthetic-$it" to 1L }
        assertEquals(maximum.size.toLong(), MultiPaymentPolicy.totalOrNull(maximum))
        assertNull(MultiPaymentPolicy.totalOrNull(maximum + ("extra" to 1L)))
    }

    @Test
    fun `one invalid recipient rejects an otherwise valid batch`() {
        val valid = listOf("first" to 1L, "second" to 2L)
        for (invalid in listOf(" \t\n" to 1L, "third" to 0L, "third" to Long.MIN_VALUE)) {
            assertNull(MultiPaymentPolicy.totalOrNull(valid + invalid))
        }
        assertNull(MultiPaymentPolicy.totalOrNull(listOf("first" to 1L, "second" to Long.MAX_VALUE)))
    }

    @Test
    fun `a failed address never authorizes the batch but retains per-row feedback`() {
        val addresses = listOf("first", "second")
        val checked = AddressValidationSnapshot(addresses, listOf(true, false))
        assertFalse(checked.allValidFor(addresses))
        assertTrue(checked.isValidAt(addresses, 0))
        assertFalse(checked.isValidAt(addresses, 1))
        assertFalse(checked.isValidAt(addresses, -1))
        assertFalse(checked.isValidAt(addresses, addresses.size))
    }

    @Test
    fun `extra validation results cannot authorize a shortened request`() {
        val checked = AddressValidationSnapshot(listOf("first"), listOf(true, true))
        assertFalse(checked.matches(listOf("first")))
        assertFalse(checked.allValidFor(listOf("first")))
        assertFalse(checked.isValidAt(listOf("first"), 0))
    }
}
