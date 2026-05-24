# Smith Net — Flow Tree (Mobbin-style)

**Method:** every screen reachable from app launch, indented per nesting level. Bracketed labels = transition triggers. `[ROLE GATE]` and `[TIER GATE]` mark hidden / locked branches.

**Notation:**
- `→` = forward navigation
- `↩` = back navigation
- `⤴` = global overlay (returns to caller)
- `⊕` = appears as section / module within parent screen, not a separate route
- `[Open]`, `[Solo]`, `[Adv]`, `[Ent]` = minimum tier
- `[Solo Hidden]` etc = role gate hides

---

## Root flow tree

```
APP LAUNCH (cold start)
└── A1 AuthScreen
    ├── [register or login] → A3 WelcomeScreen
    │   └── A2 OnboardingScreen
    │       ├── name + role + trade picker
    │       └── [complete] → A4 WelcomeToOpenScreen [NET-NEW]
    │           ├── [Start Solo Trial] → N7 (trial flow) → C1 Dashboard
    │           └── [Stay on Open] → C1 Dashboard
    │
    └── [resume / already-authed] → C1 Dashboard
```

---

## Main navigation tree (rooted at C1 Dashboard)

```
C1 DASHBOARD  (role-resolved modules + tier-aware quick actions)
│
├── ⊕ HEADER (business name or team name; status pills)
├── ⊕ TRIAL BANNER N1 [if trial active]
├── ⊕ MODULES (resolved by role via DashboardModuleResolver)
│   ├── On-clock pill / time-tracking widget
│   ├── Latest unread message preview (filtered: Solo hides crew DMs)
│   ├── Beacons in range (mesh)
│   ├── Active jobs list
│   └── Quick actions grid (4-tile)
│       ├── REPORTS → J1 ReportScreen
│       ├── SUPPLY → P1 SupplyScreen
│       ├── ARCHIVE → R1 ArchiveScreen
│       ├── CLIENTS → M1 ClientsScreen
│       ├── DISPATCH → O1 DispatchScreen [Solo Hidden]
│       ├── EXPENSES → G1 ExpensesScreen
│       ├── MAP → N1' MapScreen
│       ├── COMM → K1 ChatListScreen
│       ├── JOBBOARD → D1 JobBoardScreen
│       ├── CLOCK IN → F1 TimeTrackingScreen
│       └── UPGRADE / ADD SMITHAI / ADD CREW → N7 [tier-aware label, NET-NEW]
│
├── ⊕ BOTTOM TOOLBAR (5 primary tabs)
│   ├── DASHBOARD (current)
│   ├── JOB BOARD → D1
│   ├── PLAN → E1 PlanScreen
│   ├── COMM → K1 ChatListScreen
│   └── SETTINGS → Q2 SettingsScreen
│
├── [tap a job in active list] → D2 JobPipelineScreen
├── [tap "+ NEW JOB"] → D3 NewJobFlow
├── [tap a beacon] → L1 BeaconListScreen → L3 PeersScreen
├── [tap profile avatar in header] → Q1 ProfileScreen
└── [tap settings gear] → Q2 SettingsScreen
```

---

## D — Jobs branch

```
D1 JOB BOARD SCREEN
├── [tap job tile] → D2 JobPipelineScreen
│   ├── ⊕ JobStageBar (backlog → todo → in_progress → review → done → archived)
│   ├── [edit stage] → mutates job via JobBoardViewModel
│   ├── [tap a task] → (in-place edit, modal not yet)
│   ├── [tap "+ NEW TASK"] → in-line task add
│   ├── [tap "Add Time"] → F1 TimeTrackingScreen
│   ├── [tap "Add Expense"] → G2 JobExpenseDetailScreen
│   ├── [tap "Generate Invoice"] → H1 InvoiceScreen
│   ├── [tap "Generate Proposal"] → I1 ProposalPreviewDialog
│   ├── [tap "Generate Report"] → J1 ReportScreen
│   ├── [tap "Compile Plan"] → E1 PlanScreen [TIER GATE: Free → N2 overlay]
│   ├── [tap "Close Job"] → confirms; mutates state
│   └── ↩ [back] D1
│
├── [tap "+ NEW JOB"] → D3 NewJobFlow
│   ├── step 1: title
│   ├── step 2: client (M2 ClientDetailScreen lookup or create new)
│   ├── step 3: trade picker (TradePickerField)
│   ├── step 4: location / due date
│   ├── [Save] →
│   │   ├── if Open AND already 1 active job: ⤴ N4 active-job cap overlay
│   │   │   ├── [TRY SOLO FREE — NO CC] → N7 → trial flow → ↩ retry Save
│   │   │   └── [See active job] → D2 of existing job
│   │   └── else: ↩ D2 of new job
│   └── ↩ [back] D1
│
├── [filter by stage] → D1 (re-renders)
├── [search jobs] → D1 (re-renders)
└── ↩ [back] C1 Dashboard
```

