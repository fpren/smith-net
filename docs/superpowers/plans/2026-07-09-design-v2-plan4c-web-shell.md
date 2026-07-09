# Design System v2 — Plan 4C: Web Shell (Desktop Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The portal shell becomes a desktop version of the Android app per the amended spec §8 and Fegens' Variant-3 pick: a slim 64px glyph rail (Android's role-adaptive tab idiom) replaces the header nav at ≥1024px, the bottom tab bar covers <1024px, and at ≥1280px job/client/invoice detail slides in BESIDE its list instead of replacing the page.

**Architecture:** Three moves. (1) A `SmithRail` component carrying the role-adaptive tab set + gear + avatar/logout, mounted by a restructured `ConsoleShell` that keeps a slim top status strip (route title + ShiftClock); the shell's responsive switch moves from `md` (768) to `lg` (1024) — content-level `md:` classes elsewhere stay. (2) The jobs/clients/invoices list routes become nested layout routes with detail as `<Outlet/>`: at `xl` (1280) a two-column grid renders list + detail panel each with its own scroll region; below `xl` the outlet replaces the list (today's behavior). URL stays the source of truth (`/console/jobs/:id` keeps working, deep links unaffected). (3) Tab vocabulary mirrors Android where the web HAS the surface; Dispatch/Plan/Tasks are honest gaps documented for Plan 5 — no empty fabricated routes.

**Tech Stack:** React 18 + React Router 6 nested routes + Zustand + Tailwind (sn tokens) + Vitest/RTL/MSW. Portal commands from `desktop/portal`.

## Global Constraints

