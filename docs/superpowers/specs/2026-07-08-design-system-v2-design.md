# Smith Net Design System v2 — "Crew Soft / North Cobalt"

Date: 2026-07-08
Status: Approved direction (interactive brainstorm, four decision rounds + rendered
direction boards). Supersedes the light-mode-only / monospace-only / no-Material console
lock in `smith-net-design-system`, `smith-net-frontend-overlay`, and
`smith-net-android-overlay`. Those skills MUST be rewritten as part of this program
(workstream 7) so future sessions enforce v2, not v1.

Decision log (user choices, in order):
1. Theme strategy: light-first, dark theme ships day one.
2. Brand DNA: evolve — keep glyph language and mono-for-data; modernize everything else.
3. Scope: everything at once — both platforms, all screens, one program.
4. Chat glyph language (`● ○ [▣] [▶] > ← ↵ ▾`): KEEP, codify, typeset better. Never replace.
5. Shape direction: Crew Soft (bubbles, soft shadows, 20px radii) for user surfaces;
   Terminal Grade (mono, square, dense) for operator surfaces.
6. Palette: North Cobalt. Brown/parchment/gold family fully retired.
   (Blueprint and Site Steel variants were rendered and considered; user chose Cobalt.)

---

## 1. Token architecture

Single source of truth: `design/tokens.json` at repo root.

Generators (run in each platform's build or as a checked-in codegen step):
- Web: emits a Tailwind preset (`desktop/portal/tailwind.tokens.js`).
- Android: emits `ConsoleTheme2.kt` (object with Color/Dp/TextStyle constants).

Nobody hand-types a hex, radius, or duration in app code. CI greps for raw hex in UI
source as a guard (allowlist: tokens files, tests).

Two independent axes:
- THEME (light | dark): changes color values ONLY.
- MOOD (crew | ops): changes shape/density ONLY — radius, font role for labels,
  spacing multiplier, casing. Colors are identical across moods.

## 2. Color tokens (North Cobalt)

Light:
| token        | value    | role                                   |
|--------------|----------|----------------------------------------|
| bg.base      | #F7F8FA  | app background                         |
| bg.panel     | #FFFFFF  | cards, bubbles, composer, sheets       |
| bg.sunken    | #EEF1F5  | rails, sidebars, wells                 |
| line         | #E2E6EC  | hairline borders, dividers             |
| ink          | #1C2128  | primary text                           |
| ink.muted    | #7A8290  | secondary text, timestamps             |
| accent       | #2F5FE8  | cobalt — brand + actions               |
| attention    | #E8590C  | amber — unread, NEW divider, failed    |
| status.online| #3E9B4F  | presence                               |
| status.error | #D64545  | destructive, errors                    |

Dark:
| token        | value    |
|--------------|----------|
| bg.base      | #14171C  |
| bg.panel     | #1D2129  |
| bg.sunken    | #0E1013  |
| line         | #2B303A  |
| ink          | #E9ECF1  |
| ink.muted    | #8A93A3  |
| accent       | #6B8CFF  |
| attention    | #FF8A3D  |
| status.online| #63C76F  |
| status.error | #FF6B6B  |

Accent discipline (hard rule): cobalt acts (buttons, links, active states, mesh chip,
own-avatar), amber warns/attends (unread badge, NEW divider, FAILED status, offline
banner accent). Never swap jobs. Semantic status colors are not accents.

Shadows (crew mood only): `shadow.sm` 0 1px 3px / `shadow.md` 0 2px 8px, color derived
from ink at 10% (light) / black at 45% (dark). Ops mood: no shadows, ever.

## 3. Typography

Three voices, both platforms:
- Syne — logotype + hero display only. Never body.
- Inter — ALL UI and body text. Replaces IBM Plex Sans (Android) and Public Sans (web
  comm exception). Self-hosted woff2 on web; bundled font resource on Android.
- JetBrains Mono — everything data-shaped: timestamps, money, IDs, mesh hashes, clock,
  status microcopy, and ALL glyphs.

Scale (sp/px): 12, 13, 14 (body), 16, 20, 24, 30. Tabular numerals wherever digits align.
Uppercase labels (ops mood + status microcopy) get +0.08em tracking.

## 4. Glyph registry

New file: `design/GLYPHS.md`. Canonical set (unchanged vocabulary):
`●` online · `○` offline/away · `[▣]` photo · `[▶]` voice/play · `[≡]` file ·
`>` composer prompt · `←` back · `↵` send · `▾` disclosure

Rules:
- Glyphs render ONLY in JetBrains Mono, inside a fixed-width cell (1.3em), baseline-aligned.
- Presence dots also appear as avatar-corner dots (2px ring in surface color).
- New iconography must be added to the registry first; no icon libraries on the comm
  surface. Other surfaces may use a single line-icon set (Lucide) where a glyph does not
  exist, at 1.5px stroke, ink.muted default.
- Each registry entry: glyph, meaning, allowed contexts, never-use rules.

## 5. Mood assignment

Crew (soft): comm/chat, dashboard, jobs, invoices, expenses, clients, supply, time
tracking, reports (worker view), settings, profile, onboarding, auth.
Ops (terminal): admin health, dispatch, crew map overlays, plan/proposals.
Rule: if a laborer sees it daily it is crew; if a foreman runs the job from it, ops.

Crew shape: 20px cards/sheets, 14px bubbles, pill buttons/inputs/composer, shadow.sm/md.
Ops shape: 0 radius, hairline + 1px structural rules, mono labels uppercase, spacing
multiplier 0.75, no shadows, denser tables with tabular numerals.

## 6. Component set (shared names, both platforms)

SmithButton (cobalt fill / outline / ghost; pill in crew, square in ops)
SmithInput (pill; mono for data fields)
SmithCard, SmithSheet (Android bottom sheet = web slide-over), SmithDialog
  - SmithDialog replaces ALL 21 Material AlertDialog usages on Android (v1 violation).
  - Destructive confirmations: no outside-tap dismiss, explicit cancel.
SmithAvatar (initials fallback, corner presence dot)
MessageRow (first-of-group / grouped variants), Composer, UnreadBadge, NewDivider,
MeshChip, OfflineBanner, EmptyState, LoadingState, ErrorState, Toast,
LockedFeatureOverlay (tier gates: renders on structured 403).

## 7. Messaging mechanics (the Discord set, Crew Soft skin)

- Coalescing: same sender within 7 minutes groups under one avatar/header. Messages stay
  LEFT-ALIGNED grouped rows with soft bubble surfaces — no SMS right-alignment; grouping
  and density win.
- Unread grammar: bold channel name + amber count badge; amber NEW divider at first
  unread; jump-to-latest pill when scrolled up.
- Actions: hover toolbar (web) = long-press SmithSheet (Android). Same actions, same
  order: copy, reply (post-v2), delete, retry (failed only).
- Status microcopy (mono, 9-10px): PENDING / SENT / DELIVERED / FAILED / SEEN.
  FAILED renders attention-amber with tap-to-retry.
- Parity fixes in scope: Android RENDERS read receipts + typing (already collected);
  web composer gets WORKING attachments ([+] wired to /api/media).
- Transport: MeshChip (cobalt outline) on mesh-delivered messages; ONLINE/MESH/OFFLINE
  vocabulary identical on both platforms.

## 8. Layout

Web shell (AMENDED 2026-07-08, decision by Fegens — supersedes the original 3-zone plan):
the web app is a DESKTOP VERSION OF THE ANDROID APP. Same screens, same role-adaptive
tab set, same IA:
- Android's bottom-nav tabs become a slim 64px glyph rail on the left (logo top,
  avatar+presence bottom). Tab set stays role-adaptive, identical to Android.
