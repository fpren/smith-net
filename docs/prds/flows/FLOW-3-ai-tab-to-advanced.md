# FLOW-3 — AI tab → Advanced trial

**Sigma step:** 5 — Wireframe Prototype PRD
**Source flow:** `docs/flows/FLOW-DIAGRAMS.md` Flow 3
**Conversion target:** ≥ 25% of Solo users who open the AI Assistant section start an Advanced trial within 30 days (per `SUCCESS-METRICS.md` per-step [10]→[11])

---

## Scope

Solo (paid) user opens Settings → AI Assistant section → sees `EntitlementLock` row → taps → `LockedFeatureOverlay` (N10 variant) opens with `FounderSeatsCounter` for the lifetime template library bonus → user taps `TRY ADVANCED FREE 30 DAYS — NO CC` → server activates 30-day Advanced trial → existing AI Settings UI replaces the lock state → user taps Load model → existing `LlamaInference` lifecycle runs (SLEEPING → WAKING → ALIVE).

## Screens

| Step | Screen | Origin | Spec |
|---|---|---|---|
| 1 | Q2 SettingsScreen (Solo user, scrolled to AI ASSISTANT section) | existing-with-N | replaces existing AI section with `EntitlementLock` for tier<Adv |
| 2 | **N10 `EntitlementLock` row** | NET-NEW | `WIREFRAME-SPEC §8` |
| 3 | **N10 `LockedFeatureOverlay` (SmithAI variant)** | NET-NEW | `WIREFRAME-SPEC §2` |
| 4 | (in-overlay) **N6 `FounderSeatsCounter` for lifetime_template_library bonus** | NET-NEW | `WIREFRAME-SPEC §4` |
| 5 | trial-flow → Q2 re-renders with full AI section | existing | unchanged |
| 6 | Existing AI lifecycle (Load model → progress bar → ALIVE) | existing | unchanged |
| 7 | global **N1 `TrialBanner`** transitions to Advanced trial copy | NET-NEW | `WIREFRAME-SPEC §3` |

## Server contract

| Endpoint | Step | Behavior |
|---|---|---|
| GET /api/me/entitlements | 1 | returns `caps.smithAI=false` for Solo tier; client uses to decide which Composable to render |
| POST /api/telemetry/gate-hit | 2 | client posts `event=gate_hit.ai_tab` with `current_tier=solo`; server inserts into `gate_hit_events` |
| GET /api/founder-seats/lifetime_template_library | 3 | returns count for Advanced bonus (cap 100) |
| POST /api/me/start-trial | 4 | body: `{targetTier: "advanced"}`. Server: (a) verifies user is paid Solo (no trial-of-trial), (b) reserves lifetime template seat with 10-min hold (if available), (c) creates `subscriptions` row `tier=advanced status=trialing cents_per_period=999`, (d) updates `profiles.tier=advanced`, `tier_expires_at=NOW()+30d`, (e) issues new JWT with `tier=advanced`. Returns 200 + tokens + new entitlements. |
| (Existing) ModelDownloader background | 6 | downloads ~2GB Llama model to local storage on first activation |
| (Existing) AgentInitializer.wakeAgent | 6 | SLEEPING → WAKING → ALIVE state transitions |

## Acceptance criteria

| AC | Description | Verification |
|---|---|---|
| AC-1 | Solo user opening Q2 SettingsScreen sees `EntitlementLock` row with `● Locked — Advanced tier` | UI test |
| AC-2 | Existing AI Section (Load button, model state) is hidden for tier < Advanced | UI test |
| AC-3 | Tapping `EntitlementLock` fires `gate_hit.ai_tab` telemetry | Telemetry assertion |
| AC-4 | N10 overlay renders with title "SMITHAI" + body explaining on-device | UI test |
| AC-5 | Founder counter renders for `lifetime_template_library` bonus | UI test |
| AC-6 | Tap "TRY ADVANCED FREE 30 DAYS — NO CC" calls /api/me/start-trial | API trace |
| AC-7 | Server reserves lifetime template seat atomically (no double-reserve) | Concurrency test |
| AC-8 | Server creates trial subscription with cents_per_period=999, status=trialing, current_period_end=NOW()+30d | DB assertion |
| AC-9 | New JWT carries tier=advanced | JWT inspection |
| AC-10 | After trial start, Q2 AI ASSISTANT section transforms: lock removed → existing UI shown (Load button, etc.) | UI test |
| AC-11 | TrialBanner globally updates to "ADVANCED TRIAL · 30 DAYS · SMITHAI IS LEARNING YOUR JOBS" | UI test |
| AC-12 | Telemetry: `tier_upgrade.trial_started` with `from_tier=solo, to_tier=advanced, trigger_event=gate_hit.ai_tab, founder_locked=<bool>` | Telemetry assertion |
| AC-13 | If user dismisses overlay, returns to Q2 with section still in lock state, no trial started | UI + DB |
| AC-14 | Model load on unsupported low-spec device → fallback to RULE_BASED_FALLBACK + UI shows "MODEL UNSUPPORTED" + downgrade CTA | Existing AI lifecycle test |

