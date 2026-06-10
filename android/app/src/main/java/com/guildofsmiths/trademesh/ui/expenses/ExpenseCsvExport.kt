package com.guildofsmiths.trademesh.ui.expenses

import android.content.Context
import com.guildofsmiths.trademesh.ui.jobboard.JobExpense
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports job expenses to CSV using the exact column set ExpenseCsvImport reads
 * (date, job, category, description, qty, unit, unit_cost, vendor, ref_no,
 * hazardous, freight_term, notes), so an exported file re-imports cleanly.
 */
object ExpenseCsvExport {

    private val HEADERS = listOf(
        "date", "job", "category", "description", "qty", "unit",
        "unit_cost", "vendor", "ref_no", "hazardous", "freight_term", "notes"
    )

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** One row per expense; [jobTitle] is repeated in the `job` column. */
    fun toCsv(rows: List<Pair<String, JobExpense>>): String {
        val sb = StringBuilder()
        sb.append(HEADERS.joinToString(",")).append("\n")
        for ((jobTitle, e) in rows) {
            sb.append(
                listOf(
                    dateFmt.format(Date(e.incurredAt)),
                    jobTitle,
                    e.category,
                    e.description,
                    trimNum(e.quantity),
                    e.unit,
                    trimNum(e.unitCost),
                    e.vendor,
                    e.referenceNumber ?: "",
                    if (e.hazardous) "true" else "false",
                    e.freightTerm.name,
                    e.notes ?: ""
                ).joinToString(",") { cell(it) }
            ).append("\n")
        }
        return sb.toString()
    }

    /** Write [csv] to a cache file and fire a share chooser (type text/csv). */
    fun share(context: Context, fileName: String, csv: String) {
        com.guildofsmiths.trademesh.data.FileShare.share(
            context, fileName, csv.toByteArray(Charsets.UTF_8), "text/csv", "Export CSV"
        )
    }

    /** Quote a CSV cell when it contains a comma, quote, or newline. */
    private fun cell(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n')) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else v

    /** Render a Double without a trailing ".0" for whole numbers. */
    private fun trimNum(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}
