package com.guildofsmiths.trademesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.Job

/**
 * Client Feedback Collection Dialog
 * 
 * Shown after a job is marked as DONE to collect client satisfaction data.
 * Uses bar-based rating system (1-10) instead of stars.
 */

@Composable
fun ClientFeedbackDialog(
    job: Job,
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, feedback: String?) -> Unit,
    onSkip: () -> Unit
) {
    var selectedRating by remember { mutableStateOf(0) }
    var feedbackText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.background,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CLIENT FEEDBACK",
                    style = ConsoleTheme.header
                )
                Text(
                    text = job.title,
                    style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.accent),
                    maxLines = 2
                )
                job.clientName?.let { client ->
                    Text(
                        text = "Client: $client",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Rating section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "How satisfied was the client?",
                        style = ConsoleTheme.body
                    )
                    
                    // Large bar selector
                    LargeFeedbackBarSelector(
                        value = selectedRating,
                        onValueChange = { selectedRating = it }
                    )
                }
                
                // Feedback text section
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Additional comments (optional):",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(ConsoleTheme.surface, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = feedbackText,
                            onValueChange = { feedbackText = it },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = ConsoleTheme.text
                            ),
                            cursorBrush = SolidColor(ConsoleTheme.cursor),
                            modifier = Modifier.fillMaxSize(),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (feedbackText.isEmpty()) {
                                        Text(
                                            text = "e.g., Great communication, clean work site...",
                                            style = ConsoleTheme.caption.copy(
                                                color = ConsoleTheme.placeholder
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
                
                // Quick feedback options
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Quick tags:",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        QuickFeedbackTag("Clean work", feedbackText) { 
                            feedbackText = appendTag(feedbackText, "Clean work site") 
                        }
                        QuickFeedbackTag("On time", feedbackText) { 
                            feedbackText = appendTag(feedbackText, "On time") 
                        }
                        QuickFeedbackTag("Great comm", feedbackText) { 
                            feedbackText = appendTag(feedbackText, "Great communication") 
                        }
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        QuickFeedbackTag("Quality work", feedbackText) { 
                            feedbackText = appendTag(feedbackText, "Quality workmanship") 
                        }
                        QuickFeedbackTag("Professional", feedbackText) { 
                            feedbackText = appendTag(feedbackText, "Professional") 
                        }
                        QuickFeedbackTag("Repeat client", feedbackText) { 
                            feedbackText = appendTag(feedbackText, "Will hire again") 
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Submit button
                Text(
                    text = "[SUBMIT]",
                    style = ConsoleTheme.action.copy(
                        color = if (selectedRating > 0) ConsoleTheme.success else ConsoleTheme.textMuted
                    ),
                    modifier = Modifier
                        .then(
                            if (selectedRating > 0) {
                                Modifier.clickable {
                                    onSubmit(selectedRating, feedbackText.takeIf { it.isNotBlank() })
                                }
                            } else Modifier
                        )
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Skip for now
                Text(
                    text = "[SKIP FOR NOW]",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier.clickable { onSkip() }
                )
            }
        }
    )
}

@Composable
private fun QuickFeedbackTag(
    text: String,
    currentFeedback: String,
    onClick: () -> Unit
) {
    val isSelected = currentFeedback.contains(text, ignoreCase = true) ||
                     currentFeedback.contains(text.replace(" ", ""), ignoreCase = true)
    
    Text(
        text = "[$text]",
        style = ConsoleTheme.caption.copy(
            color = if (isSelected) ConsoleTheme.success else ConsoleTheme.accent
        ),
        modifier = Modifier
            .background(
                if (isSelected) ConsoleTheme.success.copy(alpha = 0.1f) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

private fun appendTag(current: String, tag: String): String {
    return if (current.isBlank()) {
        tag
    } else if (current.contains(tag, ignoreCase = true)) {
        current // Already has this tag
    } else {
        "$current, $tag"
    }
}

/**
 * Compact feedback prompt shown in job cards.
 */
@Composable
fun FeedbackPromptBanner(
    job: Job,
    onCollectFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (job.clientSatisfactionBars == null && job.status == com.guildofsmiths.trademesh.ui.jobboard.JobStatus.DONE) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF3CD), RoundedCornerShape(4.dp))
                .clickable { onCollectFeedback() }
                .padding(8.dp)
        ) {
            Text(
                text = "📋 Collect client feedback",
                style = ConsoleTheme.caption.copy(color = Color(0xFF856404))
            )
            Text(
                text = "[RATE]",
                style = ConsoleTheme.captionBold.copy(color = Color(0xFF856404))
            )
        }
    }
}

/**
 * Display existing feedback rating in a compact format.
 */
@Composable
fun FeedbackDisplay(
    rating: Int,
    feedbackText: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Client Satisfaction:",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
            )
            PixelBarRating(
                value = rating,
                maxValue = 10,
                showValue = true,
                filledColor = when {
                    rating <= 3 -> Color(0xFFE74C3C)
                    rating <= 5 -> Color(0xFFF39C12)
                    rating <= 7 -> Color(0xFF3498DB)
                    else -> Color(0xFF27AE60)
                }
            )
        }
        
        feedbackText?.let { text ->
            Text(
                text = "\"$text\"",
                style = ConsoleTheme.caption.copy(
                    color = ConsoleTheme.textMuted,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                ),
                maxLines = 2
            )
        }
    }
}
