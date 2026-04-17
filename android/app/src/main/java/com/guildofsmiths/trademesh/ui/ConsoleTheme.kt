package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.R

/**
 * smith net — Altara Design Tokens
 *
 * Warm parchment palette, Syne display headers, IBM Plex body/mono.
 * Indie studio, not startup.
 */
object ConsoleTheme {

    // Brand
    const val APP_NAME = "smith net"
    const val APP_VERSION = "0.1"
    const val BUILD_HASH = "a3f2c1"
    const val STUDIO = "guild of smiths"

    // ═══════════════════════════════════════════════════════════════
    // FONTS
    // ═══════════════════════════════════════════════════════════════

    val syne = FontFamily(
        Font(R.font.syne_semibold, FontWeight.SemiBold),
        Font(R.font.syne_bold, FontWeight.Bold),
    )
    val plexSans = FontFamily(
        Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
        Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    )
    val plexMono = FontFamily(
        Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    )

    // Backward-compatibility alias
    val mono = plexMono

    // ═══════════════════════════════════════════════════════════════
    // COLORS — Warm parchment palette
    // ═══════════════════════════════════════════════════════════════

    val background = Color(0xFFF4F2EE)
    val surface = Color(0xFFFAFAF8)

    val text = Color(0xFF2A2520)
    val textSecondary = Color(0xFF5C5347)
    val textMuted = Color(0xFF8C8478)
    val textQuiet = Color(0xFF8C8478)
    val textDim = Color(0xFFB0A898)
    val placeholder = Color(0xFFB0A898)

    val accent = Color(0xFF9A6F2E)            // Gold
    val accentDim = Color(0xFF9A6F2E).copy(alpha = 0.4f)
    val sentLine = Color(0xFF9A6F2E).copy(alpha = 0.15f)

    val success = Color(0xFF5A8C76)           // Sage
    val warning = Color(0xFF8C5A2E)           // Sienna
    val error = Color(0xFF8C3A3A)             // Brick

    val separator = Color(0xFFE8E4DE)
    val separatorFaint = Color(0xFFF0EDE8)

    val cursor = Color(0xFF2A2520)

    // Prefixes
    val receivedPrefix = Color(0xFF8C8478)
    val sentPrefix = Color(0xFF9A6F2E).copy(alpha = 0.6f)

    // ═══════════════════════════════════════════════════════════════
    // TYPOGRAPHY
    // ═══════════════════════════════════════════════════════════════

    // Brand header — Syne, bold, spaced
    val brand = TextStyle(
        fontFamily = syne,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = text,
        letterSpacing = 2.sp
    )

    val version = TextStyle(
        fontFamily = plexMono,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = textMuted,
        letterSpacing = 0.5.sp
    )

    val title = TextStyle(
        fontFamily = syne,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = text,
        letterSpacing = 0.3.sp
    )

    val header = TextStyle(
        fontFamily = syne,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = text,
        letterSpacing = 0.2.sp
    )

    val body = TextStyle(
        fontFamily = plexSans,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = text
    )

    val bodyBold = TextStyle(
        fontFamily = plexSans,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = text
    )

    val bodySmall = TextStyle(
        fontFamily = plexSans,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        color = textSecondary
    )

    val caption = TextStyle(
        fontFamily = plexMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        color = textMuted
    )

    val captionBold = TextStyle(
        fontFamily = plexMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = textMuted
    )

    val timestamp = TextStyle(
        fontFamily = plexMono,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = textQuiet,
        letterSpacing = (-0.3).sp
    )

    val prefix = TextStyle(
        fontFamily = plexMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp
    )

    val prompt = TextStyle(
        fontFamily = plexMono,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = textMuted
    )

    val action = TextStyle(
        fontFamily = plexMono,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = accent
    )
}

/**
 * Chic header with bold back arrow.
 */
@Composable
fun ConsoleHeader(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ConsoleTheme.background)
            .then(
                if (onBackClick != null) Modifier.clickable(onClick = onBackClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBackClick != null) {
            Text(
                text = "←",
                style = TextStyle(
                    fontFamily = ConsoleTheme.plexMono,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = ConsoleTheme.text
                )
            )
            Spacer(modifier = Modifier.width(14.dp))
        }

        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = ConsoleTheme.header)
            if (subtitle != null) {
                Text(text = subtitle, style = ConsoleTheme.caption)
            }
        }
    }
}

/**
 * Faint separator.
 */
@Composable
fun ConsoleSeparator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ConsoleTheme.separatorFaint)
    )
}
