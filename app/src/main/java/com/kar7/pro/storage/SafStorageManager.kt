package com.kar7.pro.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.kar7.pro.config.Kar7Config
import com.kar7.pro.security.SecureSessionStore
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SafFolderStatus(
    val uriString: String? = null,
    val displayName: String? = null,
    val hasWritePermission: Boolean = false,
    val isRestrictedByScopedStorage: Boolean = false,
    val limitationMessage: String? = null
)

sealed interface SafExportResult {
    data class Success(val destinationDisplay: String) : SafExportResult
    data class Restricted(val explanation: String) : SafExportResult
    data class Failed(val errorMessage: String) : SafExportResult
}

class SafStorageManager(
    private val context: Context,
    private val sessionStore: SecureSessionStore
) {
    private val appContext = context.applicationContext

    fun getVerifiedVaultPath(): String =
        File(appContext.filesDir, Kar7Config.VERIFIED_DATA_SUBDIR).absolutePath

    fun getCurrentSafFolderStatus(): SafFolderStatus {
        val savedUriStr = sessionStore.loadSafTreeUri() ?: return SafFolderStatus()
        val uri = runCatching { Uri.parse(savedUriStr) }.getOrNull() ?: return SafFolderStatus()

        val hasPersistedGrant = appContext.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull().orEmpty()
        val restricted = isScopedStorageRestrictedDocId(docId)
        val display = formatDocIdForDisplay(docId, uri)

        return SafFolderStatus(
            uriString = savedUriStr,
            displayName = display,
            hasWritePermission = hasPersistedGrant && !restricted,
            isRestrictedByScopedStorage = restricted,
            limitationMessage = if (restricted) {
                "Android 11+ restricts direct access to another app's Android/data or Android/obb folder. " +
                    "Use an accessible folder, SAF single-file export, or the FileProvider Sync Handoff to the target game."
            } else if (!hasPersistedGrant) {
                "Folder permission was revoked or is no longer persisted. Please choose a folder again."
            } else {
                null
            }
        )
    }

    fun handleTreeUriSelected(uri: Uri?): SafFolderStatus {
        if (uri == null) {
            return getCurrentSafFolderStatus()
        }
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            appContext.contentResolver.takePersistableUriPermission(uri, flags)
            val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull().orEmpty()
            val restricted = isScopedStorageRestrictedDocId(docId)
            sessionStore.saveSafTreeUri(uri.toString())
            SafFolderStatus(
                uriString = uri.toString(),
                displayName = formatDocIdForDisplay(docId, uri),
                hasWritePermission = !restricted,
                isRestrictedByScopedStorage = restricted,
                limitationMessage = if (restricted) {
                    "Android Scoped Storage prevents writing directly into another app's Android/data directory. " +
                        "Please select a shared folder (such as Documents/KAR7_Exports) or use FileProvider Sync Handoff."
                } else {
                    null
                }
            )
        } catch (ex: SecurityException) {
            SafFolderStatus(
                uriString = null,
                displayName = null,
                hasWritePermission = false,
                isRestrictedByScopedStorage = true,
                limitationMessage = "System denied persistent access to this folder (${ex.message ?: "Scoped Storage restriction"}). " +
                    "Select a user-accessible folder or use SAF single-file export."
            )
        }
    }

    suspend fun exportVerifiedFileToSelectedFolder(
        sourceFile: File,
        fileName: String,
        mimeType: String
    ): SafExportResult = withContext(Dispatchers.IO) {
        val status = getCurrentSafFolderStatus()
        val treeUriStr = status.uriString
            ?: return@withContext SafExportResult.Restricted("No Game Data Folder selected yet. Tap 'Choose Game Data Folder' first.")

        if (status.isRestrictedByScopedStorage) {
            return@withContext SafExportResult.Restricted(
                status.limitationMessage ?: "Selected folder is restricted by Android Scoped Storage."
            )
        }

        val treeUri = Uri.parse(treeUriStr)
        return@withContext try {
            val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            val createdUri = DocumentsContract.createDocument(
                appContext.contentResolver,
                parentDocUri,
                mimeType,
                fileName
            ) ?: return@withContext SafExportResult.Failed("Could not create destination document in selected SAF folder.")

            appContext.contentResolver.openOutputStream(createdUri, "w")?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    val buffer = ByteArray(Kar7Config.IO_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            } ?: return@withContext SafExportResult.Failed("Unable to open destination file stream.")

            SafExportResult.Success("Exported $fileName to ${status.displayName ?: "selected SAF folder"}")
        } catch (ex: SecurityException) {
            SafExportResult.Restricted(
                "Android Scoped Storage blocked writing to this folder. Choose a non-restricted folder or use FileProvider Sync Handoff."
            )
        } catch (ex: Exception) {
            SafExportResult.Failed("SAF export failed: ${ex.message ?: "Unknown I/O error"}")
        }
    }

    suspend fun exportVerifiedFileToDocumentUri(
        sourceFile: File,
        destinationUri: Uri
    ): SafExportResult = withContext(Dispatchers.IO) {
        return@withContext try {
            appContext.contentResolver.openOutputStream(destinationUri, "w")?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    val buffer = ByteArray(Kar7Config.IO_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            } ?: return@withContext SafExportResult.Failed("Unable to open chosen document output stream.")
            SafExportResult.Success("Exported ${sourceFile.name} via Android SAF.")
        } catch (ex: Exception) {
            SafExportResult.Failed("Export failed: ${ex.message ?: "Unknown error"}")
        }
    }

    fun isScopedStorageRestrictedDocId(docId: String): Boolean {
        val normalized = docId.lowercase()
        return normalized.contains("android/data") ||
            normalized.contains("android/obb") ||
            normalized.endsWith("android/data") ||
            normalized.endsWith("android/obb")
    }

    private fun formatDocIdForDisplay(docId: String, uri: Uri): String {
        if (docId.isBlank()) return uri.lastPathSegment ?: "Selected Folder"
        return docId.substringAfter(':', docId)
    }
}
