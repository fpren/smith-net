---
name: smith-net-design-system
description: UI design conventions for Smith Net Android app — light mode ONLY, monospace font ONLY, ConsoleTheme runtime API (NOT MaterialTheme), no Material widgets (custom Composables instead), Unicode glyphs as icons (no Material Icons). Use when writing or modifying any Compose UI in /android/, adding new screens or components, or making styling decisions.
---

# Smith Net — Design system conventions

This skill activates anytime you're writing or editing Jetpack Compose UI code in this project.

## Light mode is FORCED. Never branch on dark mode.

`TradeMeshTheme` always returns `LightColorScheme` regardless of system setting. The `DarkColorScheme` in `Theme.kt` is **dead code** — treat it as not existing. Don't introduce `isSystemInDarkTheme()` branches in new UI.

## Use `ConsoleTheme.*` — never `MaterialTheme.*`

The app does NOT consume `MaterialTheme.colorScheme.*` or `MaterialTheme.typography.*` directly. Every screen reads from a sibling object **`ConsoleTheme`** (in `ui/ConsoleTheme.kt`).

```kotlin
ConsoleTheme.background    // page bg
ConsoleTheme.surface       // card / row bg
ConsoleTheme.surfaceVariant
ConsoleTheme.textPrimary   // body text
ConsoleTheme.textMuted     // secondary text
ConsoleTheme.outline       // borders, separators
ConsoleTheme.primary       // primary actions
ConsoleTheme.onPrimary
ConsoleTheme.success       // status green / paid / done
ConsoleTheme.warning       // sparing
ConsoleTheme.error         // ONLY for delete-confirmation dialog body text

ConsoleTheme.title         // ALL-CAPS screen + section titles
ConsoleTheme.body          // body text
ConsoleTheme.bodyBold      // emphasized body
ConsoleTheme.caption       // small text
ConsoleTheme.captionBold   // ALL-CAPS section labels, status pills
```

## Monospace EVERYWHERE

`FontFamily.Monospace` is the only font family. Don't import custom fonts. Don't use italic. The monospace look IS the brand.

## Casing rules

- **ALL CAPS** for: screen titles, section headers, status pills, tier names, button labels
- **Mixed case** for: body content, descriptions, user-entered text, message bodies
- **Sentence case** for: dialog body text only

## Color palette (ALL allowed colors — don't introduce new hex)

| Hex | Token | Use |
|---|---|---|
| `#F6F8FA` | background | page bg |
| `#FFFFFF` | surface | rows, cards |
| `#EFF2F5` | surfaceVariant | subtle distinction |
| `#1F2328` | textPrimary | body text |
| `#656D76` | textMuted | secondary text |
| `#D0D7DE` | outline | separators |
| `#0969DA` | primary | actions, links |
| `#FFFFFF` | onPrimary | text on primary fill |
| `#1A7F37` | success | status green |
| `#DCFFE4` | successContainer | success tint |
| `#9A6700` | warning | cap-approached (sparing) |
| `#CF222E` | error | text-only inside delete dialog |
| `#7D8590` | statusGrey | offline / disconnected dots |

## Don't use Material widgets — use custom Composables

| Need | Use this | Don't use |
|---|---|---|
| Button | Custom: surface-bg `Box` with text + optional border. Primary: `primary` fill. | `Button`, `OutlinedButton`, `TextButton` |
| Card | `Modifier.background(surface).padding(...)` Box/Column | Material `Card` (has elevation) |
| List item | Section pattern (see below) | `ListItem` |
| Top bar | Custom Row: `←` glyph + ALL-CAPS title | `TopAppBar` |
| Bottom nav | `ui/components/BottomToolbar.kt` (custom 5-tab) | `BottomAppBar`, `NavigationBar` |
| Switch | `((●))/((○))` text glyph | Material `Switch` |
| Text field | `BasicTextField` + custom border | `OutlinedTextField`, `TextField` |
| Dialog | Custom Composable, conditionally rendered | `AlertDialog` |
| Snackbar | `Toast.makeText().show()` | `Snackbar` |
| Tabs | Custom Row of `Text` with selection state | `TabRow` |

**Exception (allowed):** `LinearProgressIndicator` for AI model load (existing pattern).

## Icons = Unicode glyphs only

