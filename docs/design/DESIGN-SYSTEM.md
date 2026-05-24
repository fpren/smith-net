# Smith Net — Design System

**Version:** 1.0 (formalized from existing Android shipping code)
**Source of truth:** `EXTRACTED-PATTERNS.md` + `INSPIRATION.md` + actual `ConsoleTheme.kt` runtime API
**Constraint:** **light mode only.** Always. The `DarkColorScheme` in `Theme.kt` is dead code.
**Persona:** Design Systems Architect — but **the system already exists in code.** This doc formalizes; it doesn't invent.

---

## 1. Design principles (the contract)

| # | Principle | Concrete rule |
|---|---|---|
| 1 | **Console, not consumer.** | Monospace everywhere. ALL-CAPS labels. No emoji, no decorative icons. |
| 2 | **Information over decoration.** | No shadows, no gradients, no card elevations. Surfaces fill flat. |
| 3 | **One product, all trades.** | Trade is metadata. Per-trade visuals (electrician circuit diagram, etc.) live in trade packs and don't restyle the core. |
| 4 | **Determinism is the brand.** | Layouts don't shift. Live data ticks (clock, status pills) recompose only the smallest necessary surface. |
| 5 | **Light mode forced.** | Every screen renders in light mode regardless of system preference. Outdoors readability + visual identity. |
| 6 | **Role gates hide; tier gates show + lock.** | Role-gated UI is invisible to that role. Tier-gated UI is visible-but-dimmed with an upgrade CTA. (See `UX-DESIGN.md §4`.) |
| 7 | **The contractor is the boss.** | Cancel is one tap. No dark patterns. No hidden destructive actions. Confirmation only on truly irreversible deletes. |
| 8 | **Cap-hits are friction-as-conversion.** | Friction fires at the moment of value, not before. No proactive nag. The lock IS the marketing surface. |

---

## 2. The runtime API: `ConsoleTheme`

The app does NOT consume `MaterialTheme.colorScheme.*` or `MaterialTheme.typography.*` directly. Every screen reads from a sibling object **`ConsoleTheme`** (defined in `ui/ConsoleTheme.kt`).

### Color accessors
```kotlin
ConsoleTheme.background     // page bg
ConsoleTheme.surface        // card / row bg
ConsoleTheme.surfaceVariant // subtle distinction
ConsoleTheme.textPrimary    // body text
ConsoleTheme.textMuted      // secondary text
ConsoleTheme.outline        // borders, separators
ConsoleTheme.primary        // primary CTAs, links
ConsoleTheme.onPrimary      // text on primary fill
ConsoleTheme.success        // status green (online, paid, done)
ConsoleTheme.successContainer // success tint background
ConsoleTheme.warning        // approaching cap (sparing)
ConsoleTheme.error          // destructive (only inside delete confirmation dialog body)
```

### Typography accessors (all monospace via `FontFamily.Monospace`)
```kotlin
ConsoleTheme.title          // screen titles, section headers (ALL CAPS)
ConsoleTheme.body           // body text
ConsoleTheme.bodyBold       // emphasized body
ConsoleTheme.caption        // small text
ConsoleTheme.captionBold    // ALL-CAPS section labels, status pills
```

### Layout accessors
```kotlin
ConsoleTheme.spacing.sectionGap     // 16dp + ConsoleSeparator + 12dp
ConsoleTheme.spacing.itemGap        // 10dp
ConsoleTheme.spacing.rowPadding     // 12-14dp
ConsoleTheme.spacing.pagePaddingX   // 16dp
ConsoleTheme.spacing.pagePaddingY   // 14dp
```

**Net-new components MUST use `ConsoleTheme.*` and never reach into `MaterialTheme.*`.** This is enforced by code review and (Step 12) AI rule.

---

## 3. Color system

