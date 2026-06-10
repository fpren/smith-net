package com.guildofsmiths.trademesh.ui.expenses

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.ExpenseCategoryRepository
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel
import com.guildofsmiths.trademesh.ui.jobboard.JobExpense
import com.guildofsmiths.trademesh.ui.report.ReportPeriod
import com.guildofsmiths.trademesh.ui.report.getPeriodStart
import java.text.SimpleDateFormat
import java.util.*

private enum class ExpenseView(val label: String) {
    BY_JOB("By Job"),
    LEDGER("Ledger"),
    TIMELINE("Timeline"),
    BOL_TABLE("BOL Table")
}

@Composable
fun ExpensesScreen(
    viewModel: JobBoardViewModel,
    onBack: () -> Unit,
    onOpenJobExpenses: (String) -> Unit,
    onOpenCategoryManager: () -> Unit,
    onOpenCsvImport: () -> Unit,
    onOpenLegalSettings: () -> Unit = {}
) {
    val jobs by viewModel.jobs.collectAsState()
    val timeEntries by TimeEntryRepository.entries.collectAsState()
    val categories by ExpenseCategoryRepository.categories.collectAsState()
    val context = LocalContext.current

    val periodPrefs = remember { context.getSharedPreferences("report_prefs", 0) }
    val viewPrefs = remember { context.getSharedPreferences("expenses_prefs", 0) }

    var period by remember {
        mutableStateOf(
            ReportPeriod.entries.firstOrNull { it.name == periodPrefs.getString("period", null) }
                ?: ReportPeriod.MONTH
        )
    }
    var view by remember {
        mutableStateOf(
            ExpenseView.entries.firstOrNull { it.name == viewPrefs.getString("view", null) }
                ?: ExpenseView.BY_JOB
        )
    }

    val periodStart = remember(period) { getPeriodStart(period) }
    val now = remember { System.currentTimeMillis() }

    val periodJobs = remember(jobs, periodStart) {
        jobs.filter { it.updatedAt >= periodStart || it.createdAt >= periodStart }
    }

    fun laborCostFor(job: Job): Double {
        val mins = TimeEntryRepository.getMinutesForJob(job.id, job.title, periodStart, now)
        return (mins / 60.0) * job.hourlyRate
    }

    fun totalFor(job: Job): Double {
        val labor = laborCostFor(job)
        val mats = job.materials.sumOf { it.totalCost }
        val other = job.expenses.filter { it.category != "labor" }.sumOf { it.totalCost }
        return labor + mats + other
    }

    // Summary rollups
    data class Rollup(val id: String, val display: String, val short: String, val amount: Double, val count: Int)
    val rollups = remember(periodJobs, timeEntries, categories) {
        val out = mutableListOf<Rollup>()
        val laborTotal = periodJobs.sumOf { laborCostFor(it) }
        val laborMins = periodJobs.sumOf { TimeEntryRepository.getMinutesForJob(it.id, it.title, periodStart, now) }
        if (laborTotal > 0 || laborMins > 0) {
            val d = ExpenseCategoryRepository.resolve("labor")
            out += Rollup("labor", d.displayName, d.shortCode, laborTotal, laborMins)
        }
        val matTotal = periodJobs.sumOf { it.materials.sumOf { m -> m.totalCost } }
        val matCount = periodJobs.sumOf { it.materials.size }
        if (matTotal > 0 || matCount > 0) {
            val d = ExpenseCategoryRepository.resolve("material")
            out += Rollup("material", d.displayName, d.shortCode, matTotal, matCount)
        }
        periodJobs.flatMap { it.expenses }
            .filter { it.category != "labor" && it.category != "material" }
            .groupBy { it.category }
            .forEach { (cid, exps) ->
                val d = ExpenseCategoryRepository.resolve(cid)
                out += Rollup(cid, d.displayName, d.shortCode, exps.sumOf { it.totalCost }, exps.size)
            }
        out.sortedBy { it.id }
    }
    val grandTotal = rollups.sumOf { it.amount }

    Column(modifier = Modifier.fillMaxSize().background(ConsoleTheme.background)) {
        ConsoleHeader(title = "EXPENSES", onBackClick = onBack)

        // Period tabs
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReportPeriod.entries.forEach { p ->
                val sel = p == period
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (sel) ConsoleTheme.accent else ConsoleTheme.surface, RoundedCornerShape(4.dp))
                        .border(0.5.dp, if (sel) ConsoleTheme.accent else ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            period = p
                            periodPrefs.edit().putString("period", p.name).apply()
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(p.label, style = ConsoleTheme.captionBold.copy(color = if (sel) Color.White else ConsoleTheme.textMuted))
                }
            }
        }

        // View picker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExpenseView.entries.forEach { v ->
                val sel = v == view
                Box(
                    modifier = Modifier
                        .background(if (sel) ConsoleTheme.accent.copy(alpha = 0.20f) else ConsoleTheme.surface, RoundedCornerShape(4.dp))
                        .border(0.5.dp, if (sel) ConsoleTheme.accent else ConsoleTheme.text.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            view = v
                            viewPrefs.edit().putString("view", v.name).apply()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("[${v.label}]", style = ConsoleTheme.caption.copy(color = if (sel) ConsoleTheme.accent else ConsoleTheme.textMuted))
                }
            }
        }

        // Summary strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            if (rollups.isEmpty()) {
                Text("No expenses this period.", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
            } else {
                rollups.forEach { r ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${r.short} ${r.display}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.text), modifier = Modifier.weight(1f))
                        Text("$${String.format("%.2f", r.amount)}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.text))
                    }
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.08f)))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.text))
                    Text("$${String.format("%.2f", grandTotal)}", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent))
                }
            }
        }

        // View body
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (view) {
                ExpenseView.BY_JOB -> ByJobView(periodJobs, ::totalFor, ::laborCostFor, onOpenJobExpenses)
                ExpenseView.LEDGER -> LedgerView(periodJobs, ::laborCostFor)
                ExpenseView.TIMELINE -> TimelineView(periodJobs, onOpenJobExpenses)
                ExpenseView.BOL_TABLE -> BolTableView(periodJobs, onOpenJobExpenses)
            }
        }

        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ToolbarButton("[+ New]", accent = true) {
                // Minimal: pick a job first — for now, open the topmost active job's BOL.
                val target = periodJobs.firstOrNull() ?: jobs.firstOrNull()
                if (target != null) onOpenJobExpenses(target.id)
                else Toast.makeText(context, "Create a job first", Toast.LENGTH_SHORT).show()
            }
            ToolbarButton("[⬆ Import CSV]") { onOpenCsvImport() }
            ToolbarButton("[⬇ Export]") {
                val rows = periodJobs.flatMap { j -> j.expenses.map { j.title to it } }
                if (rows.isEmpty()) {
                    Toast.makeText(context, "No expenses to export", Toast.LENGTH_SHORT).show()
                } else {
                    ExpenseCsvExport.share(context, "expenses.csv", ExpenseCsvExport.toCsv(rows))
                }
            }
            ToolbarButton("[⚙ Categories]") { onOpenCategoryManager() }
            ToolbarButton("[§ Legal terms]") { onOpenLegalSettings() }
        }
    }
}

