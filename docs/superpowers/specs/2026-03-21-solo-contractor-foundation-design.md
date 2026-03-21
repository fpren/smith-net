# Solo Contractor Foundation — Design Spec

**Date:** 2026-03-21
**Status:** Draft
**Scope:** End-to-end solo contractor workflow — auth/onboarding fix, dashboard navigation, job lifecycle pipeline, client-facing proposal/invoice links, BLS wage integration, trade-specific defaults

---

## Problem Statement

The current app navigation is built around messaging and plan containers. The real user — a solo contractor — thinks in terms of jobs: get the lead, write the proposal, do the work, send the invoice, get paid. The app needs to mirror that lifecycle end-to-end.

Additionally: auth/onboarding flow is broken (onboarding shows before login), the bottom tab bar doesn't match the product vision (user prefers dashboard hub), and there's no client-facing way to share proposals or invoices.

## Decisions Made During Brainstorming

- Auth screen always first, onboarding after (3 screens max)
- Today-First dashboard as home screen (not tabs, not sidebar)
- Pipeline view is per-job (tap into a job to see its lifecycle)
- Guided 4-step new job flow with trade-specific suggestions
- Proposal = scope + materials + labor + price in one document
- Client sees proposals/invoices via shareable web link (no app needed)
- Client can Approve or Request Changes on proposal link
- Manual clock-in/out now, geofence later
- Closeout warns about unchecked tasks but doesn't block
- BLS OEWS data for labor rate suggestions by metro area
- Manual material pricing with curated trade-specific default lists
- Dispatcher/foreman layer is a separate spec (Spec 2)

---

## Part A: Auth & Onboarding

### Flow

```
App Launch → Auth Screen (login/register via Supabase)
  → Auth Success → Check onboarding status
    → Not complete → Onboarding
    → Already complete → Dashboard
```

Auth screen is ALWAYS first. No exceptions.

### Onboarding (3 screens)

**Screen 1 — Your Trade**
- Occupation picker: Electrician, Plumber, HVAC, Carpenter, General Contractor, Other
- Experience level: Apprentice, Journeyman, Master, 10+ years

**Screen 2 — About You**
- Name (pre-filled from auth if available)
- Business name (optional)
- Address (street, city, state, zip)
- Hourly rate — suggested from BLS data based on zip + trade: "Electricians in your area typically charge $28-42/hr"
- License number (optional)

**Screen 3 — Done**
- Welcome message
- "Go to Dashboard" button

Language selection moves to Settings (not onboarding). Onboarding state stored in Supabase user metadata (not just SharedPreferences) so it survives reinstalls.

---

## Part B: Dashboard (Today-First Home Screen)

The contractor's daily driver. Everything is accessed from here.

### Layout

**Header:** Business name or user name. Unread message count.

**Needs Attention (top section):**
- Proposals awaiting client response
- Jobs with tasks due today
- Invoices unpaid past due
- New client approvals received

**Today's Jobs:**
- Jobs scheduled for today
- Each shows: client name, address, stage icon, time logged today
- Tap → Job Pipeline Detail

**Quick Actions:**
- [+ NEW JOB] — starts guided flow
- [CLOCK IN] — shows active jobs to clock into
- [MESSAGES] — messaging hub

**Stats Bar:**
- Active jobs count
- Outstanding invoices total ($)
- Hours this week

### Navigation from Dashboard

```
DASHBOARD (home)
  ├── Job Pipeline Detail (tap any job)
  ├── New Job (guided flow)
  ├── Messages (beacon/channel/conversation)
  ├── Settings (profile, trade, preferences)
  └── Archive (closed jobs)
```

No bottom tab bar. No sidebar. Back button on every sub-screen returns up the hierarchy.

---

## Part C: Job Lifecycle Pipeline

### Stages

```
LEAD → PROPOSAL → APPROVED → IN_PROGRESS → REVIEW → INVOICE → CLOSED
```

| Stage | What happened | What's next | Actions available |
|-------|--------------|-------------|-------------------|
| LEAD | Client info + scope entered | Create proposal | [CREATE PROPOSAL] |
| PROPOSAL | Proposal generated, shared | Waiting for client | [SHARE WITH CLIENT] |
| APPROVED | Client approved via link | Start work | [START WORK] |
| IN_PROGRESS | Clocking time, logging materials | Finish tasks | [CLOCK IN] [ADD PHOTO] [LOG MATERIALS] |
| REVIEW | Work done, walkthrough | Mark complete | [MARK COMPLETE] (warns about unchecked tasks) |
| INVOICE | Invoice generated, shared | Waiting for payment | [GENERATE INVOICE] [SHARE INVOICE] |
| CLOSED | Paid or archived | Done | [VIEW REPORT] [ARCHIVE] |

