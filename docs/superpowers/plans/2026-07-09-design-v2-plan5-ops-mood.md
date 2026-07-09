# Design System v2 — Plan 5: Ops Mood (Terminal Grade) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The ops surfaces (dispatch, plan/proposals, map overlays, admin health) get the spec-§5 Terminal Grade treatment — 0 radius, 1px hairlines, mono uppercase labels, 0.75 spacing, tabular numerals — the Android ops files finish their color tokenization (closing the dark-mode patchwork), customer documents rebrand to North Cobalt (Fegens' decision 2026-07-09), the carried-over polish list lands, and ConsoleTheme's v1 color palette is deleted once its last consumers are gone.

**Architecture:** Ops-mood affordances first (wire the dead `Tokens2.RadiusOps`, emit a web `rounded-sn-ops`, add `shape`/`ops` parameters to SmithButton/SmithDialog, add tabular-numeral helpers on both platforms). Then the Android ops sweep (7 files: colors + shape + density + tnum, Maestro-guarded), the small web ops polish, the document CSS rebrand, and two polish batches. The finale deletes ConsoleTheme's color definitions — after this plan they have zero consumers — making v2 the only palette in the codebase.

**Tech Stack:** as Plans 4A-4C. Android gradle needs `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`. Portal from `desktop/portal`; generator from repo root.

## Global Constraints

- Ops shape rules (spec §5): 0 radius (`Tokens2.RadiusOps` / `rounded-sn-ops`), structural rules = **1.dp/1px** hairlines in `colors.line` (the current 0.5dp alpha-ink borders upgrade), NO shadows (already true), mono uppercase labels (`SmithType.caption`/`captionBold` + jetBrainsMono / web `font-data uppercase`), spacing ×0.75 on STRUCTURAL padding (16→12, 12→9, 8→6; content-internal 4dp/2dp stay), tabular numerals on every numeric readout.
- Colors ONLY via LocalSmithColors/sn tokens; the dispatch amber `Color(0xFFD97706)` → `colors.attention`. Job-preserving mapping per the Plan 4B table.
- MAESTRO GUARD (yaml pins touching this plan's files): `"PROPOSALS"` + `"[+] NEW PROPOSAL"` (PlanScreen), `"NEW PROPOSAL"`/`"PROPOSE"`/`"CONFIRM"`/`"CREATE JOB"` + `solo_e2e_intent_*` testTags (IntentComponents). NO visible-string or tag changes in those files. ProposalPreviewDialog/dispatch/map carry no pins (verified) — style freely.
- theme2 components stay backward compatible: new params default to current behavior (crew screens unaffected).
- No emoji; commit style + trailer `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`. Suites + builds green before every commit; generated files only via the generator.

---

### Task 1: Ops affordances (both platforms)

**Files:**
- Modify: `scripts/gen-tokens.mjs` (emit `'sn-ops': '${t.radius.ops}px'` in the preset borderRadius — tokens.json already has `radius.ops: 0`), regenerate outputs
- Modify: `android/.../ui/theme2/SmithButton.kt` (add `shape: Shape = RoundedCornerShape(999.dp)` param — ops call sites pass `RoundedCornerShape(Tokens2.RadiusOps)`), `ui/theme2/SmithDialog.kt` (add `ops: Boolean = false`: when true the panel uses `RoundedCornerShape(Tokens2.RadiusOps)` and the title style is `SmithType.captionBold` uppercase mono instead of inter)
- Create: `android/.../ui/theme2/SmithTabular.kt` — `val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")` (top-level extension; one-line file with doc)
- Test: extend `desktop/portal/src/__tests__/tokens.test.ts` (preset contains `sn-ops`); extend `SmithTypeTest.kt` or new `SmithTabularTest.kt` (`SmithType.caption.tabular.fontFeatureSettings == "tnum"`, original style untouched)

- [ ] TDD both platform tests; implement; `node scripts/gen-tokens.mjs` + `--check`; portal suite + android compile/tests green.
- [ ] Commit `feat(design): ops-mood affordances - zero radius wired, tabular numerals, ops dialog/button variants` + trailer.

### Task 2: Android ops sweep (7 files)

**Files:** `ui/dispatch/DispatchScreen.kt`, `ui/plan/PlanScreen.kt`, `ui/plan/IntentComponents.kt`, `ui/proposal/ProposalPreviewDialog.kt`, `ui/map/MapScreen.kt`, `ui/map/MapPanels.kt`, `ui/map/LostAndFoundScreen.kt`

Per file: (1) colors → LocalSmithColors/SmithType per the 4B mechanics (`val colors = LocalSmithColors.current` after the param list; explicit color with SmithType); dispatch's 4× amber hex → `colors.attention`. (2) Shape: every `RoundedCornerShape(4.dp)` → `RoundedCornerShape(Tokens2.RadiusOps)`; every `0.5.dp` structural border → `1.dp` in `colors.line` (chips/tints keep their alpha washes but on tokens). (3) Density: structural paddings ×0.75 per the constraint (report each file's before/after dominant scale). (4) Tabular numerals: `.tabular` on stat/count/duration Text styles (dispatch stats, map panel counts, proposal totals). (5) Dialog/button call sites in PlanScreen/IntentComponents/ProposalPreviewDialog pass `ops = true` / square shape.
Gates: color-grep zero + `Color(0x` zero across the 7; `grep -rn "ConsoleTheme\.\(background\|surface\|text\b\|textSecondary\|textMuted\|textDim\|accent\b\|accentDim\|success\|warning\|error\|separator\)" app/src/main` app-WIDE list shrinks to ONLY ConsoleTheme.kt definitions (report); compile + tests; Maestro diff empty + pinned strings/tags byte-identical.
- [ ] Commit `feat(android): ops surfaces on Terminal Grade - dispatch, plan, proposals, map` + trailer.

