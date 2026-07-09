package com.guildofsmiths.trademesh.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
import com.guildofsmiths.trademesh.ui.theme2.smithColorsFor

// Light scheme fallback (console style still preferred)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0969DA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDF4FF),
    onPrimaryContainer = Color(0xFF0969DA),
    secondary = Color(0xFF1A7F37),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCFFE4),
    onSecondaryContainer = Color(0xFF1A7F37),
    tertiary = Color(0xFF8250DF),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEFF2F5),
    onSurfaceVariant = Color(0xFF656D76),
    outline = Color(0xFFD0D7DE),
    outlineVariant = Color(0xFFEFF2F5)
)

// Monospace typography for the entire app
private val MonospaceTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = FontFamily.Monospace),
    displayMedium = Typography().displayMedium.copy(fontFamily = FontFamily.Monospace),
    displaySmall = Typography().displaySmall.copy(fontFamily = FontFamily.Monospace),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = FontFamily.Monospace),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = FontFamily.Monospace),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = FontFamily.Monospace),
    titleLarge = Typography().titleLarge.copy(fontFamily = FontFamily.Monospace),
    titleMedium = Typography().titleMedium.copy(fontFamily = FontFamily.Monospace),
    titleSmall = Typography().titleSmall.copy(fontFamily = FontFamily.Monospace),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = FontFamily.Monospace),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = FontFamily.Monospace),
    bodySmall = Typography().bodySmall.copy(fontFamily = FontFamily.Monospace),
    labelLarge = Typography().labelLarge.copy(fontFamily = FontFamily.Monospace),
    labelMedium = Typography().labelMedium.copy(fontFamily = FontFamily.Monospace),
    labelSmall = Typography().labelSmall.copy(fontFamily = FontFamily.Monospace)
)

/**
 * Root Material shell. The status bar is plumbed from the resolved Smith palette
 * rather than hardcoded hex: [statusBarColor] + [lightIcons] are fed by the caller
 * (MainActivity) from the app's actual resolved dark/light state (Task 9). The
 * defaults below only apply to callers that don't pass these explicitly.
 */
@Composable
fun TradeMeshTheme(
    statusBarColor: Color = smithColorsFor(dark = false).bgBase,
    lightIcons: Boolean = true,
    content: @Composable () -> Unit
) {
    // Use light theme - matching original design
    val colorScheme = LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = statusBarColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightIcons
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MonospaceTypography,
        content = content
    )
}