### Job Pipeline Detail Screen

When contractor taps a job from dashboard:

- **Stage indicator** — visual bar showing current stage
- **Client info** — name, phone (tap to call), address (tap to navigate)
- **Scope** — description from intake
- **Tasks** — checklist, check off as completed
- **Materials** — list with quantities, costs, checkbox as purchased/used
- **Equipment** — what's needed on-site
- **Crew** — size and members
- **Time log** — clock-in/out entries
- **Photos** — on-site photos attached to job
- **Price breakdown** — labor (hours × rate) + materials + total
- **Stage-specific action buttons** — only relevant actions for current stage

### Data Model Changes

**New `JobStage` enum** (replaces `JobStatus`):
```
LEAD, PROPOSAL, APPROVED, IN_PROGRESS, REVIEW, INVOICE, CLOSED
```

**New fields on Job:**
- `clientPhone: String` — phone number
- `clientAddress: String` — job site address
- `proposalId: String?` — links to shareable proposal
- `invoiceId: String?` — links to shareable invoice
- `hourlyRate: Double` — from contractor profile
- `photos: List<String>` — on-site photo URIs
- `stage: JobStage` — replaces `status`

**Existing fields retained:** title, description, materials, crew, crewSize, toolsNeeded, workLog, estimatedStartDate, estimatedEndDate, createdBy, createdAt, updatedAt.

---

## Part D: Guided New Job Flow

4-step flow triggered by [+ NEW JOB] from dashboard. Each step is its own screen with back button and progress indicator. Can be exited at any step — partial data saved as draft.

### Step 1 — Client
- Client name (required)
- Phone number
- Job site address
- Referral source (optional)

### Step 2 — Scope
- "Describe the work" — text field
- Add photos — camera or gallery
- What they see on-site or what client described

### Step 3 — What's Needed
- **Tasks** — dynamic add/remove list. Trade-specific suggestions available (e.g., electrician sees "Install outlet", "Replace panel", etc.)
- **Equipment** — dynamic list with trade-specific suggestions
- **Materials/Supplies** — dynamic list with quantity and price per item. Trade-specific suggestions. Manual pricing.
- **Crew size** — stepper (+/-)

### Step 4 — Timeline & Price
- Estimated start date
- Estimated duration (days)
- Labor cost — auto-calculated: estimated hours × hourly rate (from profile). Editable.
- Materials cost — auto-summed from Step 3. Editable.
- Total — labor + materials
- Marked as "ESTIMATE" — disclaimer included

After Step 4: Job created in LEAD stage. Contractor can immediately [CREATE PROPOSAL] or save and return later.

---

## Part E: Client-Facing Web Links

### Proposal Link

Generated when contractor taps [SHARE WITH CLIENT] on a LEAD or PROPOSAL stage job. Creates a unique URL: `app.guildofsmiths.com/p/{uuid}`

**Page shows:**
- Contractor business name, phone, license #
- Client name, job site address
- Scope of work
- Task breakdown
- Materials list with quantities and prices
- Labor estimate (hours × rate)
- Total estimate
- "ESTIMATE — prices may vary" disclaimer
- **[APPROVE]** button — moves job to APPROVED, notifies contractor
- **[REQUEST CHANGES]** button — text field for client notes, notifies contractor

### Invoice Link

Generated when contractor taps [SHARE INVOICE] on an INVOICE stage job. URL: `app.guildofsmiths.com/i/{uuid}`

**Page shows:**
- Contractor business name, phone, license #
- Client name, job site address
- Work completed summary
- Actual hours worked (from time tracking)
- Actual materials used (checked off during job)
- Total due
- Payment instructions (from contractor profile — Zelle, check, Venmo, etc.)

### Backend

- New table: `proposals(id, job_id, uuid, data_json, status, client_response, created_at)`
- New table: `invoices(id, job_id, uuid, data_json, status, created_at)`
- New endpoints: `GET /p/:uuid` (render proposal page), `POST /p/:uuid/respond` (client approve/decline)
- New endpoints: `GET /i/:uuid` (render invoice page)
- Proposal/invoice data is a JSON snapshot of the job at the time of generation

---

## Part F: Trade-Specific Defaults

Static data per trade, baked into the app. Not AI-generated — curated lists the contractor picks from or ignores.

### Structure