### Palette (light theme — the only theme)
| Token | Hex | Role | Where used |
|---|---|---|---|
| `background` | `#F6F8FA` | Page background | every screen `Modifier.background(ConsoleTheme.background)` |
| `surface` | `#FFFFFF` | Card / row background | every interactive row + card |
| `surfaceVariant` | `#EFF2F5` | Subtle distinction | disabled rows, current-tier highlight in N7 |
| `textPrimary` | `#1F2328` | Primary body text | most text |
| `textMuted` | `#656D76` | Secondary text | captions, "Maybe later" links, sub-descriptions |
| `outline` | `#D0D7DE` | Borders, separators | `ConsoleSeparator`, input field borders |
| `outlineVariant` | `#EFF2F5` | Softer borders | rare — subtle dividers within cards |
| `primary` | `#0969DA` | Primary actions, links, focus | CTA fills, link text, active tab |
| `onPrimary` | `#FFFFFF` | Text on primary fill | CTA button labels |
| `primaryContainer` | `#DDF4FF` | Primary tint background | rare — focused state, highlighted row |
| `onPrimaryContainer` | `#0969DA` | Text on primary tint | content within primary container |
| `secondary` (success) | `#1A7F37` | Success / positive | status green dot, "paid" chip, "synced" indicator |
| `secondaryContainer` | `#DCFFE4` | Success tint background | rare — success banner |
| `warning` | (use Material default — TBD pin a hex; suggest `#9A6700` for amber) | Approaching cap | "1 left" caption emphasis |
| `error` | `#CF222E` | Destructive | ONLY inside delete confirmation dialog text — never as button fill |
| `tertiary` | `#8250DF` | Accent | very rare — flagged-for-attention items |

### Color usage rules

| Rule | Example |
|---|---|
| Status meaning is paired with text | Never communicate state via color alone. `● ONLINE` not just a green dot. |
| No CTA buttons in red | Even destructive actions ("Cancel anyway") use neutral / primary; only the dialog body text uses `error` color. |
| Primary blue is reserved for actions | Not for decorative tinting. |
| Success green only for confirmed positives | Not for "informational" — that's `textPrimary`. |
| Founder counter colors | green dot when seats remain, gray when exhausted. Text color shifts: default `textPrimary` (>100 left) → `textMuted` (11-100) → `primary` (1-10) → never red. |

### Forbidden
- ❌ Custom shadows or elevations
- ❌ Gradients
- ❌ Color-only state indicators (always pair with text)
- ❌ Reaching for hex values not in the palette
- ❌ Adding a dark variant for any screen
- ❌ Using `error` as a button fill color

---

## 4. Typography system

**Font family:** `androidx.compose.ui.text.font.FontFamily.Monospace` (system monospace) for **every** style. No custom fonts. No exceptions.

### Type scale
| Token | Material name | Size | Weight | Line height | Used for |
|---|---|---|---|---|---|
| `titleLarge` | titleLarge | 22sp | 600 | 28sp | screen titles in headers (ALL CAPS) |
| `titleMedium` | titleMedium | 16sp | 600 | 24sp | section sub-headers (rare) |
| `bodyLarge` | bodyLarge | 16sp | 400 | 24sp | primary body content |
| `bodyMedium` | bodyMedium | 14sp | 400 | 20sp | row body text |
| `bodyBold` | (custom) | 14sp | 600 | 20sp | emphasized body (e.g., row title) |
| `caption` | bodySmall | 12sp | 400 | 16sp | sub-captions, helper text, "Maybe later" |
| `captionBold` | labelMedium | 12sp | 600 | 16sp | ALL-CAPS section labels, status pills |

### Casing rules
| Casing | Used for |
|---|---|
| ALL CAPS | screen titles, section headers, status pills, tier names, button labels |
| Mixed case | body content, descriptions, user-entered text, message bodies |
| Sentence case | dialogs body text |

### Forbidden
- ❌ Variable-width / sans-serif / serif fonts
- ❌ Italic styles (monospace italic looks broken)
- ❌ Font sizes < 12sp (accessibility floor)
- ❌ Font sizes > 22sp (no display / headline-large)
- ❌ Custom letter-spacing (monospace handles it)
- ❌ Sentence-case section headers

---

## 5. Spacing system

### Base unit: 2dp. All spacing is a multiple.

