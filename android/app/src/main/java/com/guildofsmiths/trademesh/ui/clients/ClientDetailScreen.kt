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
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import com.guildofsmiths.trademesh.ui.jobboard.Task
import com.guildofsmiths.trademesh.ui.jobboard.TaskStatus
import com.guildofsmiths.trademesh.ui.jobboard.WorkLogEntry
import com.guildofsmiths.trademesh.ui.jobboard.Priority
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import java.text.SimpleDateFormat
import java.util.*

private val SHORT = SimpleDateFormat("MMM d", Locale.US)
private val SHORT_YEAR = SimpleDateFormat("MMM d, yyyy", Locale.US)

@Composable
fun ClientDetailScreen(
    clientName: String,
    allJobs: List<Job>,
    allTasks: List<Task> = emptyList(),
    onJobClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalSmithColors.current
    val context = LocalContext.current
    val clientJobs = remember(clientName, allJobs) {
        ClientRepository.getJobsForClient(clientName, allJobs)
    }
    val override = remember(clientName, allJobs) { ClientRepository.getClientOverride(clientName) }
    val latestJob = clientJobs.maxByOrNull { it.updatedAt }

    val displayName = override?.name ?: clientName
    val displayPhone = override?.phone ?: latestJob?.clientPhone ?: ""
    val displayAddress = override?.address ?: latestJob?.clientAddress ?: ""
    val displayEmail = override?.email.orEmpty()

    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(displayName) }
    var editPhone by remember { mutableStateOf(displayPhone) }
    var editEmail by remember { mutableStateOf(displayEmail) }
    var editAddress by remember { mutableStateOf(displayAddress) }

    val openJobs = clientJobs.filter { it.stage != JobStage.CLOSED }
    val recentClosed = clientJobs
        .filter { it.stage == JobStage.CLOSED }
        .sortedByDescending { it.completedAt ?: it.actualEndDate ?: it.updatedAt }
        .take(10)
    val lastServiceMs = clientJobs
        .filter { it.stage == JobStage.CLOSED }
        .mapNotNull { it.completedAt ?: it.actualEndDate }
        .maxOrNull()
    val createdMs = clientJobs.minOfOrNull { it.createdAt }

    val openJobIds = openJobs.map { it.id }.toSet()
    val pendingTasks = allTasks
        .filter { it.jobId in openJobIds && it.status != TaskStatus.DONE }
        .sortedWith(compareBy({ it.status.ordinal }, { it.updatedAt }))

    fun jobEstimate(j: Job): Double =
        j.materials.sumOf { it.totalCost } + (j.estimatedHours.takeIf { it > 0.0 } ?: 8.0) * j.hourlyRate
    fun jobBilled(j: Job): Double = j.materials.sumOf { it.totalCost } + (j.hourlyRate * 8)

    val balanceDue = openJobs
        .filter { it.stage == JobStage.INVOICE || it.stage == JobStage.REVIEW }
        .sumOf { (jobBilled(it) - it.depositCollected).coerceAtLeast(0.0) }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.bgBase)
    ) {
        ConsoleHeader(
            title = displayName,
            onBackClick = onBack,
            actionText = if (isEditing) "[Save]" else "[Edit]",
            onActionClick = {
                if (isEditing) {
                    ClientRepository.saveClientOverride(
                        clientName,
                        editName.trim(),
                        editPhone.trim(),
                        editAddress.trim(),
                        editEmail.trim()
                    )
                    isEditing = false
                } else {
                    editName = displayName
                    editPhone = displayPhone
                    editAddress = displayAddress
                    editEmail = displayEmail
                    isEditing = true
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HeaderStrip(
                isEditing = isEditing,
                editName = editName, onNameChange = { editName = it },
                editPhone = editPhone, onPhoneChange = { editPhone = it },
                editEmail = editEmail, onEmailChange = { editEmail = it },
                editAddress = editAddress, onAddressChange = { editAddress = it },
                onCancel = { isEditing = false },
                phone = displayPhone,
                email = displayEmail,
                address = displayAddress,
                openCount = openJobs.size,
                lastServiceMs = lastServiceMs,
                createdMs = createdMs,
                balanceDue = balanceDue,
                onCallPhone = {
                    if (displayPhone.isNotBlank()) context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$displayPhone"))
                    )
                },
                onMapAddress = {
                    if (displayAddress.isNotBlank()) context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(displayAddress)}"))
                    )
                },
                onEmailClick = {
                    if (displayEmail.isNotBlank()) context.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$displayEmail"))
                    )
                }
            )

            Section("CURRENT JOBS  (${openJobs.size})") {
                if (openJobs.isEmpty()) {
                    Empty("No active jobs.")
                } else {
                    openJobs.sortedByDescending { it.updatedAt }.forEachIndexed { i, j ->
                        CurrentJobRow(j, jobEstimate(j), onClick = { onJobClick(j.id) })
                        if (i < openJobs.lastIndex) Divider()
                    }
                }
            }

            Section("PENDING TASKS  (${pendingTasks.size})") {
                if (pendingTasks.isEmpty()) {
                    Empty("Nothing pending.")
                } else {
                    pendingTasks.take(15).forEachIndexed { i, t ->
                        val parent = openJobs.firstOrNull { it.id == t.jobId }
                        TaskRow(t, parent?.title.orEmpty(), onClick = { parent?.let { onJobClick(it.id) } })
                        if (i < (pendingTasks.size - 1).coerceAtMost(14)) Divider()
                    }
                    if (pendingTasks.size > 15) {
                        Text(
                            "+${pendingTasks.size - 15} more",
                            style = SmithType.caption.copy(color = colors.inkMuted),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Section("RECENT JOBS  (${recentClosed.size})") {
                if (recentClosed.isEmpty()) {
                    Empty("No completed jobs yet.")
                } else {
                    recentClosed.forEachIndexed { i, j ->
                        RecentJobRow(j, jobBilled(j), onClick = { onJobClick(j.id) })
                        if (i < recentClosed.lastIndex) Divider()
                    }
                }
            }

            Section("BILLING") {
                BillingBlock(openJobs, recentClosed, balanceDue, ::jobBilled, ::jobEstimate)
            }

            Section("TIMELINE") {
                TimelineFeed(clientJobs)
            }
        }
    }
}

@Composable
private fun HeaderStrip(
    isEditing: Boolean,
    editName: String, onNameChange: (String) -> Unit,
    editPhone: String, onPhoneChange: (String) -> Unit,
    editEmail: String, onEmailChange: (String) -> Unit,
    editAddress: String, onAddressChange: (String) -> Unit,
    onCancel: () -> Unit,
    phone: String, email: String, address: String,
    openCount: Int, lastServiceMs: Long?, createdMs: Long?, balanceDue: Double,
    onCallPhone: () -> Unit, onMapAddress: () -> Unit, onEmailClick: () -> Unit
) {
    val colors = LocalSmithColors.current
    Card {
        if (isEditing) {
            EditField("NAME", editName, onNameChange)
            EditField("PHONE", editPhone, onPhoneChange)
            EditField("EMAIL", editEmail, onEmailChange)
            EditField("ADDRESS", editAddress, onAddressChange)
            Text(
                "[Cancel]",
                style = SmithType.action.copy(color = colors.inkMuted),
                modifier = Modifier.clickable { onCancel() }.padding(top = 4.dp)
            )
        } else {
            val statusLabel = if (openCount > 0) "ACTIVE" else "INACTIVE"
            val statusColor = if (openCount > 0) colors.statusOnline else colors.inkMuted
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("[$statusLabel]", style = SmithType.captionBold.copy(color = statusColor))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("$openCount open", if (openCount > 0) colors.accent else colors.inkMuted)
                    if (balanceDue > 0.01) Chip("$${"%.0f".format(balanceDue)} due", colors.attention)
                }
            }
            Spacer(Modifier.height(6.dp))
            if (phone.isNotBlank()) ContactLine("☎", phone, onCallPhone)
            if (email.isNotBlank()) ContactLine("✉", email, onEmailClick)
            if (address.isNotBlank()) ContactLine("⌖", address, onMapAddress)
            if (phone.isBlank() && email.isBlank() && address.isBlank()) {
                Text(
                    "No contact info. Tap [Edit] to add.",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }
            Spacer(Modifier.height(4.dp))
            val meta = buildList {
                lastServiceMs?.let { add("Last service: ${SHORT.format(Date(it))}") }
                createdMs?.let { add("Since ${SHORT_YEAR.format(Date(it))}") }
            }.joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(meta, style = SmithType.caption.copy(color = colors.inkMuted))
            }
        }
    }
}

