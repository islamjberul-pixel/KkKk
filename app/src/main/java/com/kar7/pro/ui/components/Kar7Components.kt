package com.kar7.pro.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.kar7.pro.ui.theme.JetBrainsMonoFontFamily
import com.kar7.pro.ui.theme.Kar7Colors

@Composable
fun Kar7LogoEmblem(
    size: Dp = 76.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0B0B0F))
            .border(1.5.dp, Kar7Colors.StrongGoldBorder, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_kar7_launcher_1795318277649),
            contentDescription = stringResource(id = R.string.cd_app_logo),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Kar7TopBar(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Kar7Colors.PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Kar7Colors.SoftGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("top_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.cd_navigate_back),
                        tint = Kar7Colors.MetallicGold
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Kar7Colors.DeepBlack,
            titleContentColor = Kar7Colors.PureWhite
        )
    )
}

@Composable
fun Kar7GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Kar7Colors.SubtleGoldBorder,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = Kar7Colors.GlassSurface,
            border = BorderStroke(1.dp, borderColor),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = Kar7Colors.GlassSurface,
            border = BorderStroke(1.dp, borderColor),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun Kar7GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Kar7Colors.MetallicGold,
            contentColor = Kar7Colors.DeepBlack,
            disabledContainerColor = Kar7Colors.MutedGold.copy(alpha = 0.45f),
            disabledContentColor = Kar7Colors.LightGray
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Kar7Colors.DeepBlack,
                strokeWidth = 2.2.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun Kar7OutlinedGoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    borderColor: Color = Kar7Colors.StrongGoldBorder,
    contentColor: Color = Kar7Colors.SoftGold
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (enabled) borderColor else Kar7Colors.DimGray.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = Kar7Colors.DimGray
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

enum class BadgeTone { GOLD, SUCCESS, WARNING, DANGER, NEUTRAL }

@Composable
fun Kar7StatusBadge(
    label: String,
    tone: BadgeTone = BadgeTone.GOLD,
    modifier: Modifier = Modifier
) {
    val (bg, border, fg) = when (tone) {
        BadgeTone.GOLD -> Triple(
            Kar7Colors.MetallicGold.copy(alpha = 0.14f),
            Kar7Colors.StrongGoldBorder,
            Kar7Colors.SoftGold
        )
        BadgeTone.SUCCESS -> Triple(
            Kar7Colors.SuccessSurface,
            Kar7Colors.SuccessGreen.copy(alpha = 0.55f),
            Kar7Colors.SuccessGreen
        )
        BadgeTone.WARNING -> Triple(
            Kar7Colors.WarningSurface,
            Kar7Colors.WarningAmber.copy(alpha = 0.55f),
            Kar7Colors.WarningAmber
        )
        BadgeTone.DANGER -> Triple(
            Kar7Colors.DangerSurface,
            Kar7Colors.DangerRed.copy(alpha = 0.55f),
            Kar7Colors.DangerRed
        )
        BadgeTone.NEUTRAL -> Triple(
            Kar7Colors.ElevatedSurface,
            Kar7Colors.DimGray.copy(alpha = 0.5f),
            Kar7Colors.LightGray
        )
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun Kar7InfoRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    valueColor: Color = Kar7Colors.PureWhite,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Kar7Colors.LightGray,
            modifier = Modifier.weight(0.42f)
        )
        Row(
            modifier = Modifier.weight(0.58f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = if (monospace) {
                    MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                } else {
                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                },
                color = valueColor,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.width(6.dp))
                trailing()
            }
        }
    }
}

@Composable
fun Kar7ErrorBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Kar7Colors.DangerSurface,
        border = BorderStroke(1.dp, Kar7Colors.DangerRed.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = Kar7Colors.DangerRed,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Kar7Colors.PureWhite
            )
        }
    }
}

@Composable
fun Kar7EmptyState(
    title: String,
    description: String,
    icon: ImageVector = Icons.Outlined.Inbox,
    modifier: Modifier = Modifier
) {
    Kar7GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Kar7Colors.MetallicGold,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Kar7Colors.PureWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Kar7Colors.LightGray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun Kar7ResponsiveContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 680.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth()) {
            content()
        }
    }
}
