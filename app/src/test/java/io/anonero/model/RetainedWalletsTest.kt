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

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RetainedWalletsTest {
    @Test
    fun `failed close retains ownership and retry releases only on success`() {
        var mayClose = false
        val owner = RetainedWallets<Any> { mayClose }
        val wallet = owner.open("/tmp/backup/anon") { Any() }
        assertFalse(owner.close(wallet))
        assertTrue(owner.contains("/tmp/backup/anon"))
        assertTrue(owner.containsIn(File("/tmp/backup")))
        assertFalse(owner.containsIn(File("/tmp/back")))
        assertThrows(IllegalStateException::class.java) {
            owner.open("/tmp/backup/anon") { Any() }
        }
        mayClose = true
        assertTrue(owner.closeAt("/tmp/backup/anon"))
        assertTrue(owner.isEmpty())
        assertTrue(owner.closeAt("/tmp/backup/anon"))
    }

    @Test
    fun `throwing close retains resource for a later retry`() {
        var fail = true
        val owner =
            RetainedWallets<Any> {
                if (fail) throw IllegalStateException("injected close failure")
                true
            }
        val wallet = owner.open("/tmp/decoy") { Any() }
        assertThrows(IllegalStateException::class.java) { owner.close(wallet) }
        assertTrue(owner.contains("/tmp/decoy"))
        fail = false
        assertTrue(owner.closeAt("/tmp/decoy"))
        assertTrue(owner.isEmpty())
    }

    @Test
    fun `ownership uses identity and rejects foreign or previously closed objects`() {
        val owner = RetainedWallets<Any> { true }
        val wallet = owner.open("/tmp/anon-alt") { Any() }
        assertThrows(IllegalStateException::class.java) { owner.close(Any()) }
        assertTrue(owner.close(wallet))
        assertThrows(IllegalStateException::class.java) { owner.close(wallet) }
    }

    @Test
    fun `cancellation remains cancellation and ownership is retained`() {
        val cancellation = CancellationException("injected cancellation")
        val owner = RetainedWallets<Any> { throw cancellation }
        val wallet = owner.open("/tmp/cancelled-copy") { Any() }
        val thrown = assertThrows(CancellationException::class.java) { owner.close(wallet) }
        assertSame(cancellation, thrown)
        assertTrue(owner.contains("/tmp/cancelled-copy"))
    }

    @Test
    fun `failed acquisition does not invent an owned wallet`() {
        val owner = RetainedWallets<Any> { true }
        assertThrows(IllegalStateException::class.java) {
            owner.open("/tmp/failed-open") { throw IllegalStateException("injected open failure") }
        }
        assertTrue(owner.isEmpty())
    }

    @Test
    fun `destructive callback runs only without retained ownership and under its monitor`() {
        val owner = RetainedWallets<Any> { true }
        val wallet = owner.open("/tmp/remove-decoy") { Any() }
        var deleted = false
        assertFalse(
            owner.withPathClosed("/tmp/remove-decoy") {
                deleted = true
                true
            },
        )
        assertFalse(deleted)
        assertTrue(owner.close(wallet))
        assertTrue(
            owner.withPathClosed("/tmp/remove-decoy") {
                assertTrue(Thread.holdsLock(owner))
                deleted = true
                true
            },
        )
        assertTrue(deleted)
        assertFalse(owner.withPathClosed("/tmp/remove-decoy") { false })
    }
}
