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

package io.anonero.ui.home.spend

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BroadcastRecoveryTest {
    @Test
    fun acceptancePrecedesRecovery() {
        var accepted = false
        val recovered =
            completeAcceptedBroadcast(
                publishAcceptance = { accepted = true },
                refreshAndStore = {
                    assertTrue(accepted)
                    true
                },
            )
        assertTrue(accepted)
        assertTrue(recovered)
    }

    @Test
    fun unsuccessfulStoreDoesNotUndoAcceptance() {
        var accepted = false
        val recovered =
            completeAcceptedBroadcast(
                publishAcceptance = { accepted = true },
                refreshAndStore = { false },
            )
        assertTrue(accepted)
        assertFalse(recovered)
    }

    @Test
    fun recoveryExceptionDoesNotUndoAcceptance() {
        var accepted = false
        val recovered =
            completeAcceptedBroadcast(
                publishAcceptance = { accepted = true },
                refreshAndStore = { throw IOException("fixture") },
            )
        assertTrue(accepted)
        assertFalse(recovered)
    }

    @Test
    fun cancellationPropagatesAfterPublishingAcceptance() {
        var accepted = false
        val cancelled = CancellationException("fixture")
        val thrown =
            assertThrows(CancellationException::class.java) {
                completeAcceptedBroadcast(
                    publishAcceptance = { accepted = true },
                    refreshAndStore = { throw cancelled },
                )
            }
        assertTrue(accepted)
        assertSame(cancelled, thrown)
    }
}
