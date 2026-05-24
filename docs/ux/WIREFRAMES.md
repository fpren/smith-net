# Smith Net — Wireframes (net-new UI only)

**Scope:** ASCII wireframes for the 12 net-new surfaces from UX-DESIGN.md. Existing screens are not re-wireframed (they ship as-is).

**Convention:**
- All wireframes assume Android phone portrait (≈360dp wide)
- `~` characters approximate the existing dividers (`ConsoleSeparator`)
- All-CAPS text = ALL-CAPS in render (`ConsoleTheme.captionBold` or `title`)
- Mixed case text = mixed case in render (`ConsoleTheme.body`)
- `●`/`○` = filled/empty status dots (Signal-style)
- `>` = chevron right
- `[ ... ]` = button (text + border, no fill unless primary)
- `[[ ... ]]` = primary CTA (filled with primary blue, on-primary white text)
- All on light theme — `#F6F8FA` page bg, `#FFFFFF` surface bg, monospace throughout

---

## N1 — Trial banner (every screen during trial)

### Solo trial day 1-7
```
┌────────────────────────────────────────────┐
│ SOLO TRIAL · 14 DAYS LEFT · TAP TO LOCK    │
│ FOUNDER PRICING                            │
└────────────────────────────────────────────┘
[existing screen content begins below]
```

### Solo trial day 8-12
```
┌────────────────────────────────────────────┐
│ SOLO TRIAL · 7 DAYS LEFT · 747 FOUNDER     │
│ SPOTS LEFT                                 │
└────────────────────────────────────────────┘
```

### Solo trial day 13-14
```
┌────────────────────────────────────────────┐
│ SOLO TRIAL ENDS IN 2 DAYS · TAP TO STAY    │
│ SOLO                                       │
└────────────────────────────────────────────┘
```

### Trial expired
```
┌────────────────────────────────────────────┐
│ TRIAL ENDED · YOU'RE ON OPEN · TAP TO      │
│ REACTIVATE                                 │
└────────────────────────────────────────────┘
```

**Style notes:**
- 1dp outline-bottom (`#D0D7DE`), no top border (sits flush with system status bar via existing theme)
- Surface bg `#FFFFFF`, primary text `#1F2328`
- 6dp vertical, 12dp horizontal padding
- Wraps to 2 lines on 360dp width — never truncate

---

## N2 / N3 — PLAN Compiler locked overlay (Free user opens PLAN tab or composes)

```
                   PAGE BG #F6F8FA
┌────────────────────────────────────────────┐
│                                            │
│  PLAN COMPILER                             │
│                                            │
│  Your plan, compiled.                      │
│  Runs the same way every time.             │
│                                            │
│  SOLO · $2.99/MO                           │
│  ● 747 OF 1000 FOUNDER SPOTS LEFT          │
│                                            │
│  [[ TRY SOLO FREE 14 DAYS — NO CC ]]       │
│  Maybe later                               │
│                                            │
│  ───────────────────────────────────────   │
│                                            │
│  ┌─────────────── DIMMED 0.4 ALPHA ─────┐  │
│  │ INTENT  v3 · CONFIRMED                │  │
│  │ Mrs Lee Kitchen Rewire                │  │
│  │                                       │  │
│  │ SCOPE                                 │  │
│  │   Replace 200A panel...               │  │
│  │ JOBS    [3]                           │  │
│  │ TIME    24h 12m                       │  │
│  │ MATERIALS   $1,247.30                 │  │
│  │                                       │  │
│  │ COMPILED ARTIFACT                     │  │
│  │   serial: SA-00482                    │  │
│  │   sha256: a3f9c1...                   │  │
│  │   sealed: 2026-04-29T19:14Z           │  │
│  └───────────────────────────────────────┘  │
│                                            │
└────────────────────────────────────────────┘
```

**Notes:**
- Top card: surface bg, 14dp padding, no border (unlike rows — this is the "card")
- Title: `captionBold`, primary blue `#0969DA`
- Body: 2 lines, `body` style
- Price line: `bodyBold`
- Founder counter: green dot + `body` text
- Primary CTA: filled rect, primary blue bg, white text, 14dp vertical padding
- "Maybe later": text-only, `caption` muted
- Bottom dimmed area: shows live preview of *user's actual data* if they have a job, or a single canned example if their data is empty (anonymized: "Your first job will look like this")

---

## N4 — Active-job cap soft wall

