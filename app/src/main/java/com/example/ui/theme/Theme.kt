package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MyraDarkColorScheme = darkColorScheme(
    primary = MyraCyan,
    onPrimary = MyraObsidian,
    primaryContainer = MyraCyanMuted.copy(alpha = 0.35f),
    onPrimaryContainer = MyraCyan,
    secondary = MyraViolet,
    onSecondary = Color.White,
    secondaryContainer = MyraVioletMuted.copy(alpha = 0.35f),
    onSecondaryContainer = Color(0xFFDCD0FF),
    tertiary = MyraEmerald,
    onTertiary = MyraObsidian,
    background = MyraObsidian,
    onBackground = MyraTextPrimary,
    surface = MyraSurface,
    onSurface = MyraTextPrimary,
    surfaceVariant = MyraSurfaceElevated,
    onSurfaceVariant = MyraTextSecondary,
    surfaceContainerHigh = MyraSurfaceHighest,
    outline = MyraBorder,
    outlineVariant = MyraBorder.copy(alpha = 0.5f),
    error = MyraCoral,
    onError = Color.White
)

private val MyraLightColorScheme = lightColorScheme(
    primary = MyraLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F2FF),
    onPrimaryContainer = Color(0xFF003547),
    secondary = MyraLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF2E1065),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = MyraLightBackground,
    onBackground = MyraLightTextPrimary,
    surface = MyraLightSurface,
    onSurface = MyraLightTextPrimary,
    surfaceVariant = MyraLightSurfaceVariant,
    onSurfaceVariant = MyraLightTextSecondary,
    outline = Color(0xFFCBD5E1),
    error = MyraCoral,
    onError = Color.White
)

val MyraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyraTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MyraDarkColorScheme else MyraLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MyraTypography,
        shapes = MyraShapes,
        content = content
    )
}
