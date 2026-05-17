package com.guildofsmiths.trademesh.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.guildofsmiths.trademesh.data.RoleContext
import com.guildofsmiths.trademesh.data.TradeDefaults
import com.guildofsmiths.trademesh.data.TradesList
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.data.UserRole
import com.guildofsmiths.trademesh.data.WageService
import com.guildofsmiths.trademesh.data.WageSuggestion
import com.guildofsmiths.trademesh.service.AuthService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Smith Net — Post-Registration Guided Setup
 * 3-Screen: Trade, About You, Done
 */

enum class OnboardingScreen {
    TRADE,       // Your Trade + Experience
    ABOUT_YOU,   // Name, Business, Address, Rate
    CREW_CHECK,  // Solo vs Foreman detection
    DONE         // Welcome, go to dashboard
}

enum class Occupation {
    ELECTRICIAN,
    HVAC,
    PLUMBER,
    CARPENTER,
    GENERAL_LABOR,
    OTHER
}

enum class ExperienceLevel {
    APPRENTICE,
    JOURNEYMAN,
    MASTER,
    CONTRACTOR,
    NOT_APPLICABLE
}

// Keep Language enum for backward compatibility with other code that may reference it
enum class Language {
    ENGLISH,
    SPANISH,
    FRENCH,
    GERMAN,
    ITALIAN,
    PORTUGUESE,
    CHINESE,
    JAPANESE,
    KOREAN,
    ARABIC,
    HINDI,
    RUSSIAN
}

data class OnboardingData(
    var name: String = "",
    var street: String = "",
    var city: String = "",
    var stateProvince: String = "",
    var zipPostal: String = "",
    var occupation: Occupation? = null,
    var experienceLevel: ExperienceLevel? = null,
    var businessName: String = "",
    var hourlyRate: String = "",
    var licenseNumber: String = ""
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    userPreferences: UserPreferences = com.guildofsmiths.trademesh.data.UserPreferences
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(OnboardingScreen.TRADE) }
    var onboardingData by remember {
        mutableStateOf(
            OnboardingData(name = userPreferences.getUserName())
        )
    }

    val transitionSpec = remember {
        slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(280, easing = EaseInOutCubic)
        ) + fadeIn(animationSpec = tween(280)) + scaleIn(
            initialScale = 0.98f,
            animationSpec = tween(280, easing = EaseInOutCubic)
        ) togetherWith slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(220, easing = EaseInOutCubic)
        ) + fadeOut(animationSpec = tween(220)) + scaleOut(
            targetScale = 0.98f,
            animationSpec = tween(220, easing = EaseInOutCubic)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { transitionSpec },
            modifier = Modifier.fillMaxSize(),
            label = "onboarding-transition"
        ) { screen ->
            when (screen) {
                OnboardingScreen.TRADE -> TradeScreen(
                    data = onboardingData,
                    onDataChange = { onboardingData = it },
                    onContinue = {
                        scope.launch {
                            delay(160)
                            currentScreen = OnboardingScreen.ABOUT_YOU
                        }
                    }
                )
                OnboardingScreen.ABOUT_YOU -> AboutYouScreen(
                    data = onboardingData,
                    onDataChange = { onboardingData = it },
                    onContinue = {
                        saveOnboardingData(context, onboardingData, userPreferences)
                        scope.launch {
                            delay(160)
                            currentScreen = OnboardingScreen.CREW_CHECK
                        }
                    }
                )
                OnboardingScreen.CREW_CHECK -> CrewCheckContent(
                    onSolo = {
                        AuthService.updateUserRole("solo")
                        scope.launch {
                            AuthService.syncWorkMode("solo")
                            delay(160)
                            currentScreen = OnboardingScreen.DONE
                        }
                    },
                    onForeman = {
                        AuthService.updateUserRole("foreman")
                        scope.launch {
                            AuthService.syncWorkMode("foreman")
                            delay(160)
                            currentScreen = OnboardingScreen.DONE
                        }
                    }
                )
                OnboardingScreen.DONE -> DoneScreen(
                    onComplete = onComplete
                )
            }
        }

        // Page dots
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PageDots(currentScreen = currentScreen)
        }
    }
}

// ─── Screen 1: TRADE ─────────────────────────────────────────────────────────

