# Smith Net — Traceability Matrix

**Purpose:** every Feature ID from `docs/specs/FEATURES.md` traces to at least one screen (no orphans), and every screen traces to at least one feature (no rogue UI).

**Method:** map across three columns — Feature ID, Domain, Surfacing screen(s) (with section ID from `SCREEN-INVENTORY.md`).

---

## Domain 1 — Plans & Execution (the moat)

| Feature | Description | Screen(s) | Server endpoint |
|---|---|---|---|
| 1.1 PLAN Compiler | Compile plan to deterministic execution artifact | E1 PlanScreen → [Synthesize] | POST /api/synthesize |
| 1.2 Cord-based state model | VectorClock + CordEntry + Reconciliation | (architectural — surfaces in K4 message ordering, F1 time entries, D2 job state) | n/a (substrate) |
| 1.3 Plan editor | Visual / form-based authoring | E1 PlanScreen → IntentVersion edit | POST /api/intents, PATCH endpoints |
| 1.4 Plan templates | Start from a template | E1 PlanScreen → "From template" picker (planned for Step 11) | TBD |
| 1.5 Plan preview pane (Free) | Read-only greyed-out + CTA | **N3 overlay** within E1 | n/a (UI only) |
| 1.6 AI plan-authoring assist (Adv+) | SmithAI drafts a plan from job brief | E1 PlanScreen → ProposalAssist | POST /api/intents/auto-generate |
| 1.7 Cord history / audit log per job | per-job cord transitions | E1 → IntentVersion detail; D2 → job audit panel; J1 reports | GET /api/intents/:id (versions) |

## Domain 2 — Jobs & Clients

| Feature | Description | Screen(s) | Server endpoint |
|---|---|---|---|
| 2.1 Create / edit / close a job | basic job CRUD | D3 NewJobFlow, D2 JobPipelineScreen | POST/PATCH/POST close /api/jobs |
| 2.2 Per-job trade picker | 121 trades | D3 → TradePickerField, D2 detail | (client-side metadata) |
| 2.3 Searchable trade picker | full 121 list | TradePickerField (used in A2 onboarding, Q1 profile, D3) | (client-side) |
| 2.4 Client / contact records | client CRUD | M1 ClientsScreen, M2 ClientDetailScreen | (TBD — extend /api with /clients) |
| 2.5 Job persistence + cross-midnight clock | persistence + clock | F1 TimeTrackingScreen | (client-side persistence + sync) |
| 2.6 Live financials per job | running total | D2 JobPipelineScreen header | (client-derived) |
| 2.7 Active-job cap enforcement (Free) | server-authoritative cap | **N4 overlay** fires from D3 Save | POST /api/jobs returns 403 tier_gate_exceeded |

## Domain 3 — Invoicing & Payment

| Feature | Description | Screen(s) | Server endpoint |
|---|---|---|---|
| 3.1 Standard invoice template + PDF | render + send | H1 InvoiceScreen → [Send] → server templates/invoice.html | POST /api/invoices/:id/send |
| 3.2 Advanced invoice template (Adv+) | richer template | H1 → template selector → [Advanced row, locked for <Adv] | server template variant |
| 3.3 Enterprise invoice template (Ent) | multi-payer, milestone | H1 → template selector → [Enterprise row, locked for <Ent] | server template variant |
| 3.4 Invoice email send | queue + send | H1 → G4 InvoicePreviewBottomSheet → [Send] | POST /api/invoices/:id/send |
| 3.5 PDF send cap enforcement (Free) | 5/mo cap | **N5 counter footer + overlay** within H1/G4 | server enforces; 403 tier_gate_exceeded |
| 3.6 Payment status tracking | sent/viewed/paid | H1 invoice list (status chips) | server + webhook |
| 3.7 Stripe / Square integration | payment collection | H1 → invoice → [Pay link] (planned Step 11) | TBD |
| 3.8 Expense capture per job | already shipped | G2 JobExpenseDetailScreen, G1 ExpensesScreen | POST /api/expenses (TBD endpoint name) |

## Domain 4 — Comms & Channels

