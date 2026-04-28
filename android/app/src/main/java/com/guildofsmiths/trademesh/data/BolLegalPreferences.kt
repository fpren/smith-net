package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User preferences for the legal / disclaimer footer that appears at the
 * bottom of the Bill of Work & Expenses (BOL).
 *
 * Presets are organized into five groups:
 *   US_DOMESTIC           — UCC Art. 7, UCC Art. 2, IRS business records
 *   US_STATES             — state-specific mechanics-lien & home-improvement laws
 *   INTL_COMMERCIAL       — international sale of goods (CISG), UNIDROIT, Incoterms,
 *                           UNCITRAL model laws, key non-US country contract acts
 *   INTERNATIONAL_CARRIAGE — sea / road / air / rail / inland-water carrier conventions
 *   COMMERCIAL_LIEN       — affidavit / self-executing / notice-to-agent / UCC §1-308
 */
object BolLegalPreferences {

    private const val PREFS = "bol_legal_prefs"

    enum class Group {
        US_DOMESTIC,
        US_STATES,
        INTL_COMMERCIAL,
        INTERNATIONAL_CARRIAGE,
        COMMERCIAL_LIEN
    }

    enum class Preset(val group: Group) {
        // ── US DOMESTIC ─────────────────────────────────────────────
        US_UCC_POMERENE(Group.US_DOMESTIC),
        US_UCC_SALES(Group.US_DOMESTIC),
        US_IRS_BUSINESS_RECORDS(Group.US_DOMESTIC),

        // ── US STATES (mechanics-lien + home-improvement + statute of frauds) ──
        NY_LIEN_LAW(Group.US_STATES),
        NY_HOME_IMPROVEMENT(Group.US_STATES),
        NY_STATUTE_OF_FRAUDS(Group.US_STATES),
        CA_MECHANICS_LIEN(Group.US_STATES),
        CA_HOME_IMPROVEMENT(Group.US_STATES),
        TX_MECHANICS_LIEN(Group.US_STATES),
        TX_RESIDENTIAL_CONSTRUCTION(Group.US_STATES),
        FL_CONSTRUCTION_LIEN(Group.US_STATES),
        FL_HOME_SOLICITATION(Group.US_STATES),
        IL_MECHANICS_LIEN(Group.US_STATES),
        PA_MECHANICS_LIEN(Group.US_STATES),
        PA_HOME_IMPROVEMENT(Group.US_STATES),
        NJ_MECHANICS_LIEN(Group.US_STATES),
        NJ_HOME_IMPROVEMENT(Group.US_STATES),
        MA_MECHANICS_LIEN(Group.US_STATES),
        MA_HOME_IMPROVEMENT(Group.US_STATES),
        GA_MECHANICS_LIEN(Group.US_STATES),
        NC_LIEN_LAW(Group.US_STATES),
        OH_MECHANICS_LIEN(Group.US_STATES),
        WA_CONSTRUCTION_LIEN(Group.US_STATES),
        AZ_MECHANICS_LIEN(Group.US_STATES),
        VA_MECHANICS_LIEN(Group.US_STATES),
        CO_MECHANICS_LIEN(Group.US_STATES),
        MD_MECHANICS_LIEN(Group.US_STATES),

        // ── INTERNATIONAL COMMERCIAL (non-carriage) ────────────────
        INTL_CISG(Group.INTL_COMMERCIAL),
        INTL_UNIDROIT_PRINCIPLES(Group.INTL_COMMERCIAL),
        INTL_INCOTERMS_2020(Group.INTL_COMMERCIAL),
        INTL_UNCITRAL_ECOMMERCE(Group.INTL_COMMERCIAL),
        INTL_UNCITRAL_MLETR(Group.INTL_COMMERCIAL),
        INTL_ICC_URC_522(Group.INTL_COMMERCIAL),
        UK_SALE_OF_GOODS(Group.INTL_COMMERCIAL),
        UK_CONSUMER_RIGHTS(Group.INTL_COMMERCIAL),
        CA_SALE_OF_GOODS(Group.INTL_COMMERCIAL),
        EU_CONSUMER_RIGHTS(Group.INTL_COMMERCIAL),
        EU_LATE_PAYMENT(Group.INTL_COMMERCIAL),
        MX_FEDERAL_CONSUMER(Group.INTL_COMMERCIAL),

