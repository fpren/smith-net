package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine

/**
 * Settings screen — bold, clean.
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNameChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var userName by remember { mutableStateOf(UserPreferences.getUserName()) }
    var hasChanges by remember { mutableStateOf(false) }
    val isScanning by BoundaryEngine.isScanning.collectAsState()
    val isMeshConnected by BoundaryEngine.isMeshConnected.collectAsState()
    val isGatewayConnected by BoundaryEngine.isGatewayConnected.collectAsState()
    var gatewayUrl by remember { mutableStateOf("ws://192.168.8.163:3000") }
    
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
                .padding(20.dp)
        ) {
            // User ID
            Text(text = "USER ID", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = UserPreferences.getUserId(),
                style = ConsoleTheme.body,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(14.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Display name
            Text(text = "DISPLAY NAME", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(6.dp))
            
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
                    .padding(14.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (userName.isEmpty()) {
                            Text(
                                text = "Enter name",
                                style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (hasChanges && userName.trim().length >= 2) {
                Text(
                    text = "SAVE",
                    style = ConsoleTheme.action,
                    modifier = Modifier
                        .clickable {
                            UserPreferences.setUserName(userName.trim())
                            onNameChanged(userName.trim())
                            hasChanges = false
                        }
                        .padding(vertical = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            ConsoleSeparator()
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Mesh Connection
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
            
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Scanning discovers nearby peers and enables messaging",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            ConsoleSeparator()
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Gateway Connection
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
                
                // Only show CONNECT button when not connected
                // Disconnect is controlled from dashboard (admin only)
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
            
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Relay bridges mesh messages to online backend",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            ConsoleSeparator()
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // About
            Text(text = "ABOUT", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${ConsoleTheme.APP_NAME} v${ConsoleTheme.APP_VERSION}",
                style = ConsoleTheme.bodyBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "build: ${ConsoleTheme.BUILD_HASH}",
                style = ConsoleTheme.caption
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "BLE mesh communication prototype", style = ConsoleTheme.bodySmall)
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = "made by ${ConsoleTheme.STUDIO}",
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