## BDD scenarios

```gherkin
Feature: Solo user converts to Advanced via AI tab

Scenario: Solo user opens AI Assistant settings → starts Advanced trial
  Given a paid Solo user
  When they open Settings
  And they scroll to AI ASSISTANT section
  Then they see an EntitlementLock row "● Locked — Advanced tier"
  And the existing AI Section is hidden
  When they tap the lock row
  Then telemetry emits gate_hit.ai_tab
  And the LockedFeatureOverlay (N10 variant) appears
  And it shows "SMITHAI" title
  And it shows founder counter for lifetime_template_library bonus
  When they tap "TRY ADVANCED FREE 30 DAYS — NO CC"
  Then the server reserves a lifetime template seat
  And creates an Advanced trial subscription (30 days)
  And the user's tier becomes "advanced"
  And the JWT is rolled with tier=advanced
  And Q2 SettingsScreen re-renders showing the existing AI section (Load button visible)
  And the TrialBanner globally updates to "ADVANCED TRIAL · 30 DAYS · SMITHAI IS LEARNING YOUR JOBS"
  And telemetry emits tier_upgrade.trial_started

Scenario: User loads model after trial start
  Given the user just started Advanced trial
  When they tap Load model
  Then ModelDownloader begins fetching the GGUF model (~2GB)
  And AgentInitializer transitions SLEEPING → WAKING (showing 0%-100%) → ALIVE
  Then the model is loaded and SmithAI is available

Scenario: Low-spec device rejects model
  Given a Solo user on Advanced trial
  When they tap Load model
  And ModelDownloader completes but model load fails (low RAM)
  Then RULE_BASED_FALLBACK activates automatically
  And Q2 AI ASSISTANT shows "MODEL UNSUPPORTED ON THIS DEVICE — DOWNGRADE TO SOLO?"
  And a no-fee downgrade CTA is offered
  And if past trial period, user is refunded pro-rata
```

## Edge cases

| Case | Behavior |
|---|---|
| Free user (not paid Solo) reaches AI tab somehow | EntitlementLock still renders; tapping shows N10 overlay with TRIAL = Advanced; trial start works (skips Solo paywall — they go straight to Advanced trial) |
| Lifetime template seats exhausted | server allows trial start at standard price (no seat reserved); UI shows counter "0 OF 100 SPOTS — STANDARD PRICING NOW" |
| User starts Advanced trial then immediately downgrades to Solo | trial state cleared; SmithAI memory wiped on next load; founder seat released back to pool |
| User dismisses overlay then taps Load model directly (race / glitch) | client-side check `entitlements.smithAI` blocks; refuses with toast |
| Model download succeeds but device storage low after | existing AI lifecycle handles; suggests "Free up space" |

## Non-goals

- Replacing existing AI Settings UI for Advanced+ users
- Restyling the model download progress UI
- iOS path (out of scope v1)

## Linked specs

- `WIREFRAME-SPEC.md §8` (EntitlementLock), `§2` (LockedFeatureOverlay), `§3` (TrialBanner), `§4` (FounderSeatsCounter)
- `STATE-COVERAGE.md` N10 (5 states), N1 (13 states), N6 (8 states)
- `OFFER_ARCHITECTURE.md` §3.3 (Advanced bonuses incl. lifetime template library)
- `SUCCESS-METRICS.md` step [10] → [11]
- Existing AI lifecycle in `ai/AgentInitializer.kt`, `ai/LlamaInference.kt`, `ai/AIRouter.kt`
- `pricing-config.json` `advanced_lifetime_template_seats_total = 100`
