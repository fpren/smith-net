package com.guildofsmiths.trademesh.data

data class MaterialDefault(
    val name: String,
    val unit: String,
    val typicalPrice: Double
)

data class TradeDefault(
    val occupation: String,
    val socCode: String,
    val commonTasks: List<String>,
    val commonEquipment: List<String>,
    val commonMaterials: List<MaterialDefault>
)

object TradeDefaults {

    private val defaults = mapOf(
        "ELECTRICIAN" to TradeDefault(
            occupation = "Electrician",
            socCode = "47-2111",
            commonTasks = listOf(
                "Install outlet", "Replace breaker panel", "Run new circuit",
                "Install light fixture", "Troubleshoot", "Install ceiling fan",
                "Upgrade service entrance", "Install GFCI", "Wire new construction",
                "Install EV charger"
            ),
            commonEquipment = listOf(
                "Multimeter", "Wire strippers", "Conduit bender", "Fish tape",
                "Voltage tester", "Drill", "Level", "Cable puller", "Knockout punch"
            ),
            commonMaterials = listOf(
                MaterialDefault("12/2 Romex", "ft", 0.65),
                MaterialDefault("14/2 Romex", "ft", 0.45),
                MaterialDefault("20A Breaker", "ea", 8.50),
                MaterialDefault("15A Breaker", "ea", 7.00),
                MaterialDefault("Junction Box", "ea", 2.50),
                MaterialDefault("Duplex Outlet", "ea", 1.50),
                MaterialDefault("Light Switch", "ea", 2.00),
                MaterialDefault("3/4\" EMT Conduit", "10ft", 4.50),
                MaterialDefault("Wire Nuts (bag)", "bag", 5.00),
                MaterialDefault("GFCI Outlet", "ea", 15.00)
            )
        ),
        "PLUMBER" to TradeDefault(
            occupation = "Plumber",
            socCode = "47-2152",
            commonTasks = listOf(
                "Replace water heater", "Fix leak", "Install fixture",
                "Clear drain", "Replace toilet", "Install shutoff valve",
                "Repipe section", "Install sump pump", "Water line repair"
            ),
            commonEquipment = listOf(
                "Pipe wrench", "Torch", "PEX crimper", "Drain snake",
                "Level", "Tubing cutter", "Basin wrench", "Plunger"
            ),
            commonMaterials = listOf(
                MaterialDefault("PEX Tubing 1/2\"", "ft", 0.50),
                MaterialDefault("PEX Tubing 3/4\"", "ft", 0.75),
                MaterialDefault("Copper Fitting 1/2\"", "ea", 2.00),
                MaterialDefault("PVC Pipe 2\"", "10ft", 5.00),
                MaterialDefault("Shutoff Valve 1/2\"", "ea", 8.00),
                MaterialDefault("Wax Ring", "ea", 4.00),
                MaterialDefault("Supply Line", "ea", 7.00),
                MaterialDefault("P-Trap", "ea", 6.00)
            )
        ),
        "HVAC" to TradeDefault(
            occupation = "HVAC Technician",
            socCode = "49-9021",
            commonTasks = listOf(
                "AC repair", "Furnace repair", "Install thermostat",
                "Duct cleaning", "Refrigerant recharge", "Install mini-split",
                "Replace blower motor", "System inspection"
            ),
            commonEquipment = listOf(
                "Manifold gauge set", "Vacuum pump", "Leak detector",
                "Multimeter", "Thermometer", "Drill", "Tin snips"
            ),
            commonMaterials = listOf(
                MaterialDefault("R-410A Refrigerant", "lb", 15.00),
                MaterialDefault("Thermostat", "ea", 35.00),
                MaterialDefault("Air Filter", "ea", 8.00),
                MaterialDefault("Capacitor", "ea", 12.00),
                MaterialDefault("Contactor", "ea", 18.00),
                MaterialDefault("Duct Tape (HVAC)", "roll", 10.00)
            )
        ),
        "CARPENTER" to TradeDefault(
            occupation = "Carpenter",
            socCode = "47-2031",
            commonTasks = listOf(
                "Frame wall", "Install door", "Install trim",
                "Build deck", "Install cabinets", "Repair subfloor",
                "Install shelving", "Crown molding"
            ),
            commonEquipment = listOf(
                "Circular saw", "Miter saw", "Drill", "Level",
                "Speed square", "Tape measure", "Nail gun", "Chisel set"
            ),
            commonMaterials = listOf(
                MaterialDefault("2x4 Stud", "ea", 3.50),
                MaterialDefault("2x6 Lumber", "ft", 1.00),
                MaterialDefault("Plywood 4x8 3/4\"", "sheet", 45.00),
                MaterialDefault("Finish Nails (box)", "box", 8.00),
                MaterialDefault("Wood Screws (box)", "box", 9.00),
                MaterialDefault("Construction Adhesive", "tube", 5.00)
            )
        ),
        "GENERAL_CONTRACTOR" to TradeDefault(
            occupation = "General Contractor",
            socCode = "47-1011",
            commonTasks = listOf(
                "Demolition", "Drywall install", "Painting",
                "Flooring install", "Tile work", "General repair",
                "Project management", "Inspection coordination"
            ),
            commonEquipment = listOf(
                "Drill", "Level", "Tape measure", "Utility knife",
                "Pry bar", "Sawzall", "Ladder"
            ),
            commonMaterials = listOf(
                MaterialDefault("Drywall 4x8", "sheet", 12.00),
                MaterialDefault("Joint Compound", "bucket", 15.00),
                MaterialDefault("Paint (gallon)", "gal", 35.00),
                MaterialDefault("Painter's Tape", "roll", 6.00),
                MaterialDefault("Drop Cloth", "ea", 8.00)
            )
        )
    )

    fun getForTrade(occupation: String): TradeDefault? {
        return defaults[occupation.uppercase()]
    }

    fun getSocCode(occupation: String): String? {
        return defaults[occupation.uppercase()]?.socCode
    }
}
