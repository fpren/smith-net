package com.guildofsmiths.trademesh.ui.timetracking

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * C-12: Time Tracking Screen
 * Clock in/out interface matching Smith Net UI
 */

// Clock out reasons
enum class ClockOutReason(val displayName: String, val icon: String) {
    LUNCH("Lunch Break", "LUNCH"),
    JOB_DONE("Job Completed", "DONE"),
    END_DAY("End of Day", "END"),
    BREAK("Short Break", "BREAK"),
    OTHER("Other", "OTHER")
}

@Composable
fun TimeTrackingScreen(
    onNavigateBack: () -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: TimeTrackingViewModel = viewModel()
) {
    val isClockedIn by viewModel.isClockedIn.collectAsState()
    val activeEntry by viewModel.activeEntry.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val dailySummary by viewModel.dailySummary.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val availableJobs by viewModel.availableJobs.collectAsState()

    // Active ticking timer - recalculate every second
    var tickCount by remember { mutableStateOf(0) }
    var displaySeconds by remember { mutableStateOf(0L) }
    
    // Timer that ticks every second
    LaunchedEffect(isClockedIn, activeEntry, tickCount) {
        val entry = activeEntry
        if (isClockedIn && entry != null) {
            displaySeconds = (System.currentTimeMillis() - entry.clockInTime) / 1000
        }
    }
    
    // Tick every second when clocked in
    LaunchedEffect(isClockedIn) {
        while (isClockedIn) {
            delay(1000)
            tickCount++
        }
    }

    var showClockInDialog by remember { mutableStateOf(false) }
    var showClockOutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        // Header
        ConsoleHeader(
            title = "TIME CLOCK",
            onBackClick = onNavigateBack
        )
        
        ConsoleSeparator()

        // Error banner
        error?.let { errorMsg ->
            Text(
                text = "! $errorMsg",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.error),
                modifier = Modifier
                    .padding(16.dp)
                    .clickable { viewModel.clearError() }
            )
        }

        // Main clock display
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status with blinking indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isClockedIn) {
                    // Blinking dot
                    val visible = tickCount % 2 == 0
                    Text(
                        text = if (visible) ">" else " ",
                        style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.success)
                    )
                }
                Text(
                    text = if (isClockedIn) "CLOCKED IN" else "CLOCKED OUT",
                    style = ConsoleTheme.captionBold.copy(
                        color = if (isClockedIn) ConsoleTheme.success else ConsoleTheme.textMuted
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Timer display - CONSISTENT MONOSPACE
            val hours = displaySeconds / 3600
            val minutes = (displaySeconds % 3600) / 60
            val seconds = displaySeconds % 60

            // Always use same structure and font for stability
            Text(
                text = if (isClockedIn) {
                    String.format("%02d:%02d:%02d", hours, minutes, seconds)
                } else {
                    "00:00:00"
                },
                style = TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 48.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = if (isClockedIn) ConsoleTheme.text else ConsoleTheme.textDim,
                    letterSpacing = 2.sp
                )
            )
            
            if (isClockedIn) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Entry info with job
                activeEntry?.let { entry ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Started ${formatTime(entry.clockInTime)} - ${entry.entryType.displayName}",
                            style = ConsoleTheme.caption
                        )
                        entry.jobTitle?.let { job ->
                            Text(
                                text = "@ $job",
                                style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Clock IN or OUT button
            if (isClockedIn) {
                // CLOCK OUT button
                Box(
                    modifier = Modifier
                        .background(ConsoleTheme.error.copy(alpha = 0.15f))
                        .clickable(enabled = !isLoading) { showClockOutDialog = true }
                        .padding(horizontal = 48.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = if (isLoading) "..." else "CLOCK OUT",
                        style = ConsoleTheme.header.copy(color = ConsoleTheme.error)
                    )
                }
            } else {
                // CLOCK IN button - opens entry type dialog
                Box(
                    modifier = Modifier
                        .background(ConsoleTheme.success.copy(alpha = 0.15f))
                        .clickable(enabled = !isLoading) { showClockInDialog = true }
                        .padding(horizontal = 48.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = if (isLoading) "..." else "CLOCK IN",
                        style = ConsoleTheme.header.copy(color = ConsoleTheme.success)
                    )
                }
            }
        }

        ConsoleSeparator()

        // Daily summary bar
        dailySummary?.let { summary ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "TODAY", style = ConsoleTheme.captionBold)
                
                val targetMinutes = 8 * 60
                val progress = (summary.totalMinutes.toFloat() / targetMinutes).coerceIn(0f, 1f)
                val bars = (progress * 10).toInt()
                
                Text(
                    text = "[" + "=".repeat(bars) + "-".repeat(10 - bars) + "]",
                    style = ConsoleTheme.body.copy(
                        color = if (progress >= 1f) ConsoleTheme.success else ConsoleTheme.textMuted
                    )
                )
                
                Text(
                    text = "${formatDuration(summary.totalMinutes)} / 8:00",
                    style = ConsoleTheme.bodyBold
                )
            }
            ConsoleSeparator()
        }

        // Entries header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "TODAY'S ENTRIES", style = ConsoleTheme.captionBold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "< SWIPE TO DELETE",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                )
                Text(
                    text = "REFRESH",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable {
                        viewModel.loadStatus()
                        viewModel.loadEntries()
                        viewModel.loadDailySummary()
                    }
                )
            }
        }

        // Entries list with swipe to delete
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                SwipeToDeleteEntry(
                    entry = entry,
                    onDelete = { viewModel.deleteEntry(entry.id) }
                )
            }
            
            if (entries.isEmpty()) {
                item {
                    Text(
                        text = "No entries today",
                        style = ConsoleTheme.caption,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        
        // Footer - SETTINGS + Made by Guild of Smiths
        com.guildofsmiths.trademesh.ui.SmithNetSharedFooter(onSettingsClick = onSettingsClick)
    }

    // Clock IN Dialog - Select entry type and optional job
    if (showClockInDialog) {
        // Refresh available jobs when dialog opens
        LaunchedEffect(Unit) {
            viewModel.refreshAvailableJobs()
        }
        
        var selectedType by remember { mutableStateOf<EntryType?>(null) }
        var selectedJob by remember { mutableStateOf<String?>(null) }
        var customJobName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showClockInDialog = false },
            containerColor = ConsoleTheme.background,
            modifier = Modifier.fillMaxHeight(0.8f),
            title = {
                Text(text = "CLOCK IN", style = ConsoleTheme.header)
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Step 1: Entry Type
                    Text(text = "1. SELECT ENTRY TYPE", style = ConsoleTheme.captionBold)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    listOf(
                        EntryType.REGULAR to "Regular work hours",
                        EntryType.OVERTIME to "Overtime hours",
                        EntryType.BREAK to "Break time",
                        EntryType.TRAVEL to "Travel time",
                        EntryType.ON_CALL to "On-call hours"
                    ).forEach { (type, desc) ->
                        val isSelected = selectedType == type
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) ConsoleTheme.accent.copy(alpha = 0.1f)
                                    else ConsoleTheme.surface
                                )
                                .clickable { selectedType = type }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = type.displayName.uppercase(),
                                    style = ConsoleTheme.bodyBold.copy(
                                        color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.text
                                    )
                                )
                                Text(text = desc, style = ConsoleTheme.caption)
                            }
                            if (isSelected) {
                                Text(text = "[x]", style = ConsoleTheme.action)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    ConsoleSeparator()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Step 2: Job (optional)
                    Text(text = "2. TAG TO JOB (optional)", style = ConsoleTheme.captionBold)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // No job option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selectedJob == null && customJobName.isEmpty()) 
                                    ConsoleTheme.accent.copy(alpha = 0.1f)
                                else ConsoleTheme.surface
                            )
                            .clickable { 
                                selectedJob = null
                                customJobName = ""
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "No job (general time)", style = ConsoleTheme.body)
                        if (selectedJob == null && customJobName.isEmpty()) {
                            Text(text = "[x]", style = ConsoleTheme.action)
                        }
                    }

                    // Available jobs from job board
                    availableJobs.forEach { job ->
                        val isSelected = selectedJob == job
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) ConsoleTheme.accent.copy(alpha = 0.1f)
                                    else ConsoleTheme.surface
                                )
                                .clickable { 
                                    selectedJob = job
                                    customJobName = ""
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = job,
                                style = ConsoleTheme.body.copy(
                                    color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.text
                                )
                            )
                            if (isSelected) {
                                Text(text = "[x]", style = ConsoleTheme.action)
                            }
                        }
                    }

                    // Custom job name
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Or enter job name:", style = ConsoleTheme.caption)
                    BasicTextField(
                        value = customJobName,
                        onValueChange = { 
                            customJobName = it
                            if (it.isNotEmpty()) selectedJob = null
                        },
                        textStyle = ConsoleTheme.body,
                        cursorBrush = SolidColor(ConsoleTheme.cursor),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface)
                            .padding(12.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (customJobName.isEmpty()) {
                                    Text("Type job name...", style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder))
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Text(
                    text = "CLOCK IN",
                    style = ConsoleTheme.action.copy(
                        color = if (selectedType != null) ConsoleTheme.success else ConsoleTheme.textDim
                    ),
                    modifier = Modifier.clickable {
                        selectedType?.let { type ->
                            val jobName = when {
                                customJobName.isNotBlank() -> customJobName
                                selectedJob != null -> selectedJob
                                else -> null
                            }
                            viewModel.clockIn(
                                entryType = type,
                                jobTitle = jobName
                            )
                            showClockInDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                Text(
                    text = "CANCEL",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier.clickable { showClockInDialog = false }
                )
            }
        )
    }

    // Clock OUT Dialog - Select reason
    if (showClockOutDialog) {
        var selectedReason by remember { mutableStateOf<ClockOutReason?>(null) }
        var otherNote by remember { mutableStateOf("") }
        
        val hours = displaySeconds / 3600
        val minutes = (displaySeconds % 3600) / 60

        AlertDialog(
            onDismissRequest = { showClockOutDialog = false },
            containerColor = ConsoleTheme.background,
            modifier = Modifier.fillMaxHeight(0.75f),
            title = {
                Column {
                    Text(text = "CLOCK OUT", style = ConsoleTheme.header)
                    Text(
                        text = "Duration: ${hours}h ${minutes}m",
                        style = ConsoleTheme.caption
                    )
                    activeEntry?.jobTitle?.let { job ->
                        Text(
                            text = "Job: $job",
                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "SELECT REASON", style = ConsoleTheme.captionBold)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ClockOutReason.values().forEach { reason ->
                        val isSelected = selectedReason == reason
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) ConsoleTheme.accent.copy(alpha = 0.1f)
                                    else ConsoleTheme.surface
                                )
                                .clickable { selectedReason = reason }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = reason.displayName,
                                style = ConsoleTheme.body.copy(
                                    color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.text
                                )
                            )
                            if (isSelected) {
                                Text(text = "[x]", style = ConsoleTheme.action)
                            }
                        }
                    }
                    
                    // Note field for "Other"
                    if (selectedReason == ClockOutReason.OTHER) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "SPECIFY REASON", style = ConsoleTheme.captionBold)
                        BasicTextField(
                            value = otherNote,
                            onValueChange = { otherNote = it },
                            textStyle = ConsoleTheme.body,
                            cursorBrush = SolidColor(ConsoleTheme.cursor),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ConsoleTheme.surface)
                                .padding(12.dp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (otherNote.isEmpty()) {
                                        Text("Enter reason...", style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder))
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Text(
                    text = "CLOCK OUT",
                    style = ConsoleTheme.action.copy(
                        color = if (selectedReason != null) ConsoleTheme.error else ConsoleTheme.textDim
                    ),
                    modifier = Modifier.clickable {
                        selectedReason?.let { reason ->
                            val note = if (reason == ClockOutReason.OTHER) otherNote else reason.displayName
                            viewModel.clockOut(note)
                            showClockOutDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                Text(
                    text = "CANCEL",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier.clickable { showClockOutDialog = false }
                )
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// SWIPE TO DELETE ENTRY
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SwipeToDeleteEntry(
    entry: TimeEntry,
    onDelete: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val deleteThreshold = -200f
    var isDeleting by remember { mutableStateOf(false) }
    
    val backgroundColor by animateColorAsState(
        targetValue = if (offsetX < deleteThreshold / 2) ConsoleTheme.error.copy(alpha = 0.3f) 
                      else ConsoleTheme.surface,
        label = "bgColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        // Delete indicator (behind the entry)
        if (offsetX < 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = if (offsetX < deleteThreshold) "RELEASE TO DELETE" else "< DELETE",
                    style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.error)
                )
            }
        }

        // Entry content (swipeable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(ConsoleTheme.surface)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < deleteThreshold) {
                                isDeleting = true
                                onDelete()
                            }
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            // Only allow left swipe
                            val newOffset = offsetX + dragAmount
                            offsetX = newOffset.coerceIn(-300f, 0f)
                        }
                    )
                }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                // Time range
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = formatTime(entry.clockInTime), style = ConsoleTheme.body)
                    Text(text = "-", style = ConsoleTheme.caption)
                    Text(
                        text = entry.clockOutTime?.let { formatTime(it) } ?: "NOW",
                        style = ConsoleTheme.body.copy(
                            color = if (entry.clockOutTime != null) ConsoleTheme.text 
                                    else ConsoleTheme.success
                        )
                    )
                }
                
                // Type, job, and reason
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = entry.entryType.displayName,
                        style = ConsoleTheme.caption
                    )
                    // Show job if tagged
                    entry.jobTitle?.let { job ->
                        Text(
                            text = "@ $job",
                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent)
                        )
                    }
                    // Show clock out reason if present
                    entry.notes.lastOrNull()?.text?.let { note ->
                        Text(
                            text = "- $note",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                    }
                }
            }

            // Duration
            Text(
                text = entry.durationMinutes?.let { formatDuration(it) } ?: "--:--",
                style = ConsoleTheme.bodyBold
            )
        }
    }
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