---

## E — PLAN / Intent branch (the moat)

```
E1 PLAN SCREEN
├── [Free user open] → ⤴ N3 PLAN Compiler preview overlay [TIER GATE]
│   ├── dimmed live preview of user's first job-as-plan behind the overlay
│   ├── [TRY SOLO FREE 14 DAYS — NO CC] → N7 → trial flow → ↩ unlocks E1
│   └── [Maybe later] → ↩ C1 Dashboard
│
├── [Solo+ user open] → existing PLAN UI:
│   ├── ⊕ Engagements list (top of funnel)
│   │   └── [tap engagement] → engagement detail → [Convert to Intent] → IntentVersion v1 draft
│   │
│   ├── ⊕ Active Intents list (with status chips: draft / proposed / confirmed)
│   │   └── [tap Intent] → Intent detail
│   │       ├── ⊕ Scope statement (editable in draft)
│   │       ├── ⊕ Parties list
│   │       ├── ⊕ Intended jobs (linked)
│   │       ├── [Propose] → status → proposed
│   │       ├── [Confirm] → status → confirmed (must be a party)
│   │       ├── [New version] → IntentVersion v2 supersedes v1
│   │       └── [Synthesize] [Solo+, requires confirmed Intent + ≥1 closed Job + ≥1 closed TimeEntry]
│   │           └── synthesizer call → SummaryArtifact
│   │               └── [Seal in Ledger] → ledger.seal() → LedgerEntry
│   │                   ├── [Generate Invoice from sealed artifact] → H1
│   │                   ├── [Generate Report from sealed artifact] → J1
│   │                   └── [Public proposal page link copy] → /p/:uuid (web)
│   │
│   └── ⊕ Sealed Ledger entries (audit trail — read-only)
│       └── [tap entry] → Ledger entry detail (hash, supersession chain, verify button)
│           └── [Verify hash] → calls /api/ledger/verify/:entryId
│
├── [Adv+ user — AI-assisted Intent draft] → ProposalAssist subflow → drafts new IntentVersion (auto_generated=true)
│   └── then Propose / Confirm as normal
│
└── ↩ [back] C1 Dashboard
```

---

## H — Invoice branch

```
H1 INVOICE SCREEN  (entry from D2 JobPipeline / E1 PLAN sealed artifact / R1 ArchiveScreen)
├── ⊕ Invoice draft (line items, tax, total)
├── ⊕ Template selector
│   ├── Standard [All tiers]
│   ├── Advanced [Adv+, otherwise locked row → tap → ⤴ overlay → N7]
│   └── Enterprise [Ent only, otherwise locked row → tap → ⤴ overlay → N7]
│
├── [Edit line items] → in-place
├── [Add Materials] → links to materials of underlying job
├── [Set due date] → date picker
│
├── [Preview] → G4 InvoicePreviewBottomSheet
│   └── PDF preview + Send action
│
├── [Send] →
│   ├── if Open AND ≥5 PDFs sent this month: ⤴ N5 PDF cap overlay
│   │   ├── [TRY SOLO FREE — NO CC] → N7
│   │   └── [Next month: in X days] → server queues; Toast confirms
│   ├── else: server renders PDF (with N9 branding stamp if Open) + emails
│       └── Toast: "Invoice sent · X of 5 free sends used this month" (if Open)
│
└── ↩ [back] D2 / E1 / R1
```

---

## K — Comms branch

