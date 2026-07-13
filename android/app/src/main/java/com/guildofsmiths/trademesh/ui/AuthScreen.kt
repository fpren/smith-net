package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.SupabaseAuth
import com.guildofsmiths.trademesh.service.AuthService
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithButton
import com.guildofsmiths.trademesh.ui.theme2.SmithButtonVariant
import com.guildofsmiths.trademesh.ui.theme2.SmithCard
import com.guildofsmiths.trademesh.ui.theme2.SmithTextField
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import kotlinx.coroutines.launch

/**
 * C-01: Authentication Screen
 * Login / Register with Supabase Auth
 *
 * Smart UX:
 * - Clean login/register on first page
 * - Offline mode hidden until needed (network error or user asks)
 * - Encourages real account creation
 */
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoginMode by remember { mutableStateOf(true) }
    var showOfflineMode by remember { mutableStateOf(false) }
    var showResetPassword by remember { mutableStateOf(false) }
    var showTroubleOptions by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var networkErrorCount by remember { mutableStateOf(0) }
    var showResendConfirmation by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var isResettingPassword by remember { mutableStateOf(false) }

    val colors = LocalSmithColors.current
    val scope = rememberCoroutineScope()

    // Auto-show offline option after network errors
    val shouldShowOfflineHint = networkErrorCount >= 2

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Wordmark (Maestro-pinned copy - do not reword)
        Text(
            text = "GUILD OF SMITHS",
            style = SmithType.brand.copy(color = colors.ink)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Built for the trades",
            style = SmithType.caption.copy(color = colors.inkMuted)
        )

        Spacer(modifier = Modifier.height(28.dp))

        SmithCard(
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
            contentPadding = PaddingValues(24.dp),
        ) {
            if (showResetPassword) {
                // ══════════════════════════════════════════════════════════════
                // PASSWORD RESET SCREEN
                // ══════════════════════════════════════════════════════════════

                Text(
                    text = "[↻] RESET PASSWORD",
                    style = SmithType.header.copy(color = colors.accent)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Enter your email address and we'll send",
                    style = SmithType.caption.copy(color = colors.inkMuted),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "you a link to reset your password.",
                    style = SmithType.caption.copy(color = colors.inkMuted),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                SmithTextField(
                    value = email,
                    onValueChange = { email = it.lowercase().trim() },
                    placeholder = "you@example.com",
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        if (email.isNotBlank() && !isResettingPassword) {
                            scope.launch {
                                isResettingPassword = true
                                errorMessage = null
                                val result = SupabaseAuth.resetPassword(email)
                                isResettingPassword = false
                                if (result.success) {
                                    successMessage = result.error
                                    errorMessage = null
                                } else {
                                    errorMessage = result.error
                                    successMessage = null
                                }
                            }
                        }
                    },
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Success message
                if (successMessage != null) {
                    Text(
                        text = "[✓] $successMessage",
                        style = SmithType.caption.copy(color = colors.statusOnline),
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Error message
                if (errorMessage != null) {
                    Text(
                        text = "[!] $errorMessage",
                        style = SmithType.caption.copy(color = colors.statusError),
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                SmithButton(
                    text = if (isResettingPassword) "[...] SENDING..." else "[▶] SEND RESET LINK",
                    onClick = {
                        scope.launch {
                            isResettingPassword = true
                            errorMessage = null
                            val result = SupabaseAuth.resetPassword(email)
                            isResettingPassword = false
                            if (result.success) {
                                successMessage = result.error
                                errorMessage = null
                            } else {
                                errorMessage = result.error
                                successMessage = null
                            }
                        }
                    },
                    enabled = !isResettingPassword && email.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                SmithButton(
                    text = "[←] Back to Login",
                    onClick = {
                        showResetPassword = false
                        errorMessage = null
                        successMessage = null
                    },
                    variant = SmithButtonVariant.Ghost,
                )

            } else if (!showOfflineMode) {
                // ══════════════════════════════════════════════════════════════
                // MAIN AUTH - Clean Login/Register
                // ══════════════════════════════════════════════════════════════

                // Mode toggle - segmented control on the M2 pattern
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bgSunken, RoundedCornerShape(Tokens2.RadiusControl))
                        .padding(4.dp)
                ) {
                    Text(
                        text = "LOGIN",
                        style = SmithType.action.copy(
                            color = if (isLoginMode) colors.accent else colors.inkMuted
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Tokens2.RadiusControl))
                            .background(if (isLoginMode) colors.bgPanel else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                isLoginMode = true
                                errorMessage = null
                                showTroubleOptions = false
                            }
                            .padding(vertical = 8.dp)
                    )
                    Text(
                        text = "REGISTER",
                        style = SmithType.action.copy(
                            color = if (!isLoginMode) colors.accent else colors.inkMuted
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Tokens2.RadiusControl))
                            .background(if (!isLoginMode) colors.bgPanel else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                isLoginMode = false
                                errorMessage = null
                                showTroubleOptions = false
                            }
                            .padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (!isLoginMode) {
                    SmithTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        placeholder = "John Smith",
                        label = "Your name",
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                SmithTextField(
                    value = email,
                    onValueChange = { email = it.lowercase().trim() },
                    placeholder = "you@example.com",
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                    modifier = Modifier.testTag("solo_e2e_auth_email"),
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Password label row keeps the Forgot? link, so the field itself is label-less
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Password",
                        style = SmithType.bodySmall.copy(color = colors.inkMuted)
                    )
                    if (isLoginMode) {
                        Text(
                            text = "Forgot?",
                            style = SmithType.caption.copy(color = colors.accent),
                            modifier = Modifier
                                .clickable { showResetPassword = true }
                                .padding(4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                SmithTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "min 6 characters",
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        if (!isLoading) {
                            scope.launch {
                                performSupabaseAuth(
                                    isLoginMode = isLoginMode,
                                    email = email,
                                    password = password,
                                    displayName = displayName,
                                    onLoading = { isLoading = it },
                                    onError = { error ->
                                        errorMessage = error
                                        successMessage = null
                                        // Track network errors
                                        if (error?.contains("network", ignoreCase = true) == true ||
                                            error?.contains("connection", ignoreCase = true) == true ||
                                            error?.contains("timeout", ignoreCase = true) == true) {
                                            networkErrorCount++
                                        }
                                        // Show resend option for email confirmation errors
                                        showResendConfirmation = error?.contains("confirm", ignoreCase = true) == true
                                    },
                                    onSuccess = {
                                        successMessage = "Welcome!"
                                        onAuthSuccess()
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.testTag("solo_e2e_auth_password"),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Success message
                if (successMessage != null) {
                    Text(
                        text = "[✓] $successMessage",
                        style = SmithType.caption.copy(color = colors.statusOnline),
                        modifier = Modifier.padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Error message
                if (errorMessage != null) {
                    Text(
                        text = "[!] $errorMessage",
                        style = SmithType.caption.copy(color = colors.statusError),
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center
                    )

                    // Show resend confirmation option if email not confirmed
                    if (showResendConfirmation && email.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isResending) "[...] SENDING..." else "[↻] RESEND CONFIRMATION EMAIL",
                            style = SmithType.action.copy(
                                color = if (isResending) colors.inkMuted else colors.attention
                            ),
                            modifier = Modifier
                                .clickable(enabled = !isResending) {
                                    scope.launch {
                                        isResending = true
                                        val result = SupabaseAuth.resendConfirmationEmail(email)
                                        isResending = false
                                        if (result.success) {
                                            successMessage = result.error // Contains success message
                                            errorMessage = null
                                            showResendConfirmation = false
                                        } else {
                                            errorMessage = result.error
                                        }
                                    }
                                }
                                .padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(20.dp))
                SmithButton(
                    text = if (isLoading) {
                        if (isLoginMode) "[...] LOGGING IN" else "[...] CREATING ACCOUNT"
                    } else {
                        if (isLoginMode) "[▶] LOGIN" else "[▶] CREATE ACCOUNT"
                    },
                    onClick = {
                        scope.launch {
                            performSupabaseAuth(
                                isLoginMode = isLoginMode,
                                email = email,
                                password = password,
                                displayName = displayName,
                                onLoading = { isLoading = it },
                                onError = { error ->
                                    errorMessage = error
                                    successMessage = null
                                    if (error?.contains("network", ignoreCase = true) == true ||
                                        error?.contains("connection", ignoreCase = true) == true ||
                                        error?.contains("timeout", ignoreCase = true) == true) {
                                        networkErrorCount++
                                    }
                                    // Show resend option for email confirmation errors
                                    showResendConfirmation = error?.contains("confirm", ignoreCase = true) == true
                                },
                                onSuccess = {
                                    successMessage = "Welcome!"
                                    onAuthSuccess()
                                }
                            )
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ══════════════════════════════════════════════════════════════
                // TROUBLE OPTIONS - Only shown when needed
                // ══════════════════════════════════════════════════════════════

                // Show offline hint automatically after network errors
                if (shouldShowOfflineHint) {
                    Text(
                        text = "Having connection issues?",
                        style = SmithType.caption.copy(color = colors.attention)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "[↷] Try Offline Mode",
                        style = SmithType.action.copy(color = colors.attention),
                        modifier = Modifier
                            .clickable { showOfflineMode = true }
                            .padding(8.dp)
                    )
                } else {
                    // Subtle "Having trouble?" link
                    Text(
                        text = if (showTroubleOptions) "[−] Having trouble?" else "[+] Having trouble?",
                        style = SmithType.caption.copy(color = colors.inkMuted),
                        modifier = Modifier
                            .clickable { showTroubleOptions = !showTroubleOptions }
                            .padding(8.dp)
                    )

                    if (showTroubleOptions) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "[↷] Use Offline Mode (demo)",
                            style = SmithType.caption.copy(color = colors.inkMuted),
                            modifier = Modifier
                                .clickable { showOfflineMode = true }
                                .padding(vertical = 4.dp)
                        )

                        Text(
                            text = "Data won't sync across devices",
                            style = SmithType.caption.copy(color = colors.inkMuted)
                        )
                    }
                }

            } else {
                // ══════════════════════════════════════════════════════════════
                // OFFLINE MODE - Local only (demo/testing)
                // ══════════════════════════════════════════════════════════════

                Text(
                    text = "[!] OFFLINE MODE",
                    style = SmithType.header.copy(color = colors.attention)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your data stays on this device only.",
                    style = SmithType.caption.copy(color = colors.inkMuted),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Create a real account anytime to sync.",
                    style = SmithType.caption.copy(color = colors.inkMuted),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                SmithTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    placeholder = "Your Name",
                    label = "Your name",
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (errorMessage != null) {
                    Text(
                        text = "[!] $errorMessage",
                        style = SmithType.caption.copy(color = colors.statusError)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                SmithButton(
                    text = "[▶] START DEMO",
                    onClick = {
                        if (displayName.isBlank()) {
                            errorMessage = "Please enter your name"
                        } else {
                            scope.launch {
                                val result = SupabaseAuth.signUp(
                                    email = "demo_${System.currentTimeMillis()}@offline.local",
                                    password = "offline123",
                                    displayName = displayName.trim()
                                )
                                if (result.success) {
                                    onAuthSuccess()
                                } else {
                                    onSkip()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                SmithButton(
                    text = "[←] Back to Login",
                    onClick = {
                        showOfflineMode = false
                        showTroubleOptions = false
                    },
                    variant = SmithButtonVariant.Ghost,
                )
            }
        }
    }
}

/**
 * Perform login or registration via Supabase.
 */
private suspend fun performSupabaseAuth(
    isLoginMode: Boolean,
    email: String,
    password: String,
    displayName: String,
    onLoading: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onSuccess: () -> Unit
) {
    // Validation
    if (email.isBlank()) {
        onError("Email is required")
        return
    }

    if (!email.contains("@") || !email.contains(".")) {
        onError("Please enter a valid email address")
        return
    }

    if (password.isBlank()) {
        onError("Password is required")
        return
    }

    if (!isLoginMode && displayName.isBlank()) {
        onError("Please enter your name")
        return
    }

    if (password.length < 6) {
        onError("Password must be at least 6 characters")
        return
    }

    onError(null)
    onLoading(true)

    try {
        val result = if (isLoginMode) {
            SupabaseAuth.signIn(email, password)
        } else {
            SupabaseAuth.signUp(email, password, displayName)
        }

        if (result.success) {
            // Also obtain a smithnet JWT so AuthService-backed features (presence, shifts, jobs)
            // can authenticate. Best-effort: failure here does not block the Supabase login.
            if (isLoginMode) {
                runCatching { AuthService.login(email, password) }
            }
            onSuccess()
        } else {
            // Supabase rejected; try smithnet backend as a fallback so accounts that exist
            // only in the smithnet users table can still sign in.
            val smith = if (isLoginMode) {
                runCatching { AuthService.login(email, password) }.getOrNull()
            } else null
            if (smith?.success == true) {
                onSuccess()
            } else {
                onError(result.error ?: "Authentication failed")
            }
        }
    } catch (e: Exception) {
        onError(e.message ?: "Network error - check your connection")
    } finally {
        onLoading(false)
    }
}