| Glyph | Meaning |
|---|---|
| `←` | back |
| `>` | forward / chevron-right |
| `●` | filled status / on |
| `○` | empty status / off |
| `((●))` `((○))` | toggle (Signal-style) |
| `★` | bonus star (per-tier bonuses) |
| `·` | bullet / inline separator |

**Don't add** `androidx.compose.material.icons.*` imports.

## Section + List Item pattern (most-used in app)

```kotlin
Text("SECTION NAME", style = ConsoleTheme.captionBold)
Spacer(Modifier.height(10.dp))
Row(
  Modifier.fillMaxWidth().background(ConsoleTheme.surface).padding(14.dp),
  verticalAlignment = Alignment.CenterVertically,
  horizontalArrangement = Arrangement.SpaceBetween
) {
  Column { Text("Item title", style = ConsoleTheme.bodyBold)
           Text("Item caption", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)) }
  Text(">", style = ConsoleTheme.body, color = ConsoleTheme.textMuted)
}
Spacer(Modifier.height(16.dp))
ConsoleSeparator()
Spacer(Modifier.height(12.dp))
```

## Header pattern

```kotlin
Row(
  Modifier.fillMaxWidth().background(ConsoleTheme.surface).clickable(onClick = onBackClick)
    .padding(horizontal = 16.dp, vertical = 14.dp),
  verticalAlignment = Alignment.CenterVertically
) {
  Text("←", style = ConsoleTheme.title)
  Spacer(Modifier.width(14.dp))
  Text("SCREEN TITLE", style = ConsoleTheme.title)
}
ConsoleSeparator()
```

## No motion beyond 5 primitives

| Primitive | Use |
|---|---|
| Snap (0ms) | Most state changes |
| Tick (1Hz) | Live clock displays |
| Crossfade 200ms | Lock overlay appear/disappear |
| Material progress | AI model load only |
| Toast | Transient notifications |

**Forbidden:** spring/bounce, animations > 250ms, hero/shared-element transitions, skeleton shimmer (use literal `· · ·` text dots), confetti.

## Tier gates SHOW + LOCK; role gates HIDE

- **Role gates HIDE** the feature entirely (existing pattern: `if (RoleContext.can(Permission.X))`)
- **Tier gates SHOW the feature dimmed/locked + CTA** (new pattern: `LockedFeatureOverlay` + `EntitlementLock`)

## Confirmation dialogs DON'T dismiss on outside-tap

Cancel-subscription and Delete-account confirmations require explicit choice. Back-button = the safer choice (KEEP). Outside-tap on the dimmed background does nothing.

## Empty states have NO illustrations

ALL-CAPS body text + a single text-link to start the right action. No spot-art, no emoji, no "Looks like a quiet day!" friendliness.

## Don't do

- ❌ Import `androidx.compose.material3.{Button|Card|Switch|AlertDialog|Snackbar|TopAppBar|BottomAppBar|NavigationBar|NavigationDrawer|OutlinedTextField|TextField|ListItem}`
- ❌ Read from `MaterialTheme.colorScheme.*` or `MaterialTheme.typography.*`
- ❌ Hardcode hex outside the palette
- ❌ Use a font family other than `FontFamily.Monospace`
- ❌ Add `Modifier.shadow(...)` or `graphicsLayer(shadowElevation=...)`
- ❌ Add `Brush.linearGradient` to surfaces
- ❌ Add `isSystemInDarkTheme()` for new UI
- ❌ Use sentence-case for a section header
- ❌ Add emoji to user-facing text
- ❌ Use `error` color as a button background
- ❌ Add Material Icons (`androidx.compose.material.icons.*`)
- ❌ Add animation longer than 250ms or use spring physics
- ❌ Use `RoundedCornerShape(>= 12.dp)` for cards / rows
- ❌ Communicate state by color alone (always pair with text)

## Linked specs

- `docs/design/EXTRACTED-PATTERNS.md` — patterns extracted from shipping code (binding constraint)
- `docs/design/DESIGN-SYSTEM.md` — formal system rules
- `docs/tokens/DESIGN-TOKENS.md` — machine-readable tokens
- `docs/states/STATE-SPEC.md` — 108 enumerated states
- `docs/states/MICRO-INTERACTIONS.md` — interaction primitives
