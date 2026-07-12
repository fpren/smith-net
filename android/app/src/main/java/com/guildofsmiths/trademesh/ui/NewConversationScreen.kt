package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
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
import com.guildofsmiths.trademesh.data.ProfileDirectoryRepository
import com.guildofsmiths.trademesh.data.ProfileRow
import com.guildofsmiths.trademesh.data.SupabaseAuth
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.ui.components.avatarColorOf
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithButton
import com.guildofsmiths.trademesh.ui.theme2.SmithButtonVariant
import com.guildofsmiths.trademesh.ui.theme2.SmithDialog
import com.guildofsmiths.trademesh.ui.theme2.SmithTextField
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import kotlinx.coroutines.delay

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
    onBackClick: () -> Unit,
    onScanClick: () -> Unit = {}
) {
    val colors = LocalSmithColors.current
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
            .background(colors.bgBase)
    ) {
        // Header
        ConsoleHeader(
            title = "NEW CONVERSATION",
            onBackClick = onBackClick,
            actionText = "[Scan QR]",
            onActionClick = onScanClick,
            modifier = Modifier.background(colors.bgPanel)
        )
        ConsoleSeparator()

        // Search
        SmithTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search contacts...",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            imeAction = ImeAction.Search,
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
                style = SmithType.action.copy(color = colors.accent, fontSize = 11.sp),
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
                style = SmithType.action.copy(color = colors.accent, fontSize = 11.sp),
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
                    style = SmithType.captionBold.copy(color = colors.inkMuted, letterSpacing = 1.sp)
                )
                Text(
                    text = "[Scan]",
                    style = SmithType.action.copy(color = colors.accent, fontSize = 10.sp),
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
                    style = SmithType.bodySmall.copy(color = colors.inkMuted),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "›",
                    style = SmithType.body.copy(color = colors.accent)
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
            },
            onAddFromProfile = { profile, source ->
                val colleague = ColleagueRepository.addFromProfile(profile, source)
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
    val colors = LocalSmithColors.current
    Text(
        text = "$title ($count)",
        style = SmithType.captionBold.copy(color = colors.inkMuted, letterSpacing = 1.sp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun EmptySection(text: String) {
    val colors = LocalSmithColors.current
    Text(
        text = text,
        style = SmithType.caption.copy(color = colors.inkMuted),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun ClientRow(client: ClientInfo, onClick: () -> Unit) {
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar — stable per-contact color (accentForId rule), not a fixed category hex
        val initials = client.name.split(" ").let { parts ->
            if (parts.size >= 2) "${parts[0].first()}${parts[1].first()}".uppercase()
            else client.name.take(2).uppercase()
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(avatarColorOf(client.name)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = initials, style = SmithType.captionBold.copy(color = colors.inkOnAccent, fontSize = 11.sp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = client.name,
                style = SmithType.bodySmall.copy(color = colors.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (client.jobCount > 0) {
                Text(
                    text = "${client.jobCount} job${if (client.jobCount != 1) "s" else ""}",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }
        }

        Text(text = "›", style = SmithType.body.copy(color = colors.inkMuted))
    }
}

@Composable
private fun ColleagueRow(colleague: Colleague, onClick: () -> Unit) {
    val colors = LocalSmithColors.current
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
                .background(avatarColorOf(colleague.name)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = initials, style = SmithType.captionBold.copy(color = colors.inkOnAccent, fontSize = 11.sp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = colleague.name,
                style = SmithType.bodySmall.copy(color = colors.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (colleague.trade.isNotBlank()) {
                Text(
                    text = colleague.trade,
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }
        }

        Text(text = "›", style = SmithType.body.copy(color = colors.inkMuted))
    }
}

@Composable
private fun PeerRow(peer: Peer, onClick: () -> Unit) {
    val colors = LocalSmithColors.current
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
                .background(if (peer.isActive()) colors.statusOnline else colors.inkMuted)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = peer.userName,
                style = SmithType.bodySmall.copy(color = colors.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${peer.rssi} dBm",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
        }

        Text(text = "›", style = SmithType.body.copy(color = colors.inkMuted))
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

    SmithDialog(
        title = "Add client",
        onDismiss = onDismiss,
        actions = {
            SmithButton(text = "CANCEL", onClick = onDismiss, variant = SmithButtonVariant.Ghost)
            Spacer(modifier = Modifier.width(8.dp))
            SmithButton(
                text = "ADD",
                onClick = { onAdd(name, phone, address) },
                enabled = name.trim().length >= 2,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogField("Name *", name) { name = it }
            DialogField("Phone", phone) { phone = it }
            DialogField("Address", address) { address = it }
        }
    }
}

@Composable
private fun AddColleagueDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, phone: String, trade: String, note: String) -> Unit,
    onAddFromProfile: (profile: ProfileRow, source: String) -> Unit
) {
    val colors = LocalSmithColors.current
    val offline = SupabaseAuth.isOfflineMode()
    val currentUser by SupabaseAuth.currentUser.collectAsState()
    val hasOrg = currentUser?.orgId != null

    var query by remember { mutableStateOf("") }
    var manualExpanded by remember { mutableStateOf(offline) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var trade by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    var teammates by remember { mutableStateOf<List<ProfileRow>>(emptyList()) }
    var results by remember { mutableStateOf<List<ProfileRow>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // Load teammates once when dialog opens (if we have an org and we're online).
    LaunchedEffect(Unit) {
        if (!offline && hasOrg) {
            teammates = ProfileDirectoryRepository.teammates()
        }
    }

    // Debounced search on query changes.
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        if (offline) return@LaunchedEffect
        searching = true
        delay(300)
        results = ProfileDirectoryRepository.search(trimmed)
        searching = false
    }

    SmithDialog(
        title = "Add colleague",
        onDismiss = onDismiss,
        actions = {
            SmithButton(text = "CANCEL", onClick = onDismiss, variant = SmithButtonVariant.Ghost)
            Spacer(modifier = Modifier.width(8.dp))
            SmithButton(
                text = "ADD",
                onClick = { onAdd(name, phone, trade, note) },
                enabled = manualExpanded && name.trim().length >= 2,
            )
        },
    ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DialogField(
                    label = if (offline) "Offline — use manual entry below" else "Search name or SmithNet ID",
                    value = query,
                    onValueChange = { if (!offline) query = it }
                )

                if (offline) {
                    Text(
                        text = "Search unavailable offline.",
                        style = SmithType.caption.copy(color = colors.inkMuted),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (query.isBlank() && hasOrg) {
                    if (teammates.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("YOUR TEAM (${teammates.size})", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        teammates.forEach { p ->
                            ProfileRowItem(p) { onAddFromProfile(p, "team") }
                        }
                    }
                } else if (query.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    when {
                        searching -> Text("Searching...", style = SmithType.caption.copy(color = colors.inkMuted))
                        results.isEmpty() -> Text(
                            "No users found. They may have privacy on.",
                            style = SmithType.caption.copy(color = colors.inkMuted)
                        )
                        else -> {
                            Text("SEARCH RESULTS (${results.size})", style = SmithType.captionBold.copy(color = colors.inkMuted))
                            results.forEach { p ->
                                ProfileRowItem(p) { onAddFromProfile(p, "search") }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (manualExpanded) "── or enter manually ──" else "[+ Enter details manually]",
                    style = SmithType.action.copy(color = colors.accent, fontSize = 11.sp),
                    modifier = Modifier
                        .clickable { manualExpanded = !manualExpanded }
                        .padding(vertical = 4.dp)
                )

                if (manualExpanded) {
                    DialogField("Name *", name) { name = it }
                    DialogField("Phone", phone) { phone = it }
                    DialogField("Trade", trade) { trade = it }
                    DialogField("Note", note) { note = it }
                }
            }
    }
}

@Composable
private fun ProfileRowItem(profile: ProfileRow, onAdd: () -> Unit) {
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(profile.display_name, style = SmithType.bodySmall.copy(color = colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = listOfNotNull(
                profile.trade?.takeIf { it.isNotBlank() },
                profile.public_id?.takeIf { it.isNotBlank() }?.let { "ID $it" }
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = SmithType.caption.copy(color = colors.inkMuted))
            }
        }
        Text("[+ Add]", style = SmithType.action.copy(color = colors.accent, fontSize = 11.sp))
    }
}

@Composable
private fun ManualEntryDialog(
    onDismiss: () -> Unit,
    onStart: (name: String) -> Unit
) {
    val colors = LocalSmithColors.current
    var name by remember { mutableStateOf("") }

    SmithDialog(
        title = "Start conversation",
        onDismiss = onDismiss,
        actions = {
            SmithButton(text = "CANCEL", onClick = onDismiss, variant = SmithButtonVariant.Ghost)
            Spacer(modifier = Modifier.width(8.dp))
            SmithButton(
                text = "START",
                onClick = { onStart(name.trim()) },
                enabled = name.trim().length >= 2,
            )
        },
    ) {
        Text("Enter a name to start a conversation:", style = SmithType.caption.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(8.dp))
        DialogField("Name or ID", name) { name = it }
    }
}

@Composable
private fun DialogField(label: String, value: String, onValueChange: (String) -> Unit) {
    SmithTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = label,
        modifier = Modifier.fillMaxWidth(),
    )
}
