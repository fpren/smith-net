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

/** Resolved dark flag for non-Compose consumers (osmdroid tile filter). Provided by [SmithTheme]. */
val LocalSmithDark = staticCompositionLocalOf { false }

/** User-facing theme preference. Resolution happens in [resolveDark]. */
enum class ThemePreference { LIGHT, DARK, SYSTEM }

/**
 * Pure resolution rule for whether dark palette should be active. darkEnabled is the
 * master kill switch: Task 9 flipped it true at the app root (MainActivity), so the
 * user's theme preference now resolves normally. The switch remains for tests/previews
 * that need to force light regardless of preference. Kept side-effect free and JVM-testable.
 */
fun resolveDark(pref: ThemePreference, systemDark: Boolean, darkEnabled: Boolean): Boolean {
    if (!darkEnabled) return false
    return when (pref) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> systemDark
    }
}

/**
 * v2 theme provider. darkEnabled is flipped true at the app root (MainActivity), so
 * the resolved theme preference now drives light/dark for real. The parameter's
 * `false` default only applies to callers that don't pass it explicitly (tests/previews).
 */
@Composable
fun SmithTheme(
    darkEnabled: Boolean = false,
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    resolvedDark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    // When the caller already resolved the theme (MainActivity resolves once and
    // also feeds the status bar), reuse that value -- one resolution, no drift
    // between window chrome and palette.
    val dark = resolvedDark ?: resolveDark(themePreference, isSystemInDarkTheme(), darkEnabled)
    val colors = smithColorsFor(dark = dark)
    CompositionLocalProvider(LocalSmithColors provides colors, LocalSmithDark provides dark, content = content)
}
