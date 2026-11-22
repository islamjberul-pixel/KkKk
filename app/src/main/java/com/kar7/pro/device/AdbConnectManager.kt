package com.kar7.pro.device

import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface AdbPairingStatus {
    data object Idle : AdbPairingStatus
    data object AwaitingNotificationInput : AdbPairingStatus
    data object PairingInProgress : AdbPairingStatus
    data class DeveloperOptionsDisabled(
        val message: String = "PLEASE ENABLE DEVELOPER OPTIONS FIRST"
    ) : AdbPairingStatus
    data class ValidationError(val message: String) : AdbPairingStatus
    data class EndpointUnavailable(val message: String) : AdbPairingStatus
    data class HostPairingRequired(val endpoint: String, val message: String) : AdbPairingStatus
}

data class AdbEnvironmentState(
    val developerOptionsEnabled: Boolean,
    val wirelessDebuggingEnabled: Boolean,
    val adbEnabled: Boolean,
    val wirelessDebuggingSupportedApi: Boolean,
    val pairingStatus: AdbPairingStatus = AdbPairingStatus.Idle
) {
    /**
     * Unprivileged third-party Android apps cannot perform TLS/SPAKE2+ ADB pairing or hold
     * an authenticated ADB daemon session, so pairing is never falsely reported as verified.
     */
    val isPairingCompleted: Boolean
        get() = false
}

data class ParsedPairingInput(
    val pairingCode: String,
    val hostPort: String? = null
)

data class AdbEndpointCheckResult(
    val endpoint: String? = null,
    val status: AdbPairingStatus
)

interface AdbPairingTransport {
    suspend fun checkPairingEndpoint(
        pairingCode: String,
        explicitHostPort: String?,
        wirelessDebuggingEnabled: Boolean
    ): AdbEndpointCheckResult
}

/**
 * Production Android platform transport:
 * Uses NsdManager to check for an active _adb-tls-pairing._tcp. service when Wireless Debugging
 * is enabled. Explicitly reports when Android does not expose a pairing endpoint and never fakes
 * ADB pairing success.
 */
class PlatformAdbPairingTransport(
    private val context: Context
) : AdbPairingTransport {

    override suspend fun checkPairingEndpoint(
        pairingCode: String,
        explicitHostPort: String?,
        wirelessDebuggingEnabled: Boolean
    ): AdbEndpointCheckResult = withContext(Dispatchers.IO) {
        if (!wirelessDebuggingEnabled) {
            return@withContext AdbEndpointCheckResult(
                endpoint = null,
                status = AdbPairingStatus.EndpointUnavailable(
                    "Wireless Debugging is currently OFF, so Android has not started a pairing endpoint. " +
                        "Enable Wireless Debugging in Developer Options and tap 'Pair device with pairing code'."
                )
            )
        }

        val discoveredEndpoint = explicitHostPort ?: discoverAdbPairingService()
        if (discoveredEndpoint == null) {
            return@withContext AdbEndpointCheckResult(
                endpoint = null,
                status = AdbPairingStatus.EndpointUnavailable(
                    "Android did not advertise an active Wireless Debugging pairing endpoint (_adb-tls-pairing._tcp.). " +
                        "Open Developer Options -> Wireless Debugging -> 'Pair device with pairing code' to expose an IP:port, " +
                        "or submit 'code@ip:port' in the pairing notification."
                )
            )
        }

        // Unprivileged Android apps lack MANAGE_DEBUGGING / native SPAKE2+ TLS pairing daemon.
        // Never report fake pairing success.
        AdbEndpointCheckResult(
            endpoint = discoveredEndpoint,
            status = AdbPairingStatus.HostPairingRequired(
                endpoint = discoveredEndpoint,
                message = "Pairing endpoint detected at $discoveredEndpoint, but Android restricts Wireless Debugging " +
                    "TLS/SPAKE2+ pairing to an authorized ADB host. Complete pairing from your ADB host using: " +
                    "adb pair $discoveredEndpoint $pairingCode"
            )
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun discoverAdbPairingService(): String? {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val deferred = CompletableDeferred<String?>()

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("_adb-tls-pairing")) {
                    nsdManager.resolveService(
                        serviceInfo,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                                if (!deferred.isCompleted) deferred.complete(null)
                            }

                            override fun onServiceResolved(info: NsdServiceInfo) {
                                val host = info.host?.hostAddress
                                val port = info.port
                                val endpoint = if (!host.isNullOrBlank() && port > 0) "$host:$port" else null
                                if (!deferred.isCompleted) deferred.complete(endpoint)
                            }
                        }
                    )
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (!deferred.isCompleted) deferred.complete(null)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        return try {
            nsdManager.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            withTimeoutOrNull(2_000L) { deferred.await() }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }
    }
}

