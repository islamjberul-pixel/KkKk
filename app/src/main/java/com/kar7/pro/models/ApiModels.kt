package com.kar7.pro.models

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

/**
 * Handles backend error field whether Base44 returns null, a plain string, or a JSON object.
 */
data class ApiErrorDetail(
    val code: String? = null,
    val message: String? = null,
    val raw: String? = null
) {
    fun displayMessage(): String? =
        message?.takeIf { it.isNotBlank() }
            ?: raw?.takeIf { it.isNotBlank() }
            ?: code?.takeIf { it.isNotBlank() }
}

class ApiErrorDetailAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): ApiErrorDetail? {
        return when (reader.peek()) {
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                null
            }
            JsonReader.Token.STRING -> {
                val str = reader.nextString()
                ApiErrorDetail(raw = str, message = str)
            }
            JsonReader.Token.BEGIN_OBJECT -> {
                var code: String? = null
                var message: String? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "code" -> code = reader.nextStringOrNull()
                        "message" -> message = reader.nextStringOrNull()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                ApiErrorDetail(code = code, message = message)
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: ApiErrorDetail?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.beginObject()
            writer.name("code").value(value.code)
            writer.name("message").value(value.message)
            writer.endObject()
        }
    }

    private fun JsonReader.nextStringOrNull(): String? =
        if (peek() == JsonReader.Token.NULL) {
            nextNull<Unit>()
            null
        } else {
            nextString()
        }
}

@JsonClass(generateAdapter = true)
data class ApiEnvelope<T>(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "error") val error: ApiErrorDetail? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: T? = null,
    @Json(name = "server_time") val serverTime: String? = null
)

@JsonClass(generateAdapter = true)
data class AppInitData(
    @Json(name = "app_enabled") val appEnabled: Boolean = true,
    @Json(name = "maintenance_mode") val maintenanceMode: Boolean = false,
    @Json(name = "maintenance_message") val maintenanceMessage: String? = null,
    @Json(name = "min_supported_version") val minSupportedVersion: String? = null,
    @Json(name = "update_message") val updateMessage: String? = null,
    @Json(name = "latest_version") val latestVersion: String? = null,
    @Json(name = "app_name") val appName: String? = null,
    @Json(name = "app_status") val appStatus: String? = null,
    @Json(name = "app") val app: AppInfo? = null,
    @Json(name = "target") val target: TargetGameInfo? = null,
    @Json(name = "whatsapp") val whatsapp: WhatsappInfo? = null,
    @Json(name = "announcements") val announcements: List<Announcement>? = null
)

@JsonClass(generateAdapter = true)
data class SettingsData(
    @Json(name = "app_enabled") val appEnabled: Boolean? = null,
    @Json(name = "maintenance_mode") val maintenanceMode: Boolean? = null,
    @Json(name = "maintenance_message") val maintenanceMessage: String? = null,
    @Json(name = "min_supported_version") val minSupportedVersion: String? = null,
    @Json(name = "update_message") val updateMessage: String? = null,
    @Json(name = "app_status") val appStatus: String? = null,
    @Json(name = "privacy_policy") val privacyPolicy: String? = null,
    @Json(name = "privacy_url") val privacyUrl: String? = null,
    @Json(name = "app") val app: AppInfo? = null,
    @Json(name = "target") val target: TargetGameInfo? = null,
    @Json(name = "whatsapp") val whatsapp: WhatsappInfo? = null,
    @Json(name = "announcements") val announcements: List<Announcement>? = null
)

@JsonClass(generateAdapter = true)
data class KeyVerifyRequest(
    @Json(name = "key") val key: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_name") val deviceName: String,
    @Json(name = "app_version") val appVersion: String
)

@JsonClass(generateAdapter = true)
data class KeyVerifyData(
    @Json(name = "session_token") val sessionToken: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "remaining_seconds") val remainingSeconds: Long? = null,
    @Json(name = "user") val user: UserInfo? = null,
    @Json(name = "key") val key: KeyInfo? = null,
    @Json(name = "app") val app: AppInfo? = null,
    @Json(name = "target") val target: TargetGameInfo? = null,
    @Json(name = "whatsapp") val whatsapp: WhatsappInfo? = null,
    @Json(name = "announcements") val announcements: List<Announcement>? = null,
    @Json(name = "device") val device: DeviceInfo? = null
)

@JsonClass(generateAdapter = true)
data class KeyExpirationRequest(
    @Json(name = "key") val key: String
)

@JsonClass(generateAdapter = true)
data class KeyExpirationData(
    @Json(name = "key") val key: String? = null,
    @Json(name = "masked_key") val maskedKey: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "remaining_seconds") val remainingSeconds: Long? = null,
    @Json(name = "assigned_device") val assignedDevice: String? = null,
    @Json(name = "device_name") val deviceName: String? = null,
    @Json(name = "device_id") val deviceId: String? = null,
    @Json(name = "device_status") val deviceStatus: String? = null,
    @Json(name = "device") val device: DeviceInfo? = null
)

