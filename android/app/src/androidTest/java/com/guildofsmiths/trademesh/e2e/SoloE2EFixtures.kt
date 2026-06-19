package com.guildofsmiths.trademesh.e2e

/**
 * Hardcoded, deterministic fixture data for the Solo Mode E2E
 * (Plan -> Job -> Tasks -> Invoice). Mirrors the test spec persona:
 * Sam Will Smith, Electrician, Solo; client Acme Motors.
 *
 * No live lookups: every value here is fixed so the invoice total is
 * reproducible. Expected total = materials $293.95 + labor $760.00 = $1,053.95
 * (no tax, no travel charge).
 */
object SoloE2EFixtures {

    // Persona / provider
    const val PROVIDER_NAME = "Sam Will Smith"
    const val PROVIDER_TRADE = "Electrician"
    const val HOURLY_RATE = 95.0
    const val LABOR_HOURS = 8.0
    const val LABOR_MINUTES = 480            // 8h, drives the LAB line via TimeEntry
    const val LABOR_TOTAL = 760.0            // 8 * 95

    // Client
    const val CLIENT_NAME = "Acme Motors"
    const val CLIENT_PHONE = "+1 718 555 0123"
    const val CLIENT_ADDRESS = "1123 East 57th Street, Brooklyn NY 11234, Apt 3"
    const val CLIENT_NOTE = "Client currently running XAMPP server on-premise"

    // Plan / job
    const val JOB_TITLE_BASE = "Acme Server Build"
    const val SCOPE =
        "Build out proper server infrastructure on top of Acme Motors' existing on-premise XAMPP setup. Solo electrician engagement."

    /** 6 ordered tasks — must carry over from plan -> job verbatim and in order. */
    val TASKS = listOf(
        "Site assessment and XAMPP server audit",
        "Procure all required equipment and materials",
        "Install UPS battery backup and rack PDU",
        "Run and label Cat6 ethernet from switch to server",
        "Configure network, test connectivity, verify XAMPP handoff",
        "Client walkthrough and documentation handoff",
    )

    /** A purchased material: name, unit price, vendor. quantity is always 1 ea. */
    data class MaterialFixture(val name: String, val price: Double, val source: String)

    /** 5 purchased items totaling $293.95. */
    val MATERIALS = listOf(
        MaterialFixture("APC UPS Battery Backup 1500VA", 189.99, "B&H"),
        MaterialFixture("Cat6 Ethernet Cable 50ft", 14.99, "Amazon"),
        MaterialFixture("8-Port Network Switch", 29.99, "Amazon"),
        MaterialFixture("Server Rack PDU", 49.99, "Newegg"),
        MaterialFixture("Brother TZe Label Maker Tape", 8.99, "Staples"),
    )

    const val MATERIALS_TOTAL = 293.95
    const val INVOICE_TOTAL = 1053.95
}
