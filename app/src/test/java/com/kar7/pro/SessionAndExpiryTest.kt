package com.kar7.pro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kar7.pro.device.DeviceIdentity
import com.kar7.pro.device.DeviceInfoProvider
import com.kar7.pro.models.StoredSession
import com.kar7.pro.network.Base44ApiClient
import com.kar7.pro.repository.Kar7Repository
import com.kar7.pro.security.SecureSessionStore
import com.kar7.pro.security.TestSoftwareCipherEngine
import com.kar7.pro.session.SessionManager
import com.kar7.pro.session.SessionState
import com.kar7.pro.util.Formatters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionAndExpiryTest {

    @Test
    fun `server driven expiry countdown ticks down and transitions to Expired when server rejects`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SecureSessionStore(context, TestSoftwareCipherEngine())
        store.clearSession()

        val client = Base44ApiClient(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val req = chain.request()
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(401)
                        .message("Unauthorized")
                        .body("""{"success":false,"error":"Key expired on server","data":null}""".toResponseBody("application/json".toMediaType()))
                        .build()
                })
                .build(),
            ioDispatcher = dispatcher
        )
        val repo = Kar7Repository(
            apiClient = client,
            sessionStore = store,
            deviceInfoProvider = object : DeviceInfoProvider {
                override fun getDeviceIdentity() = DeviceIdentity("id1", "id1", "Test Phone", "1.0.0")
            }
        )

        val manager = SessionManager(repo, scope, validateIntervalMs = 60_000L)
        val initialSession = StoredSession(
            sessionToken = "tok_countdown",
            userKey = "KEY-123",
            remainingSeconds = 3L
        )
        store.saveSession(initialSession)

        manager.onAppForegrounded()
        manager.onSessionAuthenticated(initialSession)
        scope.runCurrent()

        assertTrue(manager.sessionState.value is SessionState.Active)
        assertEquals(3L, (manager.sessionState.value as SessionState.Active).remainingSeconds)

        scope.advanceTimeBy(1_000L)
        scope.runCurrent()
        assertEquals(2L, (manager.sessionState.value as SessionState.Active).remainingSeconds)

        scope.advanceTimeBy(2_000L)
        scope.runCurrent()
        assertTrue(manager.sessionState.value is SessionState.Expired)

        manager.onAppBackgrounded()
    }

    @Test
    fun `duration formatter formats days, hours, minutes, seconds cleanly`() {
        assertEquals("02m 05s", Formatters.formatDuration(125L))
        assertEquals("01h 01m 01s", Formatters.formatDuration(3661L))
        assertEquals("1d 00h 00m 05s", Formatters.formatDuration(86405L))
        assertEquals("Expired", Formatters.formatDuration(0L))
    }
}
