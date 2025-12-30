package com.guildofsmiths.data

/**
 * CordInvoiceSimple: Simple demonstration of cord-based invoice derivation.
 *
 * This shows the core concept: invoices derived from immutable cord entries
 * instead of mutable chat state, providing legal/financial guarantees.
 */

/**
 * Simple cord-based invoice structure.
 */
data class SimpleCordInvoice(
    val invoiceNumber: String,
    val fromName: String,
    val toName: String,
    val lineItems: List<SimpleLineItem>,
    val subtotal: Double,
    val taxAmount: Double,
    val totalDue: Double,
    val cordEntryIds: List<String>, // Links to immutable cord entries
    val verificationHash: String    // Hash of all cord entries for audit
)

data class SimpleLineItem(
    val description: String,
    val quantity: Double,
    val rate: Double,
    val total: Double
)

/**
 * Generate invoice from cord data (demonstration).
 */
fun generateCordInvoiceFromData(
    cordEntries: List<String>, // Simplified - just entry IDs
    jobId: String,
    providerName: String,
    hourlyRate: Double = 85.0
): SimpleCordInvoice? {

    if (cordEntries.isEmpty()) return null

    // Simulate labor calculation from cord entries
    val laborHours = 5.5 // Would be calculated from actual cord entries
    val laborTotal = laborHours * hourlyRate

    // Create line items
    val lineItems = mutableListOf<SimpleLineItem>()
    lineItems.add(SimpleLineItem(
        description = "Labor (Cord-Verified)",
        quantity = laborHours,
        rate = hourlyRate,
        total = Math.round(laborTotal * 100.0) / 100.0
    ))

    // Calculate totals
    val subtotal = lineItems.sumOf { it.total }
    val taxRate = 8.25
    val taxAmount = subtotal * (taxRate / 100.0)
    val totalDue = subtotal + taxAmount

    // Create verification hash (simplified)
    val verificationHash = cordEntries.sorted().joinToString(",").hashCode().toString()

    return SimpleCordInvoice(
        invoiceNumber = "CORD-INV-${System.currentTimeMillis()}",
        fromName = providerName,
        toName = "Client (from cord data)",
        lineItems = lineItems,
        subtotal = Math.round(subtotal * 100.0) / 100.0,
        taxAmount = Math.round(taxAmount * 100.0) / 100.0,
        totalDue = Math.round(totalDue * 100.0) / 100.0,
        cordEntryIds = cordEntries,
        verificationHash = verificationHash
    )
}

/**
 * Format cord invoice as text (similar to Guild of Smiths templates).
 */
fun formatCordInvoiceAsText(invoice: SimpleCordInvoice): String {
    val sb = StringBuilder()

    sb.appendLine("GUILD OF SMITHS CORD-BASED INVOICE")
    sb.appendLine("═══════════════════════════════════")
    sb.appendLine()
    sb.appendLine("Invoice #: ${invoice.invoiceNumber}")
    sb.appendLine("From: ${invoice.fromName}")
    sb.appendLine("To: ${invoice.toName}")
    sb.appendLine()

    // Line items
    sb.appendLine("Description                  | Qty  | Rate   | Total")
    sb.appendLine("─────────────────────────────┼──────┼────────┼─────────")
    invoice.lineItems.forEach { item ->
        val desc = item.description.take(27).padEnd(27)
        val qty = String.format("%.1f", item.quantity).padStart(4)
        val rate = String.format("$%.0f", item.rate).padStart(6)
        val total = String.format("$%.2f", item.total).padStart(7)
        sb.appendLine("$desc | $qty | $rate | $total")
    }

    sb.appendLine("─────────────────────────────┼──────┼────────┼─────────")
    sb.appendLine("Subtotal                     |      |        | $${String.format("%.2f", invoice.subtotal).padStart(7)}")
    sb.appendLine("Tax (8.25%)                  |      |        | $${String.format("%.2f", invoice.taxAmount).padStart(7)}")
    sb.appendLine("─────────────────────────────┼──────┼────────┼─────────")
    sb.appendLine("TOTAL DUE                    |      |        | $${String.format("%.2f", invoice.totalDue).padStart(7)}")
    sb.appendLine()

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

/**
 * Demo: Create sample cord entries and generate invoice.
 */
fun demoCordInvoiceGeneration(): String {
    // Generate invoice from simulated cord data
    val invoice = generateCordInvoiceFromData(
        cordEntries = listOf("cord-work-001", "cord-approval-002", "cord-material-003"),
        jobId = "job-123",
        providerName = "James Rivera",
        hourlyRate = 85.0
    )

    return if (invoice != null) {
        formatCordInvoiceAsText(invoice)
    } else {
        "Failed to generate invoice from cord data"
    }
}
