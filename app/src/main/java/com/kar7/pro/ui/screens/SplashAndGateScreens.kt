package com.kar7.pro.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.kar7.pro.ui.components.BadgeTone
import com.kar7.pro.ui.components.Kar7GlassCard
import com.kar7.pro.ui.components.Kar7GoldButton
import com.kar7.pro.ui.components.Kar7LogoEmblem
import com.kar7.pro.ui.components.Kar7ResponsiveContainer
import com.kar7.pro.ui.components.Kar7StatusBadge
import com.kar7.pro.ui.theme.Kar7Colors

@Composable
fun SplashScreen(
    statusText: String
) {
    val transition = rememberInfiniteTransition(label = "kar7_splash_ring")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "kar7_ring_rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Kar7Colors.DeepBlack)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(124.dp)) {
                    drawCircle(
                        color = Kar7Colors.SubtleGoldBorder,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawArc(
                        color = Kar7Colors.MetallicGold,
                        startAngle = sweepAngle,
                        sweepAngle = 95f,
                        useCenter = false,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Kar7LogoEmblem(size = 92.dp)
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = Kar7Colors.PureWhite,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(id = R.string.app_tagline),
                style = MaterialTheme.typography.labelSmall,
                color = Kar7Colors.MetallicGold
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = Kar7Colors.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("splash_status_text")
            )
        }
    }
}

@Composable
fun AppGateScreen(
    title: String,
    message: String,
    badgeLabel: String,
    badgeTone: BadgeTone,
    icon: ImageVector,
    actionLabel: String = "Retry Connection",
    onActionClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Kar7Colors.DeepBlack)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Kar7ResponsiveContainer(maxWidth = 480.dp) {
            Kar7GlassCard {
                Column(
                    modifier = Modifier.fillMaxSize(0f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Kar7LogoEmblem(size = 64.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Kar7StatusBadge(label = badgeLabel, tone = badgeTone)
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Kar7Colors.MetallicGold,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Kar7Colors.PureWhite,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Kar7Colors.LightGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Installed Version: ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Kar7Colors.DimGray
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Kar7GoldButton(
                        text = actionLabel,
                        icon = Icons.Outlined.Refresh,
                        onClick = onActionClick,
                        modifier = Modifier.testTag("gate_retry_button")
                    )
                }
            }
        }
    }
}

@Composable
fun AppDisabledScreen(onRetry: () -> Unit) {
    AppGateScreen(
        title = "Application Disabled",
        message = "KAR7 PRO access has been temporarily disabled by the administrator.",
        badgeLabel = "SERVICE DISABLED",
        badgeTone = BadgeTone.DANGER,
        icon = Icons.Outlined.Block,
        onActionClick = onRetry
    )
}

@Composable
fun MaintenanceScreen(message: String, onRetry: () -> Unit) {
    AppGateScreen(
        title = "Scheduled Maintenance",
        message = message,
        badgeLabel = "MAINTENANCE MODE",
        badgeTone = BadgeTone.WARNING,
        icon = Icons.Outlined.Build,
        onActionClick = onRetry
    )
}

@Composable
fun UpdateRequiredScreen(message: String, minVersion: String, onRetry: () -> Unit) {
    AppGateScreen(
        title = "Update Required",
        message = "$message\n\nMinimum supported version: $minVersion",
        badgeLabel = "OUTDATED CLIENT",
        badgeTone = BadgeTone.WARNING,
        icon = Icons.Outlined.SystemUpdate,
        actionLabel = "Check Again",
        onActionClick = onRetry
    )
}

@Composable
fun InitErrorScreen(message: String, onRetry: () -> Unit) {
    AppGateScreen(
        title = "Connection Unavailable",
        message = message,
        badgeLabel = "OFFLINE / ERROR",
        badgeTone = BadgeTone.DANGER,
        icon = Icons.Outlined.CloudOff,
        onActionClick = onRetry
    )
}

@Composable
fun SessionExpiredScreen(
    reason: String,
    onReturnToLogin: () -> Unit
) {
    AppGateScreen(
        title = stringResource(id = R.string.session_expired_title),
        message = reason.ifBlank { stringResource(id = R.string.session_expired_message) },
        badgeLabel = "SESSION EXPIRED",
        badgeTone = BadgeTone.DANGER,
        icon = Icons.Outlined.LockClock,
        actionLabel = stringResource(id = R.string.action_return_login),
        onActionClick = onReturnToLogin
    )
}
