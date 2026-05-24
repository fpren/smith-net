# Smith Net — Screen Inventory

**Method:** every Compose screen / overlay / dialog / bottom-sheet that routes (or fires as a modal) is listed once. Pure utility files (formatters, renderers, types-only files, view-models without UI) are excluded.

**Counts:**
- Existing Android screens: 30
- Net-new screens / overlays from Step 3: 14
- **Total routable destinations: 44**

---

## Notation

- **Tier** = minimum tier to access (Open / Solo / Adv / Ent / All)
- **Role** = role gate (S=Solo, T=Team, L=Lead, F=Foreman, E=Enterprise, A=Admin, * = all roles)
- **Origin** = `existing` = ships today, no UI change | `existing-with-N` = existing + receives a net-new state from Step 3 | `net-new` = added by Sigma Step 3
- **State count** = enumerated states from STATE-COVERAGE.md (net-new only)

---

## Section A — Auth & Onboarding (4 screens)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| A1 | AuthScreen | `ui/AuthScreen.kt` | All | * | existing |
| A2 | OnboardingScreen | `ui/OnboardingScreen.kt` | All | * | existing |
| A3 | WelcomeScreen | `ui/WelcomeScreen.kt` | All | * | existing |
| A4 | **WelcomeToOpenScreen** (post-onboarding) | net-new | Open | * | **net-new** |

## Section B — Navigation hub (1 surface, 2 components)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| B1 | MainActivity (host) | `MainActivity.kt` | All | * | existing |
| B2 | Navigation router | `ui/Navigation.kt` | All | * | existing |
| — | BottomToolbar (component) | `ui/components/BottomToolbar.kt` | All | * | existing |
| — | LeftSidebar (component) | `ui/components/LeftSidebar.kt` | All | * | existing |

## Section C — Dashboard (1 screen + role-resolved modules)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| C1 | DashboardScreen | `ui/dashboard/DashboardScreen.kt` | All | * | **existing-with-N** (adds 1 quick-action tile per tier; trial banner overlay) |
| — | DashboardModules (resolver) | `ui/dashboard/DashboardModules.kt`, `DashboardModuleResolver.kt` | role-driven | varies | existing |

## Section D — Jobs (3 screens)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| D1 | JobBoardScreen | `ui/jobboard/JobBoardScreen.kt` | All | * | existing |
| D2 | JobPipelineScreen + JobStageBar | `ui/jobpipeline/JobPipelineScreen.kt`, `JobStageBar.kt` | All | * | existing |
| D3 | NewJobFlow | `ui/newjob/NewJobFlow.kt` | All | * | **existing-with-N4** (Free tier 2nd-job blocked → N4 overlay) |

## Section E — PLAN / Intent (the moat) (1 screen + 2 component packs)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| E1 | PlanScreen | `ui/plan/PlanScreen.kt` | All to view; Solo+ to compile | * | **existing-with-N2/N3** (Free sees overlay on compose/compile) |
| — | IntentComponents | `ui/plan/IntentComponents.kt` | inherits E1 | * | existing |
| — | ProposalAssist | `ui/plan/ProposalAssist.kt` | Adv+ (AI assist) | * | existing |

## Section F — Time (1 screen)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| F1 | TimeTrackingScreen | `ui/timetracking/TimeTrackingScreen.kt` | All | * | existing |

## Section G — Expenses (5 screens)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| G1 | ExpensesScreen | `ui/expenses/ExpensesScreen.kt` | All | * | existing |
| G2 | JobExpenseDetailScreen | `ui/expenses/JobExpenseDetailScreen.kt` | All | * | existing |
| G3 | CategoryManagerScreen | `ui/expenses/CategoryManagerScreen.kt` | All | * | existing |
| G4 | InvoicePreviewBottomSheet | `ui/expenses/InvoicePreviewBottomSheet.kt` | All | * | existing |
| G5 | BolLegalSettingsScreen | `ui/expenses/BolLegalSettingsScreen.kt` | All | * | existing |

