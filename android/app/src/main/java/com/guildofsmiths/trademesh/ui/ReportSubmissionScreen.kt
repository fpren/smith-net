package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.ReportType
import com.guildofsmiths.trademesh.data.SharedJob
import com.guildofsmiths.trademesh.data.SharedJobRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.jobboard.Job
import org.json.JSONObject

/**
 * Individual Report Submission Screen
 * Allows collaborators to submit their individual reports for shared jobs
 */
@Composable
fun ReportSubmissionScreen(
    sharedJobId: String,
    onBackClick: () -> Unit,
    onReportSubmitted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sharedJob = remember(sharedJobId) {
        SharedJobRepository.getSharedJob(sharedJobId)
    }

    val currentUserId = UserPreferences.getUserId()
    val collaborator = sharedJob?.collaborators?.find { it.userId == currentUserId }

    if (sharedJob == null || collaborator == null) {
        // Error state
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ConsoleTheme.background)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Job or collaborator not found", style = ConsoleTheme.body)
        }
        return
    }

    var selectedReportType by remember { mutableStateOf<ReportType?>(null) }
    var reportContent by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .clickable(onClick = onBackClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "←", style = ConsoleTheme.title)
            Spacer(modifier = Modifier.width(14.dp))
            Text(text = "SUBMIT REPORT", style = ConsoleTheme.title)
        }

        ConsoleSeparator()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Job info
            Text(text = "JOB: ${sharedJob.baseJob.title}", style = ConsoleTheme.title)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Your Role: ${collaborator.role}", style = ConsoleTheme.body)
            Spacer(modifier = Modifier.height(16.dp))

            // Report type selection
            Text(text = "REPORT TYPE", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ReportType.values()) { reportType ->
                    ReportTypeOption(
                        type = reportType,
                        selected = selectedReportType == reportType,
                        onSelect = { selectedReportType = reportType }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Report content input
            selectedReportType?.let { type ->
                Text(text = "${type.displayName.toUpperCase()} DETAILS", style = ConsoleTheme.captionBold)
                Spacer(modifier = Modifier.height(10.dp))

                ReportContentInput(
                    reportType = type,
                    content = reportContent,
                    onContentChange = { reportContent = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Submit button
                if (reportContent.isNotBlank()) {
                    Text(
                        text = "SUBMIT ${type.displayName.toUpperCase()} REPORT →",
                        style = ConsoleTheme.action,
                        modifier = Modifier
                            .clickable(enabled = !isSubmitting) {
                                isSubmitting = true
                                submitReport(sharedJobId, currentUserId, type, reportContent) {
                                    isSubmitting = false
                                    onReportSubmitted()
                                }
                            }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Report type selection option
 */
@Composable
private fun ReportTypeOption(
    type: ReportType,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(
                if (selected) ConsoleTheme.accent.copy(alpha = 0.1f)
                else ConsoleTheme.surface
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selected) "☑" else "☐",
            style = ConsoleTheme.body,
            color = if (selected) ConsoleTheme.accent else ConsoleTheme.text
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(type.displayName, style = ConsoleTheme.bodyBold)
            Text(type.description, style = ConsoleTheme.caption)
        }
    }
}

/**
 * Dynamic report content input based on type
 */
@Composable
private fun ReportContentInput(
    reportType: ReportType,
    content: String,
    onContentChange: (String) -> Unit
) {
    when (reportType) {
        ReportType.TIME_ENTRIES -> TimeEntryReport(content, onContentChange)
        ReportType.WORK_PERFORMED -> WorkPerformedReport(content, onContentChange)
        ReportType.MATERIALS_USED -> MaterialsUsedReport(content, onContentChange)
        ReportType.PHOTOS -> PhotoReport(content, onContentChange)
        ReportType.MEASUREMENTS -> MeasurementsReport(content, onContentChange)
        ReportType.ISSUES -> IssuesReport(content, onContentChange)
    }
}

/**
 * Time entry report input
 */
@Composable
private fun TimeEntryReport(content: String, onContentChange: (String) -> Unit) {
    Column {
        Text("HOURS WORKED", style = ConsoleTheme.caption)
        BasicTextField(
            value = content,
            onValueChange = onContentChange,
            textStyle = ConsoleTheme.body,
            cursorBrush = SolidColor(ConsoleTheme.cursor),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (content.isEmpty()) {
                        Text("e.g., 4.5 hours on electrical work", style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder))
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * Work performed report input
 */
@Composable
private fun WorkPerformedReport(content: String, onContentChange: (String) -> Unit) {
    Column {
        Text("WORK DESCRIPTION", style = ConsoleTheme.caption)
        BasicTextField(
            value = content,
            onValueChange = onContentChange,
            textStyle = ConsoleTheme.body,
            cursorBrush = SolidColor(ConsoleTheme.cursor),
            minLines = 3,
            maxLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (content.isEmpty()) {
                        Text(
                            "Describe the work you performed, challenges faced, and completion status...",
                            style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * Materials used report input
 */
@Composable
private fun MaterialsUsedReport(content: String, onContentChange: (String) -> Unit) {
    Column {
        Text("MATERIALS USED", style = ConsoleTheme.caption)
        BasicTextField(
            value = content,
            onValueChange = onContentChange,
            textStyle = ConsoleTheme.body,
            cursorBrush = SolidColor(ConsoleTheme.cursor),
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (content.isEmpty()) {
                        Text(
                            "List materials used: wire (50ft), outlets (12), breakers (3), etc.",
                            style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * Photo documentation report input
 */
@Composable
private fun PhotoReport(content: String, onContentChange: (String) -> Unit) {
    Column {
        Text("PHOTO DESCRIPTIONS", style = ConsoleTheme.caption)
        BasicTextField(
            value = content,
            onValueChange = onContentChange,
            textStyle = ConsoleTheme.body,
            cursorBrush = SolidColor(ConsoleTheme.cursor),
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (content.isEmpty()) {
                        Text(
                            "Describe photos taken: before/after shots, work in progress, issues found, etc.",
                            style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * Measurements report input
 */
@Composable
private fun MeasurementsReport(content: String, onContentChange: (String) -> Unit) {
    Column {
        Text("MEASUREMENTS TAKEN", style = ConsoleTheme.caption)
        BasicTextField(
            value = content,
            onValueChange = onContentChange,
            textStyle = ConsoleTheme.body,
            cursorBrush = SolidColor(ConsoleTheme.cursor),
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (content.isEmpty()) {
                        Text(
                            "Record measurements: voltage readings, circuit loads, wire sizes, distances, etc.",
                            style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * Issues encountered report input
 */
@Composable
private fun IssuesReport(content: String, onContentChange: (String) -> Unit) {
    Column {
        Text("ISSUES ENCOUNTERED & SOLUTIONS", style = ConsoleTheme.caption)
        BasicTextField(
            value = content,
            onValueChange = onContentChange,
            textStyle = ConsoleTheme.body,
            cursorBrush = SolidColor(ConsoleTheme.cursor),
            minLines = 3,
            maxLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (content.isEmpty()) {
                        Text(
                            "Describe problems found and how they were resolved...",
                            style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * Submit report to repository
 */
private fun submitReport(
    sharedJobId: String,
    collaboratorId: String,
    reportType: ReportType,
    content: String,
    onComplete: () -> Unit
) {
    // Create report data structure
    val reportData = when (reportType) {
        ReportType.TIME_ENTRIES -> JSONObject().apply {
            put("hours", content)
            put("description", "Time worked on job")
        }
        ReportType.WORK_PERFORMED -> JSONObject().apply {
            put("description", content)
            put("completion_status", "completed")
        }
        ReportType.MATERIALS_USED -> JSONObject().apply {
            put("materials", content)
            put("notes", "Materials consumed during work")
        }
        ReportType.PHOTOS -> JSONObject().apply {
            put("descriptions", content)
            put("count", content.split(",").size)
        }
        ReportType.MEASUREMENTS -> JSONObject().apply {
            put("measurements", content)
            put("units", "various")
        }
        ReportType.ISSUES -> JSONObject().apply {
            put("issues", content)
            put("resolved", true)
        }
    }.toString()

    // Submit report
    val success = SharedJobRepository.submitReport(
        jobId = sharedJobId,
        collaboratorId = collaboratorId,
        report = com.guildofsmiths.trademesh.data.JobReport(
            collaboratorId = collaboratorId,
            reportType = reportType,
            content = reportData
        )
    )

    if (success) {
        onComplete()
    } else {
        // Handle error - could show toast or error state
    }
}

/**
 * Extension to get display name for report types
 */
val ReportType.displayName: String
    get() = when (this) {
        ReportType.TIME_ENTRIES -> "Time Worked"
        ReportType.WORK_PERFORMED -> "Work Performed"
        ReportType.MATERIALS_USED -> "Materials Used"
        ReportType.PHOTOS -> "Photo Documentation"
        ReportType.MEASUREMENTS -> "Measurements"
        ReportType.ISSUES -> "Issues & Solutions"
    }

val ReportType.description: String
    get() = when (this) {
        ReportType.TIME_ENTRIES -> "Document hours worked on this job"
        ReportType.WORK_PERFORMED -> "Describe work completed and progress made"
        ReportType.MATERIALS_USED -> "List materials and supplies used"
        ReportType.PHOTOS -> "Document work with photo descriptions"
        ReportType.MEASUREMENTS -> "Record electrical measurements and readings"
        ReportType.ISSUES -> "Note problems found and how they were resolved"
    }