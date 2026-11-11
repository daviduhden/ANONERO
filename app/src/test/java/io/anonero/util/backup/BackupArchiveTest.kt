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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun archive(vararg names: String): File =
        tmp.newFile().also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                names.forEach { name ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write("archive fixture".toByteArray())
                    zip.closeEntry()
                }
            }
        }

    private fun expectRejected(block: () -> Unit) {
        try {
            block()
            fail("Invalid backup must be rejected")
        } catch (_: IOException) {
        }
    }

    @Test
    fun `flat metadata keys cache and sidecars extract unchanged`() {
        val names = arrayOf("anon.json", "anon.keys", "anon", "anon.address.txt")
        val source = archive(*names)
        val destination = File(tmp.root, "extract")
        BackupArchive.extract(source, destination, "anon")
        assertTrue(BackupArchive.isValidDirectory(destination, "anon"))
        assertEquals(names.toSet(), destination.listFiles()!!.map { it.name }.toSet())
        names.forEach { assertEquals("archive fixture", File(destination, it).readText()) }
    }

    @Test
    fun `invalid trailing entry is rejected before writing valid preceding files`() {
        val invalidNames =
            listOf(
                "../outside",
                "/absolute",
                "nested/anon.keys",
                "anon/../anon.keys",
                "..\\outside",
                "C:\\outside",
                "anon.keys/",
                "anon-alt.keys",
                "unrelated",
            )
        invalidNames.forEachIndexed { index, name ->
            val source = archive("anon.json", "anon.keys", name)
            val destination = File(tmp.root, "extract-$index")
            expectRejected { BackupArchive.extract(source, destination, "anon") }
            assertFalse(destination.exists())
        }
        assertFalse(File(tmp.root, "outside").exists())
    }

    @Test
    fun `missing exact metadata or wallet keys is rejected`() {
        listOf(arrayOf("anon.json"), arrayOf("anon.keys"), arrayOf("anon.json", "anon.other.keys"))
            .forEachIndexed { index, names ->
                val destination = File(tmp.root, "missing-$index")
                expectRejected { BackupArchive.extract(archive(*names), destination, "anon") }
                assertFalse(destination.exists())
            }
    }

    @Test
    fun `duplicate central directory names are rejected`() {
        // ZipOutputStream rejects duplicate names itself; replace equal-length names
        // in both headers to model an archive received from an untrusted producer.
        val source = archive("anon.json", "anon.keys", "anon.kezs")
        val bytes = source.readBytes()
        val from = "anon.kezs".toByteArray()
        val to = "anon.keys".toByteArray()
        var replacements = 0
        for (offset in 0..bytes.size - from.size) {
            if (from.indices.all { bytes[offset + it] == from[it] }) {
                to.copyInto(bytes, offset)
                replacements++
            }
        }
        assertEquals(2, replacements)
        source.writeBytes(bytes)
        val destination = File(tmp.root, "duplicates")
        expectRejected { BackupArchive.extract(source, destination, "anon") }
        assertFalse(destination.exists())
    }

    @Test
    fun `existing extraction is not overwritten or merged`() {
        val destination = tmp.newFolder("existing")
        val existing = File(destination, "anon.keys").apply { writeText("existing fixture") }
        expectRejected {
            BackupArchive.extract(archive("anon.json", "anon.keys"), destination, "anon")
        }
        assertEquals("existing fixture", existing.readText())
        assertFalse(File(destination, "anon.json").exists())
    }

    @Test
    fun `symbolic link destination is rejected without modifying its target`() {
        val outside = tmp.newFolder("outside")
        val destination = File(tmp.root, "link")
        Files.createSymbolicLink(destination.toPath(), outside.toPath())
        expectRejected {
            BackupArchive.extract(archive("anon.json", "anon.keys"), destination, "anon")
        }
        assertTrue(outside.listFiles()!!.isEmpty())
    }

    @Test
    fun `restore directory validation refuses symlinks and unexpected files`() {
        val destination = tmp.newFolder("validate")
        File(destination, "anon.json").writeText("metadata fixture")
        val keys = File(destination, "anon.keys").apply { writeText("wallet fixture") }
        assertTrue(BackupArchive.isValidDirectory(destination, "anon"))
        val unexpected = File(destination, "anon-alt.keys").apply { writeText("other fixture") }
        assertFalse(BackupArchive.isValidDirectory(destination, "anon"))
        assertTrue(unexpected.delete())
        assertTrue(keys.delete())
        val outside = tmp.newFile("outside-keys")
        Files.createSymbolicLink(keys.toPath(), outside.toPath())
        assertFalse(BackupArchive.isValidDirectory(destination, "anon"))
    }

    @Test
    fun `staging cleanup does not follow symbolic links`() {
        val outside = tmp.newFolder("keep")
        val sentinel = File(outside, "sentinel").apply { writeText("keep fixture") }
        val destination = tmp.newFolder("partial")
        File(destination, "anon.json").writeText("partial fixture")
        Files.createSymbolicLink(File(destination, "link").toPath(), outside.toPath())
        BackupArchive.removeExtractionDirectory(destination)
        assertFalse(destination.exists())
        assertEquals("keep fixture", sentinel.readText())
    }
}
