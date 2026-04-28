package com.guildofsmiths.trademesh.ui.expenses

import com.guildofsmiths.trademesh.data.BolLegalPreferences
import com.guildofsmiths.trademesh.data.BolLegalPreferences.Group
import com.guildofsmiths.trademesh.data.ExpenseCategoryRepository
import com.guildofsmiths.trademesh.ui.invoice.DailyWorkSummary
import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.invoice.InvoiceDetailLevel
import com.guildofsmiths.trademesh.ui.invoice.InvoiceLineItem
import com.guildofsmiths.trademesh.ui.invoice.LineItemCategory
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.LegalFooterScope
import com.guildofsmiths.trademesh.ui.timetracking.EntryType
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OutputMode(val label: String) {
    INVOICE_ONLY("Invoice only"),
    BOL_ONLY("BOL only"),
    INVOICE_AND_BOL("Invoice + BOL")
}

data class DraftApproval(
    val includeDailyNarrative: Boolean = true,
    val includeComplianceNotes: Boolean = true,
    val includeRecommendations: Boolean = true,
    val includeWorkLogSummary: Boolean = true
)

/**
 * Produces a complete HTML document for Invoice and/or BOL rendering.
 * Zero dependencies — pure string concatenation. Emits content that renders
 * identically in Android WebView, any email client, or when piped through
 * Android's PrintManager to PDF.
 */
object InvoiceBolHtmlRenderer {

