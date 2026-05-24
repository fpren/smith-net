# Smith Net — User Journeys

Eight journeys covering the conversion funnel and core daily flows. Each names the **tier-gate trigger points** where net-new UI from UX-DESIGN.md fires.

Notation:
- 🟢 = no gate
- 🚪 = tier gate fires (one of N1-N12)
- 🔒 = role gate fires (existing app behavior)

---

## Journey 1: First-time install → first invoice sent (Free / Open)

**Goal:** new contractor installs, signs up, drafts and sends their first invoice within 10 minutes.

**Steps:**
1. 🟢 Install app from Play Store internal-testing track
2. 🟢 Onboarding flow (existing) — name, trade picker, role = solo
3. 🚪 **WelcomeToOpenScreen** (new) — choice of Solo trial or stay on Open
   - User chooses **Stay on Open** → continue to step 4
   - User chooses **Start Solo Trial** → divert to Journey 4
4. 🟢 Dashboard renders, jobs empty, GETTING STARTED tile visible
5. 🟢 Tap `+ NEW JOB`, fill (title, client, trade) → save
6. 🟢 Job detail opens; add a few time entries + materials
7. 🟢 Close job (existing flow)
8. 🟢 Tap `Generate invoice` → invoice draft populated from job data
9. 🟢 Review invoice (Standard template), edit if needed
10. 🟢 Tap `Send` — server renders PDF
11. 🚪 **N9 Branding stamp** injected into PDF + email automatically
12. 🟢 Customer receives branded PDF with `Sent via Smith Net — smithnet.app` footer
13. 🟢 Toast: `Invoice sent · 1 of 5 free PDFs used this month`

**Success metric (per SUCCESS-METRICS.md L5):** time from step 1 to step 13 < 10 minutes for median user.

**Drop-off risk points:**
- Step 2: trade picker overwhelm (mitigation: 121-trade picker is searchable per existing commit `ada0477`)
- Step 8: invoice flow not obvious from job detail
- Step 11: branding visibility may surprise user (mitigation: clear in WelcomeToOpenScreen)

---

## Journey 2: Free user hits active-job cap on day 8

**Goal:** free user tries to start a 2nd active job → converts to Solo via 14-day trial.

**Steps:**
1. 🟢 Day 8: user has 1 active job (from Journey 1, kept open across days)
2. 🟢 Real-life: a new client texts asking for a quote
3. 🟢 User opens Smith Net, taps `+ NEW JOB`
4. 🟢 Fills first field (title) — UI accepts input
5. 🚪 **N4 Active-job cap soft wall** fires on `Save` (server returns `403 tier_gate_exceeded gate_id=active_job_cap`)
6. 🟢 Telemetry: `gate_hit.active_job_cap` event fires (server-side, on the 403 emit)
7. 🟢 Overlay shows: title `ONE ACTIVE JOB AT A TIME`, body, two CTAs
8. **Branch A: User taps `[ TRY SOLO FREE — NO CC ]`**
   - 🚪 **N6 Founder seats counter** renders inside the overlay's CTA target screen
   - 🟢 Server reserves a founder seat (10-min hold)
   - 🟢 Trial begins — user routed to PLAN tab (NEW), unlocks PLAN Compiler
   - 🚪 **N1 Trial banner** appears at top: `SOLO TRIAL · 14 DAYS LEFT · ${X} FOUNDER SPOTS LEFT`
   - 🟢 User goes back, retries `+ NEW JOB`, succeeds
   - Telemetry: `tier_upgrade.trial_started`, `from_tier=open, to_tier=solo, trigger_event=gate_hit.active_job_cap`
9. **Branch B: User taps `[ See active job ]`**
   - 🟢 Returns to existing active job (no upgrade)
   - 🟢 Telemetry: `tier_upgrade.cta_dismissed, gate=active_job_cap`
   - 🟢 The friction sits with them; they may convert later

**Critical UX moment:** branding the cap as "1 active job at a time" (positive framing) vs "you've hit your free limit" (negative). The first reads as a product principle; the second reads as a paywall.

---

## Journey 3: Solo user opens AI tab on day 14 → Advanced trial

**Goal:** Solo user (paid for 14 days) explores the AI Assistant section in Settings → starts Advanced trial.

**Steps:**
1. 🟢 Solo user (paid 14 days) opens Settings
2. 🟢 Sees existing AI ASSISTANT section header
3. 🚪 **N10 AI Assistant locked row** renders: `● Locked — Advanced tier`
4. 🟢 User taps the row
5. 🚪 **N10 Locked-feature overlay** opens with title `SMITHAI`, body, founder counter `${X} OF 100 LIFETIME SPOTS LEFT`
6. 🟢 Telemetry: `gate_hit.ai_tab`
7. 🟢 User taps `[ TRY ADVANCED FREE 30 DAYS — NO CC ]`
8. 🟢 Server reserves Advanced lifetime-template seat (if available)
9. 🟢 Trial begins; SmithAI model download begins (one-time, ~2GB)
10. 🟢 Existing `AgentInitializer.wakeAgent` flow runs (SLEEPING → WAKING → ALIVE)
11. 🚪 **N1 Trial banner** updates to `ADVANCED TRIAL · 30 DAYS · SMITHAI IS LEARNING YOUR JOBS`
12. 🟢 Day 14 of trial: telemetry `gate_hit.ai_tab` likely fires multiple times — check that user actually used SmithAI (per leading indicator)
13. 🟢 Day 28 trial-end-2 reminder triggers Toast + email

