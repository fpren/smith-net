# Smith Net — Offer Architecture

**Method:** Hormozi $100M Offers / Grand Slam Offer framework, applied to the locked tier ladder (Free → Solo $2.99 → Advanced $9.99 → Enterprise $50).

**Goal:** make each upgrade feel like a no-brainer ("yes, obviously") at the moment of trigger, while protecting the moat (PLAN Compiler) and the AI floor (Advanced).

---

## 1. The Grand Slam Offer skeleton (applied per tier)

| Lever | Free (Open) | Solo ($2.99) | Advanced ($9.99) | Enterprise ($50) |
|---|---|---|---|---|
| **Dream outcome** | "I have a real tool, not a demo" | "My plans run the way I planned them" | "My phone thinks alongside me" | "My crew runs the same play I do" |
| **Likelihood proof** | Deterministic rule engine, no internet required | PLAN Compiler is bit-for-bit reproducible (NFR-D3) | SmithAI runs on-device, no cloud dependency | Cord state syncs across crew via mesh + Supabase |
| **Time delay** | Instant — works out of the box | Instant on upgrade | < 30s to load model | Instant invite, immediate sync |
| **Effort & sacrifice** | $0, branding on PDFs | $2.99/mo | $9.99/mo | $50/mo for the whole crew (not per seat) |
| **Bonuses** | (free is the bonus) | Founder pricing lock + 14-day trial no-CC + no-branding stamp | Solo trial extension + free Advanced template library | White-glove onboarding session + dedicated Slack |
| **Urgency** | n/a | Founder pricing locked for first 1000 paid Solos | First 100 Advanced get lifetime template library | First 10 Enterprises get founder annual pricing |
| **Scarcity** | n/a | Founder pricing — 1000 seats only, ever | Lifetime template library — 100 seats only | Founder annual pricing — 10 seats only |
| **Guarantee** | "free, forever, no CC" | "cancel anytime, no contract, downgrade keeps your data" | "downgrade to Solo any time without losing data, plans, or jobs" | "30-day money-back if you can't get your crew onboarded" |
| **Naming** | **Smith Net Open** | **Smith Net Solo** | **Smith Net Advanced** | **Smith Net Enterprise** |

---

## 2. Anchoring (price comparison frame)

**The pitch headline (when shown next to competitor pricing):**

> JobTread is $199/mo. Houzz Pro starts at $85. Knowify is $78. ServiceTitan starts at $398.
> **Smith Net Solo is $2.99.**
> Same problem. Different math.

**Why this lands:**
- The competitor anchor makes the $2.99 number feel like a typo (in our favor).
- It avoids a feature war ("we have X that they don't") because that's losable. Price is a moat the competitor can't match without restructuring their entire sales-led GTM.
- Even at Enterprise ($50), we're below the per-user cost of any sales-led competitor.

**Where to show the anchor:**
- Pricing page (always)
- In-app upgrade modal (subtitle line: "Less than your morning coffee")
- Onboarding email day 7 (when free user has been active 1 week)

---

## 3. Per-tier offer detail

### 3.1 Smith Net Open (Free)

**Pitch:** "Use Smith Net for one active job, free forever. Your invoices go out branded — that's how we get paid."

**What's included:**
- Basic job + client tracking
- 1 active job at a time (close to start a new one)
- Standard invoice template
- 5 PDF sends/month
- Smith Net branding on every PDF + email signature
- Rule-based fallback (no SmithAI)
- Single user
- Mesh transport (offline comms still works)
- 14-day Solo trial offered on signup (auto-downgrades to Open if not converted, **no CC required**)

**What's NOT included (with visible upgrade CTAs):**
- PLAN Compiler ("Preview only — Upgrade to Solo to compile" CTA)
- Cord-based state model
- SmithAI
- Advanced / Enterprise templates
- Crew / multi-user
- Unlimited active jobs / PDFs

**The branding is the bargain.** Free users get a real working tool; we get a marketing surface that scales with their activity.

### 3.2 Smith Net Solo ($2.99/mo)

**Pitch:** "Compile your plans. Unlimited jobs, unlimited invoices. Less than a coffee."

**What's included:**
- Everything in Open
- **PLAN Compiler** — deterministic plan execution (the moat)
- **Cord-based state model** — work-state coordination across mesh + cloud
- Unlimited active jobs
- Unlimited PDF sends
- **No Smith Net branding** on PDFs/emails (your business, your brand)
- Standard invoice template
- Single user
- 14-day full trial without CC

**Bonuses (stacked to make $2.99 feel undervalued):**
- 🔒 **Founder Pricing Lock** — first 1000 paid Solos lock in $2.99/mo *forever*, even if we raise the price later (we will)
- 📦 **No data lock-in** — downgrade or cancel any time, your jobs/invoices/clients stay accessible from Open tier
- 📤 **One-click data export** (CSV + JSON) — never feel trapped
- 🚫 **No CC required for trial** — capture the card after the user decides

