# Smith Net — UX Design

**Scope (per user direction):** keep all existing screens as-is. Only design the **net-new UI** required by Step-2 launch blockers (B5-B12) and the offer architecture from Step 1.5.

**Aesthetic constraint:** every net-new element follows `EXTRACTED-PATTERNS.md` — `ConsoleTheme`, monospace, ALL-CAPS labels, Surface-bg rows, chevron `>`, 8dp Signal dots, ConsoleSeparator, no Material widgets.

**Persona:** Chief Design Officer. **Mantra:** "Don't introduce a single pixel that wasn't already implied by what shipped."

---

## 1. The 12 net-new UI surfaces

| # | Surface | Triggered by | Tier impact |
|---|---|---|---|
| N1 | **Trial banner** (top of every screen during Solo/Advanced trial) | Trial start, dismiss-on-conversion or expiry | Free users on trial |
| N2 | **Locked-feature overlay** — full-bleed dimmer + CTA on PLAN compose, AI tab, crew invite | Free user opens PLAN compose; Solo opens AI tab; Solo/Advanced opens crew invite | Free → Solo, Solo → Advanced, Advanced → Enterprise |
| N3 | **PLAN Compiler preview pane** — read-only greyed-out view of what a compiled plan would look like | Free user opens PLAN tab | Free → Solo trigger |
| N4 | **Active-job cap "soft wall"** — refuses 2nd active job creation with upgrade CTA | Free user creates a 2nd job | Free → Solo trigger |
| N5 | **PDF send cap counter** — small caption "X of 5 free sends used this month" + 6th send blocks | Free user opens send dialog | Free → Solo trigger |
| N6 | **Founder seats counter pill** — "X of N spots left" on Solo upgrade prompt | Solo upgrade modal renders | Conversion driver |
| N7 | **Tier selection / pricing screen** — all 4 tiers, current = highlighted, founder pricing visible, trial CTA per tier | User taps "Upgrade" anywhere; Settings > Subscription | All upgrades |
| N8 | **Subscription detail screen** — current tier, next bill date, payment method, founder lock status, downgrade/cancel | Settings > Subscription | Self-service management |
| N9 | **Branded PDF stamp** (server-side template change) — Smith Net footer + "Sent via Smith Net" email signature on Free tier | Free user sends invoice/proposal | Passive distribution |
| N10 | **AI tab "lock screen"** — Solo opens AI Assistant section, sees locked state with Advanced CTA | Solo user navigates to AI Assistant section | Solo → Advanced |
| N11 | **Crew invite "lock screen"** — Solo/Advanced attempts colleague invite, sees locked state with Enterprise CTA | Solo/Advanced taps "Invite Colleague" | Advanced → Enterprise |
| N12 | **Tier-gate Toast** (telemetry-aware) — non-blocking confirmation on every gate hit | Any of N4, N5, N10, N11 | Telemetry sink |

---

## 2. Three core design principles for net-new UI

### Principle 1 — **The lock IS the landing page.**

Free users don't browse a marketing site to learn about Solo. They see the locked PLAN Compiler preview every time they open the PLAN tab. That preview *is* the marketing surface.

**UX implication:** the locked overlay must answer three questions in 3 seconds:
1. What is this?
2. Why can't I use it?
3. What does $2.99 unlock?

No marketing copy. Just the answer.

### Principle 2 — **Friction is felt at the moment of value, not before.**

Don't gate the user at signup. Don't gate the user at first job. Gate the user at *the moment the next action is the next dollar* — the second job, the sixth invoice, the AI suggestion they wanted. That's where the upgrade feels earned.

**UX implication:** every cap is a trigger for an upgrade prompt. The prompt fires on attempt, not on cap-approach. We don't say "you have 1 PDF send left this month." We let them try the 6th, and *then* show the upgrade. The frustration is real, the unlock is the answer.

### Principle 3 — **The contractor is the boss; the app is staff.**

Cancel is one tap. Downgrade is one tap. Data export is one tap. Every screen has an obvious "back." No dark patterns, no "are you sure?" double-confirms on cancellation (on subscription cancel only — destructive actions like delete-job-data still confirm).

**UX implication:** Settings > Subscription has cancel as a normal-styled row, not buried, not red-painted. Trust earned that way pays back in re-conversion.

---

## 3. Per-surface design (high level — wireframes in WIREFRAMES.md)

### N1 — Trial banner

**Placement:** thin row at top of every screen during trial. Below status bar, above the existing screen content. Background = `surface`, 1dp outline-bottom.

