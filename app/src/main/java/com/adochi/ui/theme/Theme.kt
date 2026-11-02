package com.adochi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2EE6A8),
    onPrimary = Color(0xFF00281B),
    primaryContainer = Color(0xFF0F3D2F),
    onPrimaryContainer = Color(0xFFB8F5DD),
    secondary = Color(0xFF8B7CFF),
    onSecondary = Color(0xFF160C4D),
    secondaryContainer = Color(0xFF2A2366),
    onSecondaryContainer = Color(0xFFE2DDFF),
    tertiary = Color(0xFFFFB547),
    onTertiary = Color(0xFF3A2500),
    tertiaryContainer = Color(0xFF4A3300),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF070B16),
    onBackground = Color(0xFFE8ECF6),
    surface = Color(0xFF0C1222),
    onSurface = Color(0xFFE8ECF6),
    surfaceVariant = Color(0xFF18213A),
    onSurfaceVariant = Color(0xFF9AA4BF),
    surfaceContainerLowest = Color(0xFF080D1A),
    surfaceContainerLow = Color(0xFF0E1428),
    surfaceContainer = Color(0xFF111830),
    surfaceContainerHigh = Color(0xFF172038),
    surfaceContainerHighest = Color(0xFF1E2842),
    outline = Color(0xFF2A3452),
    outlineVariant = Color(0xFF1C2540),
    error = Color(0xFFFF5C7A),
    onError = Color(0xFF3D0010),
    errorContainer = Color(0xFF4A1020),
    onErrorContainer = Color(0xFFFFD9DF),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00A877),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9F5E4),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF5B4BE0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E0FF),
    onSecondaryContainer = Color(0xFF1B1060),
    tertiary = Color(0xFFD98200),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE6C2),
    onTertiaryContainer = Color(0xFF3A2500),
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF0C1222),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0C1222),
    surfaceVariant = Color(0xFFE9EDF5),
    onSurfaceVariant = Color(0xFF5A6480),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FD),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F3F9),
    surfaceContainerHighest = Color(0xFFE8ECF4),
    outline = Color(0xFFD5DBE8),
    outlineVariant = Color(0xFFE3E8F1),
    error = Color(0xFFE5294B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDDE3),
    onErrorContainer = Color(0xFF410010),
)

private val base = Typography()

private val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Amber used for "starting" and warning states. */
val ColorScheme.warning: Color get() = tertiary

@Composable
fun AdOchiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
