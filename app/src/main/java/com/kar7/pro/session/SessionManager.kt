package com.kar7.pro.session

import com.kar7.pro.config.Kar7Config
import com.kar7.pro.models.AppError
import com.kar7.pro.models.AppResult
import com.kar7.pro.models.StoredSession
import com.kar7.pro.repository.Kar7Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Unauthenticated : SessionState
    data class Active(
        val session: StoredSession,
        val remainingSeconds: Long?,
        val lastValidatedAtMs: Long
    ) : SessionState

    data class Expired(val reason: String) : SessionState
}

class SessionManager(
    private val repository: Kar7Repository,
    private val scope: CoroutineScope,
    private val validateIntervalMs: Long = Kar7Config.FOREGROUND_VALIDATE_INTERVAL_MS
) {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private var tickerJob: Job? = null
    private var periodicValidationJob: Job? = null
    private var isForeground: Boolean = false
    private var lastValidationMs: Long = 0L

    fun onSessionAuthenticated(session: StoredSession) {
        lastValidationMs = System.currentTimeMillis()
        _sessionState.value = SessionState.Active(
            session = session,
            remainingSeconds = session.remainingSeconds,
            lastValidatedAtMs = lastValidationMs
        )
        startForegroundLoopsIfNeeded()
        // If login response omitted remaining_seconds, immediately query /session/validate
        if (session.remainingSeconds == null) {
            scope.launch { validateSessionNow() }
        }
    }

    suspend fun restoreAndValidateSession(): Boolean {
        val stored = repository.loadStoredSession() ?: run {
            _sessionState.value = SessionState.Unauthenticated
            return false
        }
        return when (val result = repository.validateSavedSession()) {
            is AppResult.Success -> {
                onSessionAuthenticated(result.data)
                true
            }
            is AppResult.Failure -> {
                if (result.error is AppError.InvalidSession ||
                    result.error is AppError.ExpiredKey ||
                    result.error is AppError.DisabledKey
                ) {
                    handleSessionInvalidated(result.error.message)
                    false
                } else {
                    // Temporary network outage on restore: keep encrypted session only if we already have one,
                    // or require validation before dashboard? Prompt: "On application launch: Validate saved session.
                    // If valid, open dashboard. If invalid, clear local session and return to login."
                    // For transient network errors, surface error cleanly rather than silently bypassing validation.
                    false
                }
            }
        }
    }

    suspend fun validateSessionNow(): AppResult<StoredSession> {
        val result = repository.validateSavedSession()
        when (result) {
            is AppResult.Success -> {
                lastValidationMs = System.currentTimeMillis()
                _sessionState.value = SessionState.Active(
                    session = result.data,
                    remainingSeconds = result.data.remainingSeconds,
                    lastValidatedAtMs = lastValidationMs
                )
            }
            is AppResult.Failure -> {
                if (result.error is AppError.InvalidSession ||
                    result.error is AppError.ExpiredKey ||
                    result.error is AppError.DisabledKey
                ) {
                    handleSessionInvalidated(result.error.message)
                }
            }
        }
        return result
    }

    fun updateServerRemainingSeconds(remainingSeconds: Long?) {
        val current = _sessionState.value as? SessionState.Active ?: return
        if (remainingSeconds != null) {
            _sessionState.value = current.copy(remainingSeconds = remainingSeconds)
        }
    }

    suspend fun logout() {
        stopForegroundLoops()
        repository.logout()
        _sessionState.value = SessionState.Unauthenticated
    }

    fun acknowledgeExpired() {
        stopForegroundLoops()
        repository.clearLocalSession()
        _sessionState.value = SessionState.Unauthenticated
    }

    fun onAppForegrounded() {
        isForeground = true
        if (_sessionState.value is SessionState.Active) {
            startForegroundLoopsIfNeeded()
            val now = System.currentTimeMillis()
            if (now - lastValidationMs >= Kar7Config.MIN_RESUME_VALIDATE_GAP_MS) {
                scope.launch { validateSessionNow() }
            }
        }
    }

    fun onAppBackgrounded() {
        isForeground = false
        stopForegroundLoops()
    }

    private fun handleSessionInvalidated(reason: String) {
        stopForegroundLoops()
        repository.clearLocalSession()
        _sessionState.value = SessionState.Expired(reason)
    }

    private fun startForegroundLoopsIfNeeded() {
        if (!isForeground || _sessionState.value !is SessionState.Active) return

        if (tickerJob?.isActive != true) {
            tickerJob = scope.launch {
                while (isActive && isForeground) {
                    delay(1_000L)
                    val current = _sessionState.value as? SessionState.Active ?: break
                    val secs = current.remainingSeconds
                    if (secs != null) {
                        if (secs <= 1L) {
                            _sessionState.value = current.copy(remainingSeconds = 0L)
                            validateSessionNow()
                        } else {
                            _sessionState.value = current.copy(remainingSeconds = secs - 1L)
                        }
                    }
                }
            }
        }

        if (periodicValidationJob?.isActive != true) {
            periodicValidationJob = scope.launch {
                while (isActive && isForeground) {
                    delay(validateIntervalMs)
                    if (_sessionState.value is SessionState.Active) {
                        validateSessionNow()
                    }
                }
            }
        }
    }

    private fun stopForegroundLoops() {
        tickerJob?.cancel()
        tickerJob = null
        periodicValidationJob?.cancel()
        periodicValidationJob = null
    }
}
