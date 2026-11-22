package com.kar7.pro.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kar7.pro.gamedata.TargetPackageDiagnostics

data class PermissionItemStatus(
    val id: String,
    val title: String,
    val reason: String,
    val statusLabel: String,
    val isGranted: Boolean
)

data class PermissionsSummary(
    val items: List<PermissionItemStatus>,
    val allRequiredReady: Boolean,
    val notificationPermissionGranted: Boolean,
    val needsRuntimeNotificationRequest: Boolean,
    val safFolderSelected: Boolean,
    val safFolderDisplay: String?
)

class PermissionsManager(
    private val context: Context,
    private val safStorageManager: SafStorageManager
) {
    private val appContext = context.applicationContext

    fun inspectPermissions(targetDiagnostics: TargetPackageDiagnostics?): PermissionsSummary {
        val hasInternet = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED

        val hasNetworkState = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_NETWORK_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val networkOnline = isNetworkAvailable()
        val internetGranted = hasInternet && hasNetworkState

        val isAndroid13Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val runtimePostNotificationsGranted = if (isAndroid13Plus) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val systemNotificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        val notificationsGranted = runtimePostNotificationsGranted && systemNotificationsEnabled

        val safStatus = safStorageManager.getCurrentSafFolderStatus()
        val safGranted = safStatus.hasWritePermission && !safStatus.isRestrictedByScopedStorage

        val targetPkg = targetDiagnostics?.packageName?.takeIf {
            it.isNotBlank() && it != "Not Configured"
        }
        val targetDetectedAndLaunchable = targetDiagnostics != null &&
            targetDiagnostics.isInstalledOrVisible &&
            targetDiagnostics.isLaunchable &&
            targetDiagnostics.isEnabled

        val items = buildList {
            add(
                PermissionItemStatus(
                    id = "network",
                    title = "Internet Access (INTERNET)",
                    reason = "Required to reach the Base44 backend, validate key expiration, and download SHA-256 verified files over HTTPS.",
                    statusLabel = when {
                        internetGranted && networkOnline -> "Granted & Online"
                        internetGranted -> "Granted (Offline)"
                        else -> "Not Granted"
                    },
                    isGranted = internetGranted
                )
            )
            add(
                PermissionItemStatus(
                    id = "notifications",
                    title = if (isAndroid13Plus) {
                        "Notifications (POST_NOTIFICATIONS)"
                    } else {
                        "Notifications (Android 11/12 App Settings)"
                    },
                    reason = "Used only to display KAR7 PRO's own persistent ADB pairing prompt. Never reads other applications' notifications.",
                    statusLabel = when {
                        notificationsGranted -> "Granted & Enabled"
                        isAndroid13Plus && !runtimePostNotificationsGranted -> "Denied (Runtime Permission Required)"
                        else -> "Disabled in System App Settings"
                    },
                    isGranted = notificationsGranted
                )
            )
            add(
                PermissionItemStatus(
                    id = "saf",
                    title = "SAF Folder Access",
                    reason = "User-chosen Storage Access Framework folder for exporting verified files without accessing private directories of other apps.",
                    statusLabel = when {
                        safStatus.isRestrictedByScopedStorage -> "Blocked by Scoped Storage (Restricted Folder)"
                        safGranted -> "Granted (${safStatus.displayName ?: "Selected Folder"})"
                        safStatus.uriString != null -> "Revoked / Permission Missing"
                        else -> "Not Selected"
                    },
                    isGranted = safGranted
                )
            )
            add(
                PermissionItemStatus(
                    id = "target_visibility",
                    title = "Target Application Detection",
                    reason = "Uses Android <queries> launcher visibility and PackageManager to detect ${targetPkg ?: "the admin-configured package"} without QUERY_ALL_PACKAGES.",
                    statusLabel = when {
                        targetPkg == null -> "Not Configured by Admin"
                        targetDetectedAndLaunchable -> "Installed & Launchable ($targetPkg)"
                        targetDiagnostics?.isInstalledOrVisible == true && targetDiagnostics.isEnabled == false ->
                            "Installed but Disabled ($targetPkg)"
                        targetDiagnostics?.isInstalledOrVisible == true ->
                            "Installed (No Launcher Activity: $targetPkg)"
                        else -> "Not Installed ($targetPkg)"
                    },
                    isGranted = targetDetectedAndLaunchable
                )
            )
        }

        return PermissionsSummary(
            items = items,
            allRequiredReady = internetGranted,
            notificationPermissionGranted = notificationsGranted,
            needsRuntimeNotificationRequest = isAndroid13Plus && !runtimePostNotificationsGranted,
            safFolderSelected = safGranted,
            safFolderDisplay = if (safGranted) safStatus.displayName else null
        )
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
