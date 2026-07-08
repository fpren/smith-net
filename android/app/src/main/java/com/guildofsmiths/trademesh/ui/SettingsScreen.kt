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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.ai.AIRouter
import com.guildofsmiths.trademesh.ai.AIStatus
import com.guildofsmiths.trademesh.ai.BatteryGate
import com.guildofsmiths.trademesh.ai.LlamaInference
import com.guildofsmiths.trademesh.ai.ModelDownloader
import com.guildofsmiths.trademesh.ai.ModelState
import com.guildofsmiths.trademesh.data.AIMode
import com.guildofsmiths.trademesh.data.Permission
import com.guildofsmiths.trademesh.data.RoleContext
import com.guildofsmiths.trademesh.data.SupabaseAuth
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.data.TradesList
import com.guildofsmiths.trademesh.data.UserRole
import com.guildofsmiths.trademesh.service.AuthService
import com.guildofsmiths.trademesh.ui.theme2.SmithConfirmDialog
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/**
 * Settings screen — bold, clean.
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNameChanged: (String) -> Unit,
    onProfileClick: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var userName by remember { mutableStateOf(UserPreferences.getUserName()) }
    var hasChanges by remember { mutableStateOf(false) }
    val isScanning by BoundaryEngine.isScanning.collectAsState()
    val isMeshConnected by BoundaryEngine.isMeshConnected.collectAsState()
    val isGatewayConnected by BoundaryEngine.isGatewayConnected.collectAsState()
    // Default the gateway to the production relay (WebSocket scheme derived from
    // the primary backend URL) so off-LAN beta devices connect out of the box.
    var gatewayUrl by remember {
        mutableStateOf(
            com.guildofsmiths.trademesh.BuildConfig.BACKEND_URL_PRIMARY
                .replaceFirst("https://", "wss://")
                .replaceFirst("http://", "ws://")
        )
    }
    
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
            // PROFILE
            // ════════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable { onProfileClick?.invoke() }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = userName.ifBlank { "Set up profile" }, style = ConsoleTheme.bodyBold)
                    Text(
                        text = "Name, trade, rates, billing",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                }
                Text(text = ">", style = ConsoleTheme.body, color = ConsoleTheme.textMuted)
            }

            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))

            // ════════════════════════════════════════════════════════════════
            // PRIVACY — discoverability + SmithNet ID
            // ════════════════════════════════════════════════════════════════
            PrivacySection()

            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))

            // ════════════════════════════════════════════════════════════════
            // WORK MODE
            // ════════════════════════════════════════════════════════════════
            WorkModeSection()

            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))

            // ════════════════════════════════════════════════════════════════
            // TEAM (invite codes for foremen; join code for everyone else)
            // ════════════════════════════════════════════════════════════════
            TeamSection()

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
            // MESH CONNECTION (hidden for solo — foreman/crew feature)
            // ════════════════════════════════════════════════════════════════
            if (RoleContext.can(Permission.GATEWAY_RELAY)) {
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
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))
            } // end foreman-only mesh/gateway section

            // ════════════════════════════════════════════════════════════════
            // SMITHAI — unified AI settings
            // ════════════════════════════════════════════════════════════════
            SmithAISection()
            
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

            // ════════════════════════════════════════════════════════════════
            // LOCATION SHARING (GPS · clock-in validation · lost & found)
            // ════════════════════════════════════════════════════════════════
            val locState by com.guildofsmiths.trademesh.data.LocationSharingPreferences.state.collectAsState()
            Text(
                text = "LOCATION SHARING",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (locState.enabled) ConsoleTheme.surface else ConsoleTheme.background)
                    .clickable { com.guildofsmiths.trademesh.data.LocationSharingPreferences.setEnabled(!locState.enabled) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (locState.enabled) "((●))" else "((○))",
                    style = ConsoleTheme.bodySmall.copy(
                        color = if (locState.enabled) ConsoleTheme.accent else ConsoleTheme.textMuted
                    )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Share my location while clocked in",
                        style = ConsoleTheme.bodySmall.copy(
                            color = if (locState.enabled) ConsoleTheme.accent else ConsoleTheme.text
                        )
                    )
                    Text(
                        "Powers clock-in geofence validation and Lost & Found.",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            // Cadence picker
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.guildofsmiths.trademesh.data.LocationSharingPreferences.Cadence.entries.forEach { cad ->
                    val sel = cad == locState.cadence
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (sel) ConsoleTheme.accent else ConsoleTheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .clickable { com.guildofsmiths.trademesh.data.LocationSharingPreferences.setCadence(cad) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val short = when (cad) {
                            com.guildofsmiths.trademesh.data.LocationSharingPreferences.Cadence.FAST -> "60s"
                            com.guildofsmiths.trademesh.data.LocationSharingPreferences.Cadence.MEDIUM -> "5min"
                            com.guildofsmiths.trademesh.data.LocationSharingPreferences.Cadence.MANUAL -> "Manual"
                        }
                        Text(short, style = ConsoleTheme.caption.copy(color = if (sel) androidx.compose.ui.graphics.Color.White else ConsoleTheme.textMuted))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Forget my trail
            val forgetScope = rememberCoroutineScope()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable {
                        forgetScope.launch {
                            com.guildofsmiths.trademesh.data.LocationTrailRepository.forgetTrail(UserPreferences.getUserId())
                        }
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "[x]", style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.error))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Forget my trail", style = ConsoleTheme.body)
                    Text(
                        "Deletes all GPS points stored on this device for your user ID.",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                }
                Text(text = ">", style = ConsoleTheme.body)
            }
            Spacer(modifier = Modifier.height(12.dp))

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
// PRIVACY — discoverability + SmithNet ID
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PrivacySection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val user by SupabaseAuth.currentUser.collectAsState()
    val offline = SupabaseAuth.isOfflineMode()

    var level by remember(user?.discoverability) {
        mutableStateOf(user?.discoverability ?: "team")
    }
    var showQr by remember { mutableStateOf(false) }
    var uploadingAvatar by remember { mutableStateOf(false) }
    val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadingAvatar = true
            scope.launch {
                val url = com.guildofsmiths.trademesh.service.AvatarUploader.upload(context, uri)
                uploadingAvatar = false
                if (url != null) {
                    SupabaseAuth.updateLocalAvatar(url)
                    Toast.makeText(context, "Photo updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Text(text = "PRIVACY", style = ConsoleTheme.captionBold)
    Spacer(modifier = Modifier.height(10.dp))

    val publicId = user?.publicId
    val formattedId = publicId?.let { formatPublicId(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.clickable { avatarPicker.launch("image/*") }) {
            com.guildofsmiths.trademesh.ui.components.SmithAvatar(
                name = user?.displayName ?: "",
                size = 44,
                photoUrl = user?.avatarUrl
            )
            Text(
                text = if (uploadingAvatar) "…" else "+",
                style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.surface, fontSize = 11.sp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(ConsoleTheme.accent, androidx.compose.foundation.shape.CircleShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Your SmithNet ID", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
            Text(
                text = formattedId ?: if (offline) "— (sign in to see)" else "—",
                style = ConsoleTheme.bodyBold
            )
        }
        if (formattedId != null) {
            Text(
                text = "[Copy]",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                modifier = Modifier
                    .clickable {
                        clipboard.setText(AnnotatedString(formattedId))
                        Toast.makeText(context, "ID copied", Toast.LENGTH_SHORT).show()
                    }
                    .padding(8.dp)
            )
            Text(
                text = if (showQr) "[Hide]" else "[QR]",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                modifier = Modifier
                    .clickable { showQr = !showQr }
                    .padding(8.dp)
            )
        }
    }
    if (showQr && publicId != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            com.guildofsmiths.trademesh.ui.comm.MyIdQrCard(publicId = publicId)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text("WHO CAN FIND ME", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
    Spacer(modifier = Modifier.height(6.dp))

    val options = listOf(
        Triple("nobody", "Nobody", "I won't appear in any search."),
        Triple("team", "Team only", "Only people in my organization can find me."),
        Triple("anyone", "Anyone", "Anyone with the app can find me by name or ID.")
    )

    options.forEach { (value, title, description) ->
        val isSelected = level == value
        val canTap = !offline && user != null

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) ConsoleTheme.surface else ConsoleTheme.background)
                .clickable(enabled = canTap) {
                    val prior = level
                    level = value
                    scope.launch {
                        val result = SupabaseAuth.updateDiscoverability(value)
                        if (!result.success) {
                            level = prior
                            Toast.makeText(
                                context,
                                result.error ?: "Privacy update failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSelected) "((●))" else "((○))",
                style = ConsoleTheme.bodySmall.copy(
                    color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.textMuted
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ConsoleTheme.bodySmall.copy(
                        color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.text
                    )
                )
                Text(
                    text = description,
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
            }
        }
    }

    if (offline) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Offline — privacy changes sync when you reconnect.",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )
    }
}

private fun formatPublicId(raw: String): String {
    val clean = raw.replace("-", "").uppercase()
    return if (clean.length >= 8) "${clean.substring(0, 4)}-${clean.substring(4, 8)}" else clean
}

// ════════════════════════════════════════════════════════════════════════════
// SMITHAI — UNIFIED AI SETTINGS
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SmithAISection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // AI state
    val aiStatus by AIRouter.status.collectAsState()
    val modelState by LlamaInference.modelState.collectAsState()
    @Suppress("UNUSED_VARIABLE") val modelInfo by LlamaInference.modelInfo.collectAsState()
    val batteryState by BatteryGate.gateState.collectAsState()

    // Agent state
    val agentState by com.guildofsmiths.trademesh.ai.AgentInitializer.agentState.collectAsState()
    val agentInitProgress by com.guildofsmiths.trademesh.ai.AgentInitializer.initializationProgress.collectAsState()

    // Download state
    val downloadState by ModelDownloader.downloadState.collectAsState()
    val downloadProgress by ModelDownloader.downloadProgress.collectAsState()

    var aiEnabled by remember { mutableStateOf(AIRouter.isEnabled()) }
    var supervisorMode by remember { mutableStateOf(UserPreferences.getAISupervisorMode()) }
    var autoDegradeEnabled by remember { mutableStateOf(BatteryGate.isAutoDegradeEnabled()) }
    var showModelPicker by remember { mutableStateOf(false) }

    // Cloud API key
    var apiKey by remember { mutableStateOf(UserPreferences.getOpenRouterApiKey()) }
    var cloudModel by remember { mutableStateOf(UserPreferences.getCloudModel()) }
    var modelEditing by remember { mutableStateOf(false) }
    var modelInput by remember { mutableStateOf("") }
    var showKeyField by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    val maskedKey = if (apiKey.length > 8) "••••••••${apiKey.takeLast(4)}" else if (apiKey.isNotBlank()) "••••" else ""
    
    // Check if any model exists
    val downloadedModels = remember(downloadState) {
        ModelDownloader.getDownloadedModels(context)
    }
    val modelDownloaded = downloadedModels.isNotEmpty()
    val isDownloading = downloadState is ModelDownloader.DownloadState.Downloading
    
    Column {
        Text(text = "SMITHAI", style = ConsoleTheme.captionBold)
        Spacer(modifier = Modifier.height(8.dp))

        // ── Enable Toggle ──
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
            Text(text = "Enable SmithAI", style = ConsoleTheme.body, modifier = Modifier.weight(1f))
            Text(
                text = if (aiEnabled) "[ON]" else "[OFF]",
                style = ConsoleTheme.bodyBold.copy(
                    color = if (aiEnabled) ConsoleTheme.success else ConsoleTheme.textDim
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Mode: Auto / Approve / Off ──
        Text(text = "MODE", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
            modifier = Modifier.padding(top = 4.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("auto" to "Auto", "semi-auto" to "Approve", "off" to "Off").forEach { (value, label) ->
                val isSelected = supervisorMode == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) ConsoleTheme.accent.copy(alpha = 0.10f) else ConsoleTheme.surface
                        )
                        .border(
                            0.5.dp,
                            if (isSelected) ConsoleTheme.accent else ConsoleTheme.text.copy(alpha = 0.06f),
                            RoundedCornerShape(4.dp)
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            supervisorMode = value
                            UserPreferences.setAISupervisorMode(value)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = ConsoleTheme.captionBold.copy(
                            color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.textMuted
                        )
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when (supervisorMode) {
                "auto" -> "Acts on your behalf — posts insights, drafts messages."
                "semi-auto" -> "Drafts suggestions for your approval before acting."
                else -> "SmithAI will only respond when asked."
            },
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Cloud Connection ──
        Text(text = "CLOUD", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        Spacer(modifier = Modifier.height(4.dp))

        if (!showKeyField) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable { showKeyField = true; keyInput = apiKey }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (apiKey.isNotBlank()) maskedKey else "Not set",
                    style = ConsoleTheme.bodySmall.copy(
                        color = if (apiKey.isNotBlank()) ConsoleTheme.text else ConsoleTheme.textMuted
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (apiKey.isNotBlank()) {
                        Text("Connected", style = ConsoleTheme.caption.copy(color = ConsoleTheme.success))
                    }
                    Text("[Edit]", style = ConsoleTheme.action.copy(color = ConsoleTheme.accent))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    textStyle = ConsoleTheme.bodySmall,
                    cursorBrush = SolidColor(ConsoleTheme.cursor),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (keyInput.isEmpty()) {
                                Text("sk-or-v1-...", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.textMuted))
                            }
                            inner()
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "[Save]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable {
                            UserPreferences.setOpenRouterApiKey(keyInput.trim())
                            apiKey = keyInput.trim()
                            showKeyField = false
                            testResult = null
                            scope.launch {
                                val ok = com.guildofsmiths.trademesh.ai.OpenRouterClient.testConnection()
                                testResult = if (ok) "Connected" else "Failed"
                            }
                        }
                    )
                    Text(
                        "[Cancel]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier.clickable { showKeyField = false }
                    )
                }
            }
        }
        if (testResult != null) {
            Text(
                testResult!!,
                style = ConsoleTheme.caption.copy(
                    color = if (testResult == "Connected") ConsoleTheme.success else ConsoleTheme.error
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Cloud Model picker ──
        Text(text = "MODEL", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        Spacer(modifier = Modifier.height(4.dp))

        if (!modelEditing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable { modelEditing = true; modelInput = cloudModel }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (cloudModel.isNotBlank()) cloudModel else "Provider default",
                    style = ConsoleTheme.bodySmall.copy(
                        color = if (cloudModel.isNotBlank()) ConsoleTheme.text else ConsoleTheme.textMuted
                    )
                )
                Text("[Edit]", style = ConsoleTheme.action.copy(color = ConsoleTheme.accent))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickOptions = listOf(
                    "Claude Sonnet" to "anthropic/claude-sonnet-4.5",
                    "Claude Haiku" to "anthropic/claude-haiku-4.5",
                    "GPT-4o mini" to "openai/gpt-4o-mini",
                    "GPT-4o" to "openai/gpt-4o",
                    "Gemini Flash" to "google/gemini-2.5-flash",
                    "Llama 3.3" to "meta-llama/llama-3.3-70b-instruct",
                    "Pi (Inflection)" to "inflection/inflection-3-pi",
                    "Grok 4" to "x-ai/grok-4",
                    "DeepSeek" to "deepseek/deepseek-chat",
                    "Free (Liquid)" to "liquid/lfm-2.5-1.2b-instruct:free"
                )
                Text("quick pick", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                val quickScroll = androidx.compose.foundation.rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(quickScroll),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickOptions.forEach { (label, slug) ->
                        Text(
                            text = "[$label]",
                            style = ConsoleTheme.action.copy(
                                color = if (modelInput == slug) ConsoleTheme.accent else ConsoleTheme.textMuted
                            ),
                            modifier = Modifier
                                .clickable { modelInput = slug }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
                Text("or paste a model id", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                BasicTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    textStyle = ConsoleTheme.bodySmall,
                    cursorBrush = SolidColor(ConsoleTheme.cursor),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (modelInput.isEmpty()) {
                                Text("provider/model-id", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.textMuted))
                            }
                            inner()
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "[Save]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable {
                            UserPreferences.setCloudModel(modelInput.trim())
                            cloudModel = modelInput.trim()
                            modelEditing = false
                        }
                    )
                    Text(
                        "[Default]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier.clickable {
                            UserPreferences.setCloudModel("")
                            cloudModel = ""
                            modelEditing = false
                        }
                    )
                    Text(
                        "[Cancel]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier.clickable { modelEditing = false }
                    )
                }
                Text(
                    "Model id is sent to your provider. OpenRouter accepts any of these. Different providers (sk-, sk-or-, xai-) honor different ids — pick one your key supports.",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Offline Model ──
        Text(text = "OFFLINE", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusText = when {
                    agentState == com.guildofsmiths.trademesh.ai.AgentState.WAKING ->
                        "Waking... (${(agentInitProgress * 100).toInt()}%)"
                    agentState == com.guildofsmiths.trademesh.ai.AgentState.ALIVE ->
                        "${downloadedModels.firstOrNull()?.name ?: "Model"} · Active"
                    agentState == com.guildofsmiths.trademesh.ai.AgentState.RULE_BASED_FALLBACK ->
                        "${downloadedModels.firstOrNull()?.name ?: "Model"} · Rules"
                    isDownloading -> {
                        val dlState = downloadState as? ModelDownloader.DownloadState.Downloading
                        "Downloading ${dlState?.model?.name ?: ""}..."
                    }
                    modelState == ModelState.LOADING -> "Loading..."
                    modelState == ModelState.READY -> downloadedModels.firstOrNull()?.name ?: "Ready"
                    modelState == ModelState.ERROR -> "Error"
                    modelDownloaded -> downloadedModels.firstOrNull()?.name ?: "Downloaded"
                    else -> "No model"
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

                if (!isDownloading) {
                    Text(
                        text = if (modelDownloaded) "[MODELS]" else "[DOWNLOAD]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable { showModelPicker = true }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
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
                if (isDownloading) {
                    Text(
                        text = "[CANCEL]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                        modifier = Modifier.clickable { ModelDownloader.cancelDownload() }
                    )
                }
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
            if (downloadState is ModelDownloader.DownloadState.Complete) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Downloaded!",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.success)
                )
            }
            if (downloadState is ModelDownloader.DownloadState.Error) {
                val errorState = downloadState as ModelDownloader.DownloadState.Error
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorState.message,
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.error)
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
                    scope.launch { ModelDownloader.downloadModel(context, model) }
                },
                onDelete = { model ->
                    ModelDownloader.deleteModel(context, model.id)
                    showModelPicker = false
                    showModelPicker = true
                },
                onDismiss = {
                    showModelPicker = false
                    ModelDownloader.resetState()
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Status ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val batteryText = "${batteryState.batteryLevel}%"
            val chargingIcon = if (batteryState.isCharging) " charging" else ""
            Text(
                text = "Battery: $batteryText$chargingIcon",
                style = ConsoleTheme.body.copy(
                    color = when {
                        batteryState.batteryLevel <= 15 -> ConsoleTheme.error
                        batteryState.batteryLevel <= 30 -> ConsoleTheme.warning
                        else -> ConsoleTheme.text
                    }
                ),
                modifier = Modifier.weight(1f)
            )

            val aiStatusText = when {
                agentState == com.guildofsmiths.trademesh.ai.AgentState.ALIVE -> "[ACTIVE]"
                agentState == com.guildofsmiths.trademesh.ai.AgentState.WAKING -> "[WAKING]"
                agentState == com.guildofsmiths.trademesh.ai.AgentState.RULE_BASED_FALLBACK -> "[RULES]"
                aiStatus == AIStatus.READY -> "[READY]"
                aiStatus == AIStatus.LOADING -> "[LOADING]"
                aiStatus == AIStatus.DEGRADED -> "[DEGRADED]"
                aiStatus == AIStatus.RULE_BASED -> "[RULES]"
                aiStatus == AIStatus.OFFLINE -> "[OFFLINE]"
                aiStatus == AIStatus.DISABLED -> "[OFF]"
                else -> "[OFF]"
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
    }
}

// ════════════════════════════════════════════════════════════════════════════
// WORK MODE SECTION
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun WorkModeSection() {
    var currentMode by remember { mutableStateOf(RoleContext.role) }
    val workModeScope = rememberCoroutineScope()

    val modes = listOf(
        UserRole.SOLO to "Jobs, time tracking, invoicing — just for me.",
        UserRole.TEAM_MEMBER to "I receive tasks from a lead or foreman.",
        UserRole.TEAM_LEAD to "I manage a small crew and assign jobs.",
        UserRole.FOREMAN to "Crew tracking, dispatch, team invoicing.",
        UserRole.GENERAL_CONTRACTOR to "Multiple subs, multiple sites, project oversight.",
    )

    Column {
        Text(text = "WORK MODE", style = ConsoleTheme.captionBold)
        Spacer(modifier = Modifier.height(8.dp))

        modes.forEach { (role, description) ->
            val isSelected = role == currentMode

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) ConsoleTheme.surface else ConsoleTheme.background)
                    .clickable {
                        currentMode = role
                        AuthService.updateUserRole(role.key)
                        workModeScope.launch { AuthService.syncWorkMode(role.key) }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSelected) "(●)" else "(○)",
                    style = ConsoleTheme.bodySmall.copy(
                        color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.textMuted
                    )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = role.displayName,
                        style = ConsoleTheme.bodySmall.copy(
                            color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.text
                        )
                    )
                    Text(
                        text = description,
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Changes the dashboard layout, permissions, and available features.",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// TEAM SECTION — foremen generate invite codes, everyone else joins a team
// ════════════════════════════════════════════════════════════════════════════

private val FOREMAN_TIER = setOf(
    UserRole.FOREMAN,
    UserRole.GENERAL_CONTRACTOR,
    UserRole.ENTERPRISE,
    UserRole.ADMIN,
)

@Composable
private fun TeamSection() {
    val role = RoleContext.role
    if (role in FOREMAN_TIER) {
        ForemanTeamSection()
    } else {
        JoinTeamSection()
    }
}

@Composable
private fun ForemanTeamSection() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val selfId = remember { UserPreferences.getUserId() }
    var invite by remember { mutableStateOf<AuthService.InviteCode?>(null) }
    var members by remember { mutableStateOf<List<AuthService.OrgMember>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var confirmTarget by remember { mutableStateOf<AuthService.OrgMember?>(null) }
    var removing by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        members = AuthService.listOrgMembers() ?: emptyList()
    }

    // Peer foreman = someone else in this org is also a foreman, i.e. the
    // current user joined another foreman's org. Original foremen are the
    // only foreman in their own org and this evaluates false for them.
    val amPeerForeman = members.any { it.id != selfId && it.role == "foreman" }

    Column {
        Text(text = "TEAM", style = ConsoleTheme.captionBold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .clickable(enabled = !loading) {
                    loading = true
                    scope.launch {
                        val fresh = AuthService.createOrgInvite()
                        loading = false
                        if (fresh != null) {
                            invite = fresh
                        } else {
                            Toast.makeText(context, "Could not create invite", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (loading) "[Generating...]" else "[Generate Invite Code]",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.accent)
            )
        }

        invite?.let { inv ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.background)
                    .clickable {
                        clipboard.setText(AnnotatedString(inv.code))
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                    }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = inv.code, style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text))
                Text(text = "[Copy]", style = ConsoleTheme.action.copy(color = ConsoleTheme.accent))
            }
            Text(
                text = "Expires ${inv.expiresAt.take(10)}. One-time use.",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "TEAM MEMBERS", style = ConsoleTheme.captionBold)
        Spacer(modifier = Modifier.height(4.dp))
        if (members.isEmpty()) {
            Text(
                text = "No members yet. Share an invite code to add your crew.",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        } else {
            members.forEach { m ->
                val canRemove = m.id != selfId && m.role != "foreman"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = m.displayName, style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text))
                        Text(text = m.role, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                    }
                    if (canRemove) {
                        Text(
                            text = "[Remove]",
                            style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                            modifier = Modifier.clickable(enabled = !removing) { confirmTarget = m },
                        )
                    }
                }
            }
        }

        if (amPeerForeman) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable(enabled = !leaving) { confirmLeave = true }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Leave this team", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text))
                Text(text = "[Leave team]", style = ConsoleTheme.action.copy(color = ConsoleTheme.accent))
            }
        }
    }

    if (confirmLeave) {
        SmithConfirmDialog(
            title = "Leave team",
            body = "Leave this team? You will become solo and lose access to team-shared work.",
            confirmText = "LEAVE",
            onConfirm = {
                leaving = true
                scope.launch {
                    when (val r = AuthService.leaveOrg()) {
                        is AuthService.LeaveResult.Ok -> {
                            leaving = false
                            confirmLeave = false
                            Toast.makeText(context, "Left team.", Toast.LENGTH_SHORT).show()
                            members = AuthService.listOrgMembers() ?: emptyList()
                        }
                        is AuthService.LeaveResult.Error -> {
                            leaving = false
                            confirmLeave = false
                            Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismiss = { confirmLeave = false },
        )
    }

    confirmTarget?.let { target ->
        SmithConfirmDialog(
            title = "Remove member",
            body = "Remove ${target.displayName} from your team? They will become solo again.",
            confirmText = "REMOVE",
            onConfirm = {
                removing = true
                scope.launch {
                    val ok = AuthService.removeOrgMember(target.id)
                    removing = false
                    confirmTarget = null
                    if (ok) {
                        members = AuthService.listOrgMembers() ?: members
                        Toast.makeText(context, "${target.displayName} removed", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Could not remove member", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { confirmTarget = null },
        )
    }
}

@Composable
private fun JoinTeamSection() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val selfId = remember { UserPreferences.getUserId() }
    var expanded by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var members by remember { mutableStateOf<List<AuthService.OrgMember>>(emptyList()) }
    var loadedMembers by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        members = AuthService.listOrgMembers() ?: emptyList()
        loadedMembers = true
    }

    val inSomeoneElsesOrg = loadedMembers && members.any { it.id != selfId }

    Column {
        if (inSomeoneElsesOrg) {
            Text(text = "YOUR TEAM", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${members.size} member${if (members.size == 1) "" else "s"}",
                    style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text),
                )
                Text(
                    text = "[Leave team]",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                    modifier = Modifier.clickable(enabled = !leaving) { confirmLeave = true },
                )
            }
        } else {
            Text(text = "JOIN A TEAM", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(8.dp))

            if (!expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable { expanded = true; error = null }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Have an invite code?", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text))
                Text(text = "[Enter code]", style = ConsoleTheme.action.copy(color = ConsoleTheme.accent))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(8); error = null },
                    textStyle = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text),
                    cursorBrush = SolidColor(ConsoleTheme.cursor),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (code.isEmpty()) {
                                Text("8-char code", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.textMuted))
                            }
                            inner()
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (loading) "[Joining...]" else "[Join]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable(enabled = !loading && code.length == 8) {
                            loading = true
                            scope.launch {
                                when (val r = AuthService.acceptOrgJoin(code)) {
                                    is AuthService.JoinResult.Ok -> {
                                        loading = false
                                        expanded = false
                                        code = ""
                                        Toast.makeText(context, "Joined team. Welcome!", Toast.LENGTH_SHORT).show()
                                    }
                                    is AuthService.JoinResult.Error -> {
                                        loading = false
                                        error = r.message
                                    }
                                }
                            }
                        }
                    )
                    Text(
                        text = "[Cancel]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier.clickable { expanded = false; code = ""; error = null }
                    )
                }
                error?.let {
                    Text(
                        text = it,
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent)
                    )
                }
            }
            }
        }
    }

    if (confirmLeave) {
        SmithConfirmDialog(
            title = "Leave team",
            body = "Leave this team? You will become solo and lose access to team-shared work.",
            confirmText = "LEAVE",
            onConfirm = {
                leaving = true
                scope.launch {
                    when (val r = AuthService.leaveOrg()) {
                        is AuthService.LeaveResult.Ok -> {
                            leaving = false
                            confirmLeave = false
                            Toast.makeText(context, "Left team.", Toast.LENGTH_SHORT).show()
                            members = AuthService.listOrgMembers() ?: emptyList()
                        }
                        is AuthService.LeaveResult.Error -> {
                            leaving = false
                            confirmLeave = false
                            Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismiss = { confirmLeave = false },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// TRADE SELECTION SECTION — searchable dropdown + secondary tags
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun TradeRoleSection() {
    var primaryTrade by remember { mutableStateOf(UserPreferences.getPrimaryTrade()) }
    var secondaryTrades by remember { mutableStateOf(UserPreferences.getSecondaryTrades()) }
    var searchQuery by remember { mutableStateOf("") }
    var showPrimaryPicker by remember { mutableStateOf(false) }
    var showSecondaryPicker by remember { mutableStateOf(false) }

    Column {
        Text(text = "TRADE", style = ConsoleTheme.captionBold)
        Spacer(modifier = Modifier.height(8.dp))

        // Primary trade
        Text(text = "PRIMARY", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .clickable { showPrimaryPicker = true; searchQuery = "" }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = primaryTrade.ifBlank { "Select your trade" },
                style = ConsoleTheme.bodySmall.copy(
                    color = if (primaryTrade.isBlank()) ConsoleTheme.textMuted else ConsoleTheme.accent
                )
            )
            Text(text = "[Change]", style = ConsoleTheme.action.copy(color = ConsoleTheme.accent))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary trades
        Text(text = "SECONDARY", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        Spacer(modifier = Modifier.height(4.dp))

        if (secondaryTrades.isNotEmpty()) {
            // Show tags
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                secondaryTrades.forEach { trade ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(trade, style = ConsoleTheme.caption.copy(color = ConsoleTheme.text))
                        Text(
                            text = "[x]",
                            style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                            modifier = Modifier.clickable {
                                UserPreferences.removeSecondaryTrade(trade)
                                secondaryTrades = UserPreferences.getSecondaryTrades()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Text(
            text = "[+ Add secondary trade]",
            style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
            modifier = Modifier
                .clickable { showSecondaryPicker = true; searchQuery = "" }
                .padding(vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Trade affects AI suggestions, safety reminders, and material defaults.",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )
    }

    // Primary trade picker dialog
    if (showPrimaryPicker) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showPrimaryPicker = false }) {
            TradePickerContent(
                title = "Primary Trade",
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                selectedTrade = primaryTrade,
                excludeTrades = emptyList(),
                onTradeSelected = { trade ->
                    primaryTrade = trade
                    UserPreferences.setPrimaryTrade(trade)
                    showPrimaryPicker = false
                },
                onDismiss = { showPrimaryPicker = false }
            )
        }
    }

    // Secondary trade picker dialog
    if (showSecondaryPicker) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSecondaryPicker = false }) {
            TradePickerContent(
                title = "Add Secondary Trade",
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                selectedTrade = null,
                excludeTrades = listOf(primaryTrade) + secondaryTrades,
                onTradeSelected = { trade ->
                    UserPreferences.addSecondaryTrade(trade)
                    secondaryTrades = UserPreferences.getSecondaryTrades()
                    showSecondaryPicker = false
                },
                onDismiss = { showSecondaryPicker = false }
            )
        }
    }
}

@Composable
private fun TradePickerContent(
    title: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedTrade: String?,
    excludeTrades: List<String>,
    onTradeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val results = remember(searchQuery, excludeTrades) {
        TradesList.search(searchQuery).filter { it !in excludeTrades }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.background, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = ConsoleTheme.header)
            Text("[x]", style = ConsoleTheme.bodyBold, modifier = Modifier.clickable { onDismiss() })
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f))
                .padding(12.dp)
        ) {
            if (searchQuery.isEmpty()) {
                Text("Search 120+ trades...", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.textMuted))
            }
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                textStyle = ConsoleTheme.bodySmall,
                cursorBrush = SolidColor(ConsoleTheme.cursor),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "${results.size} trades",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Results list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 350.dp)
                .verticalScroll(rememberScrollState())
        ) {
            results.forEach { trade ->
                val isSelected = trade == selectedTrade
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) ConsoleTheme.surface else ConsoleTheme.background)
                        .clickable { onTradeSelected(trade) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSelected) "(●)" else "(○)",
                        style = ConsoleTheme.caption.copy(
                            color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.textMuted
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = trade,
                        style = ConsoleTheme.bodySmall.copy(
                            color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.text
                        )
                    )
                }
            }
        }
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
    SettingsScreen(
        onBackClick = { },
        onNameChanged = { }
    )
}
