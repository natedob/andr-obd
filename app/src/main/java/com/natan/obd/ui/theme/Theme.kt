package com.natan.obd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Teal = Color(0xFF2EE6C5)
val Amber = Color(0xFFFFB454)
val Coral = Color(0xFFFF6B6B)
val Mint = Color(0xFF3DDC84)
val Sky = Color(0xFF7AA7FF)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0F4F45),
    onPrimaryContainer = Color(0xFFB4F5E8),
    secondary = Amber,
    tertiary = Sky,
    background = Color(0xFF0A0E14),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF0A0E14),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1E2733),
    onSurfaceVariant = Color(0xFF9AA7B8),
    surfaceContainerLow = Color(0xFF0F141C),
    surfaceContainer = Color(0xFF121821),
    surfaceContainerHigh = Color(0xFF18202B),
    surfaceContainerHighest = Color(0xFF1E2733),
    outlineVariant = Color(0xFF2A3544),
    error = Coral,
    errorContainer = Color(0xFF4A1D1D),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F5E8),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF9A5B00),
    tertiary = Color(0xFF2F5FD0),
    background = Color(0xFFF5F8FB),
    surface = Color(0xFFF5F8FB),
    surfaceVariant = Color(0xFFDDE5EE),
    onSurfaceVariant = Color(0xFF4A5666),
    surfaceContainerLow = Color(0xFFEFF3F8),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F4F9),
    surfaceContainerHighest = Color(0xFFE6ECF3),
    error = Color(0xFFC62828),
)

/** Tabular numerals keep live numbers from jittering sideways as digits change. */
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

private val AppTypography = Typography().let {
    it.copy(
        displayLarge = it.displayLarge.merge(Tabular).copy(fontWeight = FontWeight.SemiBold),
        displayMedium = it.displayMedium.merge(Tabular).copy(fontWeight = FontWeight.SemiBold),
        displaySmall = it.displaySmall.merge(Tabular).copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = it.labelSmall.copy(letterSpacing = 1.2.sp),
    )
}

@Composable
fun EcoDriveTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

/** Red -> amber -> green, so a score reads at a glance. */
fun scoreColor(score: Int): Color = when {
    score >= 80 -> Mint
    score >= 60 -> Amber
    else -> Coral
}
