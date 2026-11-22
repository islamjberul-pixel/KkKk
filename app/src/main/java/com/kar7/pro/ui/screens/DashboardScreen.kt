package com.kar7.pro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kar7.pro.device.AdbEnvironmentState
import com.kar7.pro.device.AdbPairingStatus
import com.kar7.pro.gamedata.TargetPackageDiagnostics
import com.kar7.pro.models.TargetGameInfo
import com.kar7.pro.storage.PermissionsSummary
import com.kar7.pro.ui.DataVaultState
import com.kar7.pro.ui.components.BadgeTone
import com.kar7.pro.ui.components.Kar7ErrorBanner
import com.kar7.pro.ui.components.Kar7GlassCard
import com.kar7.pro.ui.components.Kar7GoldButton
import com.kar7.pro.ui.components.Kar7LogoEmblem
import com.kar7.pro.ui.components.Kar7OutlinedGoldButton
import com.kar7.pro.ui.components.Kar7ResponsiveContainer
import com.kar7.pro.ui.components.Kar7StatusBadge
import com.kar7.pro.ui.theme.JetBrainsMonoFontFamily
import com.kar7.pro.ui.theme.Kar7Colors
import com.kar7.pro.util.Formatters

@Composable
fun DashboardScreen(
    targetInfo: TargetGameInfo?,
    targetDiagnostics: TargetPackageDiagnostics?,
    isRefreshingTarget: Boolean,
    remainingSeconds: Long?,
    isKeyExpired: Boolean,
    dataVaultState: DataVaultState,
    permissionsSummary: PermissionsSummary?,
    adbEnvironment: AdbEnvironmentState?,
    feedbackMessage: String?,
    feedbackIsError: Boolean,
    onRefreshTargetClick: () -> Unit,
    onPermissionsClick: () -> Unit,
    onDownloadDataClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onExportToSafClick: () -> Unit,
    onOpenTargetClick: () -> Unit,
    onAdbConnectClick: () -> Unit,
    onSwitchKeyClick: () -> Unit
) {
    val targetPackage = targetInfo?.packageName?.takeIf { it.isNotBlank() } ?: "Not Configured"
    val targetDisplayName = targetDiagnostics?.appLabel?.takeIf { it.isNotBlank() }
        ?: targetInfo?.displayName()
        ?: "Target App"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Kar7Colors.DeepBlack,
                        Kar7Colors.DarkCharcoal,
                        Kar7Colors.DeepBlack
                    )
                )
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isKeyExpired) Kar7Colors.DangerRed.copy(alpha = 0.14f) else Kar7Colors.GoldGlow,
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.34f),
                        radius = size.maxDimension * 0.55f
                    )
                )
            }
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Kar7ResponsiveContainer(maxWidth = 580.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // 1. Brand Header + Admin Package Indicator + Refresh + Logout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Kar7LogoEmblem(size = 42.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "KAR7 PRO",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Kar7Colors.MetallicGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$targetDisplayName • $targetPackage",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Kar7Colors.LightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("dashboard_target_package_text")
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onRefreshTargetClick,
                                enabled = !isRefreshingTarget,
                                modifier = Modifier.testTag("dashboard_refresh_target_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Refresh admin target package",
                                    tint = if (isRefreshingTarget) Kar7Colors.DimGray else Kar7Colors.MetallicGold
                                )
                            }
                            IconButton(
                                onClick = onSwitchKeyClick,
                                modifier = Modifier.testTag("dashboard_logout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = "Logout or switch key",
                                    tint = Kar7Colors.LightGray
                                )
                            }
                        }
                    }

                    // 2. Prominent PERMISSIONS Button at the Top
                    Kar7GlassCard(
                        onClick = onPermissionsClick,
                        borderColor = if (permissionsSummary?.allRequiredReady == true) {
                            Kar7Colors.SubtleGoldBorder
                        } else {
                            Kar7Colors.WarningAmber.copy(alpha = 0.65f)
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.testTag("dashboard_permissions_button")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = null,
                                    tint = Kar7Colors.MetallicGold,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "PERMISSIONS",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Kar7Colors.PureWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when {
                                            permissionsSummary?.safFolderSelected == true ->
                                                "Internet, Alerts & SAF Folder Ready"
                                            permissionsSummary?.allRequiredReady == true ->
                                                "Inspect Internet, Alerts, SAF & Target App"
                                            else ->
                                                "Review permission & app detection status"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Kar7Colors.LightGray
                                    )
                                }
                            }
                            Kar7StatusBadge(
                                label = if (permissionsSummary?.allRequiredReady == true) "CHECKED" else "REVIEW",
                                tone = if (permissionsSummary?.allRequiredReady == true) BadgeTone.SUCCESS else BadgeTone.WARNING
                            )
                        }
                    }

                    // 3. Key Expiry Countdown Card
                    Kar7GlassCard(
                        borderColor = if (isKeyExpired) {
                            Kar7Colors.DangerRed.copy(alpha = 0.8f)
                        } else {
                            Kar7Colors.StrongGoldBorder
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = null,
                                    tint = if (isKeyExpired) Kar7Colors.DangerRed else Kar7Colors.MetallicGold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "KEY EXPIRY COUNTDOWN",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isKeyExpired) Kar7Colors.DangerRed else Kar7Colors.SoftGold
                                )
                            }
                            Kar7StatusBadge(
                                label = if (isKeyExpired) "KEY EXPIRED" else "ACTIVE",
                                tone = if (isKeyExpired) BadgeTone.DANGER else BadgeTone.SUCCESS
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isKeyExpired) "KEY EXPIRED" else Formatters.formatDuration(remainingSeconds),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isKeyExpired) Kar7Colors.DangerRed else Kar7Colors.PureWhite,
                            modifier = Modifier.testTag("dashboard_countdown_text")
                        )

                        if (isKeyExpired) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Kar7GoldButton(
                                text = "Authenticate New Key",
                                onClick = onSwitchKeyClick,
                                modifier = Modifier.testTag("expired_relogin_button")
                            )
                        }
                    }

                    // 4. Verified Base44 Download & SAF Export Card (No third-party game injection)
                    Kar7GlassCard(
                        borderColor = when {
                            isKeyExpired -> Kar7Colors.DangerRed.copy(alpha = 0.7f)
                            dataVaultState.verifiedCount == dataVaultState.totalFilesCount &&
                                dataVaultState.totalFilesCount > 0 -> Kar7Colors.SuccessGreen.copy(alpha = 0.65f)
                            else -> Kar7Colors.SubtleGoldBorder
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BASE44 FILES & SAF EXPORT",
                                style = MaterialTheme.typography.labelLarge,
                                color = Kar7Colors.SoftGold
                            )
                            Kar7StatusBadge(
                                label = "${dataVaultState.verifiedCount}/${dataVaultState.totalFilesCount} VERIFIED",
                                tone = if (dataVaultState.totalFilesCount > 0 &&
                                    dataVaultState.verifiedCount == dataVaultState.totalFilesCount
                                ) BadgeTone.SUCCESS else BadgeTone.GOLD
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = dataVaultState.statusHeadline,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isKeyExpired) Kar7Colors.DangerRed else Kar7Colors.PureWhite,
                            modifier = Modifier.testTag("vault_status_headline")
                        )

                        if (!dataVaultState.detailMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dataVaultState.detailMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isKeyExpired) Kar7Colors.DangerRed else Kar7Colors.LightGray,
                                modifier = Modifier.testTag("vault_detail_message")
                            )
                        }

                        if (dataVaultState.progressPercent != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { (dataVaultState.progressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .testTag("vault_progress_bar"),
                                color = Kar7Colors.MetallicGold,
                                trackColor = Kar7Colors.ElevatedSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${dataVaultState.progressPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Kar7Colors.SoftGold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (!isKeyExpired && dataVaultState.canDownloadData) {
                            Kar7OutlinedGoldButton(
                                text = "Download & Verify Data",
                                icon = Icons.Outlined.CloudDownload,
                                onClick = onDownloadDataClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("download_verify_data_button")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        } else if (dataVaultState.isDownloadingOrVerifying) {
                            Kar7OutlinedGoldButton(
                                text = "Cancel Download",
                                icon = Icons.Outlined.Cancel,
                                onClick = onCancelDownloadClick,
                                borderColor = Kar7Colors.DangerRed.copy(alpha = 0.65f),
                                contentColor = Kar7Colors.DangerRed,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("cancel_download_button")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Kar7OutlinedGoldButton(
                            text = if (dataVaultState.safFolderSelected) {
                                "Export Verified Files to SAF"
                            } else {
                                "Choose SAF Folder & Export"
                            },
                            icon = Icons.Outlined.CreateNewFolder,
                            enabled = dataVaultState.canExportToSaf,
                            onClick = onExportToSafClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_saf_button")
                        )
                    }

                    // Feedback Banner
                    AnimatedVisibility(visible = !feedbackMessage.isNullOrBlank()) {
                        val msg = feedbackMessage.orEmpty()
                        if (feedbackIsError) {
                            Kar7ErrorBanner(
                                message = msg,
                                modifier = Modifier.testTag("dashboard_feedback_banner")
                            )
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dashboard_feedback_banner"),
                                shape = RoundedCornerShape(14.dp),
                                color = Kar7Colors.SuccessSurface,
                                border = BorderStroke(1.dp, Kar7Colors.SuccessGreen.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = Kar7Colors.SuccessGreen
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Kar7Colors.PureWhite
                                    )
                                }
                            }
                        }
                    }

                    // 5. OPEN & ADB CONNECT Action Row (OPEN works independently of download, export, or ADB)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Kar7GoldButton(
                            text = "OPEN",
                            icon = Icons.AutoMirrored.Outlined.OpenInNew,
                            enabled = true,
                            onClick = onOpenTargetClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_open_button")
                        )
                        Kar7OutlinedGoldButton(
                            text = when (adbEnvironment?.pairingStatus) {
                                AdbPairingStatus.AwaitingNotificationInput -> "ADB WAITING…"
                                is AdbPairingStatus.HostPairingRequired -> "ADB HOST REQ"
                                else -> "ADB CONNECT"
                            },
                            icon = Icons.Outlined.DeveloperMode,
                            enabled = true,
                            borderColor = when (adbEnvironment?.pairingStatus) {
                                AdbPairingStatus.AwaitingNotificationInput -> Kar7Colors.MetallicGold
                                is AdbPairingStatus.HostPairingRequired -> Kar7Colors.WarningAmber
                                else -> Kar7Colors.StrongGoldBorder
                            },
                            contentColor = Kar7Colors.SoftGold,
                            onClick = onAdbConnectClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("dashboard_adb_connect_button")
                        )
                    }
                }
            }
        }
    }
}
