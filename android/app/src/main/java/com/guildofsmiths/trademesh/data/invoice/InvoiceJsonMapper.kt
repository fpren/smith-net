package com.guildofsmiths.trademesh.data.invoice

import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.invoice.InvoiceLineItem
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Translates an apk-side [Invoice] into the wire shapes the backend's
 * /api/invoices endpoints expect. Boundary helpers live here so the
 * ApiClient stays a dumb HTTP layer and the worker doesn't have to
 * know about JSON shape.
 *
 * Unit conversions applied here (from the Android invoice wiring spec):
 *   - tax rate: percent (8.25) -> fraction (0.0825)
 *   - money:    Double -> BigDecimal string with 2-place HALF_UP rounding
 *   - dates:    epoch millis -> ISO 8601 UTC
 *   - status enum: UPPERCASE -> lowercase
 *   - category enum: UPPERCASE -> lowercase
 *
 * The apk-generated invoiceNumber is intentionally NOT sent as a wire
 * field; the backend mints its own. It is preserved in
 * summary.apkInvoiceNumber so it can be inspected later (e.g. matched
 * against an apk-shared PDF).
 *
 * `summary.fullLineItems` is an apk-private array -- the worker reads
 * it back when reconstituting an Invoice off the Room outbox queue.
 * The server doesn't read this field; it reads line items from the
 * dedicated /line-items POST instead.
 */
object InvoiceJsonMapper {

    /** Body for POST /api/invoices. */
    fun createBody(inv: Invoice): String {
        val o = JSONObject()
        o.put("idempotencyKey", inv.id)
        if (inv.toName.isNotEmpty())  o.put("clientName",  inv.toName)
        if (inv.toEmail.isNotEmpty()) o.put("clientEmail", inv.toEmail)
        if (inv.dueDate > 0L)         o.put("dueDate",     formatIso(inv.dueDate))
        if (inv.notes.isNotEmpty())   o.put("notes",       inv.notes)
        // Tax rate: apk side is percent (8.25); backend column is fraction (0.0825).
        if (inv.taxRate > 0.0)        o.put("taxRate",     formatTaxRateNumeric(inv.taxRate))
        o.put("summary", buildSummary(inv))
        return o.toString()
    }

    /** Body for POST /api/invoices/{id}/line-items. */
    fun lineItemBody(li: InvoiceLineItem): String {
        val o = JSONObject()
        o.put("description", li.description)
        o.put("quantity",    li.quantity)
        o.put("unit",        li.unit)
        o.put("rate",        formatMoney(li.rate))
        o.put("category",    li.category.name.lowercase())
        return o.toString()
    }

    /** Body for PATCH /api/invoices/{id}/status. */
    fun statusBody(status: String): String =
        JSONObject().put("status", status.lowercase()).toString()

    private fun buildSummary(inv: Invoice): JSONObject {
        val s = JSONObject()
        s.put("apkInvoiceNumber", inv.invoiceNumber)
        s.put("mode", inv.mode.name)

        val from = JSONObject()
        from.put("name",     inv.fromName)
        from.put("business", inv.fromBusiness)
        from.put("trade",    inv.fromTrade)
        from.put("phone",    inv.fromPhone)
        from.put("email",    inv.fromEmail)
        from.put("address",  inv.fromAddress)
        s.put("from", from)

        val to = JSONObject()
        to.put("name",    inv.toName)
        to.put("company", inv.toCompany)
        to.put("address", inv.toAddress)
        to.put("email",   inv.toEmail)
        s.put("to", to)

        s.put("projectRef", inv.projectRef)
        s.put("poNumber",   inv.poNumber)
        if (inv.projectStart != null) s.put("projectStart", inv.projectStart)
        if (inv.projectEnd   != null) s.put("projectEnd",   inv.projectEnd)
        s.put("workingDays", inv.workingDays)

        val crew = JSONArray()
        inv.crew.forEach { m ->
            val cm = JSONObject()
            cm.put("name", m.name)
            cm.put("role", m.role)
            cm.put("occupation", m.occupation)
            cm.put("totalHours", m.totalHours)
            cm.put("productiveHours", m.productiveHours)
            cm.put("travelHours", m.travelHours)
            crew.put(cm)
        }
        s.put("crew", crew)
        s.put("totalCrewHours", inv.totalCrewHours)

        val daily = JSONArray()
        inv.dailyBreakdown.forEach { d ->
            val dd = JSONObject()
            dd.put("day", d.day)
            dd.put("date", d.date)
            dd.put("startTime", d.startTime)
            dd.put("endTime", d.endTime)
            dd.put("totalHours", d.totalHours)
            dd.put("activities", d.activities)
            dd.put("meshSyncNotes", d.meshSyncNotes)
            dd.put("photoCount", d.photoCount)
            dd.put("voiceNoteCount", d.voiceNoteCount)
            dd.put("checklistCount", d.checklistCount)
            dd.put("keyNotes", d.keyNotes)
            daily.put(dd)
        }
        s.put("dailyBreakdown", daily)

        // Full line items so the worker can reconstitute them when popping a
        // CREATE row off Room. The server does not read this -- it reads from
        // /line-items POSTs instead.
        val full = JSONArray()
        inv.lineItems.forEach {
            val o = JSONObject()
            o.put("code", it.code)
            o.put("description", it.description)
            o.put("quantity", it.quantity)
            o.put("unit", it.unit)
            o.put("rate", it.rate)
            o.put("total", it.total)
            o.put("category", it.category.name)   // UPPERCASE here; wire-side lineItemBody lowercases
            full.put(o)
        }
        s.put("fullLineItems", full)

        s.put("workWindow", inv.workWindow)
        s.put("totalOnSiteMinutes", inv.totalOnSiteMinutes)

        val media = JSONObject()
        media.put("photos", inv.photoCount)
        media.put("voice",  inv.voiceNoteCount)
        media.put("checklist", inv.checklistCount)
        s.put("media", media)

        s.put("workLogSummary",       inv.workLogSummary)
        s.put("complianceNotes",      inv.complianceNotes)
        s.put("recommendations",      inv.recommendations)
        s.put("meshPresence",         inv.meshPresence)
        s.put("efficiencyScore",      inv.efficiencyScore)
        s.put("paymentInstructions",  inv.paymentInstructions)

        // Apk-computed totals stashed for drift detection.
        val computed = JSONObject()
        computed.put("subtotal",   formatMoney(inv.subtotal))
        computed.put("taxRate",    formatTaxRate(inv.taxRate))
        computed.put("taxAmount",  formatMoney(inv.taxAmount))
        computed.put("totalDue",   formatMoney(inv.totalDue))
        s.put("computed", computed)

        s.put("job", JSONObject().put("id", inv.jobId).put("title", inv.jobTitle))

        return s
    }

    private fun formatMoney(amount: Double): String =
        BigDecimal(amount.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun formatTaxRate(percent: Double): String =
        BigDecimal(percent.toString())
            .divide(BigDecimal("100"), 4, RoundingMode.HALF_UP)
            .toPlainString()

    /** Tax rate as a Number for the JSON body — backend wants a numeric, not a string. */
    private fun formatTaxRateNumeric(percent: Double): Double =
        BigDecimal(percent.toString())
            .divide(BigDecimal("100"), 4, RoundingMode.HALF_UP)
            .toDouble()

    private fun formatIso(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).toString()
}
