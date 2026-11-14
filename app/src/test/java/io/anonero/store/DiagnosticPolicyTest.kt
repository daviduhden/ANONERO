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

package io.anonero.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticPolicyTest {
    @Test
    fun `untrusted payloads and tags cannot reach exported diagnostics`() {
        // Synthetic payloads; never real wallet data.
        listOf(
            "socks5://user:password@private.example:9050",
            "transaction deadbeef amount 1234567",
            "seed words supplied by an exception",
            "Wallet reconnect failed; refresh paused\njava.io.IOException: secret",
            "127.0.0.1 private-node.onion",
        ).forEach { payload ->
            val safe = DiagnosticPolicy.sanitize(AnonLog(123456L, payload, payload, 6))
            assertEquals("Application", safe.tag)
            assertEquals("Diagnostic details withheld", safe.message)
            assertFalse(safe.toString().contains(payload))
        }
    }

    @Test
    fun `reviewed failure remains useful with coarse timestamp`() {
        val safe = DiagnosticPolicy.sanitize(AnonLog(123456L, "wallet-name", "Node connection failed", 5))
        assertEquals("Node connection failed", safe.message)
        assertEquals(120000L, safe.date)
        assertEquals(5, safe.priority)
    }

    @Test
    fun `sanitizing historical records is idempotent`() {
        val safe = DiagnosticPolicy.sanitize(AnonLog(123456L, "private", "untrusted text", 6))
        assertEquals(safe, DiagnosticPolicy.sanitize(safe))
    }
}
