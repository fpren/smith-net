package com.guildofsmiths.trademesh.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.BolLegalPreferences
import com.guildofsmiths.trademesh.data.BolLegalPreferences.Group
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithCard
import com.guildofsmiths.trademesh.ui.theme2.SmithType

@Composable
fun BolLegalSettingsScreen(onBack: () -> Unit) {
    val colors = LocalSmithColors.current
    val state by BolLegalPreferences.state.collectAsState()
    var custom by remember(state.customDisclaimer) { mutableStateOf(state.customDisclaimer) }
    var shipper by remember(state.shipperLabel) { mutableStateOf(state.shipperLabel) }
    var carrier by remember(state.carrierLabel) { mutableStateOf(state.carrierLabel) }
    var consignee by remember(state.consigneeLabel) { mutableStateOf(state.consigneeLabel) }

    // Group expansion state — advanced groups collapsed by default.
    var expUsDomestic by remember { mutableStateOf(true) }
    var expUsStates by remember { mutableStateOf(true) }
    var expIntlCommercial by remember { mutableStateOf(false) }
    var expIntlCarriage by remember { mutableStateOf(false) }
    var expCommercialLien by remember { mutableStateOf(false) }

    // Search filter for US States (handy when the list grows)
    var stateFilter by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(colors.bgBase)) {
        ConsoleHeader(title = "BOL LEGAL TERMS", onBackClick = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Toggle which legal framework(s) appear at the bottom of every BOL. The text is a starting template with statute, regulation, UN/UNCITRAL convention, and procedural-rule citations — not legal advice.",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )

            GroupSection(
                title = "US Domestic",
                subtitle = "UCC Art. 7 / Art. 2, IRS §§ 162 / 6001 / 274(d)",
                expanded = expUsDomestic,
                onToggle = { expUsDomestic = !expUsDomestic },
                warning = null,
                state = state,
                group = Group.US_DOMESTIC,
                filter = ""
            )
            // Filter strip for US States
            if (expUsStates) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filter states:", style = SmithType.caption.copy(color = colors.inkMuted))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = stateFilter,
                        onValueChange = { stateFilter = it },
                        singleLine = true,
                        textStyle = SmithType.bodySmall.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.ink),
                        modifier = Modifier
                            .weight(1f)
                            .background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusTiny))
                            .border(0.5.dp, colors.ink.copy(alpha = 0.1f), RoundedCornerShape(Tokens2.RadiusTiny))
                            .padding(6.dp)
                    )
                }
            }
            GroupSection(
                title = "US States",
                subtitle = "Mechanics-lien, home-improvement, residential-construction acts — 14 states",
                expanded = expUsStates,
                onToggle = { expUsStates = !expUsStates },
                warning = null,
                state = state,
                group = Group.US_STATES,
                filter = stateFilter
            )
            GroupSection(
                title = "International commercial (advanced)",
                subtitle = "UN · CISG · UNIDROIT · Incoterms 2020 · UNCITRAL e-commerce · MLETR · UK · EU · Canada · Mexico",
                expanded = expIntlCommercial,
                onToggle = { expIntlCommercial = !expIntlCommercial },
                warning = "International sale-of-goods frameworks. Enable if you sell or deliver to counterparties outside the US. CISG applies automatically between business parties in signatory states unless expressly excluded.",
                state = state,
                group = Group.INTL_COMMERCIAL,
                filter = ""
            )
            GroupSection(
                title = "International carriage (advanced)",
                subtitle = "Sea: Hague-Visby · COGSA · Hamburg (UN) · Rotterdam (UN) · Air: Montreal · Warsaw · Road: CMR · Rail: COTIF/CIM · Inland: CMNI",
                expanded = expIntlCarriage,
                onToggle = { expIntlCarriage = !expIntlCarriage },
                warning = "Carrier-liability conventions. Only enable if your work actually involves cross-border transport by sea, road, air, rail, or inland waterway — otherwise they may be misleading on the document.",
                state = state,
                group = Group.INTERNATIONAL_CARRIAGE,
                filter = ""
            )
            GroupSection(
                title = "Commercial-lien / affidavit tradition (advanced)",
                subtitle = "Admiralty Rule C(6) · self-executing contract · notice-to-agent · UCC §1-308",
                expanded = expCommercialLien,
                onToggle = { expCommercialLien = !expCommercialLien },
                warning = "These clauses follow the commercial-lien / affidavit tradition. They are assertive and are contested in many mainstream courts. Use only if you know what they do and have recorded your own fee-schedule affidavit as a reference document.",
                state = state,
                group = Group.COMMERCIAL_LIEN,
                filter = ""
            )

            Section("Custom disclaimer (optional)") {
                BasicTextField(
                    value = custom,
                    onValueChange = {
                        custom = it
                        BolLegalPreferences.setCustomDisclaimer(it)
                    },
                    textStyle = SmithType.bodySmall.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.ink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusCard))
                        .border(0.5.dp, colors.ink.copy(alpha = 0.1f), RoundedCornerShape(Tokens2.RadiusCard))
                        .padding(8.dp)
                )
                Text(
                    "Appended under [Additional terms] — use for trade-specific warranties, limitations, or payment terms.",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }

            Section("Signature block") {
                val sigOn = state.includeSignatureBlock
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { BolLegalPreferences.setIncludeSignatureBlock(!sigOn) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(sigOn)
                    Spacer(Modifier.width(8.dp))
                    Text("Include signature lines", style = SmithType.bodySmall.copy(color = colors.ink))
                }

                val notOn = state.includeNotarization
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { BolLegalPreferences.setIncludeNotarization(!notOn) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(notOn)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Include notarization block", style = SmithType.bodySmall.copy(color = colors.ink))
                        Text(
                            "Adds 'Sworn to (or affirmed) before me …' after the three signature stanzas.",
                            style = SmithType.caption.copy(color = colors.inkMuted)
                        )
                    }
                }

                LabeledLine("Shipper label", shipper) { shipper = it }
                LabeledLine("Carrier label", carrier) { carrier = it }
                LabeledLine("Consignee label", consignee) { consignee = it }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.accent.copy(alpha = 0.14f), RoundedCornerShape(Tokens2.RadiusCard))
                        .clickable {
                            BolLegalPreferences.setLabels(shipper, carrier, consignee)
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("[save labels]", style = SmithType.action.copy(color = colors.accent))
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun GroupSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    warning: String?,
    state: BolLegalPreferences.State,
    group: Group,
    filter: String
) {
    val colors = LocalSmithColors.current
    val allItems = BolLegalTerms.inGroup(group)
    val items = if (filter.isBlank()) allItems else {
        val q = filter.trim().lowercase()
        allItems.filter {
            it.shortLabel.lowercase().contains(q) ||
                it.jurisdiction.lowercase().contains(q) ||
                it.preset.name.lowercase().contains(q)
        }
    }
    val enabledCount = allItems.count { it.preset in state.enabled }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
            .border(0.5.dp, colors.ink.copy(alpha = 0.08f), RoundedCornerShape(Tokens2.RadiusCard))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (expanded) "▾" else "▸", style = SmithType.caption.copy(color = colors.inkMuted))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = SmithType.captionBold.copy(color = colors.ink))
                Text(subtitle, style = SmithType.caption.copy(color = colors.inkMuted))
            }
            Text(
                "$enabledCount / ${items.size}",
                style = SmithType.caption.copy(color = if (enabledCount > 0) colors.accent else colors.inkMuted)
            )
        }
        if (expanded) {
            if (warning != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.attention.copy(alpha = 0.10f))
                        .border(0.5.dp, colors.attention.copy(alpha = 0.3f))
                        .padding(10.dp)
                ) {
                    Text(warning, style = SmithType.caption.copy(color = colors.attention))
                }
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                items.forEach { info ->
                    val on = info.preset in state.enabled
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { BolLegalPreferences.toggle(info.preset, !on) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(on)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(info.shortLabel, style = SmithType.captionBold.copy(color = colors.ink))
                            Text(info.jurisdiction, style = SmithType.caption.copy(color = colors.inkMuted))
                            if (on) {
                                Spacer(Modifier.height(4.dp))
                                Text(info.body, style = SmithType.caption.copy(color = colors.inkMuted))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalSmithColors.current
    SmithCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = SmithType.captionBold.copy(color = colors.inkMuted))
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun Checkbox(on: Boolean) {
    val colors = LocalSmithColors.current
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(if (on) colors.accent else colors.bgBase, RoundedCornerShape(Tokens2.RadiusTiny))
            .border(0.5.dp, colors.ink.copy(alpha = 0.3f), RoundedCornerShape(Tokens2.RadiusTiny)),
        contentAlignment = Alignment.Center
    ) {
        if (on) Text("✓", style = SmithType.caption.copy(color = colors.inkOnAccent))
    }
}

@Composable
private fun LabeledLine(label: String, value: String, onChange: (String) -> Unit) {
    val colors = LocalSmithColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = SmithType.caption.copy(color = colors.inkMuted))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = SmithType.bodySmall.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.ink),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusTiny))
                .border(0.5.dp, colors.ink.copy(alpha = 0.1f), RoundedCornerShape(Tokens2.RadiusTiny))
                .padding(8.dp)
        )
    }
}
