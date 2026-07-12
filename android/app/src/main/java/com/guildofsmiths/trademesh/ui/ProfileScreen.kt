package com.guildofsmiths.trademesh.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.guildofsmiths.trademesh.data.SupabaseAuth
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.components.TradePickerField
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithTextField
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val colors = LocalSmithColors.current
    val context = LocalContext.current
    val signOutScope = rememberCoroutineScope()

    val currentUser by SupabaseAuth.currentUser.collectAsState()

    var displayName by remember { mutableStateOf(UserPreferences.getUserName()) }
    var selectedOccupation by remember { mutableStateOf(UserPreferences.getOccupation()) }
    var selectedTrade by remember { mutableStateOf(UserPreferences.getPrimaryTrade()) }
    var selectedExperience by remember { mutableStateOf(UserPreferences.getExperienceLevel()) }
    var businessName by remember { mutableStateOf(UserPreferences.getBusinessName()) }
    var hourlyRate by remember { mutableStateOf(UserPreferences.getHourlyRate().let { if (it > 0) it.toString() else "" }) }
    var licenseNumber by remember { mutableStateOf(UserPreferences.getLicenseNumber()) }
    var paymentInfo by remember { mutableStateOf(UserPreferences.getPaymentInfo()) }
    var zelleHandle by remember { mutableStateOf(UserPreferences.getZelleHandle()) }
    var venmoHandle by remember { mutableStateOf(UserPreferences.getVenmoHandle()) }

    val initialAddress = UserPreferences.getAddress()
    var addressStreet by remember { mutableStateOf(initialAddress["street"] ?: "") }
    var addressCity by remember { mutableStateOf(initialAddress["city"] ?: "") }
    var addressState by remember { mutableStateOf(initialAddress["stateProvince"] ?: "") }
    var addressZip by remember { mutableStateOf(initialAddress["zipPostal"] ?: "") }
    var addressCountry by remember { mutableStateOf(initialAddress["country"]?.ifBlank { "US" } ?: "US") }

    val experiences = listOf(
        "Apprentice" to ExperienceLevel.APPRENTICE,
        "Journeyman" to ExperienceLevel.JOURNEYMAN,
        "Master" to ExperienceLevel.MASTER,
        "Contractor" to ExperienceLevel.CONTRACTOR,
        "Not Applicable" to ExperienceLevel.NOT_APPLICABLE
    )

    val currentExperience = try {
        selectedExperience?.let { ExperienceLevel.valueOf(it) }
    } catch (e: Exception) { null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
    ) {
        ConsoleHeader(title = "PROFILE", onBackClick = onNavigateBack)
        ConsoleSeparator()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── ACCOUNT ──
            SectionLabel("ACCOUNT")

            ProfileField("Email", currentUser?.email ?: "Not logged in", readOnly = true)

            ProfileEditField(
                label = "Display Name",
                value = displayName,
                onValueChange = { displayName = it },
                placeholder = "Your name"
            )

            ProfileEditField(
                label = "Business Name",
                value = businessName,
                onValueChange = { businessName = it },
                placeholder = "Optional"
            )

            ConsoleSeparator()

            // ── TRADE ──
            SectionLabel("TRADE")

            TradePickerField(
                selected = selectedTrade,
                onTradeSelected = { trade ->
                    selectedTrade = trade
                    // Backward-compat: also map the free-text trade to the coarse
                    // Occupation enum so downstream lookups (TradeDefaults, etc.)
                    // still resolve sensibly.
                    selectedOccupation = mapTradeToOccupation(trade).name
                }
            )

            ProfileDropdown(
                label = "Experience",
                current = currentExperience?.name?.lowercase()?.replaceFirstChar { it.uppercase() }?.replace("_", " ") ?: "Select",
                options = experiences.map { it.first },
                onSelect = { index -> selectedExperience = experiences[index].second.name }
            )

            ConsoleSeparator()

            // ── RATES & BILLING ──
            SectionLabel("RATES & BILLING")

            ProfileEditField(
                label = "Hourly Rate",
                value = hourlyRate,
                onValueChange = { hourlyRate = it },
                placeholder = "$/hr"
            )

            ProfileEditField(
                label = "License #",
                value = licenseNumber,
                onValueChange = { licenseNumber = it },
                placeholder = "Optional"
            )

            ProfileEditField(
                label = "Payment Info",
                value = paymentInfo,
                onValueChange = { paymentInfo = it },
                placeholder = "Notes for clients (check, bank transfer, etc.)"
            )

            ProfileEditField(
                label = "Zelle (email or phone)",
                value = zelleHandle,
                onValueChange = { zelleHandle = it },
                placeholder = "you@example.com"
            )

            ProfileEditField(
                label = "Venmo @handle",
                value = venmoHandle,
                onValueChange = { venmoHandle = it },
                placeholder = "your-venmo-username"
            )

            ConsoleSeparator()

            // ── ADDRESS ──
            SectionLabel("ADDRESS")
            ProfileEditField(
                label = "Street",
                value = addressStreet,
                onValueChange = { addressStreet = it },
                placeholder = "123 Main St"
            )
            ProfileEditField(
                label = "City",
                value = addressCity,
                onValueChange = { addressCity = it },
                placeholder = "Brooklyn"
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ProfileEditField(
                        label = "State",
                        value = addressState,
                        onValueChange = { addressState = it },
                        placeholder = "NY"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    ProfileEditField(
                        label = "ZIP",
                        value = addressZip,
                        onValueChange = { addressZip = it },
                        placeholder = "11220"
                    )
                }
            }
            ProfileEditField(
                label = "Country",
                value = addressCountry,
                onValueChange = { addressCountry = it },
                placeholder = "US"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── ACTIONS ──
            Text(
                text = "[SAVE PROFILE]",
                style = SmithType.action.copy(color = colors.accent),
                modifier = Modifier
                    .clickable {
                        UserPreferences.setUserName(displayName)
                        selectedOccupation?.let { UserPreferences.saveOccupation(it) }
                        if (selectedTrade.isNotBlank()) UserPreferences.setPrimaryTrade(selectedTrade)
                        selectedExperience?.let { UserPreferences.saveExperienceLevel(it) }
                        UserPreferences.saveBusinessName(businessName)
                        hourlyRate.toDoubleOrNull()?.let { UserPreferences.setHourlyRate(it) }
                        UserPreferences.setLicenseNumber(licenseNumber)
                        UserPreferences.setPaymentInfo(paymentInfo)
                        UserPreferences.setZelleHandle(zelleHandle)
                        UserPreferences.setVenmoHandle(venmoHandle)
                        UserPreferences.saveAddress(
                            addressStreet, addressCity, addressState, addressZip,
                            addressCountry.ifBlank { "US" }
                        )
                        Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                    }
                    .background(colors.bgPanel)
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "[SIGN OUT]",
                style = SmithType.action.copy(color = colors.statusError),
                modifier = Modifier
                    .clickable {
                        signOutScope.launch {
                            SupabaseAuth.signOut()
                            UserPreferences.clearAllData()
                            onSignOut()
                        }
                    }
                    .background(colors.bgPanel)
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalSmithColors.current
    Text(text = text, style = SmithType.captionBold.copy(color = colors.inkMuted))
}

@Composable
private fun ProfileField(label: String, value: String, readOnly: Boolean = false) {
    val colors = LocalSmithColors.current
    Column {
        Text(text = label, style = SmithType.caption.copy(color = colors.inkMuted))
        Text(text = value, style = SmithType.body.copy(color = colors.ink))
    }
}

@Composable
private fun ProfileEditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val colors = LocalSmithColors.current
    Column {
        Text(text = label, style = SmithType.caption.copy(color = colors.inkMuted))
        SmithTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProfileDropdown(
    label: String,
    current: String,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    val colors = LocalSmithColors.current
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(text = label, style = SmithType.caption.copy(color = colors.inkMuted))
        Box {
            Text(
                text = current,
                style = SmithType.body.copy(color = colors.ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .background(colors.bgSunken, RoundedCornerShape(Tokens2.RadiusControl))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            if (expanded) {
                Popup(onDismissRequest = { expanded = false }) {
                    Column(
                        modifier = Modifier
                            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusControl))
                            .border(1.dp, colors.line, RoundedCornerShape(Tokens2.RadiusControl))
                            .width(IntrinsicSize.Max),
                    ) {
                        options.forEachIndexed { index, option ->
                            Text(
                                text = option,
                                style = SmithType.body.copy(
                                    color = if (option == current) colors.accent else colors.ink,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(index); expanded = false }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Collapse a free-text trade name onto the coarse-grained Occupation enum so
 * downstream TradeDefaults/permission logic still has something to match on.
 * Mirrors the mapping used in OnboardingScreen.
 */
private fun mapTradeToOccupation(trade: String): Occupation = when {
    trade.contains("Electr", ignoreCase = true) -> Occupation.ELECTRICIAN
    trade.contains("HVAC", ignoreCase = true) || trade.contains("Heating", ignoreCase = true) -> Occupation.HVAC
    trade.contains("Plumb", ignoreCase = true) -> Occupation.PLUMBER
    trade.contains("Carpen", ignoreCase = true) || trade.contains("Framing", ignoreCase = true) -> Occupation.CARPENTER
    trade.contains("Labor", ignoreCase = true) -> Occupation.GENERAL_LABOR
    else -> Occupation.OTHER
}
