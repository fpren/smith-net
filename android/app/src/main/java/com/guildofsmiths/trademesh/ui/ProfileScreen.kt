package com.guildofsmiths.trademesh.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.data.SupabaseAuth
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.Occupation
import com.guildofsmiths.trademesh.ui.ExperienceLevel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val signOutScope = rememberCoroutineScope()

    // Get current user data
    val currentUser by SupabaseAuth.currentUser.collectAsState()
    val userPreferences = UserPreferences

    var displayName by remember { mutableStateOf(userPreferences.getUserName()) }
    var selectedOccupation by remember { mutableStateOf(userPreferences.getOccupation()) }
    var selectedExperience by remember { mutableStateOf(userPreferences.getExperienceLevel()) }
    var businessName by remember { mutableStateOf(userPreferences.getBusinessName()) }

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

    // Convert stored string back to enum
    val currentOccupation = try {
        selectedOccupation?.let { Occupation.valueOf(it) }
    } catch (e: Exception) {
        null
    }

    val currentExperience = try {
        selectedExperience?.let { ExperienceLevel.valueOf(it) }
    } catch (e: Exception) {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Profile",
                style = ConsoleTheme.title,
                fontSize = 24.sp
            )

            Text(
                "← Back",
                style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                modifier = Modifier.clickable { onNavigateBack() }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // User Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Account Information",
                    style = ConsoleTheme.body.copy(fontWeight = FontWeight.Bold),
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Email (read-only)
                Text("Email", style = ConsoleTheme.captionBold)
                Text(
                    currentUser?.email ?: "Not logged in",
                    style = ConsoleTheme.body,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Display Name
                Text("Display Name", style = ConsoleTheme.captionBold)
                BasicTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    textStyle = ConsoleTheme.body.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Work Information Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Work Information",
                    style = ConsoleTheme.body.copy(fontWeight = FontWeight.Bold),
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Occupation
                Text("Occupation", style = ConsoleTheme.captionBold)
                var occupationExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        currentOccupation?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
                            ?: "Select occupation",
                        style = ConsoleTheme.body.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { occupationExpanded = true }
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    )

                    DropdownMenu(
                        expanded = occupationExpanded,
                        onDismissRequest = { occupationExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        occupations.forEach { (label, occupation) ->
                            DropdownMenuItem(
                                text = { Text(label, style = ConsoleTheme.body.copy(color = MaterialTheme.colorScheme.onSurface)) },
                                onClick = {
                                    selectedOccupation = occupation.name
                                    occupationExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Experience Level
                Text("Experience Level", style = ConsoleTheme.captionBold)
                var experienceExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        currentExperience?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
                            ?.replace("_", " ") ?: "Select experience",
                        style = ConsoleTheme.body.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { experienceExpanded = true }
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    )

                    DropdownMenu(
                        expanded = experienceExpanded,
                        onDismissRequest = { experienceExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        experiences.forEach { (label, experience) ->
                            DropdownMenuItem(
                                text = { Text(label, style = ConsoleTheme.body.copy(color = MaterialTheme.colorScheme.onSurface)) },
                                onClick = {
                                    selectedExperience = experience.name
                                    experienceExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Business Name
                Text("Business Name (Optional)", style = ConsoleTheme.captionBold)
                BasicTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    textStyle = ConsoleTheme.body.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Address Information (read-only, link to settings)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Business Address",
                    style = ConsoleTheme.body.copy(fontWeight = FontWeight.Bold),
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                val address = UserPreferences.getAddress()
                Text(
                    "${address["street"]}\n${address["city"]}, ${address["stateProvince"]} ${address["zipPostal"]}\n${address["country"]}",
                    style = ConsoleTheme.body
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Edit in Settings →",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
                    modifier = Modifier.clickable { /* TODO: Navigate to settings */ }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Save Profile Button
            Text(
                "SAVE PROFILE",
                style = ConsoleTheme.action.copy(fontSize = 16.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // Save profile data
                        UserPreferences.setUserName(displayName)
                        selectedOccupation?.let { UserPreferences.saveOccupation(it) }
                        selectedExperience?.let { UserPreferences.saveExperienceLevel(it) }
                        if (businessName.isNotBlank()) {
                            UserPreferences.saveBusinessName(businessName)
                        }

                        Toast.makeText(context, "Profile saved!", Toast.LENGTH_SHORT).show()
                    }
                    .background(ConsoleTheme.accent, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Sign Out Button
            Text(
                "SIGN OUT",
                style = ConsoleTheme.body.copy(color = ConsoleTheme.error, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        signOutScope.launch {
                            SupabaseAuth.signOut()
                            UserPreferences.clearAllData()
                            onSignOut()
                        }
                    }
                    .background(ConsoleTheme.surface, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