        // ── INTERNATIONAL CARRIAGE (sea / road / air / rail / inland) ──
        US_COGSA(Group.INTERNATIONAL_CARRIAGE),
        INTL_HAGUE_VISBY(Group.INTERNATIONAL_CARRIAGE),
        INTL_HAMBURG(Group.INTERNATIONAL_CARRIAGE),
        INTL_ROTTERDAM(Group.INTERNATIONAL_CARRIAGE),
        INTL_CMR(Group.INTERNATIONAL_CARRIAGE),
        INTL_MONTREAL_AIR(Group.INTERNATIONAL_CARRIAGE),
        INTL_WARSAW_AIR(Group.INTERNATIONAL_CARRIAGE),
        INTL_COTIF_CIM_RAIL(Group.INTERNATIONAL_CARRIAGE),
        INTL_CMNI_INLAND(Group.INTERNATIONAL_CARRIAGE),

        // ── COMMERCIAL-LIEN / AFFIDAVIT TRADITION ───────────────────
        AFFIDAVIT_ADMIRALTY_C6(Group.COMMERCIAL_LIEN),
        AFFIDAVIT_SELF_EXECUTING(Group.COMMERCIAL_LIEN),
        AFFIDAVIT_NOTICE_AGENT(Group.COMMERCIAL_LIEN),
        AFFIDAVIT_UCC_1_308(Group.COMMERCIAL_LIEN)
    }

    private val DEFAULT_ENABLED = setOf(
        Preset.US_UCC_POMERENE,
        Preset.US_UCC_SALES,
        Preset.US_IRS_BUSINESS_RECORDS,
        Preset.NY_LIEN_LAW
    )

    data class State(
        val enabled: Set<Preset> = DEFAULT_ENABLED,
        val customDisclaimer: String = "",
        val shipperLabel: String = "Shipper / Contractor",
        val carrierLabel: String = "Carrier / Witness",
        val consigneeLabel: String = "Consignee / Client",
        val includeSignatureBlock: Boolean = true,
        val includeNotarization: Boolean = false
    )

    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        load()
    }

    fun toggle(preset: Preset, enabled: Boolean) {
        val cur = _state.value
        val new = if (enabled) cur.enabled + preset else cur.enabled - preset
        update(cur.copy(enabled = new))
    }

    fun setCustomDisclaimer(text: String) = update(_state.value.copy(customDisclaimer = text))
    fun setIncludeSignatureBlock(on: Boolean) = update(_state.value.copy(includeSignatureBlock = on))
    fun setIncludeNotarization(on: Boolean) = update(_state.value.copy(includeNotarization = on))
    fun setLabels(shipper: String, carrier: String, consignee: String) =
        update(_state.value.copy(shipperLabel = shipper, carrierLabel = carrier, consigneeLabel = consignee))

    fun hasAnyInGroup(group: Group): Boolean = _state.value.enabled.any { it.group == group }

    private fun update(s: State) {
        _state.value = s
        prefs?.edit()?.apply {
            putStringSet("enabled", s.enabled.map { it.name }.toSet())
            putString("custom", s.customDisclaimer)
            putString("label_shipper", s.shipperLabel)
            putString("label_carrier", s.carrierLabel)
            putString("label_consignee", s.consigneeLabel)
            putBoolean("include_sig", s.includeSignatureBlock)
            putBoolean("include_notarization", s.includeNotarization)
        }?.apply()
    }

    private fun load() {
        val p = prefs ?: return
        val defaults = State()
        val stored = p.getStringSet("enabled", null)
        val enabled: Set<Preset> = if (stored == null) {
            defaults.enabled
        } else {
            val parsed = stored.mapNotNull { runCatching { Preset.valueOf(it) }.getOrNull() }.toSet()
            // Migration: promote the old single-preset legacy default to the new 4-preset default.
            if (parsed == setOf(Preset.US_UCC_POMERENE)) DEFAULT_ENABLED else parsed
        }
        _state.value = State(
            enabled = enabled,
            customDisclaimer = p.getString("custom", "") ?: "",
            shipperLabel = p.getString("label_shipper", defaults.shipperLabel) ?: defaults.shipperLabel,
            carrierLabel = p.getString("label_carrier", defaults.carrierLabel) ?: defaults.carrierLabel,
            consigneeLabel = p.getString("label_consignee", defaults.consigneeLabel) ?: defaults.consigneeLabel,
            includeSignatureBlock = p.getBoolean("include_sig", defaults.includeSignatureBlock),
            includeNotarization = p.getBoolean("include_notarization", defaults.includeNotarization)
        )
    }
}