**Copy templates:**
- Solo trial day 1: `SOLO TRIAL · 14 DAYS LEFT · TAP TO LOCK FOUNDER PRICING`
- Solo trial day 7: `SOLO TRIAL · 7 DAYS LEFT · ${X} FOUNDER SPOTS LEFT`
- Solo trial day 12 (final): `SOLO TRIAL ENDS IN 2 DAYS · TAP TO STAY SOLO`
- Advanced trial day 1: `ADVANCED TRIAL · 30 DAYS · SMITHAI IS LEARNING YOUR JOBS`
- Advanced trial day 14: `ADVANCED TRIAL · 16 DAYS LEFT · ${X} LIFETIME SPOTS LEFT`
- Advanced trial day 28: `ADVANCED TRIAL ENDS IN 2 DAYS · TAP TO KEEP SMITHAI`

**Style:** `captionBold`, 6dp vertical padding, 12dp horizontal, tap navigates to N7 (tier selection).

**Dismissable?** No — only auto-dismisses on conversion or trial-end.

### N2 / N3 / N10 / N11 — Locked-feature overlay (one component, four uses)

**Layout:** a Composable that wraps the underlying screen with:
- Bottom 40% of the viewport: fully-rendered (preview) view of the locked feature, dimmed to `40% opacity` via `Modifier.alpha(0.4f)` — this is the "you can see what you're missing" pattern
- Top 60%: a `surface`-bg card that explains:
  - 1 line ALL-CAPS title (e.g., `PLAN COMPILER`)
  - 1 short sentence body (e.g., "Compile your plan once. It runs the same every time.")
  - Tier-name + price (e.g., `SOLO · $2.99/MO`)
  - Founder counter pill if applicable (N6)
  - Primary CTA: `[ TRY FREE 14 DAYS — NO CC ]`
  - Secondary tap-target: `[ Maybe later ]` text-link, muted

**No back-arrow needed** — it's an overlay, not a screen. Tapping the dimmed area dismisses (returns to caller).

**Content per use:**

| Use | Title | Body | CTA | Linked tier |
|---|---|---|---|---|
| N2/N3 PLAN Compiler | `PLAN COMPILER` | "Your plan, compiled. Runs the same way every time." | `TRY SOLO FREE 14 DAYS — NO CC` | Solo |
| N10 AI tab | `SMITHAI` | "On-device. No cloud. Watches your jobs and helps without ever sending data anywhere." | `TRY ADVANCED FREE 30 DAYS — NO CC` | Advanced |
| N11 Crew invite | `CREW MODE` | "Bring your crew on the same plan. $50/mo for the whole team." | `START 14-DAY ENTERPRISE TRIAL` | Enterprise |

### N4 — Active-job cap soft wall

**Where it fires:** Free user taps `+ NEW JOB` and already has 1 active (non-archived) job.

**Style:** full-screen overlay (same `LockedFeatureOverlay` component as N2 with content variant):
- Title: `ONE ACTIVE JOB AT A TIME`
- Body: "Smith Net Open caps at 1. Close your active job to start another, or unlock unlimited with Solo."
- Primary CTA: `[ TRY SOLO FREE — NO CC ]`
- Secondary CTA: `[ See active job ]` (jumps to existing job)

**No "approaching cap" pre-warning.** The soft wall fires on attempt; the user knows immediately.

### N5 — PDF send cap counter

**Where it shows:** at the bottom of the existing PDF Send dialog (not a new screen).

**Render:**
- Days 1-25 of month, before 5th send: `4 of 5 free sends used this month` muted caption
- Days 1-25 of month, on 6th send attempt: `[overlay]` with title `5 SENDS PER MONTH` + body "You've sent 5 PDFs this month on Open. Unlimited with Solo." + primary CTA `[ TRY SOLO FREE — NO CC ]` + secondary `[ Next month: in X days ]`
- After conversion: counter disappears (Solo+ has unlimited)

### N6 — Founder seats counter pill

**Where it shows:** inside upgrade overlays N2/N3/N10/N11 — small status pill below the price line.

**Style:**
```
┌────────────────────────────────────┐
│  ●  747 OF 1000 FOUNDER SPOTS LEFT │
└────────────────────────────────────┘
```
8dp green dot when seats remain, fades to muted text when < 100 left, hides entirely once 0 remain.

**Live updates:** subscribes to a server-pushed counter (not polled). When user taps CTA, immediately reserves a seat with a 10-min hold (server-authoritative). If the user abandons checkout, the hold releases.

**Honesty:** if seats are exhausted, the pill changes to `0 OF 1000 FOUNDER SPOTS — STANDARD PRICING NOW` (gray dot). Don't fake scarcity.

