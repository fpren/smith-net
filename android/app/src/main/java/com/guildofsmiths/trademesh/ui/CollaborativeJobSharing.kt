package com.guildofsmiths.trademesh.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.CollaborationMode
import com.guildofsmiths.trademesh.data.ConnectionType
import com.guildofsmiths.trademesh.data.UnifiedPeer
import com.guildofsmiths.trademesh.data.UnifiedPeerRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.jobboard.Job

/**
 * Collaborative Job Sharing Screen
 * Allows selecting peers to collaborate on a job
 */
@Composable
fun CollaborativeJobSharingScreen(
    job: Job,
    onCollaboratorsSelected: (List<String>, CollaborationMode) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUserId = UserPreferences.getUserId()
    val availablePeers by UnifiedPeerRepository.availablePeers.collectAsState()

    var selectedPeers by remember { mutableStateOf(setOf<String>()) }
    var collaborationMode by remember { mutableStateOf(CollaborationMode.TEAM_EFFORT) }

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
            Text(text = "SHARE JOB COLLABORATIVELY", style = ConsoleTheme.title)
        }

        ConsoleSeparator()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Job info
            Text(text = "JOB: ${job.title}", style = ConsoleTheme.title)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = job.description, style = ConsoleTheme.body)
            Spacer(modifier = Modifier.height(16.dp))

            // Collaboration mode selection
            Text(text = "COLLABORATION MODE", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(10.dp))

            CollaborationModeSelector(
                selectedMode = collaborationMode,
                onModeSelected = { collaborationMode = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Available peers
            Text(
                text = "SELECT COLLABORATORS (${availablePeers.size} available)",
                style = ConsoleTheme.captionBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (availablePeers.isEmpty()) {
                Text(
                    text = "No peers available for collaboration",
                    style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Group peers by connection type for better UX
                    val hybridPeers = availablePeers.filter { it.connectionType == ConnectionType.HYBRID }
                    val onlinePeers = availablePeers.filter { it.connectionType == ConnectionType.ONLINE_ONLY }
                    val meshPeers = availablePeers.filter { it.connectionType == ConnectionType.MESH_ONLY }

                    if (hybridPeers.isNotEmpty()) {
                        item { Text("🟢 HYBRID CONNECTED", style = ConsoleTheme.captionBold) }
                        items(hybridPeers) { peer ->
                            UniversalPeerRow(peer, selectedPeers.contains(peer.userId)) { peerId ->
                                selectedPeers = if (peerId in selectedPeers) {
                                    selectedPeers - peerId
                                } else {
                                    selectedPeers + peerId
                                }
                            }
                        }
                    }

                    if (onlinePeers.isNotEmpty()) {
                        item { Text("🔵 ONLINE ONLY", style = ConsoleTheme.captionBold) }
                        items(onlinePeers) { peer ->
                            UniversalPeerRow(peer, selectedPeers.contains(peer.userId)) { peerId ->
                                selectedPeers = if (peerId in selectedPeers) {
                                    selectedPeers - peerId
                                } else {
                                    selectedPeers + peerId
                                }
                            }
                        }
                    }

                    if (meshPeers.isNotEmpty()) {
                        item { Text("📶 MESH ONLY", style = ConsoleTheme.captionBold) }
                        items(meshPeers) { peer ->
                            UniversalPeerRow(peer, selectedPeers.contains(peer.userId)) { peerId ->
                                selectedPeers = if (peerId in selectedPeers) {
                                    selectedPeers - peerId
                                } else {
                                    selectedPeers + peerId
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Share button
            if (selectedPeers.isNotEmpty()) {
                Text(
                    text = "SHARE JOB WITH ${selectedPeers.size} PEER${if (selectedPeers.size > 1) "S" else ""} →",
                    style = ConsoleTheme.action,
                    modifier = Modifier
                        .clickable {
                            onCollaboratorsSelected(selectedPeers.toList(), collaborationMode)
                        }
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}

/**
 * Universal peer row showing connection status
 */
@Composable
private fun UniversalPeerRow(
    peer: UnifiedPeer,
    isSelected: Boolean,
    onToggle: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onToggle(peer.userId) })
            .background(
                if (isSelected) ConsoleTheme.accent.copy(alpha = 0.1f)
                else ConsoleTheme.surface
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Connection status indicators
        ConnectionStatusIcons(peer.connectionType, peer.meshRssi)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(peer.userName, style = ConsoleTheme.bodyBold)

            // Connection details and availability
            val statusText = when (peer.connectionType) {
                ConnectionType.HYBRID -> "Online + Mesh (${peer.meshRssi ?: "?"}dBm)"
                ConnectionType.ONLINE_ONLY -> "Online only"
                ConnectionType.MESH_ONLY -> "Mesh only (${peer.meshRssi ?: "?"}dBm)"
                ConnectionType.OFFLINE -> "Offline"
            }
            Text(statusText, style = ConsoleTheme.caption)

            // Availability status
            if (!peer.isAvailableForJobs) {
                Text("⏸️ Busy (${peer.currentJobCount} jobs)", style = ConsoleTheme.caption, color = ConsoleTheme.warning)
            } else if (peer.currentJobCount > 0) {
                Text("✅ Available (${peer.currentJobCount} active)", style = ConsoleTheme.caption, color = ConsoleTheme.success)
            } else {
                Text("✅ Available", style = ConsoleTheme.caption, color = ConsoleTheme.success)
            }
        }

        if (isSelected) {
            Text("✓", style = ConsoleTheme.bodyBold, color = ConsoleTheme.accent)
        }
    }
}

/**
 * Connection status icons
 */
@Composable
private fun ConnectionStatusIcons(connectionType: ConnectionType, rssi: Int?) {
    when (connectionType) {
        ConnectionType.HYBRID -> {
            // Both online and mesh indicators
            Row {
                StatusDot(Color.Green)  // Online
                Spacer(modifier = Modifier.width(2.dp))
                StatusDot(getMeshSignalColor(rssi))  // Mesh
            }
        }
        ConnectionType.ONLINE_ONLY -> StatusDot(Color.Blue)
        ConnectionType.MESH_ONLY -> StatusDot(getMeshSignalColor(rssi))
        ConnectionType.OFFLINE -> StatusDot(Color.Gray)
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

private fun getMeshSignalColor(rssi: Int?): Color {
    return when {
        rssi == null -> Color.Gray
        rssi >= -50 -> Color.Green
        rssi >= -60 -> ConsoleTheme.accent
        rssi >= -70 -> Color.Yellow
        else -> Color.Red
    }
}

/**
 * Collaboration mode selector
 */
@Composable
private fun CollaborationModeSelector(
    selectedMode: CollaborationMode,
    onModeSelected: (CollaborationMode) -> Unit
) {
    Column {
        CollaborationModeOption(
            mode = CollaborationMode.TEAM_EFFORT,
            selected = selectedMode == CollaborationMode.TEAM_EFFORT,
            title = "TEAM EFFORT",
            description = "All work together as one team",
            onSelect = { onModeSelected(CollaborationMode.TEAM_EFFORT) }
        )

        CollaborationModeOption(
            mode = CollaborationMode.DIVIDED_LABOR,
            selected = selectedMode == CollaborationMode.DIVIDED_LABOR,
            title = "DIVIDED LABOR",
            description = "Work divided into specific tasks",
            onSelect = { onModeSelected(CollaborationMode.DIVIDED_LABOR) }
        )

        CollaborationModeOption(
            mode = CollaborationMode.SUPERVISED,
            selected = selectedMode == CollaborationMode.SUPERVISED,
            title = "SUPERVISED",
            description = "Lead supervises, others execute",
            onSelect = { onModeSelected(CollaborationMode.SUPERVISED) }
        )
    }
}

@Composable
private fun CollaborationModeOption(
    mode: CollaborationMode,
    selected: Boolean,
    title: String,
    description: String,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(
                if (selected) ConsoleTheme.accent.copy(alpha = 0.1f)
                else ConsoleTheme.surface
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selected) "☑" else "☐",
            style = ConsoleTheme.body,
            color = if (selected) ConsoleTheme.accent else ConsoleTheme.text
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = ConsoleTheme.bodyBold)
            Text(description, style = ConsoleTheme.caption)
        }
    }
}