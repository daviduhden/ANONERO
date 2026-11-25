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

package io.anonero.model

import org.junit.Assert.assertThrows
import org.junit.Test

class CoinSelectionTest {
    @Test
    fun allSelectedOutputsMustBelongToTheCapturedAccount() {
        val outputs = listOf(coin("first"), coin("second"))
        validateCoinSelection(listOf("first", "second"), outputs, 1, 15L)
        validateCoinSelection(listOf("first", "second"), outputs, 1, null)
        assertThrows(IllegalArgumentException::class.java) {
            validateCoinSelection(listOf("first", "missing"), outputs, 1, 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateCoinSelection(listOf("first", "missing"), outputs, 1, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateCoinSelection(listOf("first"), outputs, 0, 1L)
        }
    }

    @Test
    fun duplicateAndUnavailableOutputsAreRejected() {
        val output = coin("first")
        assertThrows(IllegalArgumentException::class.java) {
            validateCoinSelection(listOf("first", "first"), listOf(output), 1, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateCoinSelection(listOf("first"), listOf(output, output), 1, null)
        }
        val unavailable =
            listOf(
                output.copy(frozen = true),
                output.copy(spent = true),
                output.copy(unlocked = false),
                output.copy(keyImageKnown = false),
                output.copy(amount = -1),
            )
        for (candidate in unavailable) {
            assertThrows(IllegalArgumentException::class.java) {
                validateCoinSelection(listOf("first"), listOf(candidate), 1, null)
            }
        }
    }

    @Test
    fun selectedTotalMustBeExactAndLeaveRoomForFees() {
        assertThrows(IllegalArgumentException::class.java) {
            validateCoinSelection(listOf("first"), listOf(coin("first")), 1, 10L)
        }
        assertThrows(ArithmeticException::class.java) {
            validateCoinSelection(
                listOf("first", "second"),
                listOf(coin("first").copy(amount = Long.MAX_VALUE), coin("second")),
                1,
                null,
            )
        }
    }

    private fun coin(key: String) =
        CoinsInfo(
            unlockTime = 0,
            spent = false,
            key = key,
            amount = 10L,
            hash = "",
            pub_key = "",
            frozen = false,
            creationTime = 0,
            unlocked = true,
            subaddrAccount = 1,
            keyImageKnown = true,
        )
}