    fun render(
        invoice: Invoice,
        job: Job,
        timeEntries: List<TimeEntry>,
        mode: OutputMode,
        scope: LegalFooterScope = job.legalFooterScope,
        approval: DraftApproval = DraftApproval(),
        legal: BolLegalPreferences.State = BolLegalPreferences.state.value
    ): String {
        val sb = StringBuilder()
        sb.append("<!doctype html><html lang=\"en\"><head>")
        sb.append("<meta charset=\"utf-8\">")
        sb.append("<title>").append(esc(invoice.invoiceNumber)).append(" · ")
            .append(esc(job.clientName ?: job.title)).append("</title>")
        sb.append("<style>").append(CSS).append("</style>")
        sb.append("</head><body>")

        if (mode != OutputMode.BOL_ONLY) {
            appendInvoicePage(sb, invoice, job, timeEntries, approval, mode)
        }
        if (mode != OutputMode.INVOICE_ONLY) {
            appendBolPage(sb, invoice, job, timeEntries, scope, legal, mode)
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    // ── INVOICE PAGE ─────────────────────────────────────────────────

    private fun appendInvoicePage(
        sb: StringBuilder,
        inv: Invoice,
        job: Job,
        entries: List<TimeEntry>,
        approval: DraftApproval,
        mode: OutputMode
    ) {
        val totalPages = if (mode == OutputMode.INVOICE_AND_BOL) 2 else 1
        sb.append("<section class=\"page\" id=\"invoice\">")

        // Masthead
        sb.append("<header class=\"masthead\"><div>")
            .append("<div class=\"mark\">GUILD OF SMITHS · BUILT FOR THE TRADES</div>")
            .append("<div class=\"biz-name\">").append(esc(inv.fromBusiness.ifBlank { inv.fromName })).append("</div>")
            .append("<div class=\"biz-lines\">")
        if (inv.fromAddress.isNotBlank()) sb.append(esc(inv.fromAddress)).append("<br>")
        if (inv.fromPhone.isNotBlank() || inv.fromEmail.isNotBlank()) {
            sb.append(esc(listOfNotNull(inv.fromPhone.ifBlank { null }, inv.fromEmail.ifBlank { null }).joinToString(" · "))).append("<br>")
        }
        if (inv.fromTrade.isNotBlank()) sb.append(esc(inv.fromTrade))
        sb.append("</div></div><div>")
            .append("<div class=\"doc-title\">INVOICE</div>")
            .append("<div class=\"doc-detail-badge\">ADVANCED</div>")
            .append("<div class=\"doc-meta\">")
            .append("Invoice # &nbsp; ").append(esc(inv.invoiceNumber)).append("<br>")
            .append("Issued &nbsp;&nbsp;&nbsp;&nbsp; ").append(formatDate(inv.issueDate)).append("<br>")
            .append("Due &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ").append(formatDate(inv.dueDate))
        sb.append("</div></div></header>")

        // Parties
        sb.append("<div class=\"parties\"><div><h4>Bill to</h4>")
            .append("<div class=\"name\">").append(esc(inv.toName.ifBlank { job.clientName ?: "" })).append("</div>")
            .append("<div class=\"lines\">")
        if (inv.toCompany.isNotBlank()) sb.append(esc(inv.toCompany)).append("<br>")
        val toAddr = inv.toAddress.ifBlank { job.clientAddress }
        if (toAddr.isNotBlank()) sb.append(esc(toAddr)).append("<br>")
        if (job.clientPhone.isNotBlank()) sb.append(esc(job.clientPhone))
        sb.append("</div></div>")

        sb.append("<div><h4>Remit to</h4>")
            .append("<div class=\"name\">").append(esc(inv.fromName)).append("</div>")
            .append("<div class=\"lines\">").append(esc(inv.paymentInstructions.ifBlank { "Contact for payment details" }))
            .append("</div></div></div>")

        // Project overview (Advanced mode)
        if (approval.includeWorkLogSummary || inv.dailyBreakdown.isNotEmpty() || inv.crew.isNotEmpty()) {
            sb.append("<div class=\"overview\"><h3>PROJECT OVERVIEW</h3><div class=\"ov-grid\">")
            if (inv.workWindow.isNotBlank()) ovCell(sb, "Work window", inv.workWindow)
            ovCell(sb, "Working days", inv.workingDays.toString(), big = true)
            ovCell(sb, "Total on-site", "${String.format("%.1f", inv.totalOnSiteMinutes / 60.0)}h", big = true)
            if (inv.crew.isNotEmpty()) ovCell(sb, "Crew size", "${inv.crew.size}")
            if (inv.photoCount > 0) ovCell(sb, "Photos captured", inv.photoCount.toString())
            if (inv.voiceNoteCount > 0) ovCell(sb, "Voice notes", inv.voiceNoteCount.toString())
            if (inv.checklistCount > 0) ovCell(sb, "Checklist items", inv.checklistCount.toString())
            if (inv.meshPresence.isNotBlank()) ovCell(sb, "Mesh presence", inv.meshPresence)
            if (inv.efficiencyScore > 0) ovCell(sb, "Efficiency score", "${inv.efficiencyScore} / 100", big = true)
            sb.append("</div></div>")
        }

        // Crew roster
        if (inv.crew.isNotEmpty()) {
            sb.append("<section class=\"crew-section\"><h3>CREW</h3>")
                .append("<table class=\"crew\"><thead><tr><th>Name · Role</th>")
                .append("<th class=\"n\">Total hours</th><th class=\"n\">Productive</th>")
                .append("<th class=\"n\">Travel</th></tr></thead><tbody>")
            inv.crew.forEach { c ->
                val initials = c.name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
                sb.append("<tr class=\"crew-row\"><td>")
                    .append("<span class=\"avatar\">").append(esc(initials)).append("</span>")
                    .append("<b>").append(esc(c.name)).append("</b>")
                    .append(" <span class=\"role\">").append(esc(c.role))
                if (c.occupation.isNotBlank()) sb.append(" · ").append(esc(c.occupation))
                sb.append("</span></td>")
                sb.append("<td class=\"n\">").append(String.format("%.1f", c.totalHours)).append("</td>")
                sb.append("<td class=\"n\">").append(String.format("%.1f", c.productiveHours)).append("</td>")
                sb.append("<td class=\"n\">").append(String.format("%.1f", c.travelHours)).append("</td></tr>")
            }
            sb.append("</tbody></table></section>")
        }

        // Daily breakdown
        if (approval.includeDailyNarrative && inv.dailyBreakdown.isNotEmpty()) {
            sb.append("<section class=\"daily-section\"><h3>DAILY BREAKDOWN</h3>")
            inv.dailyBreakdown.forEach { d -> appendDay(sb, d) }
            sb.append("</section>")
        }

        // Line items
        if (inv.lineItems.isNotEmpty()) {
            sb.append("<table class=\"lines\"><thead><tr>")
                .append("<th class=\"c\">&nbsp;</th><th>Code · Description</th>")
                .append("<th class=\"n\">Qty</th><th class=\"n\">Rate</th><th class=\"n\">Total</th>")
                .append("</tr></thead><tbody>")
            val grouped = inv.lineItems.groupBy { it.category }
            LineItemCategory.entries.forEach { cat ->
                val items = grouped[cat] ?: return@forEach
                if (items.isEmpty()) return@forEach
                sb.append("<tr class=\"group-head\"><td colspan=\"5\">")
                    .append(esc(cat.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }))
                    .append("</td></tr>")
                items.forEach { li ->
                    sb.append("<tr><td class=\"c\"><span class=\"tag\">").append(esc(shortTag(cat))).append("</span></td>")
                        .append("<td>").append(esc(li.code)).append(" · ").append(esc(li.description)).append("</td>")
                        .append("<td class=\"n\">").append(String.format("%.2f", li.quantity)).append("</td>")
                        .append("<td class=\"n\">").append(String.format("%.2f", li.rate)).append("</td>")
                        .append("<td class=\"n\">").append(String.format("%.2f", li.total)).append("</td></tr>")
                }
            }
            sb.append("</tbody></table>")
        }

        // Totals
        sb.append("<div class=\"totals\"><table>")
        sb.append("<tr><td class=\"label\">Subtotal</td><td class=\"val\">$").append(String.format("%.2f", inv.subtotal)).append("</td></tr>")
        if (inv.taxAmount > 0) {
            sb.append("<tr><td class=\"label\">Tax (").append(String.format("%.2f", inv.taxRate)).append("%)</td>")
                .append("<td class=\"val\">$").append(String.format("%.2f", inv.taxAmount)).append("</td></tr>")
        }
        sb.append("<tr class=\"grand\"><td>Total</td><td class=\"val\">$").append(String.format("%.2f", inv.totalDue)).append("</td></tr>")
        if (job.depositCollected > 0) {
            sb.append("<tr class=\"deduction\"><td class=\"label\">Less deposit on file</td>")
                .append("<td class=\"val\">−$").append(String.format("%.2f", job.depositCollected)).append("</td></tr>")
            sb.append("<tr class=\"grand\"><td>Balance due</td><td class=\"val\">$")
                .append(String.format("%.2f", inv.totalDue - job.depositCollected)).append("</td></tr>")
        }
        sb.append("</table></div>")

        // Compliance / Recommendations / Work-log summary (gated on approval)
        val showCompliance = approval.includeComplianceNotes && inv.complianceNotes.isNotBlank()
        val showRecs = approval.includeRecommendations && inv.recommendations.isNotBlank()
        val showSum = approval.includeWorkLogSummary && inv.workLogSummary.isNotBlank()
        if (showCompliance || showRecs || showSum) {
            sb.append("<div class=\"supervisor\">")
            if (showCompliance) sb.append("<div class=\"sup-card compliance\"><h4>Compliance notes</h4>")
                .append(esc(inv.complianceNotes)).append("</div>")
            if (showRecs) sb.append("<div class=\"sup-card recommend\"><h4>Recommendations</h4>")
                .append(esc(inv.recommendations)).append("</div>")
            if (showSum) sb.append("<div class=\"sup-card summary\"><h4>Work-log summary</h4>")
                .append(esc(inv.workLogSummary)).append("</div>")
            sb.append("</div>")
        }

        // Footer
        sb.append("<div class=\"doc-foot\">")
            .append("<div>Thank you — payment appreciated within terms.</div>")
            .append("<div>Invoice ").append(esc(inv.invoiceNumber)).append(" · Page 1 of ").append(totalPages).append("</div>")
            .append("</div>")

        sb.append("</section>")
    }

    private fun appendDay(sb: StringBuilder, d: DailyWorkSummary) {
        val dateLabel = SimpleDateFormat("EEE · MMM d, yyyy", Locale.US).format(Date(d.date))
        sb.append("<div class=\"day\"><div class=\"day-head\"><div>")
            .append("<div class=\"date\"><span class=\"num\">DAY ").append(d.day).append("</span>").append(esc(dateLabel)).append("</div>")
            .append("<div class=\"meta\">")
        if (d.startTime.isNotBlank() && d.endTime.isNotBlank()) {
            sb.append(esc(d.startTime)).append(" → ").append(esc(d.endTime))
        }
        sb.append("</div></div>")
            .append("<div class=\"day-hours\">").append(String.format("%.1f", d.totalHours)).append("h</div>")
            .append("</div>")
        if (d.activities.isNotBlank()) {
            sb.append("<div class=\"day-narrative\">").append(esc(d.activities)).append("</div>")
        }
        val hasArtifacts = d.photoCount > 0 || d.voiceNoteCount > 0 || d.checklistCount > 0 || d.meshSyncNotes.isNotBlank()
        if (hasArtifacts) {
            sb.append("<div class=\"day-artifacts\">")
            if (d.photoCount > 0) sb.append("<span class=\"chip\"><b>").append(d.photoCount).append("</b> photos</span>")
            if (d.voiceNoteCount > 0) sb.append("<span class=\"chip\"><b>").append(d.voiceNoteCount).append("</b> voice notes</span>")
            if (d.checklistCount > 0) sb.append("<span class=\"chip\"><b>").append(d.checklistCount).append("</b> checklist items</span>")
            if (d.meshSyncNotes.isNotBlank()) sb.append("<span class=\"chip\">").append(esc(d.meshSyncNotes)).append("</span>")
            sb.append("</div>")
        }
        if (d.keyNotes.isNotBlank()) {
            sb.append("<div class=\"key-notes\"><b>Note:</b> ").append(esc(d.keyNotes)).append("</div>")
        }
        sb.append("</div>")
    }

    private fun ovCell(sb: StringBuilder, k: String, v: String, big: Boolean = false) {
        sb.append("<div class=\"ov-cell\"><div class=\"k\">").append(esc(k)).append("</div>")
        sb.append("<div class=\"v").append(if (big) " big" else "").append("\">").append(esc(v)).append("</div></div>")
    }

    private fun shortTag(c: LineItemCategory): String = when (c) {
        LineItemCategory.LABOR -> "L"
        LineItemCategory.MATERIALS -> "M"
        LineItemCategory.TRAVEL -> "TR"
        LineItemCategory.CHANGE_ORDER -> "CO"
        LineItemCategory.OTHER -> "·"
    }

    // ── BOL PAGE ─────────────────────────────────────────────────────

    private fun appendBolPage(
        sb: StringBuilder,
        inv: Invoice,
        job: Job,
        entries: List<TimeEntry>,
        scope: LegalFooterScope,
        legal: BolLegalPreferences.State,
        mode: OutputMode
    ) {
        val pageNum = if (mode == OutputMode.INVOICE_AND_BOL) 2 else 1
        val totalPages = if (mode == OutputMode.INVOICE_AND_BOL) 2 else 1
        val bolNumber = "GS-${job.id.take(6).uppercase()}-B"
        sb.append("<section class=\"page\" id=\"bol\">")

        sb.append("<header class=\"masthead\"><div>")
            .append("<div class=\"mark\">GUILD OF SMITHS · BUILT FOR THE TRADES</div>")
            .append("<div class=\"biz-name\">Bill of Work &amp; Expenses</div>")
            .append("<div class=\"biz-lines\">")
        if (mode == OutputMode.INVOICE_AND_BOL) {
            sb.append("Issued with Invoice ").append(esc(inv.invoiceNumber)).append("<br>")
        } else {
            sb.append("Standalone — references Invoice ").append(esc(inv.invoiceNumber)).append("<br>")
        }
        sb.append("Document of title under UCC Art. 7 / Pomerene Act 49 U.S.C. §§ 80101–80116")
        sb.append("</div></div><div>")
            .append("<div class=\"doc-title\">BOL</div>")
            .append("<div class=\"doc-meta\">")
            .append("BOL # &nbsp;&nbsp;&nbsp;&nbsp; ").append(esc(bolNumber)).append("<br>")
            .append("Date &nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ").append(formatDate(System.currentTimeMillis()))
            .append("</div></div></header>")

        // Parties
        sb.append("<div class=\"parties\"><div><h4>Ship from</h4>")
            .append("<div class=\"name\">").append(esc(inv.fromName)).append("</div>")
            .append("<div class=\"lines\">").append(esc(inv.fromAddress)).append("</div></div>")
        sb.append("<div><h4>Ship to</h4>")
            .append("<div class=\"name\">").append(esc(inv.toName.ifBlank { job.clientName ?: "" })).append("</div>")
            .append("<div class=\"lines\">")
        if (job.clientAddress.isNotBlank()) sb.append(esc(job.clientAddress)).append("<br>")
        if (job.clientPhone.isNotBlank()) sb.append(esc(job.clientPhone))
        sb.append("</div></div></div>")

        // Line items (labor from TimeEntry, materials + other from Job.expenses + Job.materials)
        sb.append("<table class=\"lines\"><thead><tr>")
            .append("<th class=\"c\">Unit</th><th>Description</th>")
            .append("<th class=\"n\">Qty</th><th class=\"n\">Rate</th><th class=\"n\">Total</th>")
            .append("</tr></thead><tbody>")

        val jobEntries = entries.filter { it.clockOutTime != null && (it.jobId == job.id || it.jobTitle == job.title) }
            .sortedBy { it.clockInTime }
        val dayFmt = SimpleDateFormat("MMM d", Locale.US)
        var laborSub = 0.0
        jobEntries.forEach { e ->
            val hrs = (e.durationMinutes ?: 0) / 60.0
            val rate = if (e.entryType == EntryType.OVERTIME) job.hourlyRate * 1.5 else job.hourlyRate
            val total = hrs * rate
            laborSub += total
            sb.append("<tr><td class=\"c\"><span class=\"tag\">L</span></td>")
                .append("<td>Labor ").append(esc(dayFmt.format(Date(e.clockInTime))))
                .append(" · ").append(esc(e.entryType.displayName)).append("</td>")
                .append("<td class=\"n\">").append(String.format("%.2f", hrs)).append("</td>")
                .append("<td class=\"n\">").append(String.format("%.2f", rate)).append("</td>")
                .append("<td class=\"n\">").append(String.format("%.2f", total)).append("</td></tr>")
        }

        var matSub = 0.0
        job.materials.forEach { m ->
            matSub += m.totalCost
            sb.append("<tr><td class=\"c\"><span class=\"tag\">M</span></td>")
                .append("<td>").append(esc(m.name))
            if (m.vendor.isNotBlank()) sb.append(" · ").append(esc(m.vendor))
            sb.append("</td>")
                .append("<td class=\"n\">").append(String.format("%.2f", m.quantity)).append("</td>")
                .append("<td class=\"n\">").append(String.format("%.2f", m.unitCost)).append("</td>")
                .append("<td class=\"n\">").append(String.format("%.2f", m.totalCost)).append("</td></tr>")
        }

        var otherSub = 0.0
        val matFromExpenses = job.expenses.filter { it.category == "material" }.sumOf { it.totalCost }
        job.expenses.sortedBy { it.incurredAt }.forEach { exp ->
            if (exp.category == "labor") return@forEach
            val def = ExpenseCategoryRepository.resolve(exp.category)
            if (exp.category != "material") otherSub += exp.totalCost
            sb.append("<tr><td class=\"c\"><span class=\"tag\">").append(esc(def.shortCode.trim('[', ']'))).append("</span></td>")
                .append("<td>").append(esc(exp.description))
            if (exp.vendor.isNotBlank()) sb.append(" · ").append(esc(exp.vendor))
            if (!exp.referenceNumber.isNullOrBlank()) sb.append(" · #").append(esc(exp.referenceNumber))
            sb.append("</td>")
                .append("<td class=\"n\">").append(String.format("%.2f", exp.quantity)).append("</td>")
                .append("<td class=\"n\">").append(String.format("%.2f", exp.unitCost)).append("</td>")
                .append("<td class=\"n\">").append(String.format("%.2f", exp.totalCost)).append("</td></tr>")
        }

        sb.append("</tbody></table>")

        // Totals
        val grand = laborSub + matSub + matFromExpenses + otherSub
        sb.append("<div class=\"totals\"><table>")
            .append("<tr><td class=\"label\">Subtotal labor</td><td class=\"val\">$")
            .append(String.format("%.2f", laborSub)).append("</td></tr>")
            .append("<tr><td class=\"label\">Subtotal materials</td><td class=\"val\">$")
            .append(String.format("%.2f", matSub + matFromExpenses)).append("</td></tr>")
            .append("<tr><td class=\"label\">Subtotal other</td><td class=\"val\">$")
            .append(String.format("%.2f", otherSub)).append("</td></tr>")
            .append("<tr class=\"grand\"><td>Grand total</td><td class=\"val\">$")
            .append(String.format("%.2f", grand)).append("</td></tr>")
        if (job.depositCollected > 0) {
            sb.append("<tr class=\"deduction\"><td class=\"label\">Deposit applied</td><td class=\"val\">−$")
                .append(String.format("%.2f", job.depositCollected)).append("</td></tr>")
            sb.append("<tr class=\"grand\"><td>Net unbilled</td><td class=\"val\">$")
                .append(String.format("%.2f", grand - job.depositCollected)).append("</td></tr>")
        }
        sb.append("</table></div>")

        // LEGAL FOOTER — filtered by scope
        appendLegalFooter(sb, scope, legal)

        // Signatures
        if (legal.includeSignatureBlock) {
            sb.append("<section class=\"sigs\"><h3>SIGNATURES</h3><div class=\"sig-grid\">")
            listOf(
                Triple("role-shipper", legal.shipperLabel, inv.fromName),
                Triple("role-carrier", legal.carrierLabel, ""),
                Triple("role-consignee", legal.consigneeLabel, inv.toName.ifBlank { job.clientName ?: "" })
            ).forEach { (_, role, name) ->
                sb.append("<div class=\"sig\"><div class=\"role\">").append(esc(role)).append("</div>")
                sb.append("<div class=\"line\"></div><div class=\"ln-label\">Signature")
                if (name.isNotBlank()) sb.append(" — ").append(esc(name))
                sb.append("</div>")
                sb.append("<div class=\"line\" style=\"margin-top:14px\"></div><div class=\"ln-label\">Printed name</div>")
                sb.append("<div class=\"line\" style=\"margin-top:14px\"></div><div class=\"ln-label\">Date</div>")
                sb.append("</div>")
            }
            sb.append("</div>")

            if (legal.includeNotarization) {
                sb.append("<div class=\"notary\">")
                    .append("Sworn to (or affirmed) before me this ____ day of _______________, 20____,<br>")
                    .append("by ____________________________, personally known to me or identified by ______________________________.")
                    .append("<br><br>")
                    .append("Notary Public, State of ____________ &nbsp;&nbsp; Commission expires: ____________")
                    .append("</div>")
            }
            sb.append("</section>")
        }

        sb.append("<div class=\"doc-foot\">")
            .append("<div>This document accompanies Invoice ").append(esc(inv.invoiceNumber)).append(".</div>")
            .append("<div>BOL ").append(esc(bolNumber)).append(" · Page ").append(pageNum).append(" of ").append(totalPages).append("</div>")
            .append("</div>")

        sb.append("</section>")
    }

    private fun appendLegalFooter(sb: StringBuilder, scope: LegalFooterScope, legal: BolLegalPreferences.State) {
        val allowedGroups: Set<Group> = when (scope) {
            LegalFooterScope.DOMESTIC -> setOf(Group.US_DOMESTIC, Group.US_STATES)
            LegalFooterScope.INTERNATIONAL -> setOf(Group.INTL_COMMERCIAL, Group.INTERNATIONAL_CARRIAGE)
            LegalFooterScope.BOTH -> Group.entries.toSet()
        }
        val ordered = legal.enabled
            .filter { it.group in allowedGroups }
            .sortedWith(compareBy({ it.group.ordinal }, { it.ordinal }))
        if (ordered.isEmpty() && legal.customDisclaimer.isBlank()) return

        sb.append("<section class=\"legal\"><h3>TERMS &amp; CONDITIONS</h3>")
        var lastGroup: Group? = null
        ordered.forEach { preset ->
            val info = BolLegalTerms.infoFor(preset) ?: return@forEach
            if (info.group != lastGroup) {
                lastGroup = info.group
                sb.append("<div class=\"group-head\">— ").append(esc(groupHeading(info.group))).append(" —</div>")
            }
            sb.append("<div class=\"clause\"><div class=\"head\">").append(esc(info.shortLabel)).append("</div>")
                .append(esc(info.body)).append("</div>")
        }
        if (legal.customDisclaimer.isNotBlank()) {
            sb.append("<div class=\"group-head\">— Additional terms —</div>")
            sb.append("<div class=\"clause\">").append(esc(legal.customDisclaimer)).append("</div>")
        }
        sb.append("<div class=\"safe-harbor\"><b>Disclaimer.</b> ")
            .append("The language above is a plain-language summary of the cited statutes, regulations, and procedural rules. ")
            .append("It is not legal advice, does not create an attorney-client relationship, and does not purport to waive any right or defense available under any other statute or rule of court. ")
            .append("For matters involving disputed sums, regulated work, litigation, or out-of-state / cross-border parties, review by counsel is recommended before reliance.")
        if (legal.enabled.any { it.group == Group.COMMERCIAL_LIEN }) {
            sb.append(" Where any commercial-lien / affidavit clause is enabled, the issuer acknowledges that such language is contested in many mainstream courts and is used at the issuer's own election and risk.")
        }
        sb.append("</div></section>")
    }

    private fun groupHeading(g: Group): String = when (g) {
        Group.US_DOMESTIC -> "US Domestic"
        Group.US_STATES -> "US States"
        Group.INTL_COMMERCIAL -> "International commercial"
        Group.INTERNATIONAL_CARRIAGE -> "International carriage"
        Group.COMMERCIAL_LIEN -> "Commercial-lien / affidavit tradition"
    }

    private fun formatDate(epoch: Long): String =
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epoch))

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("\n", "<br>")

    // ── SHARED STYLESHEET ────────────────────────────────────────────

    private val CSS = """
        :root{--bg:#f4f2ee;--surface:#fff;--ink:#1c1814;--muted:#6b645c;--rule:#d8d1c4;--accent:#8c6b2a;--accent-soft:#f2ead8;--success:#5a8c76;--warn:#a67c00;--danger:#8c3a3a;--mono:"IBM Plex Mono","SFMono-Regular",Menlo,Consolas,monospace;--sans:-apple-system,"Inter",Segoe UI,Roboto,system-ui,sans-serif}
        html,body{background:var(--bg);color:var(--ink);margin:0;font-family:var(--sans);font-size:14px;line-height:1.5}
        .page{max-width:840px;margin:24px auto;background:var(--surface);border:1px solid var(--rule);padding:42px 52px}
        .page+.page{margin-top:32px}
        .masthead{display:flex;align-items:flex-start;justify-content:space-between;border-bottom:2px solid var(--ink);padding-bottom:16px}
        .mark{font-family:var(--mono);font-weight:700;font-size:13px;letter-spacing:1.2px;color:var(--accent)}
        .biz-name{font-size:22px;font-weight:700;letter-spacing:-.2px;margin:2px 0 6px}
        .biz-lines{font-family:var(--mono);color:var(--muted);font-size:12px;line-height:1.5}
        .doc-title{text-align:right;font-family:var(--mono);font-weight:700;font-size:22px;letter-spacing:2px}
        .doc-detail-badge{display:inline-block;margin-top:4px;padding:2px 8px;background:var(--accent);color:#fff;font-family:var(--mono);font-size:10px;letter-spacing:1.2px;border-radius:2px}
        .doc-meta{text-align:right;font-family:var(--mono);color:var(--muted);font-size:12px;margin-top:6px;line-height:1.6}
        .parties{display:grid;grid-template-columns:1fr 1fr;gap:32px;padding:20px 0;border-bottom:1px solid var(--rule)}
        .parties h4{font-family:var(--mono);font-size:11px;letter-spacing:1px;color:var(--muted);margin:0 0 6px;font-weight:600;text-transform:uppercase}
        .parties .name{font-weight:600}
        .parties .lines{color:var(--muted);font-size:13px}
        .overview{margin-top:20px;padding:18px 20px;background:var(--accent-soft);border:1px solid var(--rule);border-radius:4px}
        .overview h3{font-family:var(--mono);font-size:11px;letter-spacing:2px;margin:0 0 12px;color:var(--accent)}
        .ov-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}
        .ov-cell .k{font-family:var(--mono);font-size:10px;color:var(--muted);text-transform:uppercase;letter-spacing:.8px}
        .ov-cell .v{font-family:var(--mono);font-size:14px;font-weight:700;color:var(--ink);margin-top:2px}
        .ov-cell .v.big{font-size:18px;color:var(--accent)}
        .crew-section,.daily-section{margin-top:26px}
        .crew-section h3,.daily-section h3{font-family:var(--mono);font-size:12px;letter-spacing:2px;color:var(--muted);margin:0 0 12px}
        table.crew{width:100%;border-collapse:collapse;font-family:var(--mono);font-size:12.5px}
        table.crew th{text-align:left;font-size:10.5px;letter-spacing:1px;text-transform:uppercase;color:var(--muted);border-bottom:1px solid var(--ink);padding:8px 6px}
        table.crew th.n,table.crew td.n{text-align:right;white-space:nowrap}
        table.crew td{border-bottom:1px solid var(--rule);padding:8px 6px;vertical-align:middle}
        .avatar{display:inline-block;width:28px;height:28px;border-radius:50%;background:var(--accent);color:#fff;font-weight:700;font-family:var(--mono);font-size:11px;text-align:center;line-height:28px;margin-right:10px;vertical-align:middle}
        .crew-row .role{color:var(--muted);font-size:11px;display:block;margin-left:38px}
        .day{border:1px solid var(--rule);border-radius:4px;padding:14px 16px;margin-bottom:12px;background:var(--surface)}
        .day-head{display:flex;justify-content:space-between;align-items:baseline;border-bottom:1px dashed var(--rule);padding-bottom:8px;margin-bottom:10px}
        .day-head .date{font-weight:700;font-size:15px}
        .day-head .date .num{color:var(--accent);font-family:var(--mono);margin-right:8px;letter-spacing:1px}
        .day-head .meta{font-family:var(--mono);font-size:11px;color:var(--muted)}
        .day-head .meta b{color:var(--ink)}
        .day-hours{font-family:var(--mono);font-size:13px;color:var(--accent);font-weight:700}
        .day-narrative{font-size:13px;color:var(--ink);line-height:1.6}
        .day-artifacts{margin-top:10px;display:flex;gap:14px;flex-wrap:wrap;font-family:var(--mono);font-size:11px;color:var(--muted)}
        .day-artifacts .chip{padding:2px 8px;border:1px solid var(--rule);border-radius:2px;background:var(--surface)}
        .day-artifacts .chip b{color:var(--ink)}
        .key-notes{margin-top:8px;padding:6px 10px;background:#fbf5df;border-left:2px solid var(--warn);font-size:12px;color:#5b4320}
        table.lines{width:100%;border-collapse:collapse;margin-top:18px;font-family:var(--mono);font-size:12.5px}
        table.lines thead th{text-align:left;color:var(--muted);font-weight:600;font-size:10.5px;letter-spacing:1px;text-transform:uppercase;border-bottom:1.5px solid var(--ink);padding:8px 6px}
        table.lines th.n,table.lines td.n{text-align:right;white-space:nowrap}
        table.lines th.c,table.lines td.c{width:40px}
        table.lines tbody td{padding:7px 6px;border-bottom:1px solid var(--rule);vertical-align:top}
        table.lines tbody tr.group-head td{background:var(--accent-soft);color:var(--accent);font-weight:700;letter-spacing:1px;font-size:10.5px;text-transform:uppercase;padding:6px 6px}
        .tag{display:inline-block;padding:1px 6px;border-radius:2px;background:var(--accent-soft);color:var(--accent);font-weight:600;font-size:10px;letter-spacing:.5px}
        .totals{display:flex;justify-content:flex-end;margin-top:18px}
        .totals table{font-family:var(--mono);font-size:13px;min-width:380px}
        .totals td{padding:4px 10px}
        .totals td.label{color:var(--muted)}
        .totals td.val{text-align:right}
        .totals tr.grand td{border-top:2px solid var(--ink);font-weight:700;font-size:15px;padding-top:10px}
        .totals tr.grand td.val{color:var(--accent)}
        .totals tr.deduction td.val{color:var(--danger)}
        .supervisor{margin-top:26px;display:grid;grid-template-columns:1fr 1fr;gap:16px}
        .sup-card{border:1px solid var(--rule);border-radius:4px;padding:12px 14px;font-size:12.5px;line-height:1.55}
        .sup-card h4{font-family:var(--mono);font-size:10.5px;letter-spacing:1.2px;color:var(--muted);margin:0 0 6px;text-transform:uppercase}
        .sup-card.compliance{border-color:#c8d8cf;background:#f0f6f3}
        .sup-card.recommend{border-color:#d7cfb8;background:#faf5e6}
        .sup-card.summary{grid-column:1/-1;border-color:var(--rule);background:#faf8f3}
        section.legal{margin-top:24px;border-top:1px dashed var(--rule);padding-top:18px}
        section.legal h3{font-family:var(--mono);font-size:12px;letter-spacing:2px;margin:0 0 12px;color:var(--muted)}
        .legal .group-head{font-family:var(--mono);color:var(--accent);letter-spacing:1.4px;font-size:10.5px;margin:18px 0 6px;font-weight:700;text-transform:uppercase;border-top:1px solid var(--rule);padding-top:12px}
        .legal .clause{margin:0 0 12px;font-size:12.5px;line-height:1.6}
        .legal .clause .head{color:var(--accent);font-family:var(--mono);font-size:11px;letter-spacing:.6px;text-transform:uppercase;margin-bottom:3px}
        .safe-harbor{margin-top:14px;padding:10px 12px;background:var(--accent-soft);border-left:3px solid var(--accent);font-size:12px;color:var(--ink);font-style:italic}
        section.sigs{margin-top:28px;padding-top:18px;border-top:1px solid var(--rule)}
        section.sigs h3{font-family:var(--mono);font-size:12px;letter-spacing:2px;margin:0 0 18px;color:var(--muted)}
        .sig-grid{display:grid;grid-template-columns:1fr 1fr 1fr;gap:20px}
        .sig .role{font-family:var(--mono);font-size:10.5px;color:var(--muted);text-transform:uppercase;letter-spacing:1px;margin-bottom:22px}
        .sig .line{border-bottom:1px solid var(--ink);height:16px}
        .sig .ln-label{font-size:10.5px;color:var(--muted);margin-top:4px;font-family:var(--mono);letter-spacing:.4px}
        .notary{margin-top:22px;padding:12px 14px;border:1px dashed var(--rule);font-family:var(--mono);font-size:11.5px;color:var(--muted);line-height:1.8}
        .doc-foot{margin-top:26px;padding-top:12px;border-top:1px solid var(--rule);display:flex;justify-content:space-between;font-family:var(--mono);color:var(--muted);font-size:10.5px;letter-spacing:.3px}
        @page{size:Letter;margin:14mm}
        @media print{body{background:#fff}.page{box-shadow:none;border:none;margin:0;max-width:none;padding:0;page-break-after:always}.day,.sup-card{page-break-inside:avoid}}
    """.trimIndent()
}