@JsonClass(generateAdapter = true)
data class SessionValidateData(
    @Json(name = "valid") val valid: Boolean? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "remaining_seconds") val remainingSeconds: Long? = null,
    @Json(name = "user") val user: UserInfo? = null,
    @Json(name = "key") val key: KeyInfo? = null,
    @Json(name = "app") val app: AppInfo? = null,
    @Json(name = "target") val target: TargetGameInfo? = null,
    @Json(name = "whatsapp") val whatsapp: WhatsappInfo? = null,
    @Json(name = "announcements") val announcements: List<Announcement>? = null,
    @Json(name = "device") val device: DeviceInfo? = null
)

@JsonClass(generateAdapter = true)
data class LogoutData(
    @Json(name = "logged_out") val loggedOut: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "username") val username: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "role") val role: String? = null
) {
    fun displayName(): String =
        name?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: email?.takeIf { it.isNotBlank() }
            ?: "Authorized User"
}

@JsonClass(generateAdapter = true)
data class KeyInfo(
    @Json(name = "id") val id: String? = null,
    @Json(name = "key") val key: String? = null,
    @Json(name = "code") val code: String? = null,
    @Json(name = "masked_key") val maskedKey: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "remaining_seconds") val remainingSeconds: Long? = null,
    @Json(name = "assigned_device") val assignedDevice: String? = null,
    @Json(name = "device_status") val deviceStatus: String? = null
)

@JsonClass(generateAdapter = true)
data class DeviceInfo(
    @Json(name = "device_id") val deviceId: String? = null,
    @Json(name = "device_name") val deviceName: String? = null,
    @Json(name = "status") val status: String? = null
)

@JsonClass(generateAdapter = true)
data class AppInfo(
    @Json(name = "name") val name: String? = null,
    @Json(name = "app_name") val appName: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "version") val version: String? = null,
    @Json(name = "app_version") val appVersion: String? = null,
    @Json(name = "min_supported_version") val minSupportedVersion: String? = null,
    @Json(name = "update_message") val updateMessage: String? = null,
    @Json(name = "maintenance_message") val maintenanceMessage: String? = null,
    @Json(name = "app_enabled") val appEnabled: Boolean? = null,
    @Json(name = "maintenance_mode") val maintenanceMode: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class TargetGameInfo(
    @Json(name = "name") val name: String? = null,
    @Json(name = "app_name") val appName: String? = null,
    @Json(name = "package_name") val packageName: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "supported") val supported: Boolean? = null,
    @Json(name = "version") val version: String? = null,
    @Json(name = "min_version") val minVersion: String? = null,
    @Json(name = "supported_versions") val supportedVersions: List<String>? = null,
    @Json(name = "data_files") val dataFiles: List<GameDataFile> = emptyList()
) {
    fun displayName(): String? =
        appName?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() }
}

@JsonClass(generateAdapter = true)
data class GameDataFile(
    @Json(name = "id") val id: String? = null,
    @Json(name = "file_name") val fileName: String = "",
    @Json(name = "download_url") val downloadUrl: String = "",
    @Json(name = "file_size") val fileSize: Long = 0L,
    @Json(name = "version") val version: String? = null,
    @Json(name = "sha256") val sha256: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "mime_type") val mimeType: String? = null
) {
    fun stableId(): String =
        id?.takeIf { it.isNotBlank() } ?: "${fileName}_${version.orEmpty()}_${sha256.take(8)}"
}

@JsonClass(generateAdapter = true)
data class WhatsappInfo(
    @Json(name = "chat_link") val chatLink: String? = null,
    @Json(name = "buy_key_message") val buyKeyMessage: String? = null,
    @Json(name = "support_message") val supportMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class Announcement(
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: String = "",
    @Json(name = "published_at") val publishedAt: String? = null
) {
    fun stableId(): String =
        id?.takeIf { it.isNotBlank() } ?: "${title.hashCode()}_${publishedAt.orEmpty()}"
}

@JsonClass(generateAdapter = true)
data class StoredSession(
    val sessionToken: String,
    val userKey: String,
    val expiresAt: String? = null,
    val remainingSeconds: Long? = null,
    val user: UserInfo? = null,
    val key: KeyInfo? = null,
    val app: AppInfo? = null,
    val target: TargetGameInfo? = null,
    val whatsapp: WhatsappInfo? = null,
    val announcements: List<Announcement> = emptyList(),
    val device: DeviceInfo? = null
)

sealed interface AppError {
    val message: String

    data class NoInternet(override val message: String = "No internet connection. Check your network and try again.") : AppError
    data class ServerUnavailable(val httpCode: Int? = null, override val message: String = "KAR7 PRO server is temporarily unavailable.") : AppError
    data class InvalidJson(override val message: String = "Received an unexpected response format from the server.") : AppError
    data class InvalidKey(override val message: String) : AppError
    data class ExpiredKey(override val message: String) : AppError
    data class DisabledKey(override val message: String) : AppError
    data class DeviceConflict(override val message: String) : AppError
    data class InvalidSession(override val message: String = "Session expired or invalidated by server.") : AppError
    data class ApiRejection(val errorCode: String? = null, override val message: String) : AppError
}

sealed interface AppResult<out T> {
    data class Success<T>(val data: T, val message: String? = null, val serverTime: String? = null) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
