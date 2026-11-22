package com.kar7.pro.download

import com.kar7.pro.config.Kar7Config
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

sealed interface ZipValidationResult {
    data class Valid(val entryCount: Int, val totalUncompressedBytes: Long) : ZipValidationResult
    data object NotAZip : ZipValidationResult
    data class Invalid(val reason: String) : ZipValidationResult
}

object FileIntegrityVerifier {

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(Kar7Config.IO_BUFFER_SIZE)
        BufferedInputStream(FileInputStream(file), Kar7Config.IO_BUFFER_SIZE).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifySha256(file: File, expectedSha256: String): Boolean {
        val expected = expectedSha256.trim().lowercase()
        if (expected.length != 64 || !expected.all { it in '0'..'9' || it in 'a'..'f' }) {
            return false
        }
        val actual = calculateSha256(file)
        return actual.equals(expected, ignoreCase = true)
    }

    fun verifyFileSize(file: File, expectedSize: Long): Boolean {
        if (expectedSize <= 0L) return file.length() > 0L
        return file.length() == expectedSize
    }

    fun isZipFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        val header = ByteArray(4)
        FileInputStream(file).use { input ->
            if (input.read(header) < 4) return false
        }
        // Standard ZIP local file header PK\x03\x04 or empty archive PK\x05\x06
        return header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
            ((header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) ||
                (header[2] == 0x05.toByte() && header[3] == 0x06.toByte()))
    }

    /**
     * Strictly inspects a verified ZIP archive without extracting it to unsafe destinations.
     * Prevents Zip Slip (path traversal), unsafe entry names, and zip bombs.
     */
    fun validateZipSafety(
        file: File,
        maxEntries: Int = Kar7Config.MAX_ZIP_ENTRIES,
        maxUncompressedBytes: Long = Kar7Config.MAX_ZIP_UNCOMPRESSED_BYTES
    ): ZipValidationResult {
        if (!isZipFile(file)) {
            return if (file.name.lowercase().endsWith(".zip")) {
                ZipValidationResult.Invalid("File has .zip extension but invalid ZIP archive header.")
            } else {
                ZipValidationResult.NotAZip
            }
        }

        val virtualRoot = File("/kar7_virtual_root")
        val canonicalRoot = virtualRoot.canonicalPath

        var entries = 0
        var totalBytes = 0L
        val buffer = ByteArray(Kar7Config.IO_BUFFER_SIZE)

        return try {
            ZipInputStream(BufferedInputStream(FileInputStream(file), Kar7Config.IO_BUFFER_SIZE)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    entries++
                    if (entries > maxEntries) {
                        return ZipValidationResult.Invalid("ZIP archive exceeds maximum allowed entry count ($maxEntries).")
                    }

                    val name = entry.name
                    if (!isSafeEntryPath(name, virtualRoot, canonicalRoot)) {
                        return ZipValidationResult.Invalid("Unsafe path traversal entry detected in ZIP: $name")
                    }

                    if (!entry.isDirectory) {
                        val declaredSize = entry.size
                        if (declaredSize > maxUncompressedBytes) {
                            return ZipValidationResult.Invalid("ZIP entry exceeds safe uncompressed size limit.")
                        }
                        var entryBytes = 0L
                        while (true) {
                            val read = zis.read(buffer)
                            if (read <= 0) break
                            entryBytes += read
                            totalBytes += read
                            if (totalBytes > maxUncompressedBytes) {
                                return ZipValidationResult.Invalid("ZIP archive exceeds safe total uncompressed size limit.")
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            ZipValidationResult.Valid(entryCount = entries, totalUncompressedBytes = totalBytes)
        } catch (ex: Exception) {
            ZipValidationResult.Invalid(ex.message ?: "Corrupted or unreadable ZIP archive.")
        }
    }

    private fun isSafeEntryPath(
        entryName: String,
        virtualRoot: File,
        canonicalRoot: String
    ): Boolean {
        if (entryName.isBlank() ||
            entryName.contains('\u0000') ||
            entryName.startsWith("/") ||
            entryName.startsWith("\\") ||
            entryName.contains("..")
        ) {
            return false
        }
        val normalized = entryName.replace('\\', '/')
        val target = File(virtualRoot, normalized)
        val canonicalTarget = runCatching { target.canonicalPath }.getOrNull() ?: return false
        return canonicalTarget.startsWith(canonicalRoot + File.separator) || canonicalTarget == canonicalRoot
    }
}
