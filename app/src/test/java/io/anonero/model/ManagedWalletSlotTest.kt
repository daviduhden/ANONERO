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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ManagedWalletSlotTest {
    @get:Rule val folder = TemporaryFolder()

    private val slot = ManagedWalletSlot<Any>()

    @Test
    fun failedCloseRetainsTheWalletAndGuardsItsPath() {
        val main = File(folder.root, "anon")
        val alias = File(folder.root, "./anon")
        val decoy = File(folder.root, "anon-alt")
        val wallet = slot.open(main) { Any() }

        val worker = Executors.newSingleThreadExecutor()
        try {
            assertFalse(
                slot.close(wallet) {
                    worker
                        .submit {
                            assertNull(slot.visible)
                            assertFalse(slot.isIdle())
                            assertThrows(IllegalStateException::class.java) { slot.open(decoy) { error("unexpected acquire") } }
                            assertFalse(slot.close(wallet) { error("unexpected release") })
                            assertFalse(slot.withPathClosed(alias) { error("unexpected deletion") })
                            assertTrue(slot.withPathClosed(decoy) { true })
                        }.get(5, TimeUnit.SECONDS)
                    false
                },
            )
        } finally {
            worker.shutdownNow()
        }

        assertSame(wallet, slot.visibleAt(alias))
        assertTrue(slot.close(wallet) { true })
        assertTrue(slot.withPathClosed(alias) { true })
        assertNull(slot.visible)
    }

    @Test
    fun initializationAndThrowingCloseRetainTheAcquiredOwner() {
        val path = File(folder.root, "anon")
        val wallet = Any()
        assertThrows(IllegalArgumentException::class.java) {
            slot.open(path, initialize = { throw IllegalArgumentException() }) { wallet }
        }
        assertSame(wallet, slot.visible)
        assertThrows(IllegalArgumentException::class.java) {
            slot.close(wallet) { throw IllegalArgumentException() }
        }
        assertSame(wallet, slot.visible)
        assertTrue(slot.close(wallet) { true })
    }

    @Test
    fun deletionRunsUnderTheSlotMonitorOnlyAfterSuccessfulClose() {
        val path = File(folder.root, "anon")
        val wallet = slot.open(path) { Any() }

        assertFalse(slot.close(wallet, afterClose = { error("deleted after failed close") }) { false })
        var deleted = false
        assertTrue(
            slot.close(wallet, afterClose = {
                assertTrue(Thread.holdsLock(slot))
                deleted = slot.withPathClosed(path) { true }
            }) {
                assertFalse(Thread.holdsLock(slot))
                true
            },
        )
        assertTrue(deleted)
    }

    @Test
    fun deletionFailureDoesNotRepublishTheClosedHandle() {
        val path = File(folder.root, "anon")
        val wallet = slot.open(path) { Any() }
        assertThrows(IllegalStateException::class.java) {
            slot.close(wallet, afterClose = { error("profile deletion failed") }) { true }
        }
        assertNull(slot.visible)
        assertTrue(slot.isIdle())
    }

    @Test
    fun failedAcquisitionReleasesTheReservation() {
        val path = File(folder.root, "anon")
        assertThrows(IllegalArgumentException::class.java) {
            slot.open(path) { throw IllegalArgumentException() }
        }
        assertTrue(slot.withPathClosed(path) { true })
    }
}
