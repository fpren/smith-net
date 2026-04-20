package com.guildofsmiths.trademesh.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.SupabaseAuth
import com.guildofsmiths.trademesh.data.UserPreferences
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val signOutScope = rememberCoroutineScope()

    val currentUser by SupabaseAuth.currentUser.collectAsState()

    var displayName by remember { mutableStateOf(UserPreferences.getUserName()) }
    var selectedOccupation by remember { mutableStateOf(UserPreferences.getOccupation()) }
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

    val occupations = listOf(
        "Electrician" to Occupation.ELECTRICIAN,
        "HVAC" to Occupation.HVAC,
        "Plumber" to Occupation.PLUMBER,
        "Carpenter" to Occupation.CARPENTER,
        "General Labor" to Occupation.GENERAL_LABOR,
        "Other" to Occupation.OTHER
    )

    val experiences = listOf(
        "Apprentice" to ExperienceLevel.APPRENTICE,
        "Journeyman" to ExperienceLevel.JOURNEYMAN,
        "Master" to ExperienceLevel.MASTER,
        "Contractor" to ExperienceLevel.CONTRACTOR,
        "Not Applicable" to ExperienceLevel.NOT_APPLICABLE
    )

    val currentOccupation = try {
        selectedOccupation?.let { Occupation.valueOf(it) }
    } catch (e: Exception) { null }

    val currentExperience = try {
        selectedExperience?.let { ExperienceLevel.valueOf(it) }
    } catch (e: Exception) { null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
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

            ProfileDropdown(
                label = "Occupation",
                current = currentOccupation?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Select",
                options = occupations.map { it.first },
                onSelect = { index -> selectedOccupation = occupations[index].second.name }
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
                style = ConsoleTheme.action,
                modifier = Modifier
                    .clickable {
                        UserPreferences.setUserName(displayName)
                        selectedOccupation?.let { UserPreferences.saveOccupation(it) }
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
                    .background(ConsoleTheme.surface)
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "[SIGN OUT]",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                modifier = Modifier
                    .clickable {
                        signOutScope.launch {
                            SupabaseAuth.signOut()
                            UserPreferences.clearAllData()
                            onSignOut()
                        }
                    }
                    .background(ConsoleTheme.surface)
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = ConsoleTheme.captionBold)
}

@Composable
private fun ProfileField(label: String, value: String, readOnly: Boolean = false) {
    Column {
        Text(text = label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        Text(text = value, style = ConsoleTheme.body)
    }
}

@Composable
private fun ProfileEditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column {
        Text(text = label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, style = ConsoleTheme.caption) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ConsoleTheme.body,
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ConsoleTheme.surface,
                unfocusedContainerColor = ConsoleTheme.surface,
                focusedIndicatorColor = ConsoleTheme.accent,
                unfocusedIndicatorColor = ConsoleTheme.textDim
            )
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
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(text = label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = current,
                style = ConsoleTheme.body,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable { expanded = true }
                    .padding(12.dp)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option, style = ConsoleTheme.body) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