### N7 — Tier selection / pricing screen

**Style:** standard screen header (`← UPGRADE`) + scrolling section per tier.

**Per-tier section:**
```
TIER NAME
$X/MO
[hero feature one-liner]

WHAT'S INCLUDED:
  ● Item 1
  ● Item 2
  ● Item 3
  ...

[BONUSES (if any):]
  ★ Founder Pricing Lock — 747 of 1000 spots left
  ★ 14-day trial, no CC required

[ CURRENT TIER ] (if active)
or
[ TRY FREE 14 DAYS — NO CC ]   ← primary action
[ START IMMEDIATELY ($X/mo) ]   ← secondary action
```

Each tier section separated by `ConsoleSeparator`. Current tier section gets a `surface-variant` background tint to distinguish.

**Sort order:** Free → Solo → Advanced → Enterprise (always — no per-user reordering).

**Anchor table at bottom:** "JobTread $199 · Knowify $78 · ServiceTitan $398 · Smith Net Solo $2.99" (per OFFER_ARCHITECTURE.md anchoring).

### N8 — Subscription detail screen

**Where:** Settings > Subscription (new row in existing SettingsScreen).

**Layout:** standard settings sections.

```
← SUBSCRIPTION

CURRENT TIER
  Smith Net Solo
  $2.99 / month  (billed monthly)

NEXT BILL
  May 30, 2026
  Visa ending 4242

FOUNDER PRICING
  ● Locked at $2.99/mo for life

CHANGE TIER
  > Upgrade to Advanced  ($9.99/mo)
  > Upgrade to Enterprise ($50/mo)
  > Switch to annual (save $5.98/yr)

PAYMENT METHOD
  > Update card

DATA
  > Export my data
  > Cancel subscription
  > Delete account
```

Cancel is a normal-styled row, NOT styled as a destructive action. (Delete account IS destructive; that gets a confirmation dialog.)

### N9 — Branded PDF stamp (server-side, not Compose)

**Where:** in `templates/invoice.html` and `templates/proposal.html` for Free-tier users only.

**Footer style:**
```
─────────────────────────────────────
Sent via Smith Net — smithnet.app
A deterministic tool for contractors. Try free →
```

Monospace, muted color, single line if possible, two if needed. Email signature mirror:
```
--
Sent via Smith Net (smithnet.app)
```

Tier resolver decides at PDF render time whether to inject the stamp (Solo+ → no stamp). One template, conditional block.

### N12 — Tier-gate Toast

Standard Android `Toast`, but emits a `gate_hit.*` telemetry event before showing. Used as fallback / supplement to overlays — NOT the primary upgrade prompt.

Example uses:
- After cancel from overlay N2: `Toast.makeText(ctx, "Maybe later. Free tier active.", LENGTH_SHORT)` + `gate_hit.plan_compiler_preview_dismissed`
- On seventh PDF attempt in same session: same `Toast` reminding them of the cap

---

## 4. Tier gates vs role gates — the UX rule

