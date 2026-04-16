# SmithNet Altara Design System Redesign

**Date:** 2026-04-16
**Scope:** Skin + layout refinement (approach B) via token-based refactor (approach A execution)
**Target:** `/tmp/smithnet-scaffold` — Vite + React + TypeScript + Tailwind project

## Summary

Replace the current cool-toned, saturated-accent design system with a warm utilitarian "Altara" aesthetic inspired by Bloomberg Terminal crossed with modern fintech. Dense, information-rich, low decoration. Every decision prioritizes legibility at small sizes over visual flair.

## Design Decisions

| Decision | Choice |
|----------|--------|
| Scope | Skin + layout refinement — new visual system plus reworked spacing/density/proportions |
| Header | Warm dark (`#3A352E`) — preserves visual hierarchy, shifted from blue-grey to warm |
| Status bar | Subtle warm dark strip matching header — terminal-style status line |
| Color palette | Full Altara earthy/muted — no saturated primaries anywhere |
| Nav tabs | Bottom border only — 1.5px gold border, no background fill |
| Dashboard proportions | Tighter: `220px \| 1fr \| 180px` — terminal density, map dominates |
| Execution | Token-based refactor — define system once in CSS vars + Tailwind, sweep components |

## Design Token System

### Surfaces

| Token | Value | Usage |
|-------|-------|-------|
| `--bg` | `#F4F2EE` | Page background (warm off-white) |
| `--card` | `#FAFAF8` | Card/panel surface |
| `--surface-hover` | `#F7F5F2` | Hover state background |
| `--surface-inset` | `#F0EDE8` | Inset areas, input fields |
| `--surface-border` | `#E8E4DE` | Strongest light surface |

### Dark Surfaces (header, status bar)

| Token | Value | Usage |
|-------|-------|-------|
| `--dk` | `#3A352E` | Header/status bar background |
| `--dk-hover` | `#4A443C` | Dark surface hover |
| `--dk-text` | `#E8E4DE` | Primary text on dark |
| `--dk-text2` | `#8C8478` | Secondary text on dark |

### Accents (the Altara six)

| Token | Value | Usage |
|-------|-------|-------|
| `--gold` | `#9A6F2E` | Active, ownership, primary accent |
| `--sage` | `#5A8C76` | Done, success, positive |
| `--slate` | `#3A6A8C` | Quote, info, data |
| `--dusty` | `#6A4A8C` | Scheduled, planned |
| `--brick` | `#8C3A3A` | Blocker, danger, urgent |
| `--sienna` | `#8C5A2E` | Invoice, treasury, warning |

### Text

| Token | Value | Usage |
|-------|-------|-------|
| `--tx` | `#2A2520` | Primary text (warm near-black) |
| `--tx2` | `#5C5347` | Secondary text |
| `--tx3` | `#8C8478` | Tertiary/muted text |

### Borders

- Default: `.5px solid rgba(0,0,0,.07)`
- Emphasis: `.5px solid rgba(0,0,0,.12)`

### Shadows (layered, warm)

| Token | Value | Usage |
|-------|-------|-------|
| `--s1` | `0 1px 3px rgba(0,0,0,.04), 0 4px 12px rgba(0,0,0,.03)` | Cards |
| `--s2` | `0 4px 12px rgba(0,0,0,.06), 0 16px 40px rgba(0,0,0,.08)` | Popups |
| `--s3` | `0 8px 24px rgba(0,0,0,.1), 0 40px 100px rgba(0,0,0,.15)` | Modals |

### Typography

| Token | Value | Role |
|-------|-------|------|
| `--font-display` | `'Syne', sans-serif` | Headings, entity names, KPI values |
| `--font-mono` | `'IBM Plex Mono', monospace` | Labels, metadata, tags, badges, numbers |
| `--font-body` | `'IBM Plex Sans', sans-serif` | Body text, descriptions |

## Component Styling Rules