| Token | dp | Used for |
|---|---|---|
| `xxs` | 4dp | inline icon-text gap (rare) |
| `xs` | 6dp | trial banner vertical padding |
| `sm` | 8dp | status dot size, status pill horizontal padding |
| `md` | 10dp | item gap (`Arrangement.spacedBy(10.dp)`) |
| `lg` | 12dp | row vertical padding (compact) |
| `lgPlus` | 14dp | row vertical padding (standard) |
| `xl` | 16dp | page horizontal padding, section bottom gap |
| `xxl` | 24dp | (rare — dialog content padding) |
| `xxxl` | 32dp | (rare — top-of-screen breathing room on splash-like screens) |

### Layout patterns

**Section pattern** (most-used in the app):
```
Text("SECTION", captionBold)        ← 0
Spacer(10dp)                         ← +10
Row(...)                             ← content
Spacer(16dp)                         ← +16
ConsoleSeparator()                   ← divider
Spacer(12dp)                         ← +12
Text("NEXT SECTION", captionBold)   ← total ~38dp gap between section heads
```

**Page padding:** `Modifier.padding(horizontal = 16.dp, vertical = 14.dp)`.

**Row padding:** standard `12-14.dp`.

**Status pill padding:** `horizontal = 10.dp, vertical = 6.dp`.

---

## 6. Shape system

| Shape | Where used | Notes |
|---|---|---|
| Rectangle (no corners) | rows, cards, separators, page bg | dominant shape — most surfaces are unrounded |
| `RoundedCornerShape(4.dp)` | input fields with borders | rare — only `BasicTextField` with custom border |
| `RoundedCornerShape(6.dp)` | net-new CTA buttons | small radius preserves console feel; not full pill |
| `CircleShape` | status dots (8dp), avatars (rare) | ONLY these two cases |

**Forbidden:** `RoundedCornerShape(>= 12.dp)` for any surface; `CutCornerShape`; `AbsoluteCutCornerShape`.

---

## 7. Border & outline

| Use | Style |
|---|---|
| Section / row separator | `ConsoleSeparator()` — 1dp, `outline` color |
| Input field border | `Modifier.border(1.dp, ConsoleTheme.outline, RoundedCornerShape(4.dp))` |
| Selected state | thicker `Modifier.border(2.dp, ConsoleTheme.primary, RoundedCornerShape(...))` |
| Disabled state | `Modifier.border(1.dp, ConsoleTheme.outlineVariant, ...)` |
| Trial banner bottom outline | `Modifier.drawBehind { drawLine(outline, ...) }` 1dp |

**No drop shadows. No elevation. Never.**

---

## 8. Iconography

**The app uses Unicode glyphs as icons** — not a Material Icons import, not custom SVGs.

| Glyph | Meaning |
|---|---|
| `←` | back |
| `>` | forward / chevron-right |
| `●` | filled status / on |
| `○` | empty status / off |
| `((●))` | active toggle (Signal-style) |
| `((○))` | inactive toggle (Signal-style) |
| `★` | bonus star (per-tier bonuses in N7 pricing screen) |
| `~` | divider segment (rare; used in some headers) |
| `+` | add (existing rare uses) |
| `·` | bullet / separator inline |

**Sparingly used:** the existing `ui/PixelIcons.kt` defines a small set of pixel-style glyph icons. Do NOT add new icons; prefer Unicode. If a new icon is genuinely needed, design it to fit the pixel-art style and add it to `PixelIcons.kt`, NOT to import a Material icon.

---

## 9. Component library

The app uses custom Composables, NOT Material widgets. Below is the canonical mapping.

| Need | Use this | Don't use |
|---|---|---|
| Button | Custom Composable: surface-bg `Box` with text + optional border. For primary: filled rect with `primary` bg, `onPrimary` text. | Material `Button`, `OutlinedButton`, `TextButton` |
| Card | `Modifier.background(surface).padding(rowPadding)` Box / Column | Material `Card` (with elevation) |
| List item | Section pattern (see §5) | Material `ListItem` |
| Top bar | Custom Row with `←` glyph + ALL-CAPS title (see EXTRACTED-PATTERNS §8) | Material `TopAppBar` |
| Bottom navigation | `ui/components/BottomToolbar.kt` (custom, 5-tab) | Material `BottomAppBar`, `NavigationBar` |
| Side navigation (desktop / wider screens) | `ui/components/LeftSidebar.kt` | Material `NavigationDrawer` |
| Switch / toggle | Signal-style text glyph `((●))/((○))` rendered as Text | Material `Switch` |
| Text field | `BasicTextField` with custom border | Material `OutlinedTextField`, `TextField` |
| Dialog | Custom Composable, conditionally rendered | Material `AlertDialog` |
| Bottom sheet | Custom Composable / `ModalBottomSheet` ONLY when bottom-anchored is required | Material `ModalBottomSheet` for everything |
| Snackbar / banner | `Toast.makeText(...).show()` | Material `Snackbar` |
| Progress indicator | Material `LinearProgressIndicator` (used by existing AI Section model load — **exception to "no Material" rule**) | Custom-skinned progress |
| Tabs | Custom row of `Text` with selection state | Material `TabRow` |
| Chips | Custom Box with text + 1dp border | Material `AssistChip`, `FilterChip` |

