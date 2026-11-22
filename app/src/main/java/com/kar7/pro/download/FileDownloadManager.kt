package com.kar7.pro.download

import android.content.Context
import android.os.StatFs
import com.kar7.pro.config.Kar7Config
import com.kar7.pro.models.GameDataFile
import com.kar7.pro.util.Formatters
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

@JsonClass(generateAdapter = true)
data class VerifiedFileRecord(
    val fileId: String,
    val fileName: String,
    val version: String,
    val sha256: String,
    val fileSize: Long,
    val mimeType: String,
    val verifiedAtMs: Long
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressPercent: Int
    ) : DownloadState

    data object Verifying : DownloadState
    data class Verified(val record: VerifiedFileRecord, val file: File) : DownloadState
    data object Cancelled : DownloadState
    data class Failed(val errorMessage: String) : DownloadState
}

class FileDownloadManager(
    context: Context,
    private val scope: CoroutineScope,
    private val okHttpClient: OkHttpClient = defaultDownloadClient(),
    private val moshi: Moshi = Moshi.Builder().build()
) {
    private val appContext = context.applicationContext
    private val verifiedDir = File(appContext.filesDir, Kar7Config.VERIFIED_DATA_SUBDIR).apply { mkdirs() }
    private val stagingDir = File(appContext.cacheDir, Kar7Config.STAGING_DATA_SUBDIR).apply { mkdirs() }
    private val indexFile = File(appContext.filesDir, "game_data/verified_index.json").apply {
        parentFile?.mkdirs()
    }

    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    init {
        restoreVerifiedFiles()
    }

    fun getVerifiedFile(fileId: String): File? {
        val state = _downloadStates.value[fileId]
        if (state is DownloadState.Verified && state.file.exists()) {
            return state.file
        }
        return null
    }

    fun getAllVerifiedFiles(): List<Pair<VerifiedFileRecord, File>> {
        return _downloadStates.value.values
            .filterIsInstance<DownloadState.Verified>()
            .filter { it.file.exists() }
            .map { it.record to it.file }
    }

    fun startDownload(dataFile: GameDataFile) {
        val fileId = dataFile.stableId()
        if (activeJobs[fileId]?.isActive == true) return

        val job = scope.launch(Dispatchers.IO) {
            try {
                performDownloadAndVerify(dataFile, fileId)
            } catch (_: CancellationException) {
                cleanupStaging(fileId)
                updateState(fileId, DownloadState.Cancelled)
            } finally {
                activeJobs.remove(fileId)
            }
        }
        activeJobs[fileId] = job
    }

    fun cancelDownload(fileId: String) {
        activeJobs[fileId]?.cancel()
        activeJobs.remove(fileId)
        cleanupStaging(fileId)
        updateState(fileId, DownloadState.Cancelled)
    }

    fun cancelAllDownloads() {
        val ids = activeJobs.keys.toList()
        ids.forEach { cancelDownload(it) }
    }

    fun clearAllDownloads(): Int {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        var deletedCount = 0
        stagingDir.listFiles()?.forEach {
            if (it.delete()) deletedCount++
        }
        verifiedDir.listFiles()?.forEach {
            if (it.delete()) deletedCount++
        }
        if (indexFile.exists()) indexFile.delete()
        _downloadStates.value = emptyMap()
        return deletedCount
    }

    private fun performDownloadAndVerify(dataFile: GameDataFile, fileId: String) {
        val httpUrl = dataFile.downloadUrl.trim().toHttpUrlOrNull()
        if (httpUrl == null || !httpUrl.isHttps) {
            updateState(
                fileId,
                DownloadState.Failed("Blocked insecure or invalid download URL. Only HTTPS downloads are permitted.")
            )
            return
        }

        if (dataFile.sha256.trim().length != 64) {
            updateState(
                fileId,
                DownloadState.Failed("Missing or invalid expected SHA-256 checksum from server.")
            )
            return
        }

        val availableBytes = runCatching {
            StatFs(verifiedDir.absolutePath).availableBytes
        }.getOrDefault(Long.MAX_VALUE)

        val requiredBytes = if (dataFile.fileSize > 0L) dataFile.fileSize + (1024L * 1024L) else 1024L * 1024L
        if (availableBytes < requiredBytes) {
            updateState(
                fileId,
                DownloadState.Failed(
                    "Insufficient device storage. Need at least ${Formatters.formatBytes(requiredBytes)}, " +
                        "only ${Formatters.formatBytes(availableBytes)} available."
                )
            )
            return
        }

        val safeName = Formatters.sanitizeFileName(dataFile.fileName)
        val stagingFile = File(stagingDir, "${fileId.hashCode()}_$safeName.tmp")
        if (stagingFile.exists()) stagingFile.delete()

        updateState(fileId, DownloadState.Downloading(0L, dataFile.fileSize, 0))

        val request = Request.Builder().url(httpUrl).get().build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    updateState(
                        fileId,
                        DownloadState.Failed("Download failed with server HTTP ${response.code}.")
                    )
                    return
                }
                val body = response.body ?: run {
                    updateState(fileId, DownloadState.Failed("Server returned an empty file stream."))
                    return
                }

                val expectedTotal = if (dataFile.fileSize > 0L) dataFile.fileSize else body.contentLength().coerceAtLeast(0L)
                var downloaded = 0L
                var lastEmitTime = 0L
                var lastPercent = -1

                val buffer = ByteArray(Kar7Config.IO_BUFFER_SIZE)
                body.byteStream().use { input ->
                    BufferedOutputStream(FileOutputStream(stagingFile), Kar7Config.IO_BUFFER_SIZE).use { output ->
                        while (true) {
                            if (!scope.coroutineContext[Job]?.isActive.let { it != false }) {
                                throw CancellationException("Download cancelled")
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val percent = if (expectedTotal > 0L) {
                                ((downloaded * 100L) / expectedTotal).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            val now = System.currentTimeMillis()
                            if (percent != lastPercent || now - lastEmitTime >= 150L) {
                                lastPercent = percent
                                lastEmitTime = now
                                updateState(
                                    fileId,
                                    DownloadState.Downloading(downloaded, expectedTotal, percent)
                                )
                            }
                        }
                        output.flush()
                    }
                }
            }
        } catch (ex: CancellationException) {
            stagingFile.delete()
            throw ex
        } catch (ex: IOException) {
            stagingFile.delete()
            updateState(
                fileId,
                DownloadState.Failed("Network interrupted during download: ${ex.message ?: "connection lost"}.")
            )
            return
        }

        // Verification stage (always runs before moving to verified storage)
        updateState(fileId, DownloadState.Verifying)

        if (!FileIntegrityVerifier.verifyFileSize(stagingFile, dataFile.fileSize)) {
            val actualSize = stagingFile.length()
            stagingFile.delete()
            updateState(
                fileId,
                DownloadState.Failed(
                    "File size mismatch. Expected ${Formatters.formatBytes(dataFile.fileSize)}, " +
                        "received ${Formatters.formatBytes(actualSize)}. Corrupted file rejected."
                )
            )
            return
        }

        if (!FileIntegrityVerifier.verifySha256(stagingFile, dataFile.sha256)) {
            stagingFile.delete()
            updateState(
                fileId,
                DownloadState.Failed("SHA-256 verification failed. File does not match server checksum and was rejected.")
            )
            return
        }

        // Reject unsupported executable binaries for security
        val lowerName = safeName.lowercase()
        if (lowerName.endsWith(".apk") || lowerName.endsWith(".dex") || lowerName.endsWith(".so")) {
            stagingFile.delete()
            updateState(
                fileId,
                DownloadState.Failed("Unsupported file format ($safeName). Executable binaries cannot be processed.")
            )
            return
        }

        // If ZIP, validate every entry for path traversal & zip bombs before keeping
        if (lowerName.endsWith(".zip") || FileIntegrityVerifier.isZipFile(stagingFile)) {
            when (val zipCheck = FileIntegrityVerifier.validateZipSafety(stagingFile)) {
                is ZipValidationResult.Invalid -> {
                    stagingFile.delete()
                    updateState(
                        fileId,
                        DownloadState.Failed("ZIP integrity check failed: ${zipCheck.reason}")
                    )
                    return
                }
                is ZipValidationResult.Valid, ZipValidationResult.NotAZip -> Unit
            }
        }

        val targetFile = File(verifiedDir, safeName)
        if (targetFile.exists()) targetFile.delete()
        if (!stagingFile.renameTo(targetFile)) {
            stagingFile.copyTo(targetFile, overwrite = true)
            stagingFile.delete()
        }

        val mimeType = dataFile.mimeType?.takeIf { it.isNotBlank() }
            ?: guessMimeType(safeName)

        val record = VerifiedFileRecord(
            fileId = fileId,
            fileName = safeName,
            version = dataFile.version ?: "1.0",
            sha256 = dataFile.sha256.lowercase(),
            fileSize = targetFile.length(),
            mimeType = mimeType,
            verifiedAtMs = System.currentTimeMillis()
        )
        persistRecord(record)
        updateState(fileId, DownloadState.Verified(record, targetFile))
    }

    private fun restoreVerifiedFiles() {
        if (!indexFile.exists()) return
        val listType = Types.newParameterizedType(List::class.java, VerifiedFileRecord::class.java)
        val adapter = moshi.adapter<List<VerifiedFileRecord>>(listType)
        val records = runCatching { adapter.fromJson(indexFile.readText()) }.getOrNull().orEmpty()
        val restored = mutableMapOf<String, DownloadState>()
        for (record in records) {
            val file = File(verifiedDir, record.fileName)
            if (file.exists() && FileIntegrityVerifier.verifySha256(file, record.sha256)) {
                restored[record.fileId] = DownloadState.Verified(record, file)
            }
        }
        _downloadStates.value = restored
    }

    @Synchronized
    private fun persistRecord(record: VerifiedFileRecord) {
        val current = _downloadStates.value.values
            .filterIsInstance<DownloadState.Verified>()
            .map { it.record }
            .filterNot { it.fileId == record.fileId } + record
        val listType = Types.newParameterizedType(List::class.java, VerifiedFileRecord::class.java)
        val adapter = moshi.adapter<List<VerifiedFileRecord>>(listType)
        runCatching {
            indexFile.writeText(adapter.toJson(current))
        }
    }

    private fun cleanupStaging(fileId: String) {
        val hashPrefix = "${fileId.hashCode()}_"
        stagingDir.listFiles()?.filter { it.name.startsWith(hashPrefix) }?.forEach { it.delete() }
    }

    private fun updateState(fileId: String, newState: DownloadState) {
        _downloadStates.update { current -> current + (fileId to newState) }
    }

    private fun guessMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".zip", ignoreCase = true) -> "application/zip"
            fileName.endsWith(".json", ignoreCase = true) -> "application/json"
            fileName.endsWith(".pak", ignoreCase = true) -> "application/octet-stream"
            fileName.endsWith(".obb", ignoreCase = true) -> "application/octet-stream"
            else -> "application/octet-stream"
        }
    }

    companion object {
        fun defaultDownloadClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(Kar7Config.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Kar7Config.DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(Kar7Config.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