| Feature | Description | Screen(s) | Server endpoint |
|---|---|---|---|
| 4.1 Per-job channels | channel scoped to job | K1 ChatListScreen, K2/K3 channel lists | POST /api/channels (type=job) |
| 4.2 Ephemeral channels | broadcast only, no persistence | K6 CreateChannelScreen → ChannelPersistence.EPHEMERAL | server enforces persistence rules |
| 4.3 KEEP HISTORY toggle | per-channel persistence flag | K4 ConversationScreen → channel info panel | PATCH channel |
| 4.4 Channel delete + archive | cascade local + remote | K4 → channel info → delete/archive | DELETE /api/channels/:id |
| 4.5 Clear messages on this device | local wipe | K4 → channel info → [Clear messages on this device] | (client-side only, no server) |
| 4.6 Single-#general collapse | de-dup on Supabase sync | (background — invisible to UI) | (sync logic) |
| 4.7 Themed conversation UI | Console colors | K4 (already themed via ConsoleTheme) | n/a |
| 4.8 Signal-style toggle dots | ((●))/((○)) | Q2 SettingsScreen privacy + location toggles | n/a |

## Domain 5 — Mesh / Connectivity

| Feature | Description | Screen(s) | Server endpoint |
|---|---|---|---|
| 5.1 BLE + WiFi-Direct mesh | MeshService transport | C1 Dashboard status pill, L1 BeaconListScreen, L3 PeersScreen | n/a (P2P) |
| 5.2 Work-mode gating on mesh | battery-saver | Q2 Settings WORK MODE section | n/a |
| 5.3 Mesh recovery from ADVERTISE_FAILED | self-recover | (silent — visible in connection-status pill on C1, K1) | n/a |
| 5.4 Mesh bridge to Supabase / Hetzner | online crossover | (BoundaryEngine — transparent to UI) | (uses POST /api/reconcile) |
| 5.5 Connection-status declutter | concise indicator | C1 Dashboard header pill, K1 ChatListScreen header | n/a |
| 5.6 OFFLINE → ONLINE auto-promotion | when realtime up | (BoundaryEngine.isOnline state — surfaces in pills) | (uses WS auth_ok) |

## Domain 6 — AI (SmithAI on-device — Adv+)

| Feature | Description | Screen(s) | Server endpoint |
|---|---|---|---|
| 6.1 LlamaInference lifecycle | SLEEPING → ALIVE | Q2 Settings AI ASSISTANT section [Adv+] | n/a (on-device) |
| 6.2 RULE_BASED_FALLBACK | battery / fail | (silent — falls back automatically) | n/a |
| 6.3 Context gathering | jobs/messages/time/prefs | (background — used by SmithAI prompts) | n/a |
| 6.4 Memory building | patterns | (background) | n/a |
| 6.5 Proactive suggestion engine | ambient | C1 Dashboard suggestions area (when SmithAI ALIVE) | n/a |
| 6.6 Tool integration (web/weather/code) | extra capabilities | (within SmithAI conversational flow when invoked) | (calls llmInterface tools) |
| 6.7 Solo-vs-crew context awareness | privacy | (enforced in AISupervisor / RoleContext) | n/a |
| 6.8 "Load model" button | manual load | Q2 Settings AI ASSISTANT → Load button [Adv+] | n/a |
| 6.9 AI tab gated for Free + Solo with CTA | tier gate | **N10 lock state** within Q2 SettingsScreen | n/a (UI only, JWT entitlements) |

## Domain 7 — Crew & Permissions