### Cards (job cards, panels)

- Background: `--card`
- Border: `.5px solid rgba(0,0,0,.07)`
- Border-radius: `8px`
- Shadow: `--s1`
- Hover: background to `--surface-hover`, border to `rgba(0,0,0,.12)`
- Selected: 2px left inset shadow in relevant accent, faint accent-tinted background `rgba(accent,.04)`

### Chips/Badges

- Font: `--font-mono`, 9–10px, weight 600
- Background: `rgba(accent,.08)`
- Border: `.5px solid rgba(accent,.12)`
- Border-radius: `3px`

### Section Headers

- Background: `--dk`
- Label: `--font-mono`, 9px, weight 700, uppercase, letter-spacing `.1em`, color `--dk-text2`

### Avatars

- Gradient approach preserved, crew colors stay as personal identifiers
- Border-radius: 28% of size (rounded square)

### Progress Bars

- Track: `--surface-inset`, 3px height, radius 2px
- Fill: gradient from accent to accent at 80% opacity

### Buttons

- Primary: `--gold` background, `--bg` text, `--s1` shadow
- Secondary: `--card` background, `.5px solid rgba(0,0,0,.07)`, `--tx2` text
- Hover: one surface level shift, never color change

### Nav Tabs

- Active: `--gold` text + 1.5px bottom border in `--gold`, no background
- Inactive: `--dk-text2` text, no border, no background

### Inputs

- Background: `--surface-inset`
- Border: `.5px solid rgba(0,0,0,.07)`
- Focus: border to `rgba(0,0,0,.12)`

## Layout

### Dashboard Grid

- Three columns: `220px | 1fr | 180px`
- Gap: `8px`
- Padding: `8px`

### Header (50px)

- Background: `--dk`
- Logo: GS badge in `--gold` bg / `--bg` text
- Brand: "SmithNet" in Syne 13px/700, "Guild of Smiths" in IBM Plex Mono 8px uppercase `--dk-text2`
- Nav: bottom-border pattern, no pill/container backgrounds
- Search: `rgba(255,255,255,.06)` bg, `.5px solid rgba(255,255,255,.06)` border
- Clock: IBM Plex Mono 16px/600 `--dk-text`
- Team hours: `rgba(154,111,46,.1)` bg, `.5px solid rgba(154,111,46,.15)` border
- Bell: brick-tinted when unread, neutral when clear
- New Job: `--gold` bg, `--bg` text, `0 2px 8px rgba(154,111,46,.3)` shadow

### Status Bar (22px)

- Background: `--dk` (matches header)
- "SMITHNET": IBM Plex Mono 8px/700 `--gold`, `.1em` spacing
- Stats: IBM Plex Mono 9px `--dk-text2`
- Blocker: brick-tinted chip
- Separators: `1px rgba(255,255,255,.08)`

### Animations

- `pulse`: unchanged (opacity toggle)
- `ripple`: gold-shifted — `rgba(154,111,46,.4)`
- `slideIn`: unchanged (scale + translateY)

## File Change Map

### Modified

| File | Changes |
|------|---------|
| `index.css` | All CSS variables, Google Fonts import, animation colors |
| `tailwind.config.ts` | Color palette, font families, shadow definitions |
| `src/lib/constants.ts` | Stage colors, trade colors, role type colors, badge colors, signal colors |
| `src/layouts/AppHeader.tsx` | Dark warm header, Syne brand, bottom-border tabs, warm accents |
| `src/layouts/StatusBar.tsx` | Dark warm bar, gold label, IBM Plex stats |
| `src/pages/Dashboard.tsx` | Grid to `220px \| 1fr \| 180px`, warm card styling |
| `src/pages/Jobs.tsx` | Warm surfaces, earthy stage colors, updated typography |
| `src/pages/Crew.tsx` | Warm surfaces, updated role badges, IBM Plex fonts |
| `src/pages/Reports.tsx` | Warm surfaces, earthy chart colors, updated typography |
| `src/components/ui/Chip.tsx` | IBM Plex Mono, `.5px` border, `rgba(accent,.08)` bg |
| `src/components/ui/ProgressBar.tsx` | `--surface-inset` track, warm gradient fill |
| `src/components/ui/SectionHeader.tsx` | `--dk` background, IBM Plex Mono label |
| `src/components/job/JobCard.tsx` | Warm card, `.5px` borders, Syne title, updated accents |

