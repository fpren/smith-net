package com.guildofsmiths.trademesh.ui.expenses

import com.guildofsmiths.trademesh.data.BolLegalPreferences
import com.guildofsmiths.trademesh.data.ExpenseCategoryRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.timetracking.EntryType
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Render the per-job Bill of Work & Expenses as plain text suitable for
 * sharing alongside (or separate from) an invoice.
 */
object BolFormatter {

    fun formatAsText(job: Job, timeEntries: List<TimeEntry>): String {
        val bolNo = "GS-${job.id.take(6).uppercase()}"
        val today = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date())
        val dayFmt = SimpleDateFormat("MMM d", Locale.US)
        val entries = timeEntries.filter { it.clockOutTime != null && (it.jobId == job.id || it.jobTitle == job.title) }

        return buildString {
            appendLine("════════════════════════════════════════")
            appendLine("BILL OF WORK & EXPENSES")
            appendLine("BOL #: $bolNo")
            appendLine("Date:  $today")
            appendLine("════════════════════════════════════════")
            appendLine()
            appendLine("SHIP FROM: ${UserPreferences.getBusinessName().ifBlank { "My Business" }}")
            appendLine("SHIP TO:   ${job.clientName ?: "—"}")
            if (job.clientAddress.isNotBlank()) appendLine("           ${job.clientAddress}")
            if (job.clientPhone.isNotBlank()) appendLine("           ${job.clientPhone}")
            appendLine()
            appendLine("Job:   ${job.title}")
            appendLine("Stage: ${job.stage.displayName}")
            if (job.depositCollected > 0) {
                appendLine("Deposit: $%.2f".format(job.depositCollected) +
                    (job.depositNote?.let { " ($it)" } ?: ""))
            }
            appendLine("────────────────────────────────────────")
            appendLine("UNIT  DESCRIPTION                   QTY    RATE     TOTAL")

            var laborSub = 0.0
            entries.sortedBy { it.clockInTime }.forEach { e ->
                val hrs = (e.durationMinutes ?: 0) / 60.0
                val rate = if (e.entryType == EntryType.OVERTIME) job.hourlyRate * 1.5 else job.hourlyRate
                val total = hrs * rate
                laborSub += total
                appendLine(
                    "[L]   Labor %-18s %6.2f %7.2f %9s".format(
                        "${dayFmt.format(Date(e.clockInTime))} ${e.entryType.displayName}".take(18),
                        hrs, rate, "$%.2f".format(total)
                    )
                )
            }

            var matSub = 0.0
            job.materials.forEach { m ->
                matSub += m.totalCost
                appendLine(
                    "[M]   %-24s %6.2f %7.2f %9s".format(
                        m.name.take(24), m.quantity, m.unitCost, "$%.2f".format(m.totalCost)
                    )
                )
            }

            var otherSub = 0.0
            job.expenses.filter { it.category != "labor" && it.category != "material" }
                .sortedBy { it.incurredAt }
                .forEach { exp ->
                    val def = ExpenseCategoryRepository.resolve(exp.category)
                    otherSub += exp.totalCost
                    val tag = exp.referenceNumber?.let { " · #$it" } ?: ""
                    val aiTag = if (exp.aiEstimated) " [AI]" else ""
                    appendLine(
                        "%-5s %-24s %6.2f %7.2f %9s".format(
                            def.shortCode,
                            (exp.description + tag + aiTag).take(24),
                            exp.quantity,
                            exp.unitCost,
                            "$%.2f".format(exp.totalCost)
                        )
                    )
                }

            val matFromExpenses = job.expenses.filter { it.category == "material" }.sumOf { it.totalCost }
            val grand = laborSub + matSub + matFromExpenses + otherSub

            appendLine("────────────────────────────────────────")
            appendLine("Subtotal Labor:     $%.2f".format(laborSub))
            appendLine("Subtotal Materials: $%.2f".format(matSub + matFromExpenses))
            appendLine("Subtotal Other:     $%.2f".format(otherSub))
            appendLine("────────────────────────────────────────")
            appendLine("GRAND TOTAL:        $%.2f".format(grand))
            if (job.depositCollected > 0) {
                appendLine("Deposit applied:   -$%.2f".format(job.depositCollected))
                appendLine("Net unbilled:       $%.2f".format(grand - job.depositCollected))
            }

            val estCount = job.expenses.count { it.aiEstimated }
            if (estCount > 0) {
                appendLine()
                appendLine("* $estCount item(s) priced by AI estimate — flagged [AI].")
            }
            appendLine("════════════════════════════════════════")

            // Legal / disclaimer footer
            val legal = BolLegalPreferences.state.value
            val hasAnyLegal = legal.enabled.isNotEmpty() ||
                legal.customDisclaimer.isNotBlank() ||
                legal.includeSignatureBlock
            if (hasAnyLegal) {
                appendLine()
                appendLine("TERMS & CONDITIONS")
                appendLine("────────────────────────────────────────")
                // Order by group (US_DOMESTIC → NEW_YORK → INTERNATIONAL → COMMERCIAL_LIEN),
                // then by enum ordinal within each group.
                val ordered = legal.enabled.sortedWith(
                    compareBy({ it.group.ordinal }, { it.ordinal })
                )
                var lastGroup: com.guildofsmiths.trademesh.data.BolLegalPreferences.Group? = null
                ordered.forEach { preset ->
                    val info = BolLegalTerms.infoFor(preset) ?: return@forEach
                    if (info.group != lastGroup) {
                        lastGroup = info.group
                        appendLine("— ${groupHeading(info.group)} —")
                    }
                    appendLine("[${info.shortLabel}]")
                    appendLine(info.body)
                    appendLine()
                }
                if (legal.customDisclaimer.isNotBlank()) {
                    appendLine("[Additional terms]")
                    appendLine(legal.customDisclaimer)
                    appendLine()
                }
                // Safe-harbor footer — appended with an extra caveat when any Group D clause is on.
                appendLine(safeHarborText(legal))
                if (legal.includeSignatureBlock) {
                    appendLine()
                    appendLine("SIGNATURES")
                    appendLine("────────────────────────────────────────")
                    signatureBlock(legal.shipperLabel).forEach(::appendLine)
                    appendLine()
                    signatureBlock(legal.carrierLabel).forEach(::appendLine)
                    appendLine()
                    signatureBlock(legal.consigneeLabel).forEach(::appendLine)
                    if (legal.includeNotarization) {
                        appendLine()
                        notarizationBlock().forEach(::appendLine)
                    }
                }
                appendLine("════════════════════════════════════════")
            }
        }
    }

    private fun groupHeading(group: com.guildofsmiths.trademesh.data.BolLegalPreferences.Group): String =
        when (group) {
            com.guildofsmiths.trademesh.data.BolLegalPreferences.Group.US_DOMESTIC -> "US Domestic"
            com.guildofsmiths.trademesh.data.BolLegalPreferences.Group.US_STATES -> "US States"
            com.guildofsmiths.trademesh.data.BolLegalPreferences.Group.INTL_COMMERCIAL -> "International commercial"
            com.guildofsmiths.trademesh.data.BolLegalPreferences.Group.INTERNATIONAL_CARRIAGE -> "International carriage"
            com.guildofsmiths.trademesh.data.BolLegalPreferences.Group.COMMERCIAL_LIEN -> "Commercial-lien / affidavit tradition"
        }

    private fun safeHarborText(s: BolLegalPreferences.State): String {
        val base = "Disclaimer: The language above cites the statutes, regulations, and procedural " +
            "rules noted by section. It is a plain-language summary intended as a starting template " +
            "for records kept by a solo contractor or small business; it is not legal advice, does " +
            "not create an attorney-client relationship, and does not purport to waive any right or " +
            "defense available under any other statute or rule of court. For matters involving " +
            "disputed sums, regulated work, litigation, or out-of-state parties, review by counsel " +
            "is recommended before reliance."
        val hasCommercialLien = s.enabled.any {
            it.group == com.guildofsmiths.trademesh.data.BolLegalPreferences.Group.COMMERCIAL_LIEN
        }
        return if (hasCommercialLien) {
            "$base Where any commercial-lien / affidavit clause is enabled, the issuer " +
                "acknowledges that such language is contested in many mainstream courts and is " +
                "used here at the issuer's own election and risk."
        } else base
    }

    private fun signatureBlock(label: String): List<String> = listOf(
        "$label:",
        "Signature:    ____________________________________",
        "Printed name: ____________________________________",
        "Date:         __________________________",
    )

    private fun notarizationBlock(): List<String> = listOf(
        "────────────────────────────────────────",
        "Sworn to (or affirmed) before me this ____ day of _______________, 20____,",
        "by ____________________________, personally known to me or identified by",
        "______________________________.",
        "",
        "Notary Public, State of _______",
        "Commission expires: ____________"
    )
}