**Failure mode to design for:** user upgrades, downloads model, then phone is too low-spec to load it (RULE_BASED_FALLBACK kicks in). UX: SmithAI section shows `MODEL UNSUPPORTED ON THIS DEVICE — DOWNGRADE TO SOLO?` with a no-fee downgrade CTA. We refund the trial pro-rata if past trial.

---

## Journey 4: Direct trial path from WelcomeToOpenScreen

**Goal:** new user opts into Solo trial immediately at signup.

**Steps:**
1. (Same as Journey 1, step 1-2)
2. 🚪 **WelcomeToOpenScreen** (new) — user taps `[ START SOLO TRIAL — NO CC ]`
3. 🚪 **N6 Founder seats** check — if available, reserved; if exhausted, message changes
4. 🟢 Trial begins, no CC
5. 🚪 **N1 Trial banner** appears immediately
6. 🟢 Dashboard renders; PLAN tab unlocked
7. 🟢 User can compose Plans, has unlimited jobs/PDFs from day 1
8. (Continue with normal usage as a Solo user)
9. 🟢 Day 12: `tier_upgrade.cta_shown` triggers an in-app prompt (not Toast — full overlay) reminding to lock founder pricing
10. **Day 14, branch:**
   - User adds CC during trial → `paid Solo` activated, founder pricing locked
   - User does not add CC → trial expires, **N1 trial banner** changes to `TRIAL ENDED · YOU'RE ON OPEN · TAP TO REACTIVATE`, all jobs/PDFs over caps lock (but data preserved)

**Why offer the trial at signup despite the moment-of-need-trigger philosophy?** Some users self-identify as "I bid 5 jobs a week, I need this now" before they ever hit a cap. Capturing them at signup with a no-CC trial removes the wait. Per OFFER_ARCHITECTURE.md, this is the ONE proactive prompt; everything else is trigger-driven.

---

## Journey 5: Advanced user adds a colleague → Enterprise upgrade

**Goal:** Advanced user (solo with SmithAI) tries to invite their first crew member.

**Steps:**
1. 🟢 Advanced user has been using SmithAI for ~30 days
2. 🟢 Job arrives that needs a helper — user navigates to existing Colleagues / Crew area
3. 🟢 Taps `+ INVITE COLLEAGUE`
4. 🚪 **N11 Crew invite locked overlay** fires with title `CREW MODE`, body, founder counter `${X} OF 10 FOUNDER ANNUAL SPOTS LEFT`
5. 🟢 Telemetry: `gate_hit.crew_invite`
6. 🟢 User taps `[ START 14-DAY ENTERPRISE TRIAL ]` (CC required for Enterprise)
7. 🟢 Stripe checkout (CC capture) → trial begins
8. 🚪 **N1 Trial banner** updates to `ENTERPRISE TRIAL · 14 DAYS · INVITE YOUR CREW`
9. 🟢 User invites colleague via email/SMS
10. 🟢 Colleague signs up, joins crew
11. 🟢 Day 14: charge begins (refund window 30 days post-charge per pricing-config.json)
12. 🟢 If user can't onboard crew within 30 days, full refund per guarantee

**Critical UX moment:** the Enterprise trial requires CC up front (per pricing-config.json design rationale: Enterprise users have budgets). The upgrade overlay must be CRYSTAL CLEAR that this is the only trial that captures CC.

---

## Journey 6: PDF send cap hit on day 11 (Free user)

**Goal:** Free user has sent 5 PDFs in a month → tries to send 6th.

**Steps:**
1. 🟢 User has sent 5 PDFs throughout the month (each existing send dialog showed counter at bottom)
2. 🟢 Tries to send a 6th invoice (taps `Send` from invoice screen)
3. 🚪 **N5 PDF cap overlay** fires with title `5 SENDS PER MONTH`, body, two CTAs
4. 🟢 Telemetry: `gate_hit.pdf_send_cap`
5. **Branch A: Tap upgrade**
   - 🟢 N7 pricing screen → Solo trial → unlimited sends
   - 🟢 Try send again, succeeds
