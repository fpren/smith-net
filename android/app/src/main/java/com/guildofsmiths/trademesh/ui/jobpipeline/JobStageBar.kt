package com.guildofsmiths.trademesh.ui.jobpipeline

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
                val isFilled = index <= currentIndex
                val emptyRingColor = ConsoleTheme.textDim.copy(alpha = 0.4f)

                val targetDotSize = if (index == currentIndex) 10.dp else 8.dp
                val dotSize by animateDpAsState(
                    targetValue = targetDotSize,
                    animationSpec = tween(durationMillis = 300),
                    label = "dotSize"
                )
                val dotColor by animateColorAsState(
                    targetValue = if (isFilled) ConsoleTheme.accent else emptyRingColor,
                    animationSpec = tween(durationMillis = 300),
                    label = "dotColor"
                )
                val bracketColor by animateColorAsState(
                    targetValue = if (isFilled) ConsoleTheme.accent
                                  else ConsoleTheme.textDim.copy(alpha = 0.4f),
                    animationSpec = tween(durationMillis = 300),
                    label = "bracketColor"
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "(",
                        style = ConsoleTheme.captionBold.copy(color = bracketColor)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .then(
                                if (isFilled) Modifier.background(dotColor)
                                else Modifier.border(1.5.dp, dotColor, CircleShape)
                            )
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = ")",
                        style = ConsoleTheme.captionBold.copy(color = bracketColor)
                    )
                }

                if (index < stages.lastIndex) {
                    val lineColor by animateColorAsState(
                        targetValue = if (index < currentIndex) ConsoleTheme.accent
                                      else ConsoleTheme.textDim.copy(alpha = 0.15f),
                        animationSpec = tween(durationMillis = 400),
                        label = "lineColor"
                    )
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

        // Current stage label — crossfade when stage changes
        AnimatedContent(
            targetState = currentStage,
            transitionSpec = {
                (fadeIn(tween(300)) togetherWith fadeOut(tween(200)))
            },
            label = "stageLabel"
        ) { stage ->
            Text(
                text = stage.displayName.uppercase(),
                style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent)
            )
        }
    }
}
