package com.guildofsmiths.trademesh.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ai.AIRouter
import com.guildofsmiths.trademesh.ai.AIStatus
import com.guildofsmiths.trademesh.ai.BatteryGate
import com.guildofsmiths.trademesh.ai.LlamaInference
import com.guildofsmiths.trademesh.ai.ModelDownloader
import com.guildofsmiths.trademesh.ai.ModelState
import com.guildofsmiths.trademesh.data.AIMode
import com.guildofsmiths.trademesh.data.SupabaseAuth
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.service.BackendConfig
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/**
 * Settings screen — bold, clean.
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNameChanged: (String) -> Unit,
    onSignOut: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var userName by remember { mutableStateOf(UserPreferences.getUserName()) }
    var hasChanges by remember { mutableStateOf(false) }
    val isScanning by BoundaryEngine.isScanning.collectAsState()
    val isMeshConnected by BoundaryEngine.isMeshConnected.collectAsState()
    val isGatewayConnected by BoundaryEngine.isGatewayConnected.collectAsState()
    var gatewayUrl by remember { mutableStateOf(BackendConfig.websocketUrl) }
    var supabaseUrl by remember { mutableStateOf(BackendConfig.supabaseUrl) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .clickable(onClick = onBackClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "←", style = ConsoleTheme.title)
            Spacer(modifier = Modifier.width(14.dp))
            Text(text = "SETTINGS", style = ConsoleTheme.title)
        }
        
        ConsoleSeparator()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ════════════════════════════════════════════════════════════════
            // USER
            // ════════════════════════════════════════════════════════════════
            Text(text = "USER", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "ID:", style = ConsoleTheme.caption)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = UserPreferences.getUserId().take(16) + "...",
                    style = ConsoleTheme.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            BasicTextField(
                value = userName,
                onValueChange = {
                    userName = it.take(20)
                    hasChanges = true
                },
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
                            if (userName.isEmpty()) {
                                Text(
                                    text = "enter name",
                                    style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                                )
                            }
                            innerTextField()
                        }
                        if (hasChanges && userName.trim().length >= 2) {
                            Text(
                                text = "[SAVE]",
                                style = ConsoleTheme.action,
                                modifier = Modifier.clickable {
                                    UserPreferences.setUserName(userName.trim())
                                    onNameChanged(userName.trim())
                                    hasChanges = false
                                }
                            )
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))
            
            // ════════════════════════════════════════════════════════════════
            // TRADE ROLE
            // ════════════════════════════════════════════════════════════════
            TradeRoleSection()

            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))
            
            // ════════════════════════════════════════════════════════════════
            // MESH CONNECTION
            // ════════════════════════════════════════════════════════════════
            Text(text = "MESH CONNECTION", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isScanning -> ConsoleTheme.success
                                isMeshConnected -> ConsoleTheme.warning
                                else -> ConsoleTheme.textDim
                            }
                        )
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = when {
                        isScanning -> "Scanning for peers..."
                        isMeshConnected -> "Connected"
                        else -> "Offline"
                    },
                    style = ConsoleTheme.body,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = if (isScanning) "STOP" else "START",
                    style = ConsoleTheme.action.copy(
                        color = if (isScanning) ConsoleTheme.textMuted else ConsoleTheme.accent
                    ),
                    modifier = Modifier
                        .clickable {
                            if (isScanning) BoundaryEngine.disconnectMesh()
                            else BoundaryEngine.connectMesh()
                        }
                        .padding(8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))
            
            // ════════════════════════════════════════════════════════════════
            // GATEWAY RELAY
            // ════════════════════════════════════════════════════════════════
            Text(text = "GATEWAY RELAY", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(10.dp))
            
            BasicTextField(
                value = gatewayUrl,
                onValueChange = { gatewayUrl = it },
                textStyle = ConsoleTheme.bodySmall,
                cursorBrush = SolidColor(ConsoleTheme.cursor),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(14.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (gatewayUrl.isEmpty()) {
                            Text(
                                text = "ws://ip:port",
                                style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.placeholder)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGatewayConnected) ConsoleTheme.success else ConsoleTheme.textDim
                        )
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = if (isGatewayConnected) "Connected to backend" else "Offline",
                    style = ConsoleTheme.body,
                    modifier = Modifier.weight(1f)
                )
                
                if (!isGatewayConnected) {
                    Text(
                        text = "CONNECT",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                        modifier = Modifier
                            .clickable { BoundaryEngine.connectGateway(gatewayUrl) }
                            .padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "SAVE URL",
                    style = ConsoleTheme.action,
                    modifier = Modifier
                        .clickable {
                            BackendConfig.setWebSocketUrl(gatewayUrl)
                            BackendConfig.setBackendUrl(gatewayUrl.replace("ws://", "http://").replace("wss://", "https://"))
                            Toast.makeText(context, "Gateway URLs saved", Toast.LENGTH_SHORT).show()
                        }
                        .padding(8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))

            // ════════════════════════════════════════════════════════════════
            // SUPABASE CONFIGURATION
            // ════════════════════════════════════════════════════════════════
            Text(text = "SUPABASE CONFIGURATION", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(10.dp))

            BasicTextField(
                value = supabaseUrl,
                onValueChange = { supabaseUrl = it },
                textStyle = ConsoleTheme.bodySmall,
                cursorBrush = SolidColor(ConsoleTheme.cursor),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(14.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (supabaseUrl.isEmpty()) {
                            Text(
                                text = "https://your-project.supabase.co",
                                style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.placeholder)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "SAVE SUPABASE URL →",
                style = ConsoleTheme.action,
                modifier = Modifier
                    .clickable {
                        BackendConfig.setSupabaseUrl(supabaseUrl)
                        Toast.makeText(context, "Supabase URL saved", Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))

            // ════════════════════════════════════════════════════════════════
            // AI ASSISTANT
            // ════════════════════════════════════════════════════════════════
            AISettingsSection()
            
            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))
            
            // ════════════════════════════════════════════════════════════════
            // ABOUT
            // ════════════════════════════════════════════════════════════════
            Text(text = "ABOUT", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${ConsoleTheme.APP_NAME} v${ConsoleTheme.APP_VERSION}",
                style = ConsoleTheme.bodyBold
            )
            Text(
                text = "build: ${ConsoleTheme.BUILD_HASH}",
                style = ConsoleTheme.caption
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "made by ${ConsoleTheme.STUDIO}",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))
            
            // ════════════════════════════════════════════════════════════════
            // ACCOUNT ACTIONS
            // ════════════════════════════════════════════════════════════════
            Text(text = "ACCOUNT", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Sign Out Button
            val signOutScope = rememberCoroutineScope()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable {
                        signOutScope.launch {
                            // Sign out from Supabase and clear local data
                            SupabaseAuth.signOut()
                            UserPreferences.clear()
                            onSignOut?.invoke()
                        }
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "[↪]", style = ConsoleTheme.bodyBold)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "SIGN OUT", style = ConsoleTheme.body, modifier = Modifier.weight(1f))
                Text(text = ">", style = ConsoleTheme.body)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Close App Button
            val closeContext = LocalContext.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable {
                        // Disconnect services and close app
                        BoundaryEngine.disconnectMesh()
                        BoundaryEngine.disconnectGateway()
                        AIRouter.shutdown()
                        
                        // Close the app
                        (closeContext as? android.app.Activity)?.finishAffinity()
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "[✕]", style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.error))
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "CLOSE APP", style = ConsoleTheme.body.copy(color = ConsoleTheme.error), modifier = Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// AI SETTINGS SECTION
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun AISettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // AI state
    val aiStatus by AIRouter.status.collectAsState()
    val modelState by LlamaInference.modelState.collectAsState()
    val modelInfo by LlamaInference.modelInfo.collectAsState()
    val batteryState by BatteryGate.gateState.collectAsState()

    // Agent state
    val agentState by com.guildofsmiths.trademesh.ai.AgentInitializer.agentState.collectAsState()
    val agentInitProgress by com.guildofsmiths.trademesh.ai.AgentInitializer.initializationProgress.collectAsState()
    val agentContextSummary by com.guildofsmiths.trademesh.ai.AgentInitializer.contextSummary.collectAsState()
    
    // Download state
    val downloadState by ModelDownloader.downloadState.collectAsState()
    val downloadProgress by ModelDownloader.downloadProgress.collectAsState()
    
    var aiEnabled by remember { mutableStateOf(AIRouter.isEnabled()) }
    var aiMode by remember { mutableStateOf(UserPreferences.getAIMode()) }
    var autoDegradeEnabled by remember { mutableStateOf(BatteryGate.isAutoDegradeEnabled()) }
    var showModelPicker by remember { mutableStateOf(false) }
    
    // Check if any model exists
    val downloadedModels = remember(downloadState) {
        ModelDownloader.getDownloadedModels(context)
    }
    val modelDownloaded = downloadedModels.isNotEmpty()
    val isDownloading = downloadState is ModelDownloader.DownloadState.Downloading
    
    Column {
        Text(text = "AI ASSISTANT", style = ConsoleTheme.captionBold)
        Spacer(modifier = Modifier.height(8.dp))
        
        // Enable/Disable Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .clickable {
                    aiEnabled = !aiEnabled
                    AIRouter.setEnabled(aiEnabled)
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "[◈]", style = ConsoleTheme.bodyBold)
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = "Enable Offline AI", style = ConsoleTheme.body, modifier = Modifier.weight(1f))
            Text(
                text = if (aiEnabled) "[ON]" else "[OFF]",
                style = ConsoleTheme.bodyBold.copy(
                    color = if (aiEnabled) ConsoleTheme.success else ConsoleTheme.textDim
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // AI Mode Toggle (Standard vs Hybrid)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .clickable {
                    aiMode = if (aiMode == AIMode.STANDARD) AIMode.HYBRID else AIMode.STANDARD
                    UserPreferences.setAIMode(aiMode)
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "[⥮]", style = ConsoleTheme.bodyBold)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "AI Mode: ${aiMode.name}", style = ConsoleTheme.body)
                Text(
                    text = when (aiMode) {
                        AIMode.STANDARD -> "Local rules only (always-on, zero battery)"
                        AIMode.HYBRID -> "Local + cloud AI (when connected & charged)"
                    },
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                )
            }
            Text(
                text = if (aiMode == AIMode.HYBRID) "[HYBRID]" else "[STANDARD]",
                style = ConsoleTheme.bodyBold.copy(
                    color = if (aiMode == AIMode.HYBRID) ConsoleTheme.accent else ConsoleTheme.success
                )
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Model Status & Download
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Model:", style = ConsoleTheme.caption)
                Spacer(modifier = Modifier.width(8.dp))
                
                val statusText = when {
                    agentState == com.guildofsmiths.trademesh.ai.AgentState.WAKING -> {
                        "Agent Waking... (${(agentInitProgress * 100).toInt()}%)"
                    }
                    agentState == com.guildofsmiths.trademesh.ai.AgentState.ALIVE -> {
                        "Agent Alive - ${downloadedModels.firstOrNull()?.name ?: "Model"}"
                    }
                    agentState == com.guildofsmiths.trademesh.ai.AgentState.RULE_BASED_FALLBACK -> {
                        "Agent (Rule-Based Mode) - ${downloadedModels.firstOrNull()?.name ?: "Model"}"
                    }
                    isDownloading -> {
                        val dlState = downloadState as? ModelDownloader.DownloadState.Downloading
                        "Downloading ${dlState?.model?.name ?: ""}..."
                    }
                    modelState == ModelState.LOADING -> "Loading..."
                    modelState == ModelState.READY -> {
                        val loadedModel = downloadedModels.firstOrNull()
                        loadedModel?.name ?: "Ready"
                    }
                    modelState == ModelState.ERROR -> "Error"
                    modelDownloaded -> {
                        val model = downloadedModels.firstOrNull()
                        "${model?.name ?: "Downloaded"}"
                    }
                    else -> "Not Downloaded"
                }
                
                val statusColor = when {
                    agentState == com.guildofsmiths.trademesh.ai.AgentState.ALIVE -> ConsoleTheme.success
                    agentState == com.guildofsmiths.trademesh.ai.AgentState.WAKING -> ConsoleTheme.warning
                    agentState == com.guildofsmiths.trademesh.ai.AgentState.RULE_BASED_FALLBACK -> ConsoleTheme.accent
                    modelState == ModelState.READY -> ConsoleTheme.accent
                    modelState == ModelState.LOADING || isDownloading -> ConsoleTheme.warning
                    modelState == ModelState.ERROR -> ConsoleTheme.error
                    modelDownloaded -> ConsoleTheme.accent
                    else -> ConsoleTheme.textDim
                }
                
                Text(
                    text = statusText,
                    style = ConsoleTheme.body.copy(color = statusColor),
                    modifier = Modifier.weight(1f)
                )
                
                // Download/Manage button
                if (!isDownloading) {
                    Text(
                        text = if (modelDownloaded) "[MODELS]" else "[DOWNLOAD]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable {
                            showModelPicker = true
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                // Load button (if downloaded but not loaded)
                if (modelDownloaded && modelState == ModelState.NOT_LOADED && aiEnabled && !isDownloading) {
                    Text(
                        text = "[LOAD]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.success),
                        modifier = Modifier.clickable {
                            scope.launch {
                                val model = downloadedModels.firstOrNull()
                                if (model != null) {
                                    val modelPath = ModelDownloader.getModelPath(context, model.id)
                                    if (modelPath != null) {
                                AIRouter.loadModel(modelPath)
                            }
                                }
                            }
                        }
                    )
                }
                
                // Cancel download button
                if (isDownloading) {
                    Text(
                        text = "[CANCEL]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                        modifier = Modifier.clickable {
                            ModelDownloader.cancelDownload()
                        }
                    )
                }
                
                // Unload button (if loaded or agent alive)
                if (modelState == ModelState.READY || agentState == com.guildofsmiths.trademesh.ai.AgentState.ALIVE) {
                    Text(
                        text = "[UNLOAD]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier.clickable {
                            LlamaInference.unloadModel()
                            com.guildofsmiths.trademesh.ai.AgentInitializer.sleepAgent()
                        }
                    )
                }
            }
            
            // Download progress
            if (isDownloading) {
                val dlState = downloadState as? ModelDownloader.DownloadState.Downloading
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = downloadProgress,
                    modifier = Modifier.fillMaxWidth(),
                    color = ConsoleTheme.accent,
                    trackColor = ConsoleTheme.textDim.copy(alpha = 0.3f)
                )
                Text(
                    text = "${(downloadProgress * 100).toInt()}% (${dlState?.model?.sizeDisplay ?: ""})",
                    style = ConsoleTheme.caption
                )
            }

            // Agent initialization progress
            if (agentState == com.guildofsmiths.trademesh.ai.AgentState.WAKING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = agentInitProgress,
                    modifier = Modifier.fillMaxWidth(),
                    color = ConsoleTheme.success,
                    trackColor = ConsoleTheme.textDim.copy(alpha = 0.3f)
                )
                Text(
                    text = "Initializing agent context...",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.success)
                )
            }
            
            // Download complete message
            if (downloadState is ModelDownloader.DownloadState.Complete) {
                val completeState = downloadState as ModelDownloader.DownloadState.Complete
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "✓ ${completeState.model.name} downloaded!",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.success)
                )
            }
            
            // Download error message
            if (downloadState is ModelDownloader.DownloadState.Error) {
                val errorState = downloadState as ModelDownloader.DownloadState.Error
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "✗ ${errorState.message}",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.error)
                )
            }
            
            // Model info (if loaded)
            if (modelInfo != null && modelState == ModelState.READY) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ctx: ${modelInfo?.contextSize} | vocab: ${modelInfo?.vocabSize}",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                )
            }

            // Agent context info (if alive)
            if (agentState == com.guildofsmiths.trademesh.ai.AgentState.ALIVE && agentContextSummary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Agent Context:",
                    style = ConsoleTheme.captionBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = agentContextSummary.take(200) + if (agentContextSummary.length > 200) "..." else "",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                )
            }
        }
        
        // Model Picker Dialog
        if (showModelPicker) {
            ModelPickerDialog(
                downloadedModels = downloadedModels,
                downloadState = downloadState,
                downloadProgress = downloadProgress,
                onDownload = { model ->
                    scope.launch {
                        ModelDownloader.downloadModel(context, model)
                    }
                },
                onDelete = { model ->
                    ModelDownloader.deleteModel(context, model.id)
                    // Force recomposition
                    showModelPicker = false
                    showModelPicker = true
                },
                onDismiss = {
                    showModelPicker = false
                    ModelDownloader.resetState()
                }
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Battery/Thermal Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Status:", style = ConsoleTheme.caption)
            Spacer(modifier = Modifier.width(8.dp))
            
            // Battery indicator
            val batteryText = "${batteryState.batteryLevel}%"
            val chargingIcon = if (batteryState.isCharging) "⚡" else ""
            val thermalIcon = when {
                batteryState.batteryTemperature >= 42 -> "🔥"
                batteryState.batteryTemperature >= 38 -> "🌡"
                else -> ""
            }
            
            Text(
                text = "Battery: $batteryText$chargingIcon $thermalIcon",
                style = ConsoleTheme.body.copy(
                    color = when {
                        batteryState.batteryLevel <= 15 -> ConsoleTheme.error
                        batteryState.batteryLevel <= 30 -> ConsoleTheme.warning
                        else -> ConsoleTheme.text
                    }
                ),
                modifier = Modifier.weight(1f)
            )
            
            // AI status badge
            val aiStatusText = when {
                agentState == com.guildofsmiths.trademesh.ai.AgentState.ALIVE -> "[AGENT ALIVE]"
                agentState == com.guildofsmiths.trademesh.ai.AgentState.WAKING -> "[WAKING]"
                agentState == com.guildofsmiths.trademesh.ai.AgentState.RULE_BASED_FALLBACK -> "[AGENT FALLBACK]"
                aiStatus == AIStatus.READY -> "[READY]"
                aiStatus == AIStatus.LOADING -> "[LOADING]"
                aiStatus == AIStatus.DEGRADED -> "[DEGRADED]"
                aiStatus == AIStatus.RULE_BASED -> "[RULES]"
                aiStatus == AIStatus.OFFLINE -> "[OFFLINE]"
                aiStatus == AIStatus.DISABLED -> "[OFF]"
                else -> "[UNKNOWN]"
            }
            
            val aiStatusColor = when (aiStatus) {
                AIStatus.READY -> ConsoleTheme.success
                AIStatus.LOADING -> ConsoleTheme.warning
                AIStatus.DEGRADED -> ConsoleTheme.warning
                AIStatus.RULE_BASED -> ConsoleTheme.accent
                AIStatus.OFFLINE, AIStatus.DISABLED -> ConsoleTheme.textDim
            }
            
            Text(
                text = aiStatusText,
                style = ConsoleTheme.bodyBold.copy(color = aiStatusColor)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Auto-degrade toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .clickable {
                    autoDegradeEnabled = !autoDegradeEnabled
                    BatteryGate.setAutoDegradeEnabled(autoDegradeEnabled)
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Auto-degrade on low battery", style = ConsoleTheme.body, modifier = Modifier.weight(1f))
            Text(
                text = if (autoDegradeEnabled) "[ON]" else "[OFF]",
                style = ConsoleTheme.bodyBold.copy(
                    color = if (autoDegradeEnabled) ConsoleTheme.success else ConsoleTheme.textDim
                )
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Usage hint
        val usageHint = when (agentState) {
            com.guildofsmiths.trademesh.ai.AgentState.ALIVE ->
                "Agent is alive and proactive - observes all messages automatically"
            com.guildofsmiths.trademesh.ai.AgentState.RULE_BASED_FALLBACK ->
                "Agent in rule-based mode - use @AI in chat for assistance"
            else -> "Use @AI in chat to invoke assistant"
        }

        Text(
            text = usageHint,
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// TRADE ROLE SECTION
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun TradeRoleSection() {
    val scope = rememberCoroutineScope()

    var currentRole by remember { mutableStateOf(UserPreferences.getTradeRole()) }
    var showRoleSelector by remember { mutableStateOf(false) }

    Column {
        Text(text = "TRADE ROLE", style = ConsoleTheme.captionBold)
        Spacer(modifier = Modifier.height(8.dp))

        // Current role display and selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .clickable { showRoleSelector = true }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "[ROLE]", style = ConsoleTheme.bodyBold)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = currentRole.displayName, style = ConsoleTheme.body)
                Text(
                    text = currentRole.description,
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                )
            }
            Text(text = ">", style = ConsoleTheme.body)
        }
    }

    // Role selector dialog - outside the Column to avoid layout issues
    if (showRoleSelector) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showRoleSelector = false }) {
            RoleSelectorContent(
                currentRole = currentRole,
                onRoleSelected = { newRole ->
                    currentRole = newRole
                    UserPreferences.setTradeRole(newRole)
                    showRoleSelector = false

                    // Send confirmation to chat timeline
                    scope.launch {
                        android.util.Log.i("SettingsScreen", "Trade role updated to: ${newRole.displayName}")
                    }
                },
                onDismiss = { showRoleSelector = false }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// ROLE SELECTOR CONTENT
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun RoleSelectorContent(
    currentRole: com.guildofsmiths.trademesh.data.TradeRole,
    onRoleSelected: (com.guildofsmiths.trademesh.data.TradeRole) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Occupation",
                style = ConsoleTheme.header
            )
            Text(
                text = "[✕]",
                style = ConsoleTheme.bodyBold,
                modifier = Modifier.clickable { onDismiss() }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        ConsoleSeparator()
        Spacer(modifier = Modifier.height(12.dp))

        // Role list with fixed height
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .verticalScroll(rememberScrollState())
        ) {
            com.guildofsmiths.trademesh.data.TradeRole.values().forEach { role ->
                val isSelected = role == currentRole

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) ConsoleTheme.surface else ConsoleTheme.background
                        )
                        .clickable { onRoleSelected(role) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSelected) "[●]" else "[○]",
                        style = ConsoleTheme.bodyBold.copy(
                            color = if (isSelected) ConsoleTheme.success else ConsoleTheme.textDim
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = role.displayName,
                            style = ConsoleTheme.bodyBold
                        )
                        Text(
                            text = role.description,
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ConsoleSeparator()
        Spacer(modifier = Modifier.height(8.dp))

        // Info text
        Text(
            text = "Role affects AI suggestions and safety reminders.",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// MODEL PICKER DIALOG
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ModelPickerDialog(
    downloadedModels: List<ModelDownloader.ModelInfo>,
    downloadState: ModelDownloader.DownloadState,
    downloadProgress: Float,
    onDownload: (ModelDownloader.ModelInfo) -> Unit,
    onDelete: (ModelDownloader.ModelInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val isDownloading = downloadState is ModelDownloader.DownloadState.Downloading
    val downloadingModelId = (downloadState as? ModelDownloader.DownloadState.Downloading)?.model?.id
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background.copy(alpha = 0.95f))
            .clickable(enabled = !isDownloading) { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable(enabled = false) { } // Prevent dismiss when clicking content
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI MODELS",
                    style = ConsoleTheme.header
                )
                if (!isDownloading) {
                    Text(
                        text = "[✕]",
                        style = ConsoleTheme.bodyBold,
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Select a model to download. Larger models = better quality but slower.",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(16.dp))
            
            // Model list
            ModelDownloader.availableModels.forEach { model ->
                val isDownloaded = downloadedModels.any { it.id == model.id }
                val isCurrentlyDownloading = downloadingModelId == model.id
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (model.recommended) ConsoleTheme.surface else ConsoleTheme.background
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Model name and recommendation badge
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = model.name,
                                    style = ConsoleTheme.bodyBold
                                )
                                if (model.recommended) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "[★ RECOMMENDED]",
                                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent)
                                    )
                                }
                            }
                            Text(
                                text = model.description,
                                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                            )
                        }
                        
                        // Size
                        Text(
                            text = model.sizeDisplay,
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Action buttons / status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        when {
                            isCurrentlyDownloading -> {
                                // Show progress
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    LinearProgressIndicator(
                                        progress = downloadProgress,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = ConsoleTheme.accent,
                                        trackColor = ConsoleTheme.textDim.copy(alpha = 0.3f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${(downloadProgress * 100).toInt()}%",
                                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning)
                                        )
                                        Text(
                                            text = "[CANCEL]",
                                            style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                                            modifier = Modifier.clickable {
                                                ModelDownloader.cancelDownload()
                                            }
                                        )
                                    }
                                }
                            }
                            isDownloaded -> {
                                Text(
                                    text = "[✓ DOWNLOADED]",
                                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.success)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "[DELETE]",
                                    style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                                    modifier = Modifier.clickable {
                                        onDelete(model)
                                    }
                                )
                            }
                            isDownloading -> {
                                // Another model is downloading
                                Text(
                                    text = "[WAITING]",
                                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                                )
                            }
                            else -> {
                                Text(
                                    text = "[DOWNLOAD]",
                                    style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                                    modifier = Modifier.clickable {
                                        onDownload(model)
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))
            
            // Info text
            Text(
                text = "Models are downloaded from Hugging Face and stored locally.",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
            )
            Text(
                text = "Requires WiFi connection. Download may take several minutes.",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen(
            onBackClick = { },
            onNameChanged = { }
        )
    }
}
