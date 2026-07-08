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
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.Job

@Composable
fun ClientsScreen(
    allJobs: List<Job>,
    onClientClick: (String) -> Unit,
    onBack: () -> Unit
) {
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
            .background(ConsoleTheme.background)
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
                .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (searchQuery.isEmpty()) {
                Text(
                    text = "Search clients...",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
            }
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text),
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
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
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
                            .background(ConsoleTheme.text.copy(alpha = 0.06f))
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
                style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
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
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = ">",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
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

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.surface,
        title = { Text("Add Client", style = ConsoleTheme.bodyBold) },
        text = {
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
        },
        confirmButton = {
            Text(
                text = "[Save]",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                modifier = Modifier.clickable {
                    if (name.isNotBlank()) onAdd(name.trim(), phone.trim(), address.trim(), note.trim())
                }.padding(8.dp)
            )
        },
        dismissButton = {
            Text(
                text = "[Cancel]",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier.clickable { onDismiss() }.padding(8.dp)
            )
        }
    )
}

@Composable
private fun DialogField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text),
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.background, RoundedCornerShape(4.dp))
                .padding(8.dp),
            singleLine = true
        )
    }
}
