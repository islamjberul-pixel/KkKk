package com.kar7.pro.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kar7.pro.device.AdbConnectManager
import com.kar7.pro.device.AdbEnvironmentState
import com.kar7.pro.device.AdbNotificationEvent
import com.kar7.pro.device.AdbPairingBus
import com.kar7.pro.device.AdbPairingNotificationManager
import com.kar7.pro.device.AdbPairingReceiver
import com.kar7.pro.device.AdbPairingStatus
import com.kar7.pro.device.AdbPairingTransport
import com.kar7.pro.device.AndroidDeviceInfoProvider
import com.kar7.pro.device.DeviceIdentity
import com.kar7.pro.download.DownloadState
import com.kar7.pro.download.FileDownloadManager
import com.kar7.pro.gamedata.GameImportManager
import com.kar7.pro.gamedata.TargetLaunchResult
import com.kar7.pro.gamedata.TargetPackageDiagnostics
import com.kar7.pro.models.AppInitData
import com.kar7.pro.models.AppResult
import com.kar7.pro.models.SettingsData
import com.kar7.pro.models.StoredSession
import com.kar7.pro.models.TargetGameInfo
import com.kar7.pro.models.WhatsappInfo
import com.kar7.pro.network.Base44ApiClient
import com.kar7.pro.repository.AppGateResult
import com.kar7.pro.repository.Kar7Repository
import com.kar7.pro.security.SecureSessionStore
import com.kar7.pro.session.SessionManager
import com.kar7.pro.session.SessionState
import com.kar7.pro.storage.PermissionsManager
import com.kar7.pro.storage.PermissionsSummary
import com.kar7.pro.storage.SafExportResult
import com.kar7.pro.storage.SafFolderStatus
import com.kar7.pro.storage.SafStorageManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AppStartupRoute {
    data object Splash : AppStartupRoute
    data object AppDisabled : AppStartupRoute
    data class Maintenance(val message: String) : AppStartupRoute
    data class UpdateRequired(val message: String, val minVersion: String) : AppStartupRoute
    data class InitError(val message: String) : AppStartupRoute
    data object Login : AppStartupRoute
    data object Dashboard : AppStartupRoute
}

data class DataVaultState(
    val isDownloadingOrVerifying: Boolean = false,
    val isExporting: Boolean = false,
    val progressPercent: Int? = null,
    val statusHeadline: String = "FILES READY",
    val detailMessage: String? = null,
    val verifiedCount: Int = 0,
    val totalFilesCount: Int = 0,
    val canDownloadData: Boolean = false,
    val canExportToSaf: Boolean = false,
    val safFolderSelected: Boolean = false,
    val safFolderDisplay: String? = null
)

data class Kar7UiState(
    val startupRoute: AppStartupRoute = AppStartupRoute.Splash,
    val splashStatusText: String = "Connecting to Base44 gateway…",
    val deviceIdentity: DeviceIdentity? = null,
    val appInitData: AppInitData? = null,
    val settingsData: SettingsData? = null,
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val isKeyExpired: Boolean = false,
    val expiredMessage: String? = null,
    val lastValidSession: StoredSession? = null,
    val isRefreshingTarget: Boolean = false,
    val targetDiagnostics: TargetPackageDiagnostics? = null,
    val safFolderStatus: SafFolderStatus = SafFolderStatus(),
    val permissionsSummary: PermissionsSummary? = null,
    val adbEnvironment: AdbEnvironmentState? = null,
    val showPermissionsModal: Boolean = false,
    val notificationDeniedMessage: String? = null,
    val dashboardFeedbackMessage: String? = null,
    val dashboardFeedbackIsError: Boolean = false,
    val isExportingToSaf: Boolean = false,
    val exportProgressPercent: Int? = null,
    val pendingSafExportAfterPicker: Boolean = false
)

sealed interface UiOneTimeEvent {
    data class ShowMessage(val message: String) : UiOneTimeEvent
    data class LaunchTargetGame(val intent: Intent) : UiOneTimeEvent
    data class LaunchDeveloperOptions(val intent: Intent, val fallbackIntent: Intent) : UiOneTimeEvent
    data class RequestNotificationPermission(val launchAdbFlowAfterGrant: Boolean) : UiOneTimeEvent
    data class LaunchAppNotificationSettings(val intent: Intent) : UiOneTimeEvent
    data object RequestSafFolderPicker : UiOneTimeEvent
}

