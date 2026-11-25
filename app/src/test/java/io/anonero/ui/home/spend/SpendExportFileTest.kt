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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class SpendExportFileTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun staleAndFailedExportFilesAreRemoved() {
        val destination = directory.newFile("export").apply { writeText("stale") }
        val exported =
            prepareSpendExport(destination) { file ->
                assertFalse(file.exists())
                file.writeText("partial")
                false
            }
        assertFalse(exported)
        assertFalse(destination.exists())
    }

    @Test
    fun thrownExportErrorRemovesPartialOutput() {
        val destination = File(directory.root, "export")
        assertThrows(IOException::class.java) {
            prepareSpendExport(destination) { file ->
                file.writeText("partial")
                throw IOException("fixture")
            }
        }
        assertFalse(destination.exists())
    }

    @Test
    fun successRequiresNonemptyOutputAndRetainsIt() {
        val destination = File(directory.root, "export")
        assertFalse(prepareSpendExport(destination) { it.createNewFile() })
        assertFalse(destination.exists())
        assertTrue(prepareSpendExport(destination) { file -> file.writeText("complete").let { true } })
        assertTrue(destination.isRegularNonEmptyExport())
    }

    @Test
    fun qrReadUsesAnActualByteLimit() {
        val exact = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(exact, ByteArrayInputStream(exact).readBoundedQrExport(4))
        assertNull(ByteArrayInputStream(exact).readBoundedQrExport(3))
        assertNull(ByteArrayInputStream(byteArrayOf()).readBoundedQrExport(4))
    }
}
