package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.guildofsmiths.trademesh.ui.Tokens2

/** Resolved v2 palette. Mirrors design/tokens.json via generated Tokens2. */
data class SmithColors(
    val bgBase: Color,
    val bgPanel: Color,
    val bgSunken: Color,
    val line: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val attention: Color,
    val statusOnline: Color,
    val statusError: Color,
    val overlay: Color,
    val inkOnAccent: Color,
)

fun smithColorsFor(dark: Boolean): SmithColors = if (dark) SmithColors(
    bgBase = Tokens2.Dark.BgBase, bgPanel = Tokens2.Dark.BgPanel,
    bgSunken = Tokens2.Dark.BgSunken, line = Tokens2.Dark.Line,
    ink = Tokens2.Dark.Ink, inkMuted = Tokens2.Dark.InkMuted,
    accent = Tokens2.Dark.Accent, attention = Tokens2.Dark.Attention,
    statusOnline = Tokens2.Dark.StatusOnline, statusError = Tokens2.Dark.StatusError,
    overlay = Tokens2.Dark.Overlay, inkOnAccent = Tokens2.Dark.InkOnAccent,
) else SmithColors(
    bgBase = Tokens2.Light.BgBase, bgPanel = Tokens2.Light.BgPanel,
    bgSunken = Tokens2.Light.BgSunken, line = Tokens2.Light.Line,
    ink = Tokens2.Light.Ink, inkMuted = Tokens2.Light.InkMuted,
    accent = Tokens2.Light.Accent, attention = Tokens2.Light.Attention,
    statusOnline = Tokens2.Light.StatusOnline, statusError = Tokens2.Light.StatusError,
    overlay = Tokens2.Light.Overlay, inkOnAccent = Tokens2.Light.InkOnAccent,
)

val LocalSmithColors = staticCompositionLocalOf { smithColorsFor(dark = false) }

/**
 * v2 theme provider. darkEnabled stays false until screens are token-clean
 * (Plans 4-5): components must be dark-READY without flipping the app dark.
 */
@Composable
fun SmithTheme(darkEnabled: Boolean = false, content: @Composable () -> Unit) {
    val colors = smithColorsFor(dark = darkEnabled && isSystemInDarkTheme())
    CompositionLocalProvider(LocalSmithColors provides colors, content = content)
}