```
K1 CHAT LIST SCREEN  (all conversations: DMs + channels + ephemeral)
├── ⊕ Filter chips: ALL / CHANNELS / DMS / EPHEMERAL / UNREAD
├── ⊕ Search
├── ⊕ Status pill: connection (mesh / online / gateway / offline)
│
├── [tap a chat] → K4 ConversationScreen
│   ├── ⊕ Message thread (with VectorClock-ordered messages)
│   ├── ⊕ Origin badge per message: online / mesh / gateway
│   ├── [send message] → BoundaryEngine routes (mesh / online / gateway)
│   ├── [attach media] → multer upload → message with media
│   ├── [voice note] → S1 MediaPlayer for review → send
│   ├── [delete message] → soft-delete + WS event
│   ├── [channel info / settings] → expand panel
│   │   ├── ⊕ KEEP HISTORY toggle (sets ChannelPersistence)
│   │   ├── ⊕ Delete + archive
│   │   └── ⊕ Clear messages on this device
│   └── ↩ [back] K1
│
├── [tap "+ New Conversation"] → K5 NewConversationScreen
│   ├── pick contacts / colleagues
│   └── [start] → K4 (DM)
│
├── [tap "+ New Channel"] → K6 CreateChannelScreen
│   ├── name, type (broadcast/group/dm/job), visibility
│   ├── [If restricted: requiresApproval toggle]
│   └── [create] → K4
│
├── [view all channels] → K2 ChannelListScreen / K3 ChannelsScreen [redundancy flagged]
│
└── ↩ [back] C1
```

---

## Q — Settings branch (heavily affected by Step 3)

```
Q2 SETTINGS SCREEN
├── ⊕ N1 TRIAL BANNER [if trial active]
│
├── ⊕ SUBSCRIPTION SECTION [NET-NEW, top of screen above PROFILE]
│   └── [tap row] → N8 Subscription Detail Screen
│       ├── ⊕ Current tier + price + cadence
│       ├── ⊕ Next bill + payment method
│       ├── ⊕ Founder pricing status
│       ├── ⊕ CHANGE TIER section (upgrade options / annual switch)
│       │   └── [tap any] → N7 pricing screen (scrolled to target tier)
│       ├── ⊕ PAYMENT METHOD
│       │   └── [Update card] → Stripe / Play Billing card-update flow
│       ├── ⊕ DATA section
│       │   ├── [Export my data] → /api/me/data/export → email link
│       │   ├── [Cancel subscription] → confirmation dialog (custom Composable)
│       │   │   ├── [KEEP SOLO] → ↩ N8
│       │   │   └── [Cancel anyway] → server cancel → Toast
│       │   └── [Delete account] → confirmation → 30-day cooling-off
│       └── ↩ [back] Q2
│
├── ⊕ PROFILE SECTION
│   └── [tap row] → Q1 ProfileScreen
│       ├── name + email + phone
│       ├── trade picker (TradePickerField)
│       ├── hourly_rate
│       └── ↩ Q2
│
├── ⊕ PRIVACY SECTION (existing)
├── ⊕ WORK MODE SECTION (existing)
├── ⊕ TRADE ROLE SECTION (existing)
│
├── ⊕ MESH CONNECTION SECTION [Solo Hidden, requires Permission.GATEWAY_RELAY]
│   └── ⊕ status dot + scan / connect actions
│
├── ⊕ AI ASSISTANT SECTION
│   ├── [Open or Solo tier] → N10 lock-state row
│   │   ├── tap row → ⤴ N10 overlay → N7
│   │   └── text-link "Try Advanced free 30 days — no CC"
│   └── [Adv+ tier] → existing AI section:
│       ├── Load button (LinearProgressIndicator on first load)
│       ├── Model state (SLEEPING / WAKING / ALIVE / RULE_BASED_FALLBACK)
│       ├── Battery gate indicator
│       └── ⊕ Free up space (if unloading)
│
└── ↩ [back] C1 Dashboard
```

---

## L / N' / O — Mesh / Map / Dispatch branches (role-gated)

```
L1 BEACON LIST SCREEN [All]
├── ⊕ Beacons currently in range
├── [tap beacon] → L3 PeersScreen [Foreman+ for full peer mgmt]
└── [tap "+ Create Beacon"] → L2 CreateBeaconScreen

N1' MAP SCREEN [All]
├── ⊕ Job locations
├── ⊕ Crew positions [Solo Hidden]
├── ⊕ LostAndFound markers
└── [tap "Lost & Found"] → N2' LostAndFoundScreen

O1 DISPATCH SCREEN [Solo Hidden, Foreman+]
├── ⊕ Crew assignment grid
├── ⊕ Job board view (denser than D1)
└── [drag job → crew member] → assigns
```

---

## M / G / J — Clients / Expenses / Reports branches