@Composable
private fun ContactLine(icon: String, value: String, onClick: () -> Unit) {
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$icon  ", style = SmithType.bodySmall.copy(color = colors.inkMuted))
        Text(value, style = SmithType.bodySmall.copy(color = colors.accent), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CurrentJobRow(j: Job, estimate: Double, onClick: () -> Unit) {
    val colors = LocalSmithColors.current
    val due = j.dueDate ?: j.estimatedEndDate
    val nextAction = j.workLog.maxByOrNull { it.timestamp }?.text
        ?: j.stage.displayName
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens2.RadiusControl))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(j.stage.icon, style = SmithType.caption.copy(color = colors.accent))
                Spacer(Modifier.width(6.dp))
                Text(
                    j.title,
                    style = SmithType.bodySmall.copy(color = colors.ink),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                j.stage.displayName + "  ·  " + priorityLabel(j.priority) +
                    (due?.let { "  ·  due ${SHORT.format(Date(it))}" } ?: ""),
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
            val assignee = j.assignedTo.firstOrNull()
            val meta = listOfNotNull(
                assignee?.takeIf { it.isNotBlank() }?.let { "→ $it" },
                "Next: $nextAction".take(60)
            ).joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(meta, style = SmithType.caption.copy(color = colors.inkMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (estimate > 0.0) Text(
                "$${"%.0f".format(estimate)}",
                style = SmithType.bodySmall.copy(color = colors.ink)
            )
            Text(">", style = SmithType.caption.copy(color = colors.inkMuted))
        }
    }
}

@Composable
private fun TaskRow(t: Task, jobTitle: String, onClick: () -> Unit) {
    val colors = LocalSmithColors.current
    val statusColor = when (t.status) {
        TaskStatus.BLOCKED -> colors.statusError
        TaskStatus.IN_PROGRESS -> colors.accent
        else -> colors.inkMuted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens2.RadiusControl))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("[${t.status.displayName}]", style = SmithType.caption.copy(color = statusColor))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(t.title, style = SmithType.bodySmall.copy(color = colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(
                jobTitle.takeIf { it.isNotBlank() }?.let { "in $it" },
                t.assignedTo?.takeIf { it.isNotBlank() }?.let { "→ $it" }
            ).joinToString("  ·  ")
            if (sub.isNotEmpty()) Text(sub, style = SmithType.caption.copy(color = colors.inkMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecentJobRow(j: Job, billed: Double, onClick: () -> Unit) {
    val colors = LocalSmithColors.current
    val date = j.completedAt ?: j.actualEndDate ?: j.updatedAt
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens2.RadiusControl))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(j.title, style = SmithType.bodySmall.copy(color = colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val tech = j.crew.firstOrNull()?.name ?: j.assignedTo.firstOrNull().orEmpty()
            val sub = listOfNotNull(
                SHORT.format(Date(date)),
                tech.takeIf { it.isNotBlank() }?.let { "by $it" }
            ).joinToString("  ·  ")
            Text(sub, style = SmithType.caption.copy(color = colors.inkMuted))
        }
        if (billed > 0.0) Text(
            "$${"%.0f".format(billed)}",
            style = SmithType.bodySmall.copy(color = colors.ink)
        )
    }
}

@Composable
private fun BillingBlock(
    openJobs: List<Job>,
    recentClosed: List<Job>,
    balanceDue: Double,
    billed: (Job) -> Double,
    estimate: (Job) -> Double
) {
    val colors = LocalSmithColors.current
    val proposalsOut = openJobs.count { it.proposalId != null && it.stage == JobStage.PROPOSAL }
    val invoiced = openJobs.filter { it.invoiceId != null || it.stage == JobStage.INVOICE }
    val invoicedTotal = invoiced.sumOf(billed)
    val deposits = openJobs.sumOf { it.depositCollected }
    val lifetime = recentClosed.sumOf(billed)
    val outstandingEstimates = openJobs.filter { it.stage == JobStage.LEAD || it.stage == JobStage.PROPOSAL }
        .sumOf(estimate)

    Stat("Proposals out", "$proposalsOut")
    if (outstandingEstimates > 0.0) Stat("Estimates outstanding", "$${"%.0f".format(outstandingEstimates)}")
    Stat("Invoices issued", "${invoiced.size}")
    if (invoicedTotal > 0.0) Stat("Invoiced amount", "$${"%.0f".format(invoicedTotal)}")
    if (deposits > 0.0) Stat("Deposits collected", "$${"%.0f".format(deposits)}")
    Stat(
        "Balance due",
        if (balanceDue > 0.01) "$${"%.0f".format(balanceDue)}" else "—",
        valueColor = if (balanceDue > 0.01) colors.attention else colors.inkMuted
    )
    Stat("Lifetime billed", "$${"%.0f".format(lifetime)}")
}

private data class Event(val ts: Long, val label: String, val detail: String, val jobTitle: String)

@Composable
private fun TimelineFeed(jobs: List<Job>) {
    val colors = LocalSmithColors.current
    val events = remember(jobs) {
        val list = mutableListOf<Event>()
        jobs.forEach { j ->
            j.workLog.forEach { w: WorkLogEntry ->
                list.add(Event(w.timestamp, "note", w.text, j.title))
            }
            list.add(Event(j.createdAt, "created", j.stage.displayName, j.title))
            j.completedAt?.let { list.add(Event(it, "closed", "Job completed", j.title)) }
            if (j.photos.isNotEmpty()) list.add(Event(j.updatedAt, "photo", "${j.photos.size} photo(s)", j.title))
            if (j.invoiceId != null) list.add(Event(j.updatedAt, "invoice", "Invoice issued", j.title))
        }
        list.sortedByDescending { it.ts }.take(20)
    }
    if (events.isEmpty()) {
        Empty("No activity yet.")
        return
    }
    events.forEachIndexed { i, e ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                "[${e.label}]",
                style = SmithType.caption.copy(color = colors.accent),
                modifier = Modifier.width(72.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(e.detail, style = SmithType.bodySmall.copy(color = colors.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${SHORT.format(Date(e.ts))}  ·  ${e.jobTitle}",
                    style = SmithType.caption.copy(color = colors.inkMuted),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (i < events.lastIndex) Divider()
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val colors = LocalSmithColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
            .border(0.5.dp, colors.ink.copy(alpha = 0.06f), RoundedCornerShape(Tokens2.RadiusCard))
            .padding(12.dp)
    ) {
        Text(title, style = SmithType.captionBold.copy(color = colors.inkMuted))
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalSmithColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
            .border(0.5.dp, colors.ink.copy(alpha = 0.06f), RoundedCornerShape(Tokens2.RadiusCard))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

@Composable
private fun Stat(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = LocalSmithColors.current.ink
) {
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = SmithType.caption.copy(color = colors.inkMuted))
        Text(value, style = SmithType.bodySmall.copy(color = valueColor))
    }
}

@Composable
private fun Chip(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = SmithType.captionBold.copy(color = color))
}

@Composable
private fun Empty(text: String) {
    val colors = LocalSmithColors.current
    Text(
        text,
        style = SmithType.caption.copy(color = colors.inkMuted),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun Divider() {
    val colors = LocalSmithColors.current
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.ink.copy(alpha = 0.06f)))
}

private fun priorityLabel(p: Priority): String = when (p) {
    Priority.URGENT -> "[!] urgent"
    Priority.HIGH -> "high"
    Priority.MEDIUM -> "medium"
    Priority.LOW -> "low"
}

@Composable
private fun EditField(label: String, value: String, onValueChange: (String) -> Unit) {
    val colors = LocalSmithColors.current
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, style = SmithType.caption.copy(color = colors.inkMuted))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = SmithType.bodySmall.copy(color = colors.ink),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusControl))
                .padding(8.dp),
            singleLine = true
        )
    }
}
