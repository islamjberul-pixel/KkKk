package com.kar7.pro.util

object VersionComparator {
    /**
     * Returns true if [installedVersion] >= [minSupportedVersion].
     * If [minSupportedVersion] is null or blank, returns true.
     */
    fun isVersionSupported(installedVersion: String, minSupportedVersion: String?): Boolean {
        val min = minSupportedVersion?.trim()?.takeIf { it.isNotEmpty() } ?: return true
        return compareSemVer(installedVersion.trim(), min) >= 0
    }

    fun compareSemVer(left: String, right: String): Int {
        val leftParts = parseParts(left)
        val rightParts = parseParts(right)
        val maxLen = maxOf(leftParts.size, rightParts.size)
        for (i in 0 until maxLen) {
            val l = leftParts.getOrElse(i) { 0 }
            val r = rightParts.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun parseParts(version: String): List<Int> {
        val cleaned = version.removePrefix("v").removePrefix("V").substringBefore("-")
        return cleaned.split(".")
            .map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }
    }
}
