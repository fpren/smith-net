package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.ui.ConsoleTheme

/**
 * smith net v2 — Smith state trio (loading / empty / error).
 *
 * Mirrors the web's contract in
 * desktop/portal/src/console/components/ui/StateViews.tsx (LoadingState /
 * EmptyState / ErrorState).
 *
 * Material3's [CircularProgressIndicator] is the ONE sanctioned Material progress
 * primitive in this codebase (design system v2 otherwise forbids Material widgets
 * in favor of custom Composables) — there is no console-native spinner, and
 * reinventing indeterminate arc animation is not worth the divergence. Every other
 * visual (color, stroke width, size, label) is pinned to Smith tokens so it still
 * reads as ours.
 */

@Composable
fun SmithLoadingState(label: String = "LOADING") {
    val colors = LocalSmithColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(
            color = colors.accent,
            trackColor = colors.line,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label.uppercase(),
            style = SmithType.caption.copy(
                fontFamily = ConsoleTheme.jetBrainsMono,
                fontSize = 10.sp,
                color = colors.inkMuted,
            ),
        )
    }
}

@Composable
fun SmithEmptyState(title: String, hint: String? = null) {
    val colors = LocalSmithColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = SmithType.bodySmall.copy(
                fontSize = 14.sp,
                color = colors.inkMuted,
            ),
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = SmithType.caption.copy(
                    fontSize = 12.sp,
                    color = colors.inkMuted.copy(alpha = 0.7f),
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SmithErrorState(message: String = "Couldn't load this.", onRetry: (() -> Unit)? = null) {
    val colors = LocalSmithColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "[x] $message",
            style = SmithType.caption.copy(
                fontFamily = ConsoleTheme.jetBrainsMono,
                fontSize = 10.sp,
                color = colors.attention,
            ),
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            SmithButton(
                text = "RETRY",
                onClick = onRetry,
                variant = SmithButtonVariant.Ghost,
            )
        }
    }
}
