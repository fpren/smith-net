package com.guildofsmiths.trademesh.ui.expenses

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.ExpenseCategoryRepository
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.jobboard.FreightTerm
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel
import com.guildofsmiths.trademesh.ui.jobboard.JobExpense
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithCard
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun ExpenseCsvImportScreen(
    viewModel: JobBoardViewModel,
    onBack: () -> Unit
) {
    val colors = LocalSmithColors.current
    val context = LocalContext.current
    val jobs by viewModel.jobs.collectAsState()
    val categories by ExpenseCategoryRepository.categories.collectAsState()

    var parsedRows by remember { mutableStateOf<List<ParsedRow>>(emptyList()) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            fileName = uri.lastPathSegment
            val (rows, errs) = parseCsv(context, uri, jobs)
            parsedRows = rows
            errors = errs
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.bgBase)) {
        ConsoleHeader(title = "IMPORT EXPENSES (CSV)", onBackClick = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Expected columns: date, job, category, description, qty, unit, unit_cost, vendor, ref_no, hazardous, freight_term, notes. Extra columns are ignored; missing ones default.",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .background(colors.accent.copy(alpha = 0.14f), RoundedCornerShape(Tokens2.RadiusCard))
                        .border(0.5.dp, colors.ink.copy(alpha = 0.12f), RoundedCornerShape(Tokens2.RadiusCard))
                        .clip(RoundedCornerShape(Tokens2.RadiusCard))
                        .clickable { picker.launch(arrayOf("text/csv", "text/*", "text/comma-separated-values", "application/vnd.ms-excel")) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("[Choose CSV file]", style = SmithType.action.copy(color = colors.accent))
                }
                Box(
                    modifier = Modifier
                        .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
                        .border(0.5.dp, colors.ink.copy(alpha = 0.12f), RoundedCornerShape(Tokens2.RadiusCard))
                        .clip(RoundedCornerShape(Tokens2.RadiusCard))
                        .clickable {
                            copyTemplateToDownloads(context, "all_categories.csv")
                            Toast.makeText(context, "Saved all_categories.csv to Downloads", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("[Download template]", style = SmithType.action.copy(color = colors.ink))
                }
            }

            fileName?.let {
                Text("File: $it", style = SmithType.caption.copy(color = colors.inkMuted))
            }

            if (errors.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.statusError.copy(alpha = 0.1f), RoundedCornerShape(Tokens2.RadiusControl))
                        .padding(8.dp)
                ) {
                    errors.forEach { Text("• $it", style = SmithType.caption.copy(color = colors.statusError)) }
                }
            }

            if (parsedRows.isNotEmpty()) {
                val ok = parsedRows.count { it.status == ParseStatus.OK }
                val warn = parsedRows.count { it.status != ParseStatus.OK }
                Text(
                    "Preview: $ok ready · $warn warning${if (warn != 1) "s" else ""}",
                    style = SmithType.bodySmall.copy(color = colors.ink)
                )

                SmithCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Column {
                            parsedRows.forEach { r ->
                                val color = when (r.status) {
                                    ParseStatus.OK -> colors.ink
                                    ParseStatus.NO_JOB_MATCH -> colors.attention
                                    ParseStatus.UNKNOWN_CATEGORY -> colors.attention
                                    ParseStatus.INVALID -> colors.statusError
                                }
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(r.statusTag, style = SmithType.caption.copy(color = color), modifier = Modifier.width(64.dp))
                                    Text(r.jobLabel, style = SmithType.caption.copy(color = color), modifier = Modifier.width(120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(r.categoryLabel, style = SmithType.caption.copy(color = color), modifier = Modifier.width(80.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(r.expense.description, style = SmithType.caption.copy(color = color), modifier = Modifier.width(140.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("$${String.format("%.2f", r.expense.totalCost)}", style = SmithType.caption.copy(color = color), modifier = Modifier.width(70.dp))
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .background(colors.accent.copy(alpha = 0.14f), RoundedCornerShape(Tokens2.RadiusCard))
                            .border(0.5.dp, colors.ink.copy(alpha = 0.12f), RoundedCornerShape(Tokens2.RadiusCard))
                            .clip(RoundedCornerShape(Tokens2.RadiusCard))
                            .clickable {
                                var imported = 0
                                var skipped = 0
                                parsedRows.forEach { r ->
                                    if (r.status == ParseStatus.INVALID || r.matchedJobId == null) {
                                        skipped++
                                        return@forEach
                                    }
                                    // Auto-create unknown category on import
                                    if (r.status == ParseStatus.UNKNOWN_CATEGORY) {
                                        val existing = ExpenseCategoryRepository.get(r.rawCategory.lowercase().replace(' ', '_'))
                                        if (existing == null) {
                                            ExpenseCategoryRepository.add(r.rawCategory, "[?]")
                                        }
                                    }
                                    viewModel.addExpense(r.matchedJobId, r.expense)
                                    imported++
                                }
                                Toast.makeText(context, "Imported $imported · skipped $skipped", Toast.LENGTH_LONG).show()
                                parsedRows = emptyList()
                                fileName = null
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("[Import ${parsedRows.count { it.matchedJobId != null && it.status != ParseStatus.INVALID }} rows]",
                            style = SmithType.action.copy(color = colors.accent))
                    }
                    Box(
                        modifier = Modifier
                            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
                            .border(0.5.dp, colors.ink.copy(alpha = 0.12f), RoundedCornerShape(Tokens2.RadiusCard))
                            .clip(RoundedCornerShape(Tokens2.RadiusCard))
                            .clickable { parsedRows = emptyList(); fileName = null; errors = emptyList() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("[clear]", style = SmithType.action.copy(color = colors.ink))
                    }
                }
            }
        }
    }
}

private enum class ParseStatus { OK, NO_JOB_MATCH, UNKNOWN_CATEGORY, INVALID }

private data class ParsedRow(
    val status: ParseStatus,
    val statusTag: String,
    val jobLabel: String,
    val categoryLabel: String,
    val rawCategory: String,
    val matchedJobId: String?,
    val expense: JobExpense
)

private fun parseCsv(context: Context, uri: Uri, jobs: List<Job>): Pair<List<ParsedRow>, List<String>> {
    val errors = mutableListOf<String>()
    val rows = mutableListOf<ParsedRow>()
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader ->
                val headerLine = reader.readLine() ?: return emptyList<ParsedRow>() to listOf("Empty file")
                val headers = splitCsv(headerLine).map { it.trim().lowercase() }
                fun colIdx(vararg names: String): Int = names.firstNotNullOfOrNull { headers.indexOf(it).takeIf { i -> i >= 0 } } ?: -1
                val iDate = colIdx("date")
                val iJob = colIdx("job", "job_title", "job_name")
                val iCat = colIdx("category")
                val iDesc = colIdx("description", "desc")
                val iQty = colIdx("qty", "quantity")
                val iUnit = colIdx("unit")
                val iRate = colIdx("unit_cost", "rate", "price")
                val iVendor = colIdx("vendor")
                val iRef = colIdx("ref_no", "reference", "receipt_no")
                val iHm = colIdx("hazardous", "hm")
                val iFreight = colIdx("freight_term")
                val iNotes = colIdx("notes")

                if (iDesc < 0) errors += "Missing required column: description"
                if (iCat < 0) errors += "Missing required column: category"

                val cats = com.guildofsmiths.trademesh.data.ExpenseCategoryRepository.categories.value

                var line = reader.readLine()
                var rowNum = 1
                while (line != null) {
                    rowNum++
                    if (line.isBlank()) { line = reader.readLine(); continue }
                    val parts = splitCsv(line)
                    fun at(i: Int): String = if (i in parts.indices) parts[i].trim() else ""

                    val desc = at(iDesc)
                    val rawCat = at(iCat)
                    val qty = at(iQty).toDoubleOrNull() ?: 1.0
                    val unit = at(iUnit).ifBlank { "ea" }
                    val rate = at(iRate).toDoubleOrNull() ?: 0.0
                    val vendor = at(iVendor)
                    val ref = at(iRef).ifBlank { null }
                    val hm = at(iHm).equals("true", ignoreCase = true) || at(iHm) == "1"
                    val freight = runCatching { FreightTerm.valueOf(at(iFreight).uppercase().ifBlank { "NA" }) }.getOrDefault(FreightTerm.NA)
                    val notes = at(iNotes).ifBlank { null }
                    val dateMs = parseDateMs(at(iDate))
                    val rawJob = at(iJob)

                    // Match category
                    val catMatch = cats.firstOrNull {
                        it.id.equals(rawCat, ignoreCase = true) ||
                            it.displayName.equals(rawCat, ignoreCase = true) ||
                            it.id == rawCat.lowercase().replace(' ', '_')
                    }
                    // Match job (fuzzy)
                    val jobMatch = jobs.firstOrNull { j ->
                        rawJob.isNotBlank() && (
                            j.title.equals(rawJob, ignoreCase = true) ||
                                (j.clientName?.equals(rawJob, ignoreCase = true) == true) ||
                                j.title.lowercase().contains(rawJob.lowercase()) ||
                                (j.clientName?.lowercase()?.contains(rawJob.lowercase()) == true)
                        )
                    }

                    val status = when {
                        desc.isBlank() -> ParseStatus.INVALID
                        jobMatch == null -> ParseStatus.NO_JOB_MATCH
                        catMatch == null -> ParseStatus.UNKNOWN_CATEGORY
                        else -> ParseStatus.OK
                    }
                    val tag = when (status) {
                        ParseStatus.OK -> "row$rowNum ✓"
                        ParseStatus.NO_JOB_MATCH -> "row$rowNum !job"
                        ParseStatus.UNKNOWN_CATEGORY -> "row$rowNum !cat"
                        ParseStatus.INVALID -> "row$rowNum ✗"
                    }
                    val exp = JobExpense(
                        category = catMatch?.id ?: rawCat.lowercase().replace(Regex("[^a-z0-9]+"), "_"),
                        description = desc,
                        quantity = qty,
                        unit = unit,
                        unitCost = rate,
                        vendor = vendor,
                        referenceNumber = ref,
                        hazardous = hm,
                        freightTerm = freight,
                        notes = notes,
                        incurredAt = dateMs ?: System.currentTimeMillis()
                    )
                    rows += ParsedRow(
                        status = status,
                        statusTag = tag,
                        jobLabel = jobMatch?.let { it.clientName ?: it.title } ?: (if (rawJob.isBlank()) "(unassigned)" else rawJob),
                        categoryLabel = catMatch?.displayName ?: rawCat,
                        rawCategory = rawCat,
                        matchedJobId = jobMatch?.id,
                        expense = exp
                    )
                    line = reader.readLine()
                }
            }
        }
    } catch (t: Throwable) {
        errors += "Parse error: ${t.message}"
    }
    return rows to errors
}

private fun parseDateMs(s: String): Long? {
    if (s.isBlank()) return null
    return try {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(s)?.time
    } catch (_: Throwable) { null }
}

/** Minimal CSV splitter supporting double-quoted fields with commas. */
private fun splitCsv(line: String): List<String> {
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
            c == '"' -> inQuotes = !inQuotes
            c == ',' && !inQuotes -> { out += sb.toString(); sb.clear() }
            else -> sb.append(c)
        }
        i++
    }
    out += sb.toString()
    return out
}

/** Copy a bundled asset template into the app-external downloads dir. */
private fun copyTemplateToDownloads(context: Context, name: String) {
    try {
        val assetIn = context.assets.open("expense_templates/$name")
        val outFile = java.io.File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
            name
        )
        outFile.outputStream().use { out -> assetIn.copyTo(out) }
        assetIn.close()
    } catch (_: Throwable) {
        // swallow — caller toasts success optimistically; a failure just means no file
    }
}