| Feature | Description | Screen(s) | Server endpoint |
|---|---|---|---|
| 7.1 Single-user mode | Solo / Adv | (default for tier < Enterprise) | n/a |
| 7.2 Multi-user crew accounts | Enterprise | (entry: Q2 → COLLEAGUES section [planned] OR Dashboard CREW module [Foreman+]) — Step 11 wires fully | POST /api/crew/* (planned) |
| 7.3 Colleague invites + scoped add | invite flow | (existing — verify entry point in Step 5; fires N11 overlay if tier<Ent) | POST /api/colleagues/invite |
| 7.4 Privacy gating | search visibility, location | Q2 SettingsScreen PRIVACY section | (existing — `is_active`, `discoverable` flags) |
| 7.5 Shared jobs across crew | Enterprise | D1 JobBoardScreen (with crew column visible only at Ent) | (existing job + crew data; tier gates surface UI) |
| 7.6 Solo-mode hides crew UI | role/tier hide | (DashboardModuleResolver hides crew modules per RoleContext) | n/a |

## Domain 8 — Onboarding & Settings

| Feature | Description | Screen(s) | Server endpoint |
|---|---|---|---|
| 8.1 Onboarding trade picker | matches new-job picker | A2 OnboardingScreen → TradePickerField | n/a |
| 8.2 Profile with 121-entry trade list | profile trade | Q1 ProfileScreen → TradePickerField | PATCH /api/auth/me |
| 8.3 Settings privacy + location | toggles | Q2 SettingsScreen PRIVACY + LOCATION sections | PATCH preferences |
| 8.4 Dashboard GETTING STARTED tiles gated on real state | not stale | C1 Dashboard | n/a (client) |
| 8.5 Beta seed-data gated | BuildFlags.SEED_DEMO_DATA | C1 Dashboard (when flag on) | n/a |

## Domain 9 — Tier gating + monetization plumbing

| Feature | Description | Screen(s) | Server endpoint |
|---|---|---|---|
| 9.1 plan_management schema | DB substrate | (no UI — schema only) | n/a |
| 9.2 Tier resolver (server-authoritative) | resolves from subscriptions | (no UI — backend) | GET /api/me/entitlements |
| 9.3 Subscription billing (Play + web) | Stripe / Play Billing | **N7 pricing** + N8 subscription detail + Stripe checkout | POST /api/me/upgrade, /webhooks/stripe, /webhooks/play-billing |
| 9.4 14-day Solo trial without CC | trial flow | A4 WelcomeToOpenScreen → trial start; **N4/N5/N3** overlays → trial start | POST /api/me/start-trial |
| 9.5 Locked-feature CTA system | overlays | **N2/N3, N4, N5, N10, N11** | (none — UI on top of server-side caps) |
| 9.6 Smith Net branding stamp | Free PDFs | **N9** server template injection | server PDF render |

## Domain 10 — Desktop portal (online-only secondary client)

| Feature | Description | Screen(s) | Server endpoint |
|---|---|---|---|
| 10.1 Web auth via Supabase | login | Desktop portal Auth UI | Supabase Auth |
| 10.2 Global chat sync via Supabase realtime | online chat | Desktop portal chat (in-progress dashboard) | Supabase Realtime |
| 10.3 Dashboard view of jobs / invoicing | overview | Desktop portal `src/dashboard/*` (in-progress) | (TBD — re-uses /api with web JWT) |

---

## Reverse map — every screen has at least one feature

| Screen ID | Surfaces feature(s) |
|---|---|
| A1 AuthScreen | (auth substrate — supports all features) |
| A2 OnboardingScreen | 8.1 |
| A3 WelcomeScreen | 8.1 (welcome step) |
| A4 WelcomeToOpenScreen [NET-NEW] | 9.4, 9.5 |
| C1 DashboardScreen | 2.6, 4.1, 5.1, 5.5, 5.6, 6.5 (when ALIVE), 7.6, 8.4, 8.5 |
| D1 JobBoardScreen | 2.1, 7.5 (Ent) |
| D2 JobPipelineScreen | 2.1, 2.5, 2.6, 1.7 |
| D3 NewJobFlow | 2.1, 2.2, 2.3, 2.7 |
| E1 PlanScreen | 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7 |
| F1 TimeTrackingScreen | 2.5 |
| G1 ExpensesScreen | 3.8 |
| G2 JobExpenseDetailScreen | 3.8 |
| G3 CategoryManagerScreen | 3.8 |
| G4 InvoicePreviewBottomSheet | 3.1, 3.2, 3.3, 3.4, 3.5 |
| G5 BolLegalSettingsScreen | (compliance for invoicing) |
| H1 InvoiceScreen | 3.1, 3.2, 3.3, 3.4, 3.5, 3.6 |
| I1 ProposalPreviewDialog | (proposal preview — supports invoicing flow) |
| J1 ReportScreen | (report output) |
| K1 ChatListScreen | 4.1, 5.5 |
| K2/K3 ChannelListScreen / ChannelsScreen | 4.1 |
| K4 ConversationScreen | 4.1, 4.3, 4.4, 4.5, 4.7 |
| K5 NewConversationScreen | 4.1 |
| K6 CreateChannelScreen | 4.1, 4.2, 4.3 |
| L1 BeaconListScreen | 5.1 |
| L2 CreateBeaconScreen | 5.1 |
| L3 PeersScreen | 5.1, 7.2 (crew presence — Ent) |
| M1 ClientsScreen | 2.4 |
| M2 ClientDetailScreen | 2.4 |
| N1' MapScreen | (location features) |
| N2' LostAndFoundScreen | (asset / tool tracking — extension) |
| O1 DispatchScreen | 7.5 (Ent dispatch) |
| P1 SupplyScreen | (materials supply — adjacent to 3.8) |
| Q1 ProfileScreen | 8.2 |
| Q2 SettingsScreen | 4.8, 5.2, 6.8, 8.3, 9.3 (subscription row), 6.9 (AI lock for Free/Solo) |
| R1 ArchiveScreen | (audit / archive — supports 1.7) |
| S1 MediaPlayer | (voice/video playback — supports 4.x media) |
| **N1 Trial Banner** | 9.4 |
| **N2/N3 PLAN lock** | 1.5, 9.5 |
| **N4 Active-job cap** | 2.7, 9.5 |
| **N5 PDF cap** | 3.5, 9.5 |
| **N6 Founder counter** | (engagement device — embedded in N2-N11) |
| **N7 Pricing screen** | 9.3 |
| **N8 Subscription detail** | 9.3 |
| **N9 PDF stamp** | 9.6 |
| **N10 AI lock state** | 6.9 |
| **N11 Crew invite lock** | 7.2, 9.5 |
| **N12 Tier-gate Toast** | 9.5 (telemetry surface) |

---

## Orphan check

| Feature ID | Has at least 1 surfacing screen? |
|---|---|
| 1.1 - 1.7 (Plans) | ✅ |
| 2.1 - 2.7 (Jobs) | ✅ |
| 3.1 - 3.8 (Invoicing) | ✅ |
| 4.1 - 4.8 (Comms) | ✅ |
| 5.1 - 5.6 (Mesh) | ✅ |
| 6.1 - 6.9 (AI) | ✅ |
| 7.1 - 7.6 (Crew) | ✅ |
| 8.1 - 8.5 (Onboarding/Settings) | ✅ |
| 9.1 - 9.6 (Tier / monetization) | ✅ |
| 10.1 - 10.3 (Desktop portal) | ✅ |

**No orphans.**

| Screen ID | Has at least 1 feature it surfaces? |
|---|---|
| All A/B/C/D/E/F/G/H/I/J/K/L/M/N'/O/P/Q/R/S | ✅ |
| All N1-N12 net-new | ✅ |

**No rogue UI.**

---

## Cross-references between specs

| When you see this in code | Find the spec | Find the screen |
|---|---|---|
| `intentService` / `synthesizer` / `ledger` | ARCHITECTURE §4, SCHEMA §6 | E1 PlanScreen |
| `BoundaryEngine` / `MeshService` / `VectorClock` | ARCHITECTURE §6, FLOW-DIAGRAMS Flow 5 | C1, K1, K4, L1-L3 |
| `auth.ts` / `Permission` enum | SECURITY §1-§2 | A1, Q1, Q2 |
| `ledger.seal` / `computeHash` | SCHEMA §6 (ledger), SECURITY §6 | E1 → [Seal] action |
| `pricingTiers.ts` (legacy 3-6-9) | DEV-READINESS §5 (to retire) | (no surfacing — legacy) |
| `plan_management` migration | SCHEMA §11 (planned tier columns) | N7, N8 |
| `electricianTools.ts` | SCHEMA §10 (trade-pack tables) | (per-trade UI surfaces — Step 11+) |
| `LlamaInference` / `AISupervisor` | ARCHITECTURE §8 | Q2 AI ASSISTANT (Adv+) |

---

## Step 11 PRD scope estimate (per-feature)

How many net-new PRDs Step 11 will need to write, grouped by feature domain. Each PRD = a vertical slice (database + service + UI + tests + BDD).

| Domain | Estimated PRDs | Notes |
|---|---|---|
| 9 Tier gating | 8-10 PRDs | Most net-new; subscription, entitlements, founder seats, all 12 N1-N12 surfaces |
| 3 Invoicing (Adv + Ent templates, branding stamp) | 3 PRDs | Templates + send queue + counter |
| 1 Plans (UI surfacing of existing pipeline) | 2 PRDs | Compose UI, sealed-artifact actions |
| 6 AI (entitlement-gated UI) | 1 PRD | The lock state + downgrade-friendly |
| 7 Crew (Enterprise) | 3 PRDs | Crew invite, shared jobs, dispatch upgrade |
| Cross-cutting security (S1, S7) | 2 PRDs | X-User-Id removal + zod validation |
| Audit log to DB (S8) | 1 PRD | Move audit log from file to DB + retention |
| Total | **~20-22 PRDs** | sized for 2-3 implementer-weeks each in Step 11 |

(Step 10 will produce the formal Feature Breakdown / Shape Up table; this is just a sizing hint.)
