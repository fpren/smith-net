package com.guildofsmiths.trademesh.ui.proposal

data class Proposal(
    val proposalNumber: String,
    val issuedDate: Long,
    val validUntil: Long,
    // Parties
    val providerName: String,
    val providerBusiness: String,
    val providerTrade: String,
    val providerPhone: String? = null,
    val providerEmail: String? = null,
    val clientName: String,
    val clientPhone: String?,
    val clientAddress: String?,
    // Scope
    val jobTitle: String,
    val scopeStatement: String,
    // Line items
    val laborLine: LaborLine,
    val materialLines: List<MaterialLine>,
    // Money
    val subtotal: Double,
    val taxRate: Double,
    val taxAmount: Double,
    val total: Double,
    val depositPercent: Int,
    val depositRequired: Double,
    val balanceOnCompletion: Double,
    // Commitment terms
    val timelineDays: Int,
    val startEstimate: String,
    val warrantyText: String,
    val exclusions: List<String>,
    val termsText: String,
    val status: ProposalStatus = ProposalStatus.DRAFT
)

data class LaborLine(
    val description: String,
    val estimatedHours: Double,
    val hourlyRate: Double,
    val total: Double
)

data class MaterialLine(
    val name: String,
    val quantity: Double,
    val unit: String,
    val unitCost: Double,
    val total: Double,
    val notes: String? = null
)

enum class ProposalStatus { DRAFT, SENT, ACCEPTED, DECLINED, EXPIRED }