@Composable
private fun ToolbarButton(label: String, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (accent) ConsoleTheme.accent.copy(alpha = 0.14f) else ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true, color = ConsoleTheme.accent),
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = ConsoleTheme.action.copy(color = if (accent) ConsoleTheme.accent else ConsoleTheme.text))
    }
}

// ─── VIEW: BY JOB (cards) ───────────────────────────────────────────

@Composable
private fun ByJobView(
    jobs: List<Job>,
    totalFor: (Job) -> Double,
    laborCostFor: (Job) -> Double,
    onOpen: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val jobsWithExpenses = jobs.map { it to totalFor(it) }.filter { it.second > 0 }.sortedByDescending { it.second }
        if (jobsWithExpenses.isEmpty()) {
            EmptyHint("No jobs with expenses in this period.")
        } else {
            jobsWithExpenses.forEach { (job, total) ->
                val labor = laborCostFor(job)
                val mats = job.materials.sumOf { it.totalCost }
                val other = job.expenses.filter { it.category != "labor" }.sumOf { it.totalCost }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                        .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onOpen(job.id) }
                        .padding(12.dp)
                ) {
                    Text(job.clientName ?: job.title, style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.text))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Labor $${String.format("%.2f", labor)}  ·  Materials $${String.format("%.2f", mats)}  ·  Other $${String.format("%.2f", other)}",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL $${String.format("%.2f", total)}", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent))
                        Text("[View BOL →]", style = ConsoleTheme.action.copy(color = ConsoleTheme.accent))
                    }
                }
            }
        }
    }
}

// ─── VIEW: LEDGER (by category) ─────────────────────────────────────

@Composable
private fun LedgerView(jobs: List<Job>, laborCostFor: (Job) -> Double) {
    data class Entry(val catId: String, val display: String, val short: String, val desc: String, val amount: Double, val subtitle: String)
    val allEntries = remember(jobs) {
        val list = mutableListOf<Entry>()
        jobs.forEach { j ->
            val labor = laborCostFor(j)
            if (labor > 0) {
                val d = ExpenseCategoryRepository.resolve("labor")
                list += Entry("labor", d.displayName, d.shortCode, "Labor on ${j.clientName ?: j.title}", labor, j.title)
            }
            j.materials.forEach { m ->
                val d = ExpenseCategoryRepository.resolve("material")
                list += Entry("material", d.displayName, d.shortCode, m.name, m.totalCost, "${j.clientName ?: j.title} · ${m.vendor}")
            }
            j.expenses.filter { it.category != "labor" && it.category != "material" }.forEach { e ->
                val d = ExpenseCategoryRepository.resolve(e.category)
                list += Entry(e.category, d.displayName, d.shortCode, e.description, e.totalCost, "${j.clientName ?: j.title} · ${e.vendor}")
            }
        }
        list
    }
    val byCat = allEntries.groupBy { it.catId }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (allEntries.isEmpty()) {
            EmptyHint("No expenses this period.")
        } else {
            byCat.entries.sortedBy { it.key }.forEach { (cid, entries) ->
                val d = ExpenseCategoryRepository.resolve(cid)
                val total = entries.sumOf { it.amount }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                        .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${d.shortCode} ${d.displayName}", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.text))
                        Text("$${String.format("%.2f", total)}", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent))
                    }
                    Spacer(Modifier.height(4.dp))
                    entries.forEach { e ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(e.desc, style = ConsoleTheme.caption.copy(color = ConsoleTheme.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(e.subtitle, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("$${String.format("%.2f", e.amount)}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.text))
                        }
                    }
                }
            }
        }
    }
}

