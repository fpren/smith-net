package com.guildofsmiths.trademesh.ui.jobboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * C-11: Job Board Screen
 * Kanban-style task board with pixel art aesthetic
 */

private val ConsoleGreen = Color(0xFF00FF00)
private val ConsoleDim = Color(0xFF00AA00)
private val ConsoleBackground = Color(0xFF0A0A0A)
private val ConsoleBorder = Color(0xFF00DD00)
private val MonoFont = FontFamily.Monospace

@Composable
fun JobBoardScreen(
    onNavigateBack: () -> Unit,
    viewModel: JobBoardViewModel = viewModel()
) {
    val jobs by viewModel.jobs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedJob by viewModel.selectedJob.collectAsState()
    val tasks by viewModel.tasks.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleBackground)
            .padding(8.dp)
    ) {
        // Header
        JobBoardHeader(
            onBack = onNavigateBack,
            onAdd = { showCreateDialog = true },
            onRefresh = { viewModel.loadJobs() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Error banner
        error?.let { errorMsg ->
            Text(
                text = "[!] $errorMsg",
                color = Color.Red,
                fontFamily = MonoFont,
                fontSize = 12.sp,
                modifier = Modifier.padding(4.dp)
            )
        }

        // Loading indicator
        if (isLoading) {
            Text(
                text = "[...] Loading jobs...",
                color = ConsoleDim,
                fontFamily = MonoFont,
                fontSize = 12.sp,
                modifier = Modifier.padding(4.dp)
            )
        }

        // Kanban Board
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val statuses = listOf(
                JobStatus.BACKLOG,
                JobStatus.TODO,
                JobStatus.IN_PROGRESS,
                JobStatus.REVIEW,
                JobStatus.DONE
            )

            items(statuses) { status ->
                KanbanColumn(
                    status = status,
                    jobs = jobs.filter { it.status == status },
                    onJobClick = { viewModel.selectJob(it) },
                    onMoveJob = { job, newStatus -> viewModel.moveJob(job.id, newStatus) }
                )
            }
        }
    }

    // Create Dialog
    if (showCreateDialog) {
        CreateJobDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, desc, priority ->
                viewModel.createJob(title, desc, priority)
                showCreateDialog = false
            }
        )
    }

    // Job Detail Dialog
    selectedJob?.let { job ->
        JobDetailDialog(
            job = job,
            tasks = tasks,
            onDismiss = { viewModel.selectJob(null) },
            onMoveJob = { status -> viewModel.moveJob(job.id, status) },
            onCreateTask = { title -> viewModel.createTask(job.id, title) },
            onDelete = { viewModel.deleteJob(job.id) }
        )
    }
}

@Composable
private fun JobBoardHeader(
    onBack: () -> Unit,
    onAdd: () -> Unit,
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
                text = "╔═══ JOB BOARD ═══╗",
                color = ConsoleGreen,
                fontFamily = MonoFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "[↻]",
                color = ConsoleGreen,
                fontFamily = MonoFont,
                fontSize = 14.sp,
                modifier = Modifier.clickable { onRefresh() }
            )
            Text(
                text = "[+]",
                color = ConsoleGreen,
                fontFamily = MonoFont,
                fontSize = 14.sp,
                modifier = Modifier.clickable { onAdd() }
            )
        }
    }
}

@Composable
private fun KanbanColumn(
    status: JobStatus,
    jobs: List<Job>,
    onJobClick: (Job) -> Unit,
    onMoveJob: (Job, JobStatus) -> Unit
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .fillMaxHeight()
            .border(1.dp, ConsoleBorder)
    ) {
        // Column header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF001100))
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = status.icon,
                    color = ConsoleGreen,
                    fontFamily = MonoFont,
                    fontSize = 14.sp
                )
                Text(
                    text = status.displayName.uppercase(),
                    color = ConsoleGreen,
                    fontFamily = MonoFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "(${jobs.size})",
                    color = ConsoleDim,
                    fontFamily = MonoFont,
                    fontSize = 10.sp
                )
            }
        }

        // Jobs list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(jobs) { job ->
                JobCard(
                    job = job,
                    onClick = { onJobClick(job) },
                    onMoveLeft = {
                        val prev = getPreviousStatus(status)
                        if (prev != null) onMoveJob(job, prev)
                    },
                    onMoveRight = {
                        val next = getNextStatus(status)
                        if (next != null) onMoveJob(job, next)
                    }
                )
            }
        }
    }
}