```
M1 CLIENTS SCREEN
└── [tap client] → M2 ClientDetailScreen
    ├── client info
    ├── linked jobs
    └── [+ New job for this client] → D3 NewJobFlow with prefilled client

G1 EXPENSES SCREEN  (cross-job expenses overview)
├── [tap a job's expenses] → G2 JobExpenseDetailScreen
│   ├── expense entries
│   ├── BOL legal terms inline (G5 settings)
│   ├── [Edit categories] → G3 CategoryManagerScreen
│   └── [Generate invoice from expenses] → G4 InvoicePreviewBottomSheet → H1
└── [Import CSV] → ExpenseCsvImport flow

J1 REPORT SCREEN
├── narrative content (manual or AI-drafted for Adv+)
├── [Render PDF] → server template render (with N9 branding stamp if Open)
└── [Share link] → /p/:uuid public page (re-uses proposal page mechanism)
```

---

## Global overlays (rendered above any screen)

| Overlay | When | Source |
|---|---|---|
| N1 Trial banner | trial active | global, sits below status bar |
| N2/N3 PLAN Compiler lock | Free user opens E1 PlanScreen or attempts compile | E1 |
| N4 Active-job cap | Free user attempts 2nd active job at D3 Save | D3 |
| N5 PDF send cap | Free user attempts 6th PDF send | H1 / G4 |
| N6 Founder seats counter | embedded in N2/N3/N4/N5/N10/N11 + N7 | inline |
| N7 Pricing screen | tap UPGRADE quick-action OR tap subscription row in Q2 OR follow CTA from any lock overlay | route |
| N8 Subscription detail | from Q2 SETTINGS → SUBSCRIPTION row | route |
| N9 PDF stamp | Open tier sends a PDF | server render |
| N10 AI lock state | Solo opens AI ASSISTANT section in Q2 | within Q2 |
| N11 Crew invite lock | Solo/Advanced taps Invite Colleague | from existing colleague-invite action |
| N12 Tier-gate Toast | post-overlay dismiss / repeat cap-attempt | global Toast |

---

## Cross-tier visibility matrix (which screens render differently per tier)

| Screen | Open | Solo | Advanced | Enterprise |
|---|---|---|---|---|
| C1 Dashboard | quick-action `UPGRADE` | quick-action `ADD SMITHAI` | quick-action `ADD CREW` | (no upgrade tile) |
| D3 NewJobFlow | Save fires N4 if 1 active | unlimited | unlimited | unlimited |
| E1 PlanScreen | N3 lock overlay | full PLAN UI | full + AI assist (ProposalAssist) | full + AI + crew shared |
| H1 InvoiceScreen | counter footer; N5 on 6th send; only Standard template | unlimited send; Standard only | unlimited; Standard + Advanced | unlimited; Standard + Advanced + Enterprise |
| Q2 Settings — SUBSCRIPTION | row shows `Open · $0/mo` | `Solo · $2.99/mo · Founder locked` | `Advanced · $9.99/mo` | `Enterprise · $50/mo` |
| Q2 Settings — AI ASSISTANT | N10 lock state | N10 lock state | full AI section (Load button, model state) | full + crew-aware |
| Crew invite action (Colleagues) | N11 (Open never invites) | N11 lock | N11 lock | full crew flow |
| PDF render (server) | N9 stamp | no stamp | no stamp + Advanced template available | + Enterprise template available |

---

## Cross-role visibility matrix (which screens hide entirely per role)

| Screen / Section | Solo | Team | Lead | Foreman | Enterprise | Admin |
|---|---|---|---|---|---|---|
| O1 DispatchScreen | hidden | hidden | visible | visible | visible | visible |
| L3 PeersScreen (full mgmt) | view-only | view-only | full | full | full | full |
| Q2 MESH CONNECTION section | hidden | hidden | hidden | visible | visible | visible |
| C1 Dashboard crew presence | hidden | visible | visible | visible | visible | visible |
| Crew DM channels in K1 | hidden | visible | visible | visible | visible | visible |
| Admin routes (`/api/admin/*`) | hidden | hidden | hidden | hidden | hidden | visible |

(See SECURITY.md §2 for full role × permission matrix.)

---

## Cycle / dead-end check

**No cycles** — every flow has a clear back path to C1 Dashboard or to its parent.

**No dead-ends** — every screen has either:
- a `↩ back` action, OR
- a primary forward action with a defined destination, OR
- a global toolbar (BottomToolbar / LeftSidebar) that always provides an escape

**Special cases:**
- **N7 Pricing** is reachable from at least 8 distinct entry points; always returns to caller via `↩` or routes forward to N8 on conversion.
- **Confirmation dialogs** (cancel subscription, delete account) have explicit Cancel / KEEP options; no implicit dismiss-and-confirm.
- **Lock overlays** (N2-N5, N10, N11) all have a primary CTA (route forward) and a secondary text-link ("Maybe later"); the dimmed background area is also tappable to dismiss.
