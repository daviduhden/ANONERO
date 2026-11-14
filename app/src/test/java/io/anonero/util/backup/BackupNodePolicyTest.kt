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

package io.anonero.util.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupNodePolicyTest {
    @Test
    fun `accepts DNS onion i2p ipv4 and bracket-aware ipv6 hosts`() {
        assertEquals("node.example.com", BackupNodePolicy.normalizeHost("  node.example.com "))
        assertEquals("node.example.onion", BackupNodePolicy.normalizeHost("node.example.onion"))
        assertEquals("node.example.i2p", BackupNodePolicy.normalizeHost("node.example.i2p"))
        assertEquals("127.0.0.1", BackupNodePolicy.normalizeHost("127.0.0.1"))
        assertEquals("::1", BackupNodePolicy.normalizeHost("::1"))
        assertEquals("2001:db8::1", BackupNodePolicy.normalizeHost("[2001:db8::1]"))
        assertEquals("fe80::1%wlan0", BackupNodePolicy.normalizeHost("fe80::1%wlan0"))
    }

    @Test
    fun `rejects schemes credentials paths whitespace and invalid hosts`() {
        assertNull(BackupNodePolicy.normalizeHost(null))
        assertNull(BackupNodePolicy.normalizeHost(""))
        assertNull(BackupNodePolicy.normalizeHost("   "))
        assertNull(BackupNodePolicy.normalizeHost("http://node.example.com"))
        assertNull(BackupNodePolicy.normalizeHost("node.example.com/path"))
        assertNull(BackupNodePolicy.normalizeHost("user:pass@node.example.com"))
        assertNull(BackupNodePolicy.normalizeHost("node example.com"))
        assertNull(BackupNodePolicy.normalizeHost("-node.example.com"))
        assertNull(BackupNodePolicy.normalizeHost("node.example.com."))
        assertNull(BackupNodePolicy.normalizeHost("node:8000"))
        assertNull(BackupNodePolicy.normalizeHost("1.2.3.4:8443"))
        assertNull(BackupNodePolicy.normalizeHost("gggg::1"))
        assertNull(BackupNodePolicy.normalizeHost("a".repeat(254)))
    }

    @Test
    fun `accepts only TCP ports`() {
        assertEquals(1, BackupNodePolicy.normalizePort(1))
        assertEquals(65535, BackupNodePolicy.normalizePort(65535))
        assertNull(BackupNodePolicy.normalizePort(0))
        assertNull(BackupNodePolicy.normalizePort(-1))
        assertNull(BackupNodePolicy.normalizePort(65536))
    }
}