One data object per trade containing:
- `commonTasks: List<String>` — typical task descriptions
- `commonEquipment: List<String>` — typical tools needed
- `commonMaterials: List<MaterialDefault>` — name + typical unit + typical price
- `socCode: String` — BLS SOC code for wage lookup

### Trades Supported (Phase 1)

- Electrician (SOC 47-2111)
- Plumber (SOC 47-2152)
- HVAC (SOC 49-9021)
- Carpenter (SOC 47-2031)
- General Contractor (SOC 47-1011)

### Example — Electrician

```
commonTasks: ["Install outlet", "Replace breaker panel", "Run new circuit",
              "Install light fixture", "Troubleshoot", "Install ceiling fan",
              "Upgrade service entrance", "Install GFCI"]

commonEquipment: ["Multimeter", "Wire strippers", "Conduit bender",
                  "Fish tape", "Voltage tester", "Drill", "Level",
                  "Cable puller", "Knockout punch"]

commonMaterials: [
  {name: "12/2 Romex", unit: "ft", typicalPrice: 0.65},
  {name: "14/2 Romex", unit: "ft", typicalPrice: 0.45},
  {name: "20A Breaker", unit: "ea", typicalPrice: 8.50},
  {name: "15A Breaker", unit: "ea", typicalPrice: 7.00},
  {name: "Junction Box", unit: "ea", typicalPrice: 2.50},
  {name: "Duplex Outlet", unit: "ea", typicalPrice: 1.50},
  {name: "Light Switch", unit: "ea", typicalPrice: 2.00},
  {name: "3/4\" EMT Conduit", unit: "10ft", typicalPrice: 4.50},
  {name: "Wire Nuts (bag)", unit: "bag", typicalPrice: 5.00}
]
```

---

## Part G: BLS Wage Data Integration

### Data Source

BLS Occupational Employment and Wage Statistics (OEWS). Free API, 500 queries/day with free key.

### Loading

- Download BLS OEWS bulk flat files (CSV) for all metro areas
- Load into Supabase table: `wage_data(metro_area_code, metro_area_name, soc_code, occupation_title, median_hourly, mean_hourly, p25_hourly, p75_hourly, updated_at)`
- ~530 metro areas × 5 trades = ~2,650 rows. Small dataset.

### Usage

- During onboarding (Step 2 — About You): app sends zip code + SOC code to backend
- Backend maps zip to closest metro area, returns wage stats
- App suggests: "Electricians in Austin, TX typically charge $28-42/hr. What's your rate?"
- Contractor accepts suggestion or enters their own rate
- Rate stored in profile, used for labor auto-calc on every job

### Zip-to-Metro Mapping

Use the HUD USPS ZIP Code Crosswalk file (free, from huduser.gov). Maps every US zip code to its CBSA (Core Based Statistical Area) code, which is the same metro area code BLS uses. Load this into Supabase as a lookup table: `zip_metro_map(zip_code, cbsa_code, metro_name)`.

### Updates

- Backend refreshes from BLS annually (data published each March for prior May)
- No real-time API calls from the app — data is cached in Supabase

---

## Part H: Migration & Compatibility

### Existing Users

Users who completed the old 4-screen onboarding are treated as complete — they skip onboarding entirely. No re-onboarding. If they're missing new fields (hourly rate, license #), those show as empty in Settings and can be filled in later.

### Onboarding State in Supabase

Store onboarding status in Supabase `auth.users.user_metadata` via the existing Supabase client's `updateUser()` method. No new backend endpoint needed — Supabase handles this natively.

On first login after update: if SharedPreferences says onboarding is complete, write that status to Supabase user_metadata. SharedPreferences remains the fast local cache; Supabase is the source of truth on reinstall.

### JobStatus → JobStage Migration

| Old Status | New Stage | Notes |
|-----------|-----------|-------|
| BACKLOG | LEAD | Unstarted jobs become leads |
| TODO | LEAD | Same — hasn't been proposed yet |
| IN_PROGRESS | IN_PROGRESS | Direct mapping |
| REVIEW | REVIEW | Direct mapping |
| DONE | CLOSED | Completed jobs are closed |
| ARCHIVED | CLOSED + isArchived=true | Archive is a flag, not a stage |

`CLOSED` is the final stage. `isArchived: Boolean` remains as a separate flag for hiding closed jobs from the active view. Archiving a job = set `isArchived = true` on a CLOSED job.

Both `JobStatus` (old) and `JobStage` (new) coexist during migration. `JobBoardViewModel` maps old to new on read.

### Job Field Reconciliation