## Section H — Invoice (1 screen + dialogs)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| H1 | InvoiceScreen | `ui/invoice/InvoiceScreen.kt` | All | * | **existing-with-N5** (PDF cap counter footer + 6th-send overlay; Adv+ unlocks Advanced template; Ent unlocks Enterprise template) |

## Section I — Proposals (1 screen + dialogs)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| I1 | Proposal preview / dialog | `ui/proposal/ProposalPreviewDialog.kt` | All | * | existing |
| — | ProposalGenerator + Formatter | `ui/proposal/ProposalGenerator.kt`, `ProposalFormatter.kt` | inherits I1 | * | existing |

## Section J — Reports (1 screen)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| J1 | ReportScreen | `ui/report/ReportScreen.kt` | All | * | existing |

## Section K — Comms / Channels (5 screens)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| K1 | ChatListScreen | `ui/ChatListScreen.kt` | All | * | existing |
| K2 | ChannelListScreen | `ui/ChannelListScreen.kt` | All | * | existing |
| K3 | ChannelsScreen | `ui/ChannelsScreen.kt` | All | * | existing |
| K4 | ConversationScreen | `ui/ConversationScreen.kt` | All | * | existing |
| K5 | NewConversationScreen | `ui/NewConversationScreen.kt` | All | * | existing |
| K6 | CreateChannelScreen | `ui/CreateChannelScreen.kt` | All | * | existing |

**Note:** Section K has **5 routable screens but 6 IDs** — K2/K3 distinction needs clarification (likely a refactor in progress). Flagged for Step 11 cleanup.

## Section L — Mesh / Beacons / Peers (3 screens)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| L1 | BeaconListScreen | `ui/BeaconListScreen.kt` | All | * | existing |
| L2 | CreateBeaconScreen | `ui/CreateBeaconScreen.kt` | All | * | existing |
| L3 | PeersScreen | `ui/PeersScreen.kt` | All | * (gated by `Permission.GATEWAY_RELAY` for foreman+) | existing |

## Section M — Clients (2 screens)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| M1 | ClientsScreen | `ui/clients/ClientsScreen.kt` | All | * | existing |
| M2 | ClientDetailScreen | `ui/clients/ClientDetailScreen.kt` | All | * | existing |

## Section N — Map / Location (2 screens)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| N1' | MapScreen | `ui/map/MapScreen.kt` | All | * | existing |
| N2' | LostAndFoundScreen | `ui/map/LostAndFoundScreen.kt` | All | * | existing |

(Prime suffix to disambiguate from Step 3 net-new IDs N1-N12.)

## Section O — Dispatch (1 screen — Foreman+)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| O1 | DispatchScreen | `ui/dispatch/DispatchScreen.kt` | All | F/L/E/A | existing (role-gated, hidden for Solo) |

## Section P — Supply (1 screen)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| P1 | SupplyScreen | `ui/supply/SupplyScreen.kt` | All | * | existing |

## Section Q — Profile & Settings (2 screens)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| Q1 | ProfileScreen | `ui/ProfileScreen.kt` | All | * | existing |
| Q2 | SettingsScreen | `ui/SettingsScreen.kt` | All | * | **existing-with-N1/N8/N10** (trial banner; new SUBSCRIPTION section above PROFILE; AI Assistant section locked for Solo) |

## Section R — Archive (1 screen)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| R1 | ArchiveScreen | `ui/ArchiveScreen.kt` | All | * | existing |

## Section S — Media & Misc (2 screens/overlays)

| ID | Screen | File | Tier | Role | Origin |
|---|---|---|---|---|---|
| S1 | MediaPlayer | `ui/MediaPlayer.kt` | All | * | existing |
| S2 | InviteBanner | `ui/InviteBanner.kt` | All | * (component, not a route) | existing |

## Section T — NET-NEW Step-3 surfaces (12 + WelcomeToOpen + Subscription detail)