@Composable
private fun JobCard(
    job: Job,
    onClick: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ConsoleDim)
            .background(Color(0xFF0A1A0A))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        // Priority indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${job.priority.icon} ${job.priority.displayName}",
                color = getPriorityColor(job.priority),
                fontFamily = MonoFont,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Title
        Text(
            text = job.title,
            color = ConsoleGreen,
            fontFamily = MonoFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Description preview
        if (job.description.isNotEmpty()) {
            Text(
                text = job.description,
                color = ConsoleDim,
                fontFamily = MonoFont,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Move buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "[◀]",
                color = if (job.status != JobStatus.BACKLOG) ConsoleGreen else ConsoleDim,
                fontFamily = MonoFont,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onMoveLeft() }
            )
            Text(
                text = "[▶]",
                color = if (job.status != JobStatus.DONE) ConsoleGreen else ConsoleDim,
                fontFamily = MonoFont,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onMoveRight() }
            )
        }
    }
}

@Composable
private fun CreateJobDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Priority) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleBackground,
        title = {
            Text(
                text = "╔═══ NEW JOB ═══╗",
                color = ConsoleGreen,
                fontFamily = MonoFont
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title", color = ConsoleDim, fontFamily = MonoFont) },
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

                // Description input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", color = ConsoleDim, fontFamily = MonoFont) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ConsoleGreen,
                        unfocusedTextColor = ConsoleGreen,
                        focusedBorderColor = ConsoleGreen,
                        unfocusedBorderColor = ConsoleDim,
                        cursorColor = ConsoleGreen
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = MonoFont),
                    maxLines = 3
                )

                // Priority selector
                Text(
                    text = "Priority:",
                    color = ConsoleDim,
                    fontFamily = MonoFont,
                    fontSize = 12.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Priority.values().forEach { p ->
                        Text(
                            text = "[${if (priority == p) "●" else "○"}] ${p.displayName}",
                            color = if (priority == p) getPriorityColor(p) else ConsoleDim,
                            fontFamily = MonoFont,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { priority = p }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Text(
                text = "[CREATE]",
                color = if (title.isNotBlank()) ConsoleGreen else ConsoleDim,
                fontFamily = MonoFont,
                modifier = Modifier.clickable {
                    if (title.isNotBlank()) {
                        onCreate(title, description, priority)
                    }
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
private fun JobDetailDialog(
    job: Job,
    tasks: List<Task>,
    onDismiss: () -> Unit,
    onMoveJob: (JobStatus) -> Unit,
    onCreateTask: (String) -> Unit,
    onDelete: () -> Unit
) {
    var newTaskTitle by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleBackground,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${job.priority.icon} ${job.title}",
                        color = ConsoleGreen,
                        fontFamily = MonoFont,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "[✕]",
                        color = ConsoleGreen,
                        fontFamily = MonoFont,
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }
                Text(
                    text = "Status: ${job.status.displayName}",
                    color = ConsoleDim,
                    fontFamily = MonoFont,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Description
                if (job.description.isNotEmpty()) {
                    Text(
                        text = job.description,
                        color = ConsoleGreen,
                        fontFamily = MonoFont,
                        fontSize = 12.sp
                    )
                }

                // Move buttons
                Text(
                    text = "Move to:",
                    color = ConsoleDim,
                    fontFamily = MonoFont,
                    fontSize = 12.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    JobStatus.values().filter { it != JobStatus.ARCHIVED }.forEach { status ->
                        Text(
                            text = "[${status.icon}]",
                            color = if (job.status == status) ConsoleGreen else ConsoleDim,
                            fontFamily = MonoFont,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable {
                                if (job.status != status) onMoveJob(status)
                            }
                        )
                    }
                }

                // Tasks section
                Text(
                    text = "╔═══ TASKS ═══╗",
                    color = ConsoleGreen,
                    fontFamily = MonoFont,
                    fontSize = 12.sp
                )

                tasks.forEach { task ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = when (task.status) {
                                TaskStatus.DONE -> "[✓]"
                                TaskStatus.IN_PROGRESS -> "[▶]"
                                TaskStatus.BLOCKED -> "[!]"
                                else -> "[ ]"
                            },
                            color = when (task.status) {
                                TaskStatus.DONE -> ConsoleGreen
                                TaskStatus.BLOCKED -> Color.Red
                                else -> ConsoleDim
                            },
                            fontFamily = MonoFont,
                            fontSize = 12.sp
                        )
                        Text(
                            text = task.title,
                            color = ConsoleGreen,
                            fontFamily = MonoFont,
                            fontSize = 12.sp
                        )
                    }
                }

                // Add task
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newTaskTitle,
                        onValueChange = { newTaskTitle = it },
                        placeholder = { Text("New task...", color = ConsoleDim, fontFamily = MonoFont) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ConsoleGreen,
                            unfocusedTextColor = ConsoleGreen,
                            focusedBorderColor = ConsoleGreen,
                            unfocusedBorderColor = ConsoleDim,
                            cursorColor = ConsoleGreen
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = MonoFont, fontSize = 12.sp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "[+]",
                        color = if (newTaskTitle.isNotBlank()) ConsoleGreen else ConsoleDim,
                        fontFamily = MonoFont,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            if (newTaskTitle.isNotBlank()) {
                                onCreateTask(newTaskTitle)
                                newTaskTitle = ""
                            }
                        }
                    )
                }

                // Delete
                Spacer(modifier = Modifier.height(16.dp))
                if (!showDeleteConfirm) {
                    Text(
                        text = "[DELETE JOB]",
                        color = Color.Red.copy(alpha = 0.7f),
                        fontFamily = MonoFont,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { showDeleteConfirm = true }
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Confirm?",
                            color = Color.Red,
                            fontFamily = MonoFont,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "[YES]",
                            color = Color.Red,
                            fontFamily = MonoFont,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { onDelete() }
                        )
                        Text(
                            text = "[NO]",
                            color = ConsoleDim,
                            fontFamily = MonoFont,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { showDeleteConfirm = false }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

// ════════════════════════════════════════════════════════════════════
// HELPERS
// ════════════════════════════════════════════════════════════════════

private fun getPriorityColor(priority: Priority): Color {
    return when (priority) {
        Priority.LOW -> Color(0xFF00AA00)
        Priority.MEDIUM -> Color(0xFF00FF00)
        Priority.HIGH -> Color(0xFFFFFF00)
        Priority.URGENT -> Color(0xFFFF0000)
    }
}

private fun getPreviousStatus(current: JobStatus): JobStatus? {
    return when (current) {
        JobStatus.TODO -> JobStatus.BACKLOG
        JobStatus.IN_PROGRESS -> JobStatus.TODO
        JobStatus.REVIEW -> JobStatus.IN_PROGRESS
        JobStatus.DONE -> JobStatus.REVIEW
        else -> null
    }
}

private fun getNextStatus(current: JobStatus): JobStatus? {
    return when (current) {
        JobStatus.BACKLOG -> JobStatus.TODO
        JobStatus.TODO -> JobStatus.IN_PROGRESS
        JobStatus.IN_PROGRESS -> JobStatus.REVIEW
        JobStatus.REVIEW -> JobStatus.DONE
        else -> null
    }
}
