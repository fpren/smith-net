---
name: smith-net-tier-gating
description: Tier system conventions for Smith Net — 4-tier ladder (Open/Solo/Advanced/Enterprise), one hero feature per tier, server-authoritative caps, founder pricing with atomic seat reservation, structured 403 contract. Use when adding tier-gated features, working with subscriptions/billing/caps, modifying entitlements, or building upgrade UX.
---

# Smith Net — Tier gating conventions

## The 4-tier ladder (locked — see `pricing-config.json` for full schema)

| Tier | Price | Hero feature |
|---|---|---|
| **Open (Free)** | $0 | Deterministic baseline taste — basic invoicing + 1 active job + branded PDFs |
| **Solo** | $2.99/mo | **PLAN Compiler + cord state model** (the moat) |
| **Advanced** | $9.99/mo | **SmithAI on-device + Advanced invoice template** |
| **Enterprise** | $50/mo (flat, NOT per seat) | **Crew / multi-user + Enterprise invoice template** |

**One hero feature per tier.** Don't bundle. Each upgrade is a single emotional unlock.

## Cap matrix (Open → Enterprise)

| Capability | Open | Solo | Adv | Ent |
|---|---|---|---|---|
| Active jobs | 1 | unlim | unlim | unlim |
| PDF sends/mo | 5 | unlim | unlim | unlim |
| PLAN Compiler | ❌ | ✅ | ✅ | ✅ |
| Cord state model | ❌ | ✅ | ✅ | ✅ |
| SmithAI on-device | ❌ | ❌ | ✅ | ✅ |
| Standard invoice template | ✅ | ✅ | ✅ | ✅ |
| Advanced invoice template | ❌ | ❌ | ✅ | ✅ |
| Enterprise invoice template | ❌ | ❌ | ❌ | ✅ |
| Crew / multi-user | ❌ | ❌ | ❌ | ✅ |
| Smith Net branding on PDFs | ✅ forced | ❌ | ❌ | ❌ |

## Server-authoritative — never trust the client

Every tier-gated capability MUST refuse server-side. Use `requireTier(min)` middleware (per F2.2) for tier minimums and `requireCap({...})` (per F6.1) for in-tier caps.

When over-cap, refuse with structured 403:

```typescript
return res.status(403).json({
  error: 'Tier cap reached: <gate_id>',
  code: 'tier_gate_exceeded',
  gate_id: 'active_job_cap',  // or pdf_send_cap, plan_compiler, ai_tab, crew_invite, advanced_template, enterprise_template
  current_tier: 'open',
  limit: 1,
  current: 1,
  details: { target_tier: 'solo' },
});
```

Client uses `gate_id` to invoke the correct `LockedFeatureOverlay` variant.

## Tier resolution

- Single source of truth: `tierResolver.ts` (per F2.2). Every check goes through this.
- JWT carries `tier` and `entitlementsHash` claims; client caches via `EntitlementsRepository`.
- On tier change server-side, response includes `X-Tier-Changed: true` header — client refreshes JWT.

## Telemetry on every gate hit

`gate_hit_events` table (per F5.2). Server-side emit fires automatically inside `requireCap` middleware — clients can also emit via `POST /api/telemetry/gate-hit`. **No PII** — uses `user_id_hash = SHA256(profile.id)`.

## Founder pricing — atomic + real

`founder_seats` table (per F5.1) with three pools:

| Tier | Bonus | Cap |
|---|---|---|
| Solo | Founder Pricing Lock ($2.99/mo for life) | 1000 |
| Advanced | Lifetime Template Library | 100 |
| Enterprise | Founder Annual Pricing ($500/yr vs $600) | 10 |

- `founderSeatService.reserve()` uses `FOR UPDATE SKIP LOCKED` for concurrency safety
- 10-min hold on tap; auto-released by `releaseExpiredHolds` cron
- WS push `founder_seats_changed` for live UI updates
- **Never fake scarcity.** When seats are exhausted, show "0 OF X SPOTS — STANDARD PRICING NOW" (gray dot).

## Trial mechanics

| Tier | Trial | CC required | Auto-action at end |
|---|---|---|---|
| Open | n/a | n/a | n/a |
| Solo | 14 days | **No** | Auto-downgrade to Open |
| Advanced | 30 days | **No** | Auto-downgrade to Solo (or Open) |
| Enterprise | 14 days | **Yes** (refund window 30d post-charge) | Begin charging |

Same-tier trial cannot be reused (`trial_already_used` 400). Trial expiry handled by hourly `trialExpirer` cron (per F7.1) — preserves all data, downgrades caps reapply.

## Tier gates SHOW + LOCK + CTA. Role gates HIDE.

| Gate type | UX treatment |
|---|---|
| **Role gate** (e.g., Solo without `Permission.GATEWAY_RELAY`) | Section/feature is hidden entirely (existing pattern) |
| **Tier gate** (e.g., Free without PLAN Compiler) | Section/feature is visible, dimmed, with upgrade CTA (new pattern) |

Why: role gates protect features that **don't apply** to that role; tier gates protect features the user **wants** but hasn't paid for. Hiding tier-gated features defeats the conversion mechanic.

## Friction at moment of value, NOT before

Don't pre-warn users as they approach a cap. The cap-hit fires at the attempt — that's the upgrade moment. No "you have 1 PDF send left" warnings; no proactive nag.

## Cancel = one tap, no friction

- Cancel subscription = a normal-styled row (NOT red)
- Confirmation dialog explains period-end behavior (custom Composable, NOT Material AlertDialog)
- Outside-tap doesn't dismiss confirmation (explicit choice required)
- Data preserved on cancel; re-conversion is one tap

## Branded PDF stamp tier-locked at SEND time

- Tier resolved at SEND time (not draft, not view)
- `invoices.sent_with_branding` column records the decision
- Already-sent PDFs are NOT retroactively unstamped if user upgrades later

## Pricing IS the moat — don't deviate

- Solo $2.99 is below the category floor (JobTread $199, Knowify $78, ServiceTitan $398). Anchoring is structural.
- Don't propose discounts / coupons — founder pricing is the only "discount" mechanism, and it's real
- Don't add "Pro" or "Business" marketing tier names — Solo / Advanced / Enterprise are role-shaped, not status-shaped
- Don't add an annual-only tier
- Don't show "limited time" countdown timers outside founder seat counters

## Don't do

- ❌ Add cap enforcement only client-side (always server-side too via `requireCap`)
- ❌ Cache tier in JWT for > 7 days without refresh on tier change
- ❌ Bypass `tierResolver` to read tier directly from `profiles.tier`
- ❌ Touch legacy `pricingTiers.ts` (the 3-6-9 pyramid — being retired by F2.x)
- ❌ Add new tier-gated features without updating `Entitlements` type and `CAPS_BY_TIER` map
- ❌ Use red as a destructive button color (only for inline text in delete dialog)
- ❌ Add proactive cap-approach warnings (friction at moment of value only)
- ❌ Send PII (`email, name, profile_id`) in `gate_hit_events.metadata`
- ❌ Pre-create founder seats outside the migration's pre-mint INSERT

## Linked specs

- `docs/specs/OFFER_ARCHITECTURE.md` — full Hormozi-shaped offers
- `docs/specs/pricing-config.json` — machine-readable pricing
- `docs/prds/F2.2`, `F5.1`, `F5.2`, `F6.1`, `F7.1` — implementation PRDs
- `docs/journeys/USER-JOURNEYS.md` — 8 user journeys with tier-gate trigger points
- `docs/ops/SUCCESS-METRICS.md` — funnel + telemetry event taxonomy
