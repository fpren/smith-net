# Solo Contractor Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the app around the solo contractor lifecycle — from lead intake through invoicing — with a Today-First dashboard, guided job creation, client-facing proposal/invoice links, and BLS wage integration.

**Architecture:** Rewrite Android navigation to use a dashboard hub (no tabs/sidebar). Add `JobStage` enum for the contractor pipeline. Create backend endpoints for shareable proposal/invoice web pages. Load BLS wage data into Supabase for labor rate suggestions. Reuse existing JobBoardViewModel, time tracking, invoice generation, and messaging code.

**Tech Stack:** Kotlin/Jetpack Compose (Android), TypeScript/Express (backend), Supabase (PostgreSQL), BLS OEWS API (wage data)

**Spec:** `docs/superpowers/specs/2026-03-21-solo-contractor-foundation-design.md`

---

## File Structure

### Android — New Files
- `android/.../ui/dashboard/DashboardScreen.kt` — Today-First home screen (~250 lines)
- `android/.../ui/dashboard/DashboardViewModel.kt` — Dashboard state: today's jobs, alerts, stats (~150 lines)
- `android/.../ui/jobpipeline/JobPipelineScreen.kt` — Per-job pipeline detail view (~300 lines)
- `android/.../ui/jobpipeline/JobStageBar.kt` — Visual stage indicator component (~80 lines)
- `android/.../ui/newjob/NewJobFlow.kt` — 4-step guided job creation (~350 lines)
- `android/.../data/TradeDefaults.kt` — Static trade-specific suggestions per occupation (~200 lines)
- `android/.../data/WageService.kt` — BLS wage lookup via backend (~60 lines)

### Android — Modified Files
- `android/.../ui/Navigation.kt` — Add dashboard, pipeline, newjob routes; remove plan/sidebar routes
- `android/.../MainActivity.kt` — Revert Scaffold/tabs, wire dashboard as post-auth destination
- `android/.../ui/jobboard/JobBoardTypes.kt` — Add `JobStage` enum, new fields on `Job`
- `android/.../ui/jobboard/JobBoardViewModel.kt` — Add stage transitions, proposal/invoice ID tracking
- `android/.../ui/OnboardingScreen.kt` — Simplify to 3 screens (Trade, About You, Done)
- `android/.../data/UserPreferences.kt` — Add hourly rate, license number, payment info fields

### Android — Deleted Files
- `android/.../ui/components/BottomNavBar.kt`
- `android/.../ui/components/LeftSidebar.kt` (if still exists)

### Backend — New Files
- `backend/src/proposals.ts` — Proposal CRUD, UUID generation, client response handling (~150 lines)
- `backend/src/invoiceLinks.ts` — Invoice link generation and rendering (~100 lines)
- `backend/src/wageData.ts` — BLS wage lookup by zip/trade (~80 lines)

### Backend — Modified Files
- `backend/src/api.ts` — Add proposal, invoice link, and wage data endpoints
- `backend/src/types.ts` — Add Proposal, InvoiceLink types

### Database — New Migrations
- `supabase/migrations/005_proposals.sql` — proposals table
- `supabase/migrations/006_invoice_links.sql` — invoice_links table
- `supabase/migrations/007_wage_data.sql` — wage_data + zip_metro_map tables

### Web — New Files
- `backend/src/templates/proposal.html` — Client-facing proposal page template
- `backend/src/templates/invoice.html` — Client-facing invoice page template

---

## Task 1: Add JobStage Enum and Update Job Data Model

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobBoardTypes.kt`

- [ ] **Step 1: Add JobStage enum**

Add after the existing `JobStatus` enum:

```kotlin
enum class JobStage(val displayName: String, val icon: String) {
    LEAD("Lead", "○"),
    PROPOSAL("Proposal", "◫"),
    APPROVED("Approved", "✓"),
    IN_PROGRESS("In Progress", "▓"),
    REVIEW("Review", "█"),
    INVOICE("Invoice", "$"),
    CLOSED("Closed", "◆")
}
```

- [ ] **Step 2: Add new fields to Job data class**

Add these fields to the `Job` data class (with defaults so existing code doesn't break):

```kotlin
    val clientPhone: String = "",
    val clientAddress: String = "",
    val proposalId: String? = null,
    val invoiceId: String? = null,
    val hourlyRate: Double = 0.0,
    val photos: List<String> = emptyList(),
    val stage: JobStage = JobStage.LEAD,
    val equipmentList: List<String> = emptyList(),
```

- [ ] **Step 3: Add stage mapping helper**

Add a function to map old JobStatus to new JobStage:

```kotlin
fun JobStatus.toStage(): JobStage = when (this) {
    JobStatus.BACKLOG -> JobStage.LEAD
    JobStatus.TODO -> JobStage.LEAD
    JobStatus.IN_PROGRESS -> JobStage.IN_PROGRESS
    JobStatus.REVIEW -> JobStage.REVIEW
    JobStatus.DONE -> JobStage.CLOSED
    JobStatus.ARCHIVED -> JobStage.CLOSED // Note: also set isArchived=true when migrating ARCHIVED jobs
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd /Users/fegensprenelon/smith-net/android && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo $JAVA_HOME) ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobBoardTypes.kt
git commit -m "feat: add JobStage enum and new fields to Job data model

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Update Navigation Routes

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/Navigation.kt`

- [ ] **Step 1: Add new routes and remove obsolete ones**

Replace the entire `NavRoutes` object with:

```kotlin
object NavRoutes {
    // AUTH FLOW
    const val AUTH = "auth"
    const val ONBOARDING = "onboarding"
    const val WELCOME = "welcome"

