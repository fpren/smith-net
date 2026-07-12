package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.ui.ConsoleTheme

/**
 * smith net v2 — colorless typography, single source of truth.
 *
 * M1 font mapping (spec 2026-07-11-modern-look-fixes-design.md): UI text =
 * Inter, data/mono = JetBrains Mono, display = Syne. Comm styles keep
 * Public Sans + JetBrains Mono (locked Plan 3 decision). The prior IBM
 * body/mono families have been retired. Color is stripped to
 * [Color.Unspecified]; screens paint from [LocalSmithColors] at the call site.
 *
 * Note: jetBrainsMono declares Normal(400) + Medium(500) only; SemiBold
 * styles (captionBold/prefix/action) resolve to the nearest weight exactly
 * as they did under the prior mono family (same declared weight set) —
 * visual parity.
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
        fontFamily = ConsoleTheme.jetBrainsMono,
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
        fontFamily = ConsoleTheme.inter,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified
    )

    val bodyBold = TextStyle(
        fontFamily = ConsoleTheme.inter,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Unspecified
    )

    val bodySmall = TextStyle(
        fontFamily = ConsoleTheme.inter,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified
    )

    val caption = TextStyle(
        fontFamily = ConsoleTheme.jetBrainsMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified
    )

    val captionBold = TextStyle(
        fontFamily = ConsoleTheme.jetBrainsMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Unspecified
    )

    val timestamp = TextStyle(
        fontFamily = ConsoleTheme.jetBrainsMono,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified,
        letterSpacing = (-0.3).sp
    )

    val prefix = TextStyle(
        fontFamily = ConsoleTheme.jetBrainsMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Unspecified,
        letterSpacing = (-0.5).sp
    )

    val prompt = TextStyle(
        fontFamily = ConsoleTheme.jetBrainsMono,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Unspecified
    )

    val action = TextStyle(
        fontFamily = ConsoleTheme.jetBrainsMono,
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
