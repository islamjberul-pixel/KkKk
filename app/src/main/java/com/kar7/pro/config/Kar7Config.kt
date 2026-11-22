package com.kar7.pro.config

object Kar7Config {
    const val APP_NAME = "KAR7 PRO"
    const val BASE_URL = "https://kar-vault-pro.base44.app/api/v1"

    // Exact Base44 API endpoints
    const val ENDPOINT_APP_INIT = "/app-init"
    const val ENDPOINT_SETTINGS = "/settings"
    const val ENDPOINT_KEYS_VERIFY = "/keys/verify"
    const val ENDPOINT_KEYS_EXPIRATION = "/keys/expiration"
    const val ENDPOINT_SESSION_VALIDATE = "/session/validate"
    const val ENDPOINT_SESSION_LOGOUT = "/session/logout"

    // Network timeouts (seconds)
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 25L
    const val WRITE_TIMEOUT_SECONDS = 25L
    const val DOWNLOAD_READ_TIMEOUT_SECONDS = 60L

    // Transient network retry limit (no infinite retries)
    const val MAX_TRANSIENT_RETRIES = 1

    // Session foreground validation interval (milliseconds)
    const val FOREGROUND_VALIDATE_INTERVAL_MS = 60_000L
    const val MIN_RESUME_VALIDATE_GAP_MS = 15_000L

    // Storage & integrity constants
    const val VERIFIED_DATA_SUBDIR = "game_data/verified"
    const val STAGING_DATA_SUBDIR = "downloads_staging"
    const val IO_BUFFER_SIZE = 8 * 1024
    const val MAX_ZIP_ENTRIES = 2_048
    const val MAX_ZIP_UNCOMPRESSED_BYTES = 1024L * 1024L * 1024L // 1 GiB safety cap
}
