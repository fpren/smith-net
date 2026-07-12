# Modern-Look Fixes (Android) — Design

**Date:** 2026-07-11
**Status:** Approved direction (Fegens, 2026-07-11); spec pending review
**Parent spec:** `2026-07-08-design-system-v2-design.md` (v2 is law; this program closes the
"not modern" audit gaps reported 2026-07-10)
**Scope:** Android app only. Web/portal, MaterialTheme-wrapper removal (spec §12), the
JobStatus enum mapping bug, and the remaining follow-up ticket backlog are OUT of scope.

## Problem

Design System v2 shipped its color/dark system, but five things still make the Android app
read dated (2026-07-10 audit, confirmed against code 2026-07-11):

1. **Fonts:** all 50 `SmithType`-consuming files still render IBM Plex — `SmithType` styles
   point at `ConsoleTheme.plexSans`/`plexMono`. Inter is wired into only 4 files.
2. **Crew Soft never applied:** `Tokens2.RadiusCard` (20dp) has zero usages; 315 literal
   `RoundedCornerShape` calls (196 of them `4.dp`); soft shadows in only 3 files.
3. **AuthScreen:** ASCII box banner, `> email:` prompts, hand-rolled inputs/buttons, and
   emoji (`🔑`, `⚠`) that violate the no-emoji rule (`📨` in InviteBanner too).
4. **Map:** MAPNIK light tiles render unconditionally (bright rectangle in dark mode);
   stock osmdroid pins.
5. **Material widget debt:** `Button`/`TextField`/`DropdownMenu` survive in 4 files.

## Decisions (locked with Fegens 2026-07-11)

- **Fonts — full spec flip.** Plex Sans → Inter, Plex Mono → JetBrains Mono across
  `SmithType`. Syne stays display-only. Comm styles (Public Sans + JB Mono, Plan 3)
  untouched.
- **AuthScreen — Crew Soft rebuild.** Login is a user surface; it gets the Crew Soft
  treatment. Terminal identity retired from auth.
- **Map — invert-filter tiles.** ColorMatrix dark filter on the MAPNIK tiles overlay in
  dark theme (no new tile provider), plus custom North Cobalt pins in both themes.
- **Radii — role-based mapping.** Cards/dialogs/sheets → `RadiusCard`; chips/badges →
  pill; inputs/small controls → new `RadiusControl` token (10); ops surfaces stay
  `RadiusOps` (0). Every literal becomes a token.
- **Sequencing — four staged plans (M1–M4)**, each an independently mergeable branch,
  same playbook as v2 Plans 1–6.

## Plan M1 — Font flip + emoji purge

**Flip site:** `ui/theme2/SmithType.kt` (18 styles). `body`/`bodyBold`/`bodySmall` →
`ConsoleTheme.inter`; `version`/`caption`/`prefix`/`prompt`/`action`/`timestamp` →
`ConsoleTheme.jetBrainsMono`; `brand`/`title`/`header` stay `syne`; `commName`/`commBody`
(Public Sans) and `commId`/`commTimestamp`/`dialpad` (JB Mono) unchanged.

**Weight check first:** `ConsoleTheme.inter` is declared from the single variable font
`res/font/inter_variable.ttf`. Verify Medium/SemiBold resolve correctly on-device; if the
variable font renders Regular-only, add explicit `Font(R.font.inter_variable, weight = ...)`
entries (or static weight files) before flipping.

**Stragglers:** audit the 9 files that reference `ConsoleTheme.*` fonts directly and remap
`plexSans` → `inter`, `plexMono` → `jetBrainsMono`. `InviteBanner.kt`'s inline
`FontFamily.Monospace` is fixed in M2 with its Material migration, not here.

