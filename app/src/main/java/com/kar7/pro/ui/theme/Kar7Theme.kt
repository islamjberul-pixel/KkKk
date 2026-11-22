package com.kar7.pro.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

object Kar7Colors {
    val DeepBlack = Color(0xFF0A0A0E)
    val DarkCharcoal = Color(0xFF14141C)
    val GraphiteSurface = Color(0xFF1A1A24)
    val GlassSurface = Color(0xF0181822)
    val ElevatedSurface = Color(0xFF232330)
    val MetallicGold = Color(0xFFE0B84C)
    val SoftGold = Color(0xFFF6DC8B)
    val MutedGold = Color(0xFF7D6725)
    val SubtleGoldBorder = Color(0x52E0B84C)
    val StrongGoldBorder = Color(0xA6E0B84C)
    val GoldGlow = Color(0x33E0B84C)
    val PureWhite = Color(0xFFFFFFFF)
    val LightGray = Color(0xFFC6C6D2)
    val DimGray = Color(0xFF8B8B9A)
    val SuccessGreen = Color(0xFF34D399)
    val WarningAmber = Color(0xFFFBBF24)
    val DangerRed = Color(0xFFF87171)
    val DangerSurface = Color(0x2AEF4444)
    val SuccessSurface = Color(0x2834D399)
    val WarningSurface = Color(0x28FBBF24)
}

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val Kar7Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
)

val Kar7Shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp)
)

private val Kar7DarkColorScheme = darkColorScheme(
    primary = Kar7Colors.MetallicGold,
    onPrimary = Kar7Colors.DeepBlack,
    primaryContainer = Kar7Colors.MutedGold,
    onPrimaryContainer = Kar7Colors.SoftGold,
    secondary = Kar7Colors.SoftGold,
    onSecondary = Kar7Colors.DeepBlack,
    secondaryContainer = Kar7Colors.ElevatedSurface,
    onSecondaryContainer = Kar7Colors.PureWhite,
    background = Kar7Colors.DeepBlack,
    onBackground = Kar7Colors.PureWhite,
    surface = Kar7Colors.DarkCharcoal,
    onSurface = Kar7Colors.PureWhite,
    surfaceVariant = Kar7Colors.GraphiteSurface,
    onSurfaceVariant = Kar7Colors.LightGray,
    outline = Kar7Colors.SubtleGoldBorder,
    outlineVariant = Kar7Colors.DimGray,
    error = Kar7Colors.DangerRed,
    onError = Kar7Colors.DeepBlack
)

@Composable
fun Kar7ProTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Kar7DarkColorScheme,
        typography = Kar7Typography,
        shapes = Kar7Shapes,
        content = content
    )
}
