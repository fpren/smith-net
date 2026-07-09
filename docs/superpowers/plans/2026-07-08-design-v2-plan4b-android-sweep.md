# Design System v2 — Plan 4B: Android All-Screens Sweep Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every crew-mood Android screen renders colors from `LocalSmithColors` (zero `ConsoleTheme` COLOR references, zero raw `Color(0x...)` outside token/theme files), every crew screen ships the Smith state trio, 401s trigger `refreshSession()` instead of silent loops, and the dark theme actually flips via a Settings appearance row.

**Architecture:** Three foundations first — SmithTheme finally MOUNTED at the app root (it currently isn't; `LocalSmithColors` resolves to its default everywhere) with token-driven root Surface + status bar; a colorless `SmithType` typography set (ConsoleTheme's TextStyles carry baked-in v1 colors, so color must move to call sites); Smith state-trio composables; and the documented-but-unwired 401→`refreshSession()` path. Then five sweep batches convert the ~1,689 crew-screen color-lines and 76 raw hexes (ops screens — dispatch/plan/proposal-preview/map — are Plan 5 and EXCLUDED). The finale adds the appearance row and flips `darkEnabled`.

**Tech Stack:** Jetpack Compose + Tokens2/LocalSmithColors + JUnit4/Robolectric/Turbine. Every gradle command needs `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home` first (JDK 25 crashes Gradle 8.2). Paths relative to `android/app/src/main/java/com/guildofsmiths/trademesh/`.

## Global Constraints

