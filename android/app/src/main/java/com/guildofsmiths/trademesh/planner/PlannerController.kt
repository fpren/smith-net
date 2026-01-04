package com.guildofsmiths.trademesh.planner

import android.content.Context
import android.util.Log
import com.guildofsmiths.trademesh.planner.compiler.PlannerCompiler
import com.guildofsmiths.trademesh.planner.persistence.PlannerStorage
import com.guildofsmiths.trademesh.planner.state.PlannerStateMachine
import com.guildofsmiths.trademesh.planner.types.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * PlannerController - Main orchestrator for the deterministic Planner
 * 
 * Coordinates:
 * - Compiler
 * - State machine
 * - Storage
 * - Transfer pipeline
 */
class PlannerController(context: Context) {

    companion object {
        private const val TAG = "PlannerController"
    }

    private val compiler = PlannerCompiler()
    private val stateMachine = PlannerStateMachine()
    private val storage = PlannerStorage(context)

    private var data: PlannerData = createEmptyPlannerData()

    private fun createEmptyPlannerData(): PlannerData {
        val now = System.currentTimeMillis()
        return PlannerData(
            id = UUID.randomUUID().toString(),
            content = "",
            contentHash = "",
            state = PlannerStateEnum.EMPTY,
            compilationResult = null,
            executionItems = emptyList(),
            selectedItemIds = emptySet(),
            lastError = null,
            createdAt = now,
            updatedAt = now
        )
    }

    // ============================================================
    // INITIALIZATION
    // ============================================================

    suspend fun initialize() {
        Log.i(TAG, "Initializing PlannerController")
        
        // Load persisted state
        val persisted = storage.loadPlannerData()
        if (persisted != null) {
            // Verify hash matches
            val currentHash = computeHash(persisted.content)
            if (currentHash == persisted.contentHash && persisted.compilationResult != null) {
                data = persisted
                Log.i(TAG, "Restored planner state: ${data.state}")
            } else {
                // Hash mismatch - reset to draft
                data = createEmptyPlannerData().copy(
                    content = persisted.content,
                    state = if (persisted.content.isNotEmpty()) PlannerStateEnum.DRAFT else PlannerStateEnum.EMPTY
                )
                Log.i(TAG, "Hash mismatch, reset to DRAFT")
            }
        }
    }

    // ============================================================
    // GETTERS
    // ============================================================

    fun getState(): PlannerStateEnum = data.state
    
    fun getContent(): String = data.content
    
    fun getExecutionItems(): List<ExecutionItem> = data.executionItems
    
    fun getSelectedIds(): Set<String> = data.selectedItemIds
    
    fun getError(): CompileError? = data.lastError
    
    fun getData(): PlannerData = data.copy()

    // ============================================================
    // CONTENT MANAGEMENT
    // ============================================================

