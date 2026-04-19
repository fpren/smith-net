package com.guildofsmiths.trademesh.data

/**
 * Full list of 120+ trades matching the desktop SmithNet version.
 * Used in onboarding and settings for trade selection.
 */
object TradesList {
    val ALL_TRADES = listOf(
        // General
        "General Contractor",
        "Handyman",
        "Project Manager",
        "Property Management",
        "Dispatcher",
        "Office Admin",
        "Bookkeeper",
        "Safety Officer",
        "Building Inspector",
        "Cost Estimator",
        "Architect",
        "MEP Engineer",
        "Interior Design",
        "Home Staging",
        "Home Inspection",

        // Structural
        "Framing",
        "Structural Engineering",
        "Civil Engineering",
        "Concrete / Flatwork",
        "Foundation Work",
        "Demolition",
        "Excavation / Earthmoving",
        "Grading / Drainage",
        "Scaffolding",
        "Steel Fabrication",
        "Ironworker / Welding",
        "Masonry / Bricklaying",
        "Stone / Marble Work",
        "Crane Operator",

        // Electrical
        "Electrician",
        "Electrical - Residential",
        "Electrical - Commercial",
        "Electrical - High Voltage",
        "Electrical - Low Voltage",
        "Solar Installation",
        "Audio / Visual Installation",
        "Security Systems",
        "Smart Home / Automation",
        "EV Charging Installation",
        "Generator Installation",
        "Fire Alarm Systems",

        // Plumbing
        "Plumber",
        "Plumbing - Commercial",
        "Plumbing - Residential",
        "Gas Fitter",
        "Waterproofing",

        // HVAC
        "HVAC Tech",
        "HVAC - Residential",
        "HVAC - Commercial",
        "Boiler Technician",
        "Refrigeration Tech",
        "Air Duct Cleaning",
        "Radiant Heating",

        // Carpentry & Finish
        "Carpentry",
        "Cabinet Making",
        "Millwork / Trim",
        "Deck / Patio Construction",
        "Flooring - Hardwood",
        "Flooring - Tile",
        "Flooring - Vinyl",
        "Carpet Installation",
        "Drywall / Plastering",
        "Ceiling Installation",
        "Insulation",
        "Door / Window Installation",
        "Glass / Glazing",
        "Window Tinting",
        "Kitchen / Bath Remodeling",

        // Painting & Coating
        "Painting - Residential",
        "Painting - Commercial",
        "Painting - Industrial",
        "Epoxy Flooring",
        "Tiling",
        "Wood Floor Refinishing",
        "Concrete Polishing",

        // Roofing & Exterior
        "Roofer",
        "Roofing - Flat",
        "Roofing - Shingle",
        "Gutter Installation",
        "Siding Installation",
        "Fence Installation",
        "Asphalt / Paving",
        "Power Washing",
        "Chimney Sweep",
        "Garage Door",

        // Cleaning
        "Cleaning - Residential",
        "Cleaning - Commercial",
        "Cleaning - Post-Construction",
        "Cleaning - Medical / Sterile",
        "Cleaning - Industrial",
        "Janitorial Services",
        "Window Cleaning",
        "Carpet Cleaning",
        "Biohazard Cleaning",
        "Mold Remediation",

        // Landscaping
        "Landscaping",
        "Lawn Care",
        "Tree Service / Arborist",
        "Irrigation / Sprinkler",
        "Hardscaping",
        "Snow Removal",
        "Pool / Spa Service",
        "Outdoor Lighting",

        // Specialty
        "Elevator Maintenance",
        "Fire Suppression",
        "Hazmat Removal",
        "Asbestos Abatement",
        "Lead Abatement",
        "Underground Utilities",
        "Sheet Metal",
        "Locksmith",
        "Appliance Repair",
        "Pest Control",
        "Junk Removal",

        // Services
        "Moving / Relocation",
        "Catering / Food Service",
        "Event Setup / Breakdown",
        "Photography / Videography",
        "IT / Tech Support",
        "Courier / Delivery",
        "Security Guard",
    )

    fun search(query: String): List<String> {
        if (query.isBlank()) return ALL_TRADES
        val lower = query.lowercase()
        return ALL_TRADES.filter { it.lowercase().contains(lower) }
    }
}
