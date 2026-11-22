package com.kar7.pro

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.kar7.pro.device.AdbConnectManager
import com.kar7.pro.device.AdbPairingStatus
import com.kar7.pro.gamedata.GameImportManager
import com.kar7.pro.gamedata.TargetLaunchResult
import com.kar7.pro.models.GameDataFile
import com.kar7.pro.models.TargetGameInfo
import com.kar7.pro.security.SecureSessionStore
import com.kar7.pro.security.TestSoftwareCipherEngine
import com.kar7.pro.storage.PermissionsManager
import com.kar7.pro.storage.SafStorageManager
import com.kar7.pro.ui.DataVaultState
import com.kar7.pro.ui.screens.DashboardScreen
import com.kar7.pro.ui.theme.Kar7ProTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashboardRedesignRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `dashboard shows permissions, countdown, download-saf export, independent OPEN, ADB CONNECT, and no INJECT`() {
        val sampleTarget = TargetGameInfo(
            appName = "OPEN GAME",
            packageName = "com.example.installedgame",
            status = "supported",
            dataFiles = listOf(
                GameDataFile(
                    id = "f1",
                    fileName = "data.zip",
                    downloadUrl = "https://cdn.example.com/data.zip",
                    fileSize = 512,
                    version = "1.0",
                    sha256 = "a".repeat(64)
                )
            )
        )

        composeRule.setContent {
            Kar7ProTheme {
                DashboardScreen(
                    targetInfo = sampleTarget,
                    targetDiagnostics = null,
                    isRefreshingTarget = false,
                    remainingSeconds = 3661L,
                    isKeyExpired = false,
                    dataVaultState = DataVaultState(
                        statusHeadline = "DOWNLOAD & VERIFY (0/1)",
                        detailMessage = "Download Base44 files and verify SHA-256 integrity before exporting to your selected SAF folder.",
                        verifiedCount = 0,
                        totalFilesCount = 1,
                        canDownloadData = true,
                        canExportToSaf = false
                    ),
                    permissionsSummary = null,
                    adbEnvironment = null,
                    feedbackMessage = null,
                    feedbackIsError = false,
                    onRefreshTargetClick = {},
                    onPermissionsClick = {},
                    onDownloadDataClick = {},
                    onCancelDownloadClick = {},
                    onExportToSafClick = {},
                    onOpenTargetClick = {},
                    onAdbConnectClick = {},
                    onSwitchKeyClick = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dashboard_permissions_button").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_countdown_text").assertIsDisplayed()
        composeRule.onNodeWithTag("download_verify_data_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("export_saf_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_open_button").performScrollTo().assertIsDisplayed()
        // OPEN must be enabled independently even when 0/1 files downloaded and no ADB connected
        composeRule.onNodeWithTag("dashboard_open_button").assertIsEnabled()
        composeRule.onNodeWithTag("dashboard_adb_connect_button").performScrollTo().assertIsDisplayed()

        // Ensure INJECT and legacy cards are absent
        composeRule.onNodeWithTag("dashboard_inject_button").assertDoesNotExist()
        composeRule.onNodeWithText("INJECT").assertDoesNotExist()
        composeRule.onNodeWithText("My Account").assertDoesNotExist()
        composeRule.onNodeWithText("Key Information").assertDoesNotExist()
        composeRule.onNodeWithText("Game Data Manager").assertDoesNotExist()
    }

    @Test
    fun `expired key renders KEY EXPIRED state while OPEN remains independent`() {
        composeRule.setContent {
            Kar7ProTheme {
                DashboardScreen(
                    targetInfo = TargetGameInfo(packageName = "com.dts.freefireth"),
                    targetDiagnostics = null,
                    isRefreshingTarget = false,
                    remainingSeconds = 0L,
                    isKeyExpired = true,
                    dataVaultState = DataVaultState(
                        statusHeadline = "KEY EXPIRED",
                        detailMessage = "KEY EXPIRED — Authenticate with an active key to download or export files."
                    ),
                    permissionsSummary = null,
                    adbEnvironment = null,
                    feedbackMessage = "KEY EXPIRED",
                    feedbackIsError = true,
                    onRefreshTargetClick = {},
                    onPermissionsClick = {},
                    onDownloadDataClick = {},
                    onCancelDownloadClick = {},
                    onExportToSafClick = {},
                    onOpenTargetClick = {},
                    onAdbConnectClick = {},
                    onSwitchKeyClick = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("expired_relogin_button").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_open_button").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `GameImportManager launches installed package via getLaunchIntentForPackage and fallback launcher query`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = shadowOf(app.packageManager)

        val pkg = "com.dts.freefireth"
        val pkgInfo = PackageInfo().apply {
            packageName = pkg
            versionName = "1.105.1"
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
                enabled = true
            }
        }
        shadowPm.installPackage(pkgInfo)

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(pkg)
        }
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.MainActivity"
            }
        }
        shadowPm.addResolveInfoForIntent(launcherIntent, resolveInfo)

        val manager = GameImportManager(app)
        val diag = manager.inspectTargetGame(pkg)
        assertTrue(diag.isInstalledOrVisible)
        assertTrue(diag.isLaunchable)

        val launchResult = manager.resolveTargetLaunch(pkg)
        assertTrue(launchResult is TargetLaunchResult.Ready)
        assertEquals(pkg, (launchResult as TargetLaunchResult.Ready).packageName)

        // Installed package WITHOUT launcher activity must report NotLaunchable, not NotInstalled
        val serviceOnlyPkg = "com.example.nolauncher"
        shadowPm.installPackage(
            PackageInfo().apply {
                packageName = serviceOnlyPkg
                applicationInfo = ApplicationInfo().apply {
                    packageName = serviceOnlyPkg
                    enabled = true
                }
            }
        )
        val noLauncherRes = manager.resolveTargetLaunch(serviceOnlyPkg)
        assertTrue(noLauncherRes is TargetLaunchResult.NotLaunchable)
    }

    @Test
    fun `AdbConnectManager enforces Developer Options and reports honest endpoint status without fake success`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val adbManager = AdbConnectManager(context)

        // Developer Options disabled -> PLEASE ENABLE DEVELOPER OPTIONS FIRST
        Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        val disabledEnv = adbManager.processPairingCode("123456")
        assertFalse(disabledEnv.developerOptionsEnabled)
        assertTrue(disabledEnv.pairingStatus is AdbPairingStatus.DeveloperOptionsDisabled)
        assertTrue(
            (disabledEnv.pairingStatus as AdbPairingStatus.DeveloperOptionsDisabled).message
                .contains("PLEASE ENABLE DEVELOPER OPTIONS FIRST")
        )

        // Developer Options enabled + invalid pairing code -> ValidationError
        Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
        val badCode = adbManager.processPairingCode("12")
        assertTrue(badCode.pairingStatus is AdbPairingStatus.ValidationError)

        // Developer Options enabled + valid 6-digit code with Wireless Debugging off -> EndpointUnavailable
        Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 0)
        val unavailable = adbManager.processPairingCode("654321")
        assertFalse(unavailable.isPairingCompleted)
        assertTrue(unavailable.pairingStatus is AdbPairingStatus.EndpointUnavailable)

        // With Wireless Debugging on + explicit ip:port -> HostPairingRequired (never fake paired)
        Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 1)
        val hostRequired = adbManager.processPairingCode("654321@192.168.1.50:37123")
        assertFalse(hostRequired.isPairingCompleted)
        assertTrue(hostRequired.pairingStatus is AdbPairingStatus.HostPairingRequired)
    }

    @Test
    fun `PermissionsManager never marks permissions or target detection granted unless actually granted`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = SecureSessionStore(app, TestSoftwareCipherEngine())
        val saf = SafStorageManager(app, store)
        val permManager = PermissionsManager(app, saf)
        val importManager = GameImportManager(app)

        // Without granting INTERNET or installing target app, nothing should falsely report granted
        val deniedSummary = permManager.inspectPermissions(importManager.inspectTargetGame("com.uninstalled.app"))
        assertEquals(4, deniedSummary.items.size)
        assertFalse(deniedSummary.items.first { it.id == "network" }.isGranted)
        assertFalse(deniedSummary.items.first { it.id == "saf" }.isGranted)
        assertFalse(deniedSummary.items.first { it.id == "target_visibility" }.isGranted)
        assertTrue(deniedSummary.items.none { it.title.contains("Listener", ignoreCase = true) })

        // Grant INTERNET + ACCESS_NETWORK_STATE -> network item reports granted
        shadowOf(app).grantPermissions(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        val grantedNetSummary = permManager.inspectPermissions(importManager.inspectTargetGame("com.uninstalled.app"))
        assertTrue(grantedNetSummary.items.first { it.id == "network" }.isGranted)
        assertFalse(grantedNetSummary.items.first { it.id == "target_visibility" }.isGranted)
    }
}
