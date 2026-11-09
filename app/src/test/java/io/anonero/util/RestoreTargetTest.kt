/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2023-2026 r4v3r23
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

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestoreTargetTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun requireEmpty(directory: File, secret: Boolean = false, credentials: Boolean = false) {
        RestoreTarget.requireEmpty(directory, "anon", secret, credentials)
    }

    @Test
    fun `absent directory is accepted without creating it`() {
        val directory = File(tmp.root, "wallets")
        requireEmpty(directory)
        assertFalse(directory.exists())
    }

    @Test
    fun `empty directory and decoy only directory are accepted unchanged`() {
        val directory = tmp.newFolder("wallets")
        requireEmpty(directory)
        val decoy = File(directory, "anon-alt.keys").apply { writeText("existing fixture") }
        File(directory, "anon-alt").writeText("cache fixture")
        File(directory, "anon-alt.keys.premigrate").writeText("snapshot fixture")
        requireEmpty(directory)
        assertEquals("existing fixture", decoy.readText())
    }

    @Test
    fun `each MAIN artifact blocks restore without deleting it`() {
        val names = listOf("anon", "anon.keys", "anon.address.txt", "anon.premigrate", "anon.keys.premigrate")
        names.forEachIndexed { index, name ->
            val directory = tmp.newFolder("target-$index")
            val existing = File(directory, name).apply { writeText("existing fixture") }
            assertThrows(RestoreTargetNotEmptyException::class.java) { requireEmpty(directory) }
            assertEquals("existing fixture", existing.readText())
        }
    }

    @Test
    fun `orphaned secret or credentials block restore without files`() {
        val directory = File(tmp.root, "missing")
        assertThrows(RestoreTargetNotEmptyException::class.java) { requireEmpty(directory, secret = true) }
        assertThrows(RestoreTargetNotEmptyException::class.java) { requireEmpty(directory, credentials = true) }
        assertFalse(directory.exists())
    }

    @Test
    fun `MAIN directory entries also block restore`() {
        val directory = tmp.newFolder("wallets")
        File(directory, "anon.keys").mkdir()
        assertThrows(RestoreTargetNotEmptyException::class.java) { requireEmpty(directory) }
    }

    @Test
    fun `broken MAIN symbolic links block restore`() {
        val directory = tmp.newFolder("wallets")
        Files.createSymbolicLink(File(directory, "anon.keys").toPath(), File(tmp.root, "missing").toPath())
        assertThrows(RestoreTargetNotEmptyException::class.java) { requireEmpty(directory) }
    }

    @Test
    fun `symbolic wallet directory is refused without touching target`() {
        val outside = tmp.newFolder("outside")
        val sentinel = File(outside, "sentinel").apply { writeText("keep fixture") }
        val directory = File(tmp.root, "wallets")
        Files.createSymbolicLink(directory.toPath(), outside.toPath())
        assertThrows(RestoreTargetNotEmptyException::class.java) { requireEmpty(directory) }
        assertEquals("keep fixture", sentinel.readText())
    }

    @Test
    fun `regular file in place of wallet directory is refused`() {
        val directory = tmp.newFile("wallets")
        assertThrows(RestoreTargetNotEmptyException::class.java) { requireEmpty(directory) }
    }
}