Existing app **HIDES** features the user's role can't access (Solo doesn't see MESH CONNECTION at all).

Net-new tier gates **SHOW** features the user's tier can't access — with a lock and a CTA.

Why the asymmetry: role gates protect features that **shouldn't apply** (a solo user has no crew, so showing crew settings is just clutter). Tier gates protect features the user **wants** but hasn't paid for. Hiding them defeats the conversion mechanic.

| Gate type | UX treatment |
|---|---|
| Role gate (e.g. solo without `Permission.GATEWAY_RELAY`) | Section/feature is hidden entirely |
| Tier gate (e.g. Free without PLAN Compiler) | Section/feature is visible, dimmed, with upgrade CTA |
| Both gates apply | Role gate wins (hide entirely) |

## 5. Onboarding changes (minimal)

The existing `OnboardingScreen.kt` (798 lines) ships and is not in scope for redesign. The only addition:

**After existing onboarding completes**, navigate to a new screen `WelcomeToOpenScreen`:
```
WELCOME TO SMITH NET OPEN

You're on the Free tier.

What you have:
  ● 1 active job
  ● 5 PDF sends per month
  ● Standard invoice template
  ● Mesh comms, even offline

Want to try Solo for 14 days?
  ● Unlimited jobs and PDFs
  ● PLAN Compiler unlocked
  ● No CC required
  ● Founder pricing: $2.99/mo for life (747 of 1000 spots left)

[ START SOLO TRIAL — NO CC ]
[ Stay on Open ]
```

This is the ONE proactive upgrade prompt at signup. After this, all upgrade prompts are trigger-driven.

## 6. Settings screen — net-new entries

Add THREE new section rows (not new sections — rows in existing sections), placed by intent:

| Add to existing section | New row | Action |
|---|---|---|
| (NEW SECTION above PROFILE) `SUBSCRIPTION` | `Smith Net Open` (or current tier) + `$0/mo` (or price) | Tap → N8 Subscription detail screen |
| (top of WORK MODE or near connectivity) | n/a | (no new row needed; trial banner is global) |
| Existing AI ASSISTANT section | If Solo, replace current AI section with an "AI Assistant — locked" row | Tap → N10 lock overlay |

Subscription section always lives **above PROFILE** so it's the first thing a user sees in Settings — that's where they think about money.

## 7. Dashboard — net-new tile

Add ONE tile to the Dashboard (in the `quickActions` rendered by `getQuickActions(role, ...)`):

- **For Free tier:** a quick-action tile labeled `UPGRADE` (chevron right). Taps to N7 pricing screen.
- **For Solo tier:** a quick-action tile labeled `ADD SMITHAI` (chevron right). Taps to N7 pricing screen, scrolled to Advanced.
- **For Advanced tier:** a quick-action tile labeled `ADD CREW` (chevron right). Taps to N7 pricing screen, scrolled to Enterprise.
- **For Enterprise tier:** no upgrade tile (top of ladder).

Tile uses existing tile pattern; only labels differ.

## 8. PLAN tab — net-new states

The existing PLAN UI (`ui/plan/IntentComponents.kt`) is the canvas. For Free users:

- The "Compose new plan" entry is REPLACED with the locked overlay (N2/N3) showing a *static rendered example* of a compiled plan as the dimmed background.
- For all users, list views of existing engagements/plans remain visible (because Free tier still tracks a job and its docs).
- The "compile" action button is the gate — tapping it as a Free user fires the overlay.
- Solo+ users see the existing UI unchanged.

## 9. Invoice send flow — net-new states

Existing send dialog gets:
- A counter line at bottom for Free: `4 of 5 free sends used this month` (no caps users → hidden)
- A 6th-send block: triggers N5 overlay (PDF cap exceeded)
- Branding stamp (N9) injected into PDF + email at server render time

## 10. Settings > AI Assistant — net-new state for Solo users

Solo users see:
```
AI ASSISTANT
  ●  Locked — Advanced tier
  Tap to learn what SmithAI does

[ Try Advanced free 30 days — no CC ]
```

Tapping the row opens N10 overlay. Locks are visible on the AI Assistant settings, NOT hidden — this is a tier gate.

## 11. Things explicitly NOT changing

- ✅ DashboardScreen layout / modules / scroll behavior — unchanged
- ✅ JobBoardScreen and pipeline — unchanged
- ✅ ConversationScreen / ChatListScreen / ChannelListScreen — unchanged
- ✅ NewJobFlow — unchanged (except the cap check fires from server, not in this UI)
- ✅ TimeTrackingScreen, ExpensesScreen, JobExpenseDetailScreen — unchanged
- ✅ MediaPlayer — unchanged
- ✅ Bottom toolbar / Left sidebar — unchanged
- ✅ Existing color tokens, typography, spacing — unchanged
- ✅ Existing icons / glyphs — unchanged
- ✅ Existing settings sections (Profile, Privacy, Work Mode, Trade Role, Mesh Connection, AI Assistant) — unchanged in layout

## 12. Accessibility (carries over from NFR-A1-A5)

- All net-new tap targets ≥ 44dp (matches existing app)
- ALL CAPS labels remain readable; complement with TalkBack `contentDescription` on every locked-feature overlay so the lock is announced
- Color is never the sole carrier of meaning — every locked state has a text label "Locked", every status dot has accompanying text
- Dynamic type respected on net-new components

## 13. Linked specs

- [EXTRACTED-PATTERNS.md](../design/EXTRACTED-PATTERNS.md) — the design system extracted from existing code (binding constraint)
- [INSPIRATION.md](../design/INSPIRATION.md) — what to draw from, what to avoid
- [USER-JOURNEYS.md](../journeys/USER-JOURNEYS.md) — the 8 user journeys with tier-gate trigger points called out
- [STATE-COVERAGE.md](../journeys/STATE-COVERAGE.md) — every state of the 12 net-new surfaces
- [WIREFRAMES.md](WIREFRAMES.md) — ASCII wireframes for each net-new surface
- [../specs/OFFER_ARCHITECTURE.md](../specs/OFFER_ARCHITECTURE.md) — the conversion script copy I'm rendering
- [../specs/pricing-config.json](../specs/pricing-config.json) — tier ceilings driving the gates
