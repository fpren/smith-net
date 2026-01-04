package com.guildofsmiths.trademesh.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * smith net — Light Theme with Developer-Centric Aesthetic
 * 
 * DESIGN PRINCIPLES:
 * - Light/neutral base for battery efficiency through visual restraint
 * - High contrast text for readability
 * - Monospace typography for headers and technical content (defined in ConsoleTheme)
 * - Minimal color palette with strategic accent usage
 * - No ornamental graphics - clarity and performance first
 * 
 * Battery optimization achieved through:
 * - Efficient rendering (light backgrounds use less power on LCD screens)
 * - Visual restraint (fewer gradient/animation effects)
 * - NOT through dark mode (which only benefits OLED)
 */

// Light color scheme aligned with ConsoleTheme
private val LightColorScheme = lightColorScheme(
    // Primary - Deep blue accent (matches ConsoleTheme.accent)
    primary = Color(0xFF0066CC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDF4FF),
    onPrimaryContainer = Color(0xFF0066CC),
    
    // Secondary - Success green
    secondary = Color(0xFF28A745),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCFFE4),
    onSecondaryContainer = Color(0xFF28A745),
    
    // Tertiary - Info blue
    tertiary = Color(0xFF17A2B8),
    onTertiary = Color(0xFFFFFFFF),
    
    // Background/Surface - Light grays (matches ConsoleTheme)
    background = Color(0xFFF5F5F7),           // ConsoleTheme.background
    onBackground = Color(0xFF0A0A0A),         // ConsoleTheme.text
    surface = Color(0xFFEAEAEC),              // ConsoleTheme.surface
    onSurface = Color(0xFF0A0A0A),            // ConsoleTheme.text
    surfaceVariant = Color(0xFFE8E8EA),
    onSurfaceVariant = Color(0xFF6E6E73),     // ConsoleTheme.textMuted
    
    // Outlines - Subtle separators
    outline = Color(0xFFD8D8DC),              // ConsoleTheme.separator
    outlineVariant = Color(0xFFE8E8EA),       // ConsoleTheme.separatorFaint
    
    // Error states
    error = Color(0xFFCC3333),                // ConsoleTheme.error
    onError = Color(0xFFFFFFFF)
)

@Composable
fun TradeMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Always use light theme for battery optimization and visual consistency
    // Dark mode is NOT used as battery savings only apply to OLED screens
    // Visual restraint and efficient rendering provide better optimization
    val colorScheme = LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Light status bar background with dark icons for visibility
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
