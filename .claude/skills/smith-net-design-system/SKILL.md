---
name: smith-net-design-system
description: UI design conventions for Smith Net (both platforms) — Design System v2 "Crew Soft / North Cobalt". Tokens-only colors from design/tokens.json (LocalSmithColors on Android, sn-* classes on web), light-first WITH a user dark toggle, Inter + JetBrains Mono + Syne typography, crew vs ops moods, Smith* components instead of Material widgets, glyphs per design/GLYPHS.md. Use when writing or modifying ANY UI in /android/ or /desktop/portal/, adding screens or components, or making styling decisions.
---

# Smith Net — Design System v2 (Crew Soft / North Cobalt)

This skill activates for any UI work in this project. **v2 is law** (the v1
light-only/monospace lock is repealed — spec:
`docs/superpowers/specs/2026-07-08-design-system-v2-design.md`).

## One token source. Never type a color.

`design/tokens.json` is the single source of truth. `node scripts/gen-tokens.mjs`
(repo root) generates:

- `desktop/portal/src/styles/tokens.css` — `--sn-*` CSS vars (light + `[data-theme="dark"]` + `prefers-color-scheme` fallback)
- `desktop/portal/tailwind.tokens.cjs` — `sn-*` Tailwind classes
- `android/.../ui/Tokens2.kt` — `Tokens2.Light/Dark` + `AvatarPalette` + radii + durations

`gen-tokens.mjs --check` gates CI. **Never hand-edit generated files. Never
hardcode hex in UI code** — edit tokens.json and regenerate. Components must
never do hex math on token values (use `color-mix()` on web — see
Avatar.tsx/Chip.tsx precedent).

## The palette (North Cobalt) — by JOB, not by hue

| Token | Light | Dark | Job |
|---|---|---|---|
| bgBase | #F7F8FA | #14171C | page background |
| bgPanel | #FFFFFF | #1D2129 | cards, rows, sheets |
| bgSunken | #EEF1F5 | #0E1013 | wells, message bubbles |
| line | #E2E6EC | #2B303A | hairlines, borders |
| ink | #1C2128 | #E9ECF1 | body text |
| inkMuted | #7A8290 | #8A93A3 | secondary text |
| accent | #2F5FE8 | #6B8CFF | **acts** — primary actions, links, selection |
| attention | #E8590C | #FF8A3D | **warns/attends** — unread badges, NEW divider, warnings, FAILED |
| statusOnline | #3E9B4F | #63C76F | online, success, in-progress |
| statusError | #D64545 | #FF6B6B | destructive, danger, errors |
| overlay | #00000066 | | scrims |
| inkOnAccent | #FFFFFF | | text on accent/danger fills |
| avatar a1-a6 | theme-invariant | | deterministic identity colors (hash by id/name) |

**Accent discipline: cobalt acts, amber warns, statusError destroys. Never swap
jobs.** Unread is ALWAYS amber, never accent.

## Dark mode is real. Both platforms.

- Web: `themeStore` stamps `data-theme` (persisted `localStorage['sn-theme']`;
  pre-paint stamp in index.html). Toggle lives in Settings → Appearance.
- Android: `SmithTheme` is mounted at the app root (MainActivity) with
  `darkEnabled = true`; the preference lives in `UserPreferences`
  (`ThemePreference LIGHT/DARK/SYSTEM`); ONE `resolveDark()` result feeds both
  the palette and the status bar (pass `resolvedDark` — never resolve twice).
- Every new surface must be dark-correct by construction: tokens only, no
  light-assuming rgba/white/black. Exception: QR codes are deliberately
  light-fixed for scannability (documented in QrCodes.kt).

## Typography

- **Inter** — all UI text (Android: `ConsoleTheme.inter` with FontVariation
  weight pinning; web: `font-sans`).
- **JetBrains Mono** — data, timestamps, ids, glyphs, microcopy (web:
  `font-data`; Android: `ConsoleTheme.jetBrainsMono`).
- **Syne** — display/logotype only (`font-display`).
- Android text styles come from **`SmithType`** (theme2) — they are COLORLESS;
  always pass `color = colors.X` explicitly. `ConsoleTheme` retains ONLY font
  families, string constants, and three shell composables — its color palette
  is deleted.
- Numeric readouts (counts, money, durations) get tabular numerals:
  `SmithType.x.tabular` (Android) / `tabular-nums` (web).

## Two moods: crew (soft) and ops (terminal)

Rule: if a laborer sees it daily it is **crew**; if a foreman runs the job from
it, **ops**. Mood = shape/density. Theme (colors) is the same for both.

**Crew** (comm, dashboard, jobs, invoices, expenses, clients, supply, time,
settings, profile, onboarding, auth): 20dp/px cards and sheets
(`Tokens2.RadiusCard` / `rounded-sn-card`), 14 bubbles (`RadiusBubble` /
`rounded-sn-bubble`), pill buttons/inputs (`999` / `rounded-sn-input`),
`shadow-sn-sm/md` on web.

