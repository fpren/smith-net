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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.BolLegalPreferences
import com.guildofsmiths.trademesh.data.BolLegalPreferences.Group
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleTheme

@Composable
fun BolLegalSettingsScreen(onBack: () -> Unit) {
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

    Column(modifier = Modifier.fillMaxSize().background(ConsoleTheme.background)) {
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
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
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
                    Text("Filter states:", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = stateFilter,
                        onValueChange = { stateFilter = it },
                        singleLine = true,
                        textStyle = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text),
                        cursorBrush = SolidColor(ConsoleTheme.cursor),
                        modifier = Modifier
                            .weight(1f)
                            .background(ConsoleTheme.background, RoundedCornerShape(2.dp))
                            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
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
                    textStyle = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text),
                    cursorBrush = SolidColor(ConsoleTheme.cursor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .background(ConsoleTheme.background, RoundedCornerShape(4.dp))
                        .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                )
                Text(
                    "Appended under [Additional terms] — use for trade-specific warranties, limitations, or payment terms.",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
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
                    Text("Include signature lines", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text))
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
                        Text("Include notarization block", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text))
                        Text(
                            "Adds 'Sworn to (or affirmed) before me …' after the three signature stanzas.",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                    }
                }

                LabeledLine("Shipper label", shipper) { shipper = it }
                LabeledLine("Carrier label", carrier) { carrier = it }
                LabeledLine("Consignee label", consignee) { consignee = it }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.accent.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                        .clickable {
                            BolLegalPreferences.setLabels(shipper, carrier, consignee)
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("[save labels]", style = ConsoleTheme.action.copy(color = ConsoleTheme.accent))
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
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (expanded) "▾" else "▸", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.text))
                Text(subtitle, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
            }
            Text(
                "$enabledCount / ${items.size}",
                style = ConsoleTheme.caption.copy(color = if (enabledCount > 0) ConsoleTheme.accent else ConsoleTheme.textMuted)
            )
        }
        if (expanded) {
            if (warning != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.warning.copy(alpha = 0.10f))
                        .border(0.5.dp, ConsoleTheme.warning.copy(alpha = 0.3f))
                        .padding(10.dp)
                ) {
                    Text(warning, style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning))
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
                            Text(info.shortLabel, style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.text))
                            Text(info.jurisdiction, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                            if (on) {
                                Spacer(Modifier.height(4.dp))
                                Text(info.body, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun Checkbox(on: Boolean) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(if (on) ConsoleTheme.accent else ConsoleTheme.background, RoundedCornerShape(2.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (on) Text("✓", style = ConsoleTheme.caption.copy(color = Color.White))
    }
}

@Composable
private fun LabeledLine(label: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text),
            cursorBrush = SolidColor(ConsoleTheme.cursor),
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.background, RoundedCornerShape(2.dp))
                .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                .padding(8.dp)
        )
    }
}
