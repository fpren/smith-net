package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.ClientInfo
import com.guildofsmiths.trademesh.data.ClientRepository
import com.guildofsmiths.trademesh.data.Colleague
import com.guildofsmiths.trademesh.data.ColleagueRepository
import com.guildofsmiths.trademesh.data.Peer
import com.guildofsmiths.trademesh.data.PeerRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.ui.jobboard.Job

/**
 * NewConversationScreen — contact picker for starting new conversations.
 *
 * Shows three sections: Clients (from jobs), Colleagues (saved contacts),
 * Nearby Peers (mesh discovery). Plus manual entry and inline add dialogs.
 */
@Composable
fun NewConversationScreen(
    allJobs: List<Job>,
    onConversationStart: (beaconId: String, channelId: String, peerId: String, peerName: String) -> Unit,
    onBackClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddClientDialog by remember { mutableStateOf(false) }
    var showAddColleagueDialog by remember { mutableStateOf(false) }
    var showManualEntryDialog by remember { mutableStateOf(false) }

    val clients = remember(allJobs) { ClientRepository.getClients(allJobs) }
    val colleagues = remember { ColleagueRepository.getAll() }
    val peers by remember { mutableStateOf(PeerRepository.getActivePeers()) }

    // Filter by search
    val query = searchQuery.trim().lowercase()
    val filteredClients = if (query.isBlank()) clients else clients.filter { it.name.lowercase().contains(query) }
    val filteredColleagues = if (query.isBlank()) colleagues else colleagues.filter { it.name.lowercase().contains(query) || it.trade.lowercase().contains(query) }
    val filteredPeers = if (query.isBlank()) peers else peers.filter { it.userName.lowercase().contains(query) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        // Header
        ConsoleHeader(
            title = "NEW CONVERSATION",
            onBackClick = onBackClick,
            modifier = Modifier.background(ConsoleTheme.surface)
        )
        ConsoleSeparator()

        // Search
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search contacts...", style = ConsoleTheme.caption) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ConsoleTheme.surface,
                unfocusedContainerColor = ConsoleTheme.surface,
                focusedTextColor = ConsoleTheme.text,
                unfocusedTextColor = ConsoleTheme.text,
                cursorColor = ConsoleTheme.accent,
                focusedIndicatorColor = ConsoleTheme.accent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            textStyle = ConsoleTheme.bodySmall,
            singleLine = true
        )

        ConsoleSeparator()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // ── CLIENTS ──
            SectionHeader("CLIENTS", filteredClients.size)
            if (filteredClients.isEmpty() && query.isBlank()) {
                EmptySection("No clients yet")
            } else {
                filteredClients.forEach { client ->
                    ClientRow(client) {
                        startClientDM(client.name, onConversationStart)
                    }
                }
            }
            Text(
                text = "[+ Add Client]",
                style = ConsoleTheme.action.copy(fontSize = 11.sp),
                modifier = Modifier
                    .clickable { showAddClientDialog = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            ConsoleSeparator()

            // ── COLLEAGUES ──
            SectionHeader("COLLEAGUES", filteredColleagues.size)
            if (filteredColleagues.isEmpty() && query.isBlank()) {
                EmptySection("No colleagues saved")
            } else {
                filteredColleagues.forEach { colleague ->
                    ColleagueRow(colleague) {
                        startColleagueDM(colleague, onConversationStart)
                    }
                }
            }
            Text(
                text = "[+ Add Colleague]",
                style = ConsoleTheme.action.copy(fontSize = 11.sp),
                modifier = Modifier
                    .clickable { showAddColleagueDialog = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            ConsoleSeparator()

            // ── NEARBY PEERS ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEARBY PEERS (${filteredPeers.size})",
                    style = ConsoleTheme.captionBold.copy(letterSpacing = 1.sp)
                )
                Text(
                    text = "[Scan]",
                    style = ConsoleTheme.action.copy(fontSize = 10.sp),
                    modifier = Modifier.clickable {
                        BoundaryEngine.requestPeerDiscovery()
                    }
                )
            }
            if (filteredPeers.isEmpty()) {
                EmptySection("No peers discovered nearby")
            } else {
                filteredPeers.forEach { peer ->
                    PeerRow(peer) {
                        startPeerDM(peer, onConversationStart)
                    }
                }
            }

            ConsoleSeparator()

            // ── MANUAL ENTRY ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showManualEntryDialog = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enter name manually",
                    style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "›",
                    style = ConsoleTheme.body.copy(color = ConsoleTheme.accent)
                )
            }

            Spacer(modifier = Modifier.height(80.dp)) // FAB clearance
        }
    }

    // ── Dialogs ──
    if (showAddClientDialog) {
        AddClientDialog(
            onDismiss = { showAddClientDialog = false },
            onAdd = { name, phone, address ->
                ClientRepository.addManualClient(name, phone, address)
                showAddClientDialog = false
                startClientDM(name, onConversationStart)
            }
        )
    }

    if (showAddColleagueDialog) {
        AddColleagueDialog(
            onDismiss = { showAddColleagueDialog = false },
            onAdd = { name, phone, trade, note ->
                val colleague = ColleagueRepository.add(name, phone, trade, note)
                showAddColleagueDialog = false
                startColleagueDM(colleague, onConversationStart)
            }
        )
    }

    if (showManualEntryDialog) {
        ManualEntryDialog(
            onDismiss = { showManualEntryDialog = false },
            onStart = { name ->
                showManualEntryDialog = false
                startClientDM(name, onConversationStart)
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════
// SECTION COMPONENTS
// ═════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        style = ConsoleTheme.captionBold.copy(letterSpacing = 1.sp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun EmptySection(text: String) {
    Text(
        text = text,
        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun ClientRow(client: ClientInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        val initials = client.name.split(" ").let { parts ->
            if (parts.size >= 2) "${parts[0].first()}${parts[1].first()}".uppercase()
            else client.name.take(2).uppercase()
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF3A6B8C)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = initials, style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.surface, fontSize = 11.sp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = client.name,
                style = ConsoleTheme.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (client.jobCount > 0) {
                Text(
                    text = "${client.jobCount} job${if (client.jobCount != 1) "s" else ""}",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
            }
        }

        Text(text = "›", style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted))
    }
}

@Composable
private fun ColleagueRow(colleague: Colleague, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val initials = colleague.name.split(" ").let { parts ->
            if (parts.size >= 2) "${parts[0].first()}${parts[1].first()}".uppercase()
            else colleague.name.take(2).uppercase()
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF6B8C5A)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = initials, style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.surface, fontSize = 11.sp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = colleague.name,
                style = ConsoleTheme.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (colleague.trade.isNotBlank()) {
                Text(
                    text = colleague.trade,
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
            }
        }

        Text(text = "›", style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted))
    }
}

