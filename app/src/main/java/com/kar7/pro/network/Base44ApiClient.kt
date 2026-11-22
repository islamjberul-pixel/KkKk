package com.kar7.pro.network

import com.kar7.pro.config.Kar7Config
import com.kar7.pro.models.ApiEnvelope
import com.kar7.pro.models.ApiErrorDetailAdapter
import com.kar7.pro.models.AppError
import com.kar7.pro.models.AppInitData
import com.kar7.pro.models.AppResult
import com.kar7.pro.models.KeyExpirationData
import com.kar7.pro.models.KeyExpirationRequest
import com.kar7.pro.models.KeyVerifyData
import com.kar7.pro.models.KeyVerifyRequest
import com.kar7.pro.models.LogoutData
import com.kar7.pro.models.SessionValidateData
import com.kar7.pro.models.SettingsData
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class Base44ApiClient(
    private val baseUrl: String = Kar7Config.BASE_URL,
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val moshi: Moshi = defaultMoshi(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    init {
        require(baseUrl.startsWith("https://")) {
            "KAR7 PRO API base URL must use HTTPS."
        }
    }

    suspend fun getAppInit(): AppResult<AppInitData> =
        executeGet(Kar7Config.ENDPOINT_APP_INIT, AppInitData::class.java)

    suspend fun getSettings(): AppResult<SettingsData> =
        executeGet(Kar7Config.ENDPOINT_SETTINGS, SettingsData::class.java)

    suspend fun verifyKey(requestPayload: KeyVerifyRequest): AppResult<KeyVerifyData> {
        val adapter = moshi.adapter(KeyVerifyRequest::class.java)
        val json = adapter.toJson(requestPayload)
        return executePost(
            endpoint = Kar7Config.ENDPOINT_KEYS_VERIFY,
            jsonBody = json,
            bearerToken = null,
            dataClass = KeyVerifyData::class.java,
            allowTransientRetry = false
        )
    }

    suspend fun getKeyExpiration(
        key: String,
        bearerToken: String? = null
    ): AppResult<KeyExpirationData> {
        val adapter = moshi.adapter(KeyExpirationRequest::class.java)
        val json = adapter.toJson(KeyExpirationRequest(key = key))
        return executePost(
            endpoint = Kar7Config.ENDPOINT_KEYS_EXPIRATION,
            jsonBody = json,
            bearerToken = bearerToken,
            dataClass = KeyExpirationData::class.java,
            allowTransientRetry = true
        )
    }

    suspend fun validateSession(sessionToken: String): AppResult<SessionValidateData> {
        return executePost(
            endpoint = Kar7Config.ENDPOINT_SESSION_VALIDATE,
            jsonBody = "{}",
            bearerToken = sessionToken,
            dataClass = SessionValidateData::class.java,
            allowTransientRetry = true
        )
    }

    suspend fun logoutSession(sessionToken: String): AppResult<LogoutData> {
        return executePost(
            endpoint = Kar7Config.ENDPOINT_SESSION_LOGOUT,
            jsonBody = "{}",
            bearerToken = sessionToken,
            dataClass = LogoutData::class.java,
            allowTransientRetry = false
        )
    }

    private suspend fun <T> executeGet(
        endpoint: String,
        dataClass: Class<T>
    ): AppResult<T> = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .get()
            .header("Accept", "application/json")
            .build()
        executeRequest(request, dataClass, allowTransientRetry = true)
    }

    private suspend fun <T> executePost(
        endpoint: String,
        jsonBody: String,
        bearerToken: String?,
        dataClass: Class<T>,
        allowTransientRetry: Boolean
    ): AppResult<T> = withContext(ioDispatcher) {
        val builder = Request.Builder()
            .url("$baseUrl$endpoint")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
        if (!bearerToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        executeRequest(builder.build(), dataClass, allowTransientRetry)
    }

    private suspend fun <T> executeRequest(
        request: Request,
        dataClass: Class<T>,
        allowTransientRetry: Boolean
    ): AppResult<T> {
        val maxAttempts = if (allowTransientRetry) 1 + Kar7Config.MAX_TRANSIENT_RETRIES else 1
        var lastException: IOException? = null

        for (attempt in 1..maxAttempts) {
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    return parseResponse(
                        httpCode = response.code,
                        isSuccessful = response.isSuccessful,
                        rawBody = rawBody,
                        dataClass = dataClass
                    )
                }
            } catch (ex: IOException) {
                lastException = ex
                if (attempt < maxAttempts && isTransientNetworkFailure(ex)) {
                    delay(350L)
                } else {
                    break
                }
            }
        }

        val error = when (lastException) {
            is UnknownHostException, is ConnectException ->
                AppError.NoInternet("Unable to reach KAR7 PRO server. Check your internet connection.")
            is SocketTimeoutException ->
                AppError.ServerUnavailable(message = "Connection timed out while contacting the server.")
            else ->
                AppError.NoInternet(lastException?.message ?: "Network error occurred.")
        }
        return AppResult.Failure(error)
    }

    private fun <T> parseResponse(
        httpCode: Int,
        isSuccessful: Boolean,
        rawBody: String,
        dataClass: Class<T>
    ): AppResult<T> {
        if (rawBody.isBlank()) {
            return if (httpCode in 500..599) {
                AppResult.Failure(AppError.ServerUnavailable(httpCode, "Server returned HTTP $httpCode."))
            } else {
                AppResult.Failure(AppError.InvalidJson("Server returned an empty response (HTTP $httpCode)."))
            }
        }

        val envelopeType = Types.newParameterizedType(ApiEnvelope::class.java, dataClass)
        val adapter = moshi.adapter<ApiEnvelope<T>>(envelopeType)
        val envelope = try {
            adapter.fromJson(rawBody)
        } catch (_: Exception) {
            return if (httpCode in 500..599) {
                AppResult.Failure(AppError.ServerUnavailable(httpCode, "Server unavailable (HTTP $httpCode)."))
            } else {
                AppResult.Failure(AppError.InvalidJson("Invalid JSON received from server."))
            }
        }

        if (envelope == null) {
            return AppResult.Failure(AppError.InvalidJson("Unable to parse server response."))
        }

        if (isSuccessful && envelope.success && envelope.data != null) {
            return AppResult.Success(
                data = envelope.data,
                message = envelope.message,
                serverTime = envelope.serverTime
            )
        }

        // If success=true on logout/void endpoints where data might be null {}
        if (isSuccessful && envelope.success && dataClass == LogoutData::class.java) {
            @Suppress("UNCHECKED_CAST")
            return AppResult.Success(
                data = LogoutData(loggedOut = true) as T,
                message = envelope.message,
                serverTime = envelope.serverTime
            )
        }

        val serverMessage = envelope.error?.displayMessage()
            ?: envelope.message?.takeIf { it.isNotBlank() }
            ?: "Request rejected by server (HTTP $httpCode)."

        val errorCode = envelope.error?.code?.lowercase()
        val mappedError = classifyServerError(httpCode, errorCode, serverMessage)
        return AppResult.Failure(mappedError)
    }

    private fun classifyServerError(
        httpCode: Int,
        errorCode: String?,
        message: String
    ): AppError {
        val normalized = "${errorCode.orEmpty()} $message"
            .lowercase()
            .replace('_', ' ')
            .replace('-', ' ')
        return when {
            httpCode == 401 || normalized.contains("invalid session") ||
                normalized.contains("session expired") || normalized.contains("session not found") ||
                normalized.contains("unauthorized") ||
                (normalized.contains("token") && normalized.contains("invalid")) ->
                AppError.InvalidSession(
                    if (message.all { it.isUpperCase() || it == '_' }) {
                        "Your session is invalid or expired ($message)."
                    } else {
                        message
                    }
                )

            normalized.contains("device") && (normalized.contains("conflict") ||
                normalized.contains("limit") || normalized.contains("mismatch") ||
                normalized.contains("another") || normalized.contains("registered") ||
                normalized.contains("bound")) ->
                AppError.DeviceConflict(message)

            normalized.contains("expired") ->
                AppError.ExpiredKey(
                    if (message.all { it.isUpperCase() || it == '_' }) {
                        "This key has expired ($message)."
                    } else {
                        message
                    }
                )

            normalized.contains("disabled") || normalized.contains("suspended") ||
                normalized.contains("revoked") || normalized.contains("banned") ->
                AppError.DisabledKey(
                    if (message.all { it.isUpperCase() || it == '_' }) {
                        "This key has been disabled ($message)."
                    } else {
                        message
                    }
                )

            normalized.contains("invalid key") || normalized.contains("key not found") ||
                normalized.contains("not found") ->
                AppError.InvalidKey(
                    if (message.all { it.isUpperCase() || it == '_' }) {
                        "Invalid key: key was not found ($message)."
                    } else {
                        message
                    }
                )

            httpCode in 500..599 ->
                AppError.ServerUnavailable(httpCode, message)

            else ->
                AppError.ApiRejection(errorCode = errorCode, message = message)
        }
    }

    private fun isTransientNetworkFailure(ex: IOException): Boolean {
        return ex is SocketTimeoutException || ex is ConnectException
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultMoshi(): Moshi = Moshi.Builder()
            .add(ApiErrorDetailAdapter())
            .build()

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(Kar7Config.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Kar7Config.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(Kar7Config.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
