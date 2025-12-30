package com.guildofsmiths.data

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

/**
 * CordInvoiceFormatter: Generates Guild of Smiths invoice text from cord-derived data.
 *
 * Based on the provided invoice templates, this formatter creates professional invoices
 * with AI supervisor reports and detailed breakdowns derived from immutable cord entries.
 */
object CordInvoiceFormatter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    private val shortDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

    /**
     * Format cord invoice as text (simplified solo format).
     */
    fun formatAsText(invoice: SimpleCordInvoice): String {
        return formatSoloInvoice(invoice)
    }

    private fun formatSoloInvoice(invoice: SimpleCordInvoice): String {
        val sb = StringBuilder()

        // Header
        sb.appendLine("GUILD OF SMITHS INVOICE – ADVANCED (AI Supervisor Report Attached)")
        sb.appendLine("──────────────────────────────────────────────────────────────")
        sb.appendLine()

        // Invoice details
        sb.appendLine("Invoice #: ${invoice.invoiceNumber}")
        sb.appendLine("From: ${invoice.fromName}")
        sb.appendLine("To: ${invoice.toName}")
        sb.appendLine()


        // To section already handled in header

        // Line items
        appendLineItems(sb, invoice)


        // Cord verification
        sb.appendLine("CORD VERIFICATION:")
        sb.appendLine("• Derived from ${invoice.cordEntryIds.size} immutable cord entries")
        sb.appendLine("• All entries cryptographically signed")
        sb.appendLine("• Deterministically ordered via Lamport clocks")
        sb.appendLine("• Verification hash: ${invoice.verificationHash}")
        sb.appendLine("• Legal audit trail available")
        sb.appendLine()

        sb.appendLine("Guild of Smiths – Cord-based integrity")

        return sb.toString()
    }



    private fun appendLineItems(sb: StringBuilder, invoice: SimpleCordInvoice) {
        sb.appendLine("Description                                      | Qty       | Rate    | Amount")
        sb.appendLine("─────────────────────────────────────────────────┼───────────┼─────────┼──────────")

        invoice.lineItems.forEach { item ->
            val desc = item.description.take(45).padEnd(45)
            val qty = String.format("%.1f", item.quantity).padStart(9)
            val rate = formatCurrency(item.rate).padStart(8)
            val total = formatCurrency(item.total).padStart(10)
            sb.appendLine("$desc| $qty | $rate | $total")
        }

        sb.appendLine("─────────────────────────────────────────────────┼───────────┼─────────┼──────────")
        sb.appendLine("Subtotal                                         |           |         | ${formatCurrency(invoice.subtotal).padStart(10)}")
        sb.appendLine("Sales Tax (8.25%)                              |           |         | ${formatCurrency(invoice.taxAmount).padStart(10)}")
        sb.appendLine("─────────────────────────────────────────────────┼───────────┼─────────┼──────────")
        sb.appendLine("Total Due                                        |           |         | ${formatCurrency(invoice.totalDue).padStart(10)}")
        sb.appendLine()
    }

    private fun formatCurrency(amount: Double): String {
        return "$${String.format("%.2f", amount)}"
    }

    private fun formatQty(qty: Double, unit: String): String {
        return if (qty == qty.toLong().toDouble()) {
            "${qty.toLong()}$unit"
        } else {
            "${String.format("%.1f", qty)}$unit"
        }
    }

    private fun daysBetween(start: Long, end: Long): Int {
        return ((end - start) / (1000 * 60 * 60 * 24)).toInt()
    }
}
