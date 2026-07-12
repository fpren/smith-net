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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.ExpenseCategoryRepository
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import java.text.SimpleDateFormat
import java.util.*

enum class ReportPeriod(val label: String) {
    WEEK("Week"), MONTH("Month"), YEAR("Year")
}

@Composable
fun ReportScreen(
    allJobs: List<Job>,
    onJobClick: (String) -> Unit,
    onOpenJobExpenses: (String) -> Unit,
    onOpenExpenses: () -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalSmithColors.current
    val context = LocalContext.current
    val periodPrefs = remember { context.getSharedPreferences("report_prefs", 0) }
    var selectedPeriod by remember {
        mutableStateOf(
            ReportPeriod.entries.firstOrNull { it.name == periodPrefs.getString("period", null) }
                ?: ReportPeriod.MONTH
        )
    }
    val timeEntries by TimeEntryRepository.entries.collectAsState()
    val now = remember { System.currentTimeMillis() }

    val periodStart = remember(selectedPeriod) { getPeriodStart(selectedPeriod) }

    val periodJobs = remember(allJobs, periodStart) {
        allJobs.filter { it.updatedAt >= periodStart || it.createdAt >= periodStart }
    }
    val closedJobs = periodJobs.filter { it.stage == JobStage.CLOSED }

    fun laborCostFor(job: Job): Double {
        val mins = TimeEntryRepository.getMinutesForJob(job.id, job.title, periodStart, now)
        return (mins / 60.0) * job.hourlyRate
    }

    fun materialsCostFor(job: Job): Double = job.materials.sumOf { it.totalCost }
    fun otherExpensesFor(job: Job): Double = job.expenses
        .filter { it.category != "labor" && it.category != "material" }
        .sumOf { it.totalCost }
    fun expenseTotalFor(job: Job): Double = laborCostFor(job) + materialsCostFor(job) +
        job.expenses.filter { it.category != "labor" }.sumOf { it.totalCost }

    val earned = closedJobs.sumOf { expenseTotalFor(it) }
    val outstanding = periodJobs
        .filter { it.stage == JobStage.INVOICE }
        .sumOf { expenseTotalFor(it) }

    val periodEntries = remember(timeEntries, periodStart) {
        timeEntries.filter { it.clockInTime >= periodStart }
    }
    val totalMinutes = periodEntries.sumOf { it.durationMinutes ?: 0 }
    val jobsWithTime = periodEntries.mapNotNull { it.jobId }.distinct().size

    val dailyGroups = remember(periodEntries) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        periodEntries
            .sortedByDescending { it.clockInTime }
            .groupBy { dateFormat.format(Date(it.clockInTime)) }
            .toSortedMap(compareByDescending { it })
    }

    val topJobs = remember(periodJobs, timeEntries) {
        periodJobs
            .filter { it.title.isNotBlank() }
            .groupBy { it.title }
            .entries
            .sortedByDescending { it.value.size }
            .take(3)
            .map { (title, jobs) ->
                val rev = jobs.filter { it.stage == JobStage.CLOSED }.sumOf { expenseTotalFor(it) }
                Triple(title, jobs.size, rev)
            }
    }

    // EXPENSE ROLL-UPS (for the EXPENSES card)
    val categories by ExpenseCategoryRepository.categories.collectAsState()
    data class CategoryRollup(val id: String, val display: String, val short: String, val amount: Double, val count: Int)

    val categoryRollups = remember(periodJobs, timeEntries, categories) {
        val rollups = mutableListOf<CategoryRollup>()
        // Labor (derived from time entries)
        val laborTotal = periodJobs.sumOf { laborCostFor(it) }
        val laborMins = periodJobs.sumOf { TimeEntryRepository.getMinutesForJob(it.id, it.title, periodStart, now) }
        if (laborTotal > 0 || laborMins > 0) {
            val laborDef = ExpenseCategoryRepository.resolve("labor")
            rollups += CategoryRollup("labor", laborDef.displayName, laborDef.shortCode, laborTotal, laborMins)
        }
        // Materials (existing materials field)
        val matTotal = periodJobs.sumOf { it.materials.sumOf { m -> m.totalCost } }
        val matCount = periodJobs.sumOf { it.materials.size }
        if (matTotal > 0 || matCount > 0) {
            val mDef = ExpenseCategoryRepository.resolve("material")
            rollups += CategoryRollup("material", mDef.displayName, mDef.shortCode, matTotal, matCount)
        }
        // All other categories (from JobExpense list)
        val otherByCat = periodJobs.flatMap { it.expenses }
            .filter { it.category != "labor" && it.category != "material" }
            .groupBy { it.category }
        otherByCat.forEach { (catId, exps) ->
            val def = ExpenseCategoryRepository.resolve(catId)
            rollups += CategoryRollup(catId, def.displayName, def.shortCode, exps.sumOf { it.totalCost }, exps.size)
        }
        rollups.sortedBy { it.id }
    }
    val expenseGrandTotal = categoryRollups.sumOf { it.amount }
    val perJobExpenses = remember(periodJobs, timeEntries) {
        periodJobs
            .map { it to expenseTotalFor(it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
    ) {
        ConsoleHeader(title = "REPORT", onBackClick = onBack)

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
                            if (isSelected) colors.accent else colors.bgPanel,
                            RoundedCornerShape(Tokens2.RadiusControl)
                        )
                        .border(
                            0.5.dp,
                            if (isSelected) colors.accent else colors.ink.copy(alpha = 0.06f),
                            RoundedCornerShape(Tokens2.RadiusControl)
                        )
                        .clip(RoundedCornerShape(Tokens2.RadiusControl))
                        .clickable {
                            selectedPeriod = period
                            periodPrefs.edit().putString("period", period.name).apply()
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = period.label,
                        style = SmithType.captionBold.copy(
                            color = if (isSelected) colors.inkOnAccent else colors.inkMuted
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
                    style = SmithType.bodySmall.copy(color = colors.accent)
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
                            .background(colors.ink.copy(alpha = 0.06f), RoundedCornerShape(Tokens2.RadiusPill))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(colors.accent, RoundedCornerShape(Tokens2.RadiusPill))
                        )
                    }
                    Text(
                        "${closedJobs.size}/${periodJobs.size} closed",
                        style = SmithType.caption.copy(color = colors.inkMuted)
                    )
                }
            }

            // HOURS card
            ReportCard("HOURS") {
                val hours = totalMinutes / 60.0
                val avg = if (jobsWithTime > 0) hours / jobsWithTime else 0.0
                Text(
                    text = "${String.format("%.1f", hours)}h logged · $jobsWithTime jobs",
                    style = SmithType.bodySmall.copy(color = colors.ink)
                )
                Text(
                    text = "Avg: ${String.format("%.1f", avg)}h/job",
                    style = SmithType.caption.copy(color = colors.inkMuted)
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
                                style = SmithType.caption.copy(color = colors.ink),
                                modifier = Modifier.weight(1f)
                            )
                            if (rev > 0) {
                                Text(
                                    text = "$${String.format("%.0f", rev)}",
                                    style = SmithType.caption.copy(color = colors.accent)
                                )
                            }
                        }
                    }
                }
            }

            // EXPENSES card
            ReportCard("EXPENSES") {
                if (categoryRollups.isEmpty()) {
                    Text(
                        text = "No expenses logged this period.",
                        style = SmithType.caption.copy(color = colors.inkMuted),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    categoryRollups.forEach { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${r.short} ${r.display}",
                                style = SmithType.caption.copy(color = colors.ink),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "$${String.format("%.2f", r.amount)}",
                                style = SmithType.caption.copy(color = colors.ink)
                            )
                            Spacer(Modifier.width(12.dp))
                            val suffix = when (r.id) {
                                "labor" -> "${String.format("%.1f", r.count / 60.0)}h"
                                "mileage" -> "${r.count} item${if (r.count != 1) "s" else ""}"
                                else -> "${r.count} item${if (r.count != 1) "s" else ""}"
                            }
                            Text(suffix, style = SmithType.caption.copy(color = colors.inkMuted))
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .height(0.5.dp)
                            .background(colors.ink.copy(alpha = 0.08f))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("TOTAL", style = SmithType.captionBold.copy(color = colors.ink))
                        Text(
                            "$${String.format("%.2f", expenseGrandTotal)}",
                            style = SmithType.captionBold.copy(color = colors.accent)
                        )
                    }

                    if (perJobExpenses.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Per-job breakdown",
                            style = SmithType.captionBold.copy(color = colors.inkMuted)
                        )
                        perJobExpenses.take(8).forEach { (job, total) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Tokens2.RadiusControl))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = rememberRipple(bounded = true),
                                        onClick = { onOpenJobExpenses(job.id) }
                                    )
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "• ${job.clientName ?: job.title}",
                                    style = SmithType.caption.copy(color = colors.ink),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "$${String.format("%.0f", total)}",
                                    style = SmithType.caption.copy(color = colors.accent)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(">", style = SmithType.caption.copy(color = colors.inkMuted))
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        "[Open Expenses page →]",
                        style = SmithType.action.copy(color = colors.accent),
                        modifier = Modifier
                            .clip(RoundedCornerShape(Tokens2.RadiusControl))
                            .clickable { onOpenExpenses() }
                            .padding(vertical = 4.dp)
                    )
                }
            }

            // TIMESHEET section
            ReportCard("TIMESHEET") {
                if (dailyGroups.isEmpty()) {
                    Text(
                        text = "No time entries for this period.",
                        style = SmithType.caption.copy(color = colors.inkMuted),
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

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(dayLabel, style = SmithType.captionBold.copy(color = colors.ink))
                            Text(
                                "${String.format("%.1f", dayHours)}h",
                                style = SmithType.captionBold.copy(color = colors.accent)
                            )
                        }

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
                                            .clip(RoundedCornerShape(Tokens2.RadiusControl))
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
                                        style = SmithType.caption.copy(color = colors.ink),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$clockIn–$clockOut",
                                        style = SmithType.caption.copy(color = colors.inkMuted)
                                    )
                                }
                                Text(
                                    text = durStr,
                                    style = SmithType.caption.copy(color = colors.inkMuted)
                                )
                            }
                        }

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(colors.ink.copy(alpha = 0.06f))
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
    val colors = LocalSmithColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
            .border(0.5.dp, colors.ink.copy(alpha = 0.06f), RoundedCornerShape(Tokens2.RadiusCard))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = SmithType.captionBold.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

internal fun getPeriodStart(period: ReportPeriod): Long {
    val cal = Calendar.getInstance()
    when (period) {
        ReportPeriod.WEEK -> cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        ReportPeriod.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
        ReportPeriod.YEAR -> cal.set(Calendar.DAY_OF_YEAR, 1)
    }
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
