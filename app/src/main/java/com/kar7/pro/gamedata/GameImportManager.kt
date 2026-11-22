package com.kar7.pro.gamedata

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.kar7.pro.models.TargetGameInfo
import com.kar7.pro.util.VersionComparator

data class TargetPackageDiagnostics(
    val packageName: String,
    val appLabel: String? = null,
    val isInstalledOrVisible: Boolean,
    val isLaunchable: Boolean = false,
    val isEnabled: Boolean = true,
    val installedVersionName: String? = null,
    val isVersionSupported: Boolean = true,
    val detectionExplanation: String = ""
)

sealed interface TargetLaunchResult {
    data class Ready(
        val intent: Intent,
        val packageName: String
    ) : TargetLaunchResult

    data class NotConfigured(
        val message: String
    ) : TargetLaunchResult

    data class NotInstalled(
        val packageName: String,
        val message: String
    ) : TargetLaunchResult

    data class NotLaunchable(
        val packageName: String,
        val message: String
    ) : TargetLaunchResult
}

class GameImportManager(
    private val context: Context
) {
    private val appContext = context.applicationContext

    /**
     * Checks whether the configured target app is installed and launchable using multiple
     * PackageManager signals so we never assume a package is missing just because one method fails.
     */
    fun inspectTargetGame(packageName: String?, targetInfo: TargetGameInfo? = null): TargetPackageDiagnostics {
        val pkg = (packageName ?: targetInfo?.packageName)?.trim().orEmpty()
        if (pkg.isEmpty()) {
            return TargetPackageDiagnostics(
                packageName = "Not Configured",
                appLabel = targetInfo?.displayName(),
                isInstalledOrVisible = false,
                isLaunchable = false,
                isVersionSupported = false,
                detectionExplanation = "No target package name is configured by admin."
            )
        }

        val pm = appContext.packageManager
        val pkgInfo = getPackageInfoOrNull(pm, pkg)
        val appInfo = pkgInfo?.applicationInfo ?: getApplicationInfoOrNull(pm, pkg)
        val installedVersion = pkgInfo?.versionName

        val launchResolution = resolveTargetLaunch(pkg)
        val isLaunchable = launchResolution is TargetLaunchResult.Ready
        val isInstalled = isLaunchable || pkgInfo != null || appInfo != null
        val isEnabled = appInfo?.enabled ?: true
        val resolvedLabel = appInfo?.let {
            runCatching { pm.getApplicationLabel(it).toString() }.getOrNull()
        } ?: targetInfo?.displayName()

        val versionSupported = evaluateVersionSupport(targetInfo, installedVersion)

        val explanation = when {
            !isInstalled ->
                "Target app ($pkg) is not installed on this device."
            !isEnabled ->
                "Target app ($pkg) is installed, but it is currently disabled in Android Settings."
            !isLaunchable ->
                "Target app ($pkg) is installed, but no launchable activity was found."
            !versionSupported ->
                "Target app ($pkg) is installed, but version ${installedVersion ?: "unknown"} is not marked supported."
            else ->
                "Target app ($pkg) is installed and ready to open."
        }

        return TargetPackageDiagnostics(
            packageName = pkg,
            appLabel = resolvedLabel,
            isInstalledOrVisible = isInstalled,
            isLaunchable = isLaunchable,
            isEnabled = isEnabled,
            installedVersionName = installedVersion,
            isVersionSupported = versionSupported,
            detectionExplanation = explanation
        )
    }

    /**
     * Resolves a direct launcher Intent for the configured package via PackageManager:
     * 1. Calls getLaunchIntentForPackage(pkg) and checks for null.
     * 2. Does not assume the app is uninstalled if getLaunchIntentForPackage returns null —
     *    queries MAIN+LAUNCHER activities, PackageInfo, and ApplicationInfo to give an accurate result.
     */
    fun resolveTargetLaunch(packageName: String?): TargetLaunchResult {
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isEmpty()) {
            return TargetLaunchResult.NotConfigured(
                "No target package name is configured by admin. Refresh settings or contact support."
            )
        }

        val pm = appContext.packageManager
        val launchIntent = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return TargetLaunchResult.Ready(launchIntent, pkg)
        }

        // Do not assume the app is missing just because getLaunchIntentForPackage() returned null:
        // query explicit MAIN/LAUNCHER activities as a fallback.
        val launcherQueryIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(pkg)
        }
        val resolvedLauncher = queryActivities(pm, launcherQueryIntent).firstOrNull()
        if (resolvedLauncher?.activityInfo != null) {
            val activityInfo = resolvedLauncher.activityInfo
            val fallbackIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(activityInfo.packageName, activityInfo.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return TargetLaunchResult.Ready(fallbackIntent, pkg)
        }

        // Check PackageInfo / ApplicationInfo before reporting NotInstalled
        val pkgInfo = getPackageInfoOrNull(pm, pkg)
        val appInfo = pkgInfo?.applicationInfo ?: getApplicationInfoOrNull(pm, pkg)
        if (pkgInfo != null || appInfo != null) {
            return if (appInfo?.enabled == false) {
                TargetLaunchResult.NotLaunchable(
                    packageName = pkg,
                    message = "Target app ($pkg) is installed, but it is currently disabled in Android Settings."
                )
            } else {
                TargetLaunchResult.NotLaunchable(
                    packageName = pkg,
                    message = "Target app ($pkg) is installed, but it does not have a launchable home-screen activity."
                )
            }
        }

        return TargetLaunchResult.NotInstalled(
            packageName = pkg,
            message = "Target app ($pkg) is not installed on this device."
        )
    }

    fun getLaunchIntentForTarget(packageName: String?): Intent? {
        return (resolveTargetLaunch(packageName) as? TargetLaunchResult.Ready)?.intent
    }

    fun evaluateVersionSupport(targetInfo: TargetGameInfo?, installedVersion: String?): Boolean {
        if (targetInfo == null) return true
        if (targetInfo.supported == false) return false
        val statusLower = targetInfo.status?.lowercase().orEmpty()
        if (statusLower.contains("unsupported") || statusLower.contains("outdated")) return false

        if (!installedVersion.isNullOrBlank()) {
            val allowedList = targetInfo.supportedVersions?.filter { it.isNotBlank() }.orEmpty()
            if (allowedList.isNotEmpty() && allowedList.none { it.equals(installedVersion, ignoreCase = true) }) {
                return false
            }
            val minVer = targetInfo.minVersion?.takeIf { it.isNotBlank() }
            if (minVer != null && !VersionComparator.isVersionSupported(installedVersion, minVer)) {
                return false
            }
        }
        return true
    }

    private fun getPackageInfoOrNull(pm: PackageManager, pkg: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun getApplicationInfoOrNull(pm: PackageManager, pkg: String): ApplicationInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun queryActivities(pm: PackageManager, intent: Intent) =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        }.getOrDefault(emptyList())
}
