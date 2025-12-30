package com.guildofsmiths.trademesh.data

/**
 * Trade roles with occupational forms and dexterity data for Guild of Smiths
 * Based on role engine (C-02) and occupational forms (#13)
 */
enum class TradeRole(val displayName: String, val description: String) {
    ELECTRICIAN("Electrician", "NEC compliance, circuit work, high-dexterity wiring"),
    HVAC_TECHNICIAN("HVAC Technician", "Zoning calculations, refrigerant handling, duct work"),
    PLUMBER("Plumber", "Fixture installation, pipe threading, wet environments"),
    CARPENTER("Carpenter / Framer", "Framing tools, precise cuts, structural work"),
    WELDER("Welder / Fabricator", "Weld techniques, metal manipulation, heat resistance"),
    GENERAL_LABORER("General Laborer / Helper", "Basic tools, site prep, versatile manual skills"),
    FOREMAN("Foreman / Crew Lead", "Oversight checklists, team coordination, supervision"),
    SHEET_METAL_WORKER("Sheet Metal Worker", "Metal forming, shears/snips, fabrication"),
    PAINTER("Painter / Finisher", "Brush/roller techniques, surface prep, steady hands"),
    MASON("Mason / Concrete", "Trowel work, block laying, heavy lifting");

    companion object {
        fun fromString(value: String?): TradeRole? {
            return values().find { it.name == value || it.displayName == value }
        }

        fun getDefault(): TradeRole = GENERAL_LABORER
    }
}

/**
 * Occupational knowledge base for each trade role
 * Includes procedures, tools with dexterity notes, regulations, and task breakdowns
 */
object OccupationalForms {

    /**
     * Get the complete knowledge base for a trade role
     */
    fun getKnowledgeBase(role: TradeRole): RoleKnowledgeBase {
        return knowledgeBases[role] ?: knowledgeBases[TradeRole.GENERAL_LABORER]!!
    }

    /**
     * Get dexterity-focused break reminders for a role
     */
    fun getBreakReminders(role: TradeRole): List<String> {
        return getKnowledgeBase(role).breakReminders
    }

    /**
     * Get common tools with dexterity considerations
     */
    fun getToolsWithDexterity(role: TradeRole): List<ToolDexterity> {
        return getKnowledgeBase(role).tools
    }

    /**
     * Get safety protocols specific to the role
     */
    fun getSafetyProtocols(role: TradeRole): List<String> {
        return getKnowledgeBase(role).safetyProtocols
    }

    /**
     * Get regulation references for the role
     */
    fun getRegulations(role: TradeRole): List<String> {
        return getKnowledgeBase(role).regulations
    }

    // ════════════════════════════════════════════════════════════════════
    // TOOL DEXTERITY DATA
    // ════════════════════════════════════════════════════════════════════

    data class ToolDexterity(
        val name: String,
        val dexterityNotes: String,
        val fatigueRisk: String,
        val gripTechnique: String
    )

    data class RoleKnowledgeBase(
        val role: TradeRole,
        val coreSkills: List<String>,
        val procedures: List<String>,
        val tools: List<ToolDexterity>,
        val regulations: List<String>,
        val safetyProtocols: List<String>,
        val breakReminders: List<String>,
        val taskBreakdowns: Map<String, List<String>>,
        val commonPatterns: List<String>
    )

    // ════════════════════════════════════════════════════════════════════
    // OCCUPATIONAL KNOWLEDGE BASES
    // ════════════════════════════════════════════════════════════════════

