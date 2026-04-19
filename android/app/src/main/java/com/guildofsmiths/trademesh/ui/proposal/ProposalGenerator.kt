package com.guildofsmiths.trademesh.ui.proposal

import com.guildofsmiths.trademesh.ui.jobboard.Job
import kotlin.math.max
import kotlin.math.min

object ProposalGenerator {

    private var counter = 1

    fun generateFromJob(
        job: Job,
        providerName: String,
        providerBusiness: String,
        providerTrade: String,
        providerPhone: String? = null,
        providerEmail: String? = null,
        hourlyRate: Double,
        estimatedHours: Double,
        taxRate: Double = 8.25,
        depositPercent: Int = 30,
        validityDays: Int = 30,
        timelineDays: Int = estimateTimelineFromHours(estimatedHours)
    ): Proposal {
        val issued = System.currentTimeMillis()
        val validUntil = issued + validityDays.toLong() * 24L * 3600_000L

        val laborDescription = "${providerTrade.substringBefore("—").trim()} labor"
        val laborTotal = estimatedHours * hourlyRate
        val laborLine = LaborLine(
            description = laborDescription.ifEmpty { "Tradesperson labor" },
            estimatedHours = estimatedHours,
            hourlyRate = hourlyRate,
            total = laborTotal
        )

        val materialLines = job.materials.map { m ->
            val unitCost = m.unitCost
            val qty = m.quantity
            val lineTotal = if (m.totalCost > 0) m.totalCost else qty * unitCost
            MaterialLine(
                name = m.name,
                quantity = qty,
                unit = m.unit,
                unitCost = unitCost,
                total = lineTotal,
                notes = m.notes.takeIf { it.isNotBlank() }
            )
        }

        val subtotal = laborLine.total + materialLines.sumOf { it.total }
        val taxAmount = subtotal * (taxRate / 100.0)
        val total = subtotal + taxAmount
        val deposit = total * (depositPercent / 100.0)
        val balance = total - deposit

        val trade = providerTrade.trim()
        val tradeKey = occupationKey(trade)

        val proposalNumber = nextNumber()

        return Proposal(
            proposalNumber = proposalNumber,
            issuedDate = issued,
            validUntil = validUntil,
            providerName = providerName,
            providerBusiness = providerBusiness,
            providerTrade = trade,
            providerPhone = providerPhone,
            providerEmail = providerEmail,
            clientName = job.clientName ?: "Client",
            clientPhone = job.clientPhone.ifBlank { null },
            clientAddress = job.clientAddress.ifBlank { null },
            jobTitle = job.title,
            scopeStatement = job.description,
            laborLine = laborLine,
            materialLines = materialLines,
            subtotal = subtotal,
            taxRate = taxRate,
            taxAmount = taxAmount,
            total = total,
            depositPercent = depositPercent,
            depositRequired = deposit,
            balanceOnCompletion = balance,
            timelineDays = timelineDays,
            startEstimate = "Within 1 week of signed acceptance",
            warrantyText = WARRANTIES[tradeKey] ?: WARRANTIES["default"]!!,
            exclusions = EXCLUSIONS[tradeKey] ?: EXCLUSIONS["default"]!!,
            termsText = termsText(depositPercent, validityDays)
        )
    }

    private fun nextNumber(): String {
        val n = counter++
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return "PROP-$year-${n.toString().padStart(4, '0')}"
    }

    private fun estimateTimelineFromHours(hours: Double): Int {
        if (hours <= 0) return 1
        val workDay = 8.0
        return max(1, min(30, Math.ceil(hours / workDay).toInt()))
    }

    private fun occupationKey(trade: String): String {
        val t = trade.lowercase()
        return when {
            "electric" in t -> "electrician"
            "plumb" in t -> "plumber"
            "hvac" in t -> "hvac"
            "carpent" in t -> "carpenter"
            "contract" in t -> "general"
            else -> "default"
        }
    }

    private fun termsText(depositPercent: Int, validityDays: Int): String = """
        |Net on completion. $depositPercent% deposit due on acceptance of this proposal. Remaining balance due within 7 days of project completion.
        |Price valid $validityDays days from issue date.
        |Scope changes require a signed change order and may affect the total and timeline.
        |Access to the worksite, electrical service, and water (as applicable) to be provided by the client.
    """.trimMargin()

    private val WARRANTIES = mapOf(
        "electrician" to "All electrical workmanship guaranteed for 1 year from completion. Manufacturer warranty applies to installed breakers, fixtures, and equipment. Work performed to current NEC code.",
        "plumber"     to "All plumbing workmanship guaranteed for 1 year from completion. Manufacturer warranty applies to fixtures and water heaters. Leaks attributable to installation covered.",
        "hvac"        to "Installation workmanship guaranteed for 1 year from completion. Equipment covered by manufacturer warranty (typically 5–10 years on compressor, 1–5 years on parts).",
        "carpenter"   to "All carpentry workmanship guaranteed for 1 year from completion. Material defects covered by manufacturer warranty. Wood movement and seasonal expansion are excluded.",
        "general"     to "All workmanship guaranteed for 1 year from completion. Subcontractor warranties pass through. Manufacturer warranty applies to all installed equipment.",
        "default"     to "All workmanship guaranteed for 1 year from completion date. Manufacturer warranty applies to installed equipment."
    )

    private val EXCLUSIONS = mapOf(
        "electrician" to listOf(
            "Permit fees unless specifically included above",
            "Utility company charges (service upgrades, meter relocations)",
            "Repairs to existing wiring beyond stated scope",
            "Patching, painting, or finish restoration",
            "Hazardous material remediation (asbestos, lead paint)"
        ),
        "plumber" to listOf(
            "Permit fees unless specifically included above",
            "Opening walls beyond stated scope or finish restoration",
            "Replacement of main water service line unless specified",
            "Tree root removal or exterior excavation",
            "Hazardous material remediation"
        ),
        "hvac" to listOf(
            "Permit fees unless specifically included above",
            "Electrical work beyond unit connection",
            "Structural modifications (roof curbs, platforms)",
            "Duct modifications beyond stated scope",
            "Hazardous material remediation"
        ),
        "carpenter" to listOf(
            "Finish painting and staining unless specified",
            "Electrical, plumbing, or HVAC modifications",
            "Permit fees unless specifically included",
            "Disposal of hazardous materials",
            "Restoration of adjacent finishes"
        ),
        "general" to listOf(
            "Permit fees unless specifically included above",
            "Work outside the defined scope",
            "Hazardous material remediation",
            "Repairs uncovered during demolition"
        ),
        "default" to listOf(
            "Permit fees unless specifically included above",
            "Repairs beyond stated scope",
            "Patching, painting, or finish restoration",
            "Hazardous material remediation"
        )
    )
}