### Net-new components (Step 5 introduced these)

| Component | File | Built from |
|---|---|---|
| `LockedFeatureOverlay` | `ui/components/LockedFeatureOverlay.kt` | Box + Column + custom Button + dimmed `Modifier.alpha(0.4f)` |
| `TrialBanner` | `ui/components/TrialBanner.kt` | Row + Text |
| `FounderSeatsCounter` | `ui/components/FounderSeatsCounter.kt` | Row + 8dp Box CircleShape + Text |
| `TierUpgradeCTA` | `ui/components/TierUpgradeCTA.kt` | Column with primary Box + secondary text-link |
| `EntitlementLock` | `ui/components/EntitlementLock.kt` | Section pattern + status dot row |
| `PdfSendCounterFooter` | `ui/components/PdfSendCounterFooter.kt` | single Text line |
| `GateHitToast` | `ui/components/GateHitToast.kt` | Toast wrapper + telemetry side-effect |
| `TierPricingScreen` (full screen) | `ui/subscription/TierPricingScreen.kt` | section pattern × 4 + custom toggles |
| `SubscriptionDetailScreen` (full screen) | `ui/subscription/SubscriptionDetailScreen.kt` | section pattern + dialogs |
| `CancelSubscriptionDialog` / `DeleteAccountDialog` | `ui/subscription/*.kt` | custom Composable Box overlay |
| `WelcomeToOpenScreen` (full screen) | `ui/WelcomeToOpenScreen.kt` | section pattern + 2 CTAs |

---

## 10. Motion

The app has **almost no motion**. This is intentional.

| Use | Spec |
|---|---|
| Screen transition | Default Android navigation transition (no custom override) |
| Live clock tick (e.g., "ON CLOCK Xh Ym Zs") | `LaunchedEffect(state) { while(state) { tick = now; delay(1000) } }` — 1Hz recompose of the smallest text unit |
| Status pill state change | Snap (no animation) — these are status indicators, not transitions |
| `LinearProgressIndicator` for model load | Material default smoothing |
| Lock overlay appearance | `Crossfade` over 200ms (subtle) — opt for instant if `Settings.System.TRANSITION_ANIMATION_SCALE` indicates reduced motion |
| Banner slide-in / out | None — appears / disappears instantly |
| Toast | Standard Android Toast animation (not configurable) |

