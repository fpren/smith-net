package com.guildofsmiths.trademesh.planner.compiler

import com.guildofsmiths.trademesh.planner.types.*
import java.util.UUID

/**
 * PlannerCompiler - Deterministic compiler for canonical plan format
 * 
 * Validates grammar, extracts sections, generates execution items.
 * No AI, no external services, pure string parsing.
 * 
 * CANONICAL GRAMMAR:
 * # PLAN
 * ## Scope
 * ## Assumptions
 * ## Tasks
 * ## Materials
 * ## Labor
 * ## Exclusions
 * ## Summary
 */
class PlannerCompiler {

    companion object {
        private const val PLAN_HEADER = "# PLAN"
        private val SECTION_PATTERN = Regex("""^## ([A-Za-z]+)$""")
        // Extended sections for GOSPLAN template
        private val KNOWN_SECTIONS = listOf(
            "JobHeader", "Scope", "Assumptions", "Tasks", "Materials", "Labor", 
            "Financial", "Phases", "Safety", "Code", "Exclusions", "Notes", "Summary"
        )
        private val ITEM_SECTIONS = listOf("Tasks", "Materials", "Labor")
        
        // Material pattern: "3 boxes Drywall screws" or "- 500 ea Fire brick @ $1.50"
        private val MATERIAL_PATTERN = Regex("""^-?\s*(\d+(?:\.\d+)?)\s+(\w+)\s+(.+?)(?:\s+@\s*\$[\d.]+)?$""")
        
        // Labor pattern: "8h Electrician" or "8 hours Electrician" or "- 40h Lead Mason @ $75/hr"
        private val LABOR_PATTERN = Regex("""^-?\s*(\d+(?:\.\d+)?)\s*h(?:rs?|ours?)?\s+(.+?)(?:\s+@\s*\$[\d.]+(?:/hr)?)?(?:\s*=.*)?$""", RegexOption.IGNORE_CASE)
    }

    fun compile(input: String): CompileResult {
        val timestamp = System.currentTimeMillis()
        val lines = normalizeInput(input)

        // Step 1: Validate plan header
        val planHeaderIndex = lines.indexOfFirst { it == PLAN_HEADER }
        if (planHeaderIndex == -1) {
            return CompileResult.Failure(
                CompileError(
                    code = ErrorCode.E001,
                    message = "Missing plan header: expected '# PLAN'",
                    line = null,
                    section = null
                )
            )
        }

        // Step 2: Extract section boundaries
        val boundariesResult = extractBoundaries(lines, planHeaderIndex)
        if (boundariesResult.error != null) {
            return CompileResult.Failure(boundariesResult.error)
        }
        val sectionMap = boundariesResult.sections

        // Step 3: Validate required sections
        if (!sectionMap.containsKey("Tasks")) {
            return CompileResult.Failure(
                CompileError(
                    code = ErrorCode.E002,
                    message = "Missing required section: '## Tasks'",
                    line = null,
                    section = null
                )
            )
        }

        // Step 4: Extract section content
        val sections = extractContent(lines, sectionMap)

        // Step 5: Validate tasks not empty
        if (sections.tasks.isEmpty()) {
            val tasksSection = sectionMap["Tasks"]!!
            return CompileResult.Failure(
                CompileError(
                    code = ErrorCode.E003,
                    message = "Tasks section must contain at least one task",
                    line = tasksSection.headerLine + 1,
                    section = "Tasks"
                )
            )
        }

        // Step 6: Generate execution items
        val executionItems = generateExecutionItems(lines, sectionMap, timestamp)

        // Step 7: Build result
        val contentHash = computeHash(input)
        val compilationResult = CompilationResult(
            contentHash = contentHash,
            compiledAt = timestamp,
            sections = sections
        )

        return CompileResult.Success(
            result = compilationResult,
            items = executionItems
        )
    }

    private fun normalizeInput(input: String): List<String> {
        return input.split("\n").map { it.trimEnd() }
    }

    private data class BoundariesResult(
        val sections: Map<String, SectionBoundary>,
        val error: CompileError?
    )

    private fun extractBoundaries(lines: List<String>, planHeaderLine: Int): BoundariesResult {
        val sections = mutableMapOf<String, SectionBoundary>()
        val boundaries = mutableListOf<SectionBoundary>()

        for (i in (planHeaderLine + 1) until lines.size) {
            val line = lines[i]
            val match = SECTION_PATTERN.find(line)

            if (match != null) {
                val name = match.groupValues[1]

                // Check unknown section
                if (name !in KNOWN_SECTIONS) {
                    return BoundariesResult(
                        sections = emptyMap(),
                        error = CompileError(
                            code = ErrorCode.E006,
                            message = "Unknown section: '## $name'",
                            line = i + 1,
                            section = name
                        )
                    )
                }

                // Check duplicate
                if (sections.containsKey(name)) {
                    return BoundariesResult(
                        sections = emptyMap(),
                        error = CompileError(
                            code = ErrorCode.E005,
                            message = "Duplicate section: '## $name'",
                            line = i + 1,
                            section = name
                        )
                    )
                }

                val boundary = SectionBoundary(
                    name = name,
                    headerLine = i,
                    startLine = i + 1,
                    endLine = lines.size
                )

                boundaries.add(boundary)
                sections[name] = boundary
            }
        }

        // Adjust end lines
        for (i in 0 until boundaries.size - 1) {
            val current = boundaries[i]
            val next = boundaries[i + 1]
            sections[current.name] = current.copy(endLine = next.headerLine)
        }

        return BoundariesResult(sections = sections, error = null)
    }

