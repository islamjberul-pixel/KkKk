package com.kar7.pro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kar7.pro.config.Kar7Config
import com.kar7.pro.download.DownloadState
import com.kar7.pro.download.FileDownloadManager
import com.kar7.pro.download.FileIntegrityVerifier
import com.kar7.pro.download.VerifiedFileRecord
import com.kar7.pro.download.ZipValidationResult
import com.kar7.pro.gamedata.GameImportManager
import com.kar7.pro.models.GameDataFile
import com.kar7.pro.security.SecureSessionStore
import com.kar7.pro.security.TestSoftwareCipherEngine
import com.kar7.pro.storage.SafStorageManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DownloadAndStorageRobolectricTest {

    private fun buildZipBytes(entryName: String, content: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(content)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `SHA-256 and ZIP validation detect safe archives and block path traversal`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val safeZip = File(context.cacheDir, "safe.zip")
        val safeBytes = buildZipBytes("assets/config.json", """{"ok":true}""".toByteArray())
        safeZip.writeBytes(safeBytes)

        assertTrue(FileIntegrityVerifier.verifySha256(safeZip, sha256Hex(safeBytes)))
        assertFalse(FileIntegrityVerifier.verifySha256(safeZip, "0".repeat(64)))
        assertTrue(FileIntegrityVerifier.validateZipSafety(safeZip) is ZipValidationResult.Valid)

        val evilZip = File(context.cacheDir, "evil.zip")
        evilZip.writeBytes(buildZipBytes("../../etc/passwd", "hack".toByteArray()))
        val evilResult = FileIntegrityVerifier.validateZipSafety(evilZip)
        assertTrue(evilResult is ZipValidationResult.Invalid)
        assertTrue((evilResult as ZipValidationResult.Invalid).reason.contains("path traversal", ignoreCase = true))

        safeZip.delete()
        evilZip.delete()
    }

    @Test
    fun `download manager tracks progress, verifies SHA-256, and rejects mismatched or insecure files`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val payload = buildZipBytes("data/bundle.bin", ByteArray(4096) { 0x42 })
        val validSha = sha256Hex(payload)

        val mockHttp = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload.toResponseBody("application/zip".toMediaType()))
                    .build()
            })
            .build()

        val verifiedDir = File(context.filesDir, Kar7Config.VERIFIED_DATA_SUBDIR).apply { mkdirs() }
        org.robolectric.shadows.ShadowStatFs.registerStats(verifiedDir, 1024, 512, 512)

        val manager = FileDownloadManager(context, scope, mockHttp)
        manager.clearAllDownloads()

        // 1. Valid HTTPS download with matching size and SHA-256
        val goodFile = GameDataFile(
            id = "good_1",
            fileName = "bundle_v1.zip",
            downloadUrl = "https://cdn.example.com/bundle_v1.zip",
            fileSize = payload.size.toLong(),
            version = "1.0",
            sha256 = validSha
        )
        manager.startDownload(goodFile)
        var finalState: DownloadState? = null
        repeat(40) {
            finalState = manager.downloadStates.value["good_1"]
            if (finalState is DownloadState.Verified || finalState is DownloadState.Failed) return@repeat
            delay(50L)
        }
        assertTrue("Expected Verified state but got $finalState", finalState is DownloadState.Verified)

        // 2. SHA-256 mismatch rejected
        val badShaFile = goodFile.copy(id = "bad_sha", sha256 = "f".repeat(64))
        manager.startDownload(badShaFile)
        var shaFailState: DownloadState? = null
        repeat(40) {
            shaFailState = manager.downloadStates.value["bad_sha"]
            if (shaFailState is DownloadState.Failed) return@repeat
            delay(50L)
        }
        assertTrue(shaFailState is DownloadState.Failed)
        assertTrue((shaFailState as DownloadState.Failed).errorMessage.contains("SHA-256"))

        // 3. Insecure HTTP URL blocked
        val insecureFile = goodFile.copy(id = "http_blocked", downloadUrl = "http://cdn.example.com/file.zip")
        manager.startDownload(insecureFile)
        var httpFailState: DownloadState? = null
        repeat(20) {
            httpFailState = manager.downloadStates.value["http_blocked"]
            if (httpFailState is DownloadState.Failed) return@repeat
            delay(30L)
        }
        assertTrue(httpFailState is DownloadState.Failed)
        assertTrue((httpFailState as DownloadState.Failed).errorMessage.contains("HTTPS"))

        // 4. Insufficient storage rejected
        org.robolectric.shadows.ShadowStatFs.registerStats(verifiedDir, 1024, 0, 0)
        val noSpaceFile = goodFile.copy(id = "no_space")
        manager.startDownload(noSpaceFile)
        var spaceFailState: DownloadState? = null
        repeat(20) {
            spaceFailState = manager.downloadStates.value["no_space"]
            if (spaceFailState is DownloadState.Failed) return@repeat
            delay(30L)
        }
        assertTrue(spaceFailState is DownloadState.Failed)
        assertTrue((spaceFailState as DownloadState.Failed).errorMessage.contains("Insufficient device storage"))
    }

    @Test
    fun `SAF storage detects Android data restrictions and GameImportManager resolves target launch states accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SecureSessionStore(context, TestSoftwareCipherEngine())
        val safManager = SafStorageManager(context, store)

        assertTrue(safManager.isScopedStorageRestrictedDocId("primary:Android/data/com.example.targetapp"))
        assertTrue(safManager.isScopedStorageRestrictedDocId("primary:Android/obb/com.example.targetapp"))
        assertFalse(safManager.isScopedStorageRestrictedDocId("primary:Documents/KAR7_Exports"))

        val importManager = GameImportManager(context)
        val diag = importManager.inspectTargetGame("com.uninstalled.targetgame")
        assertFalse(diag.isInstalledOrVisible)
        assertFalse(diag.isLaunchable)

        val missingLaunch = importManager.resolveTargetLaunch("com.uninstalled.targetgame")
        assertTrue(missingLaunch is com.kar7.pro.gamedata.TargetLaunchResult.NotInstalled)
        assertTrue(
            (missingLaunch as com.kar7.pro.gamedata.TargetLaunchResult.NotInstalled).message
                .contains("not installed", ignoreCase = true)
        )

        val emptyLaunch = importManager.resolveTargetLaunch("")
        assertTrue(emptyLaunch is com.kar7.pro.gamedata.TargetLaunchResult.NotConfigured)
    }
}
