package com.guildofsmiths.trademesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.guildofsmiths.trademesh.ui.ConsoleTheme

/**
 * Shared circular avatar for the comm surface: renders an uploaded photo via
 * Coil when [photoUrl] is set, falling back to deterministic-colored initials
 * (so it works offline and before a photo loads). Optionally overlays a
 * presence dot.
 *
 * Replaces the per-screen initials Boxes (ChatAvatar, ClientRow, ColleagueRow)
 * so photos appear everywhere from one place.
 */
@Composable
fun SmithAvatar(
    name: String,
    modifier: Modifier = Modifier,
    photoUrl: String? = null,
    size: Int = 44,
    statusColor: Color? = null,
) {
    val initials = initialsOf(name)
    val bg = avatarColorOf(name)

    Box(modifier = modifier.size(size.dp)) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = photoUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size.dp).clip(CircleShape),
                    loading = { InitialsLabel(initials, size) },
                    error = { InitialsLabel(initials, size) },
                )
            } else {
                InitialsLabel(initials, size)
            }
        }

        if (statusColor != null) {
            Box(
                modifier = Modifier
                    .size((size * 0.24f).dp.coerceAtLeast(8.dp))
                    .clip(CircleShape)
                    .background(ConsoleTheme.background)
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size((size * 0.18f).dp.coerceAtLeast(6.dp))
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
        }
    }
}

@Composable
private fun InitialsLabel(initials: String, size: Int) {
    Text(
        text = initials,
        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.surface, fontSize = (size * 0.32f).sp)
    )
}

/** First letters of up to two words, uppercased. */
fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

private val AVATAR_PALETTE = listOf(
    Color(0xFF9A6F2E), // gold
    Color(0xFF5A8C76), // sage
    Color(0xFF8C5A2E), // sienna
    Color(0xFF8C3A3A), // brick
    Color(0xFF4A6FA5), // slate blue
    Color(0xFF6E5A8C), // muted violet
)

/** Stable color from a seed string (name/id). */
fun avatarColorOf(seed: String): Color {
    if (seed.isEmpty()) return AVATAR_PALETTE[0]
    var h = 0
    for (c in seed) h = (h * 31 + c.code) and 0x7fffffff
    return AVATAR_PALETTE[h % AVATAR_PALETTE.size]
}