- Screens are the Android screens widened (dashboard modules grid, jobboard rows,
  comm) — no web-only surfaces.
- >=1280px only: opening a job/invoice/client detail slides a panel in BESIDE the
  list (does not cover it). Narrower windows behave exactly like Android
  (full-screen detail). This slide-in panel is the one desktop-only behavior.
- <1024px: single pane; the rail collapses to Android's bottom tab bar.
Android: role-adaptive bottom nav (4-5 tabs) stays; SmithSheets replace hover paradigms;
predictive back; no drawers; no desktop sidebars.
Conversation line length target: 65-75ch on wide screens.

## 9. Motion & states

- 200-250ms, cubic-bezier(.2,.8,.2,1) everywhere; existing commBubble entrance kept.
- prefers-reduced-motion respected (web) / animator duration scale (Android).
- EVERY screen ships EmptyState + LoadingState + ErrorState from the shared primitives.
  Closes the 17%-state-coverage audit finding as a build rule: PR checklist item.
- 401: silent token refresh, then redirect to login with toast. Both platforms.

## 10. Migration order (one program, seven workstreams)

1. tokens.json + both generators; same commit fixes web index.html dark-#0a0a0a bug and
   PWA manifest colors.
2. Font pipeline: Inter + JetBrains Mono + Syne on both platforms.
3. Core components (both platforms), including the AlertDialog purge on Android.
4. Comm surfaces: mechanics (7) + parity fixes.
5. All remaining screens swept to v2, state-trio enforced per screen.
6. Ops mood applied to admin/dispatch/map/plan.
7. Rewrite skills: smith-net-design-system, smith-net-frontend-overlay,
   smith-net-android-overlay + CLAUDE.md conventions section. v2 becomes law.

Verification per workstream: portal vitest + screenshot review; Android build + Maestro
solo-E2E tags; manual light/dark sweep on both platforms.

## 11. Out of scope (tracked separately — launch blockers, not design)

Payments/Stripe, auth unification (Supabase vs backend JWT), FCM push, crash reporting,
public domain/ingress, legal pages. The v2 program must not block these; workstream 1-2
can land while those proceed.

## 12. Success criteria

- Zero raw hex in UI code outside tokens files (CI grep green).
- Both platforms render identical token values (spot-check script comparing generated
  outputs to tokens.json).
- Android: 0 Material AlertDialog imports; 0 MaterialTheme wrappers.
- Web: no #0a0a0a body; lighthouse a11y pass on contrast; focus ring on every
  interactive element.
- Chat: grouped messages, unread badge + NEW divider + jump pill, receipts/typing
  visible on Android, attachments working on web — verified in both themes.
