package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.ui.ConsoleTheme

enum class SmithButtonVariant { Primary, Ghost, Danger }

@Composable
fun SmithButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SmithButtonVariant = SmithButtonVariant.Primary,
    enabled: Boolean = true,
) {
    val colors = LocalSmithColors.current
    val (bg, fg) = when (variant) {
        SmithButtonVariant.Primary -> colors.accent to colors.inkOnAccent
        SmithButtonVariant.Ghost -> Color.Transparent to colors.inkMuted
        SmithButtonVariant.Danger -> colors.statusError to colors.inkOnAccent
    }
    Text(
        text = text,
        style = TextStyle(
            fontFamily = ConsoleTheme.inter,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = if (enabled) fg else fg.copy(alpha = 0.5f),
        ),
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.5f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
