package com.guildofsmiths.trademesh.ui.proposal

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ProposalFormatter {

    private val dateFmt = SimpleDateFormat("MMMM d, yyyy", Locale.US)

    fun formatAsText(p: Proposal): String = buildString {
        appendLine("═══════════════════════════════════════════════")
        appendLine("                   PROPOSAL")
        appendLine("                ${p.proposalNumber}")
        appendLine("       Issued ${dateFmt.format(Date(p.issuedDate))}")
        appendLine("       Valid through ${dateFmt.format(Date(p.validUntil))}")
        appendLine("═══════════════════════════════════════════════")
        appendLine()

        appendLine("FROM")
        appendLine("  ${p.providerName}")
        if (p.providerBusiness.isNotBlank()) appendLine("  ${p.providerBusiness}")
        if (p.providerTrade.isNotBlank()) appendLine("  ${p.providerTrade}")
        p.providerPhone?.takeIf { it.isNotBlank() }?.let { appendLine("  Phone: $it") }
        p.providerEmail?.takeIf { it.isNotBlank() }?.let { appendLine("  Email: $it") }
        appendLine()

        appendLine("TO")
        appendLine("  ${p.clientName}")
        p.clientPhone?.let { appendLine("  $it") }
        p.clientAddress?.let { appendLine("  $it") }
        appendLine()

        appendLine("SCOPE OF WORK")
        appendLine("  ${p.jobTitle}")
        p.scopeStatement.chunkedForPrint(45).forEach { appendLine("  $it") }
        appendLine()

        appendLine("LABOR")
        appendLine("  ${p.laborLine.description}")
        appendLine("  ${"%.1f".format(p.laborLine.estimatedHours)} hrs × ${money(p.laborLine.hourlyRate)}")
        appendLine("                               ${moneyRight(p.laborLine.total)}")
        appendLine()

        if (p.materialLines.isNotEmpty()) {
            appendLine("MATERIALS")
            p.materialLines.forEach { m ->
                appendLine("  ${m.name}")
                appendLine("  ${fmtQty(m.quantity)} ${m.unit} × ${money(m.unitCost)}")
                appendLine("                               ${moneyRight(m.total)}")
            }
            appendLine("                Materials total  ${moneyRight(p.materialLines.sumOf { it.total })}")
            appendLine()
        }

        appendLine("─────────────────────────────────────────────")
        appendLine("Subtotal                         ${moneyRight(p.subtotal)}")
        appendLine("Tax (${p.taxRate}%)                    ${moneyRight(p.taxAmount)}")
        appendLine("TOTAL                            ${moneyRight(p.total)}")
        appendLine()
        appendLine("Deposit required (${p.depositPercent}%)         ${moneyRight(p.depositRequired)}")
        appendLine("Balance on completion            ${moneyRight(p.balanceOnCompletion)}")
        appendLine()

        appendLine("TIMELINE")
        appendLine("  Estimated ${p.timelineDays} working day${if (p.timelineDays == 1) "" else "s"}")
        appendLine("  Start: ${p.startEstimate}")
        appendLine()

        appendLine("WARRANTY")
        p.warrantyText.chunkedForPrint(45).forEach { appendLine("  $it") }
        appendLine()

        appendLine("EXCLUSIONS")
        p.exclusions.forEach { appendLine("  - $it") }
        appendLine()

        appendLine("TERMS")
        p.termsText.lines().forEach { line ->
            line.chunkedForPrint(45).forEach { appendLine("  $it") }
        }
        appendLine()

        appendLine("─────────────────────────────────────────────")
        appendLine("ACCEPTED BY ____________________  DATE ______")
        appendLine("═══════════════════════════════════════════════")
    }

    private fun money(v: Double) = "$${"%.2f".format(v)}"
    private fun moneyRight(v: Double) = money(v).padStart(12)
    private fun fmtQty(q: Double): String =
        if (q == q.toLong().toDouble()) q.toLong().toString() else "%.1f".format(q)

    private fun String.chunkedForPrint(width: Int): List<String> {
        if (isBlank()) return emptyList()
        val words = split(" ")
        val out = mutableListOf<StringBuilder>()
        var line = StringBuilder()
        words.forEach { w ->
            if (line.length + w.length + 1 > width) {
                if (line.isNotEmpty()) out.add(line)
                line = StringBuilder(w)
            } else {
                if (line.isNotEmpty()) line.append(' ')
                line.append(w)
            }
        }
        if (line.isNotEmpty()) out.add(line)
        return out.map { it.toString() }
    }
}
