# FLOW-2 — Cap-hit → trial conversion

**Sigma step:** 5 — Wireframe Prototype PRD
**Source flow:** `docs/flows/FLOW-DIAGRAMS.md` Flow 2
**Conversion target:** ≥ 50% of `gate_hit.active_job_cap` events convert to `trial_started` within 60 days (per `SUCCESS-METRICS.md`)

---

## Scope

Free user attempts a 2nd active job, hits the server-enforced cap, sees `LockedFeatureOverlay` (N4 variant), taps the trial CTA, server activates 14-day Solo trial with founder pricing reservation, retries the create — succeeds — with `TrialBanner` (N1) now globally visible.

## Screens

| Step | Screen | Origin | Spec |
|---|---|---|---|
| 1 | C1 Dashboard | existing | unchanged |
| 2 | D3 NewJobFlow | existing-with-N | invokes overlay on Save 403 |
| 3 | **N4 `LockedFeatureOverlay` (active-job cap variant)** | NET-NEW | `WIREFRAME-SPEC §2` |
| 4 | (in-overlay) **N6 `FounderSeatsCounter`** | NET-NEW | `WIREFRAME-SPEC §4` |
| 5 | trial-flow → loop back to D3 retry → D2 of new job | (no new screen) | — |
| 6 | global **N1 `TrialBanner`** appears | NET-NEW | `WIREFRAME-SPEC §3` |

## Server contract

| Endpoint | Step | Behavior |
|---|---|---|
| POST /api/jobs | 2 | server checks: tier=open + active_jobs ≥ 1 → returns `403 { error: "tier_gate_exceeded", gate_id: "active_job_cap", current_tier: "open", limit: 1, current: 1 }`. Inserts `gate_hit_events` row with `event="gate_hit.active_job_cap"`, `user_id_hash=SHA256(profile.id)` (no PII). |
| GET /api/founder-seats/:bonusId | 3 | returns current count for `bonus_id="founder_pricing_lock"`. WS push `founder_seats_changed` for live updates. |
| POST /api/me/start-trial | 4 | body: `{targetTier: "solo"}`. Server: (a) verifies no prior trial, (b) reserves founder seat with 10-min hold, (c) creates `subscriptions` row `status=trialing`, `cents_per_period=299`, (d) updates `profiles.tier=solo`, `profiles.tier_expires_at=NOW()+14d`, `profiles.founder_pricing_locked_at=NOW()` (if seat reserved), (e) issues new JWT with `tier=solo`. Returns 200 + new tokens + new entitlements. |
| POST /api/jobs (retry) | 5 | now succeeds (active_jobs<unlimited for Solo). |

## Acceptance criteria

| AC | Description | Verification |
|---|---|---|
| AC-1 | Free user with 1 active job tapping Save on a 2nd triggers N4 overlay within 200ms of server 403 | Trace |
| AC-2 | N4 overlay shows title "ONE ACTIVE JOB AT A TIME" | UI test |
| AC-3 | Overlay shows founder counter `● ${X} OF 1000 FOUNDER SPOTS LEFT` (live or fallback) | UI test |
| AC-4 | Tap on primary CTA fires `tier_upgrade.cta_clicked` with `gate=active_job_cap, target_tier=solo` | Telemetry assertion |
| AC-5 | Server reserves founder seat atomically (no double-reserve under concurrent load) | Concurrency test (50 parallel reservations, ensure exactly 1000 succeed) |
| AC-6 | Seat hold = 10 minutes; auto-releases if checkout abandoned | Time-travel test |
| AC-7 | Trial start re-issues JWT with `tier=solo` claim | JWT inspection |
| AC-8 | After trial start, retrying POST /api/jobs returns 201 (not 403) | API test |
| AC-9 | TrialBanner appears globally (every screen) within 500ms of trial start | UI test on multiple screens |
| AC-10 | Telemetry: `tier_upgrade.trial_started` with `from_tier=open, to_tier=solo, trigger_event=gate_hit.active_job_cap, founder_locked=true` | Telemetry assertion |
| AC-11 | Tapping "See active job" instead routes to D2 of existing active job and fires `cta_dismissed` event | UI + telemetry |
| AC-12 | If user dismisses overlay AND server-side seat hold released within 10min → counter restores | Server-side test |

## BDD scenarios

```gherkin
Feature: Free user converts to Solo trial via active-job cap

Scenario: Free user with 1 active job tries to create a second
  Given a Free-tier user with 1 active job
  When they tap "+ NEW JOB"
  And they fill the form and tap Save
  Then the server returns 403 tier_gate_exceeded with gate_id "active_job_cap"
  And the LockedFeatureOverlay (N4 variant) appears within 200ms
  And it shows "ONE ACTIVE JOB AT A TIME"
  And it shows the FounderSeatsCounter with current count
  And telemetry emits gate_hit.active_job_cap

Scenario: User accepts trial → retries → job created → banner appears
  Given the N4 overlay is open
  When the user taps "TRY SOLO FREE — NO CC"
  Then the server reserves a founder seat
  And the user's tier becomes "solo"
  And a 14-day trial subscription is created
  And the JWT is rolled with tier=solo
  And the overlay closes
  When POST /api/jobs is retried
  Then it returns 201 with the new job
  And the user lands on the new job's pipeline
  And the TrialBanner appears globally with copy "SOLO TRIAL · 14 DAYS LEFT · TAP TO LOCK FOUNDER PRICING"
  And telemetry emits tier_upgrade.trial_started

Scenario: User dismisses overlay
  Given the N4 overlay is open
  When the user taps "See active job"
  Then the overlay closes
  And the user lands on the existing active job's pipeline
  And telemetry emits tier_upgrade.cta_dismissed
  And no trial is started
```

## Edge cases

| Case | Behavior |
|---|---|
| Network failure during trial start | overlay shows "[ NO CONNECTION — OFFLINE ]" disabled CTA; user can dismiss; retry on reconnect |
| Founder seats exhausted at moment of CTA tap | server reserves no seat; subscription created at standard price (still $2.99/mo for Solo, but no lifetime lock); UI updates: counter changes to "0 OF 1000 SPOTS — STANDARD PRICING NOW" |
| Race: user closes existing job in another tab between Save and overlay show | server allows re-Save on next attempt; if overlay is already shown, user dismisses then retries successfully |
| Trial-start API succeeds but JWT roll fails | client falls back to GET /api/auth/refresh; if that fails, shows "Sign in again" prompt |
| Repeat Free-user attempts at same gate | each attempt re-fires gate_hit telemetry; overlay shows again (cap still hit); after 3rd dismissal in 24h, suppress overlay for that day, show GateHitToast instead ("Cap reached. Upgrade in Settings > Subscription.") |

## Non-goals

- A "are you sure?" confirmation step before trial start (per OFFER_ARCHITECTURE: friction at this moment kills conversion)
- Email-based recovery if user abandons in-overlay (handled by Day-7 trial reminder email separately)
- Modifying the existing NewJobFlow UI (only adding a 403 handler)

## Linked specs

- `WIREFRAME-SPEC.md §2` (LockedFeatureOverlay), `§3` (TrialBanner), `§4` (FounderSeatsCounter)
- `STATE-COVERAGE.md` N4 (7 states), N6 (8 states), N1 (13 states)
- `OFFER_ARCHITECTURE.md` §3.2 (Solo bonuses incl. founder pricing lock)
- `SUCCESS-METRICS.md` step [7] → [8] → [9] funnel
- `pricing-config.json` `solo_founder_seats_total = 1000`
