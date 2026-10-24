package com.zai.aibrowser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/*
 * Aegis visual identity — an original palette (not derived from any existing
 * browser brand): deep teal-green for privacy actions, warm paper surfaces in
 * light mode, deep green-black in dark mode, amber reserved for warnings.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E6F63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFF1E7),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF3F6A60),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E8E0),
    onSecondaryContainer = Color(0xFF05201A),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB2),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF6F8F6),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFFCFEFC),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFE7EEEA),
    onSurfaceVariant = Color(0xFF414944),
    outline = Color(0xFF6F7974),
    outlineVariant = Color(0xFFD0DAD4),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BDAC9),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0F5348),
    onPrimaryContainer = Color(0xFFA9F2E0),
    secondary = Color(0xFFB1CCC3),
    onSecondary = Color(0xFF1C352F),
    secondaryContainer = Color(0xFF324B44),
    onSecondaryContainer = Color(0xFFCDE8DF),
    tertiary = Color(0xFFF2C14E),
    onTertiary = Color(0xFF3F2D00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFDDB2),
    background = Color(0xFF0D1311),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF111815),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF232B27),
    onSurfaceVariant = Color(0xFFBEC9C3),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF3A443F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/** Consistent corner language across the app: pill inputs, 16dp cards, 24dp sheets. */
private val AegisShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AiBrowserTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AegisShapes,
        content = content
    )
}
