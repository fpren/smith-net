package com.guildofsmiths.trademesh.data.invoice

import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.invoice.InvoiceLineItem
import com.guildofsmiths.trademesh.ui.invoice.InvoiceMode
import com.guildofsmiths.trademesh.ui.invoice.LineItemCategory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class InvoiceJsonMapperTest {

    @Test fun createBody_emits_idempotencyKey_clientName_clientEmail() {
        val inv = sampleInvoice()
        val body = JSONObject(InvoiceJsonMapper.createBody(inv))
        assertEquals(inv.id, body.getString("idempotencyKey"))
        assertEquals("Acme Roofing", body.getString("clientName"))
        assertEquals("ops@acme.com", body.getString("clientEmail"))
    }

    @Test fun createBody_emits_iso_dueDate() {
        val inv = sampleInvoice().copy(dueDate = 1_700_000_000_000L) // 2023-11-14T22:13:20Z
        val body = JSONObject(InvoiceJsonMapper.createBody(inv))
        assertEquals("2023-11-14T22:13:20Z", body.getString("dueDate"))
    }

    @Test fun createBody_embeds_summary_with_apk_only_fields() {
        val inv = sampleInvoice().copy(
            efficiencyScore = 93,
            meshPresence = "97.2% average",
            workLogSummary = "south wall",
        )
        val body = JSONObject(InvoiceJsonMapper.createBody(inv))
        val summary = body.getJSONObject("summary")
        assertEquals(93, summary.getInt("efficiencyScore"))
        assertEquals("97.2% average", summary.getString("meshPresence"))
        assertEquals("south wall", summary.getString("workLogSummary"))
        assertEquals("SOLO", summary.getString("mode"))
    }

    @Test fun lineItemBody_lowercases_category_and_formats_money_as_string() {
        val li = InvoiceLineItem(
            code = "LAB-01",
            description = "Labor",
            quantity = 4.0,
            unit = "hr",
            rate = 85.0,
            total = 340.0,
            category = LineItemCategory.LABOR,
        )
        val body = JSONObject(InvoiceJsonMapper.lineItemBody(li, clientItemId = "inv-1-li-0"))
        assertEquals("Labor",       body.getString("description"))
        assertEquals(4.0,           body.getDouble("quantity"), 0.0001)
        assertEquals("hr",          body.getString("unit"))
        assertEquals("85.00",       body.getString("rate"))
        assertEquals("labor",       body.getString("category"))
        assertEquals("inv-1-li-0",  body.getString("clientItemId"))
    }

    @Test fun statusBody_lowercases_enum() {
        assertEquals("sent",
            JSONObject(InvoiceJsonMapper.statusBody("sent")).getString("status"))
    }

    @Test fun summary_preserves_apk_invoice_number() {
        val inv = sampleInvoice().copy(invoiceNumber = "INV-2026-05-0001-CREW-WEEK")
        val body = JSONObject(InvoiceJsonMapper.createBody(inv))
        val summary = body.getJSONObject("summary")
        assertEquals("INV-2026-05-0001-CREW-WEEK", summary.getString("apkInvoiceNumber"))
    }

    @Test fun createBody_emits_taxRate_as_fraction_not_percent() {
        val inv = sampleInvoice().copy(taxRate = 8.25)
        val body = JSONObject(InvoiceJsonMapper.createBody(inv))
        // Wire format: numeric fraction (0.0825), not string, not percent.
        assertEquals(0.0825, body.getDouble("taxRate"), 0.00001)
    }

    @Test fun createBody_omits_taxRate_when_zero() {
        val inv = sampleInvoice().copy(taxRate = 0.0)
        val body = JSONObject(InvoiceJsonMapper.createBody(inv))
        assertFalse(body.has("taxRate"))
    }

    private fun sampleInvoice(): Invoice = Invoice(
        id = "inv-uuid-aaaa",
        invoiceNumber = "INV-2026-05-0001",
        issueDate = 1_700_000_000_000L,
        dueDate = 1_700_000_000_000L,
        mode = InvoiceMode.SOLO,
        fromName = "Jane",
        toName = "Acme Roofing",
        toEmail = "ops@acme.com",
        jobId = "job-1",
        jobTitle = "Reroof",
    )
}
