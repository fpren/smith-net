package com.guildofsmiths.trademesh.ui.supply

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import com.guildofsmiths.trademesh.ui.jobboard.Material
import com.guildofsmiths.trademesh.ui.theme2.SmithButton
import com.guildofsmiths.trademesh.ui.theme2.SmithButtonVariant
import com.guildofsmiths.trademesh.ui.theme2.SmithDialog

data class SupplyItem(
    val material: Material,
    val materialIndex: Int,
    val jobId: String,
    val jobName: String
)

data class Vendor(
    val tag: String,
    val label: String,
    val url: String?
)

private val VENDORS = listOf(
    Vendor("HD", "Home Depot", "https://www.homedepot.com/s/"),
    Vendor("Lowes", "Lowe's", "https://www.lowes.com/search?searchTerm="),
    Vendor("Supply", "SupplyHouse", "https://www.supplyhouse.com/search?q="),
    Vendor("Amazon", "Amazon", "https://www.amazon.com/s?k="),
    Vendor("Other", "Buy in store", null)
)

@Composable
fun SupplyScreen(
    allJobs: List<Job>,
    onToggleMaterial: (jobId: String, materialIndex: Int) -> Unit,
    onAddMaterial: (jobId: String, material: Material) -> Unit,
    onUpdateMaterial: (jobId: String, materialIndex: Int, material: Material) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalSmithColors.current
    var selectedJobId by remember { mutableStateOf<String?>(null) }
    var showJobPicker by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<SupplyItem?>(null) }

    val activeJobs = remember(allJobs) {
        allJobs.filter { it.stage != JobStage.CLOSED }
    }
    val jobsWithMaterials = remember(allJobs) {
        allJobs.filter { it.stage != JobStage.CLOSED && it.materials.isNotEmpty() }
    }

    val allItems = remember(allJobs, selectedJobId) {
        val jobs = if (selectedJobId != null) {
            allJobs.filter { it.id == selectedJobId }
        } else {
            allJobs.filter { it.stage != JobStage.CLOSED }
        }

        jobs.flatMap { job ->
            job.materials.mapIndexed { index, material ->
                SupplyItem(
                    material = material,
                    materialIndex = index,
                    jobId = job.id,
                    jobName = job.clientName ?: job.title
                )
            }
        }
    }

    val unchecked = allItems.filter { !it.material.checked }
    val checked = allItems.filter { it.material.checked }
    val totalCost = allItems.sumOf { it.material.totalCost }
    val selectedJobName = if (selectedJobId != null) {
        allJobs.find { it.id == selectedJobId }?.let { it.clientName ?: it.title } ?: "All Jobs"
    } else "All Jobs"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
    ) {
        ConsoleHeader(
            title = "SUPPLY",
            onBackClick = onBack,
            actionText = "[+ ADD]",
            onActionClick = { showAddDialog = true }
        )

        // Job filter
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
                .border(0.5.dp, colors.ink.copy(alpha = 0.06f), RoundedCornerShape(Tokens2.RadiusCard))
                .clip(RoundedCornerShape(Tokens2.RadiusCard))
                .clickable { showJobPicker = !showJobPicker }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedJobName, style = SmithType.bodySmall.copy(color = colors.ink))
                Text("▼", style = SmithType.caption.copy(color = colors.inkMuted))
            }
        }

        // Job picker dropdown
        if (showJobPicker) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
                    .border(0.5.dp, colors.ink.copy(alpha = 0.1f), RoundedCornerShape(Tokens2.RadiusCard))
            ) {
                Text(
                    text = "All Jobs",
                    style = SmithType.bodySmall.copy(
                        color = if (selectedJobId == null) colors.accent else colors.ink
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedJobId = null; showJobPicker = false }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
                jobsWithMaterials.forEach { job ->
                    Box(Modifier.fillMaxWidth().height(0.5.dp).padding(horizontal = 12.dp).background(colors.ink.copy(alpha = 0.06f)))
                    Text(
                        text = job.clientName ?: job.title,
                        style = SmithType.bodySmall.copy(
                            color = if (selectedJobId == job.id) colors.accent else colors.ink
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedJobId = job.id; showJobPicker = false }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // SHOPPING LIST
            SupplyCard("SHOPPING LIST (${unchecked.size} items)") {
                if (unchecked.isEmpty()) {
                    Text(
                        text = "All items checked off!",
                        style = SmithType.caption.copy(color = colors.inkMuted),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    unchecked.forEach { item ->
                        MaterialRow(
                            item = item,
                            onToggle = { onToggleMaterial(item.jobId, item.materialIndex) },
                            onEdit = { editingItem = item }
                        )
                    }
                }
            }

            // CHECKED OFF
            if (checked.isNotEmpty()) {
                SupplyCard("CHECKED OFF (${checked.size} items)") {
                    checked.forEach { item ->
                        MaterialRow(
                            item = item,
                            onToggle = { onToggleMaterial(item.jobId, item.materialIndex) },
                            onEdit = { editingItem = item }
                        )
                    }
                }
            }

            // TOTAL
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
                    .border(0.5.dp, colors.ink.copy(alpha = 0.06f), RoundedCornerShape(Tokens2.RadiusCard))
                    .padding(14.dp)
            ) {
                Text("TOTAL", style = SmithType.captionBold.copy(color = colors.inkMuted))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$${String.format("%.0f", totalCost)} total · ${allItems.size} items · ${checked.size} purchased",
                    style = SmithType.caption.copy(color = colors.accent)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Add material dialog
    if (showAddDialog) {
        MaterialDialog(
            title = "Add Material",
            jobs = activeJobs,
            preselectedJobId = selectedJobId,
            onDismiss = { showAddDialog = false },
            onSave = { jobId, material ->
                onAddMaterial(jobId, material)
                showAddDialog = false
            }
        )
    }

    // Edit material dialog
    if (editingItem != null) {
        val item = editingItem!!
        MaterialDialog(
            title = "Edit Material",
            jobs = activeJobs,
            preselectedJobId = item.jobId,
            initialMaterial = item.material,
            lockJob = true,
            onDismiss = { editingItem = null },
            onSave = { _, material ->
                onUpdateMaterial(item.jobId, item.materialIndex, material)
                editingItem = null
            }
        )
    }
}

@Composable
private fun MaterialRow(item: SupplyItem, onToggle: () -> Unit, onEdit: () -> Unit) {
    val colors = LocalSmithColors.current
    val mat = item.material
    val context = LocalContext.current
    var showVendorPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens2.RadiusControl))
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox area — tap to toggle
        Text(
            text = if (mat.checked) "[x]" else "[ ]",
            style = SmithType.body.copy(color = if (mat.checked) colors.inkMuted else colors.ink),
            modifier = Modifier
                .width(32.dp)
                .clickable(onClick = onToggle)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mat.name,
                style = SmithType.bodySmall.copy(
                    color = if (mat.checked) colors.inkMuted else colors.ink
                )
            )
            val detailParts = buildString {
                if (item.jobName.isNotBlank()) append(item.jobName)
                if (mat.quantity > 0 && mat.unit.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append("${mat.quantity.toInt()} ${mat.unit}")
                }
                if (mat.vendor.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(mat.vendor)
                }
            }
            if (detailParts.isNotBlank()) {
                Text(detailParts, style = SmithType.caption.copy(color = colors.inkMuted))
            }
        }
        if (mat.totalCost > 0) {
            Text(
                text = "$${String.format("%.0f", mat.totalCost)}",
                style = SmithType.caption.copy(
                    color = if (mat.checked) colors.inkMuted else colors.accent
                ),
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        // Order button — always visible on unchecked items
        if (!mat.checked) {
            Text(
                text = "[Order]",
                style = SmithType.caption.copy(color = colors.accent),
                modifier = Modifier
                    .clickable { showVendorPicker = true }
                    .padding(4.dp)
            )
        }
        // Edit button
        Text(
            text = "[Edit]",
            style = SmithType.caption.copy(color = colors.inkMuted),
            modifier = Modifier
                .clickable(onClick = onEdit)
                .padding(4.dp)
        )
    }

    // Vendor picker modal
    if (showVendorPicker) {
        SmithDialog(
            title = "Order",
            onDismiss = { showVendorPicker = false },
            actions = {
                SmithButton(
                    text = "CANCEL",
                    onClick = { showVendorPicker = false },
                    variant = SmithButtonVariant.Ghost,
                )
            },
        ) {
            Text(mat.name, style = SmithType.caption.copy(color = colors.inkMuted))
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VENDORS.forEach { vendor ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Tokens2.RadiusControl))
                            .background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusControl))
                            .clickable {
                                showVendorPicker = false
                                if (vendor.url != null) {
                                    val url = vendor.url + Uri.encode(mat.name)
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = vendor.tag,
                            style = SmithType.captionBold.copy(color = colors.accent),
                            modifier = Modifier.width(50.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(vendor.label, style = SmithType.bodySmall.copy(color = colors.ink))
                            if (vendor.url != null) {
                                Text("Search & order", style = SmithType.caption.copy(color = colors.inkMuted))
                            } else {
                                Text("Manual purchase", style = SmithType.caption.copy(color = colors.inkMuted))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialDialog(
    title: String,
    jobs: List<Job>,
    preselectedJobId: String?,
    initialMaterial: Material? = null,
    lockJob: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (jobId: String, material: Material) -> Unit
) {
    val colors = LocalSmithColors.current
    var selectedJobId by remember { mutableStateOf(preselectedJobId ?: jobs.firstOrNull()?.id ?: "") }
    var name by remember { mutableStateOf(initialMaterial?.name ?: "") }
    var quantity by remember { mutableStateOf(if (initialMaterial != null && initialMaterial.quantity > 0) initialMaterial.quantity.toInt().toString() else "") }
    var unit by remember { mutableStateOf(initialMaterial?.unit ?: "ea") }
    var unitCost by remember { mutableStateOf(if (initialMaterial != null && initialMaterial.unitCost > 0) String.format("%.2f", initialMaterial.unitCost) else "") }
    var vendor by remember { mutableStateOf(initialMaterial?.vendor ?: "") }
    var showJobSelect by remember { mutableStateOf(false) }

    val selectedJobName = jobs.find { it.id == selectedJobId }?.let { it.clientName ?: it.title } ?: "Select job"

    SmithDialog(
        title = title,
        onDismiss = onDismiss,
        actions = {
            SmithButton(text = "CANCEL", onClick = onDismiss, variant = SmithButtonVariant.Ghost)
            Spacer(modifier = Modifier.width(8.dp))
            SmithButton(
                text = "SAVE",
                onClick = {
                    if (name.isNotBlank() && selectedJobId.isNotBlank()) {
                        val qty = quantity.toDoubleOrNull() ?: 0.0
                        val cost = unitCost.toDoubleOrNull() ?: 0.0
                        val material = (initialMaterial ?: Material(name = "")).copy(
                            name = name.trim(),
                            quantity = qty,
                            unit = unit.trim().ifBlank { "ea" },
                            unitCost = cost,
                            totalCost = qty * cost,
                            vendor = vendor.trim()
                        )
                        onSave(selectedJobId, material)
                    }
                },
                enabled = name.isNotBlank() && selectedJobId.isNotBlank(),
            )
        },
    ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Job selector
                if (!lockJob) {
                    Text("JOB", style = SmithType.caption.copy(color = colors.inkMuted))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusControl))
                            .clickable { showJobSelect = !showJobSelect }
                            .padding(8.dp)
                    ) {
                        Text(selectedJobName, style = SmithType.bodySmall.copy(color = colors.ink))
                    }
                    if (showJobSelect) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusControl))
                                .border(0.5.dp, colors.ink.copy(alpha = 0.1f), RoundedCornerShape(Tokens2.RadiusControl))
                        ) {
                            jobs.forEach { job ->
                                Text(
                                    text = job.clientName ?: job.title,
                                    style = SmithType.bodySmall.copy(
                                        color = if (job.id == selectedJobId) colors.accent else colors.ink
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedJobId = job.id; showJobSelect = false }
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }

                DialogField("NAME", name) { name = it }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        DialogField("QTY", quantity) { quantity = it }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        DialogField("UNIT", unit) { unit = it }
                    }
                }

                DialogField("UNIT COST ($)", unitCost, KeyboardType.Decimal) { unitCost = it }

                // Vendor chip selector (matches job board)
                Text("VENDOR", style = SmithType.caption.copy(color = colors.inkMuted))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("HD", "Lowes", "Supply", "Other").forEach { v ->
                        Text(
                            text = if (vendor == v) "[$v]" else v,
                            style = SmithType.action.copy(
                                color = if (vendor == v) colors.accent else colors.inkMuted
                            ),
                            modifier = Modifier.clickable { vendor = v }
                        )
                    }
                }
            }
    }
}

@Composable
private fun DialogField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    val colors = LocalSmithColors.current
    Column {
        Text(label, style = SmithType.caption.copy(color = colors.inkMuted))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = SmithType.bodySmall.copy(color = colors.ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusControl))
                .padding(8.dp),
            singleLine = true
        )
    }
}

@Composable
private fun SupplyCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalSmithColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
            .border(0.5.dp, colors.ink.copy(alpha = 0.06f), RoundedCornerShape(Tokens2.RadiusCard))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, style = SmithType.captionBold.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}