| ID | Screen / Overlay | Origin | States | Renders within |
|---|---|---|---|---|
| **N1** | Trial banner | net-new | 13 | global (every screen during trial) |
| **N2** | Locked PLAN Compiler overlay (compose entry) | net-new | 11 | within E1 PlanScreen |
| **N3** | Locked PLAN Compiler preview (PLAN tab open) | net-new | 11 | within E1 PlanScreen |
| **N4** | Active-job cap soft wall | net-new | 7 | fires from D3 NewJobFlow Save |
| **N5** | PDF send cap counter + overlay | net-new | 9 | within H1 InvoiceScreen send dialog (G4 InvoicePreviewBottomSheet too) |
| **N6** | Founder seats counter pill | net-new | 8 | embedded in N2/N3/N4/N5/N10/N11 + N7 |
| **N7** | Tier selection / pricing screen | **net-new (full screen)** | 12 | route from anywhere |
| **N8** | Subscription detail screen | **net-new (full screen)** | 11 | route from Q2 SettingsScreen |
| **N9** | Branded PDF stamp (server-side) | net-new (server template) | 4 | server render of invoice / proposal PDFs for Open tier |
| **N10** | AI Assistant lock state in Settings | net-new (settings section variant) | 5 | within Q2 SettingsScreen AI ASSISTANT section |
| **N11** | Crew invite locked overlay | net-new | 4 | fires from existing colleague-invite action |
| **N12** | Tier-gate Toast | net-new (Toast variant) | 2 | global, follows any cap-hit |

**Plus the WelcomeToOpenScreen (A4)** — already counted above.

---

## Total: 44 routable destinations

| Section | Count |
|---|---|
| A Auth & Onboarding | 4 (incl. A4 net-new) |
| C Dashboard | 1 |
| D Jobs | 3 |
| E PLAN / Intent | 1 |
| F Time | 1 |
| G Expenses | 5 |
| H Invoice | 1 |
| I Proposals | 1 |
| J Reports | 1 |
| K Comms | 6 |
| L Mesh / Beacons / Peers | 3 |
| M Clients | 2 |
| N' Map | 2 |
| O Dispatch | 1 |
| P Supply | 1 |
| Q Profile & Settings | 2 |
| R Archive | 1 |
| S Media | 1 (MediaPlayer; InviteBanner is a component) |
| T Net-new Step-3 | 12 (overlays + 2 full screens; A4 counted in A) |
| **TOTAL** | **48 IDs / 44 unique routable destinations** (4 IDs are sub-components) |

---

## Cross-cutting concerns

### Tier gating (visible to user via N1-N12)
| Tier-gated feature | Renders gate at | Routes to |
|---|---|---|
| PLAN Compiler (Solo) | E1 PlanScreen | N7 pricing |
| Active-job cap (Free) | D3 NewJobFlow on Save | N7 pricing |
| PDF send cap (Free) | H1 / G4 send | N7 pricing |
| SmithAI (Advanced) | Q2 Settings → AI ASSISTANT section | N7 pricing |
| Crew invite (Enterprise) | (TBD: existing colleague invite action — verify entry point in Step 5) | N7 pricing |
| Advanced invoice template (Advanced) | H1 InvoiceScreen template selector | N7 pricing |
| Enterprise invoice template (Enterprise) | H1 InvoiceScreen template selector | N7 pricing |

### Role gating (hidden via existing role checks)
| Role-gated feature | Hidden from | Visible to |
|---|---|---|
| MESH CONNECTION section in Settings | Solo | Foreman+ (`Permission.GATEWAY_RELAY`) |
| Crew quick-actions on Dashboard | Solo | Foreman+ |
| DispatchScreen (O1) | Solo | Foreman+ |
| Crew presence repository data | Solo | Foreman+ |
| AI crew insights | Solo | Foreman+ (already enforced per commit `4ce8733`) |

### Components (no route, but binding to design system)
- BottomToolbar — bottom navigation primary entry
- LeftSidebar — desktop / wider screens
- TradePickerField — used in Onboarding, Profile, NewJob, possibly more
- ConsoleTheme — style API (the most important "component")
- PixelIcons — icon set (currently sparse — Unicode glyphs preferred per EXTRACTED-PATTERNS §15)
- InviteBanner — soft prompt component (existing)
- ConsoleSeparator — divider (defined in ConsoleTheme.kt)