6. **Branch B: Tap "Next month: in 19 days"**
   - 🟢 Overlay dismisses; user is told (Toast): `PDF send saved as draft. Will send Day 1 of next month if still on Open.`
   - 🟢 Server schedules a queued send (we don't lose their work)
   - 🟢 Telemetry: `gate_hit.pdf_send_cap_dismissed`

**Don't lose their work.** Even on Free, holding the queued send for next-month-Day-1 builds trust. (Engineering note: Step 11 PRD must include the queued-send mechanism.)

---

## Journey 7: PLAN Compiler preview opened by Free user (no CTA tap)

**Goal:** track when Free users discover the moat by opening the PLAN tab. This is a soft conversion driver (visibility).

**Steps:**
1. 🟢 Free user (any time after signup) navigates to PLAN tab via bottom nav
2. 🚪 **N3 PLAN Compiler preview overlay** fires
3. 🟢 Telemetry: `gate_hit.plan_compiler_preview`
4. 🟢 Behind the dimmed surface: a static rendered example of what a compiled PLAN looks like (canned demo content per `BuildFlags.SEED_DEMO_DATA = false` rule we DON'T show — instead we show a real example using the user's first job's data, anonymized: "Compile job 'Mrs Lee Kitchen' into a plan?")
5. 🟢 Top: locked overlay card with CTA
6. **Branch A: Tap CTA → Solo trial**
   - Standard upgrade path
7. **Branch B: Dismiss → exit overlay → return to PLAN tab top-level**
   - 🟢 Telemetry: `gate_hit.plan_compiler_preview_dismissed`
   - 🟢 Visit count incremented; if visits > 3 within a week, send a single email day-7 prompt: `You've looked at PLAN three times. Try it free for 14 days?`

**Why this matters:** repeat preview-views are the strongest conversion signal short of an actual cap-hit. Treat them as priority targets.

---

## Journey 8: Solo user cancels subscription

**Goal:** Solo user (paid 90 days) wants to cancel cleanly.

**Steps:**
1. 🟢 Solo user opens Settings → SUBSCRIPTION (new section row)
2. 🚪 N8 Subscription detail screen renders
3. 🟢 User taps `Cancel subscription` (normal-styled row, not red)
4. 🟢 Confirmation dialog (custom Composable per pattern):
   ```
   CANCEL SUBSCRIPTION?
   
   Your Solo features stay until end of current period (May 30).
   
   After that you'll be on Open. Your data stays.
   
   [ KEEP SOLO ]   [ Cancel anyway ]
   ```
5. 🟢 User taps `Cancel anyway`
6. 🟢 Subscription marked `canceled` server-side; period continues until May 30
7. 🟢 Toast: `Solo cancels May 30. You can reactivate any time.`
8. 🟢 Telemetry: `tier_downgrade.canceled, from_tier=solo, to_tier=open, days_active=90, ltv_usd=8.97`
9. 🟢 Day of period end: backend transitions tier; trial banner does NOT appear (this isn't a trial)
10. 🟢 Settings shows `Smith Net Open · $0/mo` ; jobs over cap remain visible but locked-edit; PDFs over cap remain accessible but no-resend until next month

**Re-conversion design:** at any future PLAN tap or 2nd-job attempt, normal cap overlays fire. Cancellation isn't punished — it's a temporary downgrade.

---

## Cross-journey: Tier transitions matrix

| From | To | Trigger | Data preserved? | Refund? | Telemetry event |
|---|---|---|---|---|---|
| Open | Solo (trial) | N4/N5/N3 trigger or WelcomeToOpenScreen | n/a | n/a | `tier_upgrade.trial_started` |
| Solo (trial) | Solo (paid) | CC entered before day 15 | yes | n/a | `tier_upgrade.paid_converted` |
| Solo (trial) | Open | Day 15 with no CC | yes (caps reapply) | n/a | `tier_upgrade.trial_expired` |
| Solo | Advanced (trial) | N10 trigger | yes | n/a | `tier_upgrade.trial_started` |
| Advanced (trial) | Advanced (paid) | CC by day 31 | yes | n/a | `tier_upgrade.paid_converted` |
| Advanced (trial) | Solo (paid) | Day 31 no CC, was previously paid Solo | yes (AI memory wiped on next load) | n/a | `tier_upgrade.trial_expired_to_solo` |
| Advanced | Enterprise (trial) | N11 trigger | yes | n/a | `tier_upgrade.trial_started` (CC captured) |
| Enterprise (trial) | Enterprise (paid) | Day 15 charge | yes | available 30 days | `tier_upgrade.paid_converted` |
| Enterprise | Advanced | Cancel from settings | yes (crew removed) | available 30 days post-charge | `tier_downgrade.canceled` |
| Any paid | Open | Cancel from settings | yes (caps reapply at period end) | none | `tier_downgrade.canceled` |
| Open | (account deleted) | Settings > Delete account | 30-day cooling-off | n/a | `USER_DEACTIVATED` then `DATA_PURGE` |

**Data preservation principle:** downgrading **never** deletes data. Caps re-apply (you can see your 5 jobs but only 1 is "active"; you can read your 200 invoices but only 5 PDFs/mo can send). This makes re-conversion a one-tap event.

---

## Anti-journeys (things we will not design for)

- **No "winback" cold push notifications.** If a user cancels, we don't beg. The cap overlays do the work organically when they next try the gated feature.
- **No mandatory survey on cancel.** Optional only.
- **No "are you sure" confirmation on every dismissed upgrade overlay.** That's dark-pattern territory; user dismissed = user dismissed.
- **No "limited time only" countdown timers** outside of founder-seat counters (which are real, not fake).
- **No price-anchor toast that pops on every screen** ("JobTread is $199!"). Anchoring lives on the pricing screen and within upgrade overlays only.
