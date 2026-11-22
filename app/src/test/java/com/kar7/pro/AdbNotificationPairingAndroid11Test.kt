package com.kar7.pro

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import com.kar7.pro.device.AdbPairingNotificationManager
import com.kar7.pro.device.AdbPairingReceiver
import com.kar7.pro.device.AdbPairingStatus
import com.kar7.pro.models.AppInitData
import com.kar7.pro.models.TargetGameInfo
import com.kar7.pro.ui.Kar7ViewModel
import com.kar7.pro.ui.UiOneTimeEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30]) // Android 11 verification
class AdbNotificationPairingAndroid11Test {

    @Test
    fun `Android 11 skips runtime POST_NOTIFICATIONS, posts RemoteInput notification honestly, and launches OPEN independently`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        // Android 11 must NOT require runtime POST_NOTIFICATIONS request
        assertFalse(AdbPairingNotificationManager.needsRuntimeNotificationRequest(app))

        // Install an admin-configured target app with a launcher activity in Robolectric SDK 30
        val targetPkg = "com.dts.freefireth"
        val shadowPm = shadowOf(app.packageManager)
        shadowPm.installPackage(
            PackageInfo().apply {
                packageName = targetPkg
                versionName = "1.0.0"
                applicationInfo = ApplicationInfo().apply {
                    packageName = targetPkg
                    enabled = true
                }
            }
        )
        shadowPm.addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(targetPkg)
            },
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = targetPkg
                    name = "$targetPkg.MainActivity"
                }
            }
        )

        // Start with Developer Options disabled -> clicking ADB CONNECT must show exact message
        Settings.Global.putInt(app.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        Settings.Global.putInt(app.contentResolver, "adb_wifi_enabled", 0)

        val vm = Kar7ViewModel(app)
        val emittedEvents = mutableListOf<UiOneTimeEvent>()
        val eventCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { emittedEvents.add(it) }
        }

        // Verify OPEN works on Android 11 independently of downloads/ADB once package is configured
        assertEquals(
            targetPkg,
            vm.gameImportManager.resolveTargetLaunch(targetPkg).let {
                (it as? com.kar7.pro.gamedata.TargetLaunchResult.Ready)?.packageName
            }
        )

        vm.onAdbConnectClicked()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("PLEASE ENABLE DEVELOPER OPTIONS FIRST", vm.uiState.value.dashboardFeedbackMessage)
        assertTrue(emittedEvents.none { it is UiOneTimeEvent.RequestNotificationPermission })

        // Enable Developer Options and Wireless Debugging in test Settings.Global
        Settings.Global.putInt(app.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
        Settings.Global.putInt(app.contentResolver, "adb_wifi_enabled", 1)

        // Click ADB CONNECT -> posts RemoteInput notification & opens Developer/Wireless settings (no runtime POST_NOTIFICATIONS)
        vm.onAdbConnectClicked()
        delay(50L)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(emittedEvents.none { it is UiOneTimeEvent.RequestNotificationPermission })
        assertTrue(emittedEvents.any { it is UiOneTimeEvent.LaunchDeveloperOptions })

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val activeNotif = shadowOf(nm).allNotifications.firstOrNull()
        assertNotNull(activeNotif)
        assertEquals(
            "KAR7 PRO — ADB PAIRING",
            activeNotif!!.extras.getString(NotificationCompat.EXTRA_TITLE)
        )
        assertEquals(
            "Enter your 6-digit Wireless Debugging pairing code.",
            activeNotif.extras.getString(NotificationCompat.EXTRA_TEXT)
        )
        val submitAction = activeNotif.actions?.firstOrNull { it.title == "Submit" }
        assertNotNull(submitAction)
        assertTrue(submitAction!!.remoteInputs?.any { it.resultKey == AdbPairingNotificationManager.KEY_PAIRING_CODE } == true)

        // Simulate receiving RemoteInput reply via BroadcastReceiver with explicit ip:port
        val receiver = AdbPairingReceiver()
        val replyIntent = Intent(app, AdbPairingReceiver::class.java).apply {
            action = AdbPairingNotificationManager.ACTION_SUBMIT_PAIRING_CODE
        }
        val bundle = Bundle().apply {
            putCharSequence(AdbPairingNotificationManager.KEY_PAIRING_CODE, "482910@192.168.1.22:41234")
        }
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(AdbPairingNotificationManager.KEY_PAIRING_CODE).build()),
            replyIntent,
            bundle
        )
        receiver.onReceive(app, replyIntent)
        delay(200L)
        shadowOf(Looper.getMainLooper()).idle()

        // Production transport never fakes ADB pairing success; reports HostPairingRequired honestly
        assertFalse(vm.uiState.value.adbEnvironment?.isPairingCompleted == true)
        assertTrue(vm.uiState.value.adbEnvironment?.pairingStatus is AdbPairingStatus.HostPairingRequired)

        // Also test when Wireless Debugging is OFF -> reports EndpointUnavailable clearly
        Settings.Global.putInt(app.contentResolver, "adb_wifi_enabled", 0)
        receiver.onReceive(app, replyIntent)
        delay(200L)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(vm.uiState.value.adbEnvironment?.pairingStatus is AdbPairingStatus.EndpointUnavailable)

        eventCollector.cancel()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    @Config(sdk = [33])
    fun `Android 13 requests runtime POST_NOTIFICATIONS when not yet granted`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(AdbPairingNotificationManager.needsRuntimeNotificationRequest(app))
    }
}
