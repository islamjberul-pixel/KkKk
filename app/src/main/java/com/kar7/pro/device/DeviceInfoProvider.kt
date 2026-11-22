package com.kar7.pro.device

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.example.BuildConfig
import com.kar7.pro.util.Formatters

data class DeviceIdentity(
    val deviceId: String,
    val maskedDeviceId: String,
    val deviceName: String,
    val appVersion: String
)

interface DeviceInfoProvider {
    fun getDeviceIdentity(): DeviceIdentity
}

class AndroidDeviceInfoProvider(
    private val context: Context
) : DeviceInfoProvider {

    @SuppressLint("HardwareIds")
    override fun getDeviceIdentity(): DeviceIdentity {
        val rawId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim().orEmpty().ifEmpty { "unavailable-android-id" }

        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val combinedName = when {
            manufacturer.isEmpty() && model.isEmpty() -> "Android Device"
            model.lowercase().startsWith(manufacturer.lowercase()) -> model
            else -> "$manufacturer $model".trim()
        }

        return DeviceIdentity(
            deviceId = rawId,
            maskedDeviceId = Formatters.maskDeviceId(rawId),
            deviceName = combinedName,
            appVersion = BuildConfig.VERSION_NAME
        )
    }
}
