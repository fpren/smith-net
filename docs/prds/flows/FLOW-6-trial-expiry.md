# FLOW-6 — Trial expiration → downgrade

**Sigma step:** 5 — Wireframe Prototype PRD
**Source flow:** `docs/flows/FLOW-DIAGRAMS.md` Flow 6
**Principle:** downgrading **never** deletes data. Caps re-apply; data stays accessible.

---

## Scope

A user on Solo / Advanced / Enterprise trial reaches `tier_expires_at` without converting (no CC). Server cron transitions tier downward; pushes FCM notification; client refreshes JWT and re-renders all screens with new entitlements. `TrialBanner` updates to expired state; `LockedFeatureOverlay` reappears on previously-unlocked surfaces.

## Affected screens

| Surface | Pre-expiry | Post-expiry |
|---|---|---|
| Global `TrialBanner` (N1) | active trial copy | expired copy: `TRIAL ENDED · YOU'RE ON OPEN · TAP TO REACTIVATE` |
| E1 PlanScreen (Solo trial → Open) | full PLAN UI | N3 lock overlay reappears |
| D3 NewJobFlow (Solo → Open) | unlimited | N4 cap re-applies on 2nd active job |
| H1 InvoiceScreen (Solo → Open) | unlimited PDF send | N5 counter footer + 5/mo cap |
| Q2 SettingsScreen AI ASSISTANT (Advanced → Solo) | full AI section | N10 lock state row |
| Q2 SettingsScreen SUBSCRIPTION row | `Smith Net Solo · $2.99/mo` | `Smith Net Open · $0/mo` (or `Smith Net Solo · $2.99/mo` if downgrade was Adv → Solo) |

## Server contract

| Job / endpoint | Behavior |
|---|---|
| Cron job: trial-expirer | Runs every 1 hour. Selects `subscriptions WHERE status='trialing' AND current_period_end <= NOW() AND payment_method IS NULL`. For each: (a) UPDATE subscriptions SET status='expired'; (b) UPDATE profiles SET tier=<downgrade_target>; (c) emit FCM push; (d) insert audit log entry; (e) emit telemetry `tier_upgrade.trial_expired`. |
| FCM push | Title: "Solo trial ended" (or Advanced/Enterprise). Body: "You're back on Open. Reactivate any time." Click → opens app to N7 pricing. |
| Server returns 401 with `tier_changed` flag | On next API call after tier change. Client detects, refreshes JWT. |
| POST /api/auth/refresh | Returns new JWT with updated `tier` claim. |
| GET /api/me/entitlements | Returns new caps. Client re-renders. |
| Founder seat handling | Founder pricing was locked at trial-start IF user was within seat cap. On expiry without conversion, the founder lock IS RELEASED (seat returned to pool). User who reactivates later may not get founder pricing if seats are then exhausted. |

## Downgrade-target matrix

| From | To (on trial expiry, no CC) | Data preserved |
|---|---|---|
| Solo trial | Open | All jobs visible (only 1 marked active per cap rule); all PDFs visible (5/mo cap reapplies); all Plans visible (compile gated); sealed Ledger entries always readable |
| Advanced trial (was paid Solo) | Solo paid | SmithAI memory NOT wiped immediately — model file kept on device, unloaded; settings transitions to Solo lock state with "Model file kept (1.2GB) — re-upgrade to enable" |
| Advanced trial (was Open before trial) | Open | Same as Solo→Open + SmithAI model file flagged for cleanup |
| Enterprise trial (CC required so different) | Refunded period charge if within 30d post-charge; downgraded to Advanced (or lower) | Crew membership preserved but read-only until upgrade; shared jobs visible but not editable by crew |

## Acceptance criteria