### Forbidden
- ❌ Bounce / spring animations
- ❌ Hero transitions / shared element animations
- ❌ Skeleton shimmer (use literal `· · ·` text dots instead — see N6 loading state)
- ❌ Pulse / breathing effects on attention-grabbers
- ❌ Confetti, celebration animations on conversion (we're not that kind of app)

---

## 11. Layout grid

### Phone (primary)
- Single column
- 16dp horizontal page padding
- Vertical scroll for any content > viewport
- Bottom toolbar (5 tabs) sits flush with system nav bar
- Status bar inset handled at theme level (`window.statusBarColor = colorScheme.background.toArgb()`)

### Desktop / wider screen (LeftSidebar pattern)
- Left sidebar: 240dp fixed width
- Right content area: scrollable
- Same component library, same theme — just wider canvas

### Quick Actions grid (on Dashboard)
- 4 tiles per row on phone
- Each tile: 1:1 aspect, surface bg, ALL-CAPS label centered, optional sub-icon

---

## 12. Accessibility

| Requirement | Implementation |
|---|---|
| Tap targets ≥ 44dp | All interactive Rows / Buttons enforce min 44dp height |
| Color contrast ≥ 4.5:1 (body), ≥ 3:1 (large) | Verified: `#1F2328` on `#FFFFFF` = 16.1:1 ✓ ; `#656D76` on `#FFFFFF` = 5.5:1 ✓ ; `#0969DA` on `#FFFFFF` = 5.0:1 ✓ |
| TalkBack support | Every interactive element has `Modifier.semantics { contentDescription = ... }`. Locked overlays announce title + body + CTA. Status dots have text companion. |
| Dynamic type support | All `ConsoleTheme.*` styles inherit from MaterialTheme.typography which respects font scale. Layouts use `Modifier.heightIn(min=44.dp)` not fixed heights. |
| Color is never sole carrier of meaning | Every status dot has accompanying text. Every locked state has "Locked" text label. |
| Reduced motion support | Crossfade durations check `Settings.System.TRANSITION_ANIMATION_SCALE`; default to 0 if reduced |
| Focus order | Compose default focus order; verified manually in net-new dialogs / overlays |

### Net-new component a11y requirements
| Component | Required `contentDescription` |
|---|---|
| `LockedFeatureOverlay` | `${title}. ${body}. Tap to ${primaryCta} or dismiss.` |
| `TrialBanner` | `${trialState description}. Tap to manage subscription.` |
| `FounderSeatsCounter` | `${remaining} of ${total} ${bonusName} spots remain.` |
| `EntitlementLock` | `${sectionTitle} locked. Requires ${targetTier} tier. Tap to learn more.` |
| `PdfSendCounterFooter` | `${count} of 5 free PDF sends used this month.` |

---

## 13. Theming rules

| Rule | Why |
|---|---|
| Light mode forced (`isSystemInDarkTheme()` IGNORED) | Brand identity + outdoor readability |
| Use `ConsoleTheme` accessors only | Single source of truth; refactor-safe |
| Status bar color = `colorScheme.background` | Visual continuity (no contrast bar at top) |
| Light status bar icons | `WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true` (already in Theme.kt) |
| No per-screen theme overrides | If a screen needs different colors → reconsider; probably it doesn't |
| No tenant / org branding overrides | One product, one brand. (Re-evaluate post-launch.) |

---

## 14. Anti-patterns checklist (for reviewers)

If a PR introduces any of these, REJECT:

- ❌ Imports `androidx.compose.material3.Button`, `Card`, `Switch`, `AlertDialog`, `Snackbar`, `TopAppBar`, `BottomAppBar`, `NavigationBar`, `NavigationDrawer`, `OutlinedTextField`, `TextField`, `ListItem`
- ❌ Reads from `MaterialTheme.colorScheme.*` or `MaterialTheme.typography.*`
- ❌ Hardcodes a hex color outside the palette (§3)
- ❌ Uses a font family other than `FontFamily.Monospace`
- ❌ Adds drop shadows or `Modifier.shadow(...)` or `Modifier.graphicsLayer(shadowElevation=...)`
- ❌ Adds `isSystemInDarkTheme()` branching for new UI
- ❌ Uses sentence-case for a section header
- ❌ Adds emoji to user-facing text
- ❌ Uses `error` color as a button background
- ❌ Adds Material Icons (`androidx.compose.material.icons.*`)
- ❌ Adds animation longer than 250ms or includes spring physics
- ❌ Uses `RoundedCornerShape(>= 12.dp)` for a card or row
- ❌ Communicates state by color alone (no companion text)

---

## 15. Tokens (machine-readable form)

See `docs/tokens/DESIGN-TOKENS.md` — the full canonical token export (JSON) for engineering consumption + (Step 11) automated theme generation.

---

## 16. Versioning

This system is v1.0 — the first formalization. Changes go through:
1. Update `EXTRACTED-PATTERNS.md` if a NEW pattern is observed in code (descriptive)
2. Update this DESIGN-SYSTEM.md (prescriptive)
3. Update `DESIGN-TOKENS.md` (machine-readable)
4. Bump version in `ConsoleTheme.kt` doc-comment

**Never add a token without updating all three.**
