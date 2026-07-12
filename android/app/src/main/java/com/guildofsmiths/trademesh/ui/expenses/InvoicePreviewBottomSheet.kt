package com.guildofsmiths.trademesh.ui.expenses

import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.guildofsmiths.trademesh.data.BolLegalPreferences
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.LegalFooterScope
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithSheet
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import java.io.File

@Composable
fun InvoicePreviewBottomSheet(
    invoice: Invoice,
    job: Job,
    timeEntries: List<TimeEntry>,
    onDismiss: () -> Unit
) {
    val colors = LocalSmithColors.current
    val context = LocalContext.current
    val legal by BolLegalPreferences.state.collectAsState()

    var mode by remember { mutableStateOf(OutputMode.INVOICE_AND_BOL) }
    var scope by remember { mutableStateOf(autoDefaultScope(job)) }
    var approval by remember { mutableStateOf(DraftApproval()) }

    val html by remember(mode, scope, approval, legal, invoice, job, timeEntries) {
        mutableStateOf(
            InvoiceBolHtmlRenderer.render(
                invoice = invoice,
                job = job,
                timeEntries = timeEntries,
                mode = mode,
                scope = scope,
                approval = approval,
                legal = legal
            )
        )
    }

    SmithSheet(
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "PREVIEW & SHARE",
                style = SmithType.captionBold.copy(color = colors.accent)
            )

            // Output mode
            SectionLabel("Output")
            RadioRow(
                options = OutputMode.entries.map { it to it.label },
                selected = mode,
                onSelect = { mode = it }
            )

            // Legal footer scope (only if BOL is in output)
            if (mode != OutputMode.INVOICE_ONLY) {
                SectionLabel("Legal footer scope")
                RadioRow(
                    options = listOf(
                        LegalFooterScope.DOMESTIC to "Domestic",
                        LegalFooterScope.INTERNATIONAL to "International",
                        LegalFooterScope.BOTH to "Both (auto)"
                    ),
                    selected = scope,
                    onSelect = { scope = it }
                )
                val hint = when (scope) {
                    LegalFooterScope.DOMESTIC -> "US Domestic + US States only · international clauses suppressed"
                    LegalFooterScope.INTERNATIONAL -> "CISG · Incoterms · UNCITRAL · carrier conventions · US suppressed"
                    LegalFooterScope.BOTH -> "every enabled preset — recommended for mixed US + non-US engagements"
                }
                Text(hint, style = SmithType.caption.copy(color = colors.inkMuted))
            }

            // Draft approval checkboxes
            SectionLabel("Include draft content")
            Checkbox("Daily narrative", approval.includeDailyNarrative) {
                approval = approval.copy(includeDailyNarrative = it)
            }
            Checkbox("Compliance notes", approval.includeComplianceNotes) {
                approval = approval.copy(includeComplianceNotes = it)
            }
            Checkbox("Recommendations", approval.includeRecommendations) {
                approval = approval.copy(includeRecommendations = it)
            }
            Checkbox("Work-log summary", approval.includeWorkLogSummary) {
                approval = approval.copy(includeWorkLogSummary = it)
            }

            // Preview
            SectionLabel("Preview")
            HtmlPreview(html = html, modifier = Modifier.fillMaxWidth().height(360.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionBtn("[Share as HTML]", accent = true, modifier = Modifier.weight(1f)) {
                    shareAsHtml(context, html, invoice.invoiceNumber, mode)
                }
                ActionBtn("[Export PDF]", accent = false, modifier = Modifier.weight(1f)) {
                    exportAsPdf(context, html, invoice.invoiceNumber, mode)
                }
                ActionBtn("[Close]", accent = false, modifier = Modifier.weight(1f)) { onDismiss() }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalSmithColors.current
    Text(
        text.uppercase(),
        style = SmithType.captionBold.copy(color = colors.inkMuted)
    )
}

@Composable
private fun <T> RadioRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    val colors = LocalSmithColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                modifier = Modifier
                    .background(
                        if (isSel) colors.accent else colors.bgBase,
                        RoundedCornerShape(Tokens2.RadiusPill)
                    )
                    .border(
                        0.5.dp,
                        if (isSel) colors.accent else colors.ink.copy(alpha = 0.12f),
                        RoundedCornerShape(Tokens2.RadiusPill)
                    )
                    .clip(RoundedCornerShape(Tokens2.RadiusPill))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    style = SmithType.caption.copy(
                        color = if (isSel) colors.inkOnAccent else colors.ink
                    )
                )
            }
        }
    }
}