@Composable
private fun TradeScreen(
    data: OnboardingData,
    onDataChange: (OnboardingData) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        androidx.compose.material3.Text(
            "Your Trade",
            style = ConsoleTheme.title,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.material3.Text(
            "Tell us about your work so we can set up the right defaults.",
            style = ConsoleTheme.body,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Trade picker — searchable 120+ trades
        ConsoleLabel("YOUR TRADE")
        Spacer(modifier = Modifier.height(8.dp))
        var tradeSearch by remember { mutableStateOf("") }
        var tradeExpanded by remember { mutableStateOf(false) }
        var selectedTrade by remember { mutableStateOf(UserPreferences.getPrimaryTrade()) }
        val filteredTrades = remember(tradeSearch) {
            if (tradeSearch.isBlank()) TradesList.ALL_TRADES.take(20)
            else TradesList.search(tradeSearch).take(15)
        }

        // Selected trade display
        if (selectedTrade.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.accent.copy(alpha = 0.08f))
                    .clickable { tradeExpanded = true }
                    .padding(16.dp)
            ) {
                Text(selectedTrade, style = ConsoleTheme.body.copy(color = ConsoleTheme.accent))
            }
        }

        // Search field
        BasicTextField(
            value = tradeSearch,
            onValueChange = { tradeSearch = it; tradeExpanded = true },
            textStyle = ConsoleTheme.body,
            cursorBrush = SolidColor(ConsoleTheme.cursor),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(16.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (tradeSearch.isEmpty()) {
                        Text(
                            "Search trades (${TradesList.ALL_TRADES.size}+ available)",
                            style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                        )
                    }
                    innerTextField()
                }
            }
        )

        // Dropdown results
        if (tradeExpanded && filteredTrades.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                filteredTrades.forEach { trade ->
                    Text(
                        text = trade,
                        style = ConsoleTheme.bodySmall.copy(
                            color = if (trade == selectedTrade) ConsoleTheme.accent else ConsoleTheme.text
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTrade = trade
                                tradeSearch = ""
                                tradeExpanded = false
                                // Map to Occupation for backward compat
                                val occ = when {
                                    trade.contains("Electr", ignoreCase = true) -> Occupation.ELECTRICIAN
                                    trade.contains("HVAC", ignoreCase = true) || trade.contains("Heating", ignoreCase = true) -> Occupation.HVAC
                                    trade.contains("Plumb", ignoreCase = true) -> Occupation.PLUMBER
                                    trade.contains("Carpen", ignoreCase = true) || trade.contains("Framing", ignoreCase = true) -> Occupation.CARPENTER
                                    else -> Occupation.OTHER
                                }
                                onDataChange(data.copy(occupation = occ))
                                UserPreferences.setPrimaryTrade(trade)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    Box(Modifier.fillMaxWidth().height(0.5.dp).padding(horizontal = 16.dp).background(ConsoleTheme.text.copy(alpha = 0.04f)))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Experience level dropdown
        ConsoleLabel("EXPERIENCE LEVEL")
        Spacer(modifier = Modifier.height(8.dp))
        var experienceExpanded by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { experienceExpanded = true }
                .background(ConsoleTheme.surface)
                .padding(16.dp)
        ) {
            androidx.compose.material3.Text(
                text = data.experienceLevel?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
                    ?.replace("_", " ") ?: "Select experience level",
                style = if (data.experienceLevel != null) ConsoleTheme.body
                        else ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
            )
            DropdownMenu(
                expanded = experienceExpanded,
                onDismissRequest = { experienceExpanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(ConsoleTheme.background)
            ) {
                ExperienceLevel.values().forEach { level ->
                    DropdownMenuItem(
                        text = {
                            androidx.compose.material3.Text(
                                level.name.lowercase().replaceFirstChar { it.uppercase() }
                                    .replace("_", " "),
                                style = ConsoleTheme.body,
                                color = ConsoleTheme.text
                            )
                        },
                        onClick = {
                            onDataChange(data.copy(experienceLevel = level))
                            experienceExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        androidx.compose.material3.Text(
            text = "Fields are optional — you can update these in Settings.",
            style = ConsoleTheme.caption,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        ConsoleButton(label = "NEXT →", onClick = onContinue)

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// ─── Screen 2: ABOUT YOU ─────────────────────────────────────────────────────

@Composable
private fun AboutYouScreen(
    data: OnboardingData,
    onDataChange: (OnboardingData) -> Unit,
    onContinue: () -> Unit
) {
    var wageSuggestion by remember { mutableStateOf<WageSuggestion?>(null) }

    LaunchedEffect(data.zipPostal) {
        if (data.zipPostal.length == 5) {
            val socCode = TradeDefaults.getSocCode(UserPreferences.getOccupation()) ?: "47-2111"
            val suggestion = WageService.getWageSuggestion(data.zipPostal, socCode)
            wageSuggestion = suggestion
            if (suggestion != null && data.hourlyRate.isBlank()) {
                onDataChange(data.copy(hourlyRate = suggestion.medianRate.toInt().toString()))
            }
        } else {
            wageSuggestion = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        androidx.compose.material3.Text(
            "About You",
            style = ConsoleTheme.title,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.material3.Text(
            "This info appears on invoices and job records.",
            style = ConsoleTheme.body,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Name
        ConsoleLabel("YOUR NAME")
        Spacer(modifier = Modifier.height(8.dp))
        ConsoleTextField(
            value = data.name,
            onValueChange = { onDataChange(data.copy(name = it)) },
            placeholder = "Enter your name",
            imeAction = ImeAction.Next
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Business name
        ConsoleLabel("BUSINESS NAME (optional)")
        Spacer(modifier = Modifier.height(8.dp))
        ConsoleTextField(
            value = data.businessName,
            onValueChange = { onDataChange(data.copy(businessName = it)) },
            placeholder = "Enter business name",
            imeAction = ImeAction.Next
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Street
        ConsoleLabel("STREET")
        Spacer(modifier = Modifier.height(8.dp))
        ConsoleTextField(
            value = data.street,
            onValueChange = { onDataChange(data.copy(street = it)) },
            placeholder = "Enter street address",
            imeAction = ImeAction.Next
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ConsoleLabel("CITY")
                Spacer(modifier = Modifier.height(8.dp))
                ConsoleTextField(
                    value = data.city,
                    onValueChange = { onDataChange(data.copy(city = it)) },
                    placeholder = "City",
                    imeAction = ImeAction.Next
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                ConsoleLabel("STATE")
                Spacer(modifier = Modifier.height(8.dp))
                ConsoleTextField(
                    value = data.stateProvince,
                    onValueChange = { onDataChange(data.copy(stateProvince = it)) },
                    placeholder = "State",
                    imeAction = ImeAction.Next
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ConsoleLabel("ZIP CODE")
        Spacer(modifier = Modifier.height(8.dp))
        ConsoleTextField(
            value = data.zipPostal,
            onValueChange = { onDataChange(data.copy(zipPostal = it)) },
            placeholder = "ZIP / Postal code",
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Hourly rate
        ConsoleLabel("HOURLY RATE")
        Spacer(modifier = Modifier.height(8.dp))
        ConsoleTextField(
            value = data.hourlyRate,
            onValueChange = { onDataChange(data.copy(hourlyRate = it)) },
            placeholder = "What do you charge per hour?",
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next
        )
        wageSuggestion?.let { suggestion ->
            Spacer(modifier = Modifier.height(6.dp))
            androidx.compose.material3.Text(
                text = "${suggestion.metroName.split(",").first().trim()} area: typically \$${suggestion.lowRate.toInt()}–\$${suggestion.highRate.toInt()}/hr",
                style = ConsoleTheme.caption,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // License number
        ConsoleLabel("LICENSE NUMBER (optional)")
        Spacer(modifier = Modifier.height(8.dp))
        ConsoleTextField(
            value = data.licenseNumber,
            onValueChange = { onDataChange(data.copy(licenseNumber = it)) },
            placeholder = "Enter license number",
            imeAction = ImeAction.Done
        )

        Spacer(modifier = Modifier.height(32.dp))

        ConsoleButton(label = "SAVE & CONTINUE →", onClick = onContinue)

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// ─── Screen 3: CREW CHECK ────────────────────────────────────────────────────

@Composable
fun CrewCheckContent(
    onSolo: () -> Unit,
    onForeman: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "How do you work?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2A2520),
        )
        Text(
            text = "This shapes your SmithNet experience. You can change this later in Settings.",
            fontSize = 13.sp,
            color = Color(0xFF8C8478),
        )

        Surface(
            onClick = onSolo,
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFFAFAF8),
            border = BorderStroke(0.5.dp, Color(0x12000000)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "I work solo",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A2520),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Jobs, time tracking, invoicing — just for me.",
                    fontSize = 12.sp,
                    color = Color(0xFF8C8478),
                )
            }
        }

        Surface(
            onClick = onForeman,
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFFAFAF8),
            border = BorderStroke(0.5.dp, Color(0x12000000)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "I manage a crew",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A2520),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Crew tracking, dispatch, team invoicing, mesh relay.",
                    fontSize = 12.sp,
                    color = Color(0xFF8C8478),
                )
            }
        }
    }
}

// ─── Screen 4: DONE ──────────────────────────────────────────────────────────

@Composable
private fun DoneScreen(onComplete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        androidx.compose.material3.Text(
            "Welcome to\nSmith Net",
            style = ConsoleTheme.title,
            textAlign = TextAlign.Center,
            lineHeight = 44.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        androidx.compose.material3.Text(
            "Your profile is set up. You can manage jobs, track time, and generate invoices — all from the dashboard.",
            style = ConsoleTheme.body,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.material3.Text(
            "You can update any of this in Settings at any time.",
            style = ConsoleTheme.caption,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(56.dp))

        ConsoleButton(label = "GO TO DASHBOARD →", onClick = onComplete, filled = true)

        Spacer(modifier = Modifier.weight(1f))
    }
}

// ─── Shared UI helpers ────────────────────────────────────────────────────────

@Composable
private fun ConsoleLabel(text: String) {
    androidx.compose.material3.Text(
        text = text,
        style = ConsoleTheme.captionBold,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ConsoleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = ConsoleTheme.body,
        cursorBrush = SolidColor(ConsoleTheme.cursor),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .padding(16.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    androidx.compose.material3.Text(
                        text = placeholder,
                        style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun ConsoleButton(
    label: String,
    onClick: () -> Unit,
    filled: Boolean = false
) {
    val bg = if (filled) ConsoleTheme.accent else ConsoleTheme.surface
    val textColor = if (filled) Color.White else ConsoleTheme.accent

    androidx.compose.material3.Text(
        text = label,
        style = ConsoleTheme.action.copy(fontSize = 18.sp, color = textColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(bg)
            .padding(vertical = 18.dp, horizontal = 16.dp),
        textAlign = TextAlign.Center
    )
}

// ─── Page dots ────────────────────────────────────────────────────────────────

@Composable
private fun PageDots(
    currentScreen: OnboardingScreen,
    modifier: Modifier = Modifier
) {
    val screens = OnboardingScreen.values()
    val currentIndex = screens.indexOf(currentScreen)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        screens.forEachIndexed { index, _ ->
            val isActive = index == currentIndex
            Box(
                modifier = Modifier
                    .size(if (isActive) 10.dp else 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isActive) ConsoleTheme.accent else ConsoleTheme.separator
                    )
            )
        }
    }
}

// ─── Data persistence ─────────────────────────────────────────────────────────

private fun saveOnboardingData(
    context: Context,
    data: OnboardingData,
    userPreferences: UserPreferences
) {
    if (data.name.isNotBlank()) {
        userPreferences.setUserName(data.name)
    }

    userPreferences.saveAddress(
        street = data.street,
        city = data.city,
        stateProvince = data.stateProvince,
        zipPostal = data.zipPostal,
        country = ""
    )

    data.occupation?.let { userPreferences.saveOccupation(it.name) }
    data.experienceLevel?.let { userPreferences.saveExperienceLevel(it.name) }

    if (data.businessName.isNotBlank()) {
        userPreferences.saveBusinessName(data.businessName)
    }

    val rate = data.hourlyRate.toDoubleOrNull()
    if (rate != null) {
        userPreferences.setHourlyRate(rate)
    }

    if (data.licenseNumber.isNotBlank()) {
        userPreferences.setLicenseNumber(data.licenseNumber)
    }

    userPreferences.setOnboardingComplete()
}