**Emoji purge (no-emoji rule):**
- `AuthScreen.kt`: `🔑 RESET PASSWORD` → `[↻] RESET PASSWORD`; `⚠ OFFLINE MODE` →
  `[!] OFFLINE MODE` (both tokens already in the screen's existing ASCII vocabulary).
  Full visual rebuild waits for M3 — M1 only swaps the emoji characters.
- `InviteBanner.kt`: `📨 Channel Invite` → `[+] Channel Invite` (`[✉]` U+2709 rejected —
  it has an emoji-presentation variant on some devices, the same trap as `⚠`). Extend the
  existing `[+]` add/attach affordance in `design/GLYPHS.md` with the invite-banner context
  FIRST per the registry rule.

**Burn the boats:** when refs hit zero, delete `plexSans`/`plexMono` from `ConsoleTheme.kt`
and the 5 `ibm_plex_*` files from `res/font/`. No CI grep gate exists, so removal is the
regression guard. Manual gate before merge: `grep -ri "plex" android/app/src/main` → zero
hits.

## Plan M2 — Crew Soft surfaces + Material debt

**Tokens:** add `radius.control = 10` and `radius.pill = 999` to `design/tokens.json`; run
`node scripts/gen-tokens.mjs` from repo root (regenerates `Tokens2.kt` + web `tokens.css`/
`tailwind.tokens.cjs` — web side effect is inert). Never hand-edit generated files.

**SmithCard:** new `theme2/SmithCard.kt` composable = surface color + `RadiusCard` shape +
soft shadow (spec's crew shadow) in one place. Crew cards/sheet-like containers sweep onto
it so radius/shadow drift can't recur.

**Role-based radius sweep** of all 315 literal `RoundedCornerShape` call sites:

| Role (crew surfaces) | Token |
|---|---|
| Cards, dialogs, sheets, panels | `RadiusCard` (20) — via `SmithCard` where practical |
| Message bubbles | `RadiusBubble` (14) — already correct where used |
| Chips, badges, status pills, avatars-as-pills | `RadiusPill` (999) |
| Inputs, small buttons, inline controls | `RadiusControl` (10) |
| Anything on an ops screen (dispatch/plan/proposals/map/admin) | `RadiusOps` (0) |

Judgment call per site; when a 4dp corner sits on an ops surface it goes to 0, not 10.
Verification grep before merge: `RoundedCornerShape\([0-9]` → zero literal-dp hits in crew
code (999-as-pill literals also replaced).

**SmithTextField:** new `theme2/SmithTextField.kt` on `BasicTextField` — tokens-only colors,
`RadiusControl` shape, focus-visible ring, label/placeholder/error slots, `ops = true`
variant (0 radius, mono-upper label) for Terminal Grade forms.

**Material debt migration (4 files, widgets → theme2):**
- `ui/InviteBanner.kt`: `OutlinedButton`/`Button` → `SmithButton` (Ghost/Primary); inline
  `FontFamily.Monospace` → `SmithType`.
- `ui/NewConversationScreen.kt`: 2× `TextField` → `SmithTextField`.
- `ui/ProfileScreen.kt`: `TextField` → `SmithTextField`; `DropdownMenu`/`DropdownMenuItem`
  → Smith popup on `LocalSmithColors` (kills the known pinned-light dropdown bug).
- `ui/plan/IntentComponents.kt`: 3× `TextField` → `SmithTextField(ops = true)`.

Post-merge grep: Material `Button|TextField|DropdownMenu` imports exist only in
`ui/theme/Theme.kt` and `theme2/` internals (if any).

## Plan M3 — AuthScreen Crew Soft rebuild

Visual reskin only — auth logic, state machine, and flows (login/signup/reset/offline)
byte-for-byte in behavior.

- Centered `SmithCard` on the crew background; Syne `GUILD OF SMITHS` wordmark replaces the
  ASCII box banner.
- `> email:` prompts → Inter labels on `SmithTextField`s.
- Clickable-`Text` buttons → `SmithButton` (Primary for submit, Ghost for secondary,
  existing in-flight guards preserved).
- ASCII action tokens (`[▶]`, `[↻]`, `[!]`, …) kept as accents where they carry meaning;
  no emoji.
- Dark works automatically via `LocalSmithColors`.
- **Maestro gate:** grep `android/maestro/` for pinned auth strings and `solo_e2e_*`
  testTags before renaming any visible copy; preserve testTags verbatim; run the solo e2e
  flow after.

Depends on M2 (`SmithTextField`, `SmithCard`).

## Plan M4 — Map modernization

- **Dark tiles:** `mapView.overlayManager.tilesOverlay.setColorFilter(...)` with an
  inversion ColorMatrix (invert + hue-rotate 180° so water/parks keep plausible hues) when
  the resolved theme is dark; null filter in light. Driven by the same single `resolveDark`
  result the app root uses (never resolve twice).
- **Pins:** custom North Cobalt pin drawables replace stock osmdroid markers at the 4
  `Marker(mapView)` creation sites in `ui/dashboard/DashboardModules.kt` — cobalt for job
  sites, a distinct variant for crew presence (SITE_COORDS crew-presence markers included).
- **Bounded refactor:** collapse the two duplicate inline `MapView` builders
  (`DashboardModules.kt` ~L721 and ~L1238 `CrewMapView`) into one composable — it is the
  exact code being touched, and the dark filter must not be implemented twice.
- QR codes remain light-fixed (scannability decision, Plan 5) — unrelated to map tiles.

## Verification (every plan)

- Build: `cd android && ./gradlew assembleDebug` with
  `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home` (Gradle 8.2
  needs JDK 17; machine-default JDK 25 crashes the daemon).
- Existing unit tests (`./gradlew test`), plus Maestro `smithnet_solo_e2e` when visible
  copy or auth/chat surfaces change.
- Manual greps before each merge (no CI gates exist): `plex`, emoji codepoints,
  `RoundedCornerShape\([0-9]`, Material widget imports — scoped per plan above.
- Light AND dark screenshot pass on the touched screens (dark regressions are the release
  gate for the user-facing build).

## Risks

- **Inter variable-font weights** may not resolve on older Android — checked first thing
  in M1; fallback is static weight files (same as Plex uses today).
- **Radius sweep judgment calls** (196 sites) risk mis-classifying crew vs ops — mitigated
  by doing the sweep screen-by-screen with the ops-screen list from Plan 5 as the
  authority.
- **Maestro flows pin visible text** — every plan greps `android/maestro/` before renaming
  copy (Plan 2 lesson).
- **Density/legibility shift:** Inter renders slightly wider than Plex Sans; spot-check
  tight layouts (TopStrip, chips, dialpad) for truncation.