    // MAIN
    const val DASHBOARD = "dashboard"
    @Deprecated("Removed in Task 9") const val PLAN = "plan"
    @Deprecated("Removed in Task 9") const val PLAN_MODE = "plan_mode"
    @Deprecated("Removed in Task 9") const val INTENT = "intent"
    @Deprecated("Removed in Task 9") const val SOLO_DASHBOARD = "solo_dashboard"

    // JOB PIPELINE
    const val JOB_PIPELINE = "job_pipeline/{jobId}"
    const val NEW_JOB = "new_job"

    // EXISTING
    const val JOB_BOARD = "job_board"
    const val TIME_TRACKING = "time_clock"
    const val ARCHIVE = "archive"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"

    // MESSAGING
    const val BEACON_LIST = "beacons"
    const val CHANNEL_LIST = "channels/{beaconId}"
    const val CONVERSATION = "conversation/{beaconId}/{channelId}"
    const val CONVERSATION_DM = "conversation/{beaconId}/{channelId}?dmPeerId={dmPeerId}&dmPeerName={dmPeerName}"
    const val DASHBOARD_CHANNELS = "dashboard_channels"
    const val CREATE_CHANNEL = "create_channel/{beaconId}"
    const val CREATE_BEACON = "create_beacon"
    const val PEERS = "peers"

    // HELPERS
    fun jobPipeline(jobId: String) = "job_pipeline/$jobId"
    fun channelList(beaconId: String) = "channels/$beaconId"
    fun conversation(beaconId: String, channelId: String) = "conversation/$beaconId/$channelId"
    fun conversationDM(beaconId: String, channelId: String, peerId: String, peerName: String) =
        "conversation/$beaconId/$channelId?dmPeerId=$peerId&dmPeerName=$peerName"
    fun createChannel(beaconId: String) = "create_channel/$beaconId"
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/fegensprenelon/smith-net/android && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo $JAVA_HOME) ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (or fix any references to removed routes like PLAN, PLAN_MODE, INTENT, SOLO_DASHBOARD)

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/Navigation.kt
git commit -m "refactor: update NavRoutes for dashboard-centric navigation

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Add UserPreferences Fields for Contractor Profile

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/data/UserPreferences.kt`

- [ ] **Step 1: Add new preference keys and methods**

Add these constants after the existing keys:

```kotlin
    private const val KEY_HOURLY_RATE = "hourly_rate"
    private const val KEY_LICENSE_NUMBER = "license_number"
    private const val KEY_PAYMENT_INFO = "payment_info"
```

Add these methods after the existing methods:

```kotlin
    fun getHourlyRate(): Double {
        return prefs?.getString(KEY_HOURLY_RATE, "0.0")?.toDoubleOrNull() ?: 0.0
    }

    fun setHourlyRate(rate: Double) {
        prefs?.edit()?.putString(KEY_HOURLY_RATE, rate.toString())?.apply()
    }

    fun getLicenseNumber(): String {
        return prefs?.getString(KEY_LICENSE_NUMBER, "") ?: ""
    }

    fun setLicenseNumber(license: String) {
        prefs?.edit()?.putString(KEY_LICENSE_NUMBER, license.trim())?.apply()
    }

    fun getPaymentInfo(): String {
        return prefs?.getString(KEY_PAYMENT_INFO, "") ?: ""
    }

    fun setPaymentInfo(info: String) {
        prefs?.edit()?.putString(KEY_PAYMENT_INFO, info.trim())?.apply()
    }
```

- [ ] **Step 2: Verify compilation**

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/data/UserPreferences.kt
git commit -m "feat: add hourly rate, license number, payment info to UserPreferences

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Create Trade Defaults Data

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/data/TradeDefaults.kt`

- [ ] **Step 1: Create the trade defaults file**

```kotlin
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
```

- [ ] **Step 2: Verify compilation**

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/data/TradeDefaults.kt
git commit -m "feat: add trade-specific defaults for tasks, equipment, and materials

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Simplify Onboarding to 3 Screens

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/OnboardingScreen.kt`

- [ ] **Step 1: Update OnboardingScreen enum**

Change from 4 screens to 3:

```kotlin
enum class OnboardingScreen {
    TRADE,           // Your Trade + Experience
    ABOUT_YOU,       // Name, Business, Address, Rate
    DONE             // Welcome, go to dashboard
}
```

- [ ] **Step 2: Rewrite the OnboardingScreen composable**

Replace the main `OnboardingScreen` composable. Keep the existing `Occupation` and `ExperienceLevel` enums. The 3 screens are:

**Screen 1 — TRADE:** Occupation picker (existing list) + experience level picker (existing list). Use the existing selection UI components.

**Screen 2 — ABOUT_YOU:**
- Name (pre-filled from SupabaseAuth if available)
- Business name (optional)
- Address fields (street, city, state, zip — reuse existing address fields)
- Hourly rate — text field with suggestion text: "What do you charge per hour?"
- License number (optional)

**Screen 3 — DONE:**
- "Welcome to Smith Net" header
- Summary of what they entered
- [Go to Dashboard] button → calls `onComplete()`

Save all data to UserPreferences on each screen advance (not just at the end). Use `UserPreferences.setHourlyRate()` and `UserPreferences.setLicenseNumber()` from Task 3.

- [ ] **Step 3: Update page indicator from 4 dots to 3**

- [ ] **Step 4: Verify compilation**

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/OnboardingScreen.kt
git commit -m "refactor: simplify onboarding to 3 screens — Trade, About You, Done

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Create Dashboard Screen

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/dashboard/DashboardViewModel.kt`
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/dashboard/DashboardScreen.kt`

- [ ] **Step 1: Create DashboardViewModel**

```kotlin
package com.guildofsmiths.trademesh.ui.dashboard