| Field | Current | Action |
|-------|---------|--------|
| `location: String?` | Exists | Rename to `clientAddress` |
| `clientName: String?` | Exists | Keep |
| `toolsNeeded: String` | Free text | Keep for backward compat, add `equipmentList: List<String>` |
| `expenses: String` | Free text | Keep for backward compat, `materials` list is primary |
| `status: JobStatus` | Exists | Add `stage: JobStage`, derive from `status` for old jobs |

### Experience Level

Keep the existing enum: `APPRENTICE, JOURNEYMAN, MASTER, CONTRACTOR, NOT_APPLICABLE`. Drop "10+ years" — experience is certification tier, not duration. `CONTRACTOR` stays for General Contractor users.

### Relationship to Core Flow Redesign

The Intent/Synthesizer/Ledger container architecture (designed 2026-03-20) operates at the backend data layer. This spec focuses on the Android UI/UX for the solo contractor workflow. The two are complementary:
- Intent = the proposal concept in this spec (scope declaration)
- Synthesizer = generates the invoice/report from job data
- Ledger = seals the final artifact

The container architecture will be layered in via Spec 2 (Dispatcher Layer) or a future integration spec. For now, the solo contractor UI talks directly to jobs/proposals/invoices without going through the container abstraction.

---

## Part I: Proposal Security & Lifecycle

### Proposal Links

- Links expire after 30 days by default. Contractor can regenerate.
- `POST /p/:uuid/respond` is rate-limited to 5 requests per hour per UUID.
- Client must enter their name to confirm identity before approving (compared against client name on the proposal).
- Contractor can revoke a proposal link from the app (sets status to `revoked`).

### Request Changes Flow

When client taps [REQUEST CHANGES]:
1. Client enters notes in a text field, submits
2. Contractor receives in-app notification with the notes
3. Job stays in PROPOSAL stage (does not move backward)
4. Contractor edits the job details, then taps [REGENERATE PROPOSAL]
5. New proposal snapshot created with a new UUID
6. Old link shows "This proposal has been updated" with link to new one
7. Contractor shares new link with client

Valid stage transitions:
```
LEAD → PROPOSAL → APPROVED → IN_PROGRESS → REVIEW → INVOICE → CLOSED
                ↺ (revision cycle: client requests changes, contractor regenerates)
```

No backward transitions. Revision happens in-place at PROPOSAL stage.

---

## Part J: What Gets Removed

- `BottomNavBar.kt` — delete
- `LeftSidebar.kt` — already removed from PlanScreen
- Bottom tab bar logic in `MainActivity.kt` — revert Scaffold wrapper
- `NavTab` enum and `MAIN_ROUTES` — delete
- `PlanScreen.kt` as standalone navigation destination — proposal flow is now inside job pipeline
- `SOLO_DASHBOARD` route — replaced by new dashboard
- Old Plan routes from Navigation.kt that are no longer relevant

## Part K: What Gets Reused

- `JobBoardViewModel` — job CRUD, backend sync, task management, invoice generation
- `JobBoardTypes` — Job, Task, Material, CrewMember, WorkLogEntry data classes (with stage field update)
- `TimeTrackingScreen` / time tracking logic — accessed from within a job
- `InvoiceGenerator` / `InvoicePreviewDialog` — triggered from job pipeline
- All messaging screens (BeaconList, ChannelList, Conversation)
- `ConsoleTheme` — monospace terminal aesthetic stays
- Backend: api.ts, types.ts, all existing endpoints
- Supabase auth, database, all existing tables

---

## Testing

- Auth flow: login → onboarding → dashboard (no onboarding bypass)
- Onboarding state persists across app restart and reinstall (Supabase-backed)
- New job guided flow creates job in LEAD stage with all fields
- Trade suggestions appear based on user's trade selection
- BLS wage suggestion appears during onboarding based on zip + trade
- Proposal link renders correctly, client can approve/decline
- Approval notification reaches contractor, job moves to APPROVED
- Clock-in/out creates time entries linked to job
- Material checkout updates job materials
- Closeout with unchecked tasks shows warning but allows proceed
- Invoice generates from actual hours + materials
- Invoice link renders correctly
- Dashboard shows today's jobs, needs attention items, correct stats
- Back button navigation works from every sub-screen to dashboard
- Offline deferred: offline job creation, time tracking, and material logging are desirable but require a Room-based local storage layer with sync queue. This is out of scope for this spec — a separate offline-sync spec will cover it. For now, the app requires network for job creation and sync, but time tracking entries are buffered locally via existing SharedPreferences patterns
