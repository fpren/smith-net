# Smith Net — Extracted Design Patterns

**Method:** read directly from shipping Android code (Compose, Kotlin) — NOT invented.
**Sources:** `ui/theme/Theme.kt`, `ui/SettingsScreen.kt`, `ui/dashboard/DashboardScreen.kt`, `ui/components/`, plus 30+ screen files surveyed.

This document is the source of truth for **how the existing app looks**. Step 6 (Design System) will turn these into formal tokens. Net-new UI in Step 3 MUST follow these patterns — no exceptions.

---

## 1. The aesthetic in one line

**GitHub Console + Monospace everything + Signal-style indicators.**

Light-mode forced (despite a dark scheme being defined), GitHub-derived color palette, system-monospace typography, ALL-CAPS section headers, terse copy, no chrome.

## 2. Theme object (the API the app actually uses)

The app does NOT use MaterialTheme tokens directly. Instead, every screen reads from a sibling object: **`ConsoleTheme`** (defined alongside `TradeMeshTheme`).

```kotlin
ConsoleTheme.background    // page bg
ConsoleTheme.surface       // card / row bg
ConsoleTheme.textMuted     // secondary text color
ConsoleTheme.title         // TextStyle for screen + section titles
ConsoleTheme.body          // TextStyle for body
ConsoleTheme.bodyBold      // TextStyle for emphasized body
ConsoleTheme.caption       // TextStyle for small text
ConsoleTheme.captionBold   // TextStyle for ALL-CAPS section labels
```

**Rule for net-new UI:** use `ConsoleTheme.*`, not `MaterialTheme.colorScheme.*` and not `MaterialTheme.typography.*`. Consistency with shipping screens depends on this.

## 3. Color palette — LIGHT MODE ONLY (the canonical theme)

**`TradeMeshTheme` hard-codes `LightColorScheme` regardless of system dark mode.** This is intentional and the app is light-only. **Do not design dark variants for net-new UI.** Do not branch on `isSystemInDarkTheme()`. The light palette IS the brand.

(Theme.kt also contains a `DarkColorScheme` definition — it is unused and effectively dead code; treat it as not existing for design purposes.)

| Token | Hex | Usage |
|---|---|---|
| Background | `#F6F8FA` | page background |
| Surface | `#FFFFFF` | card / row background |
| Surface variant | `#EFF2F5` | subtle distinction (e.g., disabled surface, subdued tint) |
| On-background | `#1F2328` | primary text |
| On-surface variant | `#656D76` | secondary / muted text |
| Outline | `#D0D7DE` | borders, separators |
| Outline variant | `#EFF2F5` | softer borders |
| Primary | `#0969DA` | primary actions, links, focus |
| On-primary | `#FFFFFF` | text on primary |
| Primary container | `#DDF4FF` | primary tint background |
| On-primary container | `#0969DA` | text on primary tint |
| Secondary | `#1A7F37` | success / positive (online, paid, done) |
| Secondary container | `#DCFFE4` | success tint background |
| Tertiary | `#8250DF` | accent (rare — sparingly used) |

**Status semantics (Signal-style):**
- 8dp circle, `CircleShape`, filled
- Green `#1A7F37` = online / connected / synced / on
- Grey `#7D8590` (or `outline` `#D0D7DE`) = offline / disconnected / off
- Yellow / orange (Material default warning) = degraded
- Red (Material default error) = error / failed

**Net-new UI rule:** every color used must come from this table. No new hex values. No dark variants. No alternate "accent" colors. If a designed surface seems to need a color not on this list, flag it before introducing.

## 4. Typography

**Family:** `FontFamily.Monospace` for **every** text style (display, headline, title, body, label — large/medium/small).

**Effect:** the app reads like a developer console. This is intentional and is the strongest visual signal of the brand.

**ALL-CAPS pattern** for:
- Screen titles in headers (`SETTINGS`, `DASHBOARD`, `JOB BOARD`)
- Section labels (`MESH CONNECTION`, `WORK MODE`, `TRADE ROLE`)
- Status pills (`ON CLOCK`, `OFFLINE`, `ONLINE`, `MESH`)
- Module names

**Mixed case** for:
- Body content (job names, client names, message bodies)
- User-entered text
- Long-form descriptions (e.g. "Name, trade, rates, billing")

**Net-new UI rule:** if it's a label or a status, ALL CAPS. If it's content, mixed case.