- Colors/typography: sn tokens only (`font-data` for rail labels — JetBrains Mono, bracketed-label idiom `[Ho]`/`[Jo]` style short forms with `title=` tooltips carrying the full name). No raw hex, no emoji; glyphs per `design/GLYPHS.md`.
- Role adaptivity uses the WEB role model (`authStore` `ConsoleRole`; `hasForemanRole()` = foreman/enterprise/admin): worker rail = Home, Clock, Comm; foreman rail = Home, Map, Jobs, Clients, Invoices, Crew, Comm; admin additionally gets the Admin tab. (Android's Dispatch/Plan/Tasks tabs have no web surface yet — tracked, not faked.)
- Accessibility names preserved: the primary nav (rail AND bottom bar) carries `aria-label` "primary navigation"; the ShiftClock group keeps its `role="group"`/shift name; every rail item is a real link/NavLink with focus-visible ring per convention.
- Detail-beside-list: list route stays mounted with its polling; the panel mounts the SAME detail component reading `useParams` (both polling scopes run concurrently — the stores were split for exactly this). Back-links hidden at `xl+` (list is visible). `<1280px` behavior identical to today.
- Breakpoint discipline: ONLY shell-nav toggles move `md:`→`lg:` (AppHeader/BottomTabBar/ConsoleShell padding); the 71 content-level `md:` utilities stay untouched.
- Existing tests updated, not deleted: `ConsoleShell.test.tsx` (logout/role/nav/shift assertions), `BottomTabBar.test.tsx` (tab membership per role + the breakpoint class assertion). Route tests must keep passing unmodified (they render routes in isolation).
- Full suite + `npm run build` green before every commit. Commit style + trailer `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

---

### Task 1: SmithRail + shell restructure

**Files:**
- Create: `desktop/portal/src/console/layouts/SmithRail.tsx`
- Modify: `desktop/portal/src/console/ConsoleShell.tsx`, `layouts/AppHeader.tsx` (slims into `TopStrip` — rename or rewrite in place), `layouts/BottomTabBar.tsx` (breakpoint `md:`→`lg:`)
- Test: `layouts/__tests__/SmithRail.test.tsx` (create), update `__tests__/ConsoleShell.test.tsx` + `layouts/__tests__/BottomTabBar.test.tsx`

**Interfaces:**
- `SmithRail()` — `hidden lg:flex` fixed-width `w-16` column, `bg-sn-bg-panel border-r border-sn-line`: logo `SN` (font-display, sn-accent) top; NavLink tabs (short mono labels: HO/CLK/MAP/JOB/CLI/INV/CRW/COM/ADM per role, `title` = full name, active = `bg-sn-accent text-sn-ink-on-accent` pill, inactive `text-sn-ink-muted`); spacer; gear NavLink to settings; `Avatar` (size sm) with presence-ring + `title={displayName}`; logout button (icon `[>]`-style mono, `aria-label="Log out"`, same authClient.logout+clear+navigate behavior moved from AppHeader). `aria-label="primary navigation"` on its `<nav>`.
- `ConsoleShell` becomes: `flex h-screen` → `<SmithRail/>` + right column (`flex-1 flex flex-col min-w-0`): `<TopStrip/>` (brand small + current-section title optional + role Chip + ShiftClock group — the strip merges today's AppHeader right-cluster remnants and the ShiftClock strip; logged-out state = brand only), `<main class="flex-1 min-h-0 overflow-y-auto p-6 pb-20 lg:pb-6">`, `<BottomTabBar/>`.
- BottomTabBar: `lg:hidden` (was `md:hidden`); membership unchanged.
- Both navs carry "primary navigation"? NO — two elements with the same aria-label is ambiguous; rail gets `aria-label="primary navigation"`, bottom bar gets `aria-label="primary navigation"` too since only one is visible per breakpoint — RTL tests render both; update ConsoleShell.test to `getAllByRole('navigation', {name:/primary navigation/i})` length 2, or give the bar "mobile navigation" and update its test. Choose the second (distinct names), update tests accordingly.

- [ ] Step 1: failing SmithRail tests — worker role sees HO/CLK/COM only; foreman sees MAP/JOB/CLI/INV/CRW too; admin sees ADM; logout clears authStore; active tab styled.
- [ ] Step 2: implement rail + shell + strip; migrate the shell-level `md:` toggles to `lg:`; update the two shell tests (breakpoint class + nav names + shift group still present).
- [ ] Step 3: full suite + build; commit `feat(portal): SmithRail shell - desktop Android navigation` + trailer.

### Task 2: Detail slides in beside lists (jobs, clients, invoices)

**Files:**
- Modify: `src/App.tsx` (nested routes: `jobs` becomes a layout route with `:id` child, same for `clients`, `invoices`), `routes/JobsListRoute.tsx`, `routes/ClientsListRoute.tsx`, `routes/InvoicesListRoute.tsx` (render `<Outlet/>` in a two-column `xl:` grid), `routes/JobDetailRoute.tsx`, `routes/ClientDetailRoute.tsx`, `routes/InvoiceDetailRoute.tsx` (own scroll region class; back-link `xl:hidden`)
- Test: extend the three list-route tests + three detail-route tests

**Interfaces:**
- Routing: `<Route path="jobs" element={<RequireForemanRole><JobsListRoute/></RequireForemanRole>}> <Route path=":id" element={<JobDetailRoute/>}/> </Route>` — JobsListRoute renders its list in the left column and `<Outlet/>` in the right panel slot. Same pattern ×3.
- List route layout: `xl:grid xl:grid-cols-[minmax(0,1fr)_420px] xl:gap-6` wrapper; the outlet panel `hidden xl:block xl:overflow-y-auto xl:max-h-full xl:border-l xl:border-sn-line xl:pl-6` — BUT below `xl`, when `:id` is active the DETAIL must replace the list (today's UX): implement with `useMatch`/`useParams` — if an id is matched AND viewport < xl, render only the Outlet (CSS-only approach: list block `hidden xl:block` when id active; detail block always rendered when id active). Spell it: wrapper renders `{!idActive || isXl ? list : null}` via CSS classes — `<div className={idActive ? 'hidden xl:block' : ''}>{list}</div><div className={idActive ? 'block' : 'hidden'}>{outlet}</div>` (pure CSS, no JS media queries; grid only applies at xl).
- Empty-panel state at `xl` with no id: the outlet column shows `EmptyState title="Select a job"` (etc.) — implement via an index child route or conditional.
- Detail components themselves unchanged except: root gets `xl:overflow-y-auto` compatibility (they inherit the panel's scroll), back-link `<Link to="/console/jobs">` gains `xl:hidden`.
- InvoiceDetail's unmount-clears-detail effect stays (fires on selection change — correct). Verify jobs/clients don't need the same (report).
- Deep link `/console/jobs/abc` at xl → list + panel both render (list mounts fresh, fine). At <xl → detail only, as today.

- [ ] Step 1: failing tests — at simulated xl (JSDOM can't media-query; assert CLASSES: list container carries `hidden xl:block` when id active, outlet panel present; empty-panel EmptyState renders on the list route with no id at the panel slot), detail back-link has `xl:hidden`, existing navigation tests still pass.
- [ ] Step 2: implement ×3 (jobs first, then replicate); full suite + build.
- [ ] Step 3: commit `feat(portal): detail panels slide in beside lists at desktop width` + trailer.

### Task 3: Gates + dark/manual pass

- Suite + build green; `node scripts/gen-tokens.mjs --check` (root); grep `md:hidden\|hidden md:` in layouts/ + ConsoleShell → zero (all moved to lg); zero `console-` (still); no new raw hex; ConsoleShell logged-out render sane (login page unaffected); manual checklist in report: rail in light+dark, tab activation per role, slide-in at 1280+, narrow behavior unchanged, keyboard traversal of the rail (focus rings).
- Fixes go in their owning task's scope. Report gate outputs verbatim. No commit unless fixes needed.

---

## Self-Review
- Variant-3 fidelity: slim rail ✓, role-adaptive ✓ (web model, gaps honest), slide-in ≥1280 ✓, <1024 bottom bar ✓, Android-identical narrow behavior ✓.
- The rail is a superset of the bottom bar (Map/Invoices/Crew) matching today's header nav — no capability lost when the header dies; ShiftClock/logout/role-chip relocated explicitly.
- Route tests untouched by design (isolation confirmed by scout); the two shell tests get named updates.
- Breakpoint blast radius bounded: only shell toggles migrate.
- No placeholders: component contracts, exact routing shape, CSS strategy for the sub-xl replacement behavior, and test assertions specified.
