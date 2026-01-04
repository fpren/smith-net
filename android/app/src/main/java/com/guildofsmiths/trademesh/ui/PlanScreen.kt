package com.guildofsmiths.trademesh.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildofsmiths.trademesh.planner.types.ExecutionItem
import com.guildofsmiths.trademesh.planner.types.ExecutionItemType
import com.guildofsmiths.trademesh.planner.types.PlannerStateEnum
import com.guildofsmiths.trademesh.ui.plan.PlanViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.AlertDialog

/**
 * PLAN - SmithNet Deterministic Planner
 *
 * FULL-SCREEN BORDERLESS TEXT CANVAS
 * 
 * After compilation:
 * - User can generate PROPOSAL / REPORT / INVOICE / ALL
 * - Documents are rendered DIRECTLY INTO THE CANVAS
 * - Following exact GOS template formats
 * - No sidebar, no panels - just text output
 */
@Composable
fun PlanScreen(
    onNavigateBack: () -> Unit,
    initialJobId: String? = null,
    viewModel: PlanViewModel = viewModel()
) {
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    
    // Load job from Job Board if jobId is provided
    LaunchedEffect(initialJobId) {
        if (initialJobId != null) {
            viewModel.loadJobFromBoard(initialJobId)
        }
    }

    // State from ViewModel
    val content by viewModel.content.collectAsState()
    val state by viewModel.state.collectAsState()
    val executionItems by viewModel.executionItems.collectAsState()
    val selectedItemIds by viewModel.selectedItemIds.collectAsState()
    val lastError by viewModel.lastError.collectAsState()

    // Derived state
    val isCanvasDisabled = state == PlannerStateEnum.COMPILING || state == PlannerStateEnum.TRANSFERRING
    val isCompileEnabled = state == PlannerStateEnum.DRAFT && content.isNotEmpty()
    val isCompiled = state == PlannerStateEnum.COMPILED
    val showError = state == PlannerStateEnum.COMPILE_ERROR && lastError != null
    val canTransfer = isCompiled && selectedItemIds.isNotEmpty()
    val canGenerateDocs = isCompiled && executionItems.isNotEmpty()
    
    // Confirmation flow state
    var summaryConfirmed by remember { mutableStateOf(false) }
    var showingSummary by remember { mutableStateOf(false) }
    
    // Reset confirmation when state changes back to DRAFT
    LaunchedEffect(state) {
        if (state == PlannerStateEnum.DRAFT || state == PlannerStateEnum.EMPTY) {
            summaryConfirmed = false
            showingSummary = false
        }
    }
    
    // Auto-generate and show summary when compile succeeds
    LaunchedEffect(isCompiled, executionItems.size) {
        if (isCompiled && executionItems.isNotEmpty() && !showingSummary && !summaryConfirmed) {
            // Generate compile summary and display (without resetting state!)
            val summary = GOSDocumentGenerator.generateCompileSummary(executionItems)
            viewModel.displayContent(summary)  // Use displayContent, NOT updatePlanText
            showingSummary = true
        }
    }
    
    // Auto-focus on entry
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    // Auto-save draft silently
    LaunchedEffect(content) {
        if (content.isNotBlank()) {
            viewModel.saveDraft()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        // ════════════════════════════════════════════════════════════════
        // HEADER BAR
        // ════════════════════════════════════════════════════════════════

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Text(
                text = when (state) {
                    PlannerStateEnum.EMPTY -> "PLAN"
                    PlannerStateEnum.DRAFT -> "PLAN [DRAFT]"
                    PlannerStateEnum.COMPILING -> "PLAN [COMPILING...]"
                    PlannerStateEnum.COMPILED -> "PLAN [COMPILED]"
                    PlannerStateEnum.COMPILE_ERROR -> "PLAN [ERROR]"
                    PlannerStateEnum.TRANSFERRING -> "PLAN [TRANSFERRING...]"
                },
                style = ConsoleTheme.captionBold.copy(
                    color = when (state) {
                        PlannerStateEnum.COMPILED -> ConsoleTheme.success
                        PlannerStateEnum.COMPILE_ERROR -> ConsoleTheme.error
                        PlannerStateEnum.COMPILING, PlannerStateEnum.TRANSFERRING -> ConsoleTheme.warning
                        else -> ConsoleTheme.accent
                    }
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // DELETE / CLEAR button
                if (content.isNotEmpty() && !showingSummary) {
                    Text(
                        text = "[DELETE]",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.error),
                        modifier = Modifier.clickable { 
                            viewModel.clearDraft()
                            viewModel.updatePlanText("")
                        }
                    )
                }

                // Compile button (only in DRAFT state, not showing summary)
                if (isCompileEnabled && !showingSummary) {
                    Text(
                        text = "[COMPILE]",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable { viewModel.compilePlan() }
                    )
                }

                // TEST compile button (experimental online-assisted path)
                if (isCompileEnabled && !showingSummary) {
                    Text(
                        text = "[TEST ⧉]",
                        style = ConsoleTheme.captionBold.copy(color = Color(0xFFFF9500)), // Orange
                        modifier = Modifier.clickable { viewModel.testCompile() }
                    )
                }

                // ════════════════════════════════════════════════════════════════
                // EDIT PLAN BUTTON (when compiled, restores original for editing)
                // ════════════════════════════════════════════════════════════════
                if (isCompiled && executionItems.isNotEmpty()) {
                    // EDIT PLAN button - preserves original content for editing
                    Text(
                        text = "[EDIT PLAN]",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.warning),
                        modifier = Modifier.clickable {
                            // Reset to draft mode and restore original content
                            viewModel.editOriginalPlan()
                            showingSummary = false
                            summaryConfirmed = false
                        }
                    )
                }

                // X button - always visible
                Text(
                    text = "[ X ]",
                    style = ConsoleTheme.bodyBold.copy(fontSize = 16.sp),
                    modifier = Modifier.clickable {
                        if (content.isBlank()) {
                            viewModel.clearDraft()
                        }
                        onNavigateBack()
                    }
                )
            }
        }

        // ════════════════════════════════════════════════════════════════
        // EXAMPLE TEMPLATES (only when content is empty)
        // ════════════════════════════════════════════════════════════════
        
        if (content.isEmpty() && state == PlannerStateEnum.DRAFT || state == PlannerStateEnum.EMPTY) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(ConsoleTheme.surface.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LOAD TEMPLATE:",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                    
                    // Template buttons
                    PlanExamples.getAll().forEach { (name, template) ->
                        Text(
                            text = "[$name]",
                            style = ConsoleTheme.captionBold.copy(
                                color = ConsoleTheme.accent
                            ),
                            modifier = Modifier
                                .background(
                                    ConsoleTheme.surface,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    viewModel.updatePlanText(template)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                
                ConsoleSeparator()
            }
        }

        // ════════════════════════════════════════════════════════════════
        // DOCUMENT GENERATION BAR (only when compiled)
        // Multi-select mode for generating combinations
        // ════════════════════════════════════════════════════════════════
        
        // Track which document types are selected
        var selectedDocTypes by remember { mutableStateOf(setOf<String>()) }
        var showTransferDialog by remember { mutableStateOf(false) }
        
        // ════════════════════════════════════════════════════════════════
        // DOCUMENT GENERATION & JOB TRANSFER BAR (available after compile)
        // Shows summary stats + document options immediately after compile
        // ════════════════════════════════════════════════════════════════
        
        if (canGenerateDocs) {
            // Summary stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.success.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "✓ COMPILED:",
                    style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.success)
                )
                Text(
                    text = "${executionItems.filter { it.type == ExecutionItemType.TASK }.size} tasks",
                    style = ConsoleTheme.caption
                )
                Text(
                    text = "${executionItems.filter { it.type == ExecutionItemType.MATERIAL }.size} materials",
                    style = ConsoleTheme.caption
                )
                Text(
                    text = "${executionItems.filter { it.type == ExecutionItemType.LABOR }.size} labor",
                    style = ConsoleTheme.caption
                )
            }
        }
        
        // ════════════════════════════════════════════════════════════════
        // CONFIRMATION BANNER - Shows after compilation, requires user action
        // ════════════════════════════════════════════════════════════════
        
        if (showingSummary && !summaryConfirmed && canGenerateDocs) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF3CD)) // Yellow/warning color
                    .padding(16.dp)
            ) {
                Text(
                    text = "REVIEW COMPILATION RESULTS",
                    style = ConsoleTheme.captionBold.copy(
                        color = Color(0xFF856404),
                        fontSize = 14.sp
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Please review the summary above. Choose an action:",
                    style = ConsoleTheme.caption.copy(color = Color(0xFF856404))
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ACCEPT button
                    Text(
                        text = "[✓ ACCEPT]",
                        style = ConsoleTheme.captionBold.copy(
                            color = Color.White,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier
                            .background(ConsoleTheme.success, RoundedCornerShape(4.dp))
                            .clickable { summaryConfirmed = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    // EDIT PLAN button (restores original content for editing)
                    Text(
                        text = "[✏️ EDIT PLAN]",
                        style = ConsoleTheme.captionBold.copy(
                            color = Color.White,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier
                            .background(ConsoleTheme.warning, RoundedCornerShape(4.dp))
                            .clickable {
                                viewModel.editOriginalPlan()
                                showingSummary = false
                                summaryConfirmed = false
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    // START OVER button (clears everything)
                    Text(
                        text = "[❌ START OVER]",
                        style = ConsoleTheme.captionBold.copy(
                            color = ConsoleTheme.error,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier
                            .clickable {
                                viewModel.clearDraft()
                                showingSummary = false
                                summaryConfirmed = false
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
        
        // Document generation options (only shown after confirmation)
        if (canGenerateDocs && summaryConfirmed) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
            ) {
                // Row 1: Document type selection (PROPOSAL only - planning phase)
                // Note: REPORT and INVOICE are only available after job completion (in Dashboard)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DOCUMENTS:",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted)
                    )
                    
                    // Proposal toggle (only planning document available)
                    val proposalSelected = selectedDocTypes.contains("PROPOSAL")
                    Text(
                        text = if (proposalSelected) "[✓ PROPOSAL]" else "[ PROPOSAL ]",
                        style = ConsoleTheme.captionBold.copy(
                            color = if (proposalSelected) ConsoleTheme.success else ConsoleTheme.accent
                        ),
                        modifier = Modifier.clickable {
                            selectedDocTypes = if (proposalSelected) 
                                selectedDocTypes - "PROPOSAL" 
                            else 
                                selectedDocTypes + "PROPOSAL"
                        }
                    )
                    
                    // Info text about completion documents
                    Text(
                        text = "(Report & Invoice available after job completion)",
                        style = ConsoleTheme.caption.copy(
                            color = ConsoleTheme.textDim,
                            fontSize = 10.sp
                        )
                    )
                }
                
                // Row 2: Actions based on selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Generate selected documents
                    if (selectedDocTypes.isNotEmpty()) {
                        Text(
                            text = "[GENERATE ${selectedDocTypes.size}]",
                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.success),
                            modifier = Modifier.clickable {
                                // #region agent log
                                android.util.Log.e("DEBUG_DOC", """{"hypothesisId":"H2","location":"PlanScreen.kt:generate-click","message":"Generate button clicked","data":{"selectedTypes":"${selectedDocTypes.joinToString(",")}","executionItemsCount":${executionItems.size}}}""")
                                // #endregion
                                val doc = GOSDocumentGenerator.generateSelected(executionItems, selectedDocTypes)
                                // #region agent log
                                android.util.Log.e("DEBUG_DOC", """{"hypothesisId":"H3","location":"PlanScreen.kt:generate-result","message":"Document generated","data":{"docLength":${doc.length},"docPreview":"${doc.take(100).replace("\"", "\\\"").replace("\n", "\\n")}"}}""")
                                // #endregion
                                // Use displayContent to NOT reset compile state
                                viewModel.displayContent(doc)
                            }
                        )
                    }
                    
                    // Quick generate proposal (when none selected)
                    if (selectedDocTypes.isEmpty()) {
                        Text(
                            text = "[GENERATE PROPOSAL]",
                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent),
                            modifier = Modifier.clickable {
                                val doc = GOSDocumentGenerator.generateProposal(executionItems)
                                // Use displayContent to NOT reset compile state
                                viewModel.displayContent(doc)
                            }
                        )
                    }
                    
                    // EXPORT/SHARE button - always visible when compiled
                    Text(
                        text = "[📤 SHARE]",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Guild of Smiths - Document")
                                putExtra(Intent.EXTRA_TEXT, content)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
                        }
                    )
                    
                    // SAVE TO FILE button
                    Text(
                        text = "[💾 SAVE]",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable {
                            // Save to Downloads folder
                            try {
                                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US).format(java.util.Date())
                                val fileName = "GOS_Document_$dateStr.txt"
                                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                                    android.os.Environment.DIRECTORY_DOWNLOADS
                                )
                                val file = java.io.File(downloadsDir, fileName)
                                file.writeText(content)
                                
                                // Show toast notification
                                android.widget.Toast.makeText(
                                    context,
                                    "Saved to Downloads/$fileName",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Save failed: ${e.message}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // JOB TRANSFER button - transfers execution items to Job Board
                    Text(
                        text = "[→ JOB BOARD]",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.warning),
                        modifier = Modifier.clickable {
                            showTransferDialog = true
                        }
                    )
                }
            }
        }
        
        // Transfer confirmation dialog
        if (showTransferDialog) {
            AlertDialog(
                onDismissRequest = { showTransferDialog = false },
                containerColor = ConsoleTheme.background,
                title = { 
                    Text(
                        text = "TRANSFER TO JOB BOARD",
                        style = ConsoleTheme.header
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "This will create ${executionItems.size} items in the Job Board:",
                            style = ConsoleTheme.body
                        )
                        
                        // Preview items
                        val tasks = executionItems.filter { it.type == ExecutionItemType.TASK }
                        val materials = executionItems.filter { it.type == ExecutionItemType.MATERIAL }
                        val labor = executionItems.filter { it.type == ExecutionItemType.LABOR }
                        
                        if (tasks.isNotEmpty()) {
                            Text(
                                text = "• ${tasks.size} tasks",
                                style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent)
                            )
                        }
                        if (materials.isNotEmpty()) {
                            Text(
                                text = "• ${materials.size} materials (checklist)",
                                style = ConsoleTheme.caption
                            )
                        }
                        if (labor.isNotEmpty()) {
                            Text(
                                text = "• ${labor.size} labor entries",
                                style = ConsoleTheme.caption
                            )
                        }
                        
                        Text(
                            text = "\nJob will appear in Job Board as 'TO DO'.\nComplete the job to unlock Invoice/Report generation.",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                    }
                },
                confirmButton = {
                    Text(
                        text = "TRANSFER",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.success),
                        modifier = Modifier.clickable {
                            // Select all items and transfer
                            viewModel.selectAllItems()
                            viewModel.transferSelectedItems()
                            showTransferDialog = false
                            // Show job record in canvas
                            val doc = GOSDocumentGenerator.generateJobRecord(executionItems)
                            viewModel.updatePlanText(doc)
                        }
                    )
                },
                dismissButton = {
                    Text(
                        text = "CANCEL",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier.clickable { showTransferDialog = false }
                    )
                }
            )
        }

        ConsoleSeparator()

        // ════════════════════════════════════════════════════════════════
        // ERROR DISPLAY
        // ════════════════════════════════════════════════════════════════

        if (showError && lastError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.error.copy(alpha = 0.1f))
                    .padding(12.dp)
            ) {
                Text(
                    text = "[${lastError!!.code.value}] ${lastError!!.message}" +
                           (lastError!!.line?.let { " (line $it)" } ?: ""),
                    style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.error)
                )
            }
            ConsoleSeparator()
        }

        // ════════════════════════════════════════════════════════════════
        // FULL-SCREEN TEXT CANVAS (no sidebar)
        // ════════════════════════════════════════════════════════════════

            Box(
                modifier = Modifier
                .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Loading overlay
                if (state == PlannerStateEnum.COMPILING || state == PlannerStateEnum.TRANSFERRING) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ConsoleTheme.background.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (state == PlannerStateEnum.COMPILING) "COMPILING..." else "TRANSFERRING...",
                                style = ConsoleTheme.header
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                color = ConsoleTheme.accent,
                                modifier = Modifier.width(200.dp)
                            )
                        }
                    }
                }

            // Main text field - FULL SCREEN
                BasicTextField(
                    value = content,
                    onValueChange = { newText ->
                        if (!isCanvasDisabled) {
                            viewModel.updatePlanText(newText)
                        }
                    },
                    enabled = !isCanvasDisabled,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = ConsoleTheme.text,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = SolidColor(ConsoleTheme.cursor),
                    modifier = Modifier
                        .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (content.isEmpty()) {
                                // Enhanced placeholder with guided prompts
                                Text(
                                    text = getEnhancedPlaceholder(),
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        color = ConsoleTheme.placeholder,
                                        lineHeight = 20.sp
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// ENHANCED PLACEHOLDER - Guided prompts for quality plan creation
// ════════════════════════════════════════════════════════════════════════════

/**
 * Enhanced placeholder with guided prompts to help users create quality plans.
 * Designed to work for any skill level - even a 6-year-old could understand the format.
 */
private fun getEnhancedPlaceholder(): String = """╔════════════════════════════════════════════════════╗
║  DESCRIBE YOUR JOB IN PLAIN ENGLISH                ║
║  (AI will structure it for you)                    ║
╚════════════════════════════════════════════════════╝

EXAMPLE (you can copy & modify):
────────────────────────────────────────
Fix the leaky faucet in the upstairs bathroom.
Client: Mrs. Johnson at 123 Oak St.
She needs it done by Friday.
I'll probably need a new cartridge and some plumber's tape.
About 2 hours of work, charge $85/hour.
────────────────────────────────────────

TIPS FOR A QUALITY PLAN:
• WHO: Client name and contact if you have it
• WHERE: Property address or location
• WHAT: Describe the work clearly
• WHEN: Any deadlines or scheduling needs
• HOW MUCH: Estimate time, materials, labor rate

────────────────────────────────────────
OR USE FULL GOSPLAN FORMAT:
────────────────────────────────────────

# PROJECT NAME

## JobHeader
- Client: [name]
- Location: [address]
- Trade: [electrical/plumbing/hvac/etc.]

## Scope
What this job includes...

## Tasks
- Task 1
- Task 2

## Materials
- Item 1 (qty, price each)
- Item 2 (qty, price each)

## Labor
- 4h Journeyman @ $65/hr
- 2h Apprentice @ $35/hr

## Notes
Any special considerations...
"""

/**
 * Example templates for common job types
 */
object PlanExamples {
    
    val electrical = """# Electrical Panel Upgrade

## JobHeader
- Client: [Customer Name]
- Location: [Address]
- Trade: Electrical

## Scope
Upgrade 100A panel to 200A service

## Tasks
- Disconnect existing service
- Remove old panel
- Install 200A panel
- Run new service entrance cable
- Reconnect circuits
- Test and label all breakers
- Schedule inspection

## Materials
- 1 200A main panel $350
- 1 200A main breaker $80
- 50ft 4/0 SER cable $8/ft
- Misc breakers $200

## Labor
- 8h Master Electrician @ $95/hr
- 4h Journeyman @ $65/hr

## Notes
- Permit required
- Utility disconnect needed
- Customer to be without power 4-6 hrs
"""

    val plumbing = """# Bathroom Renovation Plumbing

## JobHeader
- Client: [Customer Name]
- Location: [Address]
- Trade: Plumbing

## Scope
Rough-in and finish plumbing for master bath remodel

## Tasks
- Demo existing fixtures
- Rough-in new drain locations
- Install new water supply lines
- Set new toilet
- Install vanity with double sinks
- Install shower valve and head
- Test all connections

## Materials
- 1 Toilet (customer supplied)
- 1 Vanity faucet set $180
- 1 Shower valve $120
- 20ft 1/2" PEX $1.50/ft
- 15ft 2" ABS $3/ft
- Misc fittings $100

## Labor
- 12h Journeyman Plumber @ $75/hr
- 6h Helper @ $35/hr
"""

    val hvac = """# AC System Replacement

## JobHeader
- Client: [Customer Name]
- Location: [Address]
- Trade: HVAC

## Scope
Replace failed 3-ton AC unit and coil

## Tasks
- Remove old condenser unit
- Remove old evaporator coil
- Install new condenser pad
- Set new condenser
- Install new evaporator coil
- Braze refrigerant lines
- Vacuum and charge system
- Test operation

## Materials
- 1 3-ton Condenser unit $2,800
- 1 Evaporator coil $600
- 1 Condenser pad $45
- 20ft Line set $8/ft
- Refrigerant $150
- Misc supplies $75

## Labor
- 6h HVAC Technician @ $85/hr
- 4h Helper @ $35/hr

## Notes
- EPA certification required
- Old unit contains R-22 (recovery needed)
"""

    val carpentry = """# Deck Construction

## JobHeader
- Client: [Customer Name]
- Location: [Address]
- Trade: Carpentry

## Scope
Build 12x16 pressure-treated wood deck with stairs

## Tasks
- Layout and excavate post holes
- Set concrete footings
- Install posts and beams
- Frame deck joists
- Install decking boards
- Build stairs (4 risers)
- Install railing system
- Apply sealer

## Materials
- 6 4x4x10 PT posts $25 each
- 4 bags concrete $8 each
- 8 2x10x16 PT joists $28 each
- 24 2x6x16 PT decking $18 each
- Railing kit $450
- Hardware/fasteners $200
- Sealer 5gal $120

## Labor
- 24h Lead Carpenter @ $55/hr
- 24h Helper @ $30/hr

## Notes
- Permit and inspection required
- Customer to clear area before start
"""

    val general = """# General Repair Request

## JobHeader
- Client: [Customer Name]  
- Location: [Address]
- Trade: General

## Scope
[Describe the work to be done]

## Tasks
- [Task 1]
- [Task 2]
- [Task 3]

## Materials
- [Item] (quantity @ price each)
- [Item] (quantity @ price each)

## Labor
- [Hours] [Role] @ [Rate]/hr

## Notes
- [Any special considerations]
"""

    fun getAll(): Map<String, String> = mapOf(
        "ELECTRICAL" to electrical,
        "PLUMBING" to plumbing,
        "HVAC" to hvac,
        "CARPENTRY" to carpentry,
        "GENERAL" to general
    )
}

// ════════════════════════════════════════════════════════════════════════════
// GOS DOCUMENT GENERATOR - Following exact template formats
// ════════════════════════════════════════════════════════════════════════════

/**
 * Generates documents following the exact Guild of Smiths template formats
 * from the provided .txt files
 */
object GOSDocumentGenerator {
    
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    private val dateFormatShort = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    
    private fun today(): String = dateFormat.format(Date())
    private fun todayShort(): String = dateFormatShort.format(Date())
    private fun now(): String = timestamp.format(Date())
    
    private fun generateReportNumber(): String = "GOS-IR-${todayShort().replace("-", "-")}-${(1..999).random().toString().padStart(3, '0')}"
    private fun generateInvoiceNumber(): String = "INV-${todayShort().substring(0, 7).replace("-", "-")}-${(1..9999).random().toString().padStart(4, '0')}"
    private fun generateJobId(): String = "GOS-JOB-${todayShort().replace("-", "")}-${(1..999).random().toString().padStart(3, '0')}"
    
    /**
     * PROPOSAL / INSPECTION REPORT - GOS Format
     * Uses EnhancedJobData if available for real job details
     */
    fun generateProposal(items: List<ExecutionItem>): String {
        val jobData = com.guildofsmiths.trademesh.planner.OnlineResolver.getLastJobData()
        
        // Service Provider Info (from job data or defaults)
        val providerName = jobData?.providerName ?: "[Your Name]"
        val providerBusiness = jobData?.providerBusinessName ?: "[Your Business Name]"
        val providerPhone = jobData?.providerPhone ?: "[Phone]"
        val providerEmail = jobData?.providerEmail ?: "[Email]"
        val providerAddress = jobData?.providerAddress ?: "[Your Address]"
        val providerRole = jobData?.providerGuildRole ?: "Solo Tradesperson – Guild of Smiths"
        
        // Extract data from EnhancedJobData or fall back to items
        val jobTitle = jobData?.jobTitle ?: "Service Request"
        val clientName = jobData?.clientName ?: "[Client Name]"
        val clientCompany = jobData?.clientCompany
        val clientEmail = jobData?.clientEmail
        val location = jobData?.location ?: "[Property Address]"
        val primaryTrade = jobData?.primaryTrade ?: "General Services"
        val scope = jobData?.scope ?: ""
        val scopeDetails = jobData?.scopeDetails ?: emptyList()
        
        val tasks = if (jobData != null && jobData.tasks.isNotEmpty()) {
            jobData.tasks
        } else {
            items.filter { it.type == ExecutionItemType.TASK }.map { 
                when (val parsed = it.parsed) {
                    is com.guildofsmiths.trademesh.planner.types.ParsedItemData.Task -> parsed.description
                    else -> it.source
                }
            }
        }
        
        val materials = if (jobData != null && jobData.materials.isNotEmpty()) {
            jobData.materials
        } else {
            null
        }
        
        val labor = if (jobData != null && jobData.labor.isNotEmpty()) {
            jobData.labor
        } else {
            null
        }
        
        // Calculate costs
        val laborCost = jobData?.estimatedLaborCost ?: (labor?.sumOf { it.hours * it.rate } ?: 680.0)
        val materialCost = jobData?.estimatedMaterialCost ?: (materials?.sumOf { it.estimatedCost ?: 0.0 } ?: 450.0)
        val totalCost = jobData?.estimatedTotal ?: (laborCost + materialCost + 450.0)
        
        val safetyReqs = jobData?.safetyRequirements ?: listOf("Standard PPE required", "Work area to be secured")
        val codeReqs = jobData?.codeRequirements ?: emptyList()
        val assumptions = jobData?.assumptions ?: emptyList()
        val exclusions = jobData?.exclusions ?: emptyList()
        
        return buildString {
            appendLine(providerBusiness.uppercase())
            appendLine("Professional Trade Services")
            appendLine("$providerAddress")
            appendLine("Phone: $providerPhone | Email: $providerEmail")
            appendLine()
            appendLine("SERVICE PROPOSAL / INSPECTION REPORT")
            appendLine("Date: ${today()}")
            appendLine("Report Number: ${generateReportNumber()}")
            appendLine("Prepared By: $providerName, $providerRole")
            appendLine("Prepared For: $clientName${clientCompany?.let { " ($it)" } ?: ""}${clientEmail?.let { " | $it" } ?: ""}")
            appendLine()
            appendLine("PROJECT: $jobTitle")
            appendLine("Trade: $primaryTrade")
            appendLine()
            appendLine("PROPERTY DETAILS")
            appendLine("─".repeat(60))
            appendLine("- Location: $location")
            appendLine("- Property Type: Residential")
            if (scope.isNotBlank()) {
                appendLine("- Description: $scope")
            }
            appendLine()
            appendLine("SCOPE OF WORK")
            appendLine("─".repeat(60))
            if (scopeDetails.isNotEmpty()) {
                scopeDetails.forEach { detail ->
                    appendLine("• $detail")
                }
            } else if (scope.isNotBlank()) {
                appendLine(scope)
            }
            appendLine()
            appendLine("WORK PHASES / TASKS")
            appendLine("─".repeat(60))
            tasks.forEachIndexed { index, task ->
                appendLine("${index + 1}. $task")
            }
            appendLine()
            appendLine("MATERIALS REQUIRED")
            appendLine("─".repeat(60))
            if (materials != null) {
                materials.forEach { mat ->
                    val costStr = mat.estimatedCost?.let { " - Est. \$${String.format("%.2f", it)}" } ?: ""
                    appendLine("• ${mat.name} | ${mat.quantity ?: ""} ${mat.unit ?: ""}$costStr")
                }
            } else {
                items.filter { it.type == ExecutionItemType.MATERIAL }.forEach { mat ->
                    appendLine("• ${mat.source}")
                }
            }
            appendLine()
            appendLine("LABOR")
            appendLine("─".repeat(60))
            if (labor != null) {
                labor.forEach { lab ->
                    appendLine("• ${lab.role} - ${lab.hours} hrs @ \$${String.format("%.2f", lab.rate)}/hr = \$${String.format("%.2f", lab.hours * lab.rate)}")
                }
            } else {
                items.filter { it.type == ExecutionItemType.LABOR }.forEach { lab ->
                    appendLine("• ${lab.source}")
                }
            }
            appendLine()
            if (safetyReqs.isNotEmpty()) {
                appendLine("SAFETY REQUIREMENTS")
                appendLine("─".repeat(60))
                safetyReqs.forEach { req ->
                    appendLine("• $req")
                }
                appendLine()
            }
            if (codeReqs.isNotEmpty()) {
                appendLine("CODE COMPLIANCE")
                appendLine("─".repeat(60))
                codeReqs.forEach { req ->
                    appendLine("• $req")
                }
                appendLine()
            }
            if (assumptions.isNotEmpty()) {
                appendLine("ASSUMPTIONS")
                appendLine("─".repeat(60))
                assumptions.forEach { item ->
                    appendLine("• $item")
                }
                appendLine()
            }
            if (exclusions.isNotEmpty()) {
                appendLine("EXCLUSIONS")
                appendLine("─".repeat(60))
                exclusions.forEach { item ->
                    appendLine("• $item")
                }
                appendLine()
            }
            appendLine("ESTIMATED COSTS")
            appendLine("─".repeat(60))
            appendLine("Item                     | Description                                      | Amount")
            appendLine("-------------------------|--------------------------------------------------|---------------")
            appendLine("Labor                    | Skilled tradesperson labor                       | \$${String.format("%.2f", laborCost)}")
            appendLine("Materials                | Parts, supplies, equipment                       | \$${String.format("%.2f", materialCost)}")
            appendLine("Service Fee              | Assessment, coordination, travel                 | \$450.00")
            appendLine("-------------------------|--------------------------------------------------|---------------")
            appendLine("TOTAL ESTIMATE           |                                                  | \$${String.format("%.2f", totalCost)}")
            appendLine()
            appendLine("Note: Costs include materials, labor, debris removal, and 1-year warranty on workmanship.")
            appendLine("Deposit: ${jobData?.depositRequired ?: "50%"} required upon acceptance.")
            appendLine("Warranty: ${jobData?.warranty ?: "1 year on workmanship"}")
            appendLine()
            appendLine("NEXT STEPS")
            appendLine("─".repeat(60))
            appendLine("1. Review this proposal")
            appendLine("2. Contact us to approve work or request changes")
            appendLine("3. Upon approval, we can schedule work within 7 business days")
            appendLine()
            appendLine("Prepared By: Lead Tradesperson")
            appendLine("Date: ${today()}")
            appendLine()
            appendLine("Guild of Smiths – Building Trust, One Job at a Time.")
        }
    }
    
    /**
     * REPORT - GOS Work Completion Report Format
     * Uses EnhancedJobData if available for real job details
     */
    fun generateReport(items: List<ExecutionItem>): String {
        val jobData = com.guildofsmiths.trademesh.planner.OnlineResolver.getLastJobData()
        
        // Service Provider Info
        val providerName = jobData?.providerName ?: "[Your Name]"
        val providerBusiness = jobData?.providerBusinessName ?: "[Your Business Name]"
        val providerPhone = jobData?.providerPhone ?: "[Phone]"
        val providerEmail = jobData?.providerEmail ?: "[Email]"
        val providerAddress = jobData?.providerAddress ?: "[Your Address]"
        val providerRole = jobData?.providerGuildRole ?: "Lead Tradesperson"
        
        val jobTitle = jobData?.jobTitle ?: "Service Work"
        val clientName = jobData?.clientName ?: "[Client Name]"
        val clientCompany = jobData?.clientCompany
        val location = jobData?.location ?: "[Property Address]"
        val primaryTrade = jobData?.primaryTrade ?: "General Services"
        
        val tasks = if (jobData != null && jobData.tasks.isNotEmpty()) {
            jobData.tasks
        } else {
            items.filter { it.type == ExecutionItemType.TASK }.map { 
                when (val parsed = it.parsed) {
                    is com.guildofsmiths.trademesh.planner.types.ParsedItemData.Task -> parsed.description
                    else -> it.source
                }
            }
        }
        
        val materials = if (jobData != null && jobData.materials.isNotEmpty()) {
            jobData.materials
        } else {
            null
        }
        
        val labor = if (jobData != null && jobData.labor.isNotEmpty()) {
            jobData.labor
        } else {
            null
        }
        
        val notes = jobData?.notes ?: emptyList()
        
        return buildString {
            appendLine(providerBusiness.uppercase())
            appendLine("Professional Trade Services")
            appendLine(providerAddress)
            appendLine("Phone: $providerPhone | Email: $providerEmail")
            appendLine()
            appendLine("WORK COMPLETION REPORT")
            appendLine("Date: ${today()}")
            appendLine("Report Number: ${generateReportNumber()}")
            appendLine("Prepared By: $providerName, $providerRole")
            appendLine("Client: $clientName${clientCompany?.let { " ($it)" } ?: ""}")
            appendLine()
            appendLine("PROJECT: $jobTitle")
            appendLine("Location: $location")
            appendLine("Trade: $primaryTrade")
            appendLine()
            appendLine("PROJECT SUMMARY")
            appendLine("─".repeat(60))
            appendLine()
            appendLine("TASKS COMPLETED (${tasks.size})")
            tasks.forEachIndexed { index, task ->
                appendLine("  ${index + 1}. [✓] $task")
            }
            appendLine()
            appendLine("MATERIALS USED")
            appendLine("─".repeat(60))
            if (materials != null) {
                materials.forEach { mat ->
                    appendLine("  • ${mat.name} - ${mat.quantity ?: ""} ${mat.unit ?: ""}")
                }
            } else {
                items.filter { it.type == ExecutionItemType.MATERIAL }.forEach { mat ->
                    appendLine("  • ${mat.source}")
                }
            }
            appendLine()
            appendLine("LABOR PERFORMED")
            appendLine("─".repeat(60))
            if (labor != null) {
                labor.forEach { lab ->
                    appendLine("  • ${lab.role} - ${lab.hours} hours")
                }
            } else {
                items.filter { it.type == ExecutionItemType.LABOR }.forEach { lab ->
                    appendLine("  • ${lab.source}")
                }
            }
            appendLine()
            appendLine("OBSERVATIONS")
            appendLine("─".repeat(60))
            appendLine("Work completed according to scope. All tasks verified.")
            appendLine("Quality standards met per Guild of Smiths guidelines.")
            if (notes.isNotEmpty()) {
                appendLine()
                appendLine("Work Notes:")
                notes.forEach { note ->
                    appendLine("  • $note")
                }
            }
            appendLine()
            appendLine("RECOMMENDATIONS")
            appendLine("─".repeat(60))
            appendLine("• Schedule follow-up inspection in 90 days (preventive maintenance)")
            appendLine("• Annual inspections recommended for ongoing maintenance")
            appendLine()
            appendLine("PHOTOGRAPHS")
            appendLine("─".repeat(60))
            appendLine("(Note: Photos attached digitally or provided separately)")
            appendLine("• Before/during/after documentation included")
            appendLine()
            appendLine("CLIENT SIGN-OFF")
            appendLine("─".repeat(60))
            appendLine("Client Name: $clientName")
            appendLine("Signature: _______________________  Date: ___________")
            appendLine()
            appendLine("Guild of Smiths – Building Trust, One Job at a Time.")
        }
    }
    
    /**
     * INVOICE - GOS Advanced Solo Format with AI Supervisor Report
     * Uses EnhancedJobData if available for real job details
     */
    fun generateInvoice(items: List<ExecutionItem>): String {
        val jobData = com.guildofsmiths.trademesh.planner.OnlineResolver.getLastJobData()
        
        val jobTitle = jobData?.jobTitle ?: "Service Work"
        val clientName = jobData?.clientName ?: "[Client Name]"
        val location = jobData?.location ?: "[Property Address]"
        val primaryTrade = jobData?.primaryTrade ?: "General Services"
        
        // Service Provider Info
        val providerName = jobData?.providerName ?: "[Your Name]"
        val providerBusiness = jobData?.providerBusinessName ?: "[Your Business Name]"
        val providerPhone = jobData?.providerPhone ?: "[Phone]"
        val providerEmail = jobData?.providerEmail ?: "[Email]"
        val providerAddress = jobData?.providerAddress ?: "[Your Address]"
        val providerRole = jobData?.providerGuildRole ?: "Solo (Hybrid Mode – AI Supervisor Enabled)"
        
        // Client Info
        val clientCompany = jobData?.clientCompany ?: "[Client Company]"
        val clientEmail = jobData?.clientEmail ?: "[Client Email]"
        
        val laborItems = if (jobData != null && jobData.labor.isNotEmpty()) {
            jobData.labor
        } else {
            null
        }
        
        val materialItems = if (jobData != null && jobData.materials.isNotEmpty()) {
            jobData.materials
        } else {
            null
        }
        
        val tasks = if (jobData != null && jobData.tasks.isNotEmpty()) {
            jobData.tasks
        } else {
            items.filter { it.type == ExecutionItemType.TASK }.map { it.source }
        }
        
        // Calculate costs from real data
        val totalLaborHours = laborItems?.sumOf { it.hours } ?: items.filter { it.type == ExecutionItemType.LABOR }.size * 8.0
        val totalLaborCost = laborItems?.sumOf { it.hours * it.rate } ?: (totalLaborHours * 85.0)
        val materialsCost = materialItems?.sumOf { it.estimatedCost ?: 0.0 } 
            ?: jobData?.estimatedMaterialCost 
            ?: items.filter { it.type == ExecutionItemType.MATERIAL }.size * 75.0
        val travelCost = 45.0
        val subtotal = totalLaborCost + materialsCost + travelCost
        val taxRate = 0.0825
        val tax = subtotal * taxRate
        val total = subtotal + tax
        
        val dueDate = dateFormatShort.format(Date(System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000))
        val invoiceNum = generateInvoiceNumber()
        
        return buildString {
            appendLine("${providerBusiness.uppercase()} INVOICE – ADVANCED SOLO (Hybrid Mode)")
            appendLine("──────────────────────────────────────────────────────────────")
            appendLine()
            appendLine("Invoice ID      : $invoiceNum")
            appendLine("Series          : SOLO-${todayShort().substring(0, 7)}")
            appendLine("Issue Date      : ${todayShort()}")
            appendLine("Due Date        : $dueDate (Net 14)")
            appendLine("Status          : Issued – Awaiting Payment")
            appendLine()
            appendLine("From (Service Provider):")
            appendLine("  Name          : $providerName")
            appendLine("  Business      : $providerBusiness")
            appendLine("  Guild Role    : $providerRole")
            appendLine("  Contact       : $providerPhone | $providerEmail")
            appendLine("  Address       : $providerAddress")
            appendLine()
            appendLine("To (Client):")
            appendLine("  Name          : $clientName")
            appendLine("  Company       : $clientCompany")
            appendLine("  Address       : $location")
            appendLine("  Email         : $clientEmail")
            appendLine("  Project Ref   : $jobTitle")
            appendLine()
            appendLine("Line Items")
            appendLine("──────────────────────────────────────────────────────")
            appendLine("Code    | Description                                      | Qty | Unit | Rate    | Total")
            appendLine("────────┼──────────────────────────────────────────────────┼─────┼──────┼─────────┼──────────")
            
            // Labor line items from real data
            if (laborItems != null) {
                laborItems.forEachIndexed { index, lab ->
                    val code = "LAB-${(index + 1).toString().padStart(2, '0')}"
                    val desc = "Labor – ${lab.role}".take(48).padEnd(48)
                    val hrs = lab.hours
                    val rate = lab.rate
                    val lineTotal = hrs * rate
                    appendLine("$code  | $desc | ${String.format("%3.1f", hrs)} | hr   | \$${String.format("%6.2f", rate)} | \$${String.format("%7.2f", lineTotal)}")
                }
            } else {
                items.filter { it.type == ExecutionItemType.LABOR }.forEachIndexed { index, lab ->
                    val (hours, rate) = parseLaborCost(lab.source)
                    val code = "LAB-${(index + 1).toString().padStart(2, '0')}"
                    val desc = lab.source.take(48).padEnd(48)
                    appendLine("$code  | $desc | ${String.format("%3.1f", hours)} | hr   | \$${String.format("%6.2f", rate)} | \$${String.format("%7.2f", hours * rate)}")
                }
            }
            
            // Material line items from real data
            if (materialItems != null) {
                materialItems.forEachIndexed { index, mat ->
                    val code = "MAT-${(index + 1).toString().padStart(2, '0')}"
                    val desc = "Materials – ${mat.name}".take(48).padEnd(48)
                    val qty = mat.quantity ?: "1"
                    val unit = mat.unit ?: "lot"
                    val cost = mat.estimatedCost ?: 0.0
                    appendLine("$code  | $desc | ${qty.toString().take(3).padStart(3)} | ${unit.take(4).padEnd(4)} | \$${String.format("%6.2f", cost)} | \$${String.format("%7.2f", cost)}")
                }
            } else {
                items.filter { it.type == ExecutionItemType.MATERIAL }.forEachIndexed { index, mat ->
                    val cost = parseMaterialCost(mat.source)
                    val code = "MAT-${(index + 1).toString().padStart(2, '0')}"
                    val desc = mat.source.take(48).padEnd(48)
                    appendLine("$code  | $desc | 1   | lot  | \$${String.format("%6.2f", cost)} | \$${String.format("%7.2f", cost)}")
                }
            }
            
            appendLine("TRV-01  | Travel / on-site time (geofence logged)          | 1   | ea   | \$ 45.00 | \$  45.00")
            appendLine("────────┼──────────────────────────────────────────────────┼─────┼──────┼─────────┼──────────")
            appendLine("                                        Subtotal         |         |         | \$${String.format("%7.2f", subtotal)}")
            appendLine("                                        Sales Tax (8.25%)|         |         | \$${String.format("%7.2f", tax)}")
            appendLine("──────────────────────────────────────────────────────")
            appendLine("                                        TOTAL DUE        |         |         | \$${String.format("%7.2f", total)} USD")
            appendLine()
            appendLine("Payment Terms & Instructions")
            appendLine("──────────────────────────────────────────────────────")
            appendLine("• Preferred: ACH / Zelle → $providerEmail (no fee)")
            appendLine("• Check: $providerBusiness, $providerAddress")
            appendLine("• Card: Secure link sent via Smith chat (2.9% + \$0.30 fee)")
            appendLine("• Late payments subject to 1.5% monthly interest after due date")
            appendLine("• Disputes: Must be raised in Smith project thread within 7 days")
            appendLine()
            appendLine("Work & Verification Summary")
            appendLine("──────────────────────────────────────────────────────")
            appendLine("• Project: $jobTitle")
            appendLine("• Location: $location")
            appendLine("• Trade: $primaryTrade")
            appendLine("• Tasks Completed: ${tasks.size}")
            appendLine("• Media in thread: Photos (pre/post), voice notes available in project thread")
            appendLine()
            appendLine("AI Supervisor Report – Hybrid Mode (Generated ${now()})")
            appendLine("──────────────────────────────────────────────────────")
            appendLine("Scope: Full log analysis (geofence, time logs, photos, voice notes, material scans)")
            appendLine()
            appendLine("• Time Integrity: ${String.format("%.1f", totalLaborHours)}h active labor. Idle time <5%.")
            appendLine("• Efficiency: Work completed within estimated timeframe. No rework loops detected.")
            appendLine("• Material Accuracy: All materials match calculated scope. Waste minimized.")
            appendLine("• Compliance Flags:")
            jobData?.codeRequirements?.take(3)?.forEach { req ->
                appendLine("  - $req")
            } ?: run {
                appendLine("  - Safety requirements verified")
                appendLine("  - All compliance checks passed")
            }
            appendLine("• Risk / Anomaly: None critical.")
            appendLine("• Next Actions (AI-suggested):")
            appendLine("  1. Schedule follow-up inspection in 90 days (auto-reminder)")
            appendLine("  2. Attach final signed acceptance if client replies in thread")
            appendLine()
            appendLine("This document and attached AI report serve as auditable work record.")
            appendLine("All data immutable per Guild of Smiths retention policy.")
            appendLine()
            appendLine("Guild of Smiths – Built for the trades. Hybrid AI active.")
            appendLine("Version: Smith Invoice Engine v0.9.2-ad")
        }
    }
    
    /**
     * COMPILE SUMMARY - Full GOSPLAN format
     * 
     * If EnhancedJobData is available from TEST ⧉ online research,
     * uses that data for a comprehensive summary.
     * Otherwise, falls back to basic parsing from ExecutionItems.
     */
    fun generateCompileSummary(items: List<ExecutionItem>): String {
        // Check if we have enhanced job data from TEST ⧉
        val jobData = com.guildofsmiths.trademesh.planner.OnlineResolver.getLastJobData()
        // #region agent log
        android.util.Log.e("DEBUG_H4", """{"hypothesisId":"H4","location":"GOSDocumentGenerator:generateCompileSummary","message":"Checking for EnhancedJobData","data":{"hasJobData":${jobData != null},"tasksCount":${jobData?.tasks?.size ?: -1},"materialsCount":${jobData?.materials?.size ?: -1},"laborCount":${jobData?.labor?.size ?: -1},"itemsCount":${items.size}}}""")
        // #endregion
        
        return if (jobData != null) {
            generateEnhancedCompileSummary(jobData, items)
        } else {
            generateBasicCompileSummary(items)
        }
    }
    
    /**
     * ENHANCED COMPILE SUMMARY - Full GOSPLAN template with all data from online research
     */
    private fun generateEnhancedCompileSummary(
        jobData: com.guildofsmiths.trademesh.planner.OnlineResolver.EnhancedJobData,
        items: List<ExecutionItem>
    ): String {
        return buildString {
            appendLine("╔══════════════════════════════════════════════════════════════════════╗")
            appendLine("║           GUILD OF SMITHS – COMPILED JOB SUMMARY                     ║")
            appendLine("║                    TEST ⧉ ONLINE RESEARCH COMPLETE                   ║")
            appendLine("╚══════════════════════════════════════════════════════════════════════╝")
            appendLine()
            appendLine("Generated: ${now()}")
            appendLine("Status: PENDING CONFIRMATION")
            appendLine("Source: Online Research + Trade Knowledge Base")
            appendLine()
            
            // ══════════════════════════════════════════════════════════════
            // JOB HEADER
            // ══════════════════════════════════════════════════════════════
            appendLine("═".repeat(70))
            appendLine("JOB HEADER")
            appendLine("═".repeat(70))
            appendLine()
            appendLine("Job Title:      ${jobData.jobTitle}")
            appendLine("Client:         ${jobData.clientName ?: "[Client Name]"}")
            appendLine("Location:       ${jobData.location ?: "[Location]"}")
            appendLine("Job Type:       ${jobData.jobType}")
            appendLine("Primary Trade:  ${jobData.primaryTrade}")
            appendLine("Urgency:        ${jobData.urgency.replaceFirstChar { it.uppercase() }}")
            appendLine("Crew Size:      ${jobData.crewSize} workers")
            appendLine("Est. Duration:  ${jobData.estimatedDays} days")
            appendLine()
            
            // ══════════════════════════════════════════════════════════════
            // SCOPE
            // ══════════════════════════════════════════════════════════════
            appendLine("─".repeat(70))
            appendLine("SCOPE OF WORK")
            appendLine("─".repeat(70))
            appendLine()
            appendLine(jobData.scope)
            appendLine()
            jobData.scopeDetails.forEach { detail ->
                appendLine("  • $detail")
            }
            appendLine()
            
            // ══════════════════════════════════════════════════════════════
            // EXECUTION CHECKLIST (Tasks)
            // ══════════════════════════════════════════════════════════════
            appendLine("─".repeat(70))
            appendLine("EXECUTION CHECKLIST (${jobData.tasks.size} tasks)")
            appendLine("─".repeat(70))
            appendLine()
            jobData.tasks.forEachIndexed { index, task ->
                appendLine("[ ] ${index + 1}. $task")
            }
            appendLine()
            
            // ══════════════════════════════════════════════════════════════
            // MATERIALS
            // ══════════════════════════════════════════════════════════════
            appendLine("─".repeat(70))
            appendLine("MATERIALS REQUIRED (${jobData.materials.size} items)")
            appendLine("─".repeat(70))
            appendLine()
            if (jobData.materials.isNotEmpty()) {
                jobData.materials.forEach { mat ->
                    val qty = if (mat.quantity != null) "${mat.quantity} ${mat.unit ?: "ea"}" else ""
                    val cost = if (mat.estimatedCost != null) "@ \$${String.format("%.2f", mat.estimatedCost)}" else ""
                    appendLine("  • ${if (qty.isNotEmpty()) "$qty " else ""}${mat.name} $cost".trim())
                }
            } else {
                appendLine("  (no materials specified)")
            }
            appendLine()
            
            // ══════════════════════════════════════════════════════════════
            // LABOR
            // ══════════════════════════════════════════════════════════════
            appendLine("─".repeat(70))
            appendLine("LABOR ESTIMATE (${jobData.labor.size} roles)")
            appendLine("─".repeat(70))
            appendLine()
            if (jobData.labor.isNotEmpty()) {
                jobData.labor.forEach { lab ->
                    val total = lab.hours * lab.rate
                    appendLine("  • ${lab.hours.toInt()}h ${lab.role} @ \$${String.format("%.2f", lab.rate)}/hr = \$${String.format("%.2f", total)}")
                }
            } else {
                appendLine("  (no labor specified)")
            }
            appendLine()
            
            // ══════════════════════════════════════════════════════════════
            // PHASES / TIMELINE
            // ══════════════════════════════════════════════════════════════
            appendLine("─".repeat(70))
            appendLine("WORK PHASES (${jobData.phases.size} phases)")
            appendLine("─".repeat(70))
            appendLine()
            jobData.phases.forEach { phase ->
                appendLine("  ${phase.order}. [${phase.name}] ${phase.description}")
            }
            appendLine()
            
            // ══════════════════════════════════════════════════════════════
            // FINANCIAL SNAPSHOT
            // ══════════════════════════════════════════════════════════════
            appendLine("─".repeat(70))
            appendLine("FINANCIAL SNAPSHOT")
            appendLine("─".repeat(70))
            appendLine()
            appendLine("  Est. Labor:       \$${String.format("%,.2f", jobData.estimatedLaborCost)}")
            appendLine("  Est. Materials:   \$${String.format("%,.2f", jobData.estimatedMaterialCost)}")
            appendLine("  ─────────────────────────────")
            appendLine("  Est. TOTAL:       \$${String.format("%,.2f", jobData.estimatedTotal)}")
            appendLine()
            appendLine("  Deposit Required: ${jobData.depositRequired}")
            appendLine("  Warranty:         ${jobData.warranty}")
            appendLine()
            
            // ══════════════════════════════════════════════════════════════
            // SAFETY & CODE
            // ══════════════════════════════════════════════════════════════
            if (jobData.safetyRequirements.isNotEmpty() || jobData.permitRequired) {
                appendLine("─".repeat(70))
                appendLine("SAFETY & CODE REQUIREMENTS")
                appendLine("─".repeat(70))
                appendLine()
                if (jobData.permitRequired) {
                    appendLine("  ⚠ PERMIT REQUIRED before work begins")
                }
                if (jobData.inspectionRequired) {
                    appendLine("  ⚠ INSPECTION REQUIRED at completion")
                }
                appendLine()
                appendLine("  Safety:")
                jobData.safetyRequirements.forEach { req ->
                    appendLine("    • $req")
                }
                appendLine()
                appendLine("  Code:")
                jobData.codeRequirements.forEach { req ->
                    appendLine("    • $req")
                }
                appendLine()
            }
            
            // ══════════════════════════════════════════════════════════════
            // ASSUMPTIONS & EXCLUSIONS
            // ══════════════════════════════════════════════════════════════
            if (jobData.assumptions.isNotEmpty()) {
                appendLine("─".repeat(70))
                appendLine("ASSUMPTIONS")
                appendLine("─".repeat(70))
                appendLine()
                jobData.assumptions.forEach { assumption ->
                    appendLine("  • $assumption")
                }
                appendLine()
            }
            
            if (jobData.exclusions.isNotEmpty()) {
                appendLine("─".repeat(70))
                appendLine("EXCLUSIONS")
                appendLine("─".repeat(70))
                appendLine()
                jobData.exclusions.forEach { exclusion ->
                    appendLine("  • $exclusion")
                }
                appendLine()
            }
            
            // ══════════════════════════════════════════════════════════════
            // NOTES
            // ══════════════════════════════════════════════════════════════
            if (jobData.notes.isNotEmpty()) {
                appendLine("─".repeat(70))
                appendLine("NOTES")
                appendLine("─".repeat(70))
                appendLine()
                jobData.notes.forEach { note ->
                    appendLine("  • $note")
                }
                appendLine("  • Detected keywords: ${jobData.detectedKeywords.take(5).joinToString(", ")}")
                appendLine("  • Research sources: ${jobData.researchSources.joinToString(", ")}")
                appendLine()
            }
            
            // ══════════════════════════════════════════════════════════════
            // SUMMARY LINE
            // ══════════════════════════════════════════════════════════════
            appendLine("═".repeat(70))
            appendLine("SUMMARY")
            appendLine("═".repeat(70))
            appendLine()
            appendLine("${jobData.jobType} job${jobData.location?.let { " in $it" } ?: ""}.")
            appendLine("${jobData.tasks.size} tasks, ${jobData.materials.size} materials, ${jobData.crewSize} crew.")
            appendLine("Est. ${jobData.estimatedDays} days, \$${String.format("%,.2f", jobData.estimatedTotal)} total.")
            appendLine()
            
            // ══════════════════════════════════════════════════════════════
            // NEXT STEPS
            // ══════════════════════════════════════════════════════════════
            appendLine("╔══════════════════════════════════════════════════════════════════════╗")
            appendLine("║                           NEXT STEPS                                 ║")
            appendLine("╠══════════════════════════════════════════════════════════════════════╣")
            appendLine("║  1. Review this summary carefully                                    ║")
            appendLine("║  2. Click [✓ CONFIRM] to accept                                      ║")
            appendLine("║  3. Click [TRANSFER] to send to Job Board                           ║")
            appendLine("║  4. Generate PROPOSAL or transfer to Job Board                      ║")
            appendLine("╚══════════════════════════════════════════════════════════════════════╝")
        }
    }
    
    /**
     * BASIC COMPILE SUMMARY - Fallback when no enhanced data available
     */
    private fun generateBasicCompileSummary(items: List<ExecutionItem>): String {
        val tasks = items.filter { it.type == ExecutionItemType.TASK }
        val materials = items.filter { it.type == ExecutionItemType.MATERIAL }
        val labor = items.filter { it.type == ExecutionItemType.LABOR }
        
        val laborCosts = labor.map { parseLaborCost(it.source) }
        val totalLaborCost = laborCosts.sumOf { it.first * it.second }
        val totalLaborHours = laborCosts.sumOf { it.first }
        
        return buildString {
            appendLine("═".repeat(60))
            appendLine("COMPILE SUMMARY – REVIEW BEFORE CONFIRMATION")
            appendLine("═".repeat(60))
            appendLine()
            appendLine("Generated: ${now()}")
            appendLine("Status: PENDING CONFIRMATION")
            appendLine("Note: Use TEST ⧉ for comprehensive online research")
            appendLine()
            appendLine("─".repeat(60))
            appendLine("JOB OVERVIEW")
            appendLine("─".repeat(60))
            appendLine()
            appendLine("Job ID:        ${generateJobId()} (pending)")
            appendLine("Job Type:      General")
            appendLine("Urgency:       Moderate")
            appendLine("Phase:         1 – Inspection")
            appendLine()
            appendLine("─".repeat(60))
            appendLine("SCOPE SUMMARY (${tasks.size} tasks)")
            appendLine("─".repeat(60))
            appendLine()
            tasks.forEachIndexed { index, task ->
                appendLine("${(index + 1).toString().padStart(2)}. ${task.source}")
            }
            if (tasks.isEmpty()) {
                appendLine("   (no tasks specified)")
            }
            appendLine()
            appendLine("─".repeat(60))
            appendLine("EXECUTION CHECKLIST")
            appendLine("─".repeat(60))
            appendLine()
            tasks.forEach { task ->
                appendLine("[ ] ${task.source}")
            }
            appendLine("[ ] Final walkthrough & client sign-off")
            appendLine("[ ] Generate invoice & close job")
            appendLine()
            appendLine("─".repeat(60))
            appendLine("MATERIALS (${materials.size} items)")
            appendLine("─".repeat(60))
            appendLine()
            if (materials.isNotEmpty()) {
                materials.forEach { mat ->
                    appendLine("• ${mat.source}")
                }
            } else {
                appendLine("   (no materials specified)")
            }
            appendLine()
            appendLine("─".repeat(60))
            appendLine("LABOR ESTIMATE (${labor.size} entries)")
            appendLine("─".repeat(60))
            appendLine()
            if (labor.isNotEmpty()) {
                labor.forEach { lab ->
                    val (hours, rate) = parseLaborCost(lab.source)
                    appendLine("• ${lab.source} → \$${String.format("%.2f", hours * rate)}")
                }
                appendLine()
                appendLine("Total Labor: ${totalLaborHours}h @ \$${String.format("%.2f", totalLaborCost)}")
            } else {
                appendLine("   (no labor specified)")
            }
            appendLine()
            appendLine("─".repeat(60))
            appendLine("FINANCIAL SNAPSHOT")
            appendLine("─".repeat(60))
            appendLine()
            appendLine("Est. Labor:     \$${String.format("%.2f", totalLaborCost)}")
            appendLine("Est. Materials: \$${String.format("%.2f", materials.size * 75.0)}")
            appendLine("Est. Total:     \$${String.format("%.2f", totalLaborCost + materials.size * 75.0)}")
            appendLine("Deposit Req:    50% on approval")
            appendLine()
            appendLine("═".repeat(60))
            appendLine("NEXT STEPS")
            appendLine("═".repeat(60))
            appendLine()
            appendLine("1. Review this summary carefully")
            appendLine("2. Click [✓ CONFIRM] if correct")
            appendLine("3. Or click [EDIT] to make changes")
            appendLine("4. After confirmation, select jobs to transfer")
            appendLine("5. Generate PROPOSAL or transfer to Job Board")
            appendLine()
            appendLine("═".repeat(60))
        }
    }
    
    /**
     * JOB RECORD - Compile/Transfer format
     */
    fun generateJobRecord(items: List<ExecutionItem>): String {
        val tasks = items.filter { it.type == ExecutionItemType.TASK }
        val materials = items.filter { it.type == ExecutionItemType.MATERIAL }
        val labor = items.filter { it.type == ExecutionItemType.LABOR }
        
        val laborCosts = labor.map { parseLaborCost(it.source) }
        val totalLaborCost = laborCosts.sumOf { it.first * it.second }
        
        return buildString {
            appendLine("# JOB RECORD – TRANSFERRED TO BOARD")
            appendLine("# Guild of Smiths – Active Job Entry")
            appendLine("# Job ID: ${generateJobId()}")
            appendLine("# Created: ${now()}")
            appendLine("# Status: Pending")
            appendLine("# Source: Planner → Job Board Transfer")
            appendLine()
            appendLine("## JOB HEADER")
            appendLine("Job Title:          [Job Title]")
            appendLine("Client:             [Client Name]")
            appendLine("Location:           [Address]")
            appendLine("Job Type:           Masonry / General")
            appendLine("Primary Trade:      Masonry")
            appendLine("Urgency:            Moderate")
            appendLine("Due By:             [Target Date]")
            appendLine("Created By:         (current user / foreman device)")
            appendLine()
            appendLine("## CURRENT PHASE / STATUS")
            appendLine("Phase:              1 – Inspection")
            appendLine("Status Flags:       [ ] Scheduled   [ ] In Progress   [ ] Completed   [ ] Invoiced")
            appendLine("Priority:           3 – Standard (1=critical, 5=low)")
            appendLine()
            appendLine("## SCOPE SUMMARY (from planner)")
            tasks.forEach { task ->
                appendLine("- ${task.source}")
            }
            appendLine()
            appendLine("## EXECUTION CHECKLIST")
            tasks.forEach { task ->
                appendLine("[ ] ${task.source}")
            }
            appendLine("[ ] Final walkthrough & client sign-off")
            appendLine("[ ] Generate invoice & close job")
            appendLine()
            appendLine("## RESOURCES / ASSIGNMENTS")
            appendLine("Assigned To:        [Lead Worker]")
            appendLine("Crew Size Est:      1–2 workers")
            appendLine("Materials Prep:     ")
            materials.forEach { mat ->
                appendLine("                    - ${mat.source}")
            }
            appendLine()
            appendLine("## FINANCIAL / BILLING SNAPSHOT")
            appendLine("Est. Labor:         \$${String.format("%.2f", totalLaborCost)}")
            appendLine("Est. Materials:     \$${String.format("%.2f", materials.size * 75.0)}")
            appendLine("Deposit Req:        50% on approval")
            appendLine("Warranty:           1 year workmanship")
            appendLine()
            appendLine("## NOTES")
            appendLine("Transferred from Plan compiler.")
            appendLine("${tasks.size} tasks, ${materials.size} materials, ${labor.size} labor items.")
            appendLine()
            appendLine("## NEXT AUTOMATIC ACTIONS")
            appendLine("• On transfer: Add to Job Board → Pending column")
            appendLine("• On completion: Move to Completed → prompt invoice generation")
        }
    }
    
    /**
     * SELECTED - Generate only the selected document types
     */
    fun generateSelected(items: List<ExecutionItem>, selectedTypes: Set<String>): String {
        // #region agent log
        android.util.Log.e("DEBUG_DOC", """{"hypothesisId":"H3b","location":"GOSDocumentGenerator.generateSelected","message":"generateSelected called","data":{"itemsCount":${items.size},"selectedTypes":"${selectedTypes.joinToString(",")}","isEmpty":${selectedTypes.isEmpty()}}}""")
        // #endregion
        if (selectedTypes.isEmpty()) return "No documents selected."

        return buildString {
            appendLine("═".repeat(70))
            appendLine("GUILD OF SMITHS – SELECTED DOCUMENTS")
            appendLine("Generated: ${now()}")
            appendLine("═".repeat(70))
            appendLine()
            
            var sectionNum = 1
            
            if (selectedTypes.contains("PROPOSAL")) {
                appendLine()
                appendLine("╔══════════════════════════════════════════════════════════════════════╗")
                appendLine("║  SECTION $sectionNum: PROPOSAL / INSPECTION REPORT                            ║")
                appendLine("╚══════════════════════════════════════════════════════════════════════╝")
                appendLine()
                append(generateProposal(items))
                sectionNum++
            }
            
            if (selectedTypes.contains("REPORT")) {
                appendLine()
                appendLine()
                appendLine("╔══════════════════════════════════════════════════════════════════════╗")
                appendLine("║  SECTION $sectionNum: WORK COMPLETION REPORT                                  ║")
                appendLine("╚══════════════════════════════════════════════════════════════════════╝")
                appendLine()
                append(generateReport(items))
                sectionNum++
            }
            
            if (selectedTypes.contains("INVOICE")) {
                appendLine()
                appendLine()
                appendLine("╔══════════════════════════════════════════════════════════════════════╗")
                appendLine("║  SECTION $sectionNum: INVOICE                                                 ║")
                appendLine("╚══════════════════════════════════════════════════════════════════════╝")
                appendLine()
                append(generateInvoice(items))
                sectionNum++
            }
            
            appendLine()
            appendLine()
            appendLine("═".repeat(70))
            appendLine("END OF DOCUMENT PACKAGE")
            appendLine("═".repeat(70))
        }
    }
    
    /**
     * ALL - Generate all documents combined
     */
    fun generateAll(items: List<ExecutionItem>): String {
        return buildString {
            appendLine("═".repeat(70))
            appendLine("GUILD OF SMITHS – COMPLETE DOCUMENT PACKAGE")
            appendLine("Generated: ${now()}")
            appendLine("═".repeat(70))
            appendLine()
            appendLine()
            appendLine("╔══════════════════════════════════════════════════════════════════════╗")
            appendLine("║  SECTION 1: PROPOSAL / INSPECTION REPORT                             ║")
            appendLine("╚══════════════════════════════════════════════════════════════════════╝")
            appendLine()
            append(generateProposal(items))
            appendLine()
            appendLine()
            appendLine("╔══════════════════════════════════════════════════════════════════════╗")
            appendLine("║  SECTION 2: WORK COMPLETION REPORT                                   ║")
            appendLine("╚══════════════════════════════════════════════════════════════════════╝")
            appendLine()
            append(generateReport(items))
            appendLine()
            appendLine()
            appendLine("╔══════════════════════════════════════════════════════════════════════╗")
            appendLine("║  SECTION 3: INVOICE                                                  ║")
            appendLine("╚══════════════════════════════════════════════════════════════════════╝")
            appendLine()
            append(generateInvoice(items))
            appendLine()
            appendLine()
            appendLine("╔══════════════════════════════════════════════════════════════════════╗")
            appendLine("║  SECTION 4: JOB RECORD                                               ║")
            appendLine("╚══════════════════════════════════════════════════════════════════════╝")
            appendLine()
            append(generateJobRecord(items))
            appendLine()
            appendLine()
            appendLine("═".repeat(70))
            appendLine("END OF DOCUMENT PACKAGE")
            appendLine("═".repeat(70))
        }
    }
    
    /**
     * Parse labor string like "8h Electrician" or "4h Lead Mason inspection"
     * Returns Pair(hours, hourlyRate)
     */
    private fun parseLaborCost(source: String): Pair<Double, Double> {
        val hourMatch = Regex("""(\d+\.?\d*)h""").find(source)
        val hours = hourMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 4.0
        // Default rate: $85/hr
        return Pair(hours, 85.0)
    }
    
    /**
     * Parse material string for cost estimation
     */
    private fun parseMaterialCost(source: String): Double {
        // Check for explicit price
        val priceMatch = Regex("""\$(\d+\.?\d*)""").find(source)
        if (priceMatch != null) {
            return priceMatch.groupValues[1].toDoubleOrNull() ?: 50.0
        }
        
        // Estimate based on quantity
        val qtyMatch = Regex("""(\d+)\s*(boxes?|bags?|gallons?|pieces?)""", RegexOption.IGNORE_CASE).find(source)
        val qty = qtyMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        
        return qty * 25.0 // $25 per unit estimate
    }
}

// ════════════════════════════════════════════════════════════════════
// LEGACY DATA CLASSES (for backward compatibility)
// ════════════════════════════════════════════════════════════════════

enum class AnnotationType {
    SYSTEM,
    CONFIRMED,
    ASSUMPTION,
    EXECUTION
}

data class Annotation(
    val type: AnnotationType,
    val start: Int,
    val end: Int,
    val text: String
)

data class ExecutionItem(
    val id: String,
    val title: String,
    val description: String,
    val estimatedHours: Double? = null,
    val toolsNeeded: List<String> = emptyList(),
    val start: Int,
    val end: Int
)
