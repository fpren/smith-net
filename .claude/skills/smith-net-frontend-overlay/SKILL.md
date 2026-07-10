---
name: smith-net-frontend-overlay
description: Smith Net web-portal overlay on top of the frontend-design foundation skill and smith-net-design-system. Constrains generic frontend practice to Design System v2 on /desktop/portal/ — sn-* tokens, dark toggle, SmithRail desktop-Android shell, slide-in detail panels, state trio, httpCall. Use for ANY UI work in /desktop/portal/. Overrides generic frontend-design defaults.
---

# Smith Net frontend overlay (web portal, Design System v2)

Layers on top of `frontend-design:frontend-design` and
`smith-net-design-system`. **Smith Net constraints WIN over generic defaults.**

## Override 1: Colors are sn-* tokens. Both themes, always.

Every color is an `sn-*` Tailwind class (`bg-sn-bg-panel`, `text-sn-ink`,
`border-sn-line`, `text-sn-attention`, `bg-sn-overlay`, `text-sn-ink-on-accent`,
`bg-sn-avatar-a1..a6`) or a `var(--sn-*)` in CSS/inline styles. The v1
`console-*` classes and `theme/consoleTheme.ts` are DELETED — anything
referencing them will not build. Dark works via `data-theme` on `<html>`
(themeStore; Settings → Appearance; pre-paint stamp in index.html): never write
light-assuming values (`bg-white`, `text-black`, warm rgba) — and never do hex
math on a token (use `color-mix(in srgb, var(--sn-x) N%, transparent)`).

## Override 2: Typography is Inter + JetBrains Mono + Syne

`font-sans` = Inter (body/UI), `font-data` = JetBrains Mono (data, timestamps,
ids, glyphs, microcopy — prefer it over the legacy `font-mono` alias in new
code), `font-display` = Syne (logotype). Fonts are self-hosted via fontsource
imports in `src/console/index.css`. The comm surface additionally has
`font-commsans`/`font-commmono` utilities (scoped to `.comm-surface`). Labels:
mono uppercase for section/data labels; sentence case for body copy.

## Override 3: The shell is a desktop Android (spec §8 as amended)

- `SmithRail` (64px icon rail with mono-abbreviation tab labels — HO/CLK/COM/
  MAP/…, NOT GLYPHS.md entries; `lg:`+) is the primary navigation —
  role-adaptive (worker: Home/Clock/Comm; foreman adds Map/Jobs/Clients/
  Invoices/Crew; admin adds Admin), gear + avatar + logout at the bottom,
  `aria-label` full names on abbreviated tabs.
- `TopStrip` = brand + role chip + ShiftClock. `BottomTabBar` (<`lg`) carries
  the mobile set + Settings (account access) + foreman Map.
- Jobs/clients/invoices are NESTED routes: at `xl` the detail renders in a
  420px panel BESIDE the list (`.panel-in` motion, keyed remount, independent
  scroll in BOTH states — never flip a column's scroll mode on selection);
  below `xl` detail replaces the list exactly like the phone. When the LIST
  itself is empty, suppress the panel's "select an item" EmptyState (the
  double-EmptyState guard — see `noJobsYet` in JobsListRoute.tsx); any new
  nested list/detail route needs the same guard.
- Shell breakpoints are `lg` (1024) and `xl` (1280); content-level `md:`
  utilities are unrelated — don't migrate them.

## Override 4: States and data fetching

- Every route renders `LoadingState/EmptyState/ErrorState` from
  `components/ui/StateViews` with precedence loading → error → empty → data.
- All API clients go through `console/api/httpCall.ts` (401 → single-flight
  refresh → retry once → one session-expired redirect). Never hand-roll fetch
  in a client; the offline outbox also routes through it.
- Pollers/hooks return `{ reload }` bound to the mount's cancellation (the
  `reloadRef` pattern in useJobsPolling) — retries must not write stale data
  after navigation. Stale flags are scoped per view (`listStale`/`detailStale`).

## Override 5: Components and dialogs

Shared primitives live in `components/ui/` and are already tokenized: Button,
Input, Card, Badge, Pill, Toast, Avatar, ProgressBar, SectionHeader,
StateViews, SmithDialog/ConfirmDialog (Modal is a thin alias, `size="lg"` for
forms). Destructive flows use ConfirmDialog (backdrop inert, Escape cancels,
cancel focused). `window.confirm` is extinct — never reintroduce it. Focus
convention: `focus-visible:outline focus-visible:outline-2
focus-visible:outline-sn-accent` on every interactive element.

## Override 6: Comm surface specifics

7-minute sender coalescing PLUS a new group on any calendar-day change
(`messageGrouping.ts`), LEFT-aligned rows only (own messages differ by name
color, never alignment), status microcopy PENDING/SENT/FAILED/SEEN (mono 10px
uppercase, own messages only, precedence failed > pending > seen > sent;
DELIVERED is reserved/never rendered), the mesh badge (inline span in
MessageRow.tsx — `MeshChip` is the ANDROID composable's name) only for
`origin === 'mesh' || 'gateway'`, amber unread grammar (bold name +
`bg-sn-attention` badge + frozen NEW divider + `↓ latest` pill). Optimistic
sends keep their client UUID through retry.

## Override 7: Ops surfaces (admin, map stats)

Flat (no radius/shadow), `font-data uppercase` table headers, `tabular-nums`
on every numeric cell, dense `px-2 py-1.5` cells. See smith-net-design-system
for the full mood rules.

## Override 8: Motion

200–250ms `cubic-bezier(.2,.8,.2,1)`; `.panel-in` for detail panels;
`prefers-reduced-motion` media block must disable every animation you add.
No spring/shimmer/confetti.

## Testing conventions

Vitest + RTL + MSW. MSW handlers must enforce the SAME validation as the real
backend (the attachment-only-message bug class: a mock looser than the route
makes green tests lie). Assert primitives by role (`status`/`alert`), not
implementation trivia. No test may pin a deleted class system.

## Don't do

(Inherits all `smith-net-design-system` don'ts.) Plus:
- ❌ `console-*` classes, `consoleTheme.ts`, hardcoded palette hex (all deleted)
- ❌ `prefers-color-scheme`-only theming (the user toggle must win — pattern: `:root:not([data-theme="light"])`)
- ❌ Hand-rolled fetch in api clients (httpCall only)
- ❌ Right-aligned own-messages, accent-colored unread, icon libraries on comm
- ❌ Shared stale flags across list/detail scopes
- ❌ New shell breakpoints other than lg/xl for nav concerns

## Linked specs

- Foundation: `frontend-design:frontend-design` skill
- `docs/superpowers/specs/2026-07-08-design-system-v2-design.md` (§8 amended)
- `.claude/skills/smith-net-design-system/SKILL.md` — the system itself
- `design/GLYPHS.md` — glyph registry