@Composable
private fun PeerRow(peer: Peer, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (peer.isActive()) ConsoleTheme.success else ConsoleTheme.textDim)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = peer.userName,
                style = ConsoleTheme.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${peer.rssi} dBm",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
            )
        }

        Text(text = "›", style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted))
    }
}

// ═════════════════════════════════════════════════════════════════════
// DM CREATION HELPERS
// ═════════════════════════════════════════════════════════════════════

private fun startClientDM(clientName: String, onConversationStart: (String, String, String, String) -> Unit) {
    val myUserId = UserPreferences.getUserId()
    val clientId = "client_${clientName.lowercase().replace(" ", "_")}"
    val dm = BeaconRepository.getOrCreateDM("default", myUserId, clientId, clientName)
    BoundaryEngine.joinChannel(dm.id)
    onConversationStart("default", dm.id, clientId, clientName)
}

private fun startColleagueDM(colleague: Colleague, onConversationStart: (String, String, String, String) -> Unit) {
    val myUserId = UserPreferences.getUserId()
    val dm = BeaconRepository.getOrCreateDM("default", myUserId, colleague.id, colleague.name)
    BoundaryEngine.joinChannel(dm.id)
    ColleagueRepository.updateLastMessaged(colleague.id)
    onConversationStart("default", dm.id, colleague.id, colleague.name)
}

private fun startPeerDM(peer: Peer, onConversationStart: (String, String, String, String) -> Unit) {
    val myUserId = UserPreferences.getUserId()
    val dm = BeaconRepository.getOrCreateDM("default", myUserId, peer.userId, peer.userName)
    BoundaryEngine.joinChannel(dm.id)
    onConversationStart("default", dm.id, peer.userId, peer.userName)
}

// ═════════════════════════════════════════════════════════════════════
// DIALOGS
// ═════════════════════════════════════════════════════════════════════

@Composable
private fun AddClientDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, phone: String, address: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.surface,
        title = { Text("ADD CLIENT", style = ConsoleTheme.captionBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogField("Name *", name) { name = it }
                DialogField("Phone", phone) { phone = it }
                DialogField("Address", address) { address = it }
            }
        },
        confirmButton = {
            Text(
                text = "[OK] ADD",
                style = ConsoleTheme.action,
                modifier = Modifier
                    .clickable(enabled = name.trim().length >= 2) { onAdd(name, phone, address) }
                    .padding(8.dp)
            )
        },
        dismissButton = {
            Text(
                text = "[x] CANCEL",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(8.dp)
            )
        }
    )
}

@Composable
private fun AddColleagueDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, phone: String, trade: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var trade by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.surface,
        title = { Text("ADD COLLEAGUE", style = ConsoleTheme.captionBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogField("Name *", name) { name = it }
                DialogField("Phone", phone) { phone = it }
                DialogField("Trade", trade) { trade = it }
                DialogField("Note", note) { note = it }
            }
        },
        confirmButton = {
            Text(
                text = "[OK] ADD",
                style = ConsoleTheme.action,
                modifier = Modifier
                    .clickable(enabled = name.trim().length >= 2) { onAdd(name, phone, trade, note) }
                    .padding(8.dp)
            )
        },
        dismissButton = {
            Text(
                text = "[x] CANCEL",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(8.dp)
            )
        }
    )
}

@Composable
private fun ManualEntryDialog(
    onDismiss: () -> Unit,
    onStart: (name: String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.surface,
        title = { Text("START CONVERSATION", style = ConsoleTheme.captionBold) },
        text = {
            Column {
                Text("Enter a name to start a conversation:", style = ConsoleTheme.caption)
                Spacer(modifier = Modifier.height(8.dp))
                DialogField("Name or ID", name) { name = it }
            }
        },
        confirmButton = {
            Text(
                text = "[OK] START",
                style = ConsoleTheme.action,
                modifier = Modifier
                    .clickable(enabled = name.trim().length >= 2) { onStart(name.trim()) }
                    .padding(8.dp)
            )
        },
        dismissButton = {
            Text(
                text = "[x] CANCEL",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(8.dp)
            )
        }
    )
}

@Composable
private fun DialogField(label: String, value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label, style = ConsoleTheme.caption) },
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = ConsoleTheme.background,
            unfocusedContainerColor = ConsoleTheme.background,
            focusedTextColor = ConsoleTheme.text,
            unfocusedTextColor = ConsoleTheme.text,
            cursorColor = ConsoleTheme.accent,
            focusedIndicatorColor = ConsoleTheme.accent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        textStyle = ConsoleTheme.bodySmall,
        singleLine = true
    )
}
