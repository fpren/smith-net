package com.guildofsmiths.trademesh.ui.clients

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.ClientRepository
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ClientDetailScreen(
    clientName: String,
    allJobs: List<Job>,
    onJobClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clientJobs = remember(clientName, allJobs) {
        ClientRepository.getJobsForClient(clientName, allJobs)
    }
    val override = remember(clientName) { ClientRepository.getClientOverride(clientName) }
    val latestJob = clientJobs.maxByOrNull { it.updatedAt }

    val displayName = override?.name ?: clientName
    val displayPhone = override?.phone ?: latestJob?.clientPhone ?: ""
    val displayAddress = override?.address ?: latestJob?.clientAddress ?: ""

    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(displayName) }
    var editPhone by remember { mutableStateOf(displayPhone) }
    var editAddress by remember { mutableStateOf(displayAddress) }

    val closedJobs = clientJobs.count { it.stage == JobStage.CLOSED }
    val totalEarned = clientJobs
        .filter { it.stage == JobStage.CLOSED }
        .sumOf { it.materials.sumOf { m -> m.totalCost } + (it.hourlyRate * 8) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        ConsoleHeader(
            title = displayName,
            onBackClick = onBack,
            actionText = if (isEditing) "[Save]" else "[Edit]",
            onActionClick = {
                if (isEditing) {
                    ClientRepository.saveClientOverride(
                        clientName, editName.trim(), editPhone.trim(), editAddress.trim()
                    )
                    isEditing = false
                } else {
                    editName = displayName
                    editPhone = displayPhone
                    editAddress = displayAddress
                    isEditing = true
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CONTACT section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                    .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("CONTACT", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))

                if (isEditing) {
                    EditField("NAME", editName) { editName = it }
                    EditField("PHONE", editPhone) { editPhone = it }
                    EditField("ADDRESS", editAddress) { editAddress = it }
                    Text(
                        text = "[Cancel]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier.clickable {
                            isEditing = false
                        }.padding(vertical = 4.dp)
                    )
                } else {
                    if (displayPhone.isNotBlank()) {
                        Text(
                            text = displayPhone,
                            style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                            modifier = Modifier.clickable {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$displayPhone"))
                                context.startActivity(intent)
                            }
                        )
                    }
                    if (displayAddress.isNotBlank()) {
                        Text(
                            text = displayAddress,
                            style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                            modifier = Modifier.clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(displayAddress)}"))
                                context.startActivity(intent)
                            }
                        )
                    }
                    if (displayPhone.isBlank() && displayAddress.isBlank()) {
                        Text(
                            text = "No contact info. Tap [Edit] to add.",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                    }
                }
            }

            // JOBS section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                    .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "JOBS (${clientJobs.size})",
                    style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted)
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (clientJobs.isEmpty()) {
                    Text(
                        text = "No jobs yet.",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    val dateFormat = SimpleDateFormat("MMM d", Locale.US)
                    clientJobs.forEachIndexed { index, job ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(bounded = true),
                                    onClick = { onJobClick(job.id) }
                                )
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${job.stage.icon} ${job.title}",
                                    style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
                                )
                                val dateStr = dateFormat.format(Date(job.updatedAt))
                                Text(
                                    text = "${job.stage.displayName} · $dateStr",
                                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                                )
                            }
                            Text(
                                text = ">",
                                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        if (index < clientJobs.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(ConsoleTheme.text.copy(alpha = 0.06f))
                            )
                        }
                    }
                }
            }

            // SUMMARY section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                    .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                    .padding(14.dp)
            ) {
                Text("SUMMARY", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$${String.format("%.0f", totalEarned)} total · ${clientJobs.size} jobs · $closedJobs closed",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent)
                )
            }
        }
    }
}

@Composable
private fun EditField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text),
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.background, RoundedCornerShape(4.dp))
                .padding(8.dp),
            singleLine = true
        )
    }
}
