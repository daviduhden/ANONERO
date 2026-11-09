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
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes

class RestoreTargetNotEmptyException : IOException("Restore target is unavailable")

/* Existing and partially created profiles require explicit recovery or wipe, not replacement. */
internal object RestoreTarget {
    fun requireEmpty(
        directory: File,
        walletName: String,
        hasWrappedSecret: Boolean,
        hasCredentials: Boolean,
    ) {
        if (hasWrappedSecret || hasCredentials) {
            throw RestoreTargetNotEmptyException()
        }
        val attributes = try {
            Files.readAttributes(directory.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (_: NoSuchFileException) {
            return
        } catch (_: IOException) {
            throw RestoreTargetNotEmptyException()
        }
        if (!attributes.isDirectory || attributes.isSymbolicLink) throw RestoreTargetNotEmptyException()
        val entries = directory.listFiles() ?: throw RestoreTargetNotEmptyException()
        // Include broken symlinks and directories: filtering only regular files could
        // miss a keys file or snapshot behind an unexpected filesystem entry.
        if (entries.any { it.name == walletName || it.name.startsWith("$walletName.") }) {
            throw RestoreTargetNotEmptyException()
        }
    }
}
