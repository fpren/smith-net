# FLOW-7 — Cancellation

**Sigma step:** 5 — Wireframe Prototype PRD
**Source flow:** `docs/flows/FLOW-DIAGRAMS.md` Flow 7
**Principle:** cancel is one-tap. Cancellation is a downgrade, not a loss. Trust earned this way pays back in re-conversion.

---

## Scope

A paid Solo / Advanced / Enterprise user navigates to Settings → Subscription → Cancel subscription. A custom Composable confirmation dialog explains the period-end behavior. Confirm sends to Stripe / Play Billing API; subscription marked `canceled` server-side; period continues until `current_period_end`. UI updates to show "(canceling [date])". User can reactivate at any time before period end with a single tap.

## Screens

| Step | Screen | Origin | Spec |
|---|---|---|---|
| 1 | Q2 SettingsScreen | existing-with-N | navigates to N8 |
| 2 | **N8 SubscriptionDetailScreen** | NET-NEW | `WIREFRAME-SPEC §6` |
| 3 | **CancelSubscriptionDialog** (custom Composable) | NET-NEW | `WIREFRAME-SPEC §6` |
| 4 | back to N8 with updated state showing cancellation | NET-NEW | new state in N8 |

## Server contract

| Endpoint | Behavior |
|---|---|
| POST /api/me/cancel | Body: `{}`. Server: (a) calls Stripe `subscription.update({cancel_at_period_end: true})` OR Play Billing `acknowledgePurchase(...)` with cancel intent, (b) UPDATE subscriptions SET status='canceled', (c) keeps `current_period_end` unchanged, (d) keeps `profiles.tier` until period end (cron at period end downgrades), (e) emits telemetry `tier_downgrade.canceled` with `from_tier, to_tier, days_active, ltv_usd`. Returns 200 + new subscription state. |
| POST /api/me/reactivate | Body: `{}`. Server: undo cancel via provider API (Stripe `subscription.update({cancel_at_period_end: false})`), UPDATE subscriptions SET status='active'. Returns 200. |
| POST /webhooks/stripe (subscription.updated) | Reconciles subscription state on provider-side change |
| POST /webhooks/play-billing | Same for Play |

## Acceptance criteria

| AC | Description | Verification |
|---|---|---|
| AC-1 | Q2 SUBSCRIPTION row tap → N8 SubscriptionDetailScreen renders within 500ms | UI test |
| AC-2 | N8 shows current tier, next bill date, payment method, founder lock status | UI test |
| AC-3 | "Cancel subscription" row is normal-styled (NOT red) | Visual test |
| AC-4 | Tap "Cancel subscription" opens CancelSubscriptionDialog (custom Composable, NOT Material AlertDialog) | UI test |
| AC-5 | Dialog body explains: "Your Solo features stay until end of current period (May 30). After that you'll be on Open. Your data stays." | UI text assertion |
| AC-6 | Dialog has [[ KEEP SOLO ]] (primary, kept on left) and [ Cancel anyway ] (secondary, on right) | UI test |
| AC-7 | Tap KEEP SOLO closes dialog with no API call | UI test |
| AC-8 | Tap Cancel anyway calls POST /api/me/cancel | API trace |
| AC-9 | Subscription marked canceled server-side; period continues until current_period_end | DB assertion |
| AC-10 | profiles.tier unchanged until cron triggers at period end | DB assertion |
| AC-11 | N8 re-renders with: tier row "Smith Net Solo (canceling May 30)"; Next bill row "none — cancels at period end"; new row "> Reactivate subscription" | UI test |
| AC-12 | Toast shows "Solo cancels May 30. You can reactivate any time." | UI test |
| AC-13 | Tap Reactivate calls POST /api/me/reactivate; subscription status='active'; UI restores | E2E |
| AC-14 | At period end, cron transitions tier (handled by FLOW-6 trial-expirer logic, generalized to all expirations) | E2E |
| AC-15 | Telemetry: tier_downgrade.canceled with from_tier, to_tier, days_active, ltv_usd | Telemetry |

