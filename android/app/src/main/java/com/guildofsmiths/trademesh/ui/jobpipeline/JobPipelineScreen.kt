package com.guildofsmiths.trademesh.ui.jobpipeline

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.*

@Composable
fun JobPipelineScreen(
    job: Job,
    onBack: () -> Unit,
    onStageAction: (Job, JobStage) -> Unit,
    onToggleMaterial: (Int) -> Unit,
    onClockIn: () -> Unit,
    onShareProposal: () -> Unit,
    onShareInvoice: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        ConsoleHeader(
            title = job.clientName ?: job.title,
            onBackClick = onBack
        )

        JobStageBar(currentStage = job.stage)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Client Info
            if (job.clientName?.isNotBlank() == true || job.clientPhone.isNotBlank()) {
                SectionHeader("CLIENT")
                if (job.clientName?.isNotBlank() == true) {
                    Text(text = job.clientName, style = ConsoleTheme.body)
                }
                if (job.clientPhone.isNotBlank()) {
                    Text(
                        text = "☎ ${job.clientPhone}",
                        style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${job.clientPhone}"))
                            context.startActivity(intent)
                        }
                    )
                }
                if (job.clientAddress.isNotBlank()) {
                    Text(
                        text = "⌖ ${job.clientAddress}",
                        style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(job.clientAddress)}"))
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // Scope
            if (job.description.isNotBlank()) {
                SectionHeader("SCOPE")
                Text(text = job.description, style = ConsoleTheme.bodySmall)
            }

            // Tasks
            if (job.materials.isNotEmpty() || job.workLog.isNotEmpty()) {
                ConsoleSeparator()
            }

            // Materials
            if (job.materials.isNotEmpty()) {
                SectionHeader("MATERIALS (${job.materials.count { it.checked }}/${job.materials.size})")
                job.materials.forEachIndexed { index, material ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface)
                            .clickable { onToggleMaterial(index) }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (material.checked) "[x]" else "[ ]",
                            style = ConsoleTheme.body,
                            modifier = Modifier.width(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = material.name, style = ConsoleTheme.bodySmall)
                            if (material.quantity > 0 && material.unitCost > 0) {
                                Text(
                                    text = "${material.quantity} ${material.unit} × $${material.unitCost}",
                                    style = ConsoleTheme.caption
                                )
                            }
                        }
                        if (material.totalCost > 0) {
                            Text(text = "$${String.format("%.2f", material.totalCost)}", style = ConsoleTheme.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            // Equipment
            if (job.equipmentList.isNotEmpty()) {
                SectionHeader("EQUIPMENT")
                job.equipmentList.forEach { item ->
                    Text(text = "  - $item", style = ConsoleTheme.bodySmall)
                }
            }

            // Price Breakdown
            ConsoleSeparator()
            SectionHeader("PRICE")
            val materialsCost = job.materials.sumOf { it.totalCost }
            val laborCost = job.hourlyRate * 8 // placeholder — actual hours from time entries
            Text(text = "Labor: $${String.format("%.2f", laborCost)}", style = ConsoleTheme.bodySmall)
            Text(text = "Materials: $${String.format("%.2f", materialsCost)}", style = ConsoleTheme.bodySmall)
            Text(
                text = "Total: $${String.format("%.2f", laborCost + materialsCost)}",
                style = ConsoleTheme.bodyBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stage-specific actions
            when (job.stage) {
                JobStage.LEAD -> {
                    ActionButton("[CREATE PROPOSAL]") { onStageAction(job, JobStage.PROPOSAL) }
                }
                JobStage.PROPOSAL -> {
                    ActionButton("[SHARE WITH CLIENT]") { onShareProposal() }
                }
                JobStage.APPROVED -> {
                    ActionButton("[START WORK]") { onStageAction(job, JobStage.IN_PROGRESS) }
                }
                JobStage.IN_PROGRESS -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("[CLOCK IN]") { onClockIn() }
                        ActionButton("[MARK COMPLETE]") { onStageAction(job, JobStage.REVIEW) }
                    }
                }
                JobStage.REVIEW -> {
                    val unchecked = job.materials.count { !it.checked }
                    if (unchecked > 0) {
                        Text(
                            text = "! $unchecked materials not checked off",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning)
                        )
                    }
                    ActionButton("[GENERATE INVOICE]") { onStageAction(job, JobStage.INVOICE) }
                }
                JobStage.INVOICE -> {
                    ActionButton("[SHARE INVOICE]") { onShareInvoice() }
                    Spacer(modifier = Modifier.height(4.dp))
                    ActionButton("[MARK PAID — CLOSE]") { onStageAction(job, JobStage.CLOSED) }
                }
                JobStage.CLOSED -> {
                    Text(text = "Job closed.", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text = text, style = ConsoleTheme.captionBold)
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = ConsoleTheme.action,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(ConsoleTheme.surface)
            .padding(12.dp)
    )
}