**Urgency:** Founder pricing ends after the 1,000th paid Solo signup. Display a "X seats left" counter on the pricing page once we cross 500.

**Scarcity:** Founder pricing is a one-time event. Once the 1000 seats are gone, new Solos pay the (future, higher) standard rate.

**Guarantee:** "Cancel anytime. No contract. No questions. We don't even charge for the first 14 days."

**Why this converts:** The Free tier user who hits the active-job cap on day 8 sees:
- Their active job is real revenue
- The fix is $2.99 (less than what they'll spend in gas getting to the next job)
- They lock in $2.99 forever if they're early
- They can quit any day

That's a no-brainer at the moment of need.

### 3.3 Smith Net Advanced ($9.99/mo)

**Pitch:** "Add SmithAI on-device. The brain that watches every job and never sleeps — except when your battery does."

**What's included:**
- Everything in Solo
- **SmithAI on-device assistant** — full agent state machine (SLEEPING → WAKING → ALIVE)
- AI proactive suggestions, summaries, plan-authoring assist
- Tool integration (web search, weather, code exec)
- Context-aware (sees jobs, expenses, schedule, time entries — solo-mode only sees your data)
- **Advanced invoice template** — line-item richness, terms, payment status, branding
- Single user

**Bonuses:**
- 🎁 **Lifetime Template Library** — first 100 Advanced subscribers get all future invoice templates we add, free, forever (this is a real bonus — we're shipping new templates regularly)
- 🤖 **AI tool roadmap input** — early Advanced users vote on next AI tool integrations (concrete: SMS estimate replies, weather-aware scheduling, material price lookup)
- 📈 **Priority email support** — 24hr response vs 72hr on Free/Solo
- 🔓 **Trial extension** — Advanced trial is 30 days (vs 14 for Solo)

**Urgency:** Lifetime Template Library is capped at 100 subscribers ever. Display "X of 100 Lifetime spots left" until they're gone.

**Scarcity:** Lifetime template library is one-time only.

**Guarantee:** "Downgrade to Solo any time. Your jobs, your plans, your cord state — all stay. SmithAI just goes back to sleep."

**Why this converts:** A Solo user opens the AI Assistant tab, sees a locked screen with:
- "SmithAI runs on your device. No cloud. No subscription per query."
- "Upgrade to Advanced for $9.99/mo. First 100 get lifetime template library."
- "[Try free for 30 days — no CC]"

The differentiation vs cloud AI tools: on-device, no per-query cost, works offline. That's worth $7 over Solo to a contractor doing 5+ invoices/mo.

### 3.4 Smith Net Enterprise ($50/mo — flat, not per seat)

**Pitch:** "Run your crew on the same plan. One price for the whole team."

**What's included:**
- Everything in Advanced
- **Multi-user / crew accounts** — invite colleagues, scoped permissions
- Shared jobs across the crew
- Crew-aware SmithAI (sees crew comms, time, expenses — privacy-gated per role)
- **Enterprise invoice template** — multi-payer, milestone billing, draw schedules
- Priority phone + email support
- White-glove onboarding session (1hr Zoom with founder)
- Dedicated Slack channel for direct line to product team

**Bonuses:**
- 🎯 **Founder Annual Pricing** — first 10 Enterprise customers lock in $500/yr (vs $600/yr standard) for life
- 🧑‍🏫 **Crew onboarding kit** — printed quick-start cards we ship to your crew
- 🔧 **Custom plan templates** — we'll build 1 custom plan template for your business in the first 90 days

**Urgency:** Founder annual pricing is 10 customers only. Show "X of 10 Founder spots left" until gone.

**Scarcity:** Founder annual pricing is a one-time event.

**Guarantee:** "30-day money-back. If you can't get your crew on the platform in 30 days, we refund you fully and help you migrate your data out."

**Why this converts:** Compared to ServiceTitan ($398+/user/mo) or JobTread Pro ($399/mo per company), $50/mo flat is a fraction of category. The white-glove onboarding removes the #1 reason crews don't adopt new tools (training friction).

---

## 4. Trial mechanics

| Tier | Trial length | CC required | Auto-action at end | Conversion CTA timing |
|---|---|---|---|---|
| Free (Open) | n/a — free forever | No | n/a | Tier-gate triggered, not time-triggered |
| Solo | 14 days | **No** | Auto-downgrade to Open | Day 1, Day 7 (active-use email), Day 12 (final reminder) |
| Advanced | 30 days | **No** | Auto-downgrade to Solo (or Open if not paid Solo) | Day 1, Day 14 (when SmithAI has built memory), Day 28 |
| Enterprise | 14 days | **Yes — but full refund up to day 30** | Begin charging on day 15 | Day 1 (kickoff Zoom), Day 7, Day 13 |

**No-CC-trial design rationale (Solo + Advanced):** the friction of entering a CC pre-trial is the #1 conversion killer in this segment. Capture the card *after* the user has decided. We accept the trade-off (some users churn at trial-end without ever paying) because each free-trial-Solo is also a passive distributor (branded PDFs).

**Enterprise CC-required rationale:** Enterprise users have budgets, decision-makers, and intent. The friction of CC is acceptable; the refund window does the work that the no-CC trial does at lower tiers.

---

## 5. Annual pricing (decision: yes, ship at v1)

| Tier | Monthly | Annual (discount) | Annual cost | Implied "free months" |
|---|---|---|---|---|
| Open | $0 | n/a | n/a | n/a |
| Solo | $2.99 | $29.90/yr | $29.90 | 2 months free |
| Advanced | $9.99 | $99.90/yr | $99.90 | 2 months free |
| Enterprise | $50 | $500/yr (founder) / $600/yr (post-founder) | $500-600 | 2 months free |

**Annual is opt-in only at v1** (default to monthly to keep signup simple). Show annual as a toggle on the pricing page and as an upsell in-app after 30 days of paid monthly.

---

## 6. Refund & cancellation policy

| Tier | Refund window | Refund mechanism | Cancellation friction |
|---|---|---|---|
| Open | n/a | n/a | One-tap "Delete account" |
| Solo | None (we don't charge until trial ends) | n/a | One-tap cancel from Settings |
| Advanced | None (no charge until trial ends) | n/a | One-tap cancel from Settings |
| Enterprise | 30-day full refund | Founder issues manually | One-email cancel (cancel@smithnet.app) |

**Principle:** No friction on cancel. The data stays accessible from Open tier. Cancellation is a downgrade, not a loss. This builds the kind of trust that makes re-conversion later feel safe.

---

## 7. Naming & brand language

| Element | Convention |
|---|---|
| Product name | **Smith Net** (two words, capital S, capital N) |
| Free tier | **Smith Net Open** ("open" = unrestricted access, also evokes "open for business") |
| Paid tiers | **Solo / Advanced / Enterprise** (no marketing adjectives — let the price do the talking) |
| AI assistant | **SmithAI** (one word, capital S, capital A, capital I) |
| Compiler | **PLAN Compiler** (PLAN in caps to signal it's a product term) |
| State model | **Cord-based state model** (lowercase, technical) |
| Mesh | **Smith Mesh** (only when distinguishing from generic mesh) |

**Voice & tone:** terse, contractor-direct, no marketing fluff. "We charge less because the math works." Not "Empower your business with AI-powered insights."

---

## 8. The conversion script (in-app, plain English)

**At free→Solo trigger (active-job cap):**

> You've got 2 jobs in flight. Smith Net Open caps at 1.
>
> **Upgrade to Solo for $2.99/mo** — unlimited jobs, no branding, plans that compile.
> Founder pricing: lock $2.99 forever. **First 1000 only — X spots left.**
>
> [Try free for 14 days, no CC required] [Maybe later]

**At Solo→Advanced trigger (AI tab):**

> SmithAI runs on your device. It watches your jobs, your time, your invoices — and helps without ever sending your data to the cloud.
>
> **Add SmithAI for $9.99/mo** (Smith Net Advanced).
> First 100 get lifetime template library. **X of 100 Lifetime spots left.**
>
> [Try free for 30 days, no CC] [Maybe later]

**At Advanced→Enterprise trigger (crew invite):**

> Bring your crew on the same plan.
>
> **Upgrade to Enterprise for $50/mo** — entire crew, not per seat.
> Founder Annual: $500/yr (vs $600 later). **X of 10 Founder spots left.**
>
> [Schedule onboarding] [Maybe later]

---

## 9. Things we explicitly will NOT do

- **No "contact sales" wall** at any tier. All tiers self-serve.
- **No discounting via coupons** — founder pricing is the only "discount" mechanism, and it's structural, not promotional.
- **No fake scarcity** — the 1000 / 100 / 10 caps are real and enforced server-side; once gone, the offer is gone, and we publish the date it ended.
- **No "Pro" or "Business" tier marketing names** — Solo / Advanced / Enterprise = role-shaped, not status-shaped.
- **No annual-only tiers.** Monthly is the default at every paid tier.
- **No "freemium with ads."** We don't show third-party ads. The Smith Net brand stamp on Free PDFs is *our* marketing, not an ad network.
- **No "essentials" / "starter" / "lite" tier between Free and Solo.** The free tier *is* the starter.

---

## 10. Decisions still pending (for Step 2 / Step 11)

| # | Decision | When to make |
|---|---|---|
| O1 | Stripe vs Play Store IAP for web vs mobile billing | Step 2 (architecture) |
| O2 | How to surface "founder pricing — X spots left" counter (server-authoritative, real-time) | Step 11 (PRD) |
| O3 | Email automation provider (day-7, day-12 conversion emails) | Step 2 |
| O4 | How to handle a paid user who downgrades while in mid-PLAN-execution (cord state mid-flight) | Step 2 — depends on cord state design |
| O5 | Tax handling per region (sales tax US states, VAT EU, GST CA) — punted to Stripe Tax for v1? | Step 2 |
