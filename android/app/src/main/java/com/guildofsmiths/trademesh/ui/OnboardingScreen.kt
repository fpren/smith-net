package com.guildofsmiths.trademesh.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildofsmiths.trademesh.data.UserPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Smith Net — Post-Registration Guided Setup
 * Assistant-Led • Animated • 4-Screen v1
 */

enum class OnboardingScreen {
    LANGUAGE,
    ADDRESS,
    WORK_CONTEXT,
    BUSINESS_COMPLETE
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
    var language: Language = Language.ENGLISH,
    var street: String = "",
    var city: String = "",
    var stateProvince: String = "",
    var zipPostal: String = "",
    var country: String = "",
    var occupation: Occupation? = null,
    var experienceLevel: ExperienceLevel? = null,
    var businessName: String = "",
    var aiEnabled: Boolean? = null
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    userPreferences: UserPreferences = com.guildofsmiths.trademesh.data.UserPreferences
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(OnboardingScreen.LANGUAGE) }
    var onboardingData by remember { mutableStateOf(OnboardingData()) }

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
        // Invisible swipe zones for left/right navigation
        // Left swipe zone (next screen)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(60.dp)
                .align(Alignment.CenterEnd)
                .clickable {
                    val currentIndex = OnboardingScreen.values().indexOf(currentScreen)
                    val nextIndex = (currentIndex + 1).coerceAtMost(OnboardingScreen.values().size - 1)
                    if (nextIndex != currentIndex) {
                        val nextScreen = OnboardingScreen.values()[nextIndex]
                        when (nextScreen) {
                            OnboardingScreen.ADDRESS -> {
                                // Validate language selection
                                if (onboardingData.language != null) {
                                    scope.launch {
                                        delay(120)
                                        currentScreen = nextScreen
                                    }
                                }
                            }
                            OnboardingScreen.WORK_CONTEXT -> {
                                // Validate address fields
                                if (onboardingData.street.isNotBlank() && onboardingData.city.isNotBlank() &&
                                    onboardingData.stateProvince.isNotBlank() && onboardingData.zipPostal.isNotBlank() &&
                                    onboardingData.country.isNotBlank()) {
                                    scope.launch {
                                        delay(120)
                                        currentScreen = nextScreen
                                    }
                                }
                            }
                            OnboardingScreen.BUSINESS_COMPLETE -> {
                                scope.launch {
                                    delay(120)
                                    currentScreen = nextScreen
                                }
                            }
                            else -> {} // Stay on current screen
                        }
                    }
                }
        )

        // Right swipe zone (previous screen)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(60.dp)
                .align(Alignment.CenterStart)
                .clickable {
                    val currentIndex = OnboardingScreen.values().indexOf(currentScreen)
                    val prevIndex = (currentIndex - 1).coerceAtLeast(0)
                    if (prevIndex != currentIndex) {
                        scope.launch {
                            delay(120)
                            currentScreen = OnboardingScreen.values()[prevIndex]
                        }
                    }
                }
        )
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { transitionSpec },
            modifier = Modifier.fillMaxSize(),
            label = "onboarding-transition"
        ) { screen ->
            when (screen) {
                OnboardingScreen.LANGUAGE -> LanguageScreen(
                    selectedLanguage = onboardingData.language,
                    onLanguageSelected = { language ->
                        onboardingData = onboardingData.copy(language = language)
                        scope.launch {
                            delay(160) // Micro timing for response
                            currentScreen = OnboardingScreen.ADDRESS
                        }
                    }
                )
                OnboardingScreen.ADDRESS -> AddressScreen(
                    data = onboardingData,
                    onDataChange = { onboardingData = it },
                    onContinue = { currentScreen = OnboardingScreen.WORK_CONTEXT },
                    context = context
                )
                OnboardingScreen.WORK_CONTEXT -> WorkContextScreen(
                    data = onboardingData,
                    onDataChange = { onboardingData = it },
                    onContinue = { currentScreen = OnboardingScreen.BUSINESS_COMPLETE }
                )
                OnboardingScreen.BUSINESS_COMPLETE -> BusinessCompleteScreen(
                    data = onboardingData,
                    onDataChange = { onboardingData = it },
                    onContinue = {
                        // Complete onboarding - user is already authenticated
                        saveOnboardingData(context, onboardingData, userPreferences)
                        onComplete()
                    }
                )
            }
        }

        // Page dots with swipe hint
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Swipe hint text
            Text(
                text = "Swipe left/right or tap to continue",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Page dots
            PageDots(currentScreen = currentScreen)
        }
    }
}

