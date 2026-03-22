package com.guildofsmiths.trademesh.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage

@Composable
fun DashboardScreen(
    jobs: List<Job>,
    onJobClick: (String) -> Unit,
    onNewJob: () -> Unit,
    onClockIn: () -> Unit,
    onMessages: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
    onArchive: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    LaunchedEffect(jobs) { viewModel.loadJobs(jobs) }

    val alerts by viewModel.alerts.collectAsState()
    val activeJobs by viewModel.jobs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = viewModel.getBusinessName(), style = ConsoleTheme.title)
                Text(
                    text = com.guildofsmiths.trademesh.data.UserPreferences.getUserName(),
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
                    modifier = Modifier.clickable { onProfile() }.padding(vertical = 2.dp)
                )
            }
            Row {
                Text(
                    text = "[Msg]",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable { onMessages() }.padding(4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "[⚙]",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable { onSettings() }.padding(4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        ConsoleSeparator()
        Spacer(modifier = Modifier.height(16.dp))

        // Needs Attention
        if (alerts.isNotEmpty()) {
            Text(text = "NEEDS ATTENTION", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(8.dp))
            alerts.forEach { alert ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .clickable { onJobClick(alert.jobId) }
                        .padding(12.dp)
                ) {
                    Text(
                        text = "! ",
                        style = ConsoleTheme.bodyBold,
                        color = ConsoleTheme.warning
                    )
                    Text(text = alert.message, style = ConsoleTheme.bodySmall)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Active Jobs
        Text(text = "JOBS", style = ConsoleTheme.captionBold)
        Spacer(modifier = Modifier.height(8.dp))

        if (activeJobs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No active jobs.\nTap [+ NEW JOB] to get started.",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
            }
        } else {
            activeJobs.forEach { job ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .clickable { onJobClick(job.id) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${job.stage.icon} ${job.clientName ?: job.title}",
                            style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
                        )
                        Text(
                            text = "${job.stage.displayName} · ${job.clientAddress.take(30)}",
                            style = ConsoleTheme.caption
                        )
                    }
                    Text(text = ">", style = ConsoleTheme.body, color = ConsoleTheme.textMuted)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "[+ NEW JOB]",
                style = ConsoleTheme.action,
                modifier = Modifier
                    .clickable { onNewJob() }
                    .background(ConsoleTheme.surface)
                    .padding(12.dp)
            )
            Text(
                text = "[CLOCK IN]",
                style = ConsoleTheme.action,
                modifier = Modifier
                    .clickable { onClockIn() }
                    .background(ConsoleTheme.surface)
                    .padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "${viewModel.getActiveJobCount()}", style = ConsoleTheme.bodyBold)
                Text(text = "Active", style = ConsoleTheme.caption)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "$${String.format("%.0f", viewModel.getOutstandingTotal())}", style = ConsoleTheme.bodyBold)
                Text(text = "Outstanding", style = ConsoleTheme.caption)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Archive link
        Text(
            text = "[Archive]",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
            modifier = Modifier.clickable { onArchive() }.padding(4.dp)
        )
    }
}
