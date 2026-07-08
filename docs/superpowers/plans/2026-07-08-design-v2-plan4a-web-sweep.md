# Design System v2 — Plan 4A: Web All-Screens Sweep Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every portal screen renders from `sn-*` tokens (zero `console-*`, zero raw hex outside token files), every route ships the EmptyState/LoadingState/ErrorState trio, 401s silently refresh then redirect with a toast, and the dark-mode toggle ships in Settings.

**Architecture:** Two shared foundations land first — avatar-palette tokens in the generator pipeline, the three state primitives in `components/ui/`, and a single `apiCall` wrapper with the 401 refresh-retry branch adopted by every API client. Then five mechanical sweep batches convert the 71-file `console-*` census to `sn-*` (route + its component family per batch, state trio retrofitted in the same pass so each file is touched once). The final task ships the toggle, deletes all three v1 palette copies, and runs the gates. The §8 shell rebuild (rail/context panel) is explicitly OUT of this plan — tracked separately.

**Tech Stack:** React 18 + Zustand + Tailwind 3.4 (sn preset) + Vitest/RTL/MSW. All commands from `desktop/portal` unless noted; token generator from repo root.

## Global Constraints

- Colors ONLY via `sn-*` classes / `--sn-*` vars. Mapping (apply mechanically): `console-bg`→`sn-bg-base` · `console-surface`→`sn-bg-panel` · `console-border`→`sn-line` · `console-text`→`sn-ink` · `console-text-muted`/`-dim`→`sn-ink-muted` · `console-accent`→`sn-accent` · `console-danger`→`sn-status-error` · `console-ok`→`sn-status-online` · `console-warn`→`sn-attention` · `text-white` on accent/danger fills→`text-sn-ink-on-accent` · `bg-black/40` scrims→`bg-sn-overlay`. Accent discipline: cobalt acts, amber warns, status-error is danger — a mapping must never change a color's JOB; if a v1 usage looks mis-assigned, keep its job and note it.
- Every route renders the trio from the shared primitives: LoadingState while its primary fetch is in flight, EmptyState when the primary collection is empty, ErrorState (with retry) when the primary fetch failed/stale. Existing ad-hoc state text is replaced, not duplicated.
- No emoji. Glyphs per `design/GLYPHS.md`. ASCII tokens (`[x]`, `[>]`) stay.
- Commit style `type(scope): summary` + trailer `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Tests: zero portal tests pin `console-*` strings today (verified) — sweeps must not add regressions; new/changed behavior gets tests. Full suite + `npm run build` green before every commit.
- The generated files (`styles/tokens.css`, `tailwind.tokens.cjs`, `Tokens2.kt`) are NEVER hand-edited — change `design/tokens.json` + `scripts/gen-tokens.mjs` and regenerate.
- OUT OF SCOPE: spec §8 shell rebuild (rail/context panel/breakpoints); Android (Plan 4B); ops mood (Plan 5); skill/CLAUDE.md full rewrite (Plan 6 — except the one "Light mode only" line, Task 9).

---

### Task 1: Avatar-palette tokens (shared foundation for both platforms)

**Files:**
- Modify: `design/tokens.json`, `scripts/gen-tokens.mjs`
- Regenerate: `desktop/portal/src/styles/tokens.css`, `desktop/portal/tailwind.tokens.cjs`, `android/.../ui/Tokens2.kt`
- Test: `desktop/portal/src/__tests__/tokens.test.ts` (extend)

**Interfaces:**
- Produces: token group `avatar` — six hues used for deterministic identity colors on both platforms. In `tokens.json`, add a top-level `"avatar"` object (NOT inside `color.light/dark` — same values both themes): `{"a1":"#2F5FE8","a2":"#3E9B4F","a3":"#8A5FE8","a4":"#E8590C","a5":"#1F8A99","a6":"#C2417E"}` (cobalt, green, violet, amber, teal, magenta — distinct at 28px, AA on white and #1D2129 panels).
- Generator emits: CSS vars `--sn-avatar-a1..a6` in the `:root` block only (theme-invariant); Tailwind colors `sn-avatar-a1..a6`; Kotlin `object Avatar { val A1..A6 }` inside `Tokens2` plus `val AvatarPalette = listOf(A1..A6)`.
- Consumed by: Task 8 (`lib/utils.ts` accentForId), Plan 4B (SmithAvatar / ChatList gradients).

- [ ] **Step 1: Write the failing test** — extend `tokens.test.ts`: generated `tokens.css` contains `--sn-avatar-a1: #2F5FE8` (and a6); `tailwind.tokens.cjs` contains `'sn-avatar-a1'`; run → FAIL.
- [ ] **Step 2: Implement** — tokens.json `avatar` group; in `gen-tokens.mjs` emit the vars after the shadow lines in the `:root` template only, the Tailwind color entries, and the Kotlin object + list. Run `node scripts/gen-tokens.mjs`.
- [ ] **Step 3: Verify** — test passes; `node scripts/gen-tokens.mjs --check` clean; full portal suite green (`android` compile is Plan 4B's concern but run `./gradlew :app:compileDebugKotlin` once with JDK 17 to prove Tokens2 still compiles).
- [ ] **Step 4: Commit** `feat(design): avatar palette tokens on both platforms` + trailer.

---

### Task 2: State trio primitives

**Files:**
- Create: `desktop/portal/src/console/components/ui/StateViews.tsx`
- Test: `desktop/portal/src/console/components/ui/__tests__/StateViews.test.tsx`

**Interfaces (Tasks 4-8 consume exactly these):**
```tsx
export function LoadingState({ label = 'Loading' }: { label?: string }): JSX.Element
// centered column, py-10: a spinner (CSS border-spin div, 20px, border-sn-line +
// border-t-sn-accent, animate-spin, motion-reduce:animate-none) + label in
// font-data text-xs uppercase text-sn-ink-muted. role="status" aria-live="polite".

export function EmptyState({ title, hint, action }: {
  title: string; hint?: string; action?: ReactNode;
}): JSX.Element
// centered column py-12: title text-sm text-sn-ink-muted; optional hint
// text-xs text-sn-ink-muted/70; optional action node below (caller passes a Button).

export function ErrorState({ message = "Couldn't load this.", onRetry }: {
  message?: string; onRetry?: () => void;
}): JSX.Element
// centered column py-10: `[x] ${message}` in font-data text-xs text-sn-attention
// (attention warns; the surface failed, nothing was destroyed) + optional
// RETRY ghost button (focus-visible ring per Plan 2 convention). role="alert".
```

- [ ] **Step 1: Failing tests** — renders label/title/message; onRetry fires; LoadingState has role status; ErrorState role alert; no `console-` classes (assert `container.innerHTML` lacks `console-`).
- [ ] **Step 2: Implement per contract. Step 3: suite green. Step 4: Commit** `feat(portal): shared EmptyState/LoadingState/ErrorState primitives` + trailer.

---

### Task 3: 401 silent refresh → retry → redirect with toast

**Files:**
- Create: `desktop/portal/src/console/api/httpCall.ts`
- Modify: every `desktop/portal/src/console/api/*Client.ts` with a private `call`/fetch helper (13 files — enumerate at execution: `grep -ln "credentials: *'include'" src/console/api/*.ts`), plus `auth/authClient.ts` consumers stay as-is (authClient itself must NOT loop refresh on its own 401s)
- Test: `desktop/portal/src/console/api/__tests__/httpCall.test.ts` (create), spot-update client tests

**Interfaces:**
- Produces: `httpCall<T>(path: string, init?: RequestInit): Promise<{ ok: true; data: T } | { ok: false; status: number; error: string }>` — fetch with `credentials:'include'`; on 401: call `authClient.refresh()` ONCE (module-level single-flight promise so N concurrent 401s share one refresh), retry the original request once; if the retry still 401s or refresh failed → `useAuthStore.getState().clear()` (match the store's actual reset method), `pushToast({ message: 'Session expired — sign in again', tone: 'error' })`, `window.location.assign('/console/login')`, and return the failure. Requests to `/api/auth/*` are exempt from the refresh branch (no loops).
- Each resource client's private `call` delegates to `httpCall` (adapters keep their public shapes — poller/store code compiles unchanged).

- [ ] **Step 1: Failing tests (MSW)** — (a) 401 then refresh 200 then retry 200 → ok:true, exactly one refresh call (count via handler spy); (b) 401 + refresh 401 → failure, toast pushed, redirect called (stub `window.location.assign`); (c) two concurrent 401 requests → one refresh (single-flight); (d) `/api/auth/login` 401 → no refresh attempted.
- [ ] **Step 2: Implement httpCall; adopt in all resource clients** (mechanical: replace each file's fetch core, keep response-shaping). Report the exact list of adopted files.
- [ ] **Step 3: Full suite + build green. Step 4: Commit** `feat(portal): 401s silently refresh, retry once, then redirect with toast` + trailer.

---

### Tasks 4-8: the sweep batches

Per-batch mechanics (identical every batch — worked spec, no per-file code):
1. Apply the Global Constraints class mapping to every listed file (classNames, inline `style` var() refs, template strings). Keep structure/props/behavior identical.
2. Retrofit the trio into the batch's ROUTES: primary fetch in flight → `<LoadingState/>`; primary collection empty → `<EmptyState title="..." />` (reuse the route's existing empty copy where it exists); fetch failed/stale → `<ErrorState onRetry={refetch}/>` wired to the route's existing stale/error flag or the poller's markStale. Replace ad-hoc "Loading..."/empty divs.
3. TDD the trio per route: extend the route's test file — loading renders LoadingState (`role="status"`), empty renders the title, error path renders ErrorState and retry refires the fetch (MSW). Class-mapping needs no new tests (none pin console-*) but every touched test must stay green.
4. Gate per batch: `grep -rn "console-" <batch files>` → ZERO; full suite + build green.
5. One commit per batch, message given below, + trailer.

### Task 4: Sweep batch 1 — comm surface remainder

**Files (13 comm files + route):** `components/comm/`: ActivityFeed, ActivityRow (finish the mixed file), ChannelList, DialField, DialRail, DirectoryRow, FrontTabs, IncomingRequestsFront, MessageInput, MessageList (finish mixed), MyIdCard, PeopleDirectoryFront, ScreenPopHeader; `routes/CommRoute.tsx`.
Trio: CommRoute gets LoadingState while channels load, EmptyState ("No conversations yet — dial a public id to start one") when zero channels, ErrorState on channels-fetch failure. MessageList already has a stale banner — replace with ErrorState inline variant (keep the read-receipt/scroll logic).
Commit: `feat(portal): comm surface on sn tokens + state trio (sweep 1/5)`

### Task 5: Sweep batch 2 — jobs family

**Files:** `components/jobs/` (AssignCrewModal, JobCard, JobCostRollup, JobStageBar, CreateJobModal, EditJobModal, JobStageControls, **JobStatusBadge.tsx** — its 4 inline hex map to: planned→`var(--sn-ink-muted)`, in_progress→`var(--sn-status-online)`, complete→`var(--sn-accent)`, cancelled→`var(--sn-status-error)`); `components/tasks/` (TaskList, AddTaskInput); `components/materials/` (MaterialsList, AddMaterialModal); `routes/JobsListRoute.tsx`, `routes/JobDetailRoute.tsx`.
Trio: JobsListRoute (loading/empty "No jobs yet"/error+retry via jobs poller stale flag); JobDetailRoute (loading exists — replace; error state for failed detail fetch).
Commit: `feat(portal): jobs family on sn tokens + state trio (sweep 2/5)`

### Task 6: Sweep batch 3 — invoices, expenses, time

**Files:** `components/invoices/` (InvoiceStatusBadge, CreateInvoiceModal — its `bg-black/40`→`bg-sn-overlay`, LineItemRow, AddLineItemInput, InvoiceCard); `components/expenses/` (ExpensesTable, AddExpenseModal); `components/time/` (TimeScreen, DailySummaryBar, TodayEntriesList, ClockInDialog); `routes/InvoicesListRoute.tsx`, `routes/InvoiceDetailRoute.tsx`, `routes/TimeRoute.tsx`.
Trio: InvoicesListRoute, InvoiceDetailRoute, TimeRoute (TimeScreen owns the fetch — trio lives where the data does; report placement).
Commit: `feat(portal): invoices+expenses+time on sn tokens + state trio (sweep 3/5)`

### Task 7: Sweep batch 4 — clients, crew, map, header

**Files:** `components/clients/` (ClientContactLines, ClientCard, CreateClientModal); `components/crew/` (CrewCard, AvailabilityDot); `components/map/` (JobPopup, MapSidePanel, MapFilterChips, StatsStrip); `components/header/` (ShareLocationToggle, ShiftClock, ClockToggleButton); `routes/ClientsListRoute.tsx`, `routes/ClientDetailRoute.tsx`, `routes/CrewRoute.tsx`, `routes/MapRoute.tsx`; `console/index.css` map CSS (`.job-marker*`, `.maplibregl-popup*` — swap `theme('colors.console-*')`/`--color-*`/`#22d3ee` refs to `var(--sn-*)`; the cyan crew marker maps to `var(--sn-accent)`).
Trio: ClientsListRoute, ClientDetailRoute, CrewRoute; MapRoute gets ErrorState only if its data hook exposes failure (map tiles themselves out of scope — report what exists).
Commit: `feat(portal): clients+crew+map+header on sn tokens + state trio (sweep 4/5)`

### Task 8: Sweep batch 5 — settings, admin, auth, shell, adaptive-home, surface-lab, palettes

**Files:** `routes/SettingsRoute.tsx`, `routes/AdminRoute.tsx` (colors only — ops MOOD comes in Plan 5), `routes/SurfaceLabRoute.tsx`, `routes/SurfaceHomePreviewRoute.tsx`; `auth/` (LoginForm, RegisterForm, RequireAuth — RequireAuth's inline "Loading..." becomes LoadingState); `layouts/AppHeader.tsx`, `layouts/BottomTabBar.tsx`, `ConsoleShell.tsx`; `components/adaptive-home/` (cards.tsx, AdaptiveDashboard.tsx) + `routes/AdaptiveHomeRoute.tsx` trio; `components/surface-lab/` (SurfaceLabContent, modules, AdaptiveCard, AppShell — then DELETE `theme/consoleTheme.ts` and fix AppShell's import to a local glyph const); `lib/utils.ts` — `accentForId` returns `var(--sn-avatar-aN)` CSS vars (Task 1 tokens; keep the deterministic hash), `colorForRole` maps to `var(--sn-accent|attention|status-online|ink-muted)`.
Trio: AdminRoute (has all three ad-hoc — replace with primitives), SettingsRoute (loading for profile fetch if any; report).
Gate additions: `theme/consoleTheme.ts` no longer exists; `grep -rn "accentForId\|colorForRole" src` consumers render var() correctly (update AppHeader tests if they pin hex).
Commit: `feat(portal): settings+admin+shell+home swept; v1 theme file deleted (sweep 5/5)`

---

### Task 9: Dark toggle, v1 palette deletion, final gates

**Files:**
- Modify: `routes/SettingsRoute.tsx` (Appearance section), `tailwind.config.js` (delete `console-*` colors), `console/index.css` (delete `--color-*` v1 vars lines ~32-47; `html,body` base becomes `background: var(--sn-bg-base); color: var(--sn-ink);`), `/Users/fegensprenelon/smith-net/CLAUDE.md` (one line)
- Test: extend `routes/__tests__/SettingsRoute.test.tsx` (create if absent)

**Interfaces:**
- Appearance section in Settings (between Location sharing ~line 273 and About): SectionHeader "Appearance" + three-way segmented control LIGHT / DARK / SYSTEM (`font-data text-xs uppercase`; selected = `bg-sn-accent text-sn-ink-on-accent`, others ghost) wired to `useThemeStore` `theme`/`setTheme`. No other UI reads the store.
- CLAUDE.md: replace the line `- **Light mode only** for the desktop portal. Monospace UI, console aesthetic.` with `- **Design System v2 (Crew Soft / North Cobalt)** governs the portal: light-first with a user dark toggle, sn-* tokens only. The v1 light-only/monospace lock is repealed (spec: docs/superpowers/specs/2026-07-08-design-system-v2-design.md).`

- [ ] **Step 1: Failing tests** — Settings renders the three options; clicking DARK calls setTheme('dark') and stamps `data-theme="dark"` on documentElement; SYSTEM removes it.
- [ ] **Step 2: Implement toggle; delete the v1 palette from tailwind.config.js and index.css; update CLAUDE.md.**
- [ ] **Step 3: FINAL GATES** — `grep -rn "console-" desktop/portal/src` → ZERO (the deletion makes any straggler a build/test failure — good); `grep -rEn "#[0-9a-fA-F]{6}" desktop/portal/src --include="*.tsx" --include="*.ts" | grep -v test | grep -v styles/tokens.css` → zero app-code hits (report any justified exception); full suite + build green; `node scripts/gen-tokens.mjs --check` clean; manual dark sanity list written into the task report (routes to eyeball: comm, jobs list/detail, invoices, time, settings, admin).
- [ ] **Step 4: Commit** `feat(portal): dark toggle ships; v1 console palette deleted` + trailer.

---

## Self-Review

- Census coverage: Tasks 4-8 enumerate all 71 console-* files from the scout census (routes 13 + components 44 + layout 3 + auth 3 + css + theme + utils/JobStatusBadge hex strays). Batch sums: comm 14, jobs 13, invoices/time 14, clients/crew/map/header 15+css, settings/admin/shell/home/lab 15+palettes. Files already clean (AdaptiveHomeRoute, TimeRoute wrappers, MapRoute:1) still get trio checks in their batch.
- Trio coverage: every registered route appears in exactly one batch's trio list (Map's limited case reported, not faked). Primitives exist before the first retrofit (Task 2 < Task 4).
- 401 lands before batches so ErrorState retrofits can rely on consistent failure shapes.
- Deletion ordering: v1 palette copies die only in Task 9, after batches drove usage to zero; consoleTheme.ts dies in Task 8 with its last importer.
- Type consistency: StateViews signatures (T2) consumed by T4-T9; httpCall shape (T3) adopted by clients without changing public client APIs; avatar tokens (T1) consumed in T8.
- No placeholders: mapping tables + per-file lists + exact trio semantics per route; component code given where components are new (T2), contracts where work is mechanical (T4-8) — matching the reviewed Plan 2 batch pattern.
