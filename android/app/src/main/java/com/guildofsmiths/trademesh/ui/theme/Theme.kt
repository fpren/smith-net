package com.guildofsmiths.trademesh.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Console-style dark color scheme
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF58A6FF),
    onPrimary = Color(0xFF0D1117),
    primaryContainer = Color(0xFF1F6FEB),
    onPrimaryContainer = Color(0xFFE6EDF3),
    secondary = Color(0xFF7EE787),
    onSecondary = Color(0xFF0D1117),
    secondaryContainer = Color(0xFF238636),
    onSecondaryContainer = Color(0xFFE6EDF3),
    tertiary = Color(0xFFA371F7),
    onTertiary = Color(0xFF0D1117),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF7D8590),
    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF21262D)
)

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

@Composable
fun TradeMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Force dark theme for console aesthetic
    val colorScheme = DarkColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
