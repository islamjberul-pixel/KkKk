package com.kar7.pro.util

import java.util.Locale

object Formatters {

    fun maskKey(rawKey: String?): String {
        val key = rawKey?.trim().orEmpty()
        if (key.isEmpty()) return "Not Available"
        if (key.length <= 6) return "•".repeat(key.length)
        val prefix = key.take(4)
        val suffix = key.takeLast(4)
        return "$prefix-••••-••••-$suffix"
    }

    fun maskDeviceId(deviceId: String?): String {
        val id = deviceId?.trim().orEmpty()
        if (id.isEmpty()) return "Unknown"
        if (id.length <= 6) return "••${id.takeLast(2)}"
        return "${id.take(4)}••••${id.takeLast(4)}"
    }

    fun formatDuration(totalSeconds: Long?): String {
        if (totalSeconds == null) return "Syncing…"
        if (totalSeconds <= 0L) return "Expired"
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds % 86_400L) / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            days > 0 -> String.format(Locale.US, "%dd %02dh %02dm %02ds", days, hours, minutes, seconds)
            hours > 0 -> String.format(Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds)
            else -> String.format(Locale.US, "%02dm %02ds", minutes, seconds)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIdx = 0
        while (value >= 1024.0 && unitIdx < units.lastIndex) {
            value /= 1024.0
            unitIdx++
        }
        return if (unitIdx == 0) {
            "$bytes B"
        } else {
            String.format(Locale.US, "%.2f %s", value, units[unitIdx])
        }
    }

    fun formatTimestampDisplay(iso: String?): String {
        val raw = iso?.trim().orEmpty()
        if (raw.isEmpty()) return "—"
        return raw.replace("T", " ").removeSuffix("Z").take(19)
    }

    fun sanitizeFileName(rawName: String): String {
        val base = rawName.substringAfterLast('/').substringAfterLast('\\').trim()
        val safe = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (safe.isBlank() || safe == "." || safe == "..") {
            "game_data_${System.currentTimeMillis()}.bin"
        } else {
            safe
        }
    }
}