```
┌────────────────────────────────────────────┐
│  ONE ACTIVE JOB AT A TIME                  │
│                                            │
│  Smith Net Open caps at 1.                 │
│  Close your active job to start another,   │
│  or unlock unlimited with Solo.            │
│                                            │
│  SOLO · $2.99/MO                           │
│  ● 747 OF 1000 FOUNDER SPOTS LEFT          │
│                                            │
│  [[ TRY SOLO FREE — NO CC ]]               │
│  See active job                            │
│                                            │
│  ───────────────────────────────────────   │
│                                            │
│  ┌──────────── DIMMED 0.4 ALPHA ─────────┐ │
│  │  > Mrs Lee Kitchen Rewire             │ │
│  │    Active · 12h logged · 3 invoices   │ │
│  └───────────────────────────────────────┘ │
│                                            │
└────────────────────────────────────────────┘
```

---

## N5 — PDF send cap

### In-dialog footer (counter at 0-4 sends used)
```
[existing PDF send dialog above]

  ───────────────────────────────────────
  4 of 5 free sends used this month
```

### Cap-hit overlay (6th send attempt)
```
┌────────────────────────────────────────────┐
│  5 SENDS PER MONTH                         │
│                                            │
│  You've sent 5 PDFs this month on Open.    │
│  Unlimited with Solo.                      │
│                                            │
│  SOLO · $2.99/MO                           │
│  ● 747 OF 1000 FOUNDER SPOTS LEFT          │
│                                            │
│  [[ TRY SOLO FREE — NO CC ]]               │
│  Next month: in 19 days                    │
│                                            │
│  ───────────────────────────────────────   │
│                                            │
│  Your draft is saved. Will send Day 1 of   │
│  next month if you stay on Open.           │
│                                            │
└────────────────────────────────────────────┘
```

---

## N6 — Founder seats counter pill (embedded in overlays)

```
● 747 OF 1000 FOUNDER SPOTS LEFT       (default green)
● 47 OF 1000 FOUNDER SPOTS LEFT        (muted text, < 100)
● 3 OF 1000 FOUNDER SPOTS LEFT         (primary blue text, < 10)
0 OF 1000 SPOTS — STANDARD PRICING NOW (gray dot, exhausted)
· · · LOADING SPOTS · · ·              (loading)
```

---

## N7 — Tier selection / pricing screen

```
┌────────────────────────────────────────────┐
│ ← UPGRADE                                  │
└────────────────────────────────────────────┘
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  SMITH NET OPEN
  $0/MO

  Deterministic baseline. Try everything.

  WHAT'S INCLUDED:
    ● Basic job + client tracking
    ● 1 active job at a time
    ● Standard invoice template
    ● 5 PDF sends per month
    ● Mesh comms (works offline)
    ● Smith Net branding on PDFs

  [ CURRENT TIER ]                            ← if Open
                  OR
  [ DOWNGRADE TO OPEN ]                       ← if higher

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  SMITH NET SOLO
  $2.99/MO

  PLAN Compiler. Unlimited everything.

  WHAT'S INCLUDED:
    ● Everything in Open
    ● PLAN Compiler (the moat)
    ● Cord-based state model
    ● Unlimited active jobs
    ● Unlimited PDF sends
    ● No Smith Net branding

  BONUSES:
    ★ Founder Pricing Lock — $2.99/mo for life
    ● 747 OF 1000 FOUNDER SPOTS LEFT
    ★ 14-day trial, no CC required
    ★ One-click data export anytime

  [[ TRY SOLO FREE 14 DAYS — NO CC ]]
  [ Start immediately ($2.99/mo) ]

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  SMITH NET ADVANCED
  $9.99/MO

  Add SmithAI on-device + Advanced template.

  WHAT'S INCLUDED:
    ● Everything in Solo
    ● SmithAI (on-device, no cloud)
    ● AI proactive suggestions
    ● Advanced invoice template
    ● Priority email support (24hr)

  BONUSES:
    ★ Lifetime Template Library
    ● 47 OF 100 LIFETIME SPOTS LEFT
    ★ AI roadmap input
    ★ 30-day trial, no CC required

  [[ TRY ADVANCED FREE 30 DAYS — NO CC ]]
  [ Start immediately ($9.99/mo) ]

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  SMITH NET ENTERPRISE
  $50/MO  (flat — not per seat)

  Crew + Enterprise template.

  WHAT'S INCLUDED:
    ● Everything in Advanced
    ● Multi-user / crew accounts
    ● Shared jobs across crew
    ● Crew-aware SmithAI
    ● Enterprise invoice template
    ● Priority phone + email
    ● 1hr Zoom onboarding (founder)
    ● Dedicated Slack channel

  BONUSES:
    ★ Founder Annual: $500/yr ($600 standard)
    ● 7 OF 10 FOUNDER ANNUAL SPOTS LEFT
    ★ Crew onboarding kit (printed cards)
    ★ Custom plan template (first 90 days)

  [[ START 14-DAY ENTERPRISE TRIAL ]]

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  ANNUAL?  [ ○ Monthly  ●  Annual — save 16.7% ]

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  WHY OUR PRICE LOOKS LIKE A TYPO

  JobTread        $199/mo
  Houzz Pro        $85/mo
  Knowify          $78/mo
  ServiceTitan    $398/mo
  Smith Net Solo  $2.99/mo

  Same problem. Different math.

└────────────────────────────────────────────┘
```