    private fun extractContent(lines: List<String>, sectionMap: Map<String, SectionBoundary>): ParsedSections {
        fun getTextContent(name: String): String? {
            val section = sectionMap[name] ?: return null
            val content = lines
                .subList(section.startLine, section.endLine)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
            return content.ifEmpty { null }
        }

        fun getListContent(name: String): List<String> {
            val section = sectionMap[name] ?: return emptyList()
            return lines
                .subList(section.startLine, section.endLine)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        
        // Parse JobHeader key-value pairs
        fun parseJobHeader(): JobHeaderData? {
            val section = sectionMap["JobHeader"] ?: return null
            val headerLines = lines.subList(section.startLine, section.endLine)
            
            fun getValue(prefix: String): String? {
                val line = headerLines.find { it.trim().startsWith(prefix, ignoreCase = true) }
                return line?.substringAfter(":")?.trim()?.takeIf { it.isNotEmpty() && it != "[Client Name]" && it != "[Location]" }
            }
            
            return JobHeaderData(
                jobTitle = getValue("Job Title"),
                clientName = getValue("Client"),
                location = getValue("Location"),
                jobType = getValue("Job Type"),
                primaryTrade = getValue("Primary Trade"),
                urgency = getValue("Urgency"),
                crewSize = getValue("Crew Size")?.filter { it.isDigit() }?.toIntOrNull(),
                estimatedDays = getValue("Est. Duration")?.filter { it.isDigit() }?.toIntOrNull()
            )
        }
        
        // Parse Financial key-value pairs
        fun parseFinancial(): FinancialData? {
            val section = sectionMap["Financial"] ?: return null
            val finLines = lines.subList(section.startLine, section.endLine)
            
            fun getValue(prefix: String): String? {
                val line = finLines.find { it.trim().startsWith(prefix, ignoreCase = true) }
                return line?.substringAfter(":")?.trim()
            }
            
            fun getDouble(prefix: String): Double? {
                val value = getValue(prefix) ?: return null
                return value.replace("$", "").replace(",", "").toDoubleOrNull()
            }
            
            return FinancialData(
                estimatedLaborCost = getDouble("Est. Labor"),
                estimatedMaterialCost = getDouble("Est. Materials"),
                estimatedTotal = getDouble("Est. Total"),
                depositRequired = getValue("Deposit"),
                warranty = getValue("Warranty")
            )
        }

        return ParsedSections(
            scope = getTextContent("Scope"),
            assumptions = getTextContent("Assumptions"),
            tasks = getListContent("Tasks"),
            materials = getListContent("Materials"),
            labor = getListContent("Labor"),
            exclusions = getListContent("Exclusions"),
            summary = getTextContent("Summary"),
            // Extended GOSPLAN fields
            jobHeader = parseJobHeader(),
            financial = parseFinancial(),
            phases = getListContent("Phases"),
            safety = getListContent("Safety"),
            code = getListContent("Code"),
            notes = getListContent("Notes")
        )
    }

    private fun generateExecutionItems(
        lines: List<String>,
        sectionMap: Map<String, SectionBoundary>,
        timestamp: Long
    ): List<ExecutionItem> {
        val items = mutableListOf<ExecutionItem>()
        var index = 0

        val sectionOrder = listOf(
            "Tasks" to ExecutionItemType.TASK,
            "Materials" to ExecutionItemType.MATERIAL,
            "Labor" to ExecutionItemType.LABOR
        )

        for ((name, type) in sectionOrder) {
            val section = sectionMap[name] ?: continue

            for (i in section.startLine until section.endLine) {
                val line = lines[i]
                val trimmed = line.trim()

                if (trimmed.isEmpty()) continue

                val item = ExecutionItem(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    index = index,
                    lineNumber = i + 1,
                    section = name,
                    source = trimmed,
                    parsed = parseLine(trimmed, type),
                    createdAt = timestamp
                )

                items.add(item)
                index++
            }
        }

        return items
    }

    private fun parseLine(line: String, type: ExecutionItemType): ParsedItemData {
        return when (type) {
            ExecutionItemType.TASK -> ParsedItemData.Task(description = line)
            ExecutionItemType.MATERIAL -> parseMaterial(line)
            ExecutionItemType.LABOR -> parseLabor(line)
        }
    }

    private fun parseMaterial(line: String): ParsedItemData.Material {
        val match = MATERIAL_PATTERN.find(line)
        return if (match != null) {
            ParsedItemData.Material(
                quantity = match.groupValues[1],
                unit = match.groupValues[2],
                description = match.groupValues[3]
            )
        } else {
            ParsedItemData.Material(
                quantity = null,
                unit = null,
                description = line
            )
        }
    }

    private fun parseLabor(line: String): ParsedItemData.Labor {
        val match = LABOR_PATTERN.find(line)
        return if (match != null) {
            ParsedItemData.Labor(
                hours = match.groupValues[1],
                role = match.groupValues[2],
                description = match.groupValues[2]
            )
        } else {
            ParsedItemData.Labor(
                hours = null,
                role = null,
                description = line
            )
        }
    }

    private fun computeHash(input: String): String {
        var hash = 0
        for (char in input) {
            hash = ((hash shl 5) - hash) + char.code
            hash = hash and hash
        }
        return kotlin.math.abs(hash).toString(16).padStart(8, '0')
    }
}