import androidx.lifecycle.ViewModel
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DashboardAlert(
    val jobId: String,
    val message: String,
    val type: AlertType
)

enum class AlertType { APPROVAL, OVERDUE_TASK, UNPAID_INVOICE, CLIENT_RESPONSE }

class DashboardViewModel : ViewModel() {
    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private val _alerts = MutableStateFlow<List<DashboardAlert>>(emptyList())
    val alerts: StateFlow<List<DashboardAlert>> = _alerts.asStateFlow()

    fun loadJobs(allJobs: List<Job>) {
        _jobs.value = allJobs.filter { it.stage != JobStage.CLOSED }
        _alerts.value = buildAlerts(allJobs)
    }

    private fun buildAlerts(allJobs: List<Job>): List<DashboardAlert> {
        val alerts = mutableListOf<DashboardAlert>()
        allJobs.forEach { job ->
            when {
                job.stage == JobStage.PROPOSAL ->
                    alerts.add(DashboardAlert(job.id, "${job.clientName ?: job.title} — proposal awaiting response", AlertType.APPROVAL))
                job.stage == JobStage.INVOICE ->
                    alerts.add(DashboardAlert(job.id, "${job.clientName ?: job.title} — invoice unpaid", AlertType.UNPAID_INVOICE))
            }
        }
        return alerts
    }

    fun getActiveJobCount(): Int = _jobs.value.count { it.stage != JobStage.CLOSED }

    fun getOutstandingTotal(): Double = _jobs.value
        .filter { it.stage == JobStage.INVOICE }
        .sumOf { it.materials.sumOf { m -> m.totalCost } + (it.hourlyRate * 8) } // rough estimate

    fun getBusinessName(): String {
        val biz = UserPreferences.getBusinessName()
        return biz.ifBlank { UserPreferences.getDisplayName() }
    }
}
```

- [ ] **Step 2: Create DashboardScreen composable**

```kotlin
package com.guildofsmiths.trademesh.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage

@Composable
fun DashboardScreen(
    jobs: List<Job>,
    onJobClick: (String) -> Unit,
    onNewJob: () -> Unit,
    onClockIn: () -> Unit,
    onMessages: () -> Unit,
    onSettings: () -> Unit,
    onArchive: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    LaunchedEffect(jobs) { viewModel.loadJobs(jobs) }

    val alerts by viewModel.alerts.collectAsState()
    val activeJobs by viewModel.jobs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = viewModel.getBusinessName(), style = ConsoleTheme.title)
            Row {
                Text(
                    text = "[Msg]",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable { onMessages() }.padding(4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "[⚙]",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable { onSettings() }.padding(4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        ConsoleSeparator()
        Spacer(modifier = Modifier.height(16.dp))

        // Needs Attention
        if (alerts.isNotEmpty()) {
            Text(text = "NEEDS ATTENTION", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(8.dp))
            alerts.forEach { alert ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .clickable { onJobClick(alert.jobId) }
                        .padding(12.dp)
                ) {
                    Text(
                        text = "! ",
                        style = ConsoleTheme.bodyBold,
                        color = ConsoleTheme.warning
                    )
                    Text(text = alert.message, style = ConsoleTheme.bodySmall)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Active Jobs
        Text(text = "JOBS", style = ConsoleTheme.captionBold)
        Spacer(modifier = Modifier.height(8.dp))

        if (activeJobs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No active jobs.\nTap [+ NEW JOB] to get started.",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
            }
        } else {
            activeJobs.forEach { job ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .clickable { onJobClick(job.id) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${job.stage.icon} ${job.clientName ?: job.title}",
                            style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
                        )
                        Text(
                            text = "${job.stage.displayName} · ${job.clientAddress.take(30)}",
                            style = ConsoleTheme.caption
                        )
                    }
                    Text(text = ">", style = ConsoleTheme.body, color = ConsoleTheme.textMuted)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "[+ NEW JOB]",
                style = ConsoleTheme.action,
                modifier = Modifier
                    .clickable { onNewJob() }
                    .background(ConsoleTheme.surface)
                    .padding(12.dp)
            )
            Text(
                text = "[CLOCK IN]",
                style = ConsoleTheme.action,
                modifier = Modifier
                    .clickable { onClockIn() }
                    .background(ConsoleTheme.surface)
                    .padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "${viewModel.getActiveJobCount()}", style = ConsoleTheme.bodyBold)
                Text(text = "Active", style = ConsoleTheme.caption)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "$${String.format("%.0f", viewModel.getOutstandingTotal())}", style = ConsoleTheme.bodyBold)
                Text(text = "Outstanding", style = ConsoleTheme.caption)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Archive link
        Text(
            text = "[Archive]",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
            modifier = Modifier.clickable { onArchive() }.padding(4.dp)
        )
    }
}
```

- [ ] **Step 3: Verify compilation**

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/dashboard/
git commit -m "feat: create Today-First dashboard screen with alerts, jobs list, quick actions

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Create Job Pipeline Detail Screen

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobpipeline/JobStageBar.kt`
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobpipeline/JobPipelineScreen.kt`

- [ ] **Step 1: Create JobStageBar component**

```kotlin
package com.guildofsmiths.trademesh.ui.jobpipeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.JobStage

@Composable
fun JobStageBar(currentStage: JobStage, modifier: Modifier = Modifier) {
    val stages = JobStage.entries.toList()
    val currentIndex = stages.indexOf(currentStage)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        stages.forEach { stage ->
            val stageIndex = stages.indexOf(stage)
            val color = when {
                stageIndex < currentIndex -> ConsoleTheme.success
                stageIndex == currentIndex -> ConsoleTheme.accent
                else -> ConsoleTheme.textDim
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stage.icon, style = ConsoleTheme.bodySmall, color = color)
                Text(
                    text = stage.displayName.take(4),
                    style = ConsoleTheme.caption,
                    color = color
                )
            }
        }
    }
}
```

- [ ] **Step 2: Create JobPipelineScreen**

This is a large composable. Key sections:
- Stage bar at top
- Client info (name, phone tap-to-call, address tap-to-navigate)
- Scope description
- Tasks checklist
- Materials list with checkbox
- Equipment list
- Time log entries
- Photos grid
- Price breakdown (labor + materials + total)
- Stage-specific action buttons at bottom

```kotlin
package com.guildofsmiths.trademesh.ui.jobpipeline

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.*

