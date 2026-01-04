package com.guildofsmiths.trademesh.ui.documents

import com.guildofsmiths.trademesh.planner.types.ExecutionItem
import com.guildofsmiths.trademesh.planner.types.PlannerStateEnum

/**
 * Plan-Based Document Availability Rules
 * 
 * Document generation (Proposal, Report, Invoice) is gated by PLAN state,
 * NOT Job Board state. This is the authoritative source for unlock conditions.
 * 
 * ARCHITECTURE:
 * ┌─────────────────────────────┐
 * │   PLAN STATE: COMPILED      │
 * │ (authoritative source)      │
 * └──────────┬──────────────────┘
 *            │
 *            ▼
 * ┌─────────────────────────────┐
 * │  DOCUMENT UNLOCK GATE       │
 * │  (Plan-only authority)      │
 * └───────┬─────────┬───────────┘
 *         │         │
 *         ▼         ▼
 * ┌────────────┐ ┌────────────┐
 * │  PROPOSAL  │ │   REPORT   │
 * │(Plan data) │ │ (Plan data)│
 * └─────┬──────┘ └─────┬──────┘
 *       │              │
 *       ▼              ▼
 * ┌──────────────────────────┐
 * │        INVOICE           │
 * │ (requires report + time) │
 * └──────────────────────────┘
 */
object PlanDocumentAvailability {
    
    /**
     * Check Proposal availability based on Plan state.
     * 
     * RULES:
     * - Available IMMEDIATELY after COMPILED state
     * - Requires at least one execution item
     * - Generated from Plan schema data ONLY
     */
    fun checkProposal(
        planState: PlannerStateEnum,
        executionItems: List<ExecutionItem>
    ): DocumentAvailability {
        val isCompiled = planState == PlannerStateEnum.COMPILED
        val hasItems = executionItems.isNotEmpty()
        
        return when {
            isCompiled && hasItems -> DocumentAvailability(
                type = DocumentType.PROPOSAL,
                isAvailable = true,
                reason = "Plan compiled (${executionItems.size} items)"
            )
            isCompiled && !hasItems -> DocumentAvailability(
                type = DocumentType.PROPOSAL,
                isAvailable = false,
                reason = "No execution items",
                prerequisites = listOf("Add tasks to the plan")
            )
            planState == PlannerStateEnum.COMPILING -> DocumentAvailability(
                type = DocumentType.PROPOSAL,
                isAvailable = false,
                reason = "Compiling...",
                prerequisites = listOf("Wait for compilation to complete")
            )
            planState == PlannerStateEnum.COMPILE_ERROR -> DocumentAvailability(
                type = DocumentType.PROPOSAL,
                isAvailable = false,
                reason = "Compilation error",
                prerequisites = listOf("Fix errors and compile again")
            )
            else -> DocumentAvailability(
                type = DocumentType.PROPOSAL,
                isAvailable = false,
                reason = "Plan not compiled",
                prerequisites = listOf("Compile the plan first")
            )
        }
    }
    
    /**
     * Check Report availability based on Plan state.
     * 
     * RULES:
     * - Available after Plan is COMPILED
     * - Report is generated from Plan schema data
     * - Does NOT require job to be in REVIEW/DONE state
     * 
     * NOTE: This is different from JobBoard-based report which required
     * the job to be in inspection phase. Plan-based report is available
     * immediately after successful compilation.
     */
    fun checkReport(
        planState: PlannerStateEnum,
        executionItems: List<ExecutionItem>,
        hasProposalGenerated: Boolean = false
    ): DocumentAvailability {
        val isCompiled = planState == PlannerStateEnum.COMPILED
        val hasItems = executionItems.isNotEmpty()
        
        return when {
            isCompiled && hasItems -> DocumentAvailability(
                type = DocumentType.REPORT,
                isAvailable = true,
                reason = if (hasProposalGenerated) "Ready (proposal generated)" else "Plan compiled"
            )
            isCompiled && !hasItems -> DocumentAvailability(
                type = DocumentType.REPORT,
                isAvailable = false,
                reason = "No execution items",
                prerequisites = listOf("Add tasks to the plan")
            )
            else -> DocumentAvailability(
                type = DocumentType.REPORT,
                isAvailable = false,
                reason = "Plan not compiled",
                prerequisites = listOf("Compile the plan first")
            )
        }
    }
    
    /**
     * Check Invoice availability based on Plan state.
     * 
     * RULES:
     * - Requires Plan to be COMPILED
     * - Requires items to have been transferred to jobs
     * - Requires time entries for the transferred jobs
     * 
     * Invoice is the final document that requires:
     * 1. Compiled plan with execution items
     * 2. Transfer to job board (execution complete)
     * 3. Time tracking data
     */
    fun checkInvoice(
        planState: PlannerStateEnum,
        executionItems: List<ExecutionItem>,
        hasTransferredItems: Boolean,
        hasTimeEntries: Boolean,
        totalMinutesLogged: Int = 0
    ): DocumentAvailability {
        val isCompiled = planState == PlannerStateEnum.COMPILED
        val hasItems = executionItems.isNotEmpty()
        
        return when {
            // READY: All conditions met
            isCompiled && hasTransferredItems && hasTimeEntries -> DocumentAvailability(
                type = DocumentType.INVOICE,
                isAvailable = true,
                reason = "Ready (${formatMinutes(totalMinutesLogged)} logged)"
            )
            
            // BLOCKED: No time entries
            isCompiled && hasTransferredItems && !hasTimeEntries -> DocumentAvailability(
                type = DocumentType.INVOICE,
                isAvailable = false,
                reason = "No time logged",
                prerequisites = listOf("Log time entries for transferred jobs")
            )
            
            // BLOCKED: No transferred items
            isCompiled && hasItems && !hasTransferredItems -> DocumentAvailability(
                type = DocumentType.INVOICE,
                isAvailable = false,
                reason = "No jobs created",
                prerequisites = listOf(
                    "Select execution items",
                    "Transfer to Job Board"
                )
            )
            
            // BLOCKED: No items
            isCompiled && !hasItems -> DocumentAvailability(
                type = DocumentType.INVOICE,
                isAvailable = false,
                reason = "No execution items",
                prerequisites = listOf("Add tasks to the plan")
            )
            
            // BLOCKED: Not compiled
            else -> DocumentAvailability(
                type = DocumentType.INVOICE,
                isAvailable = false,
                reason = "Plan not compiled",
                prerequisites = listOf(
                    "Compile the plan",
                    "Transfer items to Job Board",
                    "Log time entries"
                )
            )
        }
    }
    
    /**
     * Get all document availability states for current Plan state.
     */
    fun checkAllDocuments(
        planState: PlannerStateEnum,
        executionItems: List<ExecutionItem>,
        hasTransferredItems: Boolean = false,
        hasTimeEntries: Boolean = false,
        totalMinutesLogged: Int = 0,
        hasProposalGenerated: Boolean = false
    ): List<DocumentAvailability> {
        return listOf(
            checkProposal(planState, executionItems),
            checkReport(planState, executionItems, hasProposalGenerated),
            checkInvoice(planState, executionItems, hasTransferredItems, hasTimeEntries, totalMinutesLogged)
        )
    }
    
    private fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }
}