### Not Modified

| File | Reason |
|------|--------|
| `src/store/index.ts` | No visual code |
| `src/types/index.ts` | No visual code |
| `src/lib/data.ts` | Seed data only |
| `src/lib/utils.ts` | Formatting helpers only |
| `src/hooks/useClockTick.ts` | Timer logic only |
| `src/components/ui/Avatar.tsx` | Gradient logic unchanged, crew colors are personal |

## Stage Color Mapping

| Stage | Old Color | New Color | Altara Name | Rationale |
|-------|-----------|-----------|-------------|-----------|
| New | `#64748b` | `#8C8478` | Warm grey | Neutral, unactivated |
| Quote | `#3b82f6` | `#3A6A8C` | Slate blue | Analytical, estimation |
| Scheduled | `#0891b2` | `#6A4A8C` | Dusty purple | Planned, distinct but not urgent |
| Active | `#f59e0b` | `#9A6F2E` | Gold | Ownership, money in motion |
| Invoice | `#0ea5e9` | `#8C5A2E` | Sienna | Treasury, money awaiting collection |
| Done | `#16a34a` | `#5A8C76` | Sage green | Completed, positive outcome |

## Semantic Color Mapping

| Purpose | Old Color | New Color | Altara Name |
|---------|-----------|-----------|-------------|
| Danger/urgent | `#dc2626` | `#8C3A3A` | Brick red |
| Warning/caution | `#f59e0b` | `#8C5A2E` | Sienna |
| Success/positive | `#16a34a` | `#5A8C76` | Sage |
| Info/data | `#3b82f6` | `#3A6A8C` | Slate blue |

## Trade Color Mapping

| Trade | Old Color | New Color | Altara Tone |
|-------|-----------|-----------|-------------|
| Electrical | `#f59e0b` | `#9A6F2E` | Gold |
| Plumbing | `#3b82f6` | `#3A6A8C` | Slate blue |
| HVAC | `#0891b2` | `#4A7A8C` | Muted teal |
| Carpentry | `#b45309` | `#8C5A2E` | Sienna |
| Cleaning | `#0d9488` | `#5A8C76` | Sage |
| Landscaping | `#16a34a` | `#6A8C5A` | Olive sage |
| Painting | `#7c3aed` | `#6A4A8C` | Dusty purple |
| Roofing | `#64748b` | `#8C8478` | Warm grey |
| General | `#6b7280` | `#7A7468` | Stone grey |

## Role Type Color Mapping

| Role | Old Color | New Color |
|------|-----------|-----------|
| Worker | `#0891b2` | `#3A6A8C` (slate) |
| Supervisor | `#7c3aed` | `#6A4A8C` (dusty) |
| Manager | `#f59e0b` | `#9A6F2E` (gold) |
| Dispatcher | `#16a34a` | `#5A8C76` (sage) |
| Admin/Owner | `#ef4444` | `#8C3A3A` (brick) |

## Signal Color Mapping

| Signal | Old Color | New Color |
|--------|-----------|-----------|
| Access | `#dc2626` | `#8C3A3A` (brick) |
| Hazard | `#ea580c` | `#8C4A2E` (dark sienna) |
| Material | `#f59e0b` | `#8C5A2E` (sienna) |
| Break | `#0891b2` | `#3A6A8C` (slate) |
| En route | `#16a34a` | `#5A8C76` (sage) |
