---
name: smith-net-frontend-overlay
description: Smith Net project-specific overlay on top of the frontend-design foundation skill. Constrains generic frontend best practices to Smith Net's brand (light-mode-only Console aesthetic + monospace + no Material widgets + light-only palette). Use for ANY UI work in /android/ or /desktop/portal/. This OVERRIDES generic frontend-design defaults.
---

# Smith Net frontend overlay

This skill **layers on top of** `frontend-design:frontend-design`. When that skill loads with generic best practices, this overlay applies Smith Net's specific brand constraints. **Smith Net constraints WIN** when they conflict with generic defaults.

## Foundation skill referenced

`frontend-design:frontend-design` — generic distinctive-frontend creation skill from the frontend-design plugin.

## Overrides

### Override 1: Light mode is forced. No dark variants.

Generic frontend-design may suggest dark/light theming. **For Smith Net: no.** The app forces `LightColorScheme` regardless of system setting. Don't generate dark-mode CSS, don't add `prefers-color-scheme: dark` queries, don't propose dark-mode toggles.

### Override 2: Monospace EVERYWHERE — even body text

Generic guidance often suggests monospace for code only and a body font (sans-serif) for prose. **For Smith Net: monospace for everything.** `FontFamily.Monospace` (Android) / `font-family: 'Courier New', Consolas, monospace` (web). No Inter, no SF Pro, no Roboto, no Geist Sans.

### Override 3: No Material widgets in Android

Don't import `androidx.compose.material3.*` widgets (`Button`, `Card`, `AlertDialog`, `Snackbar`, `Switch`, `OutlinedTextField`, `TopAppBar`, etc.). Use the project's custom Composables that read from `ConsoleTheme.*`. Exception: `LinearProgressIndicator` for AI model load is allowed.

### Override 4: 11-color palette — don't introduce new hex

The full allowed palette is in `docs/tokens/DESIGN-TOKENS.md`. Don't generate UI with colors outside it. Don't propose accent gradients, hover states with new tints, or "subtle backgrounds" with new hex values. Use `surfaceVariant` (`#EFF2F5`) for any "tinted background" need.

### Override 5: Unicode glyphs as icons. No icon libraries.

Don't import Material Icons, Lucide, Heroicons, Phosphor, Tabler, etc. Use existing Unicode glyphs (`←`, `>`, `●`, `○`, `((●))`, `((○))`, `★`, `·`). If a new symbol is genuinely needed, design it for the pixel-art style and add to `ui/PixelIcons.kt` only.

### Override 6: ALL CAPS for labels, mixed case for content

Generic copy guidance is sentence case. **For Smith Net:**
- ALL CAPS: screen titles, section headers, status pills, button labels
- Mixed case: body content, descriptions, user-entered text
- Sentence case: dialog body text only

### Override 7: No motion beyond 5 primitives

Generic frontend skills often suggest delightful micro-animations. **For Smith Net:** snap (0ms), tick (1Hz live clocks), Crossfade 200ms (overlays only), Material progress (AI model load only), Toast (transient). **Forbidden:** spring physics, anything > 250ms, hero/shared-element transitions, skeleton shimmer (use literal `· · ·` text dots), confetti, pulse breathing.

### Override 8: Empty states have NO illustrations

Don't generate "delightful empty state" illustrations. ALL-CAPS text + a single text-link to start the right action.

### Override 9: Tier gates SHOW + LOCK (don't hide)

Generic accessibility guidance might say "don't show users features they can't use." For Smith Net **TIER**-gated features:
- Show the feature dimmed (40% alpha background)
- Top card explains what it is + what tier unlocks it
- Primary CTA leads to upgrade

(Role-gated features still HIDE per generic guidance — that's a separate rule.)

### Override 10: Confirmation dialogs DON'T dismiss on outside-tap

For destructive actions (cancel subscription, delete account), the dialog requires explicit choice. Generic dialog patterns often allow outside-tap to dismiss; Smith Net does not for these surfaces.

## When generic frontend-design and this overlay conflict

This overlay wins. Always. The generic skill provides a starting point; Smith Net's constraints are non-negotiable.

## When this overlay applies

Active in:
- `/android/` (Compose UI) — every UI file
- `/desktop/portal/` (React/Tailwind) — port the same constraints; React equivalents below

## React/Tailwind equivalents (desktop portal)

When porting Android components to React:

| Android (Compose) | React + Tailwind |
|---|---|
| `ConsoleTheme.background` | `bg-[#F6F8FA]` |
| `ConsoleTheme.surface` | `bg-white` |
| `ConsoleTheme.textPrimary` | `text-[#1F2328]` |
| `ConsoleTheme.textMuted` | `text-[#656D76]` |
| `ConsoleTheme.primary` | `text-[#0969DA]` / `bg-[#0969DA]` |
| `FontFamily.Monospace` | `font-mono` (Tailwind) — verify it points to a true monospace |
| ALL CAPS text | `tracking-wide uppercase` |
| `RoundedCornerShape(6.dp)` button | `rounded-md` |
| `CircleShape` status dot | `rounded-full w-2 h-2` |
| `ConsoleSeparator` | `border-t border-[#D0D7DE]` |

## Don't do

(Inherits all `smith-net-design-system` "Don't do" entries.)

## Linked specs

- Foundation: `frontend-design:frontend-design` skill
- `docs/design/EXTRACTED-PATTERNS.md` — patterns from shipping code
- `docs/design/DESIGN-SYSTEM.md` — formal system rules
- `docs/tokens/DESIGN-TOKENS.md` — machine-readable tokens
- `.claude/skills/smith-net-design-system/SKILL.md` — Step 12 design rules
