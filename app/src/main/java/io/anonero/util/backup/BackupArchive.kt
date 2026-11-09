package io.anonero.util.backup

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipFile

/** Extracts the flat MAIN-wallet layout emitted by ANONERO, without replacing files. */
internal object BackupArchive {
    private const val METADATA_NAME = "anon.json"

    private fun isAllowedName(name: String, walletName: String): Boolean =
        name.isNotEmpty() && name.none { it == '/' || it == '\\' || it == ':' || it == '\u0000' } &&
            (name == METADATA_NAME || name == walletName ||
                (name.startsWith("$walletName.") && name.length > walletName.length + 1))

    fun isValidDirectory(directory: File, walletName: String): Boolean {
        if (Files.isSymbolicLink(directory.toPath()) || !directory.isDirectory) return false
        val files = directory.listFiles() ?: return false
        if (files.any {
                !isAllowedName(it.name, walletName) || !it.isFile ||
                    Files.isSymbolicLink(it.toPath())
            }) return false
        return files.any { it.name == METADATA_NAME } &&
            files.any { it.name == "$walletName.keys" }
    }

    fun extract(archive: File, destination: File, walletName: String) {
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
                ) throw IOException("Invalid backup archive entry")
            }
            if (METADATA_NAME !in names || "$walletName.keys" !in names) {
                throw IOException("Backup archive is missing required files")
            }
            if (!destination.mkdirs()) throw IOException("Could not create backup extraction directory")
            try {
                val root = destination.canonicalFile
                entries.forEach { entry ->
                    val output = File(root, entry.name)
                    if (output.canonicalFile.parentFile != root || !output.createNewFile()) {
                        throw IOException("Invalid backup extraction target")
                    }
                    zip.getInputStream(entry).use { input ->
                        output.outputStream().use { input.copyTo(it) }
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

    /** Removes only a generated staging tree; symbolic links are never followed. */
    fun removeExtractionDirectory(directory: File) {
        val path = directory.toPath()
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