## BDD scenarios

```gherkin
Feature: Solo paid user cancels subscription

Scenario: User cancels and sees clear period-end behavior
  Given a paid Solo user 90 days into subscription
  When they open Settings
  And tap the SUBSCRIPTION row
  Then N8 SubscriptionDetailScreen renders
  And it shows "Smith Net Solo · $2.99/mo · billed monthly · Next bill May 30"
  When they tap "Cancel subscription"
  Then a CancelSubscriptionDialog appears (custom Composable, not AlertDialog)
  And it shows the period-end explanation
  And it shows two buttons: KEEP SOLO and Cancel anyway

Scenario: User confirms cancellation
  Given the cancel dialog is open
  When the user taps "Cancel anyway"
  Then POST /api/me/cancel is called
  And Stripe is updated with cancel_at_period_end=true
  And subscriptions.status becomes "canceled"
  And profiles.tier remains "solo" until current_period_end
  And the dialog closes
  And N8 re-renders showing "Smith Net Solo (canceling May 30)"
  And a toast shows "Solo cancels May 30. You can reactivate any time."
  And telemetry emits tier_downgrade.canceled

Scenario: User reactivates before period end
  Given a user who canceled and is in the grace period before period end
  When they open N8
  And tap "Reactivate subscription"
  Then POST /api/me/reactivate is called
  And subscriptions.status becomes "active"
  And the period_end remains the same (no new charge yet)
  And N8 re-renders with normal "Next bill" row showing original date
  And toast shows "Solo reactivated. Next bill May 30."

Scenario: User keeps subscription after second thoughts
  Given the cancel dialog is open
  When the user taps "KEEP SOLO"
  Then the dialog closes with no API call
  And N8 state is unchanged
  And no telemetry event fires
```

## Edge cases

| Case | Behavior |
|---|---|
| User cancels then immediately taps Reactivate | API supports both calls in sequence; second call cancels the cancel |
| Stripe / Play API failure during cancel | toast shows error; state remains active; user can retry |
| Period end falls during a multi-day server outage | cron catches up on resume; downgrade still occurs (within 1 hour of resume) |
| User has Founder Pricing Lock | flag preserved across cancel + reactivate (within same subscription); if subscription fully expires + new one created later, lock NOT re-granted (it's per-subscription) |
| Enterprise tier cancellation within 30-day refund window | full refund issued (founder issues manually per pricing-config.json `refund_mechanism: founder_issues_manually`); subscription transitions to canceled immediately, not at period end |
| User on Annual cadence cancels mid-year | cancel takes effect at end of current annual period (no proration) — same as monthly behavior; but Annual user gets ~10 months of unused service after cancel |

## Non-goals

- "Why are you canceling?" survey on cancel (optional only — no mandatory)
- Discounts / retention offers in dialog ("How about 50% off for 3 months?") — per OFFER_ARCHITECTURE: no fake retention
- Email "we miss you" follow-up after cancellation
- Hiding the Cancel option in any tier
- Multi-step confirmation (one dialog only)

## Cross-cutting

- N8 Subscription detail screen is also reachable from FLOW-3 (after Advanced upgrade) and any tier upgrade flow.
- `CancelSubscriptionDialog` Composable is reusable; `DeleteAccountDialog` follows the same pattern (also from N8).
- All the cron-based downgrade work is shared with FLOW-6 (trial expirer); this PRD inherits that infra.

## Linked specs

- `WIREFRAME-SPEC.md §6` (SubscriptionDetailScreen + dialogs)
- `STATE-COVERAGE.md` N8 (11 states incl. canceled-but-period-not-ended state)
- `OFFER_ARCHITECTURE.md` §6 (refund & cancellation policy)
- `pricing-config.json` `cancellation_rules`
- `FLOW-6-trial-expiry.md` (shares cron-based downgrade logic)
