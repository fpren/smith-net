package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.service.AuthService
import kotlinx.coroutines.launch

/**
 * C-01: Authentication Screen
 * Login / Register with pixel art console theme.
 */
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onSkip: () -> Unit,  // For offline/mesh-only mode
    modifier: Modifier = Modifier
) {
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var showServerConfig by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
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
            text = "║   SMITH  NET   ║",
            style = ConsoleTheme.title.copy(color = ConsoleTheme.accent)
        )
        Text(
            text = "╚═══════════════════╝",
            style = ConsoleTheme.body.copy(color = ConsoleTheme.accent)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Phase 0 · Forge",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
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
                    .clickable { isLoginMode = true }
                    .padding(8.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = if (!isLoginMode) "[●] REGISTER" else "[ ] REGISTER",
                style = ConsoleTheme.body.copy(
                    color = if (!isLoginMode) ConsoleTheme.accent else ConsoleTheme.textMuted
                ),
                modifier = Modifier
                    .clickable { isLoginMode = false }
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
                    text = "> display_name:",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
                Spacer(modifier = Modifier.height(4.dp))
                ConsoleTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    placeholder = "Your Name"
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
                onValueChange = { email = it },
                placeholder = "user@example.com",
                keyboardType = KeyboardType.Email
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Password
            Text(
                text = "> password:",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
            )
            Spacer(modifier = Modifier.height(4.dp))
            ConsoleTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "••••••••",
                isPassword = true,
                imeAction = ImeAction.Done,
                onDone = {
                    if (!isLoading) {
                        scope.launch {
                            performAuth(
                                isLoginMode = isLoginMode,
                                email = email,
                                password = password,
                                displayName = displayName,
                                onLoading = { isLoading = it },
                                onError = { errorMessage = it },
                                onSuccess = onAuthSuccess
                            )
                        }
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Error message
        if (errorMessage != null) {
            Text(
                text = "[!] $errorMessage",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.error),
                modifier = Modifier.padding(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Submit button
        Text(
            text = if (isLoading) {
                "[...] ${if (isLoginMode) "LOGGING IN" else "REGISTERING"}"
            } else {
                "[▶] ${if (isLoginMode) "LOGIN" else "REGISTER"}"
            },
            style = ConsoleTheme.bodyBold.copy(
                color = if (isLoading) ConsoleTheme.textMuted else ConsoleTheme.accent
            ),
            modifier = Modifier
                .clickable(enabled = !isLoading) {
                    scope.launch {
                        performAuth(
                            isLoginMode = isLoginMode,
                            email = email,
                            password = password,
                            displayName = displayName,
                            onLoading = { isLoading = it },
                            onError = { errorMessage = it },
                            onSuccess = onAuthSuccess
                        )
                    }
                }
                .background(ConsoleTheme.surface)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Skip / Offline mode
        Text(
            text = "[↷] Skip · Use Mesh Only",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
            modifier = Modifier
                .clickable { onSkip() }
                .padding(8.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Server config toggle
        Text(
            text = if (showServerConfig) "[−] server config" else "[+] server config",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim),
            modifier = Modifier
                .clickable { showServerConfig = !showServerConfig }
                .padding(4.dp)
        )
        
        if (showServerConfig) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "> server_url:",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
            )
            ConsoleTextField(
                value = serverUrl,
                onValueChange = { 
                    serverUrl = it
                    if (it.isNotBlank()) {
                        AuthService.setBaseUrl(it)
                    }
                },
                placeholder = "http://192.168.x.x:3000",
                modifier = Modifier.widthIn(max = 280.dp)
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
 * Perform login or registration.
 */
private suspend fun performAuth(
    isLoginMode: Boolean,
    email: String,
    password: String,
    displayName: String,
    onLoading: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onSuccess: () -> Unit
) {
    // Validation
    if (email.isBlank() || password.isBlank()) {
        onError("Email and password are required")
        return
    }
    
    if (!isLoginMode && displayName.isBlank()) {
        onError("Display name is required")
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
            AuthService.login(email, password)
        } else {
            AuthService.register(email, password, displayName)
        }
        
        if (result.success) {
            onSuccess()
        } else {
            onError(result.error ?: "Authentication failed")
        }
    } catch (e: Exception) {
        onError(e.message ?: "Network error")
    } finally {
        onLoading(false)
    }
}