class AdbConnectManager(
    private val context: Context,
    private val transport: AdbPairingTransport = PlatformAdbPairingTransport(context.applicationContext)
) {
    private val appContext = context.applicationContext

    fun inspectEnvironment(currentStatus: AdbPairingStatus? = null): AdbEnvironmentState {
        val devEnabled = isDeveloperOptionsEnabled()
        val wirelessEnabled = isWirelessDebuggingEnabled()
        val adbEnabled = isAdbEnabled()

        val effectiveStatus = when {
            !devEnabled -> AdbPairingStatus.DeveloperOptionsDisabled()
            currentStatus is AdbPairingStatus.DeveloperOptionsDisabled -> AdbPairingStatus.Idle
            currentStatus != null -> currentStatus
            else -> AdbPairingStatus.Idle
        }

        return AdbEnvironmentState(
            developerOptionsEnabled = devEnabled,
            wirelessDebuggingEnabled = wirelessEnabled,
            adbEnabled = adbEnabled,
            wirelessDebuggingSupportedApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            pairingStatus = effectiveStatus
        )
    }

    fun isDeveloperOptionsEnabled(): Boolean {
        return runCatching {
            Settings.Global.getInt(
                appContext.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        }.getOrDefault(false)
    }

    fun isWirelessDebuggingEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return runCatching {
            Settings.Global.getInt(appContext.contentResolver, "adb_wifi_enabled", 0) == 1
        }.getOrDefault(false)
    }

    fun isAdbEnabled(): Boolean {
        return runCatching {
            Settings.Global.getInt(
                appContext.contentResolver,
                Settings.Global.ADB_ENABLED,
                0
            ) == 1
        }.getOrDefault(false)
    }

    /**
     * Opens Wireless Debugging settings directly where supported, otherwise falls back
     * to Android Developer Options (highlighting Wireless Debugging where OEMs support it).
     */
    fun createBestDeveloperSettingsIntent(): Pair<Intent, Intent> {
        val pm = appContext.packageManager
        val directWirelessIntent = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val devOptionsIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
        }
        val fallbackSettings = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val primary = if (directWirelessIntent.resolveActivity(pm) != null) {
            directWirelessIntent
        } else {
            devOptionsIntent
        }
        return primary to fallbackSettings
    }

    fun parsePairingInput(rawInput: String): Result<ParsedPairingInput> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Pairing code cannot be empty. Enter the 6-digit Wireless Debugging code.")
            )
        }

        // Supports "123456" or "123456@192.168.1.5:37123" or "123456 192.168.1.5:37123"
        val tokens = trimmed.split(Regex("[@\\s]+")).filter { it.isNotBlank() }
        val code = tokens.firstOrNull().orEmpty()
        if (code.length != 6 || !code.all { it.isDigit() }) {
            return Result.failure(
                IllegalArgumentException("Invalid pairing code format. Enter exactly 6 digits (optionally '123456@ip:port').")
            )
        }

        val hostPort = tokens.getOrNull(1)
        if (hostPort != null && !isValidHostPort(hostPort)) {
            return Result.failure(
                IllegalArgumentException("Invalid IP:port format '$hostPort'. Expected format e.g. 192.168.1.10:37123.")
            )
        }

        return Result.success(ParsedPairingInput(pairingCode = code, hostPort = hostPort))
    }

    suspend fun processPairingCode(rawInput: String): AdbEnvironmentState {
        if (!isDeveloperOptionsEnabled()) {
            return inspectEnvironment(AdbPairingStatus.DeveloperOptionsDisabled())
        }

        val parsed = parsePairingInput(rawInput).getOrElse { err ->
            return inspectEnvironment(
                AdbPairingStatus.ValidationError(err.message ?: "Invalid pairing code format.")
            )
        }

        val wirelessEnabled = isWirelessDebuggingEnabled()
        val check = transport.checkPairingEndpoint(
            pairingCode = parsed.pairingCode,
            explicitHostPort = parsed.hostPort,
            wirelessDebuggingEnabled = wirelessEnabled
        )
        return inspectEnvironment(check.status)
    }

    private fun isValidHostPort(input: String): Boolean {
        val parts = input.split(":")
        if (parts.size != 2) return false
        val port = parts[1].toIntOrNull() ?: return false
        return parts[0].isNotBlank() && port in 1..65535
    }
}
