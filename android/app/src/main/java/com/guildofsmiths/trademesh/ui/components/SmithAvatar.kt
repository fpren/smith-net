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
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType

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
    val colors = LocalSmithColors.current
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
                    .background(colors.bgBase)
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
    val colors = LocalSmithColors.current
    Text(
        text = initials,
        style = SmithType.captionBold.copy(color = colors.inkOnAccent, fontSize = (size * 0.32f).sp)
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

/** Stable color from a seed string (name/id) — mirrors the web's accentForId rule. */
fun avatarColorOf(seed: String): Color {
    if (seed.isEmpty()) return Tokens2.AvatarPalette[0]
    var h = 0
    for (c in seed) h = (h * 31 + c.code) and 0x7fffffff
    return Tokens2.AvatarPalette[h % Tokens2.AvatarPalette.size]
}