@Composable
fun JobPipelineScreen(
    job: Job,
    onBack: () -> Unit,
    onStageAction: (Job, JobStage) -> Unit,
    onToggleMaterial: (Int) -> Unit,
    onClockIn: () -> Unit,
    onShareProposal: () -> Unit,
    onShareInvoice: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        ConsoleHeader(
            title = job.clientName ?: job.title,
            onBackClick = onBack
        )

        JobStageBar(currentStage = job.stage)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Client Info
            if (job.clientName?.isNotBlank() == true || job.clientPhone.isNotBlank()) {
                SectionHeader("CLIENT")
                if (job.clientName?.isNotBlank() == true) {
                    Text(text = job.clientName, style = ConsoleTheme.body)
                }
                if (job.clientPhone.isNotBlank()) {
                    Text(
                        text = "☎ ${job.clientPhone}",
                        style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${job.clientPhone}"))
                            context.startActivity(intent)
                        }
                    )
                }
                if (job.clientAddress.isNotBlank()) {
                    Text(
                        text = "⌖ ${job.clientAddress}",
                        style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(job.clientAddress)}"))
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // Scope
            if (job.description.isNotBlank()) {
                SectionHeader("SCOPE")
                Text(text = job.description, style = ConsoleTheme.bodySmall)
            }

            // Tasks
            if (job.materials.isNotEmpty() || job.workLog.isNotEmpty()) {
                ConsoleSeparator()
            }

            // Materials
            if (job.materials.isNotEmpty()) {
                SectionHeader("MATERIALS (${job.materials.count { it.checked }}/${job.materials.size})")
                job.materials.forEachIndexed { index, material ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface)
                            .clickable { onToggleMaterial(index) }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (material.checked) "[x]" else "[ ]",
                            style = ConsoleTheme.body,
                            modifier = Modifier.width(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = material.name, style = ConsoleTheme.bodySmall)
                            if (material.quantity > 0 && material.unitCost > 0) {
                                Text(
                                    text = "${material.quantity} ${material.unit} × $${material.unitCost}",
                                    style = ConsoleTheme.caption
                                )
                            }
                        }
                        if (material.totalCost > 0) {
                            Text(text = "$${String.format("%.2f", material.totalCost)}", style = ConsoleTheme.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            // Equipment
            if (job.equipmentList.isNotEmpty()) {
                SectionHeader("EQUIPMENT")
                job.equipmentList.forEach { item ->
                    Text(text = "  - $item", style = ConsoleTheme.bodySmall)
                }
            }

            // Price Breakdown
            ConsoleSeparator()
            SectionHeader("PRICE")
            val materialsCost = job.materials.sumOf { it.totalCost }
            val laborCost = job.hourlyRate * 8 // placeholder — actual hours from time entries
            Text(text = "Labor: $${String.format("%.2f", laborCost)}", style = ConsoleTheme.bodySmall)
            Text(text = "Materials: $${String.format("%.2f", materialsCost)}", style = ConsoleTheme.bodySmall)
            Text(
                text = "Total: $${String.format("%.2f", laborCost + materialsCost)}",
                style = ConsoleTheme.bodyBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stage-specific actions
            when (job.stage) {
                JobStage.LEAD -> {
                    ActionButton("[CREATE PROPOSAL]") { onStageAction(job, JobStage.PROPOSAL) }
                }
                JobStage.PROPOSAL -> {
                    ActionButton("[SHARE WITH CLIENT]") { onShareProposal() }
                }
                JobStage.APPROVED -> {
                    ActionButton("[START WORK]") { onStageAction(job, JobStage.IN_PROGRESS) }
                }
                JobStage.IN_PROGRESS -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("[CLOCK IN]") { onClockIn() }
                        ActionButton("[MARK COMPLETE]") { onStageAction(job, JobStage.REVIEW) }
                    }
                }
                JobStage.REVIEW -> {
                    val unchecked = job.materials.count { !it.checked }
                    if (unchecked > 0) {
                        Text(
                            text = "! $unchecked materials not checked off",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning)
                        )
                    }
                    ActionButton("[GENERATE INVOICE]") { onStageAction(job, JobStage.INVOICE) }
                }
                JobStage.INVOICE -> {
                    ActionButton("[SHARE INVOICE]") { onShareInvoice() }
                    Spacer(modifier = Modifier.height(4.dp))
                    ActionButton("[MARK PAID — CLOSE]") { onStageAction(job, JobStage.CLOSED) }
                }
                JobStage.CLOSED -> {
                    Text(text = "Job closed.", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text = text, style = ConsoleTheme.captionBold)
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = ConsoleTheme.action,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(ConsoleTheme.surface)
            .padding(12.dp)
    )
}
```

- [ ] **Step 3: Verify compilation**

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobpipeline/
git commit -m "feat: create Job Pipeline detail screen with stage bar, client info, materials, actions

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Create Guided New Job Flow

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/newjob/NewJobFlow.kt`

- [ ] **Step 1: Create the 4-step guided flow**

The flow has 4 steps: Client → Scope → What's Needed → Timeline & Price.

Each step is a composable inside one screen with a step counter. Trade-specific suggestions come from `TradeDefaults.getForTrade()`. Use the contractor's occupation from `UserPreferences.getOccupation()`.

Key behaviors:
- Back button goes to previous step (or exits on step 1)
- Progress indicator shows step N of 4
- Step 3 has "Suggestions" button that shows trade defaults in a picker
- Step 4 auto-calculates labor from hours × `UserPreferences.getHourlyRate()` and materials sum from step 3
- On complete: calls `onJobCreated(Job)` callback with all fields populated, stage = LEAD

This file will be ~350 lines. Create it with all 4 step composables and the main `NewJobFlow` composable that manages step state.

- [ ] **Step 2: Verify compilation**

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/newjob/
git commit -m "feat: create 4-step guided New Job flow with trade suggestions

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Rewrite MainActivity Navigation

**Dependency:** Task 10 (moveJobStage) MUST be completed before Step 4 of this task.

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/MainActivity.kt`
- Delete: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/components/BottomNavBar.kt`

- [ ] **Step 1: Remove bottom tab bar imports and Scaffold wrapper**

Remove these imports:
```kotlin
import com.guildofsmiths.trademesh.ui.components.BottomNavBar
import com.guildofsmiths.trademesh.ui.components.MAIN_ROUTES
import com.guildofsmiths.trademesh.ui.components.NavTab
```

Remove the `currentBackStackEntryAsState` import and usage.

Revert the `Scaffold` wrapper — remove the `Scaffold(bottomBar = {...}) { innerPadding ->` and its closing `}`. The `NavHost` should be directly inside the `Surface`.

Remove `Modifier.padding(innerPadding)` from NavHost.

- [ ] **Step 2: Change post-auth destination to DASHBOARD**

In the `startDestination` logic, change the final else from `NavRoutes.PLAN` to `NavRoutes.DASHBOARD`:

```kotlin
else -> NavRoutes.DASHBOARD
```

In onboarding `onComplete`, navigate to `NavRoutes.DASHBOARD` instead of `NavRoutes.PLAN`.

In auth `onAuthSuccess`, navigate to `NavRoutes.DASHBOARD` (if onboarding complete) instead of `NavRoutes.BEACON_LIST`.

- [ ] **Step 3: Add Dashboard composable route**

```kotlin
composable(NavRoutes.DASHBOARD) {
    if (UserPreferences.isOnboardingDataComplete()) {
        initializeCommunication()
    }

    val jobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel()
    val jobs by jobViewModel.jobs.collectAsState()

    com.guildofsmiths.trademesh.ui.dashboard.DashboardScreen(
        jobs = jobs,
        onJobClick = { jobId ->
            navController.navigate(NavRoutes.jobPipeline(jobId))
        },
        onNewJob = {
            navController.navigate(NavRoutes.NEW_JOB)
        },
        onClockIn = {
            navController.navigate(NavRoutes.TIME_TRACKING)
        },
        onMessages = {
            navController.navigate(NavRoutes.BEACON_LIST)
        },
        onSettings = {
            navController.navigate(NavRoutes.SETTINGS)
        },
        onArchive = {
            navController.navigate(NavRoutes.ARCHIVE)
        }
    )
}
```

- [ ] **Step 4: Add Job Pipeline composable route**

```kotlin
composable(
    route = NavRoutes.JOB_PIPELINE,
    arguments = listOf(navArgument("jobId") { type = NavType.StringType })
) { backStackEntry ->
    val jobId = backStackEntry.arguments?.getString("jobId") ?: return@composable
    val jobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel()
    val jobs by jobViewModel.jobs.collectAsState()
    val job = jobs.find { it.id == jobId }

    if (job != null) {
        com.guildofsmiths.trademesh.ui.jobpipeline.JobPipelineScreen(
            job = job,
            onBack = { navController.popBackStack() },
            onStageAction = { j, newStage ->
                // moveJobStage added in Task 10
                jobViewModel.moveJobStage(j.id, newStage)
            },
            onToggleMaterial = { index ->
                jobViewModel.toggleMaterial(jobId, index)
            },
            onClockIn = {
                navController.navigate(NavRoutes.TIME_TRACKING)
            },
            onShareProposal = { /* TODO: Task 11 */ },
            onShareInvoice = { /* TODO: Task 12 */ }
        )
    }
}
```

Note: `moveJob` currently takes `JobStatus`. We need to add a `moveJobStage` function in the next task, or update `moveJob` to accept `JobStage`. For now, add a `TODO` comment.

- [ ] **Step 5: Add New Job composable route**

```kotlin
composable(NavRoutes.NEW_JOB) {
    val jobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel()

    com.guildofsmiths.trademesh.ui.newjob.NewJobFlow(
        onBack = { navController.popBackStack() },
        onJobCreated = { newJob ->
            // Create via ViewModel, then navigate to its pipeline
            jobViewModel.createJob(
                title = newJob.title,
                description = newJob.description,
                materials = newJob.materials,
                crewSize = newJob.crewSize,
                crew = newJob.crew
            )
            navController.popBackStack()
        }
    )
}
```

- [ ] **Step 6: Remove old Plan composable route**

Delete the `composable(NavRoutes.PLAN)` block.

- [ ] **Step 7: Delete BottomNavBar.kt**

```bash
rm android/app/src/main/java/com/guildofsmiths/trademesh/ui/components/BottomNavBar.kt
```

- [ ] **Step 8: Verify compilation**

Fix any remaining references to `NavRoutes.PLAN` or removed routes.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor: rewrite MainActivity navigation — dashboard hub, pipeline detail, new job flow

Removes bottom tab bar. Dashboard is the post-auth destination.
Jobs open into pipeline detail view. New guided flow for job creation.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: Update JobBoardViewModel for Stage Transitions

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobBoardViewModel.kt`

- [ ] **Step 1: Add moveJobStage function**

```kotlin
fun moveJobStage(jobId: String, newStage: JobStage) {
    _jobs.value = _jobs.value.map { job ->
        if (job.id == jobId) job.copy(stage = newStage, updatedAt = System.currentTimeMillis())
        else job
    }
    _selectedJob.value?.let { selected ->
        if (selected.id == jobId) {
            _selectedJob.value = selected.copy(stage = newStage)
        }
    }
    syncToRepository()
}
```

- [ ] **Step 2: Update createJob to set stage and new fields**

Add parameters to `createJob` for the new fields:

```kotlin
fun createJob(
    title: String,
    description: String = "",
    priority: Priority = Priority.MEDIUM,
    toolsNeeded: String = "",
    expenses: String = "",
    crewSize: Int = 1,
    crew: List<CrewMember> = emptyList(),
    materials: List<Material> = emptyList(),
    estimatedStartDate: Long? = null,
    estimatedEndDate: Long? = null,
    // New fields
    clientName: String? = null,
    clientPhone: String = "",
    clientAddress: String = "",
    hourlyRate: Double = 0.0,
    equipmentList: List<String> = emptyList(),
    stage: JobStage = JobStage.LEAD
)
```

Set the new fields on the `Job` constructor.

- [ ] **Step 3: Verify compilation**

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobBoardViewModel.kt
git commit -m "feat: add stage transitions and new job fields to JobBoardViewModel

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: Backend — Proposal Endpoints and Web Page

**Files:**
- Create: `supabase/migrations/005_proposals.sql`
- Create: `backend/src/proposals.ts`
- Create: `backend/src/templates/proposal.html`
- Modify: `backend/src/api.ts`
- Modify: `backend/src/types.ts`

- [ ] **Step 1: Create proposals migration**

```sql
CREATE TABLE IF NOT EXISTS proposals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id TEXT NOT NULL,
    uuid TEXT UNIQUE NOT NULL DEFAULT gen_random_uuid()::text,
    contractor_name TEXT,
    contractor_phone TEXT,
    contractor_license TEXT,
    client_name TEXT,
    client_address TEXT,
    scope TEXT,
    tasks JSONB DEFAULT '[]',
    materials JSONB DEFAULT '[]',
    equipment JSONB DEFAULT '[]',
    labor_hours DECIMAL DEFAULT 0,
    labor_rate DECIMAL DEFAULT 0,
    labor_cost DECIMAL DEFAULT 0,
    materials_cost DECIMAL DEFAULT 0,
    total_cost DECIMAL DEFAULT 0,
    status TEXT DEFAULT 'pending',
    client_response TEXT,
    client_notes TEXT,
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '30 days'),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_proposals_uuid ON proposals(uuid);
CREATE INDEX idx_proposals_job_id ON proposals(job_id);
```

- [ ] **Step 2: Create proposals.ts service**

```typescript
import { supabase } from './supabase';

export interface ProposalData {
  jobId: string;
  contractorName: string;
  contractorPhone: string;
  contractorLicense: string;
  clientName: string;
  clientAddress: string;
  scope: string;
  tasks: string[];
  materials: Array<{ name: string; quantity: number; unit: string; unitCost: number; totalCost: number }>;
  equipment: string[];
  laborHours: number;
  laborRate: number;
  laborCost: number;
  materialsCost: number;
  totalCost: number;
}

export class ProposalService {
  async createProposal(data: ProposalData): Promise<{ uuid: string } | null> {
    const { data: result, error } = await supabase
      .from('proposals')
      .insert({
        job_id: data.jobId,
        contractor_name: data.contractorName,
        contractor_phone: data.contractorPhone,
        contractor_license: data.contractorLicense,
        client_name: data.clientName,
        client_address: data.clientAddress,
        scope: data.scope,
        tasks: data.tasks,
        materials: data.materials,
        equipment: data.equipment,
        labor_hours: data.laborHours,
        labor_rate: data.laborRate,
        labor_cost: data.laborCost,
        materials_cost: data.materialsCost,
        total_cost: data.totalCost,
        status: 'pending'
      })
      .select('uuid')
      .single();

    if (error) return null;
    return { uuid: result.uuid };
  }

  async getByUuid(uuid: string): Promise<any | null> {
    const { data, error } = await supabase
      .from('proposals')
      .select('*')
      .eq('uuid', uuid)
      .single();

    if (error || !data) return null;
    if (data.status === 'revoked') return null;
    if (new Date(data.expires_at) < new Date()) return { ...data, expired: true };
    return data;
  }

  async respond(uuid: string, action: 'approve' | 'decline', clientName: string, notes?: string): Promise<boolean> {
    const proposal = await this.getByUuid(uuid);
    if (!proposal || proposal.expired) return false;

    // Verify client name matches (case-insensitive)
    if (proposal.client_name.toLowerCase().trim() !== clientName.toLowerCase().trim()) {
      return false;
    }

    const { error } = await supabase
      .from('proposals')
      .update({
        status: action === 'approve' ? 'approved' : 'declined',
        client_response: action,
        client_notes: notes || null,
        updated_at: new Date().toISOString()
      })
      .eq('uuid', uuid);

    return !error;
  }

  async revoke(uuid: string): Promise<boolean> {
    const { error } = await supabase
      .from('proposals')
      .update({ status: 'revoked', updated_at: new Date().toISOString() })
      .eq('uuid', uuid);
    return !error;
  }
}

export const proposalService = new ProposalService();
```

- [ ] **Step 3: Create proposal HTML template**

Create `backend/src/templates/proposal.html` — a clean, professional, mobile-responsive HTML page that renders proposal data server-side. Includes [APPROVE] and [REQUEST CHANGES] buttons that POST to `/p/:uuid/respond`. Client must enter their name before approving.

- [ ] **Step 4: Add proposal routes to api.ts**

Add these routes:
- `GET /p/:uuid` — renders proposal page (public, no auth)
- `POST /p/:uuid/respond` — client approves/declines (rate limited, name verification)
- `POST /api/proposals` — contractor creates proposal (authenticated)
- `POST /api/proposals/:uuid/revoke` — contractor revokes (authenticated)

- [ ] **Step 5: Verify backend compiles**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck 2>&1 | grep "error TS" | wc -l`

- [ ] **Step 6: Commit**

```bash
git add supabase/migrations/005_proposals.sql backend/src/proposals.ts backend/src/templates/proposal.html backend/src/api.ts backend/src/types.ts
git commit -m "feat: add proposal system — Supabase table, service, HTML template, API routes

Client-facing web link for proposals with approve/decline buttons.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 12: Backend — Invoice Link Endpoints

**Files:**
- Create: `supabase/migrations/006_invoice_links.sql`
- Create: `backend/src/invoiceLinks.ts`
- Create: `backend/src/templates/invoice.html`
- Modify: `backend/src/api.ts`

- [ ] **Step 1: Create invoice_links migration**

```sql
CREATE TABLE IF NOT EXISTS invoice_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id TEXT NOT NULL,
    uuid TEXT UNIQUE NOT NULL DEFAULT gen_random_uuid()::text,
    contractor_name TEXT,
    contractor_phone TEXT,
    contractor_license TEXT,
    client_name TEXT,
    client_address TEXT,
    work_summary TEXT,
    hours_worked DECIMAL DEFAULT 0,
    hourly_rate DECIMAL DEFAULT 0,
    labor_cost DECIMAL DEFAULT 0,
    materials JSONB DEFAULT '[]',
    materials_cost DECIMAL DEFAULT 0,
    total_due DECIMAL DEFAULT 0,
    payment_info TEXT,
    status TEXT DEFAULT 'unpaid',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_invoice_links_uuid ON invoice_links(uuid);
```

- [ ] **Step 2: Create invoiceLinks.ts service**

Similar structure to proposals.ts — CRUD for invoice links, `getByUuid` for public rendering.

- [ ] **Step 3: Create invoice HTML template**

Professional invoice page — contractor info, work completed, hours, materials, total due, payment instructions.

- [ ] **Step 4: Add invoice link routes to api.ts**

- `GET /i/:uuid` — renders invoice page (public)
- `POST /api/invoice-links` — contractor creates invoice link (authenticated)

- [ ] **Step 5: Verify backend compiles**

- [ ] **Step 6: Commit**

```bash
git add supabase/migrations/006_invoice_links.sql backend/src/invoiceLinks.ts backend/src/templates/invoice.html backend/src/api.ts
git commit -m "feat: add invoice link system — shareable web page for client invoices

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 13: Backend — BLS Wage Data Integration

**Files:**
- Create: `supabase/migrations/007_wage_data.sql`
- Create: `backend/src/wageData.ts`
- Modify: `backend/src/api.ts`

- [ ] **Step 1: Create wage data tables**

```sql
CREATE TABLE IF NOT EXISTS wage_data (
    id SERIAL PRIMARY KEY,
    metro_area_code TEXT NOT NULL,
    metro_area_name TEXT NOT NULL,
    soc_code TEXT NOT NULL,
    occupation_title TEXT NOT NULL,
    median_hourly DECIMAL,
    mean_hourly DECIMAL,
    p25_hourly DECIMAL,
    p75_hourly DECIMAL,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(metro_area_code, soc_code)
);

CREATE TABLE IF NOT EXISTS zip_metro_map (
    zip_code TEXT PRIMARY KEY,
    cbsa_code TEXT,
    metro_name TEXT
);

CREATE INDEX idx_wage_data_soc ON wage_data(soc_code);
CREATE INDEX idx_zip_metro ON zip_metro_map(zip_code);
```

- [ ] **Step 2: Create wageData.ts service**

```typescript
import { supabase } from './supabase';

export class WageDataService {
  async getWageByZipAndTrade(zipCode: string, socCode: string): Promise<{
    metroName: string;
    medianHourly: number;
    p25Hourly: number;
    p75Hourly: number;
  } | null> {
    // Look up metro area from zip
    const { data: zipData } = await supabase
      .from('zip_metro_map')
      .select('cbsa_code, metro_name')
      .eq('zip_code', zipCode)
      .single();

    if (!zipData) return null;

    // Look up wage data
    const { data: wageData } = await supabase
      .from('wage_data')
      .select('*')
      .eq('metro_area_code', zipData.cbsa_code)
      .eq('soc_code', socCode)
      .single();

    if (!wageData) return null;

    return {
      metroName: zipData.metro_name,
      medianHourly: Number(wageData.median_hourly),
      p25Hourly: Number(wageData.p25_hourly),
      p75Hourly: Number(wageData.p75_hourly)
    };
  }
}

export const wageDataService = new WageDataService();
```

- [ ] **Step 3: Add wage data route to api.ts**

- `GET /api/wages?zip=78701&soc=47-2111` — returns wage data for zip/trade (authenticated)

- [ ] **Step 4: Verify backend compiles**

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations/007_wage_data.sql backend/src/wageData.ts backend/src/api.ts
git commit -m "feat: add BLS wage data integration — zip-to-metro lookup, wage query endpoint

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 14: Wire BLS Wage Suggestion to Onboarding

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/data/WageService.kt`
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/OnboardingScreen.kt`

- [ ] **Step 1: Create WageService**

```kotlin
package com.guildofsmiths.trademesh.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object WageService {
    private val client = OkHttpClient()
    private val baseUrl = "http://10.0.2.2:3001"

    suspend fun getWageSuggestion(zipCode: String, socCode: String): WageSuggestion? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/wages?zip=$zipCode&soc=$socCode")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                WageSuggestion(
                    metroName = json.getString("metroName"),
                    lowRate = json.getDouble("p25Hourly"),
                    highRate = json.getDouble("p75Hourly"),
                    medianRate = json.getDouble("medianHourly")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class WageSuggestion(
    val metroName: String,
    val lowRate: Double,
    val highRate: Double,
    val medianRate: Double
)
```

- [ ] **Step 2: Wire to onboarding About You screen**

In the onboarding "About You" screen, when the user enters a zip code (and their trade is already set from screen 1), call `WageService.getWageSuggestion()`. If it returns data, show hint text below the hourly rate field:

"Electricians in Austin, TX typically charge $28-42/hr"

Pre-fill the rate field with the median. The user can change it.

- [ ] **Step 3: Verify compilation and commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/data/WageService.kt android/app/src/main/java/com/guildofsmiths/trademesh/ui/OnboardingScreen.kt
git commit -m "feat: wire BLS wage suggestion to onboarding hourly rate field

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 15: Add Proposal Response Polling to Dashboard

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/dashboard/DashboardViewModel.kt`

- [ ] **Step 1: Add proposal status check to loadJobs**

When `loadJobs` is called, also check for proposals with `status = 'approved'` or `status = 'declined'` that the contractor hasn't seen yet. Add these as alerts:

- "Garcia proposal — APPROVED by client!" (AlertType.CLIENT_RESPONSE)
- "Thompson proposal — client requested changes" (AlertType.CLIENT_RESPONSE)

For now, use the existing backend to poll proposal statuses. Push notifications are a future enhancement.

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/dashboard/DashboardViewModel.kt
git commit -m "feat: add proposal response polling to dashboard alerts

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 16: Integration Verification

- [ ] **Step 1: Full Android compilation check**

```bash
cd /Users/fegensprenelon/smith-net/android && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo $JAVA_HOME) ./gradlew compileDebugKotlin 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Full backend compilation check**