    private val knowledgeBases = mapOf(

        TradeRole.ELECTRICIAN to RoleKnowledgeBase(
            role = TradeRole.ELECTRICIAN,
            coreSkills = listOf(
                "Circuit analysis and troubleshooting",
                "Precise wire stripping and termination",
                "Panel installation and configuration",
                "Grounding and GFCI compliance",
                "Voltage testing and measurement"
            ),
            procedures = listOf(
                "Lock out/tag out before panel work",
                "Test circuits before energizing",
                "Verify grounding continuity",
                "Document all wire pulls and terminations",
                "Check for proper wire fill in conduits"
            ),
            tools = listOf(
                ToolDexterity(
                    name = "Insulated pliers",
                    dexterityNotes = "Use firm but controlled grip to avoid shorting live wires",
                    fatigueRisk = "Repetitive gripping can cause hand strain after 30-45 minutes",
                    gripTechnique = "Maintain neutral wrist position, squeeze evenly with thumb and fingers"
                ),
                ToolDexterity(
                    name = "Wire strippers",
                    dexterityNotes = "Precise control needed for clean cuts without nicking conductor",
                    fatigueRisk = "Thumb pressure fatigue from repeated stripping",
                    gripTechnique = "Position at base of fingers for leverage, rotate with steady pressure"
                ),
                ToolDexterity(
                    name = "Multimeter probes",
                    dexterityNotes = "Steady hand positioning for accurate contact points",
                    fatigueRisk = "Extended testing sessions cause finger tip fatigue",
                    gripTechnique = "Light touch with index and middle fingers, steady pressure"
                ),
                ToolDexterity(
                    name = "Conduit bender",
                    dexterityNotes = "45° angle grip with even pressure distribution for clean bends",
                    fatigueRisk = "Heavy tool causes forearm strain, take breaks after 3-4 bends",
                    gripTechnique = "Two-handed control, body weight leverage, controlled bending motion"
                )
            ),
            regulations = listOf(
                "NEC 2023 - National Electrical Code",
                "OSHA 1910.333 - Electrical safety standards",
                "NFPA 70E - Electrical safety in workplace",
                "Local building codes and permits"
            ),
            safetyProtocols = listOf(
                "Verify power off with voltage tester before work",
                "Use proper PPE including insulated gloves and eye protection",
                "Maintain proper grounding at all times",
                "Test GFCI circuits monthly",
                "Document arc flash hazard assessments"
            ),
            breakReminders = listOf(
                "After 45 minutes of wire stripping - shake out hands and wrists for circulation",
                "After panel work - stretch forearms and take micro-breaks to prevent tendon strain",
                "After repetitive testing - rest fingers and consider heat therapy for tips",
                "After conduit bending - rotate shoulders and stretch back muscles"
            ),
            taskBreakdowns = mapOf(
                "Panel Upgrade" to listOf(
                    "Step 1: Lock out/tag out main disconnect - use proper grip on handle to avoid slipping",
                    "Step 2: Remove panel cover - controlled lifting with both hands to prevent back strain",
                    "Step 3: Document existing wiring - steady hand positioning for clear photos",
                    "Step 4: Install new breakers - precise finger control for proper seating",
                    "Step 5: Connect ground wires - firm but gentle grip to avoid conductor damage",
                    "Step 6: Test circuits - light touch on probes, take breaks between circuits"
                ),
                "Outlet Installation" to listOf(
                    "Step 1: Mark box location - steady hand for accurate measurements",
                    "Step 2: Cut drywall opening - controlled sawing motion to prevent slips",
                    "Step 3: Install electrical box - even pressure distribution when securing",
                    "Step 4: Pull and strip wires - micro-breaks after each connection to prevent fatigue",
                    "Step 5: Wire outlet - neutral wrist positioning for precise terminations",
                    "Step 6: Secure outlet - firm grip without over-tightening faceplate screws"
                )
            ),
            commonPatterns = listOf(
                "Always test circuits before energizing",
                "Document all work performed with timestamps",
                "Check for proper wire fill ratios",
                "Verify GFCI protection in wet locations",
                "Ground all metal enclosures properly"
            )
        ),

        TradeRole.HVAC_TECHNICIAN to RoleKnowledgeBase(
            role = TradeRole.HVAC_TECHNICIAN,
            coreSkills = listOf(
                "Refrigerant handling and recovery",
                "Ductwork fabrication and installation",
                "Load calculations and zoning",
                "Airflow measurement and balancing",
                "System diagnostics and troubleshooting"
            ),
            procedures = listOf(
                "Verify refrigerant charge with gauges",
                "Check ductwork for proper sealing",
                "Balance air flows in all zones",
                "Document system performance metrics",
                "Calibrate thermostats and controls"
            ),
            tools = listOf(
                ToolDexterity(
                    name = "Manifold gauges",
                    dexterityNotes = "Steady hand positioning for accurate pressure readings",
                    fatigueRisk = "Extended holding causes shoulder strain",
                    gripTechnique = "Support weight with one hand, read with other, alternate sides"
                ),
                ToolDexterity(
                    name = "Pipe cutter",
                    dexterityNotes = "Controlled cutting motion with steady pressure",
                    fatigueRisk = "Repetitive wrist rotation causes strain",
                    gripTechnique = "Two-handed control, body leverage, pause between cuts"
                ),
                ToolDexterity(
                    name = "Flaring tool",
                    dexterityNotes = "Precise alignment for perfect 45° flares",
                    fatigueRisk = "Thumb pressure on handles causes fatigue",
                    gripTechnique = "Position at base of palm, squeeze evenly, rest after 5-6 flares"
                ),
                ToolDexterity(
                    name = "Duct crimper",
                    dexterityNotes = "Maintaining steady positioning for uniform crimps",
                    fatigueRisk = "Heavy tool causes forearm fatigue quickly",
                    gripTechnique = "Two-handed control, controlled pressure, take breaks after 10 crimps"
                )
            ),
            regulations = listOf(
                "EPA Section 608 - Refrigerant handling certification",
                "ASHRAE Standard 62.1 - Ventilation requirements",
                "Local mechanical codes and permits",
                "OSHA respiratory protection standards"
            ),
            safetyProtocols = listOf(
                "Wear proper PPE when handling refrigerants",
                "Test for refrigerant leaks with electronic detector",
                "Ensure proper ventilation in confined spaces",
                "Use fall protection when working on roofs",
                "Document refrigerant recovery and disposal"
            ),
            breakReminders = listOf(
                "After 30 minutes of gauge monitoring - stretch shoulders and rotate neck",
                "After flaring multiple fittings - shake out hands and wrists",
                "After duct crimping - stretch forearms and take seated breaks",
                "After confined space work - full body stretches and hydration"
            ),
            taskBreakdowns = mapOf(
                "System Installation" to listOf(
                    "Step 1: Position equipment - proper lifting technique to avoid back strain",
                    "Step 2: Connect refrigerant lines - steady hand positioning for wrench work",
                    "Step 3: Install ductwork - controlled movements in tight spaces",
                    "Step 4: Wire controls - precise finger control for terminal connections",
                    "Step 5: Test system - monitor gauges steadily, alternate hands",
                    "Step 6: Balance air flows - light touch on adjustment dampers"
                )
            ),
            commonPatterns = listOf(
                "Always recover refrigerant before system work",
                "Check ductwork for air leaks",
                "Verify proper superheat and subcooling",
                "Document system performance after completion",
                "Calibrate all thermostats and sensors"
            )
        ),

        TradeRole.PLUMBER to RoleKnowledgeBase(
            role = TradeRole.PLUMBER,
            coreSkills = listOf(
                "Pipe threading and fitting installation",
                "Fixture mounting and connection",
                "Drain cleaning and repair",
                "Pressure testing and leak detection",
                "Water quality testing and treatment"
            ),
            procedures = listOf(
                "Pressure test all new installations",
                "Check for proper pipe support spacing",
                "Verify all connections are leak-free",
                "Test DWV system for proper drainage",
                "Document pressure test results"
            ),
            tools = listOf(
                ToolDexterity(
                    name = "Pipe wrench",
                    dexterityNotes = "Optimal 45° angle grip to minimize forearm tendon strain",
                    fatigueRisk = "Heavy tool causes rapid forearm fatigue",
                    gripTechnique = "Position at base of fingers, controlled torque, rest after 5-6 turns"
                ),
                ToolDexterity(
                    name = "Threader",
                    dexterityNotes = "Steady forward pressure with even hand positioning",
                    fatigueRisk = "Vibration and pressure cause hand/wrist fatigue",
                    gripTechnique = "Two-handed control, body weight leverage, short sessions"
                ),
                ToolDexterity(
                    name = "Pipe cutter",
                    dexterityNotes = "Controlled cutting motion with steady guide hand",
                    fatigueRisk = "Thumb pressure on trigger causes fatigue",
                    gripTechnique = "Firm guide hand, controlled trigger pulls, alternate hands"
                ),
                ToolDexterity(
                    name = "Drain snake",
                    dexterityNotes = "Controlled feeding with steady hand positioning",
                    fatigueRisk = "Repetitive cranking causes wrist strain",
                    gripTechnique = "Light grip, let tool do work, rest after 10 minutes"
                )
            ),
            regulations = listOf(
                "IPC 2021 - International Plumbing Code",
                "Local plumbing codes and permits",
                "EPA safe drinking water standards",
                "OSHA confined space entry requirements"
            ),
            safetyProtocols = listOf(
                "Wear eye protection when cutting or threading",
                "Test for gas leaks with electronic detector",
                "Use proper PPE in wet environments",
                "Document all pressure tests and results",
                "Follow confined space entry procedures"
            ),
            breakReminders = listOf(
                "After pipe wrench work - stretch forearms and rotate wrists",
                "After threading - shake out hands and take finger breaks",
                "After drain cleaning - full arm stretches and wrist rotations",
                "After wet work - dry hands thoroughly to prevent skin issues"
            ),
            taskBreakdowns = mapOf(
                "Fixture Installation" to listOf(
                    "Step 1: Position fixture - proper lifting to avoid back strain",
                    "Step 2: Mark mounting holes - steady hand for accurate placement",
                    "Step 3: Secure fixture - controlled screwdriver work, neutral wrist",
                    "Step 4: Connect supply lines - precise wrench positioning",
                    "Step 5: Connect drain - controlled tightening without over-torque",
                    "Step 6: Pressure test - monitor gauges steadily, document results"
                )
            ),
            commonPatterns = listOf(
                "Always pressure test new installations",
                "Check for proper pipe slope and support",
                "Verify all connections are leak-free",
                "Document pressure test readings",
                "Test DWV system with water before covering"
            )
        ),

        // Additional roles with basic implementations
        TradeRole.CARPENTER to RoleKnowledgeBase(
            role = TradeRole.CARPENTER,
            coreSkills = listOf("Precise measuring and cutting", "Framing assembly", "Finish carpentry"),
            procedures = listOf("Measure twice, cut once", "Check for level and plumb", "Secure all connections"),
            tools = listOf(
                ToolDexterity("Hammer", "Controlled swing motion", "Shoulder fatigue", "Two-handed grip, controlled swings"),
                ToolDexterity("Circular saw", "Steady guide hand", "Vibration fatigue", "Two-handed control, short sessions")
            ),
            regulations = listOf("IRC building codes", "OSHA fall protection"),
            safetyProtocols = listOf("Wear safety glasses", "Use fall protection", "Check for overhead hazards"),
            breakReminders = listOf("After hammering - shake out arms", "After sawing - rest hands"),
            taskBreakdowns = emptyMap(),
            commonPatterns = listOf("Measure twice, cut once", "Check for level", "Secure all connections")
        ),

        TradeRole.WELDER to RoleKnowledgeBase(
            role = TradeRole.WELDER,
            coreSkills = listOf("Weld techniques", "Metal preparation", "Safety procedures"),
            procedures = listOf("Clean metal surfaces", "Set proper amperage", "Maintain proper arc length"),
            tools = listOf(
                ToolDexterity("Welding torch", "Steady control for consistent arc", "Arm fatigue", "Two-handed steady positioning"),
                ToolDexterity("Angle grinder", "Controlled grinding motion", "Vibration fatigue", "Light grip, short sessions")
            ),
            regulations = listOf("AWS welding standards", "OSHA welding safety"),
            safetyProtocols = listOf("Wear welding helmet", "Use proper ventilation", "Ground work properly"),
            breakReminders = listOf("After welding - rest arms and eyes", "After grinding - shake out hands"),
            taskBreakdowns = emptyMap(),
            commonPatterns = listOf("Clean surfaces thoroughly", "Set correct parameters", "Safety first")
        ),

        TradeRole.GENERAL_LABORER to RoleKnowledgeBase(
            role = TradeRole.GENERAL_LABORER,
            coreSkills = listOf("Basic tool operation", "Site preparation", "Material handling"),
            procedures = listOf("Follow safety protocols", "Use proper lifting techniques", "Communicate with crew"),
            tools = listOf(
                ToolDexterity("Wheelbarrow", "Balanced load distribution", "Back strain", "Proper lifting, even weight"),
                ToolDexterity("Shovel", "Controlled digging motion", "Shoulder fatigue", "Alternate sides, take breaks")
            ),
            regulations = listOf("OSHA general safety", "Local site requirements"),
            safetyProtocols = listOf("Wear proper PPE", "Use safe lifting", "Report hazards"),
            breakReminders = listOf("After lifting - stretch back", "After repetitive work - rotate tasks"),
            taskBreakdowns = emptyMap(),
            commonPatterns = listOf("Safety first", "Use proper lifting", "Communicate clearly")
        ),

        TradeRole.FOREMAN to RoleKnowledgeBase(
            role = TradeRole.FOREMAN,
            coreSkills = listOf("Crew coordination", "Safety oversight", "Quality control"),
            procedures = listOf("Conduct safety meetings", "Monitor work progress", "Document completion"),
            tools = listOf(
                ToolDexterity("Clipboard", "Light documentation work", "Light wrist strain", "Neutral positioning"),
                ToolDexterity("Radio", "Quick communication", "Minimal fatigue", "One-handed use")
            ),
            regulations = listOf("OSHA supervisory requirements", "Project specifications"),
            safetyProtocols = listOf("Conduct daily safety briefings", "Monitor PPE usage", "Stop unsafe work"),
            breakReminders = listOf("During walkthroughs - take seated breaks", "After documentation - stretch wrists"),
            taskBreakdowns = emptyMap(),
            commonPatterns = listOf("Safety first", "Clear communication", "Quality oversight")
        ),

        TradeRole.SHEET_METAL_WORKER to RoleKnowledgeBase(
            role = TradeRole.SHEET_METAL_WORKER,
            coreSkills = listOf("Metal forming", "Precision cutting", "Assembly work"),
            procedures = listOf("Measure accurately", "Cut cleanly", "Form precisely"),
            tools = listOf(
                ToolDexterity("Sheet metal shears", "Controlled cutting motion", "Hand fatigue", "Even pressure, alternate hands"),
                ToolDexterity("Metal brake", "Steady bending control", "Arm strain", "Two-handed operation, controlled motion")
            ),
            regulations = listOf("SMACNA standards", "OSHA metal work safety"),
            safetyProtocols = listOf("Wear gloves", "Use proper supports", "Avoid sharp edges"),
            breakReminders = listOf("After cutting - rest hands", "After bending - stretch arms"),
            taskBreakdowns = emptyMap(),
            commonPatterns = listOf("Measure twice", "Cut once", "Safety with sharp edges")
        ),

        TradeRole.PAINTER to RoleKnowledgeBase(
            role = TradeRole.PAINTER,
            coreSkills = listOf("Surface preparation", "Paint application", "Finish work"),
            procedures = listOf("Prepare surfaces", "Apply evenly", "Clean up properly"),
            tools = listOf(
                ToolDexterity("Paintbrush", "Steady stroke control", "Wrist fatigue", "Light grip, controlled motion"),
                ToolDexterity("Paint roller", "Even pressure distribution", "Shoulder fatigue", "Two-handed control")
            ),
            regulations = listOf("Local paint regulations", "OSHA chemical safety"),
            safetyProtocols = listOf("Proper ventilation", "Wear masks", "Clean spills immediately"),
            breakReminders = listOf("After brush work - shake out wrists", "After rolling - stretch shoulders"),
            taskBreakdowns = emptyMap(),
            commonPatterns = listOf("Prep surfaces", "Apply evenly", "Clean as you go")
        ),

        TradeRole.MASON to RoleKnowledgeBase(
            role = TradeRole.MASON,
            coreSkills = listOf("Block laying", "Mortar mixing", "Level work"),
            procedures = listOf("Mix proper mortar", "Lay level courses", "Check for plumb"),
            tools = listOf(
                ToolDexterity("Trowel", "Controlled material application", "Wrist fatigue", "Neutral wrist, controlled motion"),
                ToolDexterity("Masonry hammer", "Precise striking control", "Arm fatigue", "Two-handed grip, controlled swings")
            ),
            regulations = listOf("ACI concrete standards", "OSHA masonry safety"),
            safetyProtocols = listOf("Wear eye protection", "Use proper lifting", "Avoid overexertion"),
            breakReminders = listOf("After troweling - shake out wrists", "After lifting - stretch back"),
            taskBreakdowns = emptyMap(),
            commonPatterns = listOf("Check level often", "Proper mortar mix", "Safety lifting")
        )
    )
}