    fun onContentChange(content: String) {
        val isEmpty = content.isEmpty()

        if (isEmpty && data.state == PlannerStateEnum.EMPTY) {
            return // No-op
        }

        data = if (isEmpty) {
            data.copy(
                content = "",
                contentHash = "",
                state = PlannerStateEnum.EMPTY,
                compilationResult = null,
                executionItems = emptyList(),
                selectedItemIds = emptySet(),
                lastError = null,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            data.copy(
                content = content,
                state = PlannerStateEnum.DRAFT,
                compilationResult = null,
                executionItems = emptyList(),
                selectedItemIds = emptySet(),
                lastError = null,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    // ============================================================
    // COMPILATION
    // ============================================================

    fun compile(): CompileResult {
        if (!stateMachine.canPerformAction(data.state, PlannerStateMachine.PlannerAction.COMPILE)) {
            return CompileResult.Failure(
                CompileError(
                    code = ErrorCode.E001,
                    message = "Cannot compile in current state: ${data.state}",
                    line = null,
                    section = null
                )
            )
        }

        // Set compiling state
        data = data.copy(state = PlannerStateEnum.COMPILING)

        val result = compiler.compile(data.content)

        data = when (result) {
            is CompileResult.Success -> {
                data.copy(
                    contentHash = result.result.contentHash,
                    state = PlannerStateEnum.COMPILED,
                    compilationResult = result.result,
                    executionItems = result.items,
                    selectedItemIds = emptySet(),
                    lastError = null,
                    updatedAt = System.currentTimeMillis()
                )
            }
            is CompileResult.Failure -> {
                data.copy(
                    state = PlannerStateEnum.COMPILE_ERROR,
                    compilationResult = null,
                    executionItems = emptyList(),
                    selectedItemIds = emptySet(),
                    lastError = result.error,
                    updatedAt = System.currentTimeMillis()
                )
            }
        }

        return result
    }

    // ============================================================
    // SELECTION
    // ============================================================

    fun toggleItemSelection(itemId: String) {
        if (data.state != PlannerStateEnum.COMPILED) return
        
        // Verify item exists
        if (data.executionItems.none { it.id == itemId }) return

        val newSelection = data.selectedItemIds.toMutableSet()
        if (newSelection.contains(itemId)) {
            newSelection.remove(itemId)
        } else {
            newSelection.add(itemId)
        }

        data = data.copy(selectedItemIds = newSelection)
    }

    fun selectAllItems() {
        if (data.state != PlannerStateEnum.COMPILED) return
        
        data = data.copy(
            selectedItemIds = data.executionItems.map { it.id }.toSet()
        )
    }

    fun deselectAllItems() {
        if (data.state != PlannerStateEnum.COMPILED) return
        
        data = data.copy(selectedItemIds = emptySet())
    }

    // ============================================================
    // TRANSFER
    // ============================================================

    suspend fun transfer(): TransferResult {
        if (!stateMachine.canPerformAction(data.state, PlannerStateMachine.PlannerAction.TRANSFER)) {
            return TransferResult.Failure(
                TransferError(
                    code = "TP001",
                    message = "Cannot transfer in current state: ${data.state}",
                    stage = "validation"
                )
            )
        }

        if (data.selectedItemIds.isEmpty()) {
            return TransferResult.Failure(
                TransferError(
                    code = "TP002",
                    message = "No items selected",
                    stage = "validation"
                )
            )
        }

        // Set transferring state
        data = data.copy(state = PlannerStateEnum.TRANSFERRING)

        return try {
            val timestamp = System.currentTimeMillis()
            val selectedItems = data.executionItems.filter { data.selectedItemIds.contains(it.id) }

            // Build jobs from selected items
            val jobs = selectedItems.map { item ->
                buildJobFromItem(item, data.contentHash, timestamp)
            }

            // Save jobs to storage
            withContext(Dispatchers.IO) {
                storage.saveJobs(jobs)
            }

            // Remove transferred items
            val remainingItems = data.executionItems
                .filter { !data.selectedItemIds.contains(it.id) }
                .mapIndexed { index, item -> item.copy(index = index) }

            data = data.copy(
                state = PlannerStateEnum.COMPILED,
                executionItems = remainingItems,
                selectedItemIds = emptySet(),
                updatedAt = System.currentTimeMillis()
            )

            Log.i(TAG, "Transferred ${jobs.size} items to jobs")

            TransferResult.Success(
                jobs = jobs,
                remainingItemCount = remainingItems.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "Transfer failed", e)
            
            data = data.copy(state = PlannerStateEnum.COMPILED)
            
            TransferResult.Failure(
                TransferError(
                    code = "TF001",
                    message = "Transfer failed: ${e.message}",
                    stage = "write"
                )
            )
        }
    }

    private fun buildJobFromItem(item: ExecutionItem, planHash: String, timestamp: Long): PlannerJob {
        val details = when (val parsed = item.parsed) {
            is ParsedItemData.Task -> JobDetails.Task(description = parsed.description)
            is ParsedItemData.Material -> JobDetails.Material(
                description = parsed.description,
                quantity = parsed.quantity,
                unit = parsed.unit
            )
            is ParsedItemData.Labor -> JobDetails.Labor(
                description = parsed.description,
                hours = parsed.hours,
                role = parsed.role
            )
        }

        return PlannerJob(
            id = UUID.randomUUID().toString(),
            sourceItemId = item.id,
            sourcePlanHash = planHash,
            type = item.type,
            status = JobStatus.PENDING,
            title = item.source,
            details = details,
            createdAt = timestamp,
            transferredAt = timestamp
        )
    }

    // ============================================================
    // JOBS
    // ============================================================

    suspend fun getJobs(): List<PlannerJob> {
        return storage.getAllJobs()
    }

    // ============================================================
    // PERSISTENCE
    // ============================================================

    suspend fun persist() {
        storage.savePlannerData(data)
    }

    fun clear() {
        data = createEmptyPlannerData()
    }
    
    /**
     * Reset to draft state while preserving the original plan content.
     * Used when user wants to edit the plan after compilation.
     * Unlike clear(), this keeps the content intact for editing.
     */
    fun resetToDraft() {
        data = data.copy(
            state = PlannerStateEnum.DRAFT,
            compilationResult = null,
            executionItems = emptyList(),
            selectedItemIds = emptySet(),
            lastError = null,
            updatedAt = System.currentTimeMillis()
            // Note: content is preserved, not cleared
        )
    }

    // ============================================================
    // UTILITY
    // ============================================================

    private fun computeHash(input: String): String {
        var hash = 0
        for (char in input) {
            hash = ((hash shl 5) - hash) + char.code
            hash = hash and hash
        }
        return kotlin.math.abs(hash).toString(16).padStart(8, '0')
    }
}
