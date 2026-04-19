package com.guildofsmiths.trademesh.ui.plan

/**
 * Rule-based proposal suggestions when AI is unavailable.
 * Maps trade categories + scope keywords to common tasks/equipment/supplies.
 */
object ProposalAssist {

    fun getRuleBasedSuggestion(scope: String, trade: String): ProposalSuggestion {
        val scopeLower = scope.lowercase()
        val category = categorize(trade)
        val jobType = detectJobType(scopeLower)

        return when (category) {
            TradeCategory.PLUMBING -> plumbingSuggestion(scopeLower, jobType)
            TradeCategory.ELECTRICAL -> electricalSuggestion(scopeLower, jobType)
            TradeCategory.CARPENTRY -> carpentrySuggestion(scopeLower, jobType)
            TradeCategory.HVAC -> hvacSuggestion(scopeLower, jobType)
            TradeCategory.PAINTING -> paintingSuggestion(scopeLower, jobType)
            TradeCategory.ROOFING -> roofingSuggestion(scopeLower, jobType)
            TradeCategory.GENERAL -> generalSuggestion(scopeLower, jobType)
        }
    }

    /**
     * Parse an AI response string into a ProposalSuggestion.
     * Expects the format from AIPrompts.generateProposal().
     */
    fun parseAIResponse(response: String): ProposalSuggestion? {
        try {
            val tasks = mutableListOf<String>()
            val equipment = mutableListOf<String>()
            val supplies = mutableListOf<String>()
            var crewSize = 1
            var currentSection = ""

            for (line in response.lines()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("TASKS:", ignoreCase = true) -> currentSection = "tasks"
                    trimmed.startsWith("EQUIPMENT:", ignoreCase = true) -> currentSection = "equipment"
                    trimmed.startsWith("SUPPLIES:", ignoreCase = true) -> currentSection = "supplies"
                    trimmed.startsWith("CREW:", ignoreCase = true) -> {
                        val num = trimmed.substringAfter(":").trim().filter { it.isDigit() }
                        crewSize = num.toIntOrNull() ?: 1
                    }
                    trimmed.isNotBlank() && currentSection.isNotEmpty() -> {
                        val cleaned = trimmed
                            .removePrefix("-").removePrefix("•")
                            .replace(Regex("^\\d+\\.\\s*"), "")
                            .trim()
                        if (cleaned.isNotBlank()) {
                            when (currentSection) {
                                "tasks" -> tasks.add(cleaned)
                                "equipment" -> equipment.add(cleaned)
                                "supplies" -> supplies.add(cleaned)
                            }
                        }
                    }
                }
            }

            if (tasks.isEmpty() && equipment.isEmpty() && supplies.isEmpty()) return null
            return ProposalSuggestion(tasks, equipment, supplies, crewSize)
        } catch (_: Exception) {
            return null
        }
    }

    // ── Trade categorization ──

    private enum class TradeCategory {
        PLUMBING, ELECTRICAL, CARPENTRY, HVAC, PAINTING, ROOFING, GENERAL
    }

    private enum class JobType {
        RENOVATION, INSTALL, REPAIR, NEW_CONSTRUCTION, GENERAL
    }

    private fun categorize(trade: String): TradeCategory {
        val t = trade.lowercase()
        return when {
            t.contains("plumb") || t.contains("pipe") -> TradeCategory.PLUMBING
            t.contains("electri") || t.contains("wiring") -> TradeCategory.ELECTRICAL
            t.contains("carpen") || t.contains("framing") || t.contains("cabinet") ||
                t.contains("trim") || t.contains("finish") -> TradeCategory.CARPENTRY
            t.contains("hvac") || t.contains("heating") || t.contains("cooling") ||
                t.contains("air condition") -> TradeCategory.HVAC
            t.contains("paint") || t.contains("drywall") || t.contains("plaster") -> TradeCategory.PAINTING
            t.contains("roof") || t.contains("gutter") -> TradeCategory.ROOFING
            else -> TradeCategory.GENERAL
        }
    }

    private fun detectJobType(scope: String): JobType = when {
        scope.contains("renovat") || scope.contains("remodel") || scope.contains("demo") -> JobType.RENOVATION
        scope.contains("install") || scope.contains("mount") || scope.contains("set up") -> JobType.INSTALL
        scope.contains("repair") || scope.contains("fix") || scope.contains("replace") || scope.contains("leak") -> JobType.REPAIR
        scope.contains("new") || scope.contains("build") || scope.contains("construct") -> JobType.NEW_CONSTRUCTION
        else -> JobType.GENERAL
    }

    // ── Trade-specific suggestions ──

    private fun plumbingSuggestion(scope: String, jobType: JobType) = ProposalSuggestion(
        tasks = when (jobType) {
            JobType.RENOVATION -> listOf("Shut off water and drain lines", "Demo existing fixtures", "Rough-in new plumbing layout", "Install fixtures and trim", "Pressure test and inspect")
            JobType.REPAIR -> listOf("Diagnose issue and locate source", "Shut off water supply", "Remove and replace damaged components", "Test and verify repair", "Clean up work area")
            JobType.INSTALL -> listOf("Mark layout and verify measurements", "Run supply and drain lines", "Install fixture and connect", "Test for leaks", "Clean up and brief client")
            else -> listOf("Assess scope and existing conditions", "Shut off water and prep area", "Complete plumbing work per scope", "Test all connections", "Final walkthrough with client")
        },
        equipment = listOf("Pipe wrench set", "Tubing cutter", "Propane torch / PEX crimper", "Level", "Inspection camera"),
        supplies = when {
            scope.contains("bath") -> listOf("PEX pipe 1/2\" and 3/4\"", "SharkBite fittings", "Wax ring / flange", "Teflon tape", "Supply lines", "Drain assembly")
            scope.contains("kitchen") -> listOf("Supply lines", "P-trap kit", "Shut-off valves", "Teflon tape", "Plumber's putty", "Drain basket")
            scope.contains("water heater") -> listOf("Flex connectors", "T&P relief valve", "Expansion tank", "Gas flex line", "Teflon tape", "Pipe dope")
            else -> listOf("PEX pipe assorted", "Fittings and connectors", "Teflon tape", "Pipe dope", "Supply lines", "Shut-off valves")
        },
        crewSize = if (jobType == JobType.RENOVATION) 2 else 1
    )

    private fun electricalSuggestion(scope: String, jobType: JobType) = ProposalSuggestion(
        tasks = when (jobType) {
            JobType.INSTALL -> listOf("Turn off power and verify dead", "Run conduit / wire to location", "Install device or fixture", "Make connections and terminate", "Test and restore power")
            JobType.REPAIR -> listOf("Identify circuit and isolate", "Diagnose fault", "Replace damaged component", "Test circuit", "Restore power and verify")
            else -> listOf("Survey existing electrical", "De-energize circuits", "Complete electrical work per scope", "Test all circuits", "Final inspection prep")
        },
        equipment = listOf("Multimeter", "Wire strippers", "Conduit bender", "Fish tape", "Voltage tester"),
        supplies = when {
            scope.contains("panel") -> listOf("Breaker panel", "Circuit breakers", "THHN wire", "Ground rod and clamp", "Conduit and fittings", "Wire nuts")
            scope.contains("outlet") || scope.contains("receptacle") -> listOf("Receptacles", "Cover plates", "Romex 12/2", "Wire nuts", "Electrical boxes", "Cable staples")
            scope.contains("light") -> listOf("Light fixtures", "Switch", "Romex 14/2", "Wire nuts", "Electrical boxes", "Cable connectors")
            else -> listOf("Romex wire assorted", "Wire nuts", "Electrical boxes", "Breakers", "Conduit", "Cable connectors")
        },
        crewSize = if (scope.contains("panel") || jobType == JobType.NEW_CONSTRUCTION) 2 else 1
    )

    private fun carpentrySuggestion(scope: String, jobType: JobType) = ProposalSuggestion(
        tasks = when (jobType) {
            JobType.INSTALL -> listOf("Measure and mark layout", "Cut materials to size", "Install and secure", "Check level and alignment", "Finish and touch up")
            JobType.RENOVATION -> listOf("Protect surrounding areas", "Demo existing work", "Frame and prep substrate", "Install new materials", "Trim and finish")
            else -> listOf("Assess existing conditions", "Measure and plan cuts", "Cut and assemble components", "Install and fasten", "Sand, fill, and finish")
        },
        equipment = listOf("Miter saw", "Circular saw", "Drill/driver", "Level", "Tape measure", "Nail gun"),
        supplies = when {
            scope.contains("cabinet") -> listOf("Cabinet units", "Mounting screws", "Shims", "Handles/pulls", "Wood filler", "Caulk")
            scope.contains("deck") || scope.contains("fence") -> listOf("Pressure treated lumber", "Deck screws", "Post brackets", "Concrete mix", "Joist hangers", "Stain/sealer")
            scope.contains("trim") || scope.contains("molding") -> listOf("Trim boards / molding", "Finish nails", "Wood filler", "Caulk", "Sandpaper", "Paint/stain")
            else -> listOf("Lumber assorted", "Screws and nails", "Wood glue", "Sandpaper", "Wood filler", "Finish material")
        },
        crewSize = if (jobType == JobType.NEW_CONSTRUCTION || scope.contains("deck")) 2 else 1
    )

    private fun hvacSuggestion(scope: String, jobType: JobType) = ProposalSuggestion(
        tasks = listOf("Assess existing system", "Disconnect and remove old unit (if applicable)", "Install new equipment", "Connect ductwork / refrigerant lines", "Test and commission system", "Clean up and brief client"),
        equipment = listOf("Manifold gauge set", "Vacuum pump", "Refrigerant scale", "Multimeter", "Duct crimper"),
        supplies = listOf("Refrigerant", "Duct tape / mastic", "Sheet metal / flex duct", "Thermostat wire", "Line set", "Condensate line"),
        crewSize = 2
    )

    private fun paintingSuggestion(scope: String, jobType: JobType) = ProposalSuggestion(
        tasks = listOf("Prep surfaces — patch, sand, clean", "Mask and protect floors/trim", "Prime surfaces as needed", "Apply finish coats", "Remove masking and touch up", "Final walkthrough"),
        equipment = listOf("Sprayer or roller set", "Brushes assorted", "Drop cloths", "Ladder", "Sander"),
        supplies = when {
            scope.contains("exterior") -> listOf("Exterior paint", "Primer", "Caulk", "Painter's tape", "Sandpaper", "Wood filler")
            else -> listOf("Interior paint", "Primer", "Painter's tape", "Spackle", "Sandpaper", "Caulk")
        },
        crewSize = if (scope.contains("exterior") || scope.contains("whole") || scope.contains("entire")) 2 else 1
    )

    private fun roofingSuggestion(scope: String, jobType: JobType) = ProposalSuggestion(
        tasks = listOf("Set up safety / fall protection", "Remove existing roofing material", "Inspect and repair decking", "Install underlayment and flashing", "Install shingles / roofing material", "Clean up and final inspection"),
        equipment = listOf("Roofing nailer", "Ladder / scaffolding", "Safety harness", "Pry bar", "Chalk line"),
        supplies = listOf("Shingles / roofing material", "Underlayment", "Flashing", "Roofing nails", "Drip edge", "Ridge vent", "Ice & water shield"),
        crewSize = 3
    )

    private fun generalSuggestion(scope: String, jobType: JobType) = ProposalSuggestion(
        tasks = when (jobType) {
            JobType.RENOVATION -> listOf("Protect work area", "Demo existing materials", "Prep substrate", "Install new materials", "Clean up and inspect")
            JobType.REPAIR -> listOf("Assess damage", "Remove damaged materials", "Repair and replace", "Test and verify", "Clean up")
            JobType.INSTALL -> listOf("Mark layout", "Prep mounting surface", "Install per specifications", "Verify and test", "Clean up and brief client")
            else -> listOf("Review scope with client", "Prep work area", "Complete work per scope", "Quality check", "Final walkthrough")
        },
        equipment = listOf("Drill/driver", "Level", "Tape measure", "Utility knife", "Pry bar"),
        supplies = listOf("Fasteners assorted", "Adhesive / caulk", "Shims", "Drop cloths", "Cleanup bags"),
        crewSize = 1
    )
}
