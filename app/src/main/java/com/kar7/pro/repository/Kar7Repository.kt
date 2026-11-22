package com.kar7.pro.repository

import com.kar7.pro.device.DeviceIdentity
import com.kar7.pro.device.DeviceInfoProvider
import com.kar7.pro.models.Announcement
import com.kar7.pro.models.AppError
import com.kar7.pro.models.AppInitData
import com.kar7.pro.models.AppResult
import com.kar7.pro.models.KeyExpirationData
import com.kar7.pro.models.KeyVerifyRequest
import com.kar7.pro.models.SessionValidateData
import com.kar7.pro.models.SettingsData
import com.kar7.pro.models.StoredSession
import com.kar7.pro.models.TargetGameInfo
import com.kar7.pro.models.WhatsappInfo
import com.kar7.pro.network.Base44ApiClient
import com.kar7.pro.security.SecureSessionStore
import com.kar7.pro.util.VersionComparator

sealed interface AppGateResult {
    data class Allowed(
        val initData: AppInitData,
        val serverTime: String?
    ) : AppGateResult

    data object Disabled : AppGateResult
    data class Maintenance(val message: String) : AppGateResult
    data class UpdateRequired(val message: String, val minVersion: String) : AppGateResult
    data class Error(val error: AppError) : AppGateResult
}

