package com.guildofsmiths.trademesh.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            .background(ConsoleTheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Header - match Settings style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .clickable(onClick = onNavigateBack)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "←", style = ConsoleTheme.title)
            Spacer(modifier = Modifier.width(14.dp))
            Text(text = "PROFILE", style = ConsoleTheme.title)
        }
        
        ConsoleSeparator()

        Column(modifier = Modifier.padding(16.dp)) {
            // ════════════════════════════════════════════════════════════════
            // USER INFO
            // ════════════════════════════════════════════════════════════════
            Text(text = "ACCOUNT", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(8.dp))

            // Email (read-only)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "EMAIL:", style = ConsoleTheme.caption)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentUser?.email ?: "Not logged in",
                    style = ConsoleTheme.body
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Display Name
            BasicTextField(
                value = displayName,
                onValueChange = { displayName = it },
                textStyle = ConsoleTheme.body,
                cursorBrush = SolidColor(ConsoleTheme.cursor),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(12.dp),
                decorationBox = { innerTextField ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "NAME:", style = ConsoleTheme.caption)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (displayName.isEmpty()) {
                                Text(
                                    text = "enter name",
                                    style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))

            // ════════════════════════════════════════════════════════════════
            // WORK INFO
            // ════════════════════════════════════════════════════════════════
            Text(text = "WORK INFORMATION", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(8.dp))

            // Occupation
            var occupationExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable { occupationExpanded = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "OCCUPATION:", style = ConsoleTheme.caption)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentOccupation?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
                        ?: "Select",
                    style = ConsoleTheme.body,
                    modifier = Modifier.weight(1f)
                )
                Text(text = ">", style = ConsoleTheme.body)
                
                DropdownMenu(
                    expanded = occupationExpanded,
                    onDismissRequest = { occupationExpanded = false }
                ) {
                    occupations.forEach { (label, occupation) ->
                        DropdownMenuItem(
                            text = { Text(label, style = ConsoleTheme.body) },
                            onClick = {
                                selectedOccupation = occupation.name
                                occupationExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Experience Level
            var experienceExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable { experienceExpanded = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "EXPERIENCE:", style = ConsoleTheme.caption)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentExperience?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
                        ?.replace("_", " ") ?: "Select",
                    style = ConsoleTheme.body,
                    modifier = Modifier.weight(1f)
                )
                Text(text = ">", style = ConsoleTheme.body)
                
                DropdownMenu(
                    expanded = experienceExpanded,
                    onDismissRequest = { experienceExpanded = false }
                ) {
                    experiences.forEach { (label, experience) ->
                        DropdownMenuItem(
                            text = { Text(label, style = ConsoleTheme.body) },
                            onClick = {
                                selectedExperience = experience.name
                                experienceExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Business Name
            BasicTextField(
                value = businessName,
                onValueChange = { businessName = it },
                textStyle = ConsoleTheme.body,
                cursorBrush = SolidColor(ConsoleTheme.cursor),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(12.dp),
                decorationBox = { innerTextField ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "BUSINESS:", style = ConsoleTheme.caption)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (businessName.isEmpty()) {
                                Text(
                                    text = "optional",
                                    style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))

            // ════════════════════════════════════════════════════════════════
            // ADDRESS
            // ════════════════════════════════════════════════════════════════
            Text(text = "BUSINESS ADDRESS", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(8.dp))

            val address = UserPreferences.getAddress()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(12.dp)
            ) {
                Text(
                    text = "${address["street"]}\n${address["city"]}, ${address["stateProvince"]} ${address["zipPostal"]}\n${address["country"]}",
                    style = ConsoleTheme.body
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))

            // ════════════════════════════════════════════════════════════════
            // ACTIONS
            // ════════════════════════════════════════════════════════════════
            Text(text = "ACTIONS", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(8.dp))

            // Save Profile Button - matches Settings style with save icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable {
                        UserPreferences.setUserName(displayName)
                        selectedOccupation?.let { UserPreferences.saveOccupation(it) }
                        selectedExperience?.let { UserPreferences.saveExperienceLevel(it) }
                        if (businessName.isNotBlank()) {
                            UserPreferences.saveBusinessName(businessName)
                        }
                        Toast.makeText(context, "Profile saved!", Toast.LENGTH_SHORT).show()
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "[⬆]", style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.success))
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "SAVE PROFILE", style = ConsoleTheme.body, modifier = Modifier.weight(1f))
                Text(text = ">", style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Sign Out Button - matches Settings [↪] SIGN OUT style
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable {
                        signOutScope.launch {
                            SupabaseAuth.signOut()
                            UserPreferences.clearAllData()
                            onSignOut()
                        }
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "[↪]", style = ConsoleTheme.bodyBold)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "SIGN OUT", style = ConsoleTheme.body, modifier = Modifier.weight(1f))
                Text(text = ">", style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