---

## N8 — Subscription detail screen

### Solo paid (most common after launch)
```
┌────────────────────────────────────────────┐
│ ← SUBSCRIPTION                             │
└────────────────────────────────────────────┘
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  CURRENT TIER

    Smith Net Solo
    $2.99 / month  (billed monthly)

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  NEXT BILL

    May 30, 2026
    Visa ending 4242

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  FOUNDER PRICING

    ● Locked at $2.99/mo for life

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  CHANGE TIER

    > Upgrade to Advanced  ($9.99/mo)
    > Upgrade to Enterprise ($50/mo)
    > Switch to annual (save $5.98/yr)

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  PAYMENT METHOD

    > Update card

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  DATA

    > Export my data
    > Cancel subscription
    > Delete account

└────────────────────────────────────────────┘
```

### Open tier (minimal)
```
┌────────────────────────────────────────────┐
│ ← SUBSCRIPTION                             │
└────────────────────────────────────────────┘
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  CURRENT TIER

    Smith Net Open
    $0 / month

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  CHANGE TIER

    > Upgrade

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  DATA

    > Export my data
    > Delete account

└────────────────────────────────────────────┘
```

### Cancel confirmation dialog (custom Composable, NOT Material AlertDialog)
```
┌────────────────────────────────────────────┐
│  CANCEL SUBSCRIPTION?                      │
│                                            │
│  Your Solo features stay until end of      │
│  current period (May 30).                  │
│                                            │
│  After that you'll be on Open. Your data   │
│  stays.                                    │
│                                            │
│  [[ KEEP SOLO ]]    [ Cancel anyway ]      │
└────────────────────────────────────────────┘
```

---

## N9 — Branded PDF stamp (rendered HTML)

### Invoice PDF footer (Free tier only)
```
─────────────────────────────────────────────
INVOICE LINE ITEMS
   Labor  10h × $85    $850.00
   Materials           $124.50
   Subtotal            $974.50
   Tax (8.25%)          $80.40
   ─────────────────
   Total             $1,054.90

─────────────────────────────────────────────
[CONTRACTOR NAME / LICENSE / CONTACT block]

─────────────────────────────────────────────
                       ┌─────────────────┐
                       │ Sent via Smith  │
                       │ Net — smithnet  │
                       │ .app  · A       │
                       │ deterministic   │
                       │ tool for        │
                       │ contractors.    │
                       │ Try free →      │
                       └─────────────────┘
```

### Email signature (Free tier only)
```
[user's email body]

--
Sent via Smith Net (smithnet.app)
```

**Note:** stamp injection at server template render time, not in Compose.

---

## N10 — Settings > AI Assistant locked state (Solo user)

In existing SettingsScreen.kt scroll, the AI ASSISTANT section now renders:

```
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  AI ASSISTANT

  ┌─ surface row ─────────────────────────┐
  │  ●  Locked — Advanced tier            │
  │  Tap to learn what SmithAI does       │
  │                              >        │
  └───────────────────────────────────────┘

  [ Try Advanced free 30 days — no CC ]   ← text-link

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
```

Tapping the row → N10 overlay (same overlay component as N2).

---

## N11 — Crew invite locked overlay (Solo or Advanced taps Invite)

```
┌────────────────────────────────────────────┐
│  CREW MODE                                 │
│                                            │
│  Bring your crew on the same plan.         │
│  $50/mo for the whole team — not per seat. │
│                                            │
│  ENTERPRISE · $50/MO                       │
│  ● 7 OF 10 FOUNDER ANNUAL SPOTS LEFT       │
│                                            │
│  [[ START 14-DAY ENTERPRISE TRIAL ]]       │
│  Maybe later                               │
│                                            │
│  ───────────────────────────────────────   │
│                                            │
│  ┌──────────── DIMMED 0.4 ALPHA ─────────┐ │
│  │  CREW                                 │ │
│  │    + INVITE COLLEAGUE                 │ │
│  │                                       │ │
│  │  ● Pending: jane@contractorlife.com   │ │
│  │                                       │ │
│  │  CREW JOBS                            │ │
│  │    > Mrs Lee Kitchen Rewire (you, 1)  │ │
│  └───────────────────────────────────────┘ │
│                                            │
└────────────────────────────────────────────┘
```