class Kar7Repository(
    private val apiClient: Base44ApiClient,
    private val sessionStore: SecureSessionStore,
    private val deviceInfoProvider: DeviceInfoProvider
) {
    fun getDeviceIdentity(): DeviceIdentity = deviceInfoProvider.getDeviceIdentity()

    fun loadStoredSession(): StoredSession? = sessionStore.loadSession()

    fun clearLocalSession() = sessionStore.clearSession()

    fun loadSafTreeUri(): String? = sessionStore.loadSafTreeUri()

    fun saveSafTreeUri(uriString: String?) = sessionStore.saveSafTreeUri(uriString)

    suspend fun initializeApp(): AppGateResult {
        val device = deviceInfoProvider.getDeviceIdentity()
        return when (val result = apiClient.getAppInit()) {
            is AppResult.Failure -> AppGateResult.Error(result.error)
            is AppResult.Success -> {
                val data = result.data
                evaluateGate(data, device.appVersion, result.serverTime)
            }
        }
    }

    fun evaluateGate(
        data: AppInitData,
        installedVersion: String,
        serverTime: String?
    ): AppGateResult {
        val enabled = data.app?.appEnabled ?: data.appEnabled
        val maintenance = data.app?.maintenanceMode ?: data.maintenanceMode
        val maintenanceMsg = data.app?.maintenanceMessage?.takeIf { it.isNotBlank() }
            ?: data.maintenanceMessage?.takeIf { it.isNotBlank() }
        val minSupported = data.app?.minSupportedVersion?.takeIf { it.isNotBlank() }
            ?: data.minSupportedVersion
        val updateMsg = data.app?.updateMessage?.takeIf { it.isNotBlank() }
            ?: data.updateMessage?.takeIf { it.isNotBlank() }

        if (!enabled) {
            return AppGateResult.Disabled
        }
        if (maintenance) {
            val msg = maintenanceMsg
                ?: "KAR7 PRO is currently undergoing scheduled maintenance. Please try again later."
            return AppGateResult.Maintenance(msg)
        }
        if (!VersionComparator.isVersionSupported(installedVersion, minSupported)) {
            val msg = updateMsg
                ?: "A newer version of KAR7 PRO ($minSupported) is required to continue."
            return AppGateResult.UpdateRequired(
                message = msg,
                minVersion = minSupported.orEmpty()
            )
        }
        return AppGateResult.Allowed(initData = data, serverTime = serverTime)
    }

    suspend fun fetchSettings(): AppResult<SettingsData> = apiClient.getSettings()

    suspend fun loginWithKey(rawKey: String): AppResult<StoredSession> {
        val cleanKey = rawKey.trim()
        if (cleanKey.isEmpty()) {
            return AppResult.Failure(AppError.InvalidKey("Please enter your access key."))
        }
        val device = deviceInfoProvider.getDeviceIdentity()
        val request = KeyVerifyRequest(
            key = cleanKey,
            deviceId = device.deviceId,
            deviceName = device.deviceName,
            appVersion = device.appVersion
        )

        return when (val result = apiClient.verifyKey(request)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val data = result.data
                val token = data.sessionToken?.takeIf { it.isNotBlank() }
                    ?: return AppResult.Failure(
                        AppError.InvalidJson("Server did not return a valid session token.")
                    )

                val session = StoredSession(
                    sessionToken = token,
                    userKey = cleanKey,
                    expiresAt = data.expiresAt ?: data.key?.expiresAt,
                    remainingSeconds = data.remainingSeconds ?: data.key?.remainingSeconds,
                    user = data.user,
                    key = data.key,
                    app = data.app,
                    target = data.target,
                    whatsapp = data.whatsapp,
                    announcements = sortAnnouncements(data.announcements.orEmpty()),
                    device = data.device
                )
                sessionStore.saveSession(session)
                AppResult.Success(session, result.message, result.serverTime)
            }
        }
    }

    suspend fun validateSavedSession(): AppResult<StoredSession> {
        val existing = sessionStore.loadSession()
            ?: return AppResult.Failure(AppError.InvalidSession("No active session found."))

        return when (val result = apiClient.validateSession(existing.sessionToken)) {
            is AppResult.Failure -> {
                if (result.error is AppError.InvalidSession ||
                    result.error is AppError.ExpiredKey ||
                    result.error is AppError.DisabledKey
                ) {
                    sessionStore.clearSession()
                }
                result
            }
            is AppResult.Success -> {
                val data: SessionValidateData = result.data
                if (data.valid == false) {
                    sessionStore.clearSession()
                    return AppResult.Failure(
                        AppError.InvalidSession(result.message ?: "Session is no longer valid.")
                    )
                }
                val updated = existing.copy(
                    expiresAt = data.expiresAt ?: data.key?.expiresAt ?: existing.expiresAt,
                    remainingSeconds = data.remainingSeconds ?: data.key?.remainingSeconds ?: existing.remainingSeconds,
                    user = data.user ?: existing.user,
                    key = data.key ?: existing.key,
                    app = data.app ?: existing.app,
                    target = data.target ?: existing.target,
                    whatsapp = data.whatsapp ?: existing.whatsapp,
                    announcements = data.announcements?.let { sortAnnouncements(it) } ?: existing.announcements,
                    device = data.device ?: existing.device
                )
                sessionStore.saveSession(updated)
                AppResult.Success(updated, result.message, result.serverTime)
            }
        }
    }

    suspend fun fetchKeyExpiration(): AppResult<KeyExpirationData> {
        val existing = sessionStore.loadSession()
            ?: return AppResult.Failure(AppError.InvalidSession("No active key session."))
        return apiClient.getKeyExpiration(
            key = existing.userKey,
            bearerToken = existing.sessionToken
        )
    }

    suspend fun logout(): AppResult<Unit> {
        val token = sessionStore.loadSession()?.sessionToken
        val remoteResult = if (!token.isNullOrBlank()) {
            apiClient.logoutSession(token)
        } else {
            null
        }
        // Always clear local encrypted session even if network fails
        sessionStore.clearSession()
        return when (remoteResult) {
            is AppResult.Failure -> AppResult.Success(Unit, message = "Local session cleared.")
            else -> AppResult.Success(Unit, message = "Logged out successfully.")
        }
    }

    fun sortAnnouncements(list: List<Announcement>): List<Announcement> {
        return list.sortedByDescending { it.publishedAt.orEmpty() }
    }

    fun mergeWhatsapp(primary: WhatsappInfo?, fallback: WhatsappInfo?): WhatsappInfo? {
        if (primary == null) return fallback
        if (fallback == null) return primary
        return WhatsappInfo(
            chatLink = primary.chatLink?.takeIf { it.isNotBlank() } ?: fallback.chatLink,
            buyKeyMessage = primary.buyKeyMessage?.takeIf { it.isNotBlank() } ?: fallback.buyKeyMessage,
            supportMessage = primary.supportMessage?.takeIf { it.isNotBlank() } ?: fallback.supportMessage
        )
    }

    fun mergeTarget(primary: TargetGameInfo?, fallback: TargetGameInfo?): TargetGameInfo? {
        if (primary == null) return fallback
        if (fallback == null) return primary
        return TargetGameInfo(
            name = primary.name?.takeIf { it.isNotBlank() } ?: fallback.name,
            appName = primary.appName?.takeIf { it.isNotBlank() } ?: fallback.appName,
            packageName = primary.packageName?.takeIf { it.isNotBlank() } ?: fallback.packageName,
            status = primary.status?.takeIf { it.isNotBlank() } ?: fallback.status,
            supported = primary.supported ?: fallback.supported,
            version = primary.version?.takeIf { it.isNotBlank() } ?: fallback.version,
            minVersion = primary.minVersion?.takeIf { it.isNotBlank() } ?: fallback.minVersion,
            supportedVersions = primary.supportedVersions?.takeIf { it.isNotEmpty() } ?: fallback.supportedVersions,
            dataFiles = primary.dataFiles.ifEmpty { fallback.dataFiles }
        )
    }
}
