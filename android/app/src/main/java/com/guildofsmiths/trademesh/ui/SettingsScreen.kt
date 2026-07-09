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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithConfirmDialog
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import com.guildofsmiths.trademesh.ui.theme2.ThemePreference
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
    onThemePreferenceChange: (ThemePreference) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalSmithColors.current
    var userName by remember { mutableStateOf(UserPreferences.getUserName()) }
    var hasChanges by remember { mutableStateOf(false) }
    var themePreference by remember { mutableStateOf(UserPreferences.getThemePreference()) }
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
            .background(colors.bgBase)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
                .clickable(onClick = onBackClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "←", style = SmithType.title.copy(color = colors.ink))
            Spacer(modifier = Modifier.width(14.dp))
            Text(text = "SETTINGS", style = SmithType.title.copy(color = colors.ink))
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
                    .background(colors.bgPanel)
                    .clickable { onProfileClick?.invoke() }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = userName.ifBlank { "Set up profile" }, style = SmithType.bodyBold.copy(color = colors.ink))
                    Text(
                        text = "Name, trade, rates, billing",
                        style = SmithType.caption.copy(color = colors.inkMuted)
                    )
                }
                Text(text = ">", style = SmithType.body, color = colors.inkMuted)
            }

            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))

            // ════════════════════════════════════════════════════════════════
            // APPEARANCE — Light / Dark / System
            // ════════════════════════════════════════════════════════════════
            AppearanceSection(
                current = themePreference,
                onSelect = { pref ->
                    themePreference = pref
                    UserPreferences.setThemePreference(pref)
                    onThemePreferenceChange(pref)
                }
            )

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
            Text(text = "MESH CONNECTION", style = SmithType.captionBold.copy(color = colors.inkMuted))
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isScanning -> colors.statusOnline
                                isMeshConnected -> colors.attention
                                else -> colors.inkMuted
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
                    style = SmithType.body.copy(color = colors.ink),
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = if (isScanning) "STOP" else "START",
                    style = SmithType.action.copy(
                        color = if (isScanning) colors.inkMuted else colors.accent
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
            Text(text = "GATEWAY RELAY", style = SmithType.captionBold.copy(color = colors.inkMuted))
            Spacer(modifier = Modifier.height(10.dp))
            
            BasicTextField(
                value = gatewayUrl,
                onValueChange = { gatewayUrl = it },
                textStyle = SmithType.bodySmall.copy(color = colors.inkMuted),
                cursorBrush = SolidColor(colors.ink),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel)
                    .padding(14.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (gatewayUrl.isEmpty()) {
                            Text(
                                text = "ws://ip:port",
                                style = SmithType.bodySmall.copy(color = colors.inkMuted)
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
                    .background(colors.bgPanel)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGatewayConnected) colors.statusOnline else colors.inkMuted
                        )
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = if (isGatewayConnected) "Connected to backend" else "Offline",
                    style = SmithType.body.copy(color = colors.ink),
                    modifier = Modifier.weight(1f)
                )
                
                if (!isGatewayConnected) {
                    Text(
                        text = "CONNECT",
                        style = SmithType.action.copy(color = colors.accent),
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
            Text(text = "ABOUT", style = SmithType.captionBold.copy(color = colors.inkMuted))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${ConsoleTheme.APP_NAME} v${ConsoleTheme.APP_VERSION}",
                style = SmithType.bodyBold.copy(color = colors.ink)
            )
            Text(
                text = "build: ${ConsoleTheme.BUILD_HASH}",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "made by ${ConsoleTheme.STUDIO}",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(12.dp))
            
            // ════════════════════════════════════════════════════════════════
            // ACCOUNT ACTIONS
            // ════════════════════════════════════════════════════════════════
            Text(text = "ACCOUNT", style = SmithType.captionBold.copy(color = colors.inkMuted))
            Spacer(modifier = Modifier.height(8.dp))

            // ════════════════════════════════════════════════════════════════
            // LOCATION SHARING (GPS · clock-in validation · lost & found)
            // ════════════════════════════════════════════════════════════════
            val locState by com.guildofsmiths.trademesh.data.LocationSharingPreferences.state.collectAsState()
            Text(
                text = "LOCATION SHARING",
                style = SmithType.caption.copy(color = colors.inkMuted),
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (locState.enabled) colors.bgPanel else colors.bgBase)
                    .clickable { com.guildofsmiths.trademesh.data.LocationSharingPreferences.setEnabled(!locState.enabled) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (locState.enabled) "((●))" else "((○))",
                    style = SmithType.bodySmall.copy(
                        color = if (locState.enabled) colors.accent else colors.inkMuted
                    )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Share my location while clocked in",
                        style = SmithType.bodySmall.copy(
                            color = if (locState.enabled) colors.accent else colors.ink
                        )
                    )
                    Text(
                        "Powers clock-in geofence validation and Lost & Found.",
                        style = SmithType.caption.copy(color = colors.inkMuted)
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
                            .background(if (sel) colors.accent else colors.bgPanel, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .border(0.5.dp, colors.ink.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
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
                        Text(short, style = SmithType.caption.copy(color = if (sel) colors.inkOnAccent else colors.inkMuted))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Forget my trail
            val forgetScope = rememberCoroutineScope()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel)
                    .clickable {
                        forgetScope.launch {
                            com.guildofsmiths.trademesh.data.LocationTrailRepository.forgetTrail(UserPreferences.getUserId())
                        }
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "[x]", style = SmithType.bodyBold.copy(color = colors.statusError))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Forget my trail", style = SmithType.body.copy(color = colors.ink))
                    Text(
                        "Deletes all GPS points stored on this device for your user ID.",
                        style = SmithType.caption.copy(color = colors.inkMuted)
                    )
                }
                Text(text = ">", style = SmithType.body.copy(color = colors.ink))
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Sign Out Button
            val signOutScope = rememberCoroutineScope()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel)
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
                Text(text = "[↪]", style = SmithType.bodyBold.copy(color = colors.ink))
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "SIGN OUT", style = SmithType.body.copy(color = colors.ink), modifier = Modifier.weight(1f))
                Text(text = ">", style = SmithType.body.copy(color = colors.ink))
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Close App Button
            val closeContext = LocalContext.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel)
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
                Text(text = "[✕]", style = SmithType.bodyBold.copy(color = colors.statusError))
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "CLOSE APP", style = SmithType.body.copy(color = colors.statusError), modifier = Modifier.weight(1f))
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
    val colors = LocalSmithColors.current
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

    Text(text = "PRIVACY", style = SmithType.captionBold.copy(color = colors.inkMuted))
    Spacer(modifier = Modifier.height(10.dp))

    val publicId = user?.publicId
    val formattedId = publicId?.let { formatPublicId(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPanel)
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
                style = SmithType.captionBold.copy(color = colors.inkOnAccent, fontSize = 11.sp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(colors.accent, androidx.compose.foundation.shape.CircleShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Your SmithNet ID", style = SmithType.caption.copy(color = colors.inkMuted))
            Text(
                text = formattedId ?: if (offline) "— (sign in to see)" else "—",
                style = SmithType.bodyBold.copy(color = colors.ink)
            )
        }
        if (formattedId != null) {
            Text(
                text = "[Copy]",
                style = SmithType.action.copy(color = colors.accent),
                modifier = Modifier
                    .clickable {
                        clipboard.setText(AnnotatedString(formattedId))
                        Toast.makeText(context, "ID copied", Toast.LENGTH_SHORT).show()
                    }
                    .padding(8.dp)
            )
            Text(
                text = if (showQr) "[Hide]" else "[QR]",
                style = SmithType.action.copy(color = colors.accent),
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
    Text("WHO CAN FIND ME", style = SmithType.caption.copy(color = colors.inkMuted))
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
                .background(if (isSelected) colors.bgPanel else colors.bgBase)
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
                style = SmithType.bodySmall.copy(
                    color = if (isSelected) colors.accent else colors.inkMuted
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = SmithType.bodySmall.copy(
                        color = if (isSelected) colors.accent else colors.ink
                    )
                )
                Text(
                    text = description,
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }
        }
    }

    if (offline) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Offline — privacy changes sync when you reconnect.",
            style = SmithType.caption.copy(color = colors.inkMuted)
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
    val colors = LocalSmithColors.current
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
        Text(text = "SMITHAI", style = SmithType.captionBold.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(8.dp))

        // ── Enable Toggle ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
                .clickable {
                    aiEnabled = !aiEnabled
                    AIRouter.setEnabled(aiEnabled)
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Enable SmithAI", style = SmithType.body.copy(color = colors.ink), modifier = Modifier.weight(1f))
            Text(
                text = if (aiEnabled) "[ON]" else "[OFF]",
                style = SmithType.bodyBold.copy(
                    color = if (aiEnabled) colors.statusOnline else colors.inkMuted
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Mode: Auto / Approve / Off ──
        Text(text = "MODE", style = SmithType.caption.copy(color = colors.inkMuted),
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
                            if (isSelected) colors.accent.copy(alpha = 0.10f) else colors.bgPanel
                        )
                        .border(
                            0.5.dp,
                            if (isSelected) colors.accent else colors.ink.copy(alpha = 0.06f),
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
                        style = SmithType.captionBold.copy(
                            color = if (isSelected) colors.accent else colors.inkMuted
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
            style = SmithType.caption.copy(color = colors.inkMuted)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Cloud Connection ──
        Text(text = "CLOUD", style = SmithType.caption.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(4.dp))

        if (!showKeyField) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel)
                    .clickable { showKeyField = true; keyInput = apiKey }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (apiKey.isNotBlank()) maskedKey else "Not set",
                    style = SmithType.bodySmall.copy(
                        color = if (apiKey.isNotBlank()) colors.ink else colors.inkMuted
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (apiKey.isNotBlank()) {
                        Text("Connected", style = SmithType.caption.copy(color = colors.statusOnline))
                    }
                    Text("[Edit]", style = SmithType.action.copy(color = colors.accent))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    textStyle = SmithType.bodySmall.copy(color = colors.inkMuted),
                    cursorBrush = SolidColor(colors.ink),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (keyInput.isEmpty()) {
                                Text("sk-or-v1-...", style = SmithType.bodySmall.copy(color = colors.inkMuted))
                            }
                            inner()
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "[Save]",
                        style = SmithType.action.copy(color = colors.accent),
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
                        style = SmithType.action.copy(color = colors.inkMuted),
                        modifier = Modifier.clickable { showKeyField = false }
                    )
                }
            }
        }
        if (testResult != null) {
            Text(
                testResult!!,
                style = SmithType.caption.copy(
                    color = if (testResult == "Connected") colors.statusOnline else colors.statusError
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Cloud Model picker ──
        Text(text = "MODEL", style = SmithType.caption.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(4.dp))

        if (!modelEditing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel)
                    .clickable { modelEditing = true; modelInput = cloudModel }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (cloudModel.isNotBlank()) cloudModel else "Provider default",
                    style = SmithType.bodySmall.copy(
                        color = if (cloudModel.isNotBlank()) colors.ink else colors.inkMuted
                    )
                )
                Text("[Edit]", style = SmithType.action.copy(color = colors.accent))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel)
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
                Text("quick pick", style = SmithType.caption.copy(color = colors.inkMuted))
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
                            style = SmithType.action.copy(
                                color = if (modelInput == slug) colors.accent else colors.inkMuted
                            ),
                            modifier = Modifier
                                .clickable { modelInput = slug }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
                Text("or paste a model id", style = SmithType.caption.copy(color = colors.inkMuted))
                BasicTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    textStyle = SmithType.bodySmall.copy(color = colors.inkMuted),
                    cursorBrush = SolidColor(colors.ink),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (modelInput.isEmpty()) {
                                Text("provider/model-id", style = SmithType.bodySmall.copy(color = colors.inkMuted))
                            }
                            inner()
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "[Save]",
                        style = SmithType.action.copy(color = colors.accent),
                        modifier = Modifier.clickable {
                            UserPreferences.setCloudModel(modelInput.trim())
                            cloudModel = modelInput.trim()
                            modelEditing = false
                        }
                    )
                    Text(
                        "[Default]",
                        style = SmithType.action.copy(color = colors.inkMuted),
                        modifier = Modifier.clickable {
                            UserPreferences.setCloudModel("")
                            cloudModel = ""
                            modelEditing = false
                        }
                    )
                    Text(
                        "[Cancel]",
                        style = SmithType.action.copy(color = colors.inkMuted),
                        modifier = Modifier.clickable { modelEditing = false }
                    )
                }
                Text(
                    "Model id is sent to your provider. OpenRouter accepts any of these. Different providers (sk-, sk-or-, xai-) honor different ids — pick one your key supports.",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Offline Model ──
        Text(text = "OFFLINE", style = SmithType.caption.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
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
                    agentState == com.guildofsmiths.trademesh.ai.AgentState.ALIVE -> colors.statusOnline
                    agentState == com.guildofsmiths.trademesh.ai.AgentState.WAKING -> colors.attention
                    agentState == com.guildofsmiths.trademesh.ai.AgentState.RULE_BASED_FALLBACK -> colors.accent
                    modelState == ModelState.READY -> colors.accent
                    modelState == ModelState.LOADING || isDownloading -> colors.attention
                    modelState == ModelState.ERROR -> colors.statusError
                    modelDownloaded -> colors.accent
                    else -> colors.inkMuted
                }

                Text(
                    text = statusText,
                    style = SmithType.body.copy(color = statusColor),
                    modifier = Modifier.weight(1f)
                )

                if (!isDownloading) {
                    Text(
                        text = if (modelDownloaded) "[MODELS]" else "[DOWNLOAD]",
                        style = SmithType.action.copy(color = colors.accent),
                        modifier = Modifier.clickable { showModelPicker = true }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (modelDownloaded && modelState == ModelState.NOT_LOADED && aiEnabled && !isDownloading) {
                    Text(
                        text = "[LOAD]",
                        style = SmithType.action.copy(color = colors.statusOnline),
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
                        style = SmithType.action.copy(color = colors.statusError),
                        modifier = Modifier.clickable { ModelDownloader.cancelDownload() }
                    )
                }
                if (modelState == ModelState.READY || agentState == com.guildofsmiths.trademesh.ai.AgentState.ALIVE) {
                    Text(
                        text = "[UNLOAD]",
                        style = SmithType.action.copy(color = colors.inkMuted),
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
                    color = colors.accent,
                    trackColor = colors.inkMuted.copy(alpha = 0.3f)
                )
                Text(
                    text = "${(downloadProgress * 100).toInt()}% (${dlState?.model?.sizeDisplay ?: ""})",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }
            if (agentState == com.guildofsmiths.trademesh.ai.AgentState.WAKING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = agentInitProgress,
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.statusOnline,
                    trackColor = colors.inkMuted.copy(alpha = 0.3f)
                )
                Text(
                    text = "Initializing agent context...",
                    style = SmithType.caption.copy(color = colors.statusOnline)
                )
            }
            if (downloadState is ModelDownloader.DownloadState.Complete) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Downloaded!",
                    style = SmithType.caption.copy(color = colors.statusOnline)
                )
            }
            if (downloadState is ModelDownloader.DownloadState.Error) {
                val errorState = downloadState as ModelDownloader.DownloadState.Error
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorState.message,
                    style = SmithType.caption.copy(color = colors.statusError)
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
                .background(colors.bgPanel)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val batteryText = "${batteryState.batteryLevel}%"
            val chargingIcon = if (batteryState.isCharging) " charging" else ""
            Text(
                text = "Battery: $batteryText$chargingIcon",
                style = SmithType.body.copy(
                    color = when {
                        batteryState.batteryLevel <= 15 -> colors.statusError
                        batteryState.batteryLevel <= 30 -> colors.attention
                        else -> colors.ink
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
                AIStatus.READY -> colors.statusOnline
                AIStatus.LOADING -> colors.attention
                AIStatus.DEGRADED -> colors.attention
                AIStatus.RULE_BASED -> colors.accent
                AIStatus.OFFLINE, AIStatus.DISABLED -> colors.inkMuted
            }
            Text(
                text = aiStatusText,
                style = SmithType.bodyBold.copy(color = aiStatusColor)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Auto-degrade toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
                .clickable {
                    autoDegradeEnabled = !autoDegradeEnabled
                    BatteryGate.setAutoDegradeEnabled(autoDegradeEnabled)
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Auto-degrade on low battery", style = SmithType.body.copy(color = colors.ink), modifier = Modifier.weight(1f))
            Text(
                text = if (autoDegradeEnabled) "[ON]" else "[OFF]",
                style = SmithType.bodyBold.copy(
                    color = if (autoDegradeEnabled) colors.statusOnline else colors.inkMuted
                )
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// APPEARANCE SECTION — Light / Dark / System segmented control
// ════════════════════════════════════════════════════════════════════════════

private val ThemeOptions = listOf(
    ThemePreference.LIGHT to "LIGHT",
    ThemePreference.DARK to "DARK",
    ThemePreference.SYSTEM to "SYSTEM",
)

@Composable
private fun AppearanceSection(
    current: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
) {
    val colors = LocalSmithColors.current

    Column {
        Text(text = "APPEARANCE", style = SmithType.captionBold.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeOptions.forEach { (pref, label) ->
                val isSelected = pref == current
                Text(
                    text = label,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontFamily = ConsoleTheme.jetBrainsMono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        color = if (isSelected) colors.inkOnAccent else colors.inkMuted
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (isSelected) {
                                Modifier.background(colors.accent)
                            } else {
                                Modifier.border(1.dp, colors.line, RoundedCornerShape(4.dp))
                            }
                        )
                        .clickable { onSelect(pref) }
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// WORK MODE SECTION
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun WorkModeSection() {
    val colors = LocalSmithColors.current
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
        Text(text = "WORK MODE", style = SmithType.captionBold.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(8.dp))

        modes.forEach { (role, description) ->
            val isSelected = role == currentMode

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) colors.bgPanel else colors.bgBase)
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
                    style = SmithType.bodySmall.copy(
                        color = if (isSelected) colors.accent else colors.inkMuted
                    )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = role.displayName,
                        style = SmithType.bodySmall.copy(
                            color = if (isSelected) colors.accent else colors.ink
                        )
                    )
                    Text(
                        text = description,
                        style = SmithType.caption.copy(color = colors.inkMuted)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Changes the dashboard layout, permissions, and available features.",
            style = SmithType.caption.copy(color = colors.inkMuted)
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
    val colors = LocalSmithColors.current
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
        Text(text = "TEAM", style = SmithType.captionBold.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
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
                style = SmithType.action.copy(color = colors.accent)
            )
        }

        invite?.let { inv ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgBase)
                    .clickable {
                        clipboard.setText(AnnotatedString(inv.code))
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                    }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = inv.code, style = SmithType.bodySmall.copy(color = colors.ink))
                Text(text = "[Copy]", style = SmithType.action.copy(color = colors.accent))
            }
            Text(
                text = "Expires ${inv.expiresAt.take(10)}. One-time use.",
                style = SmithType.caption.copy(color = colors.inkMuted),
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "TEAM MEMBERS", style = SmithType.captionBold.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(4.dp))
        if (members.isEmpty()) {
            Text(
                text = "No members yet. Share an invite code to add your crew.",
                style = SmithType.caption.copy(color = colors.inkMuted),
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
                        Text(text = m.displayName, style = SmithType.bodySmall.copy(color = colors.ink))
                        Text(text = m.role, style = SmithType.caption.copy(color = colors.inkMuted))
                    }
                    if (canRemove) {
                        Text(
                            text = "[Remove]",
                            style = SmithType.action.copy(color = colors.accent),
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
                    .background(colors.bgPanel)
                    .clickable(enabled = !leaving) { confirmLeave = true }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Leave this team", style = SmithType.bodySmall.copy(color = colors.ink))
                Text(text = "[Leave team]", style = SmithType.action.copy(color = colors.accent))
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
            confirmEnabled = !leaving,
        )
    }

    confirmTarget?.let { target ->
        SmithConfirmDialog(
            title = "Remove member",
            body = "Remove ${target.displayName} from your team? They will become solo again.",
            confirmText = "REMOVE",
            confirmEnabled = !removing,
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
    val colors = LocalSmithColors.current
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
            Text(text = "YOUR TEAM", style = SmithType.captionBold.copy(color = colors.inkMuted))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${members.size} member${if (members.size == 1) "" else "s"}",
                    style = SmithType.bodySmall.copy(color = colors.ink),
                )
                Text(
                    text = "[Leave team]",
                    style = SmithType.action.copy(color = colors.accent),
                    modifier = Modifier.clickable(enabled = !leaving) { confirmLeave = true },
                )
            }
        } else {
            Text(text = "JOIN A TEAM", style = SmithType.captionBold.copy(color = colors.inkMuted))
            Spacer(modifier = Modifier.height(8.dp))

            if (!expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel)
                    .clickable { expanded = true; error = null }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Have an invite code?", style = SmithType.bodySmall.copy(color = colors.ink))
                Text(text = "[Enter code]", style = SmithType.action.copy(color = colors.accent))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(8); error = null },
                    textStyle = SmithType.bodySmall.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.ink),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (code.isEmpty()) {
                                Text("8-char code", style = SmithType.bodySmall.copy(color = colors.inkMuted))
                            }
                            inner()
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (loading) "[Joining...]" else "[Join]",
                        style = SmithType.action.copy(color = colors.accent),
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
                        style = SmithType.action.copy(color = colors.inkMuted),
                        modifier = Modifier.clickable { expanded = false; code = ""; error = null }
                    )
                }
                error?.let {
                    Text(
                        text = it,
                        style = SmithType.caption.copy(color = colors.accent)
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
            confirmEnabled = !leaving,
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
    val colors = LocalSmithColors.current
    var primaryTrade by remember { mutableStateOf(UserPreferences.getPrimaryTrade()) }
    var secondaryTrades by remember { mutableStateOf(UserPreferences.getSecondaryTrades()) }
    var searchQuery by remember { mutableStateOf("") }
    var showPrimaryPicker by remember { mutableStateOf(false) }
    var showSecondaryPicker by remember { mutableStateOf(false) }

    Column {
        Text(text = "TRADE", style = SmithType.captionBold.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(8.dp))

        // Primary trade
        Text(text = "PRIMARY", style = SmithType.caption.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
                .clickable { showPrimaryPicker = true; searchQuery = "" }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = primaryTrade.ifBlank { "Select your trade" },
                style = SmithType.bodySmall.copy(
                    color = if (primaryTrade.isBlank()) colors.inkMuted else colors.accent
                )
            )
            Text(text = "[Change]", style = SmithType.action.copy(color = colors.accent))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary trades
        Text(text = "SECONDARY", style = SmithType.caption.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(4.dp))

        if (secondaryTrades.isNotEmpty()) {
            // Show tags
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                secondaryTrades.forEach { trade ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bgPanel)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(trade, style = SmithType.caption.copy(color = colors.ink))
                        Text(
                            text = "[x]",
                            style = SmithType.action.copy(color = colors.inkMuted),
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
            style = SmithType.action.copy(color = colors.accent),
            modifier = Modifier
                .clickable { showSecondaryPicker = true; searchQuery = "" }
                .padding(vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Trade affects AI suggestions, safety reminders, and material defaults.",
            style = SmithType.caption.copy(color = colors.inkMuted)
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
    val colors = LocalSmithColors.current
    val results = remember(searchQuery, excludeTrades) {
        TradesList.search(searchQuery).filter { it !in excludeTrades }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgBase, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = SmithType.header.copy(color = colors.ink))
            Text("[x]", style = SmithType.bodyBold.copy(color = colors.ink), modifier = Modifier.clickable { onDismiss() })
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
                .border(0.5.dp, colors.ink.copy(alpha = 0.06f))
                .padding(12.dp)
        ) {
            if (searchQuery.isEmpty()) {
                Text("Search 120+ trades...", style = SmithType.bodySmall.copy(color = colors.inkMuted))
            }
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                textStyle = SmithType.bodySmall.copy(color = colors.inkMuted),
                cursorBrush = SolidColor(colors.ink),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "${results.size} trades",
            style = SmithType.caption.copy(color = colors.inkMuted)
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
                        .background(if (isSelected) colors.bgPanel else colors.bgBase)
                        .clickable { onTradeSelected(trade) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSelected) "(●)" else "(○)",
                        style = SmithType.caption.copy(
                            color = if (isSelected) colors.accent else colors.inkMuted
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = trade,
                        style = SmithType.bodySmall.copy(
                            color = if (isSelected) colors.accent else colors.ink
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
    val colors = LocalSmithColors.current
    val isDownloading = downloadState is ModelDownloader.DownloadState.Downloading
    val downloadingModelId = (downloadState as? ModelDownloader.DownloadState.Downloading)?.model?.id
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase.copy(alpha = 0.95f))
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
                    style = SmithType.header.copy(color = colors.ink)
                )
                if (!isDownloading) {
                    Text(
                        text = "[✕]",
                        style = SmithType.bodyBold.copy(color = colors.ink),
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Select a model to download. Larger models = better quality but slower.",
                style = SmithType.caption.copy(color = colors.inkMuted)
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
                            if (model.recommended) colors.bgPanel else colors.bgBase
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
                                    style = SmithType.bodyBold.copy(color = colors.ink)
                                )
                                if (model.recommended) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "[★ RECOMMENDED]",
                                        style = SmithType.caption.copy(color = colors.accent)
                                    )
                                }
                            }
                            Text(
                                text = model.description,
                                style = SmithType.caption.copy(color = colors.inkMuted)
                            )
                        }
                        
                        // Size
                        Text(
                            text = model.sizeDisplay,
                            style = SmithType.caption.copy(color = colors.inkMuted)
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
                                        color = colors.accent,
                                        trackColor = colors.inkMuted.copy(alpha = 0.3f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${(downloadProgress * 100).toInt()}%",
                                            style = SmithType.caption.copy(color = colors.attention)
                                        )
                                        Text(
                                            text = "[CANCEL]",
                                            style = SmithType.action.copy(color = colors.statusError),
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
                                    style = SmithType.caption.copy(color = colors.statusOnline)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "[DELETE]",
                                    style = SmithType.action.copy(color = colors.statusError),
                                    modifier = Modifier.clickable {
                                        onDelete(model)
                                    }
                                )
                            }
                            isDownloading -> {
                                // Another model is downloading
                                Text(
                                    text = "[WAITING]",
                                    style = SmithType.caption.copy(color = colors.inkMuted)
                                )
                            }
                            else -> {
                                Text(
                                    text = "[DOWNLOAD]",
                                    style = SmithType.action.copy(color = colors.accent),
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
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
            Text(
                text = "Requires WiFi connection. Download may take several minutes.",
                style = SmithType.caption.copy(color = colors.inkMuted)
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
