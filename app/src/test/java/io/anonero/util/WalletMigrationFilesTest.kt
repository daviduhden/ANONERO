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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WalletMigrationFilesTest {
    @get:Rule val directory = TemporaryFolder()

    private val base get() = File(directory.root, "wallet").path

    @Test
    fun `legacy pair survives repeated replay until verified open`() {
        val (keys, cache) = WalletKey.migrationSnapshots(base)
        keys.first.writeText("old keys")
        cache.first.writeText("old cache")
        repeat(2) {
            keys.second.writeText("partial keys")
            cache.second.writeText("partial cache")
            WalletKey.recoverLegacyFiles(base, wrappedSecretPresent = false)
            assertEquals("old keys", keys.second.readText())
            assertEquals("old cache", cache.second.readText())
            assertTrue(keys.first.exists() && cache.first.exists())
        }
        WalletKey.discardMigrationSnapshots(base)
        assertFalse(keys.first.exists() || cache.first.exists())
    }

    @Test
    fun `missing cache snapshot preserves untouched wallet metadata`() {
        val (keys, cache) = WalletKey.migrationSnapshots(base)
        keys.first.writeText("old keys")
        cache.second.writeText("user notes")
        WalletKey.recoverLegacyFiles(base, wrappedSecretPresent = false)
        assertEquals("old keys", keys.second.readText())
        assertEquals("user notes", cache.second.readText())
    }

    @Test
    fun `interrupted cleanup cannot replace current cache from a lone sidecar`() {
        val (keys, cache) = WalletKey.migrationSnapshots(base)
        keys.second.writeText("current keys")
        cache.second.writeText("current cache")
        cache.first.writeText("stale cache")
        WalletKey.recoverLegacyFiles(base, wrappedSecretPresent = false)
        assertEquals("current cache", cache.second.readText())
        assertEquals("current keys", keys.second.readText())
        assertTrue(cache.first.exists())
        WalletKey.discardMigrationSnapshots(base)
        assertFalse(cache.first.exists())
    }

    @Test
    fun `wrapped secret awaiting verified open keeps live and legacy files`() {
        val (keys, cache) = WalletKey.migrationSnapshots(base)
        keys.first.writeText("legacy keys")
        cache.first.writeText("legacy cache")
        keys.second.writeText("bound keys")
        cache.second.writeText("bound cache")
        WalletKey.recoverLegacyFiles(base, wrappedSecretPresent = true)
        assertEquals("bound keys", keys.second.readText())
        assertEquals("bound cache", cache.second.readText())
        assertEquals("legacy keys", keys.first.readText())
        assertEquals("legacy cache", cache.first.readText())
    }

    @Test
    fun `failure restoring cache preserves snapshot pair for retry`() {
        val (keys, cache) = WalletKey.migrationSnapshots(base)
        keys.first.writeText("old keys")
        cache.first.writeText("old cache")
        assertTrue(cache.second.mkdir())
        File(cache.second, "existing").writeText("occupied")
        assertThrows(java.io.IOException::class.java) {
            WalletKey.recoverLegacyFiles(base, wrappedSecretPresent = false)
        }
        assertEquals("old keys", keys.first.readText())
        assertEquals("old cache", cache.first.readText())
    }
}
