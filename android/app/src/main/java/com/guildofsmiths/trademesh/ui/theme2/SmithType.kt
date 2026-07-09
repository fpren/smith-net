package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.ui.ConsoleTheme

/**
 * smith net v2 — colorless typography.
 *
 * Mirrors every TextStyle NAME defined in [ConsoleTheme] (font family, size, weight,
 * letter spacing, line height copied field-for-field) but with color stripped to
 * [Color.Unspecified]. Screens paint color from [LocalSmithColors] at the call site
 * (`Text(text, style = SmithType.body, color = colors.ink)`) instead of baking a
 * fixed parchment-palette color into the style, so the same style works under both
 * the light and dark v2 palettes.
 *
 * Grepped ConsoleTheme.kt directly (2026-07) to confirm the real TextStyle set: it
 * defines exactly these 18 names, all mirrored below. Nothing was skipped and
 * nothing extra was found consumed by crew screens beyond this set.
 */
object SmithType {

    val brand = TextStyle(
        fontFamily = ConsoleTheme.syne,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Unspecified,
        letterSpacing = 2.sp
    )

    val version = TextStyle(
        fontFamily = ConsoleTheme.plexMono,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified,
        letterSpacing = 0.5.sp
    )

    val title = TextStyle(
        fontFamily = ConsoleTheme.syne,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Unspecified,
        letterSpacing = 0.3.sp
    )

    val header = TextStyle(
        fontFamily = ConsoleTheme.syne,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Unspecified,
        letterSpacing = 0.2.sp
    )

    val body = TextStyle(
        fontFamily = ConsoleTheme.plexSans,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified
    )

    val bodyBold = TextStyle(
        fontFamily = ConsoleTheme.plexSans,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Unspecified
    )

    val bodySmall = TextStyle(
        fontFamily = ConsoleTheme.plexSans,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified
    )

    val caption = TextStyle(
        fontFamily = ConsoleTheme.plexMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified
    )

    val captionBold = TextStyle(
        fontFamily = ConsoleTheme.plexMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Unspecified
    )

    val timestamp = TextStyle(
        fontFamily = ConsoleTheme.plexMono,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified,
        letterSpacing = (-0.3).sp
    )

    val prefix = TextStyle(
        fontFamily = ConsoleTheme.plexMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Unspecified,
        letterSpacing = (-0.5).sp
    )

    val prompt = TextStyle(
        fontFamily = ConsoleTheme.plexMono,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified
    )

    val action = TextStyle(
        fontFamily = ConsoleTheme.plexMono,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Unspecified
    )

    // ── Comm-surface typography (Public Sans + JetBrains Mono) ──────────

    val commName = TextStyle(
        fontFamily = ConsoleTheme.publicSans,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Unspecified
    )

    val commBody = TextStyle(
        fontFamily = ConsoleTheme.publicSans,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = Color.Unspecified
    )

    val commId = TextStyle(
        fontFamily = ConsoleTheme.jetBrainsMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified,
        letterSpacing = 1.sp
    )

    val commTimestamp = TextStyle(
        fontFamily = ConsoleTheme.jetBrainsMono,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        color = Color.Unspecified
    )

    val dialpad = TextStyle(
        fontFamily = ConsoleTheme.jetBrainsMono,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified,
        letterSpacing = 4.sp
    )
}
