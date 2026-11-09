package io.anonero.util

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes

class RestoreTargetNotEmptyException : IOException("Restore target is unavailable")

/** Existing and partially created profiles require explicit recovery or wipe, not replacement. */
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
