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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import com.guildofsmiths.trademesh.ui.theme2.SmithButton
import com.guildofsmiths.trademesh.ui.theme2.SmithButtonVariant
import com.guildofsmiths.trademesh.ui.theme2.SmithDialog
import com.guildofsmiths.trademesh.ui.theme2.SmithEmptyState
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
    viewModel: TimeTrackingViewModel = viewModel()
) {
    val colors = LocalSmithColors.current
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
            .background(colors.bgBase)
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
                style = SmithType.caption.copy(color = colors.statusError),
                modifier = Modifier
                    .padding(16.dp)
                    .clickable { viewModel.clearError() }
            )
        }

        // Main clock display
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
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
                        style = SmithType.bodyBold.copy(color = colors.statusOnline)
                    )
                }
                Text(
                    text = if (isClockedIn) "CLOCKED IN" else "CLOCKED OUT",
                    style = SmithType.captionBold.copy(
                        color = if (isClockedIn) colors.statusOnline else colors.inkMuted
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Timer display
            val hours = displaySeconds / 3600
            val minutes = (displaySeconds % 3600) / 60
            val seconds = displaySeconds % 60

            if (isClockedIn) {
                // Active timer with seconds
                Text(
                    text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                    style = SmithType.brand.copy(
                        fontSize = 52.sp,
                        color = colors.ink
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Entry info with job
                activeEntry?.let { entry ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Started ${formatTime(entry.clockInTime)} - ${entry.entryType.displayName}",
                            style = SmithType.caption.copy(color = colors.inkMuted)
                        )
                        com.guildofsmiths.trademesh.data.TimeEntryRepository
                            .resolveJobTitle(entry)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { job ->
                                Text(
                                    text = "@ $job",
                                    style = SmithType.captionBold.copy(color = colors.accent)
                                )
                            }
                    }
                }
            } else {
                Text(
                    text = "--:--:--",
                    style = SmithType.brand.copy(
                        fontSize = 52.sp,
                        color = colors.inkMuted
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Clock IN or OUT button
            if (isClockedIn) {
                // CLOCK OUT button
                Box(
                    modifier = Modifier
                        .background(colors.statusError.copy(alpha = 0.15f))
                        .clickable(enabled = !isLoading) { showClockOutDialog = true }
                        .padding(horizontal = 48.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = if (isLoading) "..." else "CLOCK OUT",
                        style = SmithType.header.copy(color = colors.statusError)
                    )
                }
            } else {
                // CLOCK IN button - opens entry type dialog
                Box(
                    modifier = Modifier
                        .background(colors.statusOnline.copy(alpha = 0.15f))
                        .clickable(enabled = !isLoading) { showClockInDialog = true }
                        .padding(horizontal = 48.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = if (isLoading) "..." else "CLOCK IN",
                        style = SmithType.header.copy(color = colors.statusOnline)
                    )
                }
            }
        }

        ConsoleSeparator()

        // Daily summary — 8 hour-slots inside [ ] with half-hour resolution,
        // overtime triangles trailing outside in red. Glyphs match the rest
        // of the app's palette (■/▣/□ shades, ▲/△ priority triangles).
        dailySummary?.let { summary ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "TODAY", style = SmithType.captionBold.copy(color = colors.inkMuted))

                // Compute today's cumulative minutes live: completed entries
                // that overlap today's window + currently-active entry clamped
                // at midnight. tickCount in the read makes it recompute every
                // second while the clock is running, so a session that crossed
                // midnight starts counting from 00:00 onward.
                @Suppress("UNUSED_EXPRESSION") tickCount
                val nowMs = System.currentTimeMillis()
                val todayStart = run {
                    val cal = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    cal.timeInMillis
                }
                val activeTodayMin = activeEntry?.let { e ->
                    if (e.clockOutTime != null) 0
                    else ((nowMs - maxOf(e.clockInTime, todayStart)) / 60_000L).toInt().coerceAtLeast(0)
                } ?: 0
                val completedTodayMin = entries.sumOf { e ->
                    val out = e.clockOutTime ?: return@sumOf 0
                    if (out <= todayStart) 0
                    else ((out - maxOf(e.clockInTime, todayStart)) / 60_000L).toInt().coerceAtLeast(0)
                }
                val totalMin = (completedTodayMin + activeTodayMin).coerceAtLeast(0)
                val targetMin = 8 * 60
                val halfSegs = (totalMin / 30).coerceAtLeast(0)
                val shiftHalves = halfSegs.coerceAtMost(16)
                val otHalves = (halfSegs - 16).coerceAtLeast(0)
                val atTarget = totalMin >= targetMin
                val shiftColor = if (atTarget) colors.statusOnline else colors.accent

                // Wrap-around overlay: once past 8h, OT halves re-color slots
                // left-to-right in red ON TOP of the sage shift fill. Same
                // bracket frame either way.
                val bar = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.inkMuted)) { append("[") }
                    for (h in 0 until 8) {
                        val otFull = (h * 2 + 2) <= otHalves
                        val otHalf = !otFull && (h * 2 + 1) == otHalves
                        val shiftFull = (h * 2 + 2) <= shiftHalves
                        val shiftHalf = !shiftFull && (h * 2 + 1) == shiftHalves
                        val (glyph, color) = when {
                            otFull -> "■" to colors.statusError
                            otHalf -> "▣" to colors.statusError
                            shiftFull -> "■" to shiftColor
                            shiftHalf -> "▣" to shiftColor
                            else -> "□" to colors.inkMuted
                        }
                        withStyle(SpanStyle(color = color)) { append(glyph) }
                    }
                    withStyle(SpanStyle(color = colors.inkMuted)) { append("]") }
                }
                Text(text = bar, style = SmithType.body.copy(color = colors.ink))

                Text(
                    text = "${formatDuration(totalMin)} / 8:00",
                    style = SmithType.bodyBold.copy(
                        color = if (atTarget) colors.statusError else colors.ink
                    )
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
            Text(text = "TODAY'S ENTRIES", style = SmithType.captionBold.copy(color = colors.inkMuted))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "< SWIPE TO DELETE",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
                Text(
                    text = "REFRESH",
                    style = SmithType.action.copy(color = colors.accent),
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
                    SmithEmptyState(title = "No entries today")
                }
            }
        }
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

        SmithDialog(
            title = "Clock in",
            onDismiss = { showClockInDialog = false },
            sizeFraction = 0.9f to 0.8f,
            actions = {
                SmithButton(text = "CANCEL", onClick = { showClockInDialog = false }, variant = SmithButtonVariant.Ghost)
                Spacer(modifier = Modifier.width(8.dp))
                SmithButton(
                    text = "CLOCK IN",
                    onClick = {
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
                    },
                    enabled = selectedType != null,
                )
            },
        ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Step 1: Entry Type
                    Text(text = "1. SELECT ENTRY TYPE", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    
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
                                    if (isSelected) colors.accent.copy(alpha = 0.1f)
                                    else colors.bgPanel
                                )
                                .clickable { selectedType = type }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = type.displayName.uppercase(),
                                    style = SmithType.bodyBold.copy(
                                        color = if (isSelected) colors.accent else colors.ink
                                    )
                                )
                                Text(text = desc, style = SmithType.caption.copy(color = colors.inkMuted))
                            }
                            if (isSelected) {
                                Text(text = "[x]", style = SmithType.action.copy(color = colors.accent))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    ConsoleSeparator()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Step 2: Job (optional)
                    Text(text = "2. TAG TO JOB (optional)", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // No job option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selectedJob == null && customJobName.isEmpty()) 
                                    colors.accent.copy(alpha = 0.1f)
                                else colors.bgPanel
                            )
                            .clickable { 
                                selectedJob = null
                                customJobName = ""
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "No job (general time)", style = SmithType.body.copy(color = colors.ink))
                        if (selectedJob == null && customJobName.isEmpty()) {
                            Text(text = "[x]", style = SmithType.action.copy(color = colors.accent))
                        }
                    }

                    // Available jobs from job board
                    availableJobs.forEach { job ->
                        val isSelected = selectedJob == job
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) colors.accent.copy(alpha = 0.1f)
                                    else colors.bgPanel
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
                                style = SmithType.body.copy(
                                    color = if (isSelected) colors.accent else colors.ink
                                )
                            )
                            if (isSelected) {
                                Text(text = "[x]", style = SmithType.action.copy(color = colors.accent))
                            }
                        }
                    }

                    // Custom job name
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Or enter job name:", style = SmithType.caption.copy(color = colors.inkMuted))
                    BasicTextField(
                        value = customJobName,
                        onValueChange = { 
                            customJobName = it
                            if (it.isNotEmpty()) selectedJob = null
                        },
                        textStyle = SmithType.body.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.ink),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bgPanel)
                            .padding(12.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (customJobName.isEmpty()) {
                                    Text("Type job name...", style = SmithType.body.copy(color = colors.inkMuted))
                                }
                                innerTextField()
                            }
                        }
                    )
                }
        }
    }

    // Clock OUT Dialog - Select reason
    if (showClockOutDialog) {
        var selectedReason by remember { mutableStateOf<ClockOutReason?>(null) }
        var otherNote by remember { mutableStateOf("") }
        
        val hours = displaySeconds / 3600
        val minutes = (displaySeconds % 3600) / 60

        SmithDialog(
            title = "Clock out",
            onDismiss = { showClockOutDialog = false },
            sizeFraction = 0.9f to 0.75f,
            actions = {
                SmithButton(text = "CANCEL", onClick = { showClockOutDialog = false }, variant = SmithButtonVariant.Ghost)
                Spacer(modifier = Modifier.width(8.dp))
                SmithButton(
                    text = "CLOCK OUT",
                    onClick = {
                        selectedReason?.let { reason ->
                            val note = if (reason == ClockOutReason.OTHER) otherNote else reason.displayName
                            viewModel.clockOut(note)
                            showClockOutDialog = false
                        }
                    },
                    enabled = selectedReason != null,
                )
            },
        ) {
            Text(
                text = "Duration: ${hours}h ${minutes}m",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
            activeEntry?.let { e ->
                com.guildofsmiths.trademesh.data.TimeEntryRepository
                    .resolveJobTitle(e)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { job ->
                        Text(
                            text = "Job: $job",
                            style = SmithType.captionBold.copy(color = colors.accent)
                        )
                    }
            }
            Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "SELECT REASON", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ClockOutReason.values().forEach { reason ->
                        val isSelected = selectedReason == reason
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) colors.accent.copy(alpha = 0.1f)
                                    else colors.bgPanel
                                )
                                .clickable { selectedReason = reason }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = reason.displayName,
                                style = SmithType.body.copy(
                                    color = if (isSelected) colors.accent else colors.ink
                                )
                            )
                            if (isSelected) {
                                Text(text = "[x]", style = SmithType.action.copy(color = colors.accent))
                            }
                        }
                    }
                    
                    // Note field for "Other"
                    if (selectedReason == ClockOutReason.OTHER) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "SPECIFY REASON", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        BasicTextField(
                            value = otherNote,
                            onValueChange = { otherNote = it },
                            textStyle = SmithType.body.copy(color = colors.ink),
                            cursorBrush = SolidColor(colors.ink),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.bgPanel)
                                .padding(12.dp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (otherNote.isEmpty()) {
                                        Text("Enter reason...", style = SmithType.body.copy(color = colors.inkMuted))
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
        }
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
    val colors = LocalSmithColors.current
    var offsetX by remember { mutableStateOf(0f) }
    val deleteThreshold = -200f
    var isDeleting by remember { mutableStateOf(false) }
    
    val backgroundColor by animateColorAsState(
        targetValue = if (offsetX < deleteThreshold / 2) colors.statusError.copy(alpha = 0.3f) 
                      else colors.bgPanel,
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
                    style = SmithType.captionBold.copy(color = colors.statusError)
                )
            }
        }

        // Entry content (swipeable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(colors.bgPanel)
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
                    Text(text = formatTime(entry.clockInTime), style = SmithType.body.copy(color = colors.ink))
                    Text(text = "-", style = SmithType.caption.copy(color = colors.inkMuted))
                    Text(
                        text = entry.clockOutTime?.let { formatTime(it) } ?: "NOW",
                        style = SmithType.body.copy(
                            color = if (entry.clockOutTime != null) colors.ink 
                                    else colors.statusOnline
                        )
                    )
                }
                
                // Type, job, and reason
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = entry.entryType.displayName,
                        style = SmithType.caption.copy(color = colors.inkMuted)
                    )
                    // Show job if tagged (resolved through Job for live employer renames)
                    com.guildofsmiths.trademesh.data.TimeEntryRepository
                        .resolveJobTitle(entry)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { job ->
                            Text(
                                text = "@ $job",
                                style = SmithType.captionBold.copy(color = colors.accent)
                            )
                        }
                    // Show clock out reason if present
                    entry.notes.lastOrNull()?.text?.let { note ->
                        Text(
                            text = "- $note",
                            style = SmithType.caption.copy(color = colors.inkMuted)
                        )
                    }
                }
            }

            // Duration
            Text(
                text = entry.durationMinutes?.let { formatDuration(it) } ?: "--:--",
                style = SmithType.bodyBold.copy(color = colors.ink)
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
