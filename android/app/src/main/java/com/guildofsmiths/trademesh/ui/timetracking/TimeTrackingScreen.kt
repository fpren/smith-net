package com.guildofsmiths.trademesh.ui.timetracking

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * C-12: Time Tracking Screen
 * Clock in/out interface with pixel art aesthetic
 */

private val ConsoleGreen = Color(0xFF00FF00)
private val ConsoleDim = Color(0xFF00AA00)
private val ConsoleBackground = Color(0xFF0A0A0A)
private val ConsoleBorder = Color(0xFF00DD00)
private val MonoFont = FontFamily.Monospace

@Composable
fun TimeTrackingScreen(
    onNavigateBack: () -> Unit,
    viewModel: TimeTrackingViewModel = viewModel()
) {
    val isClockedIn by viewModel.isClockedIn.collectAsState()
    val activeEntry by viewModel.activeEntry.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val dailySummary by viewModel.dailySummary.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()

    var showClockInDialog by remember { mutableStateOf(false) }
    var showClockOutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleBackground)
            .padding(8.dp)
    ) {
        // Header
        TimeTrackingHeader(
            onBack = onNavigateBack,
            onRefresh = {
                viewModel.loadStatus()
                viewModel.loadEntries()
                viewModel.loadDailySummary()
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Error banner
        error?.let { errorMsg ->
            Text(
                text = "[!] $errorMsg",
                color = Color.Red,
                fontFamily = MonoFont,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(4.dp)
                    .clickable { viewModel.clearError() }
            )
        }

        // Main clock display
        ClockDisplay(
            isClockedIn = isClockedIn,
            activeEntry = activeEntry,
            elapsedSeconds = elapsedSeconds,
            isLoading = isLoading,
            onClockIn = { showClockInDialog = true },
            onClockOut = { showClockOutDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Daily summary
        dailySummary?.let { summary ->
            DailySummaryCard(summary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recent entries
        Text(
            text = "╔═══ RECENT ENTRIES ═══╗",
            color = ConsoleGreen,
            fontFamily = MonoFont,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(entries) { entry ->
                TimeEntryCard(entry)
            }
        }
    }

    // Clock In Dialog
    if (showClockInDialog) {
        ClockInDialog(
            onDismiss = { showClockInDialog = false },
            onClockIn = { entryType, note ->
                viewModel.clockIn(entryType = entryType, note = note)
                showClockInDialog = false
            }
        )
    }

    // Clock Out Dialog
    if (showClockOutDialog) {
        ClockOutDialog(
            elapsedSeconds = elapsedSeconds,
            onDismiss = { showClockOutDialog = false },
            onClockOut = { note ->
                viewModel.clockOut(note)
                showClockOutDialog = false
            }
        )
    }
}

@Composable
private fun TimeTrackingHeader(
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ConsoleBorder)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "[<]",
                color = ConsoleGreen,
                fontFamily = MonoFont,
                fontSize = 14.sp,
                modifier = Modifier.clickable { onBack() }
            )
            Text(
                text = "╔═══ TIME CLOCK ═══╗",
                color = ConsoleGreen,
                fontFamily = MonoFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "[↻]",
            color = ConsoleGreen,
            fontFamily = MonoFont,
            fontSize = 14.sp,
            modifier = Modifier.clickable { onRefresh() }
        )
    }
}

@Composable
private fun ClockDisplay(
    isClockedIn: Boolean,
    activeEntry: TimeEntry?,
    elapsedSeconds: Long,
    isLoading: Boolean,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, if (isClockedIn) ConsoleGreen else ConsoleDim)
            .background(Color(0xFF001100))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status indicator
        Text(
            text = if (isClockedIn) "▓▓▓ CLOCKED IN ▓▓▓" else "░░░ CLOCKED OUT ░░░",
            color = if (isClockedIn) ConsoleGreen else ConsoleDim,
            fontFamily = MonoFont,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Timer display
        if (isClockedIn) {
            val hours = elapsedSeconds / 3600
            val minutes = (elapsedSeconds % 3600) / 60
            val seconds = elapsedSeconds % 60

            Text(
                text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                color = ConsoleGreen,
                fontFamily = MonoFont,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )

            activeEntry?.let { entry ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Started: ${formatTime(entry.clockInTime)}",
                    color = ConsoleDim,
                    fontFamily = MonoFont,
                    fontSize = 12.sp
                )
                entry.jobTitle?.let { job ->
                    Text(
                        text = "Job: $job",
                        color = ConsoleDim,
                        fontFamily = MonoFont,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            Text(
                text = "--:--:--",
                color = ConsoleDim,
                fontFamily = MonoFont,
                fontSize = 48.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Clock button
        Box(
            modifier = Modifier
                .border(2.dp, if (isClockedIn) Color.Red else ConsoleGreen)
                .background(if (isClockedIn) Color(0xFF110000) else Color(0xFF001100))
                .clickable(enabled = !isLoading) {
                    if (isClockedIn) onClockOut() else onClockIn()
                }
                .padding(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Text(
                text = if (isLoading) "[...]" else if (isClockedIn) "[■ CLOCK OUT]" else "[▶ CLOCK IN]",
                color = if (isClockedIn) Color.Red else ConsoleGreen,
                fontFamily = MonoFont,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DailySummaryCard(summary: DailySummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ConsoleBorder)
            .padding(12.dp)
    ) {
        Text(
            text = "╔═══ TODAY'S SUMMARY ═══╗",
            color = ConsoleGreen,
            fontFamily = MonoFont,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem("Total", summary.totalMinutes)
            SummaryItem("Regular", summary.regularMinutes)
            SummaryItem("OT", summary.overtimeMinutes)
            SummaryItem("Break", summary.breakMinutes)
        }

        // Progress bar
        Spacer(modifier = Modifier.height(8.dp))
        val targetMinutes = 8 * 60 // 8 hours
        val progress = (summary.totalMinutes.toFloat() / targetMinutes).coerceIn(0f, 1f)
        val filledBlocks = (progress * 20).toInt()
        val emptyBlocks = 20 - filledBlocks

        Text(
            text = "[" + "█".repeat(filledBlocks) + "░".repeat(emptyBlocks) + "] ${(progress * 100).toInt()}%",
            color = if (progress >= 1f) ConsoleGreen else ConsoleDim,
            fontFamily = MonoFont,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SummaryItem(label: String, minutes: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatDuration(minutes),
            color = ConsoleGreen,
            fontFamily = MonoFont,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = ConsoleDim,
            fontFamily = MonoFont,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun TimeEntryCard(entry: TimeEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ConsoleDim)
            .background(Color(0xFF0A1A0A))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.entryType.icon,
                    color = ConsoleGreen,
                    fontFamily = MonoFont,
                    fontSize = 14.sp
                )
                Text(
                    text = formatTime(entry.clockInTime),
                    color = ConsoleGreen,
                    fontFamily = MonoFont,
                    fontSize = 12.sp
                )
                Text(
                    text = "→",
                    color = ConsoleDim,
                    fontFamily = MonoFont,
                    fontSize = 12.sp
                )
                Text(
                    text = entry.clockOutTime?.let { formatTime(it) } ?: "...",
                    color = if (entry.clockOutTime != null) ConsoleGreen else ConsoleDim,
                    fontFamily = MonoFont,
                    fontSize = 12.sp
                )
            }

            entry.jobTitle?.let { job ->
                Text(
                    text = job,
                    color = ConsoleDim,
                    fontFamily = MonoFont,
                    fontSize = 10.sp
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = entry.durationMinutes?.let { formatDuration(it) } ?: "--:--",
                color = ConsoleGreen,
                fontFamily = MonoFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = entry.status.displayName,
                color = when (entry.status) {
                    EntryStatus.APPROVED -> ConsoleGreen
                    EntryStatus.PENDING_REVIEW -> Color.Yellow
                    EntryStatus.DISPUTED -> Color.Red
                    else -> ConsoleDim
                },
                fontFamily = MonoFont,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun ClockInDialog(
    onDismiss: () -> Unit,
    onClockIn: (EntryType, String?) -> Unit
) {
    var selectedType by remember { mutableStateOf(EntryType.REGULAR) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleBackground,
        title = {
            Text(
                text = "╔═══ CLOCK IN ═══╗",
                color = ConsoleGreen,
                fontFamily = MonoFont
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Entry Type:",
                    color = ConsoleDim,
                    fontFamily = MonoFont,
                    fontSize = 12.sp
                )

                EntryType.values().forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = type }
                            .padding(4.dp)
                    ) {
                        Text(
                            text = if (selectedType == type) "[●]" else "[○]",
                            color = if (selectedType == type) ConsoleGreen else ConsoleDim,
                            fontFamily = MonoFont,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${type.icon} ${type.displayName}",
                            color = if (selectedType == type) ConsoleGreen else ConsoleDim,
                            fontFamily = MonoFont,
                            fontSize = 12.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)", color = ConsoleDim, fontFamily = MonoFont) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ConsoleGreen,
                        unfocusedTextColor = ConsoleGreen,
                        focusedBorderColor = ConsoleGreen,
                        unfocusedBorderColor = ConsoleDim,
                        cursorColor = ConsoleGreen
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = MonoFont),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Text(
                text = "[▶ START]",
                color = ConsoleGreen,
                fontFamily = MonoFont,
                modifier = Modifier.clickable {
                    onClockIn(selectedType, note.ifBlank { null })
                }
            )
        },
        dismissButton = {
            Text(
                text = "[CANCEL]",
                color = ConsoleDim,
                fontFamily = MonoFont,
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    )
}

@Composable
private fun ClockOutDialog(
    elapsedSeconds: Long,
    onDismiss: () -> Unit,
    onClockOut: (String?) -> Unit
) {
    var note by remember { mutableStateOf("") }

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleBackground,
        title = {
            Text(
                text = "╔═══ CLOCK OUT ═══╗",
                color = ConsoleGreen,
                fontFamily = MonoFont
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Duration: ${hours}h ${minutes}m",
                    color = ConsoleGreen,
                    fontFamily = MonoFont,
                    fontSize = 14.sp
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)", color = ConsoleDim, fontFamily = MonoFont) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ConsoleGreen,
                        unfocusedTextColor = ConsoleGreen,
                        focusedBorderColor = ConsoleGreen,
                        unfocusedBorderColor = ConsoleDim,
                        cursorColor = ConsoleGreen
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = MonoFont),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Text(
                text = "[■ STOP]",
                color = Color.Red,
                fontFamily = MonoFont,
                modifier = Modifier.clickable {
                    onClockOut(note.ifBlank { null })
                }
            )
        },
        dismissButton = {
            Text(
                text = "[CANCEL]",
                color = ConsoleDim,
                fontFamily = MonoFont,
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    )
}

// ════════════════════════════════════════════════════════════════════
// HELPERS
// ════════════════════════════════════════════════════════════════════

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return String.format("%d:%02d", h, m)
}
