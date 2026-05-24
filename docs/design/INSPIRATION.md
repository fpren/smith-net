# Smith Net — Design Inspiration

The aesthetic was already chosen by the time Sigma engaged. This doc names what's already there so net-new UI doesn't drift.

---

## Primary influences (visible in the shipping code)

### 1. GitHub Light Console
**Where it shows up:** color palette is GitHub's light theme (`#0969DA` blue, `#1A7F37` green, `#F6F8FA` page bg, `#FFFFFF` surface, `#1F2328` text, `#656D76` muted, `#D0D7DE` outlines — direct lifts).

**Why it works for Smith Net:** contractor-direct, no decoration, reads as serious tooling not lifestyle SaaS. A foreman opening the app should feel like opening a terminal, not like opening a wellness journal. Light mode reads cleanly outdoors on a job site (where dark mode reflects sky glare).

**What to keep:** the palette, the chevrons (`>`), the ALL-CAPS section labels, the lack of card shadows.

**Light-mode only.** The app forces light and so does every net-new design. Do not introduce a dark variant for any new surface.

### 2. Signal Messenger (the indicator language)
**Where it shows up:** `((●))/((○))` toggle glyphs, 8dp filled-circle status dots in green/grey, "Clear messages on this device" action verbiage, ephemeral channel concept.

**Why it works:** Signal's UI signals trust. Smith Net's privacy posture (mesh, ephemeral channels, on-device AI, encrypted at rest) deserves the same visual vocabulary.

**What to keep:** the dot indicators, the ephemeral channel framing, the "this device" language, terse descriptive copy under settings rows.

### 3. Monospace IDE conventions
**Where it shows up:** entire app uses `FontFamily.Monospace`. Section dividers using box-drawing-style separators (Unicode `═` is in the source comments — implying the same vibe in UI).

**Why it works:** monospace is the contractor's mental model — fixed-width estimates, line-itemed invoices, "this thing right here costs this much." Variable-width body fonts feel marketing-y.

**What to keep:** monospace everywhere, no exceptions, no system-default fallback for "headlines."

### 4. Notion-like content density
**Where it shows up:** spacious row padding (12-14dp), generous spacing between sections (16dp + separator + 12dp), one-thing-per-row layouts.

**Why it works:** contractors read on phones in the truck. Tight rows = misclick. The Notion-style "everything is a row" pattern is forgiving.

**What to keep:** never cram two actions into one row. Never abbreviate a section header to save vertical space.

---

## Secondary influences (in the spirit, not the code yet)

### 5. Linear's "no decoration" rule
Buttons are text + minimal border. No shimmers, no gradients, no skeletons that pretend to be content. Smith Net does this already (BasicTextField + custom border, not OutlinedTextField).

### 6. Stripe Dashboard's tabular precision
Numbers right-aligned, currency consistent, no surprise rounding. Smith Net's invoice + financials screens already do this; net-new tier-pricing displays should follow.

### 7. Procreate / craft-tool tactility
A contractor's tool feels intentional in the hand. Smith Net's existing terse copy ("Set up profile", "Name, trade, rates, billing") echoes a tool that knows its job.

---

## What we are explicitly NOT inspired by

| Anti-influence | Why we avoid it |
|---|---|
| **Slack / Discord chat-app design** | Soft-edged, emoji-heavy, designed for play. Contractors aren't playing. |
| **Material You "expressive" themes** | Generic Android styling kills the brand specificity. Console is the brand. |
| **iOS Human Interface "card-based"** | Cards with shadows look like ads. Smith Net surfaces look like data. |
| **Wellness / consumer apps (Calm, Notion-for-life)** | Variable-width fonts, pastel gradients, soft microcopy ("Hello Sarah! 👋"). Wrong audience. |
| **Salesforce / NetSuite enterprise** | Cluttered, feature-bloated, multi-pane. We're the opposite. |
| **JobTread / ServiceTitan / similar contractor apps** | Stock-photo-y, marketing-heavy, "construction stock" iconography. We are not them; that's the moat. |

---

## Reference imagery / mood

| Influence | Where to look (for style reference, not code reuse) |
|---|---|
| GitHub web app | github.com — blame view, settings pages, code review |
| Signal Android | settings, conversation, channel info |
| Linear web app | settings, command palette, issue detail |
| ESBuild / Bun docs | left-aligned monospace doc sites |
| Tailscale admin console | terse status-driven settings |
| Vim status line | short, dense, all-info-at-a-glance |

**Test:** if a net-new screen design would look natural sitting next to a Linear issue page or a GitHub settings tab, it's right. If it would look natural next to a Houzz Pro or a HoneyBook screenshot, it's wrong.

---

## The brand promise visible in the design

The app *looks* like:
- A tool that doesn't waste your time
- A tool that won't surprise you tomorrow with a different layout
- A tool that takes contractors seriously
- A tool whose author respects monospaced text and doesn't apologize for it

That promise is the design system. Net-new UI must keep it.
