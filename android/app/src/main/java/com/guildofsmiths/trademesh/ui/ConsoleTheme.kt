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
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * smith net — Chic Dev Aesthetic
 * 
 * Bold, confident, oversized typography.
 * Clean but striking. Indie studio, not startup.
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
    
    val mono = FontFamily(
        Font(DeviceFontFamilyName("Courier New")),
        Font(DeviceFontFamilyName("monospace"))
    )
    
    // ═══════════════════════════════════════════════════════════════
    // COLORS — High contrast, minimal palette
    // ═══════════════════════════════════════════════════════════════
    
    val background = Color(0xFFF5F5F7)
    val surface = Color(0xFFEAEAEC)
    
    val text = Color(0xFF0A0A0A)              // Near black — bold reads strong
    val textSecondary = Color(0xFF3A3A3C)
    val textMuted = Color(0xFF6E6E73)
    val textQuiet = Color(0xFF8E8E93)
    val textDim = Color(0xFFAEAEB2)
    val placeholder = Color(0xFFBEBEC3)
    
    val accent = Color(0xFF0066CC)            // Deep blue — confident
    val accentDim = Color(0xFF0066CC).copy(alpha = 0.4f)
    val sentLine = Color(0xFF0066CC).copy(alpha = 0.15f)
    
    val success = Color(0xFF28A745)
    val warning = Color(0xFFE67700)
    val error = Color(0xFFCC3333)     // Muted red for delete actions
    
    val separator = Color(0xFFD8D8DC)
    val separatorFaint = Color(0xFFE8E8EA)
    
    val cursor = Color(0xFF0A0A0A)
    
    // Prefixes
    val receivedPrefix = Color(0xFF6E6E73)
    val sentPrefix = Color(0xFF0066CC).copy(alpha = 0.6f)
    
    // ═══════════════════════════════════════════════════════════════
    // TYPOGRAPHY — Bold, oversized, confident
    // ═══════════════════════════════════════════════════════════════
    
    // Brand header — BIG, BOLD, S P A C E D
    val brand = TextStyle(
        fontFamily = mono,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = text,
        letterSpacing = 3.sp
    )
    
    // Version tag — subtle
    val version = TextStyle(
        fontFamily = mono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = textMuted,
        letterSpacing = 0.5.sp
    )
    
    // Screen titles — prominent
    val title = TextStyle(
        fontFamily = mono,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = text,
        letterSpacing = 0.5.sp
    )
    
    // Section headers
    val header = TextStyle(
        fontFamily = mono,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = text,
        letterSpacing = 0.3.sp
    )
    
    // Body — readable, larger for chat messages
    val body = TextStyle(
        fontFamily = mono,
        fontSize = 17.sp,
        fontWeight = FontWeight.Normal,
        color = text
    )
    
    val bodyBold = TextStyle(
        fontFamily = mono,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        color = text
    )
    
    val bodySmall = TextStyle(
        fontFamily = mono,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = textSecondary
    )
    
    // Captions & metadata
    val caption = TextStyle(
        fontFamily = mono,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = textMuted
    )
    
    val captionBold = TextStyle(
        fontFamily = mono,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = textMuted
    )
    
    // Message timestamps
    val timestamp = TextStyle(
        fontFamily = mono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = textQuiet,
        letterSpacing = (-0.3).sp
    )
    
    // Arrow prefixes — tight
    val prefix = TextStyle(
        fontFamily = mono,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1).sp
    )
    
    // Input prompt
    val prompt = TextStyle(
        fontFamily = mono,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = textMuted
    )
    
    // Action text
    val action = TextStyle(
        fontFamily = mono,
        fontSize = 14.sp,
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
            .background(ConsoleTheme.surface)
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
                    fontFamily = FontFamily.Monospace,
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
