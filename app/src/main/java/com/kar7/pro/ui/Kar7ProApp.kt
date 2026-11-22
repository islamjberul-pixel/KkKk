package com.kar7.pro.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kar7.pro.session.SessionState
import com.kar7.pro.ui.components.PermissionsModalDialog
import com.kar7.pro.ui.screens.AppDisabledScreen
import com.kar7.pro.ui.screens.DashboardScreen
import com.kar7.pro.ui.screens.InitErrorScreen
import com.kar7.pro.ui.screens.LoginScreen
import com.kar7.pro.ui.screens.MaintenanceScreen
import com.kar7.pro.ui.screens.SplashScreen
import com.kar7.pro.ui.screens.UpdateRequiredScreen
import com.kar7.pro.ui.theme.Kar7Colors
import com.kar7.pro.ui.theme.Kar7ProTheme
import com.kar7.pro.util.WhatsappHelper
import com.kar7.pro.util.WhatsappLaunchResult

@Composable
fun Kar7ProApp(
    viewModel: Kar7ViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var launchAdbAfterNotificationGrant by remember { mutableStateOf(false) }

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        viewModel.onSafTreeSelected(uri)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val launchAdb = launchAdbAfterNotificationGrant
        launchAdbAfterNotificationGrant = false
        viewModel.onNotificationPermissionResult(granted, launchAdb)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onForeground()
                Lifecycle.Event.ON_PAUSE -> viewModel.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiOneTimeEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is UiOneTimeEvent.LaunchTargetGame -> {
                    runCatching {
                        context.startActivity(event.intent)
                    }.onFailure { err ->
                        viewModel.onTargetGameLaunchFailed(err.message)
                    }
                }
                is UiOneTimeEvent.LaunchDeveloperOptions -> {
                    runCatching {
                        context.startActivity(event.intent)
                    }.onFailure {
                        runCatching { context.startActivity(event.fallbackIntent) }
                            .onFailure {
                                snackbarHostState.showSnackbar("Unable to open Android Developer Settings.")
                            }
                    }
                }
                is UiOneTimeEvent.RequestNotificationPermission -> {
                    launchAdbAfterNotificationGrant = event.launchAdbFlowAfterGrant
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                is UiOneTimeEvent.LaunchAppNotificationSettings -> {
                    runCatching {
                        context.startActivity(event.intent)
                    }.onFailure {
                        snackbarHostState.showSnackbar("Unable to open app notification settings.")
                    }
                }
                UiOneTimeEvent.RequestSafFolderPicker -> {
                    openDocumentTreeLauncher.launch(null)
                }
            }
        }
    }

    fun launchBuyKey() {
        when (val res = WhatsappHelper.openBuyKey(context, viewModel.currentWhatsappInfo())) {
            WhatsappLaunchResult.Launched -> Unit
            is WhatsappLaunchResult.Unavailable -> viewModel.emitMessage(res.reason)
        }
    }

    fun launchSupport() {
        when (val res = WhatsappHelper.openSupport(context, viewModel.currentWhatsappInfo())) {
            WhatsappLaunchResult.Launched -> Unit
            is WhatsappLaunchResult.Unavailable -> viewModel.emitMessage(res.reason)
        }
    }

    Kar7ProTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val route = uiState.startupRoute) {
                AppStartupRoute.Splash -> {
                    SplashScreen(statusText = uiState.splashStatusText)
                }

                AppStartupRoute.AppDisabled -> {
                    AppDisabledScreen(onRetry = viewModel::runAppInitialization)
                }

                is AppStartupRoute.Maintenance -> {
                    MaintenanceScreen(
                        message = route.message,
                        onRetry = viewModel::runAppInitialization
                    )
                }

                is AppStartupRoute.UpdateRequired -> {
                    UpdateRequiredScreen(
                        message = route.message,
                        minVersion = route.minVersion,
                        onRetry = viewModel::runAppInitialization
                    )
                }

                is AppStartupRoute.InitError -> {
                    InitErrorScreen(
                        message = route.message,
                        onRetry = viewModel::runAppInitialization
                    )
                }

                AppStartupRoute.Login -> {
                    LoginScreen(
                        deviceIdentity = uiState.deviceIdentity,
                        isLoggingIn = uiState.isLoggingIn,
                        errorMessage = uiState.loginError,
                        onKeyChanged = viewModel::clearLoginError,
                        onLoginClick = viewModel::loginWithKey,
                        onBuyKeyClick = ::launchBuyKey,
                        onSupportClick = ::launchSupport
                    )
                }

                AppStartupRoute.Dashboard -> {
                    val active = sessionState as? SessionState.Active
                    val displaySession = active?.session ?: uiState.lastValidSession
                    val remainingSeconds = active?.remainingSeconds
                    val targetInfo = viewModel.effectiveTargetInfo(displaySession, uiState)
                    val dataVaultState = viewModel.computeDataVaultState(
                        session = displaySession,
                        remainingSeconds = remainingSeconds,
                        downloads = downloadStates,
                        ui = uiState
                    )

                    DashboardScreen(
                        targetInfo = targetInfo,
                        targetDiagnostics = uiState.targetDiagnostics,
                        isRefreshingTarget = uiState.isRefreshingTarget,
                        remainingSeconds = remainingSeconds,
                        isKeyExpired = uiState.isKeyExpired,
                        dataVaultState = dataVaultState,
                        permissionsSummary = uiState.permissionsSummary,
                        adbEnvironment = uiState.adbEnvironment,
                        feedbackMessage = uiState.dashboardFeedbackMessage,
                        feedbackIsError = uiState.dashboardFeedbackIsError,
                        onRefreshTargetClick = viewModel::refreshAdminTargetConfig,
                        onPermissionsClick = viewModel::openPermissionsModal,
                        onDownloadDataClick = viewModel::downloadRequiredDataFiles,
                        onCancelDownloadClick = viewModel::cancelOngoingDownloads,
                        onExportToSafClick = viewModel::onExportToSafClicked,
                        onOpenTargetClick = viewModel::onOpenTargetGameClicked,
                        onAdbConnectClick = viewModel::onAdbConnectClicked,
                        onSwitchKeyClick = viewModel::logoutToLogin
                    )

                    if (uiState.showPermissionsModal) {
                        PermissionsModalDialog(
                            summary = uiState.permissionsSummary,
                            safStatus = uiState.safFolderStatus,
                            notificationDeniedMessage = uiState.notificationDeniedMessage,
                            onRequestNotifications = viewModel::requestNotificationPermissionFromPermissionsModal,
                            onOpenAppNotificationSettings = viewModel::openAppNotificationSettings,
                            onSelectSafFolder = { openDocumentTreeLauncher.launch(null) },
                            onRefresh = { viewModel.refreshAdminTargetConfig() },
                            onDismiss = viewModel::closePermissionsModal
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(16.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Kar7Colors.ElevatedSurface,
                    contentColor = Kar7Colors.PureWhite,
                    actionColor = Kar7Colors.MetallicGold
                )
            }
        }
    }
}