@Composable
private fun IntroAIScreen(onAiEnabled: (Boolean) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Guild of Smiths themed assistant icon - monospace style
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ConsoleTheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Simple ASCII-style robot representation
                Text(
                    "[AI]",
                    style = ConsoleTheme.title.copy(
                        fontSize = 16.sp,
                        letterSpacing = 2.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Text(
                    "ASSISTANT",
                    style = ConsoleTheme.captionBold.copy(fontSize = 8.sp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "Hi.\nI'm your assistant.",
            style = ConsoleTheme.title,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "I can help with notes, summaries, and confirmations while you work.\nWould you like me to assist you?",
            style = ConsoleTheme.body,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Console-style button using text
            Text(
                text = "TURN ON →",
                style = ConsoleTheme.action.copy(fontSize = 18.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAiEnabled(true) }
                    .background(ConsoleTheme.surface)
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Not Now",
                style = ConsoleTheme.caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAiEnabled(false) }
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AddressScreen(
    data: OnboardingData,
    onDataChange: (OnboardingData) -> Unit,
    onContinue: () -> Unit,
    context: Context
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        Text(
            "Where should I use for invoices and records?",
            style = ConsoleTheme.title,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Street
            Text(
                text = "STREET",
                style = ConsoleTheme.captionBold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = data.street,
                onValueChange = { onDataChange(data.copy(street = it)) },
                textStyle = ConsoleTheme.body,
                cursorBrush = SolidColor(ConsoleTheme.cursor),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(16.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (data.street.isEmpty()) {
                            Text(
                                text = "Enter street address",
                                style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // City
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CITY",
                        style = ConsoleTheme.captionBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicTextField(
                        value = data.city,
                        onValueChange = { onDataChange(data.copy(city = it)) },
                        textStyle = ConsoleTheme.body,
                        cursorBrush = SolidColor(ConsoleTheme.cursor),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface)
                            .padding(16.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (data.city.isEmpty()) {
                                    Text(
                                        text = "Enter city",
                                        style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                // State/Province
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "STATE",
                        style = ConsoleTheme.captionBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicTextField(
                        value = data.stateProvince,
                        onValueChange = { onDataChange(data.copy(stateProvince = it)) },
                        textStyle = ConsoleTheme.body,
                        cursorBrush = SolidColor(ConsoleTheme.cursor),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface)
                            .padding(16.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (data.stateProvince.isEmpty()) {
                                    Text(
                                        text = "Enter state",
                                        style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ZIP/Postal
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ZIP CODE",
                        style = ConsoleTheme.captionBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicTextField(
                        value = data.zipPostal,
                        onValueChange = { onDataChange(data.copy(zipPostal = it)) },
                        textStyle = ConsoleTheme.body,
                        cursorBrush = SolidColor(ConsoleTheme.cursor),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface)
                            .padding(16.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (data.zipPostal.isEmpty()) {
                                    Text(
                                        text = "Enter ZIP code",
                                        style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                // Country - Dropdown
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "COUNTRY",
                        style = ConsoleTheme.captionBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val countries = listOf(
                        "United States", "Canada", "United Kingdom", "Australia",
                        "Germany", "France", "Japan", "South Korea", "Brazil",
                        "Mexico", "India", "China", "Russia", "Italy", "Spain",
                        "Netherlands", "Sweden", "Norway", "Denmark", "Finland"
                    )

                    var expanded by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true }
                            .background(ConsoleTheme.surface)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = if (data.country.isNotBlank()) data.country else "Select country",
                            style = if (data.country.isNotBlank()) ConsoleTheme.body else ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                        )

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .background(ConsoleTheme.background)
                        ) {
                            countries.forEach { country ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            country,
                                            style = ConsoleTheme.body,
                                            color = ConsoleTheme.text
                                        )
                                    },
                                    onClick = {
                                        onDataChange(data.copy(country = country))
                                        expanded = false
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = ConsoleTheme.text,
                                        leadingIconColor = ConsoleTheme.text
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        val isValid = data.street.isNotBlank() &&
                     data.city.isNotBlank() &&
                     data.stateProvince.isNotBlank() &&
                     data.zipPostal.isNotBlank() &&
                     data.country.isNotBlank()

        Spacer(modifier = Modifier.height(24.dp))

        if (isValid) {
            Text(
                text = "CONTINUE →",
                style = ConsoleTheme.action.copy(fontSize = 18.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onContinue() }
                    .background(ConsoleTheme.surface)
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "Please fill in all address fields",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun WorkContextScreen(
    data: OnboardingData,
    onDataChange: (OnboardingData) -> Unit,
    onContinue: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        Text(
            "What kind of work do you mainly do?",
            style = ConsoleTheme.title,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Occupation dropdown
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "WHAT KIND OF WORK DO YOU MAINLY DO?",
                style = ConsoleTheme.captionBold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            var occupationExpanded by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { occupationExpanded = true }
                    .background(ConsoleTheme.surface)
                    .padding(16.dp)
            ) {
                Text(
                    text = data.occupation?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
                        ?.replace("_", " ") ?: "Select occupation",
                    style = if (data.occupation != null) ConsoleTheme.body else ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                )

                DropdownMenu(
                    expanded = occupationExpanded,
                    onDismissRequest = { occupationExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .background(ConsoleTheme.background)
                ) {
                    Occupation.values().forEach { occupation ->
                        val occupationText = occupation.name.lowercase().replaceFirstChar { it.uppercase() }
                            .replace("_", " ")
                        DropdownMenuItem(
                            text = {
                                Text(
                                    occupationText,
                                    style = ConsoleTheme.body,
                                    color = ConsoleTheme.text
                                )
                            },
                            onClick = {
                                onDataChange(data.copy(occupation = occupation))
                                occupationExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Experience level dropdown
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "HOW EXPERIENCED SHOULD I ASSUME YOU ARE?",
                style = ConsoleTheme.captionBold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            var experienceExpanded by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { experienceExpanded = true }
                    .background(ConsoleTheme.surface)
                    .padding(16.dp)
            ) {
                Text(
                    text = data.experienceLevel?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
                        ?.replace("_", " ") ?: "Select experience level",
                    style = if (data.experienceLevel != null) ConsoleTheme.body else ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                )

                DropdownMenu(
                    expanded = experienceExpanded,
                    onDismissRequest = { experienceExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .background(ConsoleTheme.background)
                ) {
                    ExperienceLevel.values().forEach { level ->
                        val levelText = level.name.lowercase().replaceFirstChar { it.uppercase() }
                            .replace("_", " ")
                        DropdownMenuItem(
                            text = {
                                Text(
                                    levelText,
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
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Fields are optional - you can continue anytime",
            style = ConsoleTheme.caption,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Always-available continue button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        delay(160) // Micro timing
                        onContinue()
                    }
                }
                .background(ConsoleTheme.accent)
                .padding(vertical = 20.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "CONTINUE →",
                style = ConsoleTheme.action.copy(fontSize = 18.sp),
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun BusinessCompleteScreen(
    data: OnboardingData,
    onDataChange: (OnboardingData) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            "Business Information",
            style = ConsoleTheme.title,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "If you work under a business name, you can add it now.",
            style = ConsoleTheme.body,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Business name input
        Text(
            text = "BUSINESS NAME",
            style = ConsoleTheme.captionBold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        BasicTextField(
            value = data.businessName,
            onValueChange = { onDataChange(data.copy(businessName = it)) },
            textStyle = ConsoleTheme.body,
            cursorBrush = SolidColor(ConsoleTheme.cursor),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(16.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (data.businessName.isEmpty()) {
                        Text(
                            text = "Enter business name (optional)",
                            style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                        )
                    }
                    innerTextField()
                }
            }
        )

        Spacer(modifier = Modifier.height(48.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "CONTINUE →",
                style = ConsoleTheme.action.copy(fontSize = 18.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onContinue() }
                    .background(ConsoleTheme.surface)
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(420)) + slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(420, easing = EaseOutCubic)
            )
        ) {
            Text(
                "That's it.\nYou can change any of this later.",
                style = ConsoleTheme.bodySmall,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

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
                        if (isActive) ConsoleTheme.accent
                        else ConsoleTheme.separator
                    )
                    .animateContentSize()
            )
        }
    }
}

@Composable
private fun LanguageScreen(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // AI Assistant Icon
        Text(
            text = "[AI] ASSISTANT",
            style = ConsoleTheme.brand.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "Language",
            style = ConsoleTheme.title,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Choose your preferred language for the app interface.",
            style = ConsoleTheme.body,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Language dropdown
        var expanded by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selectedLanguage.name.lowercase().replaceFirstChar { it.uppercase() },
                style = ConsoleTheme.body.copy(
                    color = ConsoleTheme.accent,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .background(ConsoleTheme.surface)
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(ConsoleTheme.background)
            ) {
                Language.values().forEach { language ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                language.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = ConsoleTheme.body.copy(color = ConsoleTheme.text)
                            )
                        },
                        onClick = {
                            onLanguageSelected(language)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AuthOnboardingScreen(
    onAuthSuccess: () -> Unit,
    onAuthSkip: () -> Unit
) {
    // This will integrate the AuthScreen as the final onboarding step
    AuthScreen(
        onAuthSuccess = onAuthSuccess,
        onSkip = onAuthSkip
    )
}

private fun saveOnboardingData(
    context: Context,
    data: OnboardingData,
    userPreferences: UserPreferences
) {
    // Save language preference
    userPreferences.setLanguage(data.language)

    // Save to Profile
    userPreferences.saveAddress(
        street = data.street,
        city = data.city,
        stateProvince = data.stateProvince,
        zipPostal = data.zipPostal,
        country = data.country
    )

    data.occupation?.let { occupation ->
        userPreferences.saveOccupation(occupation.name)
    }

    data.experienceLevel?.let { experienceLevel ->
        userPreferences.saveExperienceLevel(experienceLevel.name)
    }

    if (data.businessName.isNotBlank()) {
        userPreferences.saveBusinessName(data.businessName)
    }

    // Save to Settings
    data.aiEnabled?.let { aiEnabled ->
        userPreferences.saveAiEnabled(aiEnabled)
    }

    // Mark onboarding as complete
    userPreferences.setOnboardingComplete()
}