## 5. Spacing & layout

| Element | Value |
|---|---|
| Standard padding (rows, cards) | 12-14dp |
| Page padding | 16dp horizontal, 14dp vertical |
| Section spacing (between groups) | 16dp top + ConsoleSeparator + 12dp bottom |
| Item spacing (within a list/group) | 10dp `Arrangement.spacedBy(10.dp)` on Column |
| Inline spacing (icon ↔ text) | 14dp `Spacer(width=14.dp)` |
| Status dot size | 8dp |
| Border / outline thickness | 1.dp (default) |

**Container grammar:**
- `Modifier.fillMaxWidth().background(ConsoleTheme.surface).padding(12-14dp)` is the standard "row container."
- Vertical scroll on the body (`verticalScroll(rememberScrollState())`).
- No SafeArea / WindowInsets dance — the status bar color is set to background and inset handling is handled at theme level.

## 6. Shape language

- **Rectangles** dominate (no rounded everywhere).
- **`RoundedCornerShape`** used sparingly — text fields, buttons.
- **`CircleShape`** ONLY for status dots and (where present) avatars.
- **Borders** used to denote selection, disabled state, or input fields (e.g., `Modifier.border(1.dp, ConsoleTheme.outline, RoundedCornerShape(...))`).

## 7. The "Section + List Item" pattern (most-used pattern in the app)

```
Text("SECTION NAME", style = captionBold)
Spacer(height = 10.dp)
Row(
  Modifier.fillMaxWidth().background(ConsoleTheme.surface).padding(14.dp),
  verticalAlignment = Alignment.CenterVertically,
  horizontalArrangement = Arrangement.SpaceBetween
) {
  Column {
    Text("Item title", style = ConsoleTheme.bodyBold)
    Text("Item caption", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
  }
  Text(">", style = ConsoleTheme.body, color = ConsoleTheme.textMuted)
}
Spacer(height = 16.dp)
ConsoleSeparator()
Spacer(height = 12.dp)
```

**Variations seen in code:**
- Right-side: chevron `>`, a status dot, a switch-style indicator `((●))/((○))`, or a count badge
- Left-side: optional small icon / dot, then text stack
- Tappable: the whole Row gets `.clickable { ... }`

## 8. Header pattern

```
Row(
  Modifier.fillMaxWidth().background(ConsoleTheme.surface)
    .clickable(onClick = onBackClick).padding(horizontal = 16.dp, vertical = 14.dp),
  verticalAlignment = Alignment.CenterVertically
) {
  Text("←", style = ConsoleTheme.title)
  Spacer(width = 14.dp)
  Text("SCREEN TITLE", style = ConsoleTheme.title)
}
ConsoleSeparator()
```

The back arrow is a literal `←` glyph (Unicode), not an icon resource. Monospace consistency.

## 9. Status pill pattern (e.g., `ON CLOCK 1h 23m 45s`)

```
Box(
  Modifier.background(ConsoleTheme.surface).padding(horizontal = 10.dp, vertical = 6.dp)
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(green))
    Spacer(width = 8.dp)
    Text("ON CLOCK 1h 23m 45s", style = ConsoleTheme.captionBold)
  }
}
```

Live ticking via `LaunchedEffect(isClockedIn) { while (isClockedIn) { nowMs = currentTime; delay(1000) } }`. The pill **always** shows current state; it doesn't disappear when off — it changes to `OFF CLOCK` muted.

## 10. Role-aware composition pattern

Every screen calls `RoleContext.role` and conditionally composes:

```kotlin
val modules = resolveModules(RoleContext.role)
modules.forEach { module -> when (module) { ... } }

if (RoleContext.can(Permission.GATEWAY_RELAY)) {
  // Foreman / Lead / Enterprise / Admin only
}
```

**Critical UX rule already in code:** unprivileged features are **HIDDEN**, not greyed-out. Solo doesn't see "MESH CONNECTION" section at all (gated on `Permission.GATEWAY_RELAY`).

**Net-new tier-gate UX must reverse this principle for the moat:** locked moat features are **VISIBLE BUT DISABLED + UPGRADE CTA**, because that visibility is the conversion driver. (Role gates and tier gates are different things — see UX-DESIGN.md §4.)

## 11. Live data ticking pattern

Time-sensitive UI uses `LaunchedEffect(state) { while (state) { tick = now; delay(intervalMs) } }`. Never `Timer`, never `Handler`. Always the coroutine `delay`.

