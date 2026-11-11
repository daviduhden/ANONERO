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

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipFile

/**
 * Extracts the flat MAIN-wallet layout emitted by ANONERO, without replacing files.
 *
 * Every entry is validated before writing: names must be the metadata file, the wallet
 * base, or a wallet sidecar, with no directory separators, traversal, duplicates, or
 * symlinks. A failed extraction removes only the generated staging tree.
 */
internal object BackupArchive {
    private const val METADATA_NAME = "anon.json"
    private const val MAX_ENTRY_BYTES = 256L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024

    private fun isAllowedName(
        name: String,
        walletName: String,
    ): Boolean =
        name.isNotEmpty() && name.none { it == '/' || it == '\\' || it == ':' || it == '\u0000' } &&
            (
                name == METADATA_NAME || name == walletName ||
                    name == "$walletName.keys" || name == "$walletName.address.txt"
            )

    fun isValidDirectory(
        directory: File,
        walletName: String,
    ): Boolean {
        if (Files.isSymbolicLink(directory.toPath()) || !directory.isDirectory) return false
        val files = directory.listFiles() ?: return false
        if (files.any {
                !isAllowedName(it.name, walletName) || !it.isFile ||
                    Files.isSymbolicLink(it.toPath())
            }
        ) {
            return false
        }
        return files.any { it.name == METADATA_NAME } &&
            files.any { it.name == "$walletName.keys" }
    }

    fun extract(
        archive: File,
        destination: File,
        walletName: String,
    ) {
        if (Files.isSymbolicLink(destination.toPath()) || destination.exists()) {
            throw IOException("Backup extraction requires a fresh directory")
        }
        ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val names = HashSet<String>()
            // Validate the whole directory before writing any plaintext, including
            // entries after otherwise valid metadata and wallet files.
            entries.forEach { entry ->
                if (entry.isDirectory || !isAllowedName(entry.name, walletName) ||
                    !names.add(entry.name)
                ) {
                    throw IOException("Invalid backup archive entry")
                }
                if (entry.size > MAX_ENTRY_BYTES) {
                    throw IOException("Backup archive entry is too large")
                }
            }
            if (METADATA_NAME !in names || "$walletName.keys" !in names) {
                throw IOException("Backup archive is missing required files")
            }
            if (!destination.mkdirs()) throw IOException("Could not create backup extraction directory")
            try {
                val root = destination.canonicalFile
                var total = 0L
                entries.forEach { entry ->
                    val output = File(root, entry.name)
                    if (output.canonicalFile.parentFile != root || !output.createNewFile()) {
                        throw IOException("Invalid backup extraction target")
                    }
                    zip.getInputStream(entry).use { input ->
                        output.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            var entryTotal = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                entryTotal += read
                                total += read
                                if (entryTotal > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                                    throw IOException("Backup archive is too large")
                                }
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                try {
                    removeExtractionDirectory(destination)
                } catch (cleanup: Exception) {
                    e.addSuppressed(cleanup)
                }
                throw e
            }
        }
    }

    // Removes only a generated staging tree; symbolic links are never followed.
    fun removeExtractionDirectory(directory: File) {
        val path = directory.toPath()
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    dir: Path,
                    exc: IOException?,
                ): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }
}
