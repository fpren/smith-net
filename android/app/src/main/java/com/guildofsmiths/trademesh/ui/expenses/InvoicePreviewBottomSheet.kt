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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.guildofsmiths.trademesh.data.BolLegalPreferences
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.LegalFooterScope
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicePreviewBottomSheet(
    invoice: Invoice,
    job: Job,
    timeEntries: List<TimeEntry>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val legal by BolLegalPreferences.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ConsoleTheme.surface
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
                style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent)
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
                Text(hint, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
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
    Text(
        text.uppercase(),
        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted)
    )
}

@Composable
private fun <T> RadioRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                modifier = Modifier
                    .background(
                        if (isSel) ConsoleTheme.accent else ConsoleTheme.background,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        0.5.dp,
                        if (isSel) ConsoleTheme.accent else ConsoleTheme.text.copy(alpha = 0.12f),
                        RoundedCornerShape(4.dp)
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    style = ConsoleTheme.caption.copy(
                        color = if (isSel) Color.White else ConsoleTheme.text
                    )
                )
            }
        }
    }
}

@Composable
private fun Checkbox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
                    if (checked) ConsoleTheme.accent else ConsoleTheme.background,
                    RoundedCornerShape(2.dp)
                )
                .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Text("✓", style = ConsoleTheme.caption.copy(color = Color.White))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text))
    }
}

@Composable
private fun ActionBtn(label: String, accent: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                if (accent) ConsoleTheme.accent.copy(alpha = 0.14f) else ConsoleTheme.surface,
                RoundedCornerShape(4.dp)
            )
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = ConsoleTheme.action.copy(
                color = if (accent) ConsoleTheme.accent else ConsoleTheme.text
            )
        )
    }
}

@Composable
private fun HtmlPreview(html: String, modifier: Modifier = Modifier) {
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
            .background(ConsoleTheme.background)
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.15f))
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