- Colors ONLY via `LocalSmithColors.current` (or `Tokens2` in non-composable code). Mapping (apply mechanically): `ConsoleTheme.background`→`colors.bgBase` · `.surface`→`colors.bgPanel` · `.text`→`colors.ink` · `.textSecondary`/`.textMuted`/`.textDim`/`.textQuiet`/`.placeholder`→`colors.inkMuted` · `.accent`/`.accentDim`→`colors.accent` · `.success`→`colors.statusOnline` · `.warning`→`colors.attention` · `.error`→`colors.statusError` · `.separator`/`.separatorFaint`→`colors.line` · `.sentLine`/`.cursor`/`.receivedPrefix`/`.sentPrefix`→nearest job-preserving token (report each). Raw `Color(0xFFD97706)` amber → `colors.attention`. Avatar gradient hexes → `Tokens2.AvatarPalette` (deterministic index by id hash — the web's accentForId rule). White-on-fill → `colors.inkOnAccent`. A mapping must never change a color's JOB.
- Typography: `ConsoleTheme.<style>` refs where the style's baked-in color matters get `SmithType.<style>` (colorless) + explicit `color = colors.X`. Pure-font refs (`ConsoleTheme.inter`, `.jetBrainsMono`) stay.
- OPS EXCLUSION (Plan 5, do not touch): `dispatch/DispatchScreen.kt`, `plan/PlanScreen.kt`, `plan/IntentComponents.kt`, `proposal/ProposalPreviewDialog.kt`, `map/MapScreen.kt`, `map/MapPanels.kt`, `map/LostAndFoundScreen.kt`. PeersScreen counts as CREW.
- Every crew screen with a data load renders the Smith trio: `SmithLoadingState` while loading, `SmithEmptyState` when its primary collection is empty, `SmithErrorState` (+retry where a reload exists) on failure. Where no failure signal exists upstream, implement what the data layer supports and report the gap honestly.
- Maestro guard: `android/maestro/smithnet_solo_e2e.yaml` pins visible text incl. bracketed nav labels (`[Home]`, `[Clients]`, `[Plan]`, `[Save]`, `[▶] LOGIN`, `[OK] CREATE`) — NEVER change bracketed-label text or the `solo_e2e_*` testTags. Grep the yaml before renaming any visible string.
- No Material widgets in new code (`material3.Text` allowed); no emoji; commit style + trailer `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Gate per batch: for the batch's files, `grep -n "ConsoleTheme\.\(background\|surface\|text\|textSecondary\|textMuted\|textDim\|textQuiet\|placeholder\|accent\|accentDim\|success\|warning\|error\|separator\|separatorFaint\|sentLine\|cursor\|receivedPrefix\|sentPrefix\)" <files>` → zero; `grep -n "Color(0x" <files>` → zero; `:app:compileDebugKotlin :app:testDebugUnitTest` BUILD SUCCESSFUL.
- `ConsoleTheme.kt` itself keeps its definitions until Plans 5/6 (ops screens still consume it); this plan only stops CREW screens from using its colors. Files whose only refs are fonts/styles need no color work.

---

### Task 1: Mount SmithTheme + token-driven root chrome

**Files:**
- Modify: `MainActivity.kt` (~252-260 setContent), `ui/theme/Theme.kt` (status bar SideEffect ~90-94)
- Modify: `ui/theme2/SmithTheme.kt` (add a `darkPreference` seam)
- Test: `app/src/test/.../ui/theme2/SmithThemeMountTest.kt` (create — pure JVM where possible)

**Interfaces:**
- `SmithTheme(darkEnabled: Boolean = false, content)` gains an overload/param `themePreference: ThemePreference = ThemePreference.SYSTEM` where `enum class ThemePreference { LIGHT, DARK, SYSTEM }` lives in theme2; resolution: LIGHT→light, DARK→dark, SYSTEM→`isSystemInDarkTheme()`, ALL still gated behind `darkEnabled` (stays false until Task 9). Expose `fun resolveDark(pref: ThemePreference, systemDark: Boolean, darkEnabled: Boolean): Boolean` as a pure testable function.
- MainActivity: `setContent { TradeMeshTheme { SmithTheme(darkEnabled = false) { Surface(color = LocalSmithColors.current.bgBase) { ... } } } }` — SmithTheme INSIDE TradeMeshTheme (Material stays outermost until Plan 5/6), root Surface color from the token.
- Theme.kt SideEffect: `window.statusBarColor` and `isAppearanceLightStatusBars` become parameters fed from the resolved SmithColors (light: bgBase + light-icons=true; dark: dark bgBase + false). Until Task 9 the values are the light tokens — but the plumbing takes the resolved palette, not hardcoded hex. Remove the dead `DarkColorScheme` Material palette (unused by design; report if anything referenced it).
- Produces: `LocalSmithColors` now resolves through a real provider everywhere; every already-migrated surface (ConversationScreen, dialogs) is unaffected (same light palette).

- [ ] Step 1: failing test for `resolveDark` (8 cases: 3 prefs × system on/off, darkEnabled false forces light).
- [ ] Step 2: implement; compile + full unit tests green.
- [ ] Step 3: Commit `feat(android): SmithTheme mounted at root; status bar and surface on tokens` + trailer.

### Task 2: SmithType (colorless typography) + Smith state trio

**Files:**
- Create: `ui/theme2/SmithType.kt`, `ui/theme2/SmithStates.kt`
- Test: `app/src/test/.../ui/theme2/SmithTypeTest.kt`

**Interfaces:**
- `object SmithType` mirrors ConsoleTheme's text-style NAMES used by crew screens (`title, header, body, bodyBold, bodySmall, caption, captionBold, timestamp, prefix, action, commName, commBody, commId, commTimestamp, brand, version, prompt, dialpad`) with IDENTICAL font/size/weight/spacing but `color = Color.Unspecified` — copy each definition from ConsoleTheme.kt and strip the color. Test asserts a sample (body, caption, commBody) match ConsoleTheme's font/size/weight and have unspecified color.
- `SmithStates.kt`: `SmithLoadingState(label: String = "LOADING")` — centered column, 20dp spinner drawn with a 2dp arc in `colors.line`/`colors.accent` via `CircularProgressIndicator(color = colors.accent, trackColor = colors.line, strokeWidth = 2.dp)` (material3 CircularProgressIndicator is ALLOWED here as the one sanctioned progress primitive — note it in the file header) + label jetBrainsMono 10sp uppercase inkMuted; `SmithEmptyState(title: String, hint: String? = null)`; `SmithErrorState(message: String = "Couldn't load this.", onRetry: (() -> Unit)? = null)` — `[x] message` jetBrainsMono 10sp `colors.attention` + RETRY ghost SmithButton. Mirror the web's contracts.
- [ ] TDD SmithType; SmithStates compile-verified; commit `feat(android): SmithType colorless styles + Smith state trio` + trailer.

### Task 3: 401 → refreshSession wiring

**Files:**
- Modify: `service/ChatManager.kt` (onFailure ~167-184), the REST error seam (`AuthService.kt` / api clients that check `res.code`)
- Test: extend/create under `app/src/test/.../service/` or `data/` per what's mockable

**Interfaces:**
- WS: in `onFailure`/`onClosed`, when the failure is an HTTP 401/403 upgrade rejection (response?.code), call `SupabaseAuth.refreshSession()` (suspend — launch on the manager's scope) before `scheduleReconnect()`; cap refresh attempts (one per N reconnects or exponential guard) so a dead session doesn't hot-loop; on refresh failure fall back to the existing reconnect cadence and log `[x] session refresh failed`.
- REST: add a small shared helper (e.g. `service/AuthedRequest.kt`): `suspend fun <T> withAuthRetry(block: suspend () -> Response<T>-like): ...` — on 401, `refreshSession()` once, retry once. Adopt it in the clients that already inspect `res.code` (InvoicesApiClient, PresenceApiClient, GatewayClient — enumerate by grep, adopt each, report the list). Match each client's existing result shape.
- [ ] TDD what's unit-testable (the retry helper with a fake block); compile + tests green; commit `feat(android): 401s refresh the session before retry/reconnect` + trailer.

### Tasks 4-8: the sweep batches

Per-batch mechanics (identical): (1) apply the color mapping + SmithType conversion to every listed file — `val colors = LocalSmithColors.current` at each composable that needs it; (2) replace raw `Color(0x...)` per the Global Constraints; (3) retrofit the trio where the screen loads data (ViewModel/repository flags — wire what exists, report gaps); (4) run the batch gate greps + gradle compile + unit tests; (5) one commit per batch + trailer. Maestro guard before any visible-string change.

### Task 4: Sweep A — comm cluster residue
**Files:** `ui/ConversationScreen.kt` (6 raw hex: connection-tint trio ~186-188, status trio ~1521-1523 → token equivalents; judge tints: they are bg washes — map to bgBase/bgSunken variants or drop the wash for `colors.bgBase`, report), `ui/ChatListScreen.kt` (remaining ConsoleTheme colors + 3 avatar hexes → AvatarPalette), `ui/ChannelListScreen.kt`, `ui/CreateChannelScreen.kt`, `ui/NewConversationScreen.kt` (2 avatar hexes), `ui/ArchiveScreen.kt` (1 hex amber → attention), `ui/comm/IncomingScreen.kt`, `ui/comm/QrCodes.kt`, `ui/BeaconListScreen.kt`, `ui/CreateBeaconScreen.kt`, `ui/MediaPlayer.kt` (47 color-lines), `ui/PeersScreen.kt`, `ui/components/SmithAvatar.kt` (6-hex gradient → `Tokens2.AvatarPalette` deterministic by name hash).
Commit: `feat(android): comm cluster on Smith tokens (sweep A)`

### Task 5: Sweep B — dashboard + jobboard
**Files:** `ui/dashboard/DashboardScreen.kt`, `ui/dashboard/DashboardModules.kt` (128 color-lines + 6 hexes: `0xFFD97706`→attention, `0xFF1d4ed8`→accent, `0xFFDC2626`→statusError), `ui/jobboard/JobBoardScreen.kt` (154 color-lines — the single largest file; work top-to-bottom, compile mid-way). Trio: DashboardScreen + JobBoard list states per their ViewModel flags (JobBoardViewModel exists).
Commit: `feat(android): dashboard + jobboard on Smith tokens (sweep B)`

### Task 6: Sweep C — pipeline, newjob, supply, timetracking, report
**Files:** `ui/jobpipeline/JobPipelineScreen.kt`, `ui/jobpipeline/JobStageBar.kt`, `ui/newjob/NewJobFlow.kt`, `ui/supply/SupplyScreen.kt`, `ui/timetracking/TimeTrackingScreen.kt`, `ui/report/ReportScreen.kt`. Trio per data screen.
Commit: `feat(android): pipeline+supply+time+report on Smith tokens (sweep C)`

### Task 7: Sweep D — invoices + expenses + clients
**Files:** `ui/invoice/InvoiceScreen.kt`, `ui/expenses/{ExpensesScreen,JobExpenseDetailScreen,CategoryManagerScreen,BolLegalSettingsScreen,ExpenseCsvImport,InvoicePreviewBottomSheet}.kt` (CategoryManager's 2 hexes: user-entered hex parsing STAYS — it's user data — the fallback `0xFF8C6B2A` → accent), `ui/clients/{ClientsScreen,ClientDetailScreen}.kt`. Trio per data screen.
Commit: `feat(android): invoices+expenses+clients on Smith tokens (sweep D)`

### Task 8: Sweep E — settings, profile, onboarding, auth, shared components
**Files:** `ui/SettingsScreen.kt` (174 color-lines — colors only, admin-health sections included, mood untouched), `ui/ProfileScreen.kt`, `ui/OnboardingScreen.kt` (10 parchment hexes → tokens), `ui/WelcomeScreen.kt`, `ui/AuthScreen.kt`, `ui/components/{BottomToolbar,LeftSidebar,TradePickerField}.kt`, `ui/PixelIcons.kt` (16 color-lines), and the `BottomNavBar`/`ConsoleHeader` composables inside `ui/ConsoleTheme.kt` (sweep their COLOR usage to LocalSmithColors in place; the theme object's definitions stay; bracketed labels untouched — Maestro).
Commit: `feat(android): settings+onboarding+auth+shell on Smith tokens (sweep E)`

### Task 9: Appearance row + dark flip

**Files:**
- Modify: `ui/SettingsScreen.kt` (appearance section), `data/UserPreferences.kt` (theme pref persistence), `MainActivity.kt` (feed pref into SmithTheme, `darkEnabled = true`), `ui/theme/Theme.kt` (status bar follows resolved palette)

**Interfaces:** `UserPreferences.getThemePreference(): ThemePreference` / `setThemePreference(p)` (SharedPreferences-backed, default SYSTEM); Settings gains an "APPEARANCE" section with LIGHT/DARK/SYSTEM rows (selected = accent fill + inkOnAccent text, jetBrainsMono 11sp uppercase — mirror the web control); MainActivity reads the pref into a state, passes `SmithTheme(darkEnabled = true, themePreference = pref)`; status bar + root Surface follow the resolved palette (Task 1 plumbing). Manual QA checklist in the report (screens to eyeball dark: conversation, chat list, jobboard, dashboard, settings, invoices).
- [ ] TDD `resolveDark` already covered; add UserPreferences round-trip test; compile + tests; commit `feat(android): dark theme ships behind Settings appearance` + trailer.

### Task 10: Whole-plan gates
- Crew-file color grep (the Global Constraints pattern) over every Task 4-8 file → zero; `grep -rn "Color(0x" app/src/main --include="*.kt" | grep -v Tokens2.kt | grep -v ConsoleTheme.kt | grep -v theme/Theme.kt | grep -v <ops files>` → report remaining (must be ops-only + CategoryManager user-hex parsing); full `testDebugUnitTest` + `compileDebugKotlin`; `node scripts/gen-tokens.mjs --check`; Maestro yaml pins unchanged (`git diff master -- android/maestro` empty). Report all outputs verbatim.

---

## Self-Review
- Census coverage: Tasks 4-8 enumerate every crew file with color refs from the scout census; ops exclusions listed by name; ConsoleTheme.kt's own BottomNavBar/ConsoleHeader handled in Task 8 without deleting the theme object.
- Hidden prerequisites from the scout are Tasks 1-2 (mount + colorless type) — everything downstream assumes them.
- Type consistency: ThemePreference/resolveDark (T1) consumed by T9; SmithType/SmithStates (T2) consumed by T4-8; withAuthRetry (T3) self-contained.
- Maestro/testTag guard is a Global Constraint applied per batch, not an afterthought.
- No placeholders: mapping table + per-file lists + judgment items named individually (tints, sentLine, CategoryManager user hex).
