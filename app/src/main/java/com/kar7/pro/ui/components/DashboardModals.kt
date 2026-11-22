package com.kar7.pro.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kar7.pro.storage.PermissionsSummary
import com.kar7.pro.storage.SafFolderStatus
import com.kar7.pro.ui.theme.Kar7Colors

@Composable
fun PermissionsModalDialog(
    summary: PermissionsSummary?,
    safStatus: SafFolderStatus,
    notificationDeniedMessage: String?,
    onRequestNotifications: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onSelectSafFolder: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Kar7Colors.DarkCharcoal,
        titleContentColor = Kar7Colors.PureWhite,
        textContentColor = Kar7Colors.LightGray,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = Kar7Colors.MetallicGold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Permissions & Status", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("permissions_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh permission status",
                        tint = Kar7Colors.MetallicGold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "KAR7 PRO checks only real permissions and visibility required for Base44 API access, pairing notifications, SAF folder export, and launching the configured app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Kar7Colors.LightGray
                )

                summary?.items?.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Kar7Colors.GraphiteSurface,
                        border = BorderStroke(
                            1.dp,
                            if (item.isGranted) Kar7Colors.SuccessGreen.copy(alpha = 0.45f)
                            else Kar7Colors.SubtleGoldBorder
                        ),
                        modifier = Modifier.testTag("permission_item_${item.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Kar7Colors.PureWhite,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (item.isGranted) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                                    contentDescription = if (item.isGranted) "Granted" else "Not granted",
                                    tint = if (item.isGranted) Kar7Colors.SuccessGreen else Kar7Colors.WarningAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Status: ${item.statusLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.isGranted) Kar7Colors.SuccessGreen else Kar7Colors.SoftGold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = Kar7Colors.LightGray
                            )
                        }
                    }
                }

                if (!notificationDeniedMessage.isNullOrBlank()) {
                    Kar7ErrorBanner(message = notificationDeniedMessage)
                }

                if (summary?.needsRuntimeNotificationRequest == true) {
                    Kar7OutlinedGoldButton(
                        text = "Allow Notifications (Android 13+)",
                        icon = Icons.Outlined.NotificationsActive,
                        onClick = onRequestNotifications,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("permissions_request_notifications_button")
                    )
                } else if (summary?.notificationPermissionGranted == false) {
                    Kar7OutlinedGoldButton(
                        text = "Open Notification Settings",
                        icon = Icons.Outlined.NotificationsActive,
                        onClick = onOpenAppNotificationSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("permissions_open_notification_settings_button")
                    )
                }

                if (!safStatus.limitationMessage.isNullOrBlank()) {
                    Kar7ErrorBanner(message = safStatus.limitationMessage)
                }

                Kar7OutlinedGoldButton(
                    text = if (safStatus.hasWritePermission) "Change SAF Folder" else "Select SAF Folder",
                    icon = Icons.Outlined.CreateNewFolder,
                    onClick = onSelectSafFolder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("permissions_select_saf_button")
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Kar7Colors.MetallicGold, fontWeight = FontWeight.Bold)
            }
        }
    )
}
