package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.SupabaseAuth
import com.guildofsmiths.trademesh.service.AuthService
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
    
    val scope = rememberCoroutineScope()
    
    // Auto-show offline option after network errors
    val shouldShowOfflineHint = networkErrorCount >= 2
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo / Brand
        Text(
            text = "╔═══════════════════╗",
            style = ConsoleTheme.body.copy(color = ConsoleTheme.accent)
        )
        Text(
            text = "║  GUILD OF SMITHS  ║",
            style = ConsoleTheme.title.copy(color = ConsoleTheme.accent)
        )
        Text(
            text = "╚═══════════════════╝",
            style = ConsoleTheme.body.copy(color = ConsoleTheme.accent)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Built for the trades",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (showResetPassword) {
            // ══════════════════════════════════════════════════════════════
            // PASSWORD RESET SCREEN
            // ══════════════════════════════════════════════════════════════
            
            Text(
                text = "🔑 RESET PASSWORD",
                style = ConsoleTheme.header.copy(color = ConsoleTheme.accent)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Enter your email address and we'll send",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                textAlign = TextAlign.Center
            )
            Text(
                text = "you a link to reset your password.",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Column(modifier = Modifier.widthIn(max = 300.dp)) {
                Text(
                    text = "> email:",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
                Spacer(modifier = Modifier.height(4.dp))
                ConsoleTextField(
                    value = email,
                    onValueChange = { email = it.lowercase().trim() },
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                    onDone = {
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
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Success message
            if (successMessage != null) {
                Text(
                    text = "[✓] $successMessage",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.success),
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Error message
            if (errorMessage != null) {
                Text(
                    text = "[!] $errorMessage",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.error),
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Send reset button
            Text(
                text = if (isResettingPassword) "[...] SENDING..." else "[▶] SEND RESET LINK",
                style = ConsoleTheme.bodyBold.copy(
                    color = if (isResettingPassword) ConsoleTheme.textMuted else ConsoleTheme.accent
                ),
                modifier = Modifier
                    .clickable(enabled = !isResettingPassword && email.isNotBlank()) {
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
                    .background(ConsoleTheme.surface)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "[←] Back to Login",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                modifier = Modifier
                    .clickable { 
                        showResetPassword = false
                        errorMessage = null
                        successMessage = null
                    }
                    .padding(8.dp)
            )
            
        } else if (!showOfflineMode) {
            // ══════════════════════════════════════════════════════════════
            // MAIN AUTH - Clean Login/Register
            // ══════════════════════════════════════════════════════════════
            
            // Mode toggle
            Row(
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isLoginMode) "[●] LOGIN" else "[ ] LOGIN",
                    style = ConsoleTheme.body.copy(
                        color = if (isLoginMode) ConsoleTheme.accent else ConsoleTheme.textMuted
                    ),
                    modifier = Modifier
                        .clickable { 
                            isLoginMode = true
                            errorMessage = null 
                            showTroubleOptions = false
                        }
                        .padding(8.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = if (!isLoginMode) "[●] REGISTER" else "[ ] REGISTER",
                    style = ConsoleTheme.body.copy(
                        color = if (!isLoginMode) ConsoleTheme.accent else ConsoleTheme.textMuted
                    ),
                    modifier = Modifier
                        .clickable { 
                            isLoginMode = false
                            errorMessage = null
                            showTroubleOptions = false
                        }
                        .padding(8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Form fields
            Column(
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                // Display Name (Register only)
                if (!isLoginMode) {
                    Text(
                        text = "> your_name:",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ConsoleTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        placeholder = "John Smith"
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Email
                Text(
                    text = "> email:",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
                Spacer(modifier = Modifier.height(4.dp))
                ConsoleTextField(
                    value = email,
                    onValueChange = { email = it.lowercase().trim() },
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Password
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "> password:",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                    
                    // Forgot password link (only in login mode)
                    if (isLoginMode) {
                        Text(
                            text = "Forgot?",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
                            modifier = Modifier
                                .clickable { showResetPassword = true }
                                .padding(4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                ConsoleTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "min 6 characters",
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    onDone = {
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
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Success message
            if (successMessage != null) {
                Text(
                    text = "[✓] $successMessage",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.success),
                    modifier = Modifier.padding(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Error message
            if (errorMessage != null) {
                Text(
                    text = "[!] $errorMessage",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.error),
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center
                )
                
                // Show resend confirmation option if email not confirmed
                if (showResendConfirmation && email.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isResending) "[...] SENDING..." else "[↻] RESEND CONFIRMATION EMAIL",
                        style = ConsoleTheme.action.copy(
                            color = if (isResending) ConsoleTheme.textMuted else ConsoleTheme.warning
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
            
            // Submit button
            Text(
                text = if (isLoading) {
                    "[...] ${if (isLoginMode) "LOGGING IN" else "CREATING ACCOUNT"}"
                } else {
                    "[▶] ${if (isLoginMode) "LOGIN" else "CREATE ACCOUNT"}"
                },
                style = ConsoleTheme.bodyBold.copy(
                    color = if (isLoading) ConsoleTheme.textMuted else ConsoleTheme.accent
                ),
                modifier = Modifier
                    .clickable(enabled = !isLoading) {
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
                    }
                    .background(ConsoleTheme.surface)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // ══════════════════════════════════════════════════════════════
            // TROUBLE OPTIONS - Only shown when needed
            // ══════════════════════════════════════════════════════════════
            
            // Show offline hint automatically after network errors
            if (shouldShowOfflineHint) {
                Text(
                    text = "Having connection issues?",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "[↷] Try Offline Mode",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.warning),
                    modifier = Modifier
                        .clickable { showOfflineMode = true }
                        .padding(8.dp)
                )
            } else {
                // Subtle "Having trouble?" link
                Text(
                    text = if (showTroubleOptions) "[−] Having trouble?" else "[+] Having trouble?",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim),
                    modifier = Modifier
                        .clickable { showTroubleOptions = !showTroubleOptions }
                        .padding(8.dp)
                )
                
                if (showTroubleOptions) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "[↷] Use Offline Mode (demo)",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier
                            .clickable { showOfflineMode = true }
                            .padding(vertical = 4.dp)
                    )
                    
                    Text(
                        text = "Data won't sync across devices",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                    )
                }
            }
            
        } else {
            // ══════════════════════════════════════════════════════════════
            // OFFLINE MODE - Local only (demo/testing)
            // ══════════════════════════════════════════════════════════════
            
            Text(
                text = "⚠ OFFLINE MODE",
                style = ConsoleTheme.header.copy(color = ConsoleTheme.warning)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Your data stays on this device only.",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Create a real account anytime to sync.",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Column(modifier = Modifier.widthIn(max = 300.dp)) {
                Text(
                    text = "> your_name:",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
                Spacer(modifier = Modifier.height(4.dp))
                ConsoleTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    placeholder = "Your Name"
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (errorMessage != null) {
                Text(
                    text = "[!] $errorMessage",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.error)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Text(
                text = "[▶] START DEMO",
                style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.warning),
                modifier = Modifier
                    .clickable {
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
                    }
                    .background(ConsoleTheme.surface)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "[←] Back to Login",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                modifier = Modifier
                    .clickable { 
                        showOfflineMode = false
                        showTroubleOptions = false
                    }
                    .padding(8.dp)
            )
        }
    }
}

/**
 * Console-themed text field.
 */
@Composable
private fun ConsoleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .padding(12.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = ConsoleTheme.body.copy(color = ConsoleTheme.textDim)
            )
        }
        
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = ConsoleTheme.body,
            cursorBrush = SolidColor(ConsoleTheme.cursor),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onDone = { onDone?.invoke() }
            ),
            singleLine = true
        )
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
            onSuccess()
        } else {
            onError(result.error ?: "Authentication failed")
        }
    } catch (e: Exception) {
        onError(e.message ?: "Network error - check your connection")
    } finally {
        onLoading(false)
    }
}