@Composable
private fun Checkbox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(
                    if (checked) colors.accent else colors.bgBase,
                    RoundedCornerShape(Tokens2.RadiusTiny)
                )
                .border(0.5.dp, colors.ink.copy(alpha = 0.3f), RoundedCornerShape(Tokens2.RadiusTiny)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Text("✓", style = SmithType.caption.copy(color = colors.inkOnAccent))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = SmithType.bodySmall.copy(color = colors.ink))
    }
}

@Composable
private fun ActionBtn(label: String, accent: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalSmithColors.current
    Box(
        modifier = modifier
            .background(
                if (accent) colors.accent.copy(alpha = 0.14f) else colors.bgPanel,
                RoundedCornerShape(Tokens2.RadiusControl)
            )
            .border(0.5.dp, colors.ink.copy(alpha = 0.12f), RoundedCornerShape(Tokens2.RadiusControl))
            .clip(RoundedCornerShape(Tokens2.RadiusControl))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = SmithType.action.copy(
                color = if (accent) colors.accent else colors.ink
            )
        )
    }
}

@Composable
private fun HtmlPreview(html: String, modifier: Modifier = Modifier) {
    val colors = LocalSmithColors.current
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
            }
        },
        update = { wv -> wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) },
        modifier = modifier
            .background(colors.bgBase)
            .border(0.5.dp, colors.ink.copy(alpha = 0.15f))
    )
}

private fun autoDefaultScope(job: Job): LegalFooterScope {
    // Simple heuristic: client address or phone suggests country.
    // If "United States" or state abbreviations present → DOMESTIC.
    // If no US markers and any non-US marker → INTERNATIONAL. Otherwise BOTH.
    val addr = (job.clientAddress + " " + (job.clientName ?: "")).lowercase()
    val hasUsMarker = addr.contains("united states") || addr.contains(", us") ||
        Regex("\\b(al|ak|az|ar|ca|co|ct|de|fl|ga|hi|id|il|in|ia|ks|ky|la|me|md|ma|mi|mn|ms|mo|mt|ne|nv|nh|nj|nm|ny|nc|nd|oh|ok|or|pa|ri|sc|sd|tn|tx|ut|vt|va|wa|wv|wi|wy)\\b").containsMatchIn(addr)
    val hasNonUsMarker = Regex("\\b(canada|mexico|united kingdom|germany|france|italy|spain|netherlands|australia|china|japan|india|brazil)\\b").containsMatchIn(addr)
    return when {
        hasUsMarker && !hasNonUsMarker -> job.legalFooterScope.takeIf { it != LegalFooterScope.BOTH } ?: LegalFooterScope.DOMESTIC
        hasNonUsMarker && !hasUsMarker -> LegalFooterScope.INTERNATIONAL
        else -> job.legalFooterScope
    }
}

private fun shareAsHtml(context: android.content.Context, html: String, invoiceNumber: String, mode: OutputMode) {
    try {
        val suffix = when (mode) {
            OutputMode.INVOICE_ONLY -> "invoice"
            OutputMode.BOL_ONLY -> "bol"
            OutputMode.INVOICE_AND_BOL -> "invoice-bol"
        }
        val dir = File(context.cacheDir, "shared-docs").apply { mkdirs() }
        val file = File(dir, "$invoiceNumber-$suffix.html")
        file.writeText(html)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$invoiceNumber — $suffix")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Share").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (t: Throwable) {
        Log.e("InvoicePreviewSheet", "share failed", t)
        // Fallback: plain-text share of the HTML string (renders poorly but at least goes through).
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, html)
            putExtra(Intent.EXTRA_SUBJECT, invoiceNumber)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(fallback, "Share"))
    }
}

private fun exportAsPdf(context: android.content.Context, html: String, invoiceNumber: String, mode: OutputMode) {
    try {
        val wv = WebView(context)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
                val suffix = when (mode) {
                    OutputMode.INVOICE_ONLY -> "invoice"
                    OutputMode.BOL_ONLY -> "bol"
                    OutputMode.INVOICE_AND_BOL -> "invoice-bol"
                }
                val jobName = "$invoiceNumber-$suffix"
                val adapter = view.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, adapter, PrintAttributes.Builder().build())
            }
        }
        wv.settings.javaScriptEnabled = false
        wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    } catch (t: Throwable) {
        Log.e("InvoicePreviewSheet", "pdf export failed", t)
    }
}
