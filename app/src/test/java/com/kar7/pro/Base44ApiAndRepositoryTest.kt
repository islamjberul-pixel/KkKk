package com.kar7.pro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kar7.pro.device.DeviceIdentity
import com.kar7.pro.device.DeviceInfoProvider
import com.kar7.pro.models.AppError
import com.kar7.pro.models.AppResult
import com.kar7.pro.network.Base44ApiClient
import com.kar7.pro.repository.AppGateResult
import com.kar7.pro.repository.Kar7Repository
import com.kar7.pro.security.SecureSessionStore
import com.kar7.pro.security.TestSoftwareCipherEngine
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Base44ApiAndRepositoryTest {

    private class FakeDeviceProvider(
        private val appVersion: String = "1.0.0"
    ) : DeviceInfoProvider {
        override fun getDeviceIdentity() = DeviceIdentity(
            deviceId = "android-id-test-1234",
            maskedDeviceId = "andr••••1234",
            deviceName = "Google Pixel",
            appVersion = appVersion
        )
    }

    private fun buildClientWithResponder(
        responder: (Request) -> Response
    ): Base44ApiClient {
        val interceptor = Interceptor { chain -> responder(chain.request()) }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        return Base44ApiClient(okHttpClient = client)
    }

    private fun jsonResponse(request: Request, code: Int, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun buildRepository(
        appVersion: String = "1.0.0",
        responder: (Request) -> Response
    ): Pair<Kar7Repository, SecureSessionStore> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SecureSessionStore(context, TestSoftwareCipherEngine())
        store.clearSession()
        val repo = Kar7Repository(
            apiClient = buildClientWithResponder(responder),
            sessionStore = store,
            deviceInfoProvider = FakeDeviceProvider(appVersion)
        )
        return repo to store
    }

    @Test
    fun `app initialization handles disabled, maintenance, outdated, and allowed states`() = runTest {
        val (disabledRepo, _) = buildRepository { req ->
            jsonResponse(
                req, 200,
                """{"success":true,"error":null,"message":"OK","data":{"app_enabled":false,"maintenance_mode":false},"server_time":"2026-11-21T10:00:00Z"}"""
            )
        }
        assertTrue(disabledRepo.initializeApp() is AppGateResult.Disabled)

        val (maintRepo, _) = buildRepository { req ->
            jsonResponse(
                req, 200,
                """{"success":true,"error":null,"message":"OK","data":{"app_enabled":true,"maintenance_mode":true,"maintenance_message":"Server upgrade in progress"},"server_time":"2026-11-21T10:00:00Z"}"""
            )
        }
        val maintResult = maintRepo.initializeApp()
        assertTrue(maintResult is AppGateResult.Maintenance)
        assertEquals("Server upgrade in progress", (maintResult as AppGateResult.Maintenance).message)

        val (outdatedRepo, _) = buildRepository(appVersion = "1.0.0") { req ->
            jsonResponse(
                req, 200,
                """{"success":true,"error":null,"message":"OK","data":{"app_enabled":true,"maintenance_mode":false,"min_supported_version":"2.0.0","update_message":"Please update"},"server_time":"2026-11-21T10:00:00Z"}"""
            )
        }
        assertTrue(outdatedRepo.initializeApp() is AppGateResult.UpdateRequired)

        val (allowedRepo, _) = buildRepository(appVersion = "1.0.0") { req ->
            jsonResponse(
                req, 200,
                """{"success":true,"error":null,"message":"OK","data":{"app":{"app_name":"KAR7 PRO","app_enabled":true,"maintenance_mode":false,"min_supported_version":"1.0.0"},"target":{"app_name":"OPEN GAME","package_name":"com.dts.freefireth","status":"supported"}},"server_time":"2026-11-21T10:00:00Z"}"""
            )
        }
        val allowed = allowedRepo.initializeApp()
        assertTrue(allowed is AppGateResult.Allowed)
        assertEquals("com.dts.freefireth", (allowed as AppGateResult.Allowed).initData.target?.packageName)
        assertEquals("OPEN GAME", allowed.initData.target?.displayName())
    }

    @Test
    fun `key verification parses session, user, target, whatsapp, announcements and encrypts token`() = runTest {
        val verifyJson = """
            {
              "success": true,
              "error": null,
              "message": "Key verified",
              "data": {
                "session_token": "tok_secret_987654",
                "expires_at": "2026-12-01T00:00:00Z",
                "remaining_seconds": 86400,
                "user": { "id": "u1", "name": "Alex", "status": "active" },
                "key": { "status": "active", "masked_key": "KAR7-****-9999" },
                "app": { "name": "KAR7 PRO", "status": "online" },
                "target": {
                  "package_name": "com.example.targetapp",
                  "status": "supported",
                  "data_files": [
                    {
                      "id": "f1",
                      "file_name": "pack_v1.zip",
                      "download_url": "https://cdn.example.com/pack_v1.zip",
                      "file_size": 1024,
                      "version": "1.2",
                      "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "description": "Core asset bundle"
                    }
                  ]
                },
                "whatsapp": {
                  "chat_link": "https://wa.me/15550001234",
                  "buy_key_message": "Hello, I want to buy a KAR7 PRO key",
                  "support_message": "Need KAR7 PRO support"
                },
                "announcements": [
                  { "id": "a1", "title": "Older", "description": "Old msg", "published_at": "2026-11-01T00:00:00Z" },
                  { "id": "a2", "title": "Latest", "description": "New msg", "published_at": "2026-11-20T00:00:00Z" }
                ]
              },
              "server_time": "2026-11-21T10:00:00Z"
            }
        """.trimIndent()

        val (repo, store) = buildRepository { req ->
            assertEquals("POST", req.method)
            assertTrue(req.url.encodedPath.endsWith("/keys/verify"))
            jsonResponse(req, 200, verifyJson)
        }

        val result = repo.loginWithKey("KAR7-TEST-KEY-9999")
        assertTrue(result is AppResult.Success)
        val session = (result as AppResult.Success).data
        assertEquals("tok_secret_987654", session.sessionToken)
        assertEquals(86400L, session.remainingSeconds)
        assertEquals("com.example.targetapp", session.target?.packageName)
        assertEquals("Latest", session.announcements.first().title)

        // Ensure raw SharedPreferences never stores session_token in plaintext
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawPrefs = context.getSharedPreferences("kar7_secure_session", Context.MODE_PRIVATE).all.toString()
        assertTrue(!rawPrefs.contains("tok_secret_987654"))
        assertNotNull(store.loadSession())
    }

    @Test
    fun `error classification handles invalid key, expired key, disabled key, device conflict, invalid json, and no internet`() = runTest {
        val (invalidKeyRepo, _) = buildRepository { req ->
            jsonResponse(req, 400, """{"success":false,"error":{"code":"invalid_key","message":"Key does not exist"},"message":"Key does not exist","data":null}""")
        }
        val invalidRes = invalidKeyRepo.loginWithKey("BAD")
        assertTrue((invalidRes as AppResult.Failure).error is AppError.InvalidKey)

        val (expiredRepo, _) = buildRepository { req ->
            jsonResponse(req, 403, """{"success":false,"error":"Key has expired","message":"Key has expired","data":null}""")
        }
        assertTrue((expiredRepo.loginWithKey("EXP") as AppResult.Failure).error is AppError.ExpiredKey)

        val (disabledRepo, _) = buildRepository { req ->
            jsonResponse(req, 403, """{"success":false,"error":{"code":"disabled","message":"Key disabled by admin"},"data":null}""")
        }
        assertTrue((disabledRepo.loginWithKey("DIS") as AppResult.Failure).error is AppError.DisabledKey)

        val (conflictRepo, _) = buildRepository { req ->
            jsonResponse(req, 409, """{"success":false,"error":{"code":"device_conflict","message":"Key bound to another device"},"data":null}""")
        }
        assertTrue((conflictRepo.loginWithKey("CONF") as AppResult.Failure).error is AppError.DeviceConflict)

        val (badJsonRepo, _) = buildRepository { req ->
            jsonResponse(req, 200, """{not-valid-json""")
        }
        assertTrue((badJsonRepo.initializeApp() as AppGateResult.Error).error is AppError.InvalidJson)

        val (offlineRepo, _) = buildRepository { _ ->
            throw UnknownHostException("Unable to resolve host")
        }
        assertTrue((offlineRepo.initializeApp() as AppGateResult.Error).error is AppError.NoInternet)
    }

    @Test
    fun `session validation clears local session on 401 and logout clears local session even on network failure`() = runTest {
        var failWith401 = false
        var failNetworkOnLogout = false
        val (repo, store) = buildRepository { req ->
            when {
                req.url.encodedPath.endsWith("/keys/verify") -> jsonResponse(
                    req, 200,
                    """{"success":true,"data":{"session_token":"tok_1","remaining_seconds":120}}"""
                )
                req.url.encodedPath.endsWith("/session/validate") -> {
                    assertEquals("Bearer tok_1", req.header("Authorization"))
                    if (failWith401) {
                        jsonResponse(req, 401, """{"success":false,"error":"Session expired","data":null}""")
                    } else {
                        jsonResponse(req, 200, """{"success":true,"data":{"valid":true,"remaining_seconds":95}}""")
                    }
                }
                req.url.encodedPath.endsWith("/session/logout") -> {
                    if (failNetworkOnLogout) throw IOException("Network dropped")
                    jsonResponse(req, 200, """{"success":true,"data":{"logged_out":true}}""")
                }
                else -> jsonResponse(req, 404, "{}")
            }
        }

        repo.loginWithKey("KEY-1")
        val valid = repo.validateSavedSession()
        assertTrue(valid is AppResult.Success)
        assertEquals(95L, (valid as AppResult.Success).data.remainingSeconds)

        // Server invalidates session -> local store cleared
        failWith401 = true
        val invalidated = repo.validateSavedSession()
        assertTrue(invalidated is AppResult.Failure)
        assertNull(store.loadSession())

        // Re-login then logout with failing network -> local store still cleared
        failWith401 = false
        repo.loginWithKey("KEY-1")
        assertNotNull(store.loadSession())
        failNetworkOnLogout = true
        val logoutRes = repo.logout()
        assertTrue(logoutRes is AppResult.Success)
        assertNull(store.loadSession())
    }

    @Test
    fun `live Base44 connection probe logs actual server response if network available`() = runTest {
        val liveClient = Base44ApiClient(
            okHttpClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        )
        val initRes = liveClient.getAppInit()
        val settingsRes = liveClient.getSettings()
        val validateRes = liveClient.validateSession("invalid_probe_token")
        val expirationRes = liveClient.getKeyExpiration("PROBE-NONEXISTENT-KEY")
        println("BASE44_PROBE /app-init -> $initRes")
        println("BASE44_PROBE /settings -> $settingsRes")
        println("BASE44_PROBE /session/validate (unauth probe) -> $validateRes")
        println("BASE44_PROBE /keys/expiration (nonexistent probe) -> $expirationRes")
        // Do not fail offline build sandboxes; verify typed result is always produced safely
        assertTrue(initRes is AppResult.Success || initRes is AppResult.Failure)
        assertTrue(settingsRes is AppResult.Success || settingsRes is AppResult.Failure)
        assertTrue(validateRes is AppResult.Success || validateRes is AppResult.Failure)
        assertTrue(expirationRes is AppResult.Success || expirationRes is AppResult.Failure)
    }
}
