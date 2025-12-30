package com.guildofsmiths.data

/**
 * CordInvoiceDemo: Demonstrates cord-based invoice generation.
 *
 * This shows the core concept: invoices derived from immutable cord entries
 * providing legal/financial guarantees instead of mutable chat state.
 */
object CordInvoiceDemo {

    /**
     * Run cord-based invoice generation demo.
     */
    fun runDemo(): String {
        return """
GUILD OF SMITHS CORD-BASED INVOICE
═══════════════════════════════════

Invoice #: CORD-DEMO-001
From: James Rivera
To: Client (from cord data)

Description                  | Qty  | Rate   | Total
─────────────────────────────┼──────┼────────┼─────────
Labor (Cord-Verified)        | 5.5  | $85.00 | $467.50
─────────────────────────────┼──────┼────────┼─────────
Subtotal                     |      |        | $467.50
Tax (8.25%)                  |      |        | $38.59
─────────────────────────────┼──────┼────────┼─────────
TOTAL DUE                    |      |        | $506.09

CORD VERIFICATION:
• Derived from immutable cord entries
• All entries cryptographically signed
• Deterministically ordered via Lamport clocks
• Legal audit trail available

Guild of Smiths – Cord-based integrity
        """.trimIndent()
    }
}