// ─── VIEW: TIMELINE (by date) ───────────────────────────────────────

@Composable
private fun TimelineView(jobs: List<Job>, onOpen: (String) -> Unit) {
    data class Row(val at: Long, val jobId: String, val label: String, val amount: Double, val short: String, val job: String)
    val rows = remember(jobs) {
        val out = mutableListOf<Row>()
        jobs.forEach { j ->
            j.materials.forEach { m ->
                out += Row(j.updatedAt, j.id, m.name, m.totalCost, "[M]", j.clientName ?: j.title)
            }
            j.expenses.filter { it.category != "labor" }.forEach { e ->
                val d = ExpenseCategoryRepository.resolve(e.category)
                out += Row(e.incurredAt, j.id, e.description.ifBlank { d.displayName }, e.totalCost, d.shortCode, j.clientName ?: j.title)
            }
        }
        out.sortedByDescending { it.at }
    }
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.US) }

    val grouped = rows.groupBy { dateFmt.format(Date(it.at)) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (rows.isEmpty()) {
            EmptyHint("No dated expenses yet.")
        } else {
            grouped.forEach { (dateLabel, items) ->
                Text(dateLabel, style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
                items.forEach { r ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onOpen(r.jobId) }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(r.short, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted), modifier = Modifier.width(36.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(r.label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.job, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("$${String.format("%.2f", r.amount)}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent))
                    }
                }
            }
        }
    }
}

// ─── VIEW: BOL TABLE (full grid, horizontally scrollable) ───────────

@Composable
private fun BolTableView(jobs: List<Job>, onOpen: (String) -> Unit) {
    data class Row(val date: Long, val jobId: String, val job: String, val catShort: String, val desc: String, val qty: Double, val rate: Double, val total: Double)
    val rows = remember(jobs) {
        val out = mutableListOf<Row>()
        jobs.forEach { j ->
            j.materials.forEach { m ->
                out += Row(j.updatedAt, j.id, j.clientName ?: j.title, "[M]", m.name, m.quantity, m.unitCost, m.totalCost)
            }
            j.expenses.forEach { e ->
                if (e.category == "labor") return@forEach
                val d = ExpenseCategoryRepository.resolve(e.category)
                out += Row(e.incurredAt, j.id, j.clientName ?: j.title, d.shortCode, e.description, e.quantity, e.unitCost, e.totalCost)
            }
        }
        out.sortedByDescending { it.date }
    }
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.US) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val hScroll = rememberScrollState()
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(hScroll)) {
            Column {
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    TH("DATE", 52.dp)
                    TH("JOB", 140.dp)
                    TH("CAT", 36.dp)
                    TH("DESC", 180.dp)
                    TH("QTY", 48.dp)
                    TH("RATE", 60.dp)
                    TH("TOTAL", 70.dp)
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.1f)))
                val vScroll = rememberScrollState()
                Column(modifier = Modifier.verticalScroll(vScroll)) {
                    if (rows.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("No line items.", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                        }
                    }
                    rows.forEach { r ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .clickable { onOpen(r.jobId) }
                                .padding(vertical = 3.dp)
                        ) {
                            TD(dateFmt.format(Date(r.date)), 52.dp)
                            TD(r.job, 140.dp)
                            TD(r.catShort, 36.dp)
                            TD(r.desc, 180.dp)
                            TD(String.format("%.2f", r.qty), 48.dp)
                            TD(String.format("%.2f", r.rate), 60.dp)
                            TD("$${String.format("%.2f", r.total)}", 70.dp, accent = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TH(label: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        label,
        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted),
        modifier = Modifier.width(width).padding(horizontal = 4.dp)
    )
}

@Composable
private fun TD(label: String, width: androidx.compose.ui.unit.Dp, accent: Boolean = false) {
    Text(
        label,
        style = ConsoleTheme.caption.copy(color = if (accent) ConsoleTheme.accent else ConsoleTheme.text),
        modifier = Modifier.width(width).padding(horizontal = 4.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
    }
}
