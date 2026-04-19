package com.guildofsmiths.trademesh.ui.jobpipeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.JobStage

@Composable
fun JobStageBar(currentStage: JobStage, modifier: Modifier = Modifier) {
    val stages = JobStage.entries.toList()
    val currentIndex = stages.indexOf(currentStage)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Dots + connecting lines
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            stages.forEachIndexed { index, _ ->
                val dotColor = when {
                    index < currentIndex -> ConsoleTheme.accent
                    index == currentIndex -> ConsoleTheme.accent
                    else -> ConsoleTheme.textDim.copy(alpha = 0.3f)
                }
                val dotSize = if (index == currentIndex) 10.dp else 8.dp

                // Dot
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(dotColor)
                )

                // Connecting line (not after last dot)
                if (index < stages.lastIndex) {
                    val lineColor = if (index < currentIndex) {
                        ConsoleTheme.accent
                    } else {
                        ConsoleTheme.textDim.copy(alpha = 0.15f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(lineColor)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Current stage label
        Text(
            text = currentStage.displayName.uppercase(),
            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent)
        )
    }
}
