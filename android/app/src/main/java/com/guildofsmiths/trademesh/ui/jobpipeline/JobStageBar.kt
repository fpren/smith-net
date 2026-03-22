package com.guildofsmiths.trademesh.ui.jobpipeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.JobStage

@Composable
fun JobStageBar(currentStage: JobStage, modifier: Modifier = Modifier) {
    val stages = JobStage.entries.toList()
    val currentIndex = stages.indexOf(currentStage)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        stages.forEach { stage ->
            val stageIndex = stages.indexOf(stage)
            val color = when {
                stageIndex < currentIndex -> ConsoleTheme.success
                stageIndex == currentIndex -> ConsoleTheme.accent
                else -> ConsoleTheme.textDim
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stage.icon, style = ConsoleTheme.bodySmall, color = color)
                Text(
                    text = stage.displayName.take(4),
                    style = ConsoleTheme.caption,
                    color = color
                )
            }
        }
    }
}