class Kar7ViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = Base44ApiClient()
    private val sessionStore = SecureSessionStore(application)
    private val deviceInfoProvider = AndroidDeviceInfoProvider(application)
    val repository = Kar7Repository(apiClient, sessionStore, deviceInfoProvider)
    val sessionManager = SessionManager(repository, viewModelScope)
    val downloadManager = FileDownloadManager(application, viewModelScope)
    val safStorageManager = SafStorageManager(application, sessionStore)
    val gameImportManager = GameImportManager(application)
    val permissionsManager = PermissionsManager(application, safStorageManager)
    private var adbConnectManager = AdbConnectManager(application)

    private val _uiState = MutableStateFlow(
        Kar7UiState(
            deviceIdentity = repository.getDeviceIdentity(),
            safFolderStatus = safStorageManager.getCurrentSafFolderStatus(),
            adbEnvironment = adbConnectManager.inspectEnvironment()
        )
    )
    val uiState: StateFlow<Kar7UiState> = _uiState.asStateFlow()

    val sessionState: StateFlow<SessionState> = sessionManager.sessionState
    val downloadStates: StateFlow<Map<String, DownloadState>> = downloadManager.downloadStates

    private val _events = MutableSharedFlow<UiOneTimeEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiOneTimeEvent> = _events.asSharedFlow()

    init {
        AdbPairingBus.registerSubscriber()

        viewModelScope.launch {
            sessionManager.sessionState.collect { state ->
                when (state) {
                    is SessionState.Expired -> {
                        downloadManager.cancelAllDownloads()
                        AdbPairingNotificationManager.cancelNotification(getApplication())
                        _uiState.update { current ->
                            current.copy(
                                isKeyExpired = true,
                                expiredMessage = "KEY EXPIRED",
                                dashboardFeedbackMessage = "KEY EXPIRED",
                                dashboardFeedbackIsError = true
                            )
                        }
                    }
                    is SessionState.Active -> {
                        val expired = state.remainingSeconds != null && state.remainingSeconds <= 0L
                        if (expired) {
                            downloadManager.cancelAllDownloads()
                        }
                        _uiState.update { current ->
                            current.copy(
                                lastValidSession = state.session,
                                isKeyExpired = expired,
                                expiredMessage = if (expired) "KEY EXPIRED" else null
                            )
                        }
                        refreshEnvironmentDiagnostics(state.session)
                    }
                    SessionState.Unauthenticated -> Unit
                }
            }
        }

        viewModelScope.launch {
            AdbPairingBus.events.collect { event ->
                when (event) {
                    is AdbNotificationEvent.PairingReplySubmitted -> {
                        handleAdbPairingReply(event.rawCodeInput)
                    }
                    AdbNotificationEvent.PairingCancelled -> {
                        _uiState.update { current ->
                            current.copy(
                                adbEnvironment = adbConnectManager.inspectEnvironment(AdbPairingStatus.Idle),
                                dashboardFeedbackMessage = "ADB pairing prompt dismissed.",
                                dashboardFeedbackIsError = false
                            )
                        }
                    }
                }
            }
        }

        runAppInitialization()
    }

    override fun onCleared() {
        super.onCleared()
        AdbPairingBus.unregisterSubscriber()
    }

    fun overrideAdbTransportForTesting(transport: AdbPairingTransport) {
        adbConnectManager = AdbConnectManager(getApplication(), transport)
        refreshEnvironmentDiagnostics()
    }

    fun runAppInitialization() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    startupRoute = AppStartupRoute.Splash,
                    splashStatusText = "Connecting to Base44 gateway…",
                    loginError = null
                )
            }

            when (val gate = repository.initializeApp()) {
                is AppGateResult.Error -> {
                    _uiState.update {
                        it.copy(startupRoute = AppStartupRoute.InitError(gate.error.message))
                    }
                }
                AppGateResult.Disabled -> {
                    _uiState.update { it.copy(startupRoute = AppStartupRoute.AppDisabled) }
                }
                is AppGateResult.Maintenance -> {
                    _uiState.update { it.copy(startupRoute = AppStartupRoute.Maintenance(gate.message)) }
                }
                is AppGateResult.UpdateRequired -> {
                    _uiState.update {
                        it.copy(startupRoute = AppStartupRoute.UpdateRequired(gate.message, gate.minVersion))
                    }
                }
                is AppGateResult.Allowed -> {
                    _uiState.update {
                        it.copy(
                            appInitData = gate.initData,
                            splashStatusText = "Verifying encrypted session…"
                        )
                    }
                    when (val settingsRes = repository.fetchSettings()) {
                        is AppResult.Success -> _uiState.update { it.copy(settingsData = settingsRes.data) }
                        is AppResult.Failure -> Unit
                    }

                    val hasExisting = repository.loadStoredSession() != null
                    if (!hasExisting) {
                        _uiState.update { it.copy(startupRoute = AppStartupRoute.Login) }
                    } else {
                        val valid = sessionManager.restoreAndValidateSession()
                        if (valid) {
                            _uiState.update { it.copy(startupRoute = AppStartupRoute.Dashboard) }
                            refreshEnvironmentDiagnostics()
                        } else {
                            _uiState.update {
                                it.copy(
                                    startupRoute = AppStartupRoute.Login,
                                    loginError = "Saved session expired or could not be verified. Please sign in again."
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun loginWithKey(keyInput: String) {
        if (_uiState.value.isLoggingIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, loginError = null) }
            when (val result = repository.loginWithKey(keyInput)) {
                is AppResult.Success -> {
                    sessionManager.onSessionAuthenticated(result.data)
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            loginError = null,
                            isKeyExpired = false,
                            expiredMessage = null,
                            lastValidSession = result.data,
                            dashboardFeedbackMessage = null,
                            startupRoute = AppStartupRoute.Dashboard
                        )
                    }
                    refreshEnvironmentDiagnostics(result.data)
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            loginError = result.error.message
                        )
                    }
                }
            }
        }
    }

    fun clearLoginError() {
        _uiState.update { it.copy(loginError = null) }
    }

    fun logoutToLogin() {
        downloadManager.cancelAllDownloads()
        AdbPairingNotificationManager.cancelNotification(getApplication())
        viewModelScope.launch {
            sessionManager.logout()
            _uiState.update {
                it.copy(
                    startupRoute = AppStartupRoute.Login,
                    isKeyExpired = false,
                    expiredMessage = null,
                    lastValidSession = null,
                    loginError = null,
                    dashboardFeedbackMessage = null,
                    adbEnvironment = adbConnectManager.inspectEnvironment(AdbPairingStatus.Idle)
                )
            }
        }
    }

    fun currentWhatsappInfo(): WhatsappInfo? {
        val activeSession = (sessionState.value as? SessionState.Active)?.session
        val mergedInitSettings = repository.mergeWhatsapp(
            _uiState.value.settingsData?.whatsapp,
            _uiState.value.appInitData?.whatsapp
        )
        return repository.mergeWhatsapp(activeSession?.whatsapp, mergedInitSettings)
    }

    /**
     * Resolves the latest admin-configured target app metadata by prioritizing live Base44
     * /settings updates over cached session/app-init target entries.
     */
    fun effectiveTargetInfo(
        session: StoredSession? = (sessionState.value as? SessionState.Active)?.session ?: _uiState.value.lastValidSession,
        ui: Kar7UiState = _uiState.value
    ): TargetGameInfo? {
        val sessionOrInit = repository.mergeTarget(session?.target, ui.appInitData?.target)
        return repository.mergeTarget(ui.settingsData?.target, sessionOrInit)
    }

    /**
     * Fetches the latest admin-configured target package and file manifest from Base44
     * (/settings and /session/validate) and re-checks PackageManager visibility.
     */
    fun refreshAdminTargetConfig() {
        if (_uiState.value.isRefreshingTarget) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingTarget = true) }
            val settingsResult = repository.fetchSettings()
            if (settingsResult is AppResult.Success) {
                _uiState.update { it.copy(settingsData = settingsResult.data) }
            }
            if (sessionState.value is SessionState.Active) {
                sessionManager.validateSessionNow()
            }
            refreshEnvironmentDiagnostics()
            val pkg = effectiveTargetInfo()?.packageName?.takeIf { it.isNotBlank() }
            val diag = _uiState.value.targetDiagnostics
            val msg = when {
                settingsResult is AppResult.Failure ->
                    "Target check completed locally; server refresh failed: ${settingsResult.error.message}"
                pkg == null ->
                    "Admin config refreshed. No target package is configured."
                diag?.isLaunchable == true ->
                    "Target updated ($pkg): installed and ready to open."
                diag?.isInstalledOrVisible == true ->
                    "Target updated ($pkg): ${diag.detectionExplanation}"
                else ->
                    "Target updated ($pkg): not installed on this device."
            }
            _uiState.update {
                it.copy(
                    isRefreshingTarget = false,
                    dashboardFeedbackMessage = msg,
                    dashboardFeedbackIsError = settingsResult is AppResult.Failure
                )
            }
        }
    }

    fun refreshEnvironmentDiagnostics(
        session: StoredSession? = (sessionState.value as? SessionState.Active)?.session ?: _uiState.value.lastValidSession
    ) {
        val targetInfo = effectiveTargetInfo(session)
        val diag = gameImportManager.inspectTargetGame(targetInfo?.packageName, targetInfo)
        val permSummary = permissionsManager.inspectPermissions(diag)
        val safStatus = safStorageManager.getCurrentSafFolderStatus()
        val adbEnv = adbConnectManager.inspectEnvironment(_uiState.value.adbEnvironment?.pairingStatus)
        _uiState.update {
            it.copy(
                targetDiagnostics = diag,
                permissionsSummary = permSummary,
                safFolderStatus = safStatus,
                adbEnvironment = adbEnv
            )
        }
    }

    fun computeDataVaultState(
        session: StoredSession?,
        remainingSeconds: Long?,
        downloads: Map<String, DownloadState>,
        ui: Kar7UiState
    ): DataVaultState {
        val expired = ui.isKeyExpired || sessionState.value is SessionState.Expired ||
            (remainingSeconds != null && remainingSeconds <= 0L)
        val targetInfo = effectiveTargetInfo(session, ui)
        val dataFiles = targetInfo?.dataFiles.orEmpty()

        val verifiedCount = dataFiles.count { downloads[it.stableId()] is DownloadState.Verified }
        val activeDownloads = dataFiles.mapNotNull { downloads[it.stableId()] as? DownloadState.Downloading }
        val verifyingAny = dataFiles.any { downloads[it.stableId()] is DownloadState.Verifying }
        val failedAny = dataFiles.firstNotNullOfOrNull { downloads[it.stableId()] as? DownloadState.Failed }

        val progress: Int? = when {
            activeDownloads.isNotEmpty() -> {
                val sumBytes = activeDownloads.sumOf { it.bytesDownloaded }
                val totalBytes = activeDownloads.sumOf { it.totalBytes }
                if (totalBytes > 0L) ((sumBytes * 100L) / totalBytes).toInt().coerceIn(1, 99)
                else activeDownloads.map { it.progressPercent }.average().toInt().coerceIn(1, 99)
            }
            verifyingAny -> 99
            ui.isExportingToSaf -> ui.exportProgressPercent
            else -> null
        }

        val allVerified = dataFiles.isNotEmpty() && verifiedCount == dataFiles.size
        val safReady = ui.safFolderStatus.hasWritePermission && !ui.safFolderStatus.isRestrictedByScopedStorage

        val headline = when {
            expired -> "KEY EXPIRED"
            dataFiles.isEmpty() -> "NO FILES PUBLISHED"
            activeDownloads.isNotEmpty() -> "DOWNLOADING (${progress ?: 1}%)"
            verifyingAny -> "VERIFYING SHA-256…"
            ui.isExportingToSaf -> "EXPORTING TO SAF (${progress ?: 1}%)"
            allVerified -> "SHA-256 VERIFIED ($verifiedCount/${dataFiles.size})"
            else -> "DOWNLOAD & VERIFY ($verifiedCount/${dataFiles.size})"
        }

        val detail = when {
            expired -> "KEY EXPIRED — Authenticate with an active key to download or export files."
            dataFiles.isEmpty() -> "No files are published for this configuration on Base44."
            failedAny != null -> failedAny.errorMessage
            ui.safFolderStatus.limitationMessage != null -> ui.safFolderStatus.limitationMessage
            allVerified && safReady ->
                "All files passed SHA-256 verification. Ready to export to ${ui.safFolderStatus.displayName ?: "selected SAF folder"}."
            allVerified ->
                "All files passed SHA-256 verification in internal app storage. Select an accessible SAF folder to export."
            else ->
                "Download Base44 files and verify SHA-256 integrity before exporting to your selected SAF folder."
        }

        return DataVaultState(
            isDownloadingOrVerifying = activeDownloads.isNotEmpty() || verifyingAny,
            isExporting = ui.isExportingToSaf,
            progressPercent = progress,
            statusHeadline = headline,
            detailMessage = detail,
            verifiedCount = verifiedCount,
            totalFilesCount = dataFiles.size,
            canDownloadData = !expired && dataFiles.isNotEmpty() && !allVerified &&
                activeDownloads.isEmpty() && !verifyingAny,
            canExportToSaf = !expired && verifiedCount > 0 && activeDownloads.isEmpty() &&
                !verifyingAny && !ui.isExportingToSaf,
            safFolderSelected = safReady,
            safFolderDisplay = ui.safFolderStatus.displayName
        )
    }

    fun downloadRequiredDataFiles() {
        if (_uiState.value.isKeyExpired || sessionState.value !is SessionState.Active) {
            setFeedback("KEY EXPIRED", isError = true)
            return
        }
        val dataFiles = effectiveTargetInfo()?.dataFiles.orEmpty()
        if (dataFiles.isEmpty()) {
            setFeedback("No downloadable files returned by Base44.", isError = true)
            return
        }
        dataFiles.forEach { file ->
            val id = file.stableId()
            if (downloadStates.value[id] !is DownloadState.Verified) {
                downloadManager.startDownload(file)
            }
        }
    }

    fun cancelOngoingDownloads() {
        downloadManager.cancelAllDownloads()
    }

    fun onExportToSafClicked() {
        if (_uiState.value.isKeyExpired || sessionState.value !is SessionState.Active) {
            setFeedback("KEY EXPIRED", isError = true)
            return
        }
        val verifiedFiles = getVerifiedFilesForCurrentTarget()
        if (verifiedFiles.isEmpty()) {
            setFeedback("Download and SHA-256 verify files before exporting to SAF.", isError = true)
            return
        }

        val safStatus = safStorageManager.getCurrentSafFolderStatus()
        _uiState.update { it.copy(safFolderStatus = safStatus) }
        if (!safStatus.hasWritePermission || safStatus.isRestrictedByScopedStorage) {
            _uiState.update { it.copy(pendingSafExportAfterPicker = true) }
            viewModelScope.launch {
                _events.emit(UiOneTimeEvent.RequestSafFolderPicker)
            }
            return
        }

        exportVerifiedFilesToSaf(verifiedFiles)
    }

    private fun getVerifiedFilesForCurrentTarget(): List<Pair<com.kar7.pro.download.VerifiedFileRecord, java.io.File>> {
        val dataFiles = effectiveTargetInfo()?.dataFiles.orEmpty()
        val states = downloadStates.value
        return dataFiles.mapNotNull { file ->
            val verified = states[file.stableId()] as? DownloadState.Verified
            if (verified != null && verified.file.exists()) {
                verified.record to verified.file
            } else {
                null
            }
        }
    }

    private fun exportVerifiedFilesToSaf(
        verifiedFiles: List<Pair<com.kar7.pro.download.VerifiedFileRecord, java.io.File>>
    ) {
        viewModelScope.launch {
            val total = verifiedFiles.size.coerceAtLeast(1)
            _uiState.update {
                it.copy(
                    isExportingToSaf = true,
                    exportProgressPercent = 1,
                    dashboardFeedbackMessage = null
                )
            }

            var exportedCount = 0
            for ((index, pair) in verifiedFiles.withIndex()) {
                val (record, file) = pair
                when (val result = safStorageManager.exportVerifiedFileToSelectedFolder(file, record.fileName, record.mimeType)) {
                    is SafExportResult.Success -> {
                        exportedCount++
                        val pct = (((index + 1) * 100) / total).coerceIn(1, 100)
                        _uiState.update { it.copy(exportProgressPercent = pct) }
                    }
                    is SafExportResult.Restricted -> {
                        _uiState.update {
                            it.copy(
                                isExportingToSaf = false,
                                exportProgressPercent = null,
                                safFolderStatus = safStorageManager.getCurrentSafFolderStatus()
                            )
                        }
                        setFeedback(result.explanation, isError = true)
                        return@launch
                    }
                    is SafExportResult.Failed -> {
                        _uiState.update {
                            it.copy(
                                isExportingToSaf = false,
                                exportProgressPercent = null
                            )
                        }
                        setFeedback(result.errorMessage, isError = true)
                        return@launch
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isExportingToSaf = false,
                    exportProgressPercent = null
                )
            }
            val folderName = _uiState.value.safFolderStatus.displayName ?: "selected SAF folder"
            setFeedback("Exported $exportedCount verified file(s) to $folderName.", isError = false)
        }
    }

    /**
     * OPEN button action: completely independent of downloads, SAF export, or ADB state.
     * Uses Android PackageManager (getLaunchIntentForPackage + launcher fallback + PackageInfo)
     * against the live admin-configured package name.
     */
    fun onOpenTargetGameClicked() {
        val targetInfo = effectiveTargetInfo()
        val pkg = targetInfo?.packageName
        when (val resolution = gameImportManager.resolveTargetLaunch(pkg)) {
            is TargetLaunchResult.Ready -> {
                viewModelScope.launch {
                    _events.emit(UiOneTimeEvent.LaunchTargetGame(resolution.intent))
                }
            }
            is TargetLaunchResult.NotConfigured -> {
                setFeedback(resolution.message, isError = true)
            }
            is TargetLaunchResult.NotLaunchable -> {
                setFeedback(resolution.message, isError = true)
            }
            is TargetLaunchResult.NotInstalled -> {
                setFeedback(resolution.message, isError = true)
            }
        }
    }

    fun onTargetGameLaunchFailed(reason: String? = null) {
        val pkg = effectiveTargetInfo()?.packageName?.takeIf { it.isNotBlank() }
        val message = when {
            !reason.isNullOrBlank() -> "Unable to launch ${pkg ?: "target app"}: $reason"
            pkg != null -> "Target app ($pkg) is not installed or could not be launched."
            else -> "Target app could not be launched."
        }
        setFeedback(message, isError = true)
    }

    /**
     * Direct ADB CONNECT flow:
     * 1. Check Developer Options & Wireless Debugging.
     * 2. If Developer Options disabled -> show "PLEASE ENABLE DEVELOPER OPTIONS FIRST".
     * 3. On Android 13+, request POST_NOTIFICATIONS if needed (never on Android 11/12).
     * 4. Show persistent KAR7 PRO RemoteInput pairing notification and open Wireless Debugging / Developer Options.
     */
    fun onAdbConnectClicked() {
        val env = adbConnectManager.inspectEnvironment(_uiState.value.adbEnvironment?.pairingStatus)
        if (!env.developerOptionsEnabled) {
            _uiState.update {
                it.copy(
                    adbEnvironment = env.copy(pairingStatus = AdbPairingStatus.DeveloperOptionsDisabled()),
                    dashboardFeedbackMessage = "PLEASE ENABLE DEVELOPER OPTIONS FIRST",
                    dashboardFeedbackIsError = true
                )
            }
            return
        }

        val app = getApplication<Application>()
        if (AdbPairingNotificationManager.needsRuntimeNotificationRequest(app)) {
            viewModelScope.launch {
                _events.emit(UiOneTimeEvent.RequestNotificationPermission(launchAdbFlowAfterGrant = true))
            }
            return
        }

        if (!AdbPairingNotificationManager.hasNotificationPermission(app)) {
            setFeedback(
                "Notifications are disabled for KAR7 PRO. Enable notifications in System Settings -> Apps -> KAR7 PRO to use the ADB pairing prompt.",
                isError = true
            )
            return
        }

        startAdbPairingNotificationAndOpenSettings()
    }

    fun onNotificationPermissionResult(granted: Boolean, launchAdbFlowAfterGrant: Boolean) {
        refreshEnvironmentDiagnostics()
        if (granted) {
            _uiState.update { it.copy(notificationDeniedMessage = null) }
            if (launchAdbFlowAfterGrant) {
                startAdbPairingNotificationAndOpenSettings()
            } else {
                setFeedback("Notification permission granted.", isError = false)
            }
        } else {
            val msg = "Notification permission denied. Open Settings -> Apps -> KAR7 PRO -> Notifications and enable notifications to enter your pairing code."
            _uiState.update { it.copy(notificationDeniedMessage = msg) }
            setFeedback(msg, isError = true)
        }
    }

    fun requestNotificationPermissionFromPermissionsModal() {
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            AdbPairingNotificationManager.needsRuntimeNotificationRequest(app)
        ) {
            viewModelScope.launch {
                _events.emit(UiOneTimeEvent.RequestNotificationPermission(launchAdbFlowAfterGrant = false))
            }
        } else {
            openAppNotificationSettings()
        }
    }

    fun openAppNotificationSettings() {
        val app = getApplication<Application>()
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", app.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        viewModelScope.launch {
            _events.emit(UiOneTimeEvent.LaunchAppNotificationSettings(intent))
        }
    }

    private fun startAdbPairingNotificationAndOpenSettings() {
        val app = getApplication<Application>()
        val shown = AdbPairingNotificationManager.showPairingPromptNotification(app)
        val env = adbConnectManager.inspectEnvironment(AdbPairingStatus.AwaitingNotificationInput)
        val wirelessText = if (env.wirelessDebuggingEnabled) {
            "Wireless Debugging is ON. Tap 'Pair device with pairing code' in Android settings, then enter the 6-digit code in KAR7 PRO's notification to check endpoint availability."
        } else {
            "Enable Wireless Debugging in Developer Settings, tap 'Pair device with pairing code', and enter the 6-digit code in KAR7 PRO's notification."
        }

        _uiState.update {
            it.copy(
                adbEnvironment = env,
                dashboardFeedbackMessage = if (shown) wirelessText else "Unable to post pairing notification. Check app notification settings.",
                dashboardFeedbackIsError = !shown
            )
        }

        val (primary, fallback) = adbConnectManager.createBestDeveloperSettingsIntent()
        viewModelScope.launch {
            _events.emit(UiOneTimeEvent.LaunchDeveloperOptions(primary, fallback))
        }
    }

    fun handleAdbPairingReply(rawCodeInput: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    adbEnvironment = adbConnectManager.inspectEnvironment(AdbPairingStatus.PairingInProgress)
                )
            }
            val updatedEnv = adbConnectManager.processPairingCode(rawCodeInput)
            AdbPairingReceiver.updateNotificationFromStatus(getApplication(), updatedEnv.pairingStatus)

            val (msg, isError) = when (val status = updatedEnv.pairingStatus) {
                is AdbPairingStatus.DeveloperOptionsDisabled -> status.message to true
                is AdbPairingStatus.ValidationError -> status.message to true
                is AdbPairingStatus.EndpointUnavailable -> status.message to true
                is AdbPairingStatus.HostPairingRequired -> status.message to false
                else -> "ADB status updated." to false
            }

            _uiState.update {
                it.copy(
                    adbEnvironment = updatedEnv,
                    dashboardFeedbackMessage = msg,
                    dashboardFeedbackIsError = isError
                )
            }
        }
    }

    fun openPermissionsModal() {
        refreshEnvironmentDiagnostics()
        _uiState.update { it.copy(showPermissionsModal = true) }
    }

    fun closePermissionsModal() {
        _uiState.update { it.copy(showPermissionsModal = false) }
    }

    fun onSafTreeSelected(uri: Uri?) {
        val shouldAutoExport = _uiState.value.pendingSafExportAfterPicker
        val status = safStorageManager.handleTreeUriSelected(uri)
        _uiState.update {
            it.copy(
                safFolderStatus = status,
                pendingSafExportAfterPicker = false
            )
        }
        refreshEnvironmentDiagnostics()

        if (shouldAutoExport && status.hasWritePermission && !status.isRestrictedByScopedStorage) {
            val verified = getVerifiedFilesForCurrentTarget()
            if (verified.isNotEmpty()) {
                exportVerifiedFilesToSaf(verified)
                return
            }
        }

        viewModelScope.launch {
            val msg = status.limitationMessage
                ?: status.displayName?.let { "SAF Folder Selected: $it" }
                ?: "Folder selection unchanged."
            _events.emit(UiOneTimeEvent.ShowMessage(msg))
        }
    }

    fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(UiOneTimeEvent.ShowMessage(message))
        }
    }

    fun onForeground() {
        sessionManager.onAppForegrounded()
        viewModelScope.launch {
            when (val res = repository.fetchSettings()) {
                is AppResult.Success -> _uiState.update { it.copy(settingsData = res.data) }
                is AppResult.Failure -> Unit
            }
            refreshEnvironmentDiagnostics()
        }
    }

    fun onBackground() {
        sessionManager.onAppBackgrounded()
    }

    private fun setFeedback(message: String, isError: Boolean) {
        _uiState.update {
            it.copy(
                dashboardFeedbackMessage = message,
                dashboardFeedbackIsError = isError
            )
        }
    }
}