---

## N12 — Tier-gate Toast (standard Android Toast)

```
       ┌─────────────────────────────┐
       │  Maybe later. Free tier     │
       │  active.                    │
       └─────────────────────────────┘
```

Standard Toast — bottom-anchored, 3.5s LENGTH_SHORT, monospace via app theme.

---

## WelcomeToOpenScreen (post-onboarding, before dashboard)

```
┌────────────────────────────────────────────┐
│  WELCOME TO SMITH NET OPEN                 │
│                                            │
│  You're on the Free tier.                  │
│                                            │
│  WHAT YOU HAVE:                            │
│    ● 1 active job                          │
│    ● 5 PDF sends per month                 │
│    ● Standard invoice template             │
│    ● Mesh comms — even offline             │
│                                            │
│  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ │
│                                            │
│  WANT TO TRY SOLO FOR 14 DAYS?             │
│    ● Unlimited jobs and PDFs               │
│    ● PLAN Compiler unlocked                │
│    ● No CC required                        │
│    ● Founder pricing: $2.99/mo for life    │
│      ● 747 OF 1000 FOUNDER SPOTS LEFT      │
│                                            │
│  [[ START SOLO TRIAL — NO CC ]]            │
│  [ Stay on Open ]                          │
│                                            │
└────────────────────────────────────────────┘
```

---

## Settings screen — net-new SUBSCRIPTION section (placed above PROFILE)

```
[← SETTINGS header — existing]
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  SUBSCRIPTION

  ┌─ surface row ─────────────────────────┐
  │  Smith Net Solo                       │
  │  $2.99 / month  ·  Founder locked     │
  │                              >        │
  └───────────────────────────────────────┘

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  PROFILE   [existing — unchanged]

  Set up profile
  Name, trade, rates, billing                >

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  PRIVACY   [existing — unchanged]

  ...
```

---

## Dashboard — net-new quick-action tile

The existing `getQuickActions(role, ...)` adds one tile per current tier:

```
[existing dashboard above]

QUICK ACTIONS
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
│REPORTS │ │SUPPLY  │ │ARCHIVE │ │UPGRADE │  ← NEW (Free tier shown)
└────────┘ └────────┘ └────────┘ └────────┘
                                      ^^^^
                              tap → N7 pricing

For Solo tier: tile reads ADD SMITHAI
For Advanced tier: tile reads ADD CREW
For Enterprise tier: NO upgrade tile (top of ladder)
```

Tile uses existing tile pattern (4-grid, surface bg, monospace caption) — only the label and target route differ.

---

## Color usage reference (only colors used in net-new UI)

```
┌──────────────────────────────────────┐
│ #F6F8FA  page background             │
│ #FFFFFF  surface (rows, cards)       │
│ #EFF2F5  surface variant (subtle)    │
│ #1F2328  primary text                │
│ #656D76  muted text                  │
│ #D0D7DE  outline / separator         │
│ #0969DA  primary actions (CTA fill)  │
│ #FFFFFF  text on primary fill        │
│ #1A7F37  status green (online,paid)  │
│ #DCFFE4  success tint background     │
│ #7D8590  status grey (offline)       │
└──────────────────────────────────────┘

NO OTHER COLORS. NO DARK MODE. NO GRADIENTS. NO SHADOWS.
```

---

## Wireframe coverage check

| Surface | Wireframe | States covered |
|---|---|---|
| N1 Trial banner | ✅ | 4 example variants (day 1-7, 8-12, 13-14, expired) |
| N2/N3 PLAN overlay | ✅ | default + dimmed preview |
| N4 Active-job cap | ✅ | overlay variant |
| N5 PDF cap | ✅ | counter footer + overlay |
| N6 Founder counter | ✅ | 5 states (default, low, almost gone, exhausted, loading) |
| N7 Pricing screen | ✅ | full screen with all 4 tiers |
| N8 Subscription detail | ✅ | Solo paid + Open + cancel dialog |
| N9 PDF stamp | ✅ | invoice footer + email signature |
| N10 AI Settings lock | ✅ | section render |
| N11 Crew invite lock | ✅ | overlay variant |
| N12 Toast | ✅ | render |
| WelcomeToOpenScreen | ✅ | first-time path |
| Settings SUBSCRIPTION row | ✅ | placement above PROFILE |
| Dashboard UPGRADE tile | ✅ | placement |

**Total wireframes: 14 (12 net-new + 2 entry-point insertions).** Step 5 (Wireframe Prototypes) will turn these into per-flow PRDs with component specs.