Examples in code:
- Dashboard "ON CLOCK" pill: 1000ms tick
- Dashboard clock-state poll: 5000ms tick
- AI status poll: TBD

## 12. Toast for non-blocking feedback

`Toast.makeText(context, "...", Toast.LENGTH_SHORT).show()` is used for ephemeral confirmations (e.g., "Copied"). Net-new UI: **continue the convention, no Snackbars** (which would import a different look-and-feel).

## 13. Dialog pattern

Custom Composables, not stock `AlertDialog`. Example: `CrewAssignDialog` is a full Composable that's conditionally rendered with `if (assigningJob != null) { CrewAssignDialog(...) }`. Net-new dialogs (e.g., upgrade prompts, founder-seat-claim confirmation) follow this pattern, NOT Material `AlertDialog`.

## 14. Navigation anti-patterns to AVOID (already in code)

- `commit 1039492` fixed Quick Actions duplicating the bottom nav. **Lesson:** never put the same action in two surfaces.
- `commit 5119abd` decluttered connection status. **Lesson:** one connection indicator, not three.
- `commit 2b2e02a` gated GETTING STARTED tiles on real state. **Lesson:** never show stale demo content; always reflect actual user state.

**Net-new UI rule:** every CTA must point to one place. Every status must be derived from one source. No demo content unless `BuildFlags.SEED_DEMO_DATA` is on.

## 15. What the code does NOT use (and we shouldn't introduce)

- ❌ Material 3 `BottomAppBar` (custom `BottomToolbar` instead)
- ❌ Material 3 `NavigationDrawer` (custom `LeftSidebar` instead)
- ❌ Material 3 `TopAppBar` (custom Row with `←` + title)
- ❌ Material 3 `Snackbar` (Toast instead)
- ❌ Material `AlertDialog` (custom Composables)
- ❌ Material `OutlinedTextField` styling (uses `BasicTextField` with custom border)
- ❌ Material `Switch` (uses Signal-style `((●))/((○))` text glyphs)
- ❌ Material Icons (uses Unicode glyphs `←`, `>`, `●`, `○` for visual consistency with monospace)
- ❌ Custom fonts (system Monospace only)

**Net-new UI:** stick to this discipline. If you find yourself reaching for a Material widget, look for a `Console*` or `Smith*` named pattern in the code first.

## 16. Components inventory

| Shared component | File | Used in |
|---|---|---|
| `ConsoleSeparator` | (defined in `ui/SettingsScreen.kt` or theme — verify) | every screen |
| `BottomToolbar` | `ui/components/BottomToolbar.kt` | most screens |
| `LeftSidebar` | `ui/components/LeftSidebar.kt` | dashboard / settings |
| `TradePickerField` | `ui/components/TradePickerField.kt` | onboarding, profile, new-job |

**Net-new components for Step 3** (added in this step, formalized in Step 6):
- `LockedFeatureOverlay` — dimmed feature surface + CTA
- `TierUpgradePrompt` — modal-like Composable with bonus stack + counter
- `FounderSeatsCounter` — live "X of N spots left" pill
- `TrialBanner` — top banner showing days-left in trial + CTA
- `BrandingStamp` — Smith Net signature footer for Free-tier PDFs (server-side, not Compose)
- `GateHitToast` — extends Toast with telemetry hook
- `EntitlementDot` — small lock-icon glyph for tier-gated nav items

All using `ConsoleTheme`, monospace, ALL-CAPS labels, surface-bg rows.

---

## 17. Anti-patterns when designing net-new UI for this app

- ❌ Don't introduce shadows / elevation — the app is flat
- ❌ Don't introduce gradients — flat fills only
- ❌ Don't introduce icons that aren't in the existing set (unicode glyphs only)
- ❌ Don't introduce a non-monospace font for any reason
- ❌ Don't introduce sentence-case section headers (they're ALL CAPS)
- ❌ Don't introduce Material color tokens directly (`MaterialTheme.colorScheme.primary`) — go through ConsoleTheme
- ❌ Don't introduce a sub-tab pattern when the app uses scroll + section dividers
- ❌ Don't introduce Snackbar / Banner / Sheet from Material — use Toast or full-screen Composable
- ❌ Don't show demo / placeholder content
- ❌ Don't introduce splash screens with logo wash

If a net-new UI requirement seems to need any of the above, FLAG it explicitly to the user before designing.
