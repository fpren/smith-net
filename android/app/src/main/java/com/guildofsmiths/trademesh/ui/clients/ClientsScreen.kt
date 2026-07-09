package com.guildofsmiths.trademesh.ui.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.ClientInfo
import com.guildofsmiths.trademesh.data.ClientRepository
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithButton
import com.guildofsmiths.trademesh.ui.theme2.SmithButtonVariant
import com.guildofsmiths.trademesh.ui.theme2.SmithDialog
import com.guildofsmiths.trademesh.ui.theme2.SmithType

@Composable
fun ClientsScreen(
    allJobs: List<Job>,
    onClientClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalSmithColors.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    // Bump to force the client list to recompute after a manual client is added
    // (manual clients live in ClientRepository overrides, not in allJobs).
    var clientsRefresh by remember { mutableStateOf(0) }

    val clients = remember(allJobs, clientsRefresh) { ClientRepository.getClients(allJobs) }
    val filteredClients = remember(clients, searchQuery) {
        if (searchQuery.isBlank()) clients
        else clients.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
    ) {
        ConsoleHeader(
            title = "CLIENTS",
            onBackClick = onBack,
            actionText = "[+ ADD]",
            onActionClick = { showAddDialog = true }
        )

        // Search bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(colors.bgPanel, RoundedCornerShape(4.dp))
                .border(0.5.dp, colors.ink.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (searchQuery.isEmpty()) {
                Text(
                    text = "Search clients...",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = SmithType.bodySmall.copy(color = colors.ink),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (filteredClients.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No clients match \"$searchQuery\""
                           else "No clients yet. Create a job or tap [+ ADD].",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(filteredClients, key = { it.name }) { client ->
                    ClientRow(client = client, onClick = { onClientClick(client.name) })
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(colors.ink.copy(alpha = 0.06f))
                    )
                }
            }
        }
    }

    // Add client dialog
    if (showAddDialog) {
        AddClientDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, phone, address, note ->
                ClientRepository.addManualClient(name, phone, address, note = note)
                clientsRefresh++
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ClientRow(client: ClientInfo, onClick: () -> Unit) {
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = client.name,
                style = SmithType.bodySmall.copy(color = colors.ink)
            )
            val stageIcon = client.latestStage?.icon ?: ""
            val jobLabel = if (client.jobCount == 1) "1 job" else "${client.jobCount} jobs"
            val addressShort = client.address.take(25)
            val subtitle = if (stageIcon.isNotBlank() && addressShort.isNotBlank()) {
                "$jobLabel · $stageIcon · $addressShort"
            } else if (addressShort.isNotBlank()) {
                "$jobLabel · $addressShort"
            } else {
                jobLabel
            }
            Text(
                text = subtitle,
                style = SmithType.caption.copy(color = colors.inkMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = ">",
            style = SmithType.caption.copy(color = colors.inkMuted),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun AddClientDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, phone: String, address: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    SmithDialog(
        title = "Add Client",
        onDismiss = onDismiss,
        actions = {
            SmithButton(text = "CANCEL", onClick = onDismiss, variant = SmithButtonVariant.Ghost)
            Spacer(modifier = Modifier.width(8.dp))
            SmithButton(
                text = "SAVE",
                onClick = {
                    if (name.isNotBlank()) onAdd(name.trim(), phone.trim(), address.trim(), note.trim())
                },
                enabled = name.isNotBlank(),
            )
        },
    ) {
        Column(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.fillMaxWidth().testTag("solo_e2e_client_name")) {
                DialogField("NAME", name) { name = it }
            }
            DialogField("PHONE", phone) { phone = it }
            Box(Modifier.fillMaxWidth().testTag("solo_e2e_client_address")) {
                DialogField("ADDRESS", address) { address = it }
            }
            Box(Modifier.fillMaxWidth().testTag("solo_e2e_client_note")) {
                DialogField("NOTE", note) { note = it }
            }
        }
    }
}

@Composable
private fun DialogField(label: String, value: String, onValueChange: (String) -> Unit) {
    val colors = LocalSmithColors.current
    Column {
        Text(label, style = SmithType.caption.copy(color = colors.inkMuted))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = SmithType.bodySmall.copy(color = colors.ink),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgBase, RoundedCornerShape(4.dp))
                .padding(8.dp),
            singleLine = true
        )
    }
}