**Ops** (dispatch, plan/proposals, map overlays, admin health): **0 radius**
(`Tokens2.RadiusOps` / `rounded-sn-ops`), 1dp/1px `line` hairlines, mono
UPPERCASE labels, ~0.75 spacing density, NO shadows, tabular numerals on every
number. Android ops call sites use `SmithDialog(ops = true)` and
`SmithButton(shape = RoundedCornerShape(Tokens2.RadiusOps))`.

## Components — Smith*, never Material

| Need | Android | Web |
|---|---|---|
| Button | `SmithButton` (Primary/Ghost/Danger, `shape` for ops) | `Button` (ui/, sn tokens) |
| Dialog | `SmithDialog` (`destructive`, `ops`, `sizeFraction`) | `SmithDialog` (`size 'md'/'lg'`) |
| Confirm | `SmithConfirmDialog` (`confirmIsDanger`, `confirmEnabled` in-flight guard) | `ConfirmDialog` |
| Bottom sheet | `SmithSheet` | slide-over / dialog per context |
| States | `SmithLoadingState/SmithEmptyState/SmithErrorState` | `LoadingState/EmptyState/ErrorState` (ui/StateViews) |
| Avatar | `SmithAvatar` (AvatarPalette hash, presence dot) | `Avatar` (avatar tokens, statusColor ring) |

Allowed Material on Android: `material3.Text` (text primitive) and
`CircularProgressIndicator` inside SmithLoadingState only. Everything else
Material (AlertDialog, ModalBottomSheet, Button, TextField widgets, MaterialTheme
color reads) is banned in new code — the purge is complete; don't regress it.

## Every screen ships the state trio

LoadingState while the primary fetch is in flight, EmptyState when the primary
collection is empty, ErrorState (+retry where a reload exists) on failure —
precedence **loading → error → empty → data**. Wire retry to a real reload
(hooks return `{ reload }` bound to the mount's cancellation on web). Never
share a stale flag across list/detail scopes (the false-error-flash bug class —
stores use `listStale`/`detailStale`). Empty states are text only — no
illustrations, no emoji, no forced cheer.

## Glyphs per `design/GLYPHS.md` only

`●` online · `○` offline · `[▣]` photo · `[▶]` voice/play · `[≡]` file ·
`>` prompt · `←` back · `↵` send · `▾` disclosure · `↓` jump-to-latest (comm
pill only). Glyphs render in JetBrains Mono. New iconography goes into the
registry FIRST. No icon libraries on comm surfaces; elsewhere a single
line-icon set (Lucide, 1.5px stroke, inkMuted) is permitted where no glyph
exists. ASCII tokens (`[x]`, `[+]`, `[>]`) are fine. **No emoji anywhere.**

## Motion (spec §9)

200–250ms, `cubic-bezier(.2,.8,.2,1)`. Web slide-in panels use `.panel-in`
(220ms, keyed remount). `prefers-reduced-motion` must disable animations (web
media block; Android animator scale). The comm `commBubble` entrance is kept.
No spring physics, no shimmer, no confetti.

## Destructive confirmations

No outside-tap dismiss; explicit cancel button; Escape/back = cancel is
allowed; cancel gets initial focus (web) / Ghost variant (Android); confirm is
`statusError` only when genuinely destructive (`confirmIsDanger = false` for
status changes). Disable confirm while the operation is in flight
(`confirmEnabled = !inFlight`).

## Gates that must never regress

- Tier gates SHOW + LOCK (dimmed + upgrade CTA); role gates HIDE. Unchanged from v1.
- Maestro e2e (`android/maestro/smithnet_solo_e2e.yaml`) pins visible strings
  (bracketed nav labels `[Home]`/`[Clients]`/`[Plan]`, `[▶] LOGIN`, flow copy)
  and `solo_e2e_*` testTags — **grep the yaml before renaming ANY user-visible
  string or tag.**
- CI greps: zero raw hex outside token files; zero `console-*`; zero Material
  AlertDialog/ModalBottomSheet; `gen-tokens --check`.

## Don't do

- ❌ Hardcode hex / `Color(0x...)` / named colors in UI code (tokens only)
- ❌ Hand-edit tokens.css / tailwind.tokens.cjs / Tokens2.kt (generated)
- ❌ Read `MaterialTheme.colorScheme` or add Material widgets (Android)
- ❌ Use `console-*` classes or the deleted ConsoleTheme colors (they no longer exist)
- ❌ Give amber an "acts" job or accent a "warns" job
- ❌ Ship a screen without the state trio
- ❌ Add emoji, icon libraries on comm, or unregistered glyphs
- ❌ Rename Maestro-pinned strings or `solo_e2e_*` tags
- ❌ Resolve dark twice (Android) or read theme outside themeStore/SmithTheme
- ❌ Animations >250ms, spring physics, shimmer
- ❌ Communicate state by color alone (always pair with text)

## Linked specs

- `docs/superpowers/specs/2026-07-08-design-system-v2-design.md` — THE spec (amended §8: web shell = desktop Android)
- `design/tokens.json` + `design/GLYPHS.md` — tokens and glyph registry
- `.claude/skills/smith-net-frontend-overlay/SKILL.md` — web specifics
- `.claude/skills/smith-net-android-overlay/SKILL.md` — Android specifics
