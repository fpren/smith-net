package com.guildofsmiths.trademesh.ui.proposal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProposalPreviewDialog(
    proposal: Proposal,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.background,
        modifier = Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.95f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("PROPOSAL PREVIEW", style = ConsoleTheme.header)
                    Text(
                        proposal.proposalNumber,
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent)
                    )
                }
                Text(
                    "X",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeaderBlock(proposal, dateFmt)
                ConsoleSeparator()
                PartyBlock("FROM", proposal.providerName,
                    listOfNotNull(
                        proposal.providerBusiness.takeIf { it.isNotBlank() },
                        proposal.providerTrade.takeIf { it.isNotBlank() },
                        proposal.providerPhone?.takeIf { it.isNotBlank() }?.let { "Phone: $it" },
                        proposal.providerEmail?.takeIf { it.isNotBlank() }?.let { "Email: $it" }
                    ))
                PartyBlock("TO", proposal.clientName,
                    listOfNotNull(proposal.clientPhone, proposal.clientAddress))
                ConsoleSeparator()

                Text("SCOPE OF WORK", style = ConsoleTheme.captionBold)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(proposal.jobTitle, style = ConsoleTheme.bodyBold)
                    if (proposal.scopeStatement.isNotBlank()) {
                        Text(proposal.scopeStatement, style = ConsoleTheme.body)
                    }
                }
                ConsoleSeparator()

                LaborBlock(proposal)

                if (proposal.materialLines.isNotEmpty()) {
                    ConsoleSeparator()
                    MaterialsBlock(proposal)
                }

                ConsoleSeparator()
                TotalsBlock(proposal)

                ConsoleSeparator()
                TimelineBlock(proposal)

                ConsoleSeparator()
                TextBlock("WARRANTY", proposal.warrantyText)

                ConsoleSeparator()
                ListBlock("EXCLUSIONS", proposal.exclusions)

                ConsoleSeparator()
                TextBlock("TERMS", proposal.termsText)

                ConsoleSeparator()
                SignatureBlock()

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Guild of Smiths — Built for the trades.",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionBtn("COPY", ConsoleTheme.accent) {
                    val txt = ProposalFormatter.formatAsText(proposal)
                    copyToClipboard(context, txt)
                    Toast.makeText(context, "Proposal copied", Toast.LENGTH_SHORT).show()
                }
                ActionBtn("SHARE", ConsoleTheme.accent) {
                    val txt = ProposalFormatter.formatAsText(proposal)
                    sharePlain(context, txt, "${proposal.proposalNumber} — ${proposal.jobTitle}")
                }
                ActionBtn("CLOSE", ConsoleTheme.textMuted, onClick = onDismiss)
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun ActionBtn(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = ConsoleTheme.action.copy(color = color),
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun HeaderBlock(p: Proposal, dateFmt: SimpleDateFormat) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("GUILD OF SMITHS PROPOSAL", style = ConsoleTheme.header)
            Text("──────────────────────────────", style = ConsoleTheme.caption)
            Text(
                "Issued ${dateFmt.format(Date(p.issuedDate))}  ·  Valid through ${dateFmt.format(Date(p.validUntil))}",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
            )
        }
    }
}

@Composable
private fun PartyBlock(label: String, primary: String, lines: List<String>) {
    Text(label, style = ConsoleTheme.captionBold)
    Column(modifier = Modifier.padding(start = 12.dp)) {
        Text(primary, style = ConsoleTheme.bodyBold)
        lines.forEach { Text(it, style = ConsoleTheme.caption) }
    }
}

@Composable
private fun LaborBlock(p: Proposal) {
    Text("LABOR", style = ConsoleTheme.captionBold)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(p.laborLine.description, style = ConsoleTheme.body)
            Text(
                "${"%.1f".format(p.laborLine.estimatedHours)} hrs × $${"%.2f".format(p.laborLine.hourlyRate)}",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
            )
        }
        Text("$${"%.2f".format(p.laborLine.total)}", style = ConsoleTheme.bodyBold)
    }
}

@Composable
private fun MaterialsBlock(p: Proposal) {
    Text("MATERIALS", style = ConsoleTheme.captionBold)
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        p.materialLines.forEach { m ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(m.name, style = ConsoleTheme.body)
                    Text(
                        "${fmtQty(m.quantity)} ${m.unit} × $${"%.2f".format(m.unitCost)}",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                    m.notes?.let {
                        Text(it, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                    }
                }
                Text("$${"%.2f".format(m.total)}", style = ConsoleTheme.body)
            }
        }
        val matTotal = p.materialLines.sumOf { it.total }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Materials subtotal", style = ConsoleTheme.caption)
            Text("$${"%.2f".format(matTotal)}", style = ConsoleTheme.bodyBold)
        }
    }
}

@Composable
private fun TotalsBlock(p: Proposal) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        TotalRow("Subtotal", p.subtotal, bold = false)
        TotalRow("Tax (${p.taxRate}%)", p.taxAmount, bold = false)
        TotalRow("TOTAL", p.total, bold = true, accent = true)
        Spacer(modifier = Modifier.height(6.dp))
        TotalRow("Deposit (${p.depositPercent}%)", p.depositRequired, bold = false, accent = true)
        TotalRow("Balance on completion", p.balanceOnCompletion, bold = true)
    }
}

@Composable
private fun TotalRow(label: String, amount: Double, bold: Boolean, accent: Boolean = false) {
    val style = if (bold) ConsoleTheme.bodyBold else ConsoleTheme.body
    val color = if (accent) ConsoleTheme.accent else ConsoleTheme.text
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label + ":", style = style.copy(color = color))
        Text(
            "$${"%.2f".format(amount)}",
            style = style.copy(color = color),
            modifier = Modifier.width(100.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun TimelineBlock(p: Proposal) {
    Text("TIMELINE", style = ConsoleTheme.captionBold)
    Column(modifier = Modifier.padding(start = 12.dp)) {
        Text(
            "Estimated ${p.timelineDays} working day${if (p.timelineDays == 1) "" else "s"}",
            style = ConsoleTheme.body
        )
        Text(
            "Start: ${p.startEstimate}",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )
    }
}

@Composable
private fun TextBlock(label: String, body: String) {
    Text(label, style = ConsoleTheme.captionBold)
    Column(modifier = Modifier.padding(start = 12.dp)) {
        Text(body, style = ConsoleTheme.body)
    }
}

@Composable
private fun ListBlock(label: String, items: List<String>) {
    Text(label, style = ConsoleTheme.captionBold)
    Column(modifier = Modifier.padding(start = 12.dp)) {
        items.forEach { Text("- $it", style = ConsoleTheme.body) }
    }
}

@Composable
private fun SignatureBlock() {
    Column {
        Text("ACCEPTANCE", style = ConsoleTheme.captionBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("_____________________", style = ConsoleTheme.body)
                Text("Client signature", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
            }
            Column(modifier = Modifier.width(120.dp)) {
                Text("___________", style = ConsoleTheme.body)
                Text("Date", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
            }
        }
    }
}

private fun fmtQty(q: Double): String =
    if (q == q.toLong().toDouble()) q.toLong().toString() else "%.1f".format(q)

private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Proposal", text))
}

private fun sharePlain(ctx: Context, text: String, subject: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(intent, "Share proposal"))
}