### Task 3: Web ops polish

**Files:** `routes/AdminRoute.tsx` (count/age columns get `font-data tabular-nums`; cells densify `px-3 py-2`→`px-2 py-1.5`; table headers `font-data uppercase text-[10px] tracking-wide`), `components/map/StatsStrip.tsx` (`tabular-nums` on the counts). Tests: extend AdminRoute test (a header carries the mono-uppercase classes; a count cell carries tabular-nums).
- [ ] TDD; suite + build; commit `feat(portal): admin + map stats on Terminal Grade numerals and density` + trailer.

### Task 4: Customer documents → North Cobalt

**Files:** `android/.../ui/expenses/InvoiceBolHtmlRenderer.kt` — the `:root` token block (~line 477): parchment/gold → North Cobalt (`--bg:#F7F8FA; --surface:#FFFFFF; --ink:#1C2128; --muted:#7A8290; --rule:#E2E6EC; --accent:#2F5FE8; --accent-soft:#EEF1F5; --success:#3E9B4F; --warn:#E8590C; --danger:#D64545`); the three off-token pastel cards (~:517, :536-538) re-derive from the vars (`color-mix` or the soft tokens) instead of hardcoded warm hues; radii MAY stay ≤4px (customer paper, not an app surface — document the choice); fonts already Inter/mono.
Constraint: this is a CSS-string edit — zero Kotlin logic changes. If a unit test snapshots the HTML, update it deliberately.
- [ ] Compile + tests; commit `feat(android): invoice and BOL documents rebranded to North Cobalt` + trailer.

### Task 5: Web polish batch (Plan 4C carry-overs)

**Files:** list routes ×3 (panel slide-in motion: the panel content wrapper gets `xl:animate-[panelIn_.22s_cubic-bezier(.2,.8,.2,1)]` keyed on the id so selection changes animate — define `@keyframes panelIn { from { opacity:0; transform:translateX(12px) } }` in index.css with a `prefers-reduced-motion` guard; ALSO suppress the Select-a-X EmptyState when the list itself is empty (no double-EmptyState)); `layouts/SmithRail.tsx` (`font-mono`→`font-data`); `components/adaptive-home/cards.tsx` (a Crew entry point: the crew-presence card links to `/console/crew` — verify it exists; if no crew card exists add a small link row to the dashboard grid, foreman-gated); `components/comm/DialRail.tsx` mount in CommRoute gated `hidden xl:block` (relieves the 1024 squeeze).
Tests: double-EmptyState case (empty list + no id → only the list's own EmptyState); reduced-motion guard present in css; Crew link href.
- [ ] TDD; suite + build; commit `feat(portal): panel motion, comm width relief, crew entry, polish` + trailer.

### Task 6: Android polish + v1 palette deletion

**Files:** `ui/SettingsScreen.kt` (AppearanceSection gets the sibling bgPanel card wrap), `ui/comm/QrCodes.kt` (module ints → Tokens2 ink/bgBase values — verify scannability contrast stays ~21:1; report), `desktop/portal/.../BottomTabBar.test.tsx` (stale 'behind the gear' comment wording — yes it's a web file, it rides in this polish task), THEN the closure: `ui/ConsoleTheme.kt` — after Task 2, grep app-wide for every ConsoleTheme COLOR property; if zero consumers remain outside the definitions, DELETE the color properties (background/surface/text*/accent*/success/warning/error/separator*/sentLine/cursor/receivedPrefix/sentPrefix + the four comm prefix colors) from the object, keeping fonts + text styles + BottomNavBar/ConsoleHeader/ConsoleSeparator composables + string constants. Text styles that baked deleted colors must already be color-free at call sites (they are — every call site passes explicit color since 4B; the STYLE definitions themselves still name colors — strip the `color =` from each TextStyle definition so they become colorless like SmithType, and verify no call site relied on the baked color: `grep` for `style = ConsoleTheme\.` WITHOUT an explicit color in the same call across app/src — sample and fix stragglers or report).
- [ ] Compile + full tests; gates: `grep -rn "0xFF9A6F2E\|0xFFF4F2EE\|0xFF5A8C76\|0xFF8C3A3A\|0xFF8C5A2E" app/src/main` → only historical comments if any; commit `feat(android): v1 palette deleted - v2 is the only color system` + trailer.

### Task 7: Whole-plan gates
- Portal suite + build; android compile + full tests; `gen-tokens --check`; Maestro diff vs master empty; app-wide ConsoleTheme color-consumer grep → zero; report all outputs verbatim. Dark-mode note in the report: with ops swept, the dark patchwork is CLOSED — remaining release gate is device QA only.

---

## Self-Review
- Spec §5 coverage: shape/hairline/mono/density/tnum per surface; ops dialogs/buttons square via new params (crew unaffected by defaults); admin/map web already-flat surfaces get the numeral+density delta only.
- Maestro: the two pinned ops files called out with exact strings/tags; ProposalPreviewDialog verified pin-free; `[OK] CREATE`/`CREATE PROPOSAL` confirmed OUTSIDE this plan's files.
- The palette deletion is gated on the app-wide consumer grep inside Task 6, not assumed.
- Docs rebrand honors the user's explicit choice; radii-stay-on-paper documented as a decision.
- Type consistency: `.tabular` (T1) consumed T2/T3-android-side; `ops`/`shape` params (T1) consumed T2; `rounded-sn-ops` available to web (T3 uses density/numerals; radius already flat there).
