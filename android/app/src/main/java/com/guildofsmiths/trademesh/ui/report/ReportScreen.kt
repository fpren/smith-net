package com.guildofsmiths.trademesh.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import java.text.SimpleDateFormat
import java.util.*

enum class ReportPeriod(val label: String) {
    WEEK("Week"), MONTH("Month"), YEAR("Year")
}

@Composable
fun ReportScreen(
    allJobs: List<Job>,
    onJobClick: (String) -> Unit,
    onBack: () -> Unit
) {
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.MONTH) }
    val timeEntries by TimeEntryRepository.entries.collectAsState()

    val periodStart = remember(selectedPeriod) { getPeriodStart(selectedPeriod) }

    val periodJobs = remember(allJobs, periodStart) {
        allJobs.filter { it.updatedAt >= periodStart || it.createdAt >= periodStart }
    }
    val closedJobs = periodJobs.filter { it.stage == JobStage.CLOSED }
    val earned = closedJobs.sumOf { it.materials.sumOf { m -> m.totalCost } + (it.hourlyRate * 8) }
    val outstanding = periodJobs
        .filter { it.stage == JobStage.INVOICE }
        .sumOf { it.materials.sumOf { m -> m.totalCost } + (it.hourlyRate * 8) }

    val periodEntries = remember(timeEntries, periodStart) {
        timeEntries.filter { it.clockInTime >= periodStart }
    }
    val totalMinutes = periodEntries.sumOf { it.durationMinutes ?: 0 }
    val jobsWithTime = periodEntries.mapNotNull { it.jobId }.distinct().size

    // Group entries by day for timesheet
    val dailyGroups = remember(periodEntries) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        periodEntries
            .sortedByDescending { it.clockInTime }
            .groupBy { dateFormat.format(Date(it.clockInTime)) }
            .toSortedMap(compareByDescending { it })
    }

    // Top job types
    val topJobs = remember(periodJobs) {
        periodJobs
            .filter { it.title.isNotBlank() }
            .groupBy { it.title }
            .entries
            .sortedByDescending { it.value.size }
            .take(3)
            .map { (title, jobs) ->
                val rev = jobs.filter { it.stage == JobStage.CLOSED }
                    .sumOf { it.materials.sumOf { m -> m.totalCost } + (it.hourlyRate * 8) }
                Triple(title, jobs.size, rev)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        ConsoleHeader(title = "REPORT", onBackClick = onBack)

        // Period tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReportPeriod.entries.forEach { period ->
                val isSelected = period == selectedPeriod
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) ConsoleTheme.accent else ConsoleTheme.surface,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            0.5.dp,
                            if (isSelected) ConsoleTheme.accent else ConsoleTheme.text.copy(alpha = 0.06f),
                            RoundedCornerShape(4.dp)
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { selectedPeriod = period }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = period.label,
                        style = ConsoleTheme.captionBold.copy(
                            color = if (isSelected) androidx.compose.ui.graphics.Color.White
                                    else ConsoleTheme.textMuted
                        )
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // REVENUE card
            ReportCard("REVENUE") {
                Text(
                    text = "$${String.format("%.0f", earned)} earned · $${String.format("%.0f", outstanding)} outstanding",
                    style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.accent)
                )
                Spacer(modifier = Modifier.height(6.dp))
                val totalJobs = periodJobs.size.coerceAtLeast(1)
                val progress = closedJobs.size.toFloat() / totalJobs
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(ConsoleTheme.accent, RoundedCornerShape(3.dp))
                        )
                    }
                    Text(
                        "${closedJobs.size}/${periodJobs.size} closed",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                }
            }

            // HOURS card
            ReportCard("HOURS") {
                val hours = totalMinutes / 60.0
                val avg = if (jobsWithTime > 0) hours / jobsWithTime else 0.0
                Text(
                    text = "${String.format("%.1f", hours)}h logged · $jobsWithTime jobs",
                    style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
                )
                Text(
                    text = "Avg: ${String.format("%.1f", avg)}h/job",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
            }

            // TOP JOBS card
            if (topJobs.isNotEmpty()) {
                ReportCard("TOP JOBS") {
                    topJobs.forEach { (title, count, rev) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$title · $count jobs",
                                style = ConsoleTheme.caption.copy(color = ConsoleTheme.text),
                                modifier = Modifier.weight(1f)
                            )
                            if (rev > 0) {
                                Text(
                                    text = "$${String.format("%.0f", rev)}",
                                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent)
                                )
                            }
                        }
                    }
                }
            }

            // TIMESHEET section
            ReportCard("TIMESHEET") {
                if (dailyGroups.isEmpty()) {
                    Text(
                        text = "No time entries for this period.",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    val dayFormat = SimpleDateFormat("EEE, MMM d", Locale.US)
                    val timeFormat = SimpleDateFormat("h:mma", Locale.US)

                    dailyGroups.forEach { (dateKey, entries) ->
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey)
                        val dayLabel = if (date != null) dayFormat.format(date) else dateKey
                        val dayTotal = entries.sumOf { it.durationMinutes ?: 0 }
                        val dayHours = dayTotal / 60.0

                        // Day header
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(dayLabel, style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.text))
                            Text(
                                "${String.format("%.1f", dayHours)}h",
                                style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent)
                            )
                        }

                        // Entries for this day
                        entries.forEach { entry ->
                            val clockIn = timeFormat.format(Date(entry.clockInTime)).lowercase()
                            val clockOut = if (entry.clockOutTime != null)
                                timeFormat.format(Date(entry.clockOutTime)).lowercase()
                            else "—"
                            val dur = entry.durationMinutes ?: 0
                            val durStr = "${String.format("%.1f", dur / 60.0)}h"
                            val jobName = entry.jobTitle ?: "Unassigned"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (entry.jobId != null) Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = rememberRipple(bounded = true),
                                                onClick = { onJobClick(entry.jobId) }
                                            )
                                        else Modifier
                                    )
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = jobName,
                                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.text),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$clockIn–$clockOut",
                                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                                    )
                                }
                                Text(
                                    text = durStr,
                                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                                )
                            }
                        }

                        // Day separator
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(ConsoleTheme.text.copy(alpha = 0.06f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReportCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

private fun getPeriodStart(period: ReportPeriod): Long {
    val cal = Calendar.getInstance()
    when (period) {
        ReportPeriod.WEEK -> {
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        }
        ReportPeriod.MONTH -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
        }
        ReportPeriod.YEAR -> {
            cal.set(Calendar.DAY_OF_YEAR, 1)
        }
    }
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
