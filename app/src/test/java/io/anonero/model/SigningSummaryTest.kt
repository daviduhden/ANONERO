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

package io.anonero.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SigningSummaryTest {
    // These are format fixtures, not valid Monero addresses with checksums.
    private val addressA =
        "44AFFq5kSiGBoZQm3vW6cD7eF8gH9iJ2kL3mN4pQ5rS6tU7vW8xY9zA"
    private val addressB =
        "4AdUndXHHZ6cfufTMvppY6JwXNouMBzSkbLYfpAV5Usx3skxNgYeYTRj5UzqtReoS44qo9mtmXCqY45DJ852K5Jv2684"

    @Test
    fun `parses a one-destination transaction with no change`() {
        val message =
            "Loaded 1 transactions, for 0.500100000000, fee 0.000100000000, " +
                "sending 0.500000000000 to $addressA, no change, with min ring size 16."

        val summary = SigningSummary.parse(message)

        requireNotNull(summary)
        assertEquals(1L, summary.txCount)
        assertEquals(500_000_000_000L, summary.amount)
        assertEquals(100_000_000L, summary.fee)
        assertNull(summary.change)
        assertEquals(16L, summary.minRingSize)
        assertEquals(1, summary.destinations.size)
        assertEquals(500_000_000_000L, summary.destinations.single().amount)
        assertEquals(addressA, summary.destinations.single().address)
    }

    @Test
    fun `parses change and keeps it separate from the paid amount`() {
        val message =
            "Loaded 1 transactions, for 1.000100000000, fee 0.000100000000, " +
                "sending 0.500000000000 to $addressA, " +
                "0.500000000000 change to $addressB, with min ring size 16."

        val summary = SigningSummary.parse(message)

        requireNotNull(summary)
        assertEquals(500_000_000_000L, summary.amount)
        assertEquals(100_000_000L, summary.fee)
        assertEquals(500_000_000_000L, summary.change?.amount)
        assertEquals(addressB, summary.change?.address)
        assertEquals(1, summary.destinations.size)
    }

    @Test
    fun `parses multiple destinations as one transaction set`() {
        val message =
            "Loaded 1 transactions, for 0.300100000000, fee 0.000100000000, " +
                "sending 0.100000000000 to $addressA, " +
                "sending 0.200000000000 to $addressB, no change, with min ring size 16."

        val summary = SigningSummary.parse(message)

        requireNotNull(summary)
        assertEquals(300_000_000_000L, summary.amount)
        assertEquals(2, summary.destinations.size)
        assertEquals(listOf(100_000_000_000L, 200_000_000_000L), summary.destinations.map { it.amount })
    }

    @Test
    fun `keeps the integrated address when a payment id is present`() {
        val message =
            "Loaded 1 transactions, for 0.500100000000, fee 0.000100000000, " +
                "sending 0.500000000000 to $addressA " +
                "($addressB with encrypted payment id 0123456789abcdef), " +
                "no change, with min ring size 16."

        val summary = SigningSummary.parse(message)

        requireNotNull(summary)
        assertEquals(addressA, summary.destinations.single().address)
    }

    @Test
    fun `rejects totals that do not add up`() {
        val message =
            "Loaded 1 transactions, for 0.500000000000, fee 0.000100000000, " +
                "sending 0.500000000000 to $addressA, no change, with min ring size 16."

        assertNull(SigningSummary.parse(message))
    }

    @Test
    fun `rejects null empty and truncated messages`() {
        assertNull(SigningSummary.parse(null))
        assertNull(SigningSummary.parse(""))
        assertNull(SigningSummary.parse("Loaded 1 transactions"))
    }
}
