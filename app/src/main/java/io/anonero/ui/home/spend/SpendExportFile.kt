package io.anonero.ui.home.spend

import io.anonero.AnonConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption

private const val MAX_QR_EXPORT_BYTES = 16 * 1024 * 1024

internal fun prepareUnsignedExport(
    cacheDir: File,
    write: (File) -> Boolean,
): Boolean = prepareSpendExport(File(cacheDir, AnonConfig.EXPORT_UNSIGNED_TX_FILE), write)

internal fun prepareSpendExport(
    file: File,
    write: (File) -> Boolean,
): Boolean {
    val path = file.toPath()
    Files.deleteIfExists(path)
    try {
        val complete = write(file) && file.isRegularNonEmptyExport()
        if (!complete) Files.deleteIfExists(path)
        return complete
    } catch (failure: Throwable) {
        try {
            Files.deleteIfExists(path)
        } catch (cleanup: IOException) {
            failure.addSuppressed(cleanup)
        } catch (cleanup: SecurityException) {
            failure.addSuppressed(cleanup)
        }
        throw failure
    }
}

internal fun File.isRegularNonEmptyExport(): Boolean = Files.isRegularFile(toPath(), LinkOption.NOFOLLOW_LINKS) && length() > 0L

/** Larger files can be exported directly without building a QR payload in memory. */
internal fun File.readQrExport(): ByteArray? {
    if (length() > MAX_QR_EXPORT_BYTES) return null
    return Files.newInputStream(toPath(), LinkOption.NOFOLLOW_LINKS).use {
        it.readBoundedQrExport(MAX_QR_EXPORT_BYTES)
    }
}

internal fun InputStream.readBoundedQrExport(limit: Int): ByteArray? {
    require(limit > 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = limit
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size, remaining))
        if (read < 0) break
        if (read == 0) throw IOException("Export stream returned no bytes")
        output.write(buffer, 0, read)
        remaining -= read
    }
    if (remaining == 0 && read() != -1) return null
    return output.toByteArray().takeIf { it.isNotEmpty() }
}
