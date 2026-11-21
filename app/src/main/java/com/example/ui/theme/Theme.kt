package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val EscudoDarkColorScheme = darkColorScheme(
    primary = ShieldEmerald,
    onPrimary = Color(0xFF04261E),
    primaryContainer = ShieldEmeraldDark,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = CobaltWork,
    onSecondary = Color(0xFF042538),
    secondaryContainer = CobaltWorkDark,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AmberWarning,
    onTertiary = Color(0xFF271702),
    tertiaryContainer = AmberWarningDark,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = CrimsonAlert,
    onError = Color(0xFFFFFFFF),
    errorContainer = CrimsonAlertDark,
    onErrorContainer = Color(0xFFFECDD3),
    background = ObsidianCanvas,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle
)

private val EscudoLightColorScheme = lightColorScheme(
    primary = ShieldTealLight,
    onPrimary = Color.White,
    primaryContainer = ShieldTealContainerLight,
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = CobaltLight,
    onSecondary = Color.White,
    secondaryContainer = CobaltContainerLight,
    onSecondaryContainer = Color(0xFF082F49),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    error = Color(0xFFBE123C),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF4C0519),
    background = SlateCanvasLight,
    onBackground = TextPrimaryLight,
    surface = SlateSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SlateSurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = SlateOutlineLight
)

val EscudoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun EscudoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) EscudoDarkColorScheme else EscudoLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = EscudoTypography,
        shapes = EscudoShapes,
        content = content
    )
}