| AC | Description | Verification |
|---|---|---|
| AC-1 | Cron transitions tier within 1 hour of `current_period_end` | Time-travel test |
| AC-2 | FCM push delivered to user's device(s) | E2E with FCM mock |
| AC-3 | Subsequent API call returns 401 with `tier_changed`; client auto-refreshes JWT | E2E |
| AC-4 | Client UI re-renders all affected screens within 2s of new entitlements | UI test |
| AC-5 | TrialBanner transitions to expired copy | UI test |
| AC-6 | E1 PlanScreen reverts to N3 lock overlay (Solo→Open downgrade) | UI test |
| AC-7 | All user data is preserved (no jobs / invoices / plans / channels / messages deleted) | DB assertion |
| AC-8 | Founder seat released back to pool if user did not convert | DB assertion + counter UI test |
| AC-9 | Reactivation path: tap banner → N7 → trial-or-pay → state restored (with warning if founder pricing no longer available) | E2E |
| AC-10 | Audit log entry written: `USER_PROFILE_UPDATE` with role/tier change details | Audit log assertion |
| AC-11 | Email day+1 follow-up sent from cohort job: "Your Solo trial ended. X of 1000 founder spots are still open if you reactivate." | Email assertion |

## BDD scenarios

```gherkin
Feature: Solo trial expires without CC, user is downgraded to Open

Scenario: Trial expires, tier downgraded, banner updates
  Given a user on Solo trial day 14 with no payment method
  When the trial-expirer cron runs after current_period_end
  Then subscriptions.status becomes "expired"
  And profiles.tier becomes "open"
  And an FCM push is delivered: "Solo trial ended. You're back on Open. Reactivate any time."
  When the user opens the app
  Then JWT refresh returns new token with tier=open
  And the TrialBanner shows "TRIAL ENDED · YOU'RE ON OPEN · TAP TO REACTIVATE"
  When they navigate to E1 PlanScreen
  Then N3 LockedFeatureOverlay appears
  When they navigate to D3 NewJobFlow and try to create a 2nd active job
  Then N4 active-job cap overlay appears
  And all their jobs from before are still visible (just only 1 is "active")

Scenario: Reactivation after expiry
  Given a user is on Open after trial expiry
  When they tap the TrialBanner ("TAP TO REACTIVATE")
  Then they land on N7 TierPricingScreen
  And the Solo section shows the standard CTA (founder pricing only if seats remain)
  When they start a new trial OR pay immediately
  Then their tier transitions back to Solo
  And caps are removed
  And all their preserved data is now usable (unlimited active jobs etc)

Scenario: Advanced trial expires (was paid Solo) → downgrade to Solo paid
  Given a Solo paid user on Advanced trial day 30 with no payment method for Advanced
  When trial-expirer runs
  Then profiles.tier becomes "solo"
  And SmithAI is unloaded but model file is preserved on device
  And Q2 SettingsScreen AI ASSISTANT shows "Model file kept (1.2GB) — re-upgrade to enable" + Free up space link
  And the user remains paid Solo (their Solo subscription was not affected)
```

## Edge cases

| Case | Behavior |
|---|---|
| User adds CC during the last hour of trial | Cron skips (payment_method IS NOT NULL); subscription transitions to `active`; charged; remains on tier |
| User has multiple devices | All devices get FCM push; first to refresh JWT updates; others detect `tier_changed` on next API call |
| Trial expires while user is mid-action (e.g., creating a job) | API call returns 403 tier_gate_exceeded; UI shows the appropriate cap overlay (N4 for active-job cap); user's draft is preserved locally |
| User had founder pricing locked, then trial expired | Lock released. Founder counter increments. If user reactivates and seats remain, they can re-lock. If seats exhausted, they pay standard rate. |
| Network error during JWT refresh | Client retries; degrades gracefully — UI shows old tier briefly; backend's 403 on subsequent calls forces another refresh |
| User has data exceeding new caps (e.g., 10 active jobs after Solo→Open downgrade) | All jobs remain visible; only 1 enforced as "active" via cap. Others auto-marked as `archived` on next dashboard load? **NO** — they remain `active` in DB but client UI shows them as "Locked — close to start a new one"; user explicitly closes one to bring under cap |

## Non-goals

- Auto-archiving on downgrade (preserve user state; let them choose)
- "Win-back" email beyond the day+1 reminder (one email only — per OFFER_ARCHITECTURE no begging)
- Post-cancellation upsell push notifications

## Linked specs

- `OFFER_ARCHITECTURE.md` §4 (trial mechanics), §6 (refund policy)
- `WIREFRAME-SPEC.md §3` (TrialBanner), `§2` (LockedFeatureOverlay)
- `STATE-COVERAGE.md` N1 (banner expired states)
- `SCHEMA.md §11` (subscriptions table)
- `pricing-config.json` `cancellation_rules`
- Existing audit log: `backend/src/auditLog.ts`
