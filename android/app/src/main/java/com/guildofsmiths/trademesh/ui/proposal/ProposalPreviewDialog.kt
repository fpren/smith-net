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
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithDialog
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import com.guildofsmiths.trademesh.ui.theme2.tabular
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProposalPreviewDialog(
    proposal: Proposal,
    onDismiss: () -> Unit,
    /** When set, the SHARE button delegates here (e.g. to share a public link
     *  with a text fallback); otherwise it shares the formatted text directly. */
    onShare: (() -> Unit)? = null
) {
    val colors = LocalSmithColors.current
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }

    SmithDialog(
        title = "PROPOSAL PREVIEW",
        onDismiss = onDismiss,
        sizeFraction = 0.98f to 0.95f,
        ops = true,
        actions = {
            ActionBtn("COPY", colors.accent) {
                val txt = ProposalFormatter.formatAsText(proposal)
                copyToClipboard(context, txt)
                Toast.makeText(context, "Proposal copied", Toast.LENGTH_SHORT).show()
            }
            Spacer(modifier = Modifier.width(8.dp))
            ActionBtn("SHARE", colors.accent) {
                if (onShare != null) {
                    onShare()
                } else {
                    val txt = ProposalFormatter.formatAsText(proposal)
                    sharePlain(context, txt, "${proposal.proposalNumber} — ${proposal.jobTitle}")
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            ActionBtn("CLOSE", colors.inkMuted, onClick = onDismiss)
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                proposal.proposalNumber,
                style = SmithType.caption.copy(color = colors.accent)
            )
            Text(
                "X",
                style = SmithType.action.copy(color = colors.accent),
                modifier = Modifier.clickable { onDismiss() }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
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

                Text("SCOPE OF WORK", style = SmithType.captionBold.copy(color = colors.inkMuted))
                Column(modifier = Modifier.padding(start = 9.dp)) {
                    Text(proposal.jobTitle, style = SmithType.bodyBold.copy(color = colors.ink))
                    if (proposal.scopeStatement.isNotBlank()) {
                        Text(proposal.scopeStatement, style = SmithType.body.copy(color = colors.ink))
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
                    style = SmithType.caption.copy(color = colors.inkMuted),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
    }
}

@Composable
private fun ActionBtn(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = SmithType.action.copy(color = color),
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(1.dp, color, RoundedCornerShape(Tokens2.RadiusOps))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun HeaderBlock(p: Proposal, dateFmt: SimpleDateFormat) {
    val colors = LocalSmithColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPanel)
            .padding(9.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("GUILD OF SMITHS PROPOSAL", style = SmithType.header.copy(color = colors.ink))
            Text("──────────────────────────────", style = SmithType.caption.copy(color = colors.inkMuted))
            Text(
                "Issued ${dateFmt.format(Date(p.issuedDate))}  ·  Valid through ${dateFmt.format(Date(p.validUntil))}",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
        }
    }
}

@Composable
private fun PartyBlock(label: String, primary: String, lines: List<String>) {
    val colors = LocalSmithColors.current
    Text(label, style = SmithType.captionBold.copy(color = colors.inkMuted))
    Column(modifier = Modifier.padding(start = 9.dp)) {
        Text(primary, style = SmithType.bodyBold.copy(color = colors.ink))
        lines.forEach { Text(it, style = SmithType.caption.copy(color = colors.inkMuted)) }
    }
}

@Composable
private fun LaborBlock(p: Proposal) {
    val colors = LocalSmithColors.current
    Text("LABOR", style = SmithType.captionBold.copy(color = colors.inkMuted))
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(p.laborLine.description, style = SmithType.body.copy(color = colors.ink))
            Text(
                "${"%.1f".format(p.laborLine.estimatedHours)} hrs × $${"%.2f".format(p.laborLine.hourlyRate)}",
                style = SmithType.caption.copy(color = colors.inkMuted).tabular
            )
        }
        Text("$${"%.2f".format(p.laborLine.total)}", style = SmithType.bodyBold.copy(color = colors.ink).tabular)
    }
}

@Composable
private fun MaterialsBlock(p: Proposal) {
    val colors = LocalSmithColors.current
    Text("MATERIALS", style = SmithType.captionBold.copy(color = colors.inkMuted))
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        p.materialLines.forEach { m ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(m.name, style = SmithType.body.copy(color = colors.ink))
                    Text(
                        "${fmtQty(m.quantity)} ${m.unit} × $${"%.2f".format(m.unitCost)}",
                        style = SmithType.caption.copy(color = colors.inkMuted).tabular
                    )
                    m.notes?.let {
                        Text(it, style = SmithType.caption.copy(color = colors.inkMuted))
                    }
                }
                Text("$${"%.2f".format(m.total)}", style = SmithType.body.copy(color = colors.ink).tabular)
            }
        }
        val matTotal = p.materialLines.sumOf { it.total }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Materials subtotal", style = SmithType.caption.copy(color = colors.inkMuted))
            Text("$${"%.2f".format(matTotal)}", style = SmithType.bodyBold.copy(color = colors.ink).tabular)
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
    val colors = LocalSmithColors.current
    val style = if (bold) SmithType.bodyBold else SmithType.body
    val color = if (accent) colors.accent else colors.ink
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label + ":", style = style.copy(color = color))
        Text(
            "$${"%.2f".format(amount)}",
            style = style.copy(color = color).tabular,
            modifier = Modifier.width(100.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun TimelineBlock(p: Proposal) {
    val colors = LocalSmithColors.current
    Text("TIMELINE", style = SmithType.captionBold.copy(color = colors.inkMuted))
    Column(modifier = Modifier.padding(start = 9.dp)) {
        Text(
            "Estimated ${p.timelineDays} working day${if (p.timelineDays == 1) "" else "s"}",
            style = SmithType.body.copy(color = colors.ink)
        )
        Text(
            "Start: ${p.startEstimate}",
            style = SmithType.caption.copy(color = colors.inkMuted)
        )
    }
}

@Composable
private fun TextBlock(label: String, body: String) {
    val colors = LocalSmithColors.current
    Text(label, style = SmithType.captionBold.copy(color = colors.inkMuted))
    Column(modifier = Modifier.padding(start = 9.dp)) {
        Text(body, style = SmithType.body.copy(color = colors.ink))
    }
}

@Composable
private fun ListBlock(label: String, items: List<String>) {
    val colors = LocalSmithColors.current
    Text(label, style = SmithType.captionBold.copy(color = colors.inkMuted))
    Column(modifier = Modifier.padding(start = 9.dp)) {
        items.forEach { Text("- $it", style = SmithType.body.copy(color = colors.ink)) }
    }
}

@Composable
private fun SignatureBlock() {
    val colors = LocalSmithColors.current
    Column {
        Text("ACCEPTANCE", style = SmithType.captionBold.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("_____________________", style = SmithType.body.copy(color = colors.ink))
                Text("Client signature", style = SmithType.caption.copy(color = colors.inkMuted))
            }
            Column(modifier = Modifier.width(120.dp)) {
                Text("___________", style = SmithType.body.copy(color = colors.ink))
                Text("Date", style = SmithType.caption.copy(color = colors.inkMuted))
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
