package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors

@Composable
internal fun PixelCamera(enabled: Boolean) {
    val colors = LocalSmithColors.current
    val color = if (enabled) colors.ink else colors.inkMuted
    val px = 2.dp

    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Spacer(modifier = Modifier.width(px * 2))
                Box(modifier = Modifier.width(px * 2).height(px).background(color))
            }
            Box(modifier = Modifier.width(px * 6).height(px * 4).background(color))
        }
        Box(
            modifier = Modifier
                .size(px * 2)
                .background(colors.bgBase)
        )
    }
}

@Composable
internal fun PixelFile(enabled: Boolean) {
    val colors = LocalSmithColors.current
    val color = if (enabled) colors.ink else colors.inkMuted
    val px = 2.dp

    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.width(px * 5).height(px * 6).background(color))
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 2.dp, top = 2.dp)
                .width(px * 2)
                .height(px * 2)
                .background(colors.bgBase)
        )
    }
}

@Composable
internal fun PixelVideo(enabled: Boolean) {
    val colors = LocalSmithColors.current
    val color = if (enabled) colors.ink else colors.inkMuted
    val px = 2.dp

    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.width(px * 6).height(px * 4).background(color))
        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.size(px).background(colors.bgBase))
            Box(modifier = Modifier.size(px).background(colors.bgBase))
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.size(px).background(colors.bgBase))
            Box(modifier = Modifier.size(px).background(colors.bgBase))
        }
    }
}

@Composable
internal fun PixelPlusButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalSmithColors.current
    val color = if (enabled) colors.ink else colors.inkMuted
    val px = 3.dp

    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(px * 5)
                .height(px)
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(px)
                .height(px * 5)
                .background(color)
        )
    }
}

@Composable
internal fun PixelNote(enabled: Boolean) {
    val colors = LocalSmithColors.current
    val color = if (enabled) colors.ink else colors.inkMuted
    val px = 2.dp

    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer page
        Box(modifier = Modifier.width(px * 6).height(px * 7).background(color))
        // Three inner lines (negative space)
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(px)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(px * 4)
                        .height(px / 2)
                        .background(colors.bgBase)
                )
            }
        }
    }
}

@Composable
internal fun PixelPackage(enabled: Boolean) {
    val colors = LocalSmithColors.current
    val color = if (enabled) colors.ink else colors.inkMuted
    val px = 2.dp

    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        // Box body
        Box(modifier = Modifier.width(px * 7).height(px * 5).background(color))
        // Horizontal band (tape)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(px * 7)
                .height(px)
                .background(colors.bgBase)
        )
        // Vertical band (tape)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(px)
                .height(px * 5)
                .background(colors.bgBase)
        )
    }
}

@Composable
internal fun PixelCheckmark(enabled: Boolean) {
    val colors = LocalSmithColors.current
    val color = if (enabled) colors.accent else colors.inkMuted
    val px = 2.dp

    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        // Short diagonal (down-right) at lower-left
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 2.dp, bottom = 4.dp)
                .width(px)
                .height(px * 2)
                .background(color)
        )
        // Bottom vertex
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 4.dp, bottom = 3.dp)
                .width(px)
                .height(px)
                .background(color)
        )
        // Long diagonal (up-right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 4.dp)
                .width(px)
                .height(px * 2)
                .background(color)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 6.dp)
                .width(px)
                .height(px * 2)
                .background(color)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 8.dp)
                .width(px)
                .height(px * 2)
                .background(color)
        )
    }
}