```bash
cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck 2>&1 | grep "error TS" | wc -l
```
Expected: Same or fewer errors than before

- [ ] **Step 3: Verify no orphaned imports**

```bash
grep -rn "BottomNavBar\|MAIN_ROUTES\|NavTab\|NavRoutes.PLAN[^_]" /Users/fegensprenelon/smith-net/android/app/src/ --include="*.kt" | grep -v "node_modules\|\.gradle" | head -10
```
Expected: No results

- [ ] **Step 4: Install and test on emulator**

```bash
cd /Users/fegensprenelon/smith-net/android && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo $JAVA_HOME) ./gradlew installDebug 2>&1 | tail -5
```

- [ ] **Step 5: Milestone commit**

```bash
git add -A
git commit -m "milestone: Solo Contractor Foundation complete — dashboard, pipeline, guided flow, proposals, invoices

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Summary

| Task | What It Does |
|------|-------------|
| 1 | Add JobStage enum and new fields to Job |
| 2 | Update Navigation routes (add dashboard, pipeline, newjob) |
| 3 | Add hourly rate, license, payment to UserPreferences |
| 4 | Create trade-specific defaults (tasks, equipment, materials) |
| 5 | Simplify onboarding to 3 screens |
| 6 | Create Today-First Dashboard screen |
| 7 | Create Job Pipeline Detail screen with stage bar |
| 8 | Create 4-step guided New Job flow |
| 9 | Rewrite MainActivity navigation (revert tabs, wire dashboard) |
| 10 | Add stage transitions to JobBoardViewModel |
| 11 | Backend: Proposal system (table, service, web page, API) |
| 12 | Backend: Invoice link system (table, service, web page, API) |
| 13 | Backend: BLS wage data integration |
| 14 | Wire BLS wage suggestion to onboarding |
| 15 | Add proposal response polling to dashboard |
| 16 | Integration verification |
