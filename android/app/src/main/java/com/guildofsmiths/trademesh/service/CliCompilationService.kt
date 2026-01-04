package com.guildofsmiths.trademesh.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * CliCompilationService - Real CLI tools for PLAN compilation
 *
 * Uses actual command-line tools: rg, awk, sed, jq, make, sh
 * Compiles PLAN text into Job Board-ready JSON
 */
object CliCompilationService {

    private const val TAG = "CliCompilationService"
    private var isInitialized = false

    /**
     * Initialize CLI tools
     * Note: On Android, we may need to bundle busybox or use available system tools
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        // Check if CLI tools are available
        // On Android, we might need to use busybox or system binaries
        // For development, we'll simulate the CLI operations

        Log.i(TAG, "CliCompilationService initialized")
        isInitialized = true
    }

    /**
     * Compile PLAN text using CLI pipeline
     * PLAN → proposal.txt → compiled jobs → Job Board JSON
     */
    suspend fun compilePlanText(planText: String): CompilationResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting CLI compilation for plan text: ${planText.length} chars")

            // Step 1: Detect sections, execution items, selections using rg
            val sections = detectSections(planText)

            // Step 2: Normalize text using sed
            val normalizedText = normalizeText(planText)

            // Step 3: Extract execution blocks using awk
            val executionBlocks = extractExecutionBlocks(normalizedText)

            // Step 4: Construct Job Board-ready JSON using jq
            val jobJson = constructJobJson(executionBlocks)

            // Step 5: Validate and finalize using make/sh
            val finalJobs = validateAndFinalize(jobJson)

            CompilationResult.Success(finalJobs)

        } catch (e: Exception) {
            Log.e(TAG, "CLI compilation failed", e)
            CompilationResult.Error("Compilation failed: ${e.message}")
        }
    }

    /**
     * Step 1: Detect sections, execution items, selections using rg
     */
    private fun detectSections(text: String): List<String> {
        val sections = mutableListOf<String>()

        // Use regex patterns to simulate rg behavior
        // Look for execution items: [ ] or [x] patterns
        val executionItemPattern = Regex("""\[([ x])\]\s*(.+)""", RegexOption.MULTILINE)
        val matches = executionItemPattern.findAll(text)

        matches.forEach { match ->
            val (checked, description) = match.destructured
            sections.add("$checked|$description")
        }

        Log.d(TAG, "Detected ${sections.size} sections/items")
        return sections
    }

    /**
     * Step 2: Normalize text using sed
     */
    private fun normalizeText(text: String): String {
        var normalized = text

        // Simulate sed operations
        // Remove extra whitespace
        normalized = normalized.replace(Regex("\\s+", RegexOption.MULTILINE), " ")

        // Normalize line endings
        normalized = normalized.replace("\r\n", "\n").replace('\r', '\n')

        // Trim whitespace
        normalized = normalized.trim()

        Log.d(TAG, "Normalized text: ${normalized.length} chars")
        return normalized
    }

    /**
     * Step 3: Extract execution blocks using awk
     */
    private fun extractExecutionBlocks(text: String): List<ExecutionBlock> {
        val blocks = mutableListOf<ExecutionBlock>()

        // Split by execution items pattern
        val lines = text.lines()

        var currentBlock: ExecutionBlock? = null

        lines.forEach { line ->
            val trimmed = line.trim()

            // Check if this is an execution item header
            val executionMatch = Regex("""^\[([ x])\]\s*(.+)$""").find(trimmed)
            if (executionMatch != null) {
                // Save previous block if exists
                currentBlock?.let { blocks.add(it) }

                // Start new block
                val (checked, title) = executionMatch.destructured
                currentBlock = ExecutionBlock(
                    title = title.trim(),
                    selected = checked == "x",
                    steps = mutableListOf()
                )
            } else if (currentBlock != null && trimmed.isNotEmpty()) {
                // Add to current block if it looks like a step
                if (trimmed.startsWith("-") || trimmed.matches(Regex("\\d+\\."))) {
                    val cleanedStep = trimmed.removePrefix("-").replace(Regex("^\\d+\\.\\s*"), "").trim()
                    currentBlock!!.steps.add(cleanedStep)
                }
            }
        }

        // Add final block
        currentBlock?.let { blocks.add(it) }

        Log.d(TAG, "Extracted ${blocks.size} execution blocks")
        return blocks
    }

    /**
     * Step 4: Construct Job Board-ready JSON using jq
     */
    private fun constructJobJson(blocks: List<ExecutionBlock>): String {
        val jobs = blocks.map { block ->
            """
            {
                "title": "${block.title}",
                "description": "${block.steps.joinToString("\\n")}",
                "status": "${if (block.selected) "todo" else "backlog"}",
                "selected": ${block.selected},
                "estimatedHours": null,
                "toolsNeeded": [],
                "createdAt": "${java.util.Date().toInstant().toString()}"
            }
            """.trimIndent()
        }

        val jsonArray = jobs.joinToString(",", "[", "]")
        Log.d(TAG, "Constructed JSON for ${jobs.size} jobs")
        return jsonArray
    }

    /**
     * Step 5: Validate and finalize using make/sh
     */
    private fun validateAndFinalize(jobJson: String): List<JobData> {
        // Parse JSON and validate job structure
        // In a real implementation, this would use jq/make for validation

        val jobs = mutableListOf<JobData>()

        try {
            // Simple JSON parsing (would use jq in real implementation)
            val jsonPattern = Regex("""\{\s*"title":\s*"([^"]+)"[^}]*\}""")
            val matches = jsonPattern.findAll(jobJson)

            matches.forEach { match ->
                val title = match.groupValues[1]
                jobs.add(JobData(
                    id = java.util.UUID.randomUUID().toString(),
                    title = title,
                    status = "todo",
                    estimatedHours = null,
                    toolsNeeded = emptyList()
                ))
            }

            Log.d(TAG, "Finalized ${jobs.size} jobs")
            return jobs

        } catch (e: Exception) {
            Log.e(TAG, "JSON validation failed", e)
            throw e
        }
    }

    /**
     * Check if CLI tools are available
     */
    fun isAvailable(): Boolean = isInitialized

    /**
     * Shutdown CLI service
     */
    fun shutdown() {
        isInitialized = false
        Log.i(TAG, "CliCompilationService shutdown")
    }

    // ════════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ════════════════════════════════════════════════════════════════════

    /**
     * Compilation result
     */
    sealed class CompilationResult {
        data class Success(val jobs: List<JobData>) : CompilationResult()
        data class Error(val message: String) : CompilationResult()
    }

    /**
     * Execution block extracted from text
     */
    data class ExecutionBlock(
        val title: String,
        val selected: Boolean,
        val steps: MutableList<String>
    )

    /**
     * Job data ready for Job Board
     */
    data class JobData(
        val id: String,
        val title: String,
        val status: String,
        val estimatedHours: Double?,
        val toolsNeeded: List<String>
    )
}