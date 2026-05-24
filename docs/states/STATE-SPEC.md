# Smith Net — Interface State Specification

**Sigma step:** 7 — Interface States
**Source:** `docs/journeys/STATE-COVERAGE.md` (86 enumerated states) + `WIREFRAME-SPEC.md` (component definitions) + `DESIGN-TOKENS.md` (token references)
**Persona:** Staff UX Lead

This doc renders **every state** of every net-new surface as a token-referenced spec. State counts cross-checked against STATE-COVERAGE.md.

---

## How to read this doc

For each component, every state has:
- **Trigger** — what causes the state
- **Render** — token-level visual spec
- **Behavior** — what's interactive, what fires
- **Telemetry** — events emitted

Token references use `{token.path}` notation per `DESIGN-TOKENS.md`. Where a state doesn't differ from default, it inherits.

State categories tracked: `default / loading / empty / partial / approached-cap / cap-hit / locked / dismissed / converted / error / disabled / disconnected / focused / pressed`. Not every component has every state — only what applies.

---

## Component N1 — TrialBanner

### State 1.1: Hidden
- **Trigger:** `trialState == NoTrial` AND tier is paid
- **Render:** not in tree
- **Behavior:** none
- **Telemetry:** none

### State 1.2: Solo trial day 1-7
- **Trigger:** `trialState = Solo(daysLeft in 1..7)`
- **Render:**
  - Background: `{color.surface}` `#FFFFFF`
  - Bottom border: `{stroke.default}` (1dp `#D0D7DE`)
  - Padding: `{padding.bannerVertical}` 6dp / `{padding.bannerHorizontal}` 12dp
  - Text: `{type.captionBold}` color `{color.textPrimary}` `#1F2328`
  - Copy: `SOLO TRIAL · ${daysLeft} DAYS LEFT · TAP TO LOCK FOUNDER PRICING`
  - Wraps to 2 lines on narrow widths; never truncates
- **Behavior:** entire row tappable → routes to N7 pricing
- **Telemetry:** on tap → `tier_upgrade.cta_clicked, source: trial_banner, target_tier: solo`

### State 1.3: Solo trial day 8-12
- **Trigger:** `Solo(daysLeft in 8..12)`
- **Render:** same as 1.2 except copy: `SOLO TRIAL · ${daysLeft} DAYS LEFT · ${X} FOUNDER SPOTS LEFT`
- **Behavior:** as 1.2

### State 1.4: Solo trial day 13-14
- **Trigger:** `Solo(daysLeft in 13..14)`
- **Render:** copy: `SOLO TRIAL ENDS IN ${daysLeft} DAYS · TAP TO STAY SOLO`
- **Behavior:** as 1.2

### State 1.5: Advanced trial day 1-14
- **Trigger:** `Advanced(daysLeft in 1..14)`
- **Render:** copy: `ADVANCED TRIAL · ${daysLeft} DAYS · SMITHAI IS LEARNING YOUR JOBS`
- **Behavior:** as 1.2 with `target_tier: advanced`

### State 1.6: Advanced trial day 15-28
- **Trigger:** `Advanced(daysLeft in 15..28)`
- **Render:** copy: `ADVANCED TRIAL · ${daysLeft} DAYS LEFT · ${X} LIFETIME SPOTS LEFT`

### State 1.7: Advanced trial day 29-30
- **Trigger:** `Advanced(daysLeft in 29..30)`
- **Render:** copy: `ADVANCED TRIAL ENDS IN ${daysLeft} DAYS · TAP TO KEEP SMITHAI`

### State 1.8: Enterprise trial day 1-13
- **Trigger:** `Enterprise(daysLeft in 1..13)`
- **Render:** copy: `ENTERPRISE TRIAL · ${daysLeft} DAYS · INVITE YOUR CREW`

### State 1.9: Solo trial expired
- **Trigger:** trial ended without conversion to paid
- **Render:** copy: `TRIAL ENDED · YOU'RE ON OPEN · TAP TO REACTIVATE`
- **Behavior:** tap → N7 (founder seats may be unavailable)

### State 1.10: Advanced trial expired (was paid Solo)
- **Render:** copy: `ADVANCED TRIAL ENDED · YOU'RE BACK ON SOLO`

### State 1.11: Enterprise trial expired (was paid Advanced)
- **Render:** copy: `ENTERPRISE TRIAL ENDED · YOU'RE BACK ON ADVANCED`

### State 1.12: Network unavailable, counter stale
- **Trigger:** counter API hasn't refreshed in > 60s
- **Render:** as current trial state but `${X}` portion of copy is hidden / replaced with `(syncing)`
- **Behavior:** still tappable; no telemetry change

### State 1.13: Founder seats exhausted mid-trial
- **Trigger:** seats fall to 0 during user's trial
- **Render:** drops the `${X} SPOTS LEFT` segment from copy
- **Behavior:** tap still routes to N7 (CTA shows standard pricing)

---

## Component N2 / N3 / N10 / N11 — LockedFeatureOverlay (single Composable, 4+ content variants)

### State 2.1: Default (open, fully loaded)
- **Trigger:** overlay invoked from any tier-gated surface
- **Render:**
  - Top card: `{color.surface}` bg, `{padding.rowStandard}` 14dp, fillMaxWidth, no border, no shadow
  - Title: `{type.captionBold}`, color `{color.primary}` `#0969DA`
  - Body: `{type.body}`, color `{color.textPrimary}`, max 2 lines wrapped
  - Tier label: `{type.bodyBold}` (e.g., `SOLO · $2.99/MO`)
  - Founder counter: rendered if `founderBonusId != null` (see Component N6)
  - Primary CTA: filled `{color.primary}` bg, `{color.onPrimary}` text, `{type.captionBold}`, `{padding.ctaVertical}` 14dp vertical, fillMaxWidth, `{shape.button}` 6dp radius
  - Secondary CTA: text-link only, `{type.caption}` muted color, centered, 12dp top padding
  - ConsoleSeparator divider: 1dp `{color.outline}`
  - Background region: bottom 40% viewport, `Modifier.alpha(0.4f)` wrapping `backgroundContent()`

### State 2.2: Loading (founder counter fetching)
- **Trigger:** counter not yet returned; max 800ms
- **Render:** as 2.1 but counter row shows `· · · LOADING SPOTS · · ·` `{type.caption}` muted

### State 2.3: Server unavailable for counter
- **Trigger:** GET /api/founder-seats failed
- **Render:** as 2.1 but counter row hidden entirely; CTA still works
- **Behavior:** primary CTA enabled; trial start may fail too — handled by 2.7

### State 2.4: Network offline (CTA disabled)
- **Trigger:** no network connectivity at any layer
- **Render:** primary CTA replaced with `[ NO CONNECTION — OFFLINE ]`, fill `{color.surfaceVariant}`, text `{color.textMuted}`
- **Behavior:** CTA non-tappable; secondary "Maybe later" still works
- **Telemetry:** `tier_upgrade.cta_blocked_offline`

### State 2.5: Pressed (primary CTA active touch)
- **Trigger:** finger down on CTA
- **Render:** CTA fill darkens to `{color.primary}` × 0.85 opacity composite
- **Behavior:** Material ripple (default) on release

### State 2.6: Reduced motion (accessibility setting on)
- **Trigger:** `Settings.System.TRANSITION_ANIMATION_SCALE == 0`
- **Render:** appears instantly (no Crossfade); otherwise as 2.1
- **Behavior:** unchanged

### State 2.7: Trial start failed
- **Trigger:** primary CTA tapped → POST /api/me/start-trial returned non-200
- **Render:** brief Toast: `Couldn't start trial. Try again.` + overlay remains open
- **Behavior:** CTA re-enabled
- **Telemetry:** `tier_upgrade.trial_start_failed, error_code: ${code}`

### State 2.8: Founder seats exhausted (overlay open)
- **Trigger:** counter shows `0 OF ${total}`
- **Render:** counter row: `0 OF ${total} SPOTS — STANDARD PRICING NOW`, gray dot, muted text; CTA still functional at standard price
- **Behavior:** unchanged primary CTA action

### State 2.9: Dismissed (Maybe later or background tap)
- **Trigger:** user taps secondary CTA OR dimmed background
- **Render:** overlay animates out (Crossfade 200ms or instant if reduced-motion)
- **Behavior:** returns to caller screen
- **Telemetry:** `tier_upgrade.cta_dismissed, gate: ${triggerEvent}`

### State 2.10: Converted (primary CTA tapped, server OK)
- **Trigger:** trial start succeeded
- **Render:** overlay closes immediately; transitions to caller screen with new entitlements
- **Telemetry:** `tier_upgrade.trial_started, from_tier, to_tier, trigger_event, founder_locked`

### State 2.11: TalkBack focused
- **Trigger:** screen-reader navigates to overlay
- **Render:** focus highlight on top card (system default 2dp outline)
- **Behavior:** announces: "Title. Body. Primary CTA: ${primaryCta}. Maybe later button. Tap dimmed background to dismiss."

---

## Component N4 — Active-job cap soft wall (uses LockedFeatureOverlay variant)

### State 4.1: Hidden
- **Trigger:** user is paid OR has 0 active jobs
- **Render:** not in tree

### State 4.2: Cap-approached (1 active, no attempt yet)
- **Trigger:** Free user with 1 active job, no save attempt
- **Render:** **NO PROACTIVE WARNING** per Principle 2 (UX-DESIGN §2). User sees nothing until they attempt.

### State 4.3: Cap-hit (Save attempt fires overlay)
- **Trigger:** Free user, 1 active, taps Save on a 2nd → server returns 403
- **Render:** LockedFeatureOverlay (cap variant): title `ONE ACTIVE JOB AT A TIME`, body, founder counter, CTAs primary `[ TRY SOLO FREE — NO CC ]` / secondary `[ See active job ]`
- **Behavior:** secondary CTA routes to D2 of existing active job (not just dismiss)
- **Telemetry:** `gate_hit.active_job_cap`

### State 4.4: Already on trial (race)
- **Trigger:** user started trial in another tab between open and Save
- **Render:** server returns 201 (Solo has no cap); overlay never fires
- **Behavior:** normal job creation

### State 4.5: Network unavailable when attempting
- **Trigger:** offline POST /api/jobs
- **Render:** overlay shows immediately (UX); job queues offline
- **Behavior:** on reconnect, server cap re-evaluates; if rejected, Toast notifies "Couldn't create — cap reached"
- **Telemetry:** `gate_hit.active_job_cap (offline-projected)` then on reconnect `gate_hit.active_job_cap (server-confirmed)` or `gate_hit_resolved`

### State 4.6: Race — closed existing job in another window
- **Trigger:** rare; existing job closed mid-attempt
- **Render:** server allows; overlay never fires; goes to D2 of new job
- **Behavior:** normal

### State 4.7: User dismissed, cap still active (try-again attempt)
- **Trigger:** user dismissed overlay then re-tapped Save within session
- **Render:** overlay re-appears; on 3rd dismissal in 24h, switch to GateHitToast (N12) instead — "Cap reached. Upgrade in Settings > Subscription."

---

## Component N5 — PDF send cap counter + cap-hit overlay

### State 5.1: Hidden (paid tier)
- **Trigger:** tier ≥ Solo
- **Render:** counter footer not in tree; no overlay possible

### State 5.2: 0 sends used (start of month)
- **Render:** `0 of 5 free sends used this month` `{type.caption}` `{color.textMuted}`

### State 5.3: 1-3 sends used (progress)
- **Render:** `${count} of 5 free sends used this month`

### State 5.4: 4 sends used (one left)
- **Render:** `4 of 5 free sends used this month — 1 left` — `{type.caption}` color steps from `{color.textMuted}` → `{color.textPrimary}` (slight emphasis without warning color)

### State 5.5: 5 sends used (cap reached, post-send)
- **Trigger:** the 5th send completed
- **Render:** updates AFTER successful send to `5 of 5 free sends used this month — full`

### State 5.6: 6th send attempt (cap hit overlay)
- **Trigger:** user taps Send on 6th attempt → server returns 403
- **Render:** LockedFeatureOverlay (PDF cap variant): title `5 SENDS PER MONTH`, body, founder counter, CTAs primary `[ TRY SOLO FREE — NO CC ]` / secondary `[ Next month: in ${X} days ]`
- **Telemetry:** `gate_hit.pdf_send_cap`

### State 5.7: 6th attempt + queued send (user dismisses upgrade)
- **Trigger:** user taps secondary CTA `[ Next month: in X days ]`
- **Render:** Toast: `Send saved as draft. Will send Day 1 of next month if still on Open.`
- **Behavior:** server schedules a queued send; original draft preserved

### State 5.8: Network unavailable for cap check
- **Trigger:** client thinks within cap, server says no
- **Render:** Send appears to succeed in client; server returns 403 with cap details; client reconciles + shows overlay after-the-fact
- **Behavior:** Toast: `Send rejected — cap reached. See details.` then opens overlay

### State 5.9: Month boundary mid-attempt
- **Trigger:** server-clock vs client-clock disagreement near month boundary
- **Render:** server uses server-time (authoritative); if a 6th attempt at 23:59:59 reaches server at 00:00:00, server treats as new-month → 1 send used
- **Behavior:** transparent to user; no state visible

---

## Component N6 — FounderSeatsCounter

### State 6.1: Loading (initial paint)
- **Render:** dot color `{color.statusGrey}`, label `· · · LOADING SPOTS · · ·` `{type.caption}` muted

### State 6.2: Available, > 100 left
- **Render:** dot `{color.statusGreen}`, label `${X} OF ${total} ${BONUS_NAME} LEFT`, label color `{color.textPrimary}`

### State 6.3: Available, 11-100 left
- **Render:** dot still `{color.statusGreen}`, label color shifts to `{color.textMuted}` (subtle urgency)

### State 6.4: Available, 1-10 left
- **Render:** dot still `{color.statusGreen}`, label color shifts to `{color.primary}` `#0969DA` (urgency without red)

### State 6.5: Exhausted (0 left)
- **Render:** dot `{color.statusGrey}`, label `0 OF ${total} SPOTS — STANDARD PRICING NOW`, label `{color.textMuted}`
- **Behavior:** parent overlay's CTA still functional at standard price

### State 6.6: Stale (> 60s since last refresh)
- **Render:** dot color stays as previous state but at 50% alpha; label unchanged
- **Behavior:** counter still rendered; refresh triggered in background

### State 6.7: Server unavailable
- **Render:** entire counter hidden
- **Behavior:** parent overlay still works

### State 6.8: Reservation held (user tapped CTA, server holds 10min)
- **Render:** local count decrements optimistically (-1); reconciles on checkout completion or 10-min hold expiry
- **Behavior:** if hold released, count restores

---

## Component N7 — TierPricingScreen

### State 7.1: Default (loaded, current tier highlighted)
- **Render:**
  - Header: `← UPGRADE` standard
  - 4 sections in fixed order: Open / Solo / Advanced / Enterprise
  - Current tier section has `{color.surfaceVariant}` background tint
  - CTAs per `WIREFRAME-SPEC §5`
  - Annual toggle below sections
  - Anchor table at bottom

### State 7.2: Tier with active trial
- **Render:** trial-tier section shows `[ TRIAL — ${daysLeft} DAYS LEFT ]` instead of CTA, primary blue text

### State 7.3: Tier above current (upgrade options)
- **Render:** primary CTA `[ TRY ${TIER} FREE ${X} DAYS — NO CC ]` and secondary `[ Start immediately ($X/mo) ]`

### State 7.4: Tier below current (downgrade options)
- **Render:** secondary action `[ DOWNGRADE TO ${TIER} ]`; tap fires confirmation dialog (custom Composable, similar to CancelSubscriptionDialog)

### State 7.5: Founder pricing visible (seats remain)
- **Render:** per-tier counter pill below price line (Component N6 default state)

### State 7.6: Founder pricing exhausted
- **Render:** "founder lifetime price" line removed; standard price shows; counter shows exhausted state (6.5)

### State 7.7: Annual toggle off (default)
- **Render:** `[ ●  Monthly  ○  Annual — save 16.7% ]`; sections show monthly prices

### State 7.8: Annual toggle on
- **Render:** `[ ○  Monthly  ●  Annual — save 16.7% ]`; sections re-render with annual prices + "save $X / yr" lines

### State 7.9: Loading (initial fetch of entitlements)
- **Render:** sections render skeletons (3 lines per section, monospace dashes `─ ─ ─ ─`); max 800ms

### State 7.10: Network unavailable
- **Render:** screen renders from local cache (entitlements last-known); CTA shows `[ NO CONNECTION — TRY AGAIN ]` (disabled fill `{color.surfaceVariant}`)

### State 7.11: CTA pressed (during trial start API call)
- **Render:** primary CTA shows spinner inline + text `STARTING TRIAL...`; disable while in flight (max 5s)

### State 7.12: TalkBack focused on a tier section
- **Behavior:** announces: "${tier name}. ${price}. ${hero feature}. Includes ${first 3 items}. Tap CTA to ${primary action}."

---

## Component N8 — SubscriptionDetailScreen

### State 8.1: Open tier (minimal layout)
- **Render:** sections: CURRENT TIER (`Smith Net Open · $0/mo`), CHANGE TIER (`> Upgrade`), DATA (`> Export my data`, `> Delete account`)

### State 8.2: Solo paid (full layout)
- **Render:** sections per WIREFRAME-SPEC §6 — CURRENT TIER, NEXT BILL, FOUNDER PRICING, CHANGE TIER, PAYMENT METHOD, DATA

### State 8.3: Advanced paid
- **Render:** as 8.2 + AI ROW (`SMITHAI · Loaded · 1.2GB on device`) + downgrade-to-Solo option in CHANGE TIER

### State 8.4: Enterprise paid
- **Render:** as 8.3 + CREW row (count of crew members) + `> Manage crew` chevron

### State 8.5: Trial active
- **Render:** tier row shows trial badge `(TRIAL — ${days} LEFT)`; Next bill row shows `${trialEndDate} (charge if you keep)`

### State 8.6: Cancelled, period not ended
- **Render:** tier row: `Smith Net Solo (canceling May 30)`; Next bill row: `none — cancels at period end`; new row: `> Reactivate subscription`

### State 8.7: Founder pricing locked
- **Render:** new section row: `● Locked at $${price}/${cadence} for life`, green dot

### State 8.8: Payment method missing (Solo trial in flight, no CC)
- **Render:** PAYMENT METHOD row: `> Add payment method (locks Solo at trial end)`, primary blue text

### State 8.9: Payment method declined
- **Render:** payment method row uses `{color.error}` text only (still `{color.surface}` bg, NOT a red button): `Payment failed — update card to keep Solo`; tap → update flow

### State 8.10: Loading (initial fetch)
- **Render:** sections render skeletons; max 800ms

### State 8.11: Network unavailable
- **Render:** shows last-known state from local cache; muted bar at top: `Offline — last updated ${time}`

---

## Component N9 — Branded PDF stamp (server-side render-time)

### State 9.1: Stamp present
- **Trigger:** tier == Open at PDF render time
- **Render:** footer block injected via `templates/invoice.html` `{{#if isOpenTier}}` conditional
  - Style: monospace, `{color.textMuted}` `#656D76`, 10px font (HTML/CSS units), 24px top-padding, 1px `{color.outline}` top-border, text-align center
  - Content: `Sent via Smith Net — smithnet.app` line break `A deterministic tool for contractors. Try free →`
- **Email signature:** appended to body `\n\n--\nSent via Smith Net (smithnet.app)`

### State 9.2: Stamp absent
- **Trigger:** tier ≥ Solo at PDF render time
- **Render:** footer block omitted; email signature not modified

### State 9.3: Tier downgrade between draft and send
- **Trigger:** user downgrades after generating draft, before sending
- **Render:** server uses tier at SEND TIME (deterministic per FLOW-1 AC); whichever tier is current when `/api/invoices/:id/send` is called

### State 9.4: Tier upgrade after send
- **Trigger:** sent PDF already exists at customer's inbox
- **Render:** **never re-stamps retroactively** — already-sent PDFs frozen at send-time tier

---

## Component N10 — EntitlementLock (Settings AI Assistant lock state)

### State 10.1: Locked (Free or Solo tier)
- **Render:**
  - Section title `AI ASSISTANT` `{type.captionBold}`
  - Spacer 10dp
  - Surface row: `● Locked — Advanced tier` (gray dot + `{type.bodyBold}`) sub-line `Tap to learn what SmithAI does` `{type.caption}` muted, chevron `>` muted
  - Spacer 8dp
  - Text-link `[ Try Advanced free 30 days — no CC ]` `{type.caption}` `{color.primary}`
  - Spacer 16dp + ConsoleSeparator + Spacer 12dp

### State 10.2: Unlocked (Advanced+)
- **Render:** existing AI section unchanged (Load button, model state, battery gate indicator)

### State 10.3: Trial active, model still downloading
- **Render:** existing pattern — section title + `LinearProgressIndicator` for model load

### State 10.4: Trial active, model loaded, AI ALIVE
- **Render:** existing UI

### State 10.5: Trial expired, downgraded to Solo
- **Trigger:** Solo paid + Advanced trial expired
- **Render:** section returns to lock state (10.1) but adds caption: `Model file kept (1.2GB) — re-upgrade to enable` + small text-link `[ Free up space ]`

---

## Component N11 — Crew invite locked overlay (LockedFeatureOverlay variant)

### State 11.1: Lock fires (Solo or Advanced tap)
- **Trigger:** tier < Enterprise taps `+ INVITE COLLEAGUE`
- **Render:** LockedFeatureOverlay (Crew variant): title `CREW MODE`, body, founder counter for Enterprise founder annual bonus, CTA `[ START 14-DAY ENTERPRISE TRIAL ]`
- **Note:** this is the ONLY trial that captures CC up-front (per pricing-config.json)
- **Telemetry:** `gate_hit.crew_invite`

### State 11.2: Enterprise tier (existing flow)
- **Render:** existing crew invite flow runs unchanged

### State 11.3: Trial active, mid crew onboarding
- **Render:** existing flow + N1 banner reminds days left

### State 11.4: Crew invite limit edge case
- **Note:** post-launch only — Enterprise has unlimited crew per pricing-config.json. Not applicable v1.

---

## Component N12 — GateHitToast

### State 12.1: Dismiss confirm
- **Trigger:** user dismisses an overlay
- **Render:** standard Android Toast, `LENGTH_SHORT` (3.5s), bottom-anchored, app monospace font, `{color.textPrimary}` on dark Toast bg (Android default)
- **Content:** `Maybe later. Free tier active.`

### State 12.2: Cap reminder mid-session
- **Trigger:** repeat attempt at same gate within session
- **Content:** `Cap reached. Upgrade in Settings > Subscription.`

---

## Component A4 — WelcomeToOpenScreen

### State A4.1: Default
- **Render:** full-screen scrollable, no back arrow (one-way)
  - Title `WELCOME TO SMITH NET OPEN` `{type.title}`
  - Body: "You're on the Free tier." `{type.body}`
  - WHAT YOU HAVE section: 5 bullet items with 8dp green dots, body text
  - ConsoleSeparator
  - WANT TO TRY SOLO section: bullets + founder counter
  - Two CTAs: primary `[[ START SOLO TRIAL — NO CC ]]` + secondary `[ Stay on Open ]`
  - Page padding `{padding.pageHorizontal}`/`{padding.pageVertical}`

### State A4.2: Founder seats exhausted (rare, post-launch)
- **Render:** counter shows exhausted state; primary CTA still active at standard pricing

### State A4.3: Loading (counter fetching)
- **Render:** counter row shows `· · · LOADING SPOTS · · ·`; CTAs visible from start

### State A4.4: Trial start in flight (CTA pressed)
- **Render:** primary CTA shows spinner inline + `STARTING TRIAL...`; disable max 5s

### State A4.5: Trial start failed
- **Render:** Toast: `Couldn't start trial. Try again.`; CTA re-enabled

---

## Component CancelSubscriptionDialog

### State CSD.1: Default (open)
- **Render:** custom Composable centered overlay, max-width 320dp, `{color.surface}` bg, `{padding.rowStandard}` padding, `{shape.button}` 6dp radius, 1dp `{color.outline}` border
  - Title: `CANCEL SUBSCRIPTION?` `{type.captionBold}`
  - Body: 2-3 lines `{type.body}`
  - Buttons row: `[[ KEEP SOLO ]]` (primary fill `{color.primary}` `{color.onPrimary}`) on left, `[ Cancel anyway ]` (secondary, transparent fill, 1dp border, `{color.textPrimary}` text) on right
- **Behavior:** outside-click does NOT dismiss (intentional — confirmation forces explicit choice); back-button cancels (= KEEP SOLO)

### State CSD.2: Cancel-anyway in flight
- **Render:** secondary button shows spinner + `CANCELING...`

### State CSD.3: Network failure
- **Render:** Toast: `Couldn't cancel. Try again.`; dialog remains open
- **Behavior:** retry available

---

## Component DeleteAccountDialog

### State DAD.1: Default
- **Render:** as CSD.1 with:
  - Title `DELETE ACCOUNT?` 
  - Body explains 30-day cooling-off period + irreversibility after that
  - Buttons: `[[ KEEP ACCOUNT ]]` (primary) + `[ Delete anyway ]` (secondary, text color `{color.error}` `#CF222E` — the **only** place red is used for inline interactive text)

### State DAD.2: Delete in flight
- **Render:** secondary shows `DELETING...`

---

## Existing screens — net-new states layered onto existing UI

### C1 DashboardScreen (existing) — net-new state inserts

#### State C1.N1: Trial banner above content
- **When:** trial active
- **Render:** TrialBanner (any of N1.2-N1.13) at top, above existing modules
- **Layout impact:** dashboard scrollable content shifts down by banner height (~36dp)

#### State C1.N2: Tier-aware UPGRADE quick-action tile
- **Open tier:** tile labeled `UPGRADE`, taps to N7
- **Solo tier:** tile labeled `ADD SMITHAI`, taps to N7 scrolled to Advanced
- **Advanced tier:** tile labeled `ADD CREW`, taps to N7 scrolled to Enterprise
- **Enterprise tier:** tile NOT in grid (4-tile grid still 4 tiles, just different last tile from existing set)

### Q2 SettingsScreen (existing) — net-new state inserts

#### State Q2.N1: SUBSCRIPTION section above PROFILE
- **Render:** new section header `SUBSCRIPTION` `{type.captionBold}` + row showing current tier + `>`
- **Behavior:** tap → N8 SubscriptionDetailScreen

#### State Q2.N10: AI Assistant lock state for tier < Advanced
- **Render:** EntitlementLock (Component N10 state 10.1) replaces existing AI section content

### D3 NewJobFlow (existing) — net-new state inserts

#### State D3.N4: Save-fired overlay
- **Trigger:** Save returns 403 tier_gate_exceeded gate_id=active_job_cap
- **Render:** N4 LockedFeatureOverlay (state 4.3) appears over the form
- **Form state:** preserved in background (dimmed); on dismiss, user returns to filled form

### H1 InvoiceScreen + G4 InvoicePreviewBottomSheet (existing) — net-new state inserts

#### State H1.N5a: PDF counter footer (Open tier)
- **Render:** PdfSendCounterFooter at bottom of send dialog (states 5.2-5.5)

#### State H1.N5b: 6th-send overlay
- **Trigger:** Send returns 403
- **Render:** N5 LockedFeatureOverlay (state 5.6)

### E1 PlanScreen (existing) — net-new state inserts

#### State E1.N3: Free user opens PLAN tab
- **Trigger:** tier == Open
- **Render:** N3 LockedFeatureOverlay (states 2.1-2.11) at top; dimmed background = sample compiled plan
- **Behavior:** user cannot interact with PLAN UI under overlay; CTA or dismiss are only actions

#### State E1.N2: Solo+ user attempts AI assist on PLAN
- **Note:** AI assist (ProposalAssist) is Advanced-only. If Solo user taps it, fires N10 overlay variant.

---

## Cross-cutting state — Network connectivity (every screen)

### State N.1: Online (Hetzner reachable)
- **Render:** existing connection pill on C1, K1: `● ONLINE`, green dot
- **Behavior:** all server-authoritative actions work

### State N.2: Mesh-only (offline from Hetzner, BLE/WiFi-Direct active)
- **Render:** existing pill: `● MESH ${peerCount}`, green dot (still "connected", just locally)
- **Behavior:** local actions queued; cap-checks deferred to reconnect

### State N.3: Gateway (relayed via another device)
- **Render:** pill: `● GATEWAY`, green dot
- **Behavior:** as N.1 but via relay

### State N.4: Fully offline (no Hetzner, no peers)
- **Render:** pill: `○ OFFLINE`, gray dot
- **Behavior:** local actions queued; sync on reconnect; tier-gate checks use last-known entitlements (cached)

### State N.5: Reconnecting (transition)
- **Render:** pill briefly shows `· · · RECONNECTING` for max 3s
- **Behavior:** ReconciliationEngine fires (per FLOW-5)

---

## State count audit

| Component | States enumerated here | States in STATE-COVERAGE.md |
|---|---|---|
| N1 TrialBanner | 13 | 13 ✓ |
| N2/N3/N10/N11 LockedFeatureOverlay | 11 | 11 ✓ |
| N4 Active-job cap | 7 | 7 ✓ |
| N5 PDF cap | 9 | 9 ✓ |
| N6 FounderSeatsCounter | 8 | 8 ✓ |
| N7 TierPricingScreen | 12 | 12 ✓ |
| N8 SubscriptionDetailScreen | 11 | 11 ✓ |
| N9 PDF stamp | 4 | 4 ✓ |
| N10 EntitlementLock | 5 | 5 ✓ |
| N11 Crew invite | 4 | 4 ✓ |
| N12 GateHitToast | 2 | 2 ✓ |
| A4 WelcomeToOpenScreen | 5 | (added — was entry-only in STATE-COVERAGE) |
| CancelSubscriptionDialog | 3 | (added) |
| DeleteAccountDialog | 2 | (added) |
| Cross-cutting connectivity | 5 | (added) |
| Existing-screen state inserts | 7 | (added) |
| **Total** | **108** | **86 + 22 = 108** |

**Step 7 increases the catalogued state count from 86 → 108** by adding entry-state coverage for A4 + the 2 dialogs + connectivity matrix + 7 net-new states layered onto existing screens.

---

## Linked specs

- `STATE-COVERAGE.md` — original 86 states (this doc supersedes with 108)
- `WIREFRAME-SPEC.md` — composable signatures + layouts
- `DESIGN-TOKENS.md` — every `{token}` reference
- `MICRO-INTERACTIONS.md` — interaction details + the very-limited motion language
