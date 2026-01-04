package com.guildofsmiths.trademesh.planner.types

/**
 * Planner Types - Core data models for the deterministic Planner
 * 
 * CONSTRAINTS:
 * - Deterministic only (no AI at runtime)
 * - Offline-capable
 * - Plaintext-first
 */

// ============================================================
// PLANNER STATE
// ============================================================

enum class PlannerStateEnum {
    EMPTY,          // No content in canvas
    DRAFT,          // Content exists, not compiled
    COMPILING,      // Compilation in progress
    COMPILED,       // Compilation succeeded, items available
    COMPILE_ERROR,  // Compilation failed
    TRANSFERRING    // Transfer in progress
}

// ============================================================
// EXECUTION ITEM TYPES
// ============================================================

enum class ExecutionItemType {
    TASK,
    MATERIAL,
    LABOR
}

data class ExecutionItem(
    val id: String,
    val type: ExecutionItemType,
    val index: Int,
    val lineNumber: Int,
    val section: String,
    val source: String,
    val parsed: ParsedItemData,
    val createdAt: Long
)

sealed class ParsedItemData {
    data class Task(val description: String) : ParsedItemData()
    data class Material(
        val description: String,
        val quantity: String?,
        val unit: String?
    ) : ParsedItemData()
    data class Labor(
        val description: String,
        val hours: String?,
        val role: String?
    ) : ParsedItemData()
}

// ============================================================
// COMPILATION TYPES
// ============================================================

data class CompilationResult(
    val contentHash: String,
    val compiledAt: Long,
    val sections: ParsedSections
)

data class ParsedSections(
    val scope: String?,
    val assumptions: String?,
    val tasks: List<String>,
    val materials: List<String>,
    val labor: List<String>,
    val exclusions: List<String>,
    val summary: String?,
    // Extended GOSPLAN fields
    val jobHeader: JobHeaderData?,
    val financial: FinancialData?,
    val phases: List<String>,
    val safety: List<String>,
    val code: List<String>,
    val notes: List<String>
)

/**
 * Job Header data extracted from ## JobHeader section
 */
data class JobHeaderData(
    val jobTitle: String?,
    val clientName: String?,
    val location: String?,
    val jobType: String?,
    val primaryTrade: String?,
    val urgency: String?,
    val crewSize: Int?,
    val estimatedDays: Int?
)

/**
 * Financial data extracted from ## Financial section
 */
data class FinancialData(
    val estimatedLaborCost: Double?,
    val estimatedMaterialCost: Double?,
    val estimatedTotal: Double?,
    val depositRequired: String?,
    val warranty: String?
)

data class CompileError(
    val code: ErrorCode,
    val message: String,
    val line: Int?,
    val section: String?
)

enum class ErrorCode(val value: String) {
    E001("E001"),  // Missing # PLAN header
    E002("E002"),  // Missing ## Tasks section
    E003("E003"),  // Empty Tasks section
    E004("E004"),  // Malformed section header
    E005("E005"),  // Duplicate section
    E006("E006")   // Unknown section
}

sealed class CompileResult {
    data class Success(
        val result: CompilationResult,
        val items: List<ExecutionItem>
    ) : CompileResult()
    
    data class Failure(val error: CompileError) : CompileResult()
}

data class SectionBoundary(
    val name: String,
    val headerLine: Int,
    val startLine: Int,
    val endLine: Int
)

// ============================================================
// JOB TYPES
// ============================================================

enum class JobStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

data class PlannerJob(
    val id: String,
    val sourceItemId: String,
    val sourcePlanHash: String,
    val type: ExecutionItemType,
    val status: JobStatus,
    val title: String,
    val details: JobDetails,
    val createdAt: Long,
    val transferredAt: Long
)

sealed class JobDetails {
    data class Task(val description: String) : JobDetails()
    data class Material(
        val description: String,
        val quantity: String?,
        val unit: String?
    ) : JobDetails()
    data class Labor(
        val description: String,
        val hours: String?,
        val role: String?
    ) : JobDetails()
}

data class TransferManifest(
    val id: String,
    val timestamp: Long,
    val sourcePlanHash: String,
    val itemIds: List<String>,
    val jobIds: List<String>,
    val status: TransferStatus
)

enum class TransferStatus {
    PENDING,
    COMMITTED,
    ROLLED_BACK
}

sealed class TransferResult {
    data class Success(
        val jobs: List<PlannerJob>,
        val remainingItemCount: Int
    ) : TransferResult()
    
    data class Failure(val error: TransferError) : TransferResult()
}

data class TransferError(
    val code: String,
    val message: String,
    val stage: String
)

// ============================================================
// PLANNER DATA (Full state)
// ============================================================

data class PlannerData(
    val id: String,
    val content: String,
    val contentHash: String,
    val state: PlannerStateEnum,
    val compilationResult: CompilationResult?,
    val executionItems: List<ExecutionItem>,
    val selectedItemIds: Set<String>,
    val lastError: CompileError?,
    val createdAt: Long,
    val updatedAt: Long
)
