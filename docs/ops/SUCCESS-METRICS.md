# Smith Net — Success Metrics

## North Star

**Paid Solo conversions per 100 free signups, measured at 30 / 60 / 90 days.**

Why this and not MAU: Smith Net's whole thesis is that the moat (PLAN Compiler) is worth $2.99/mo. If free users don't convert to Solo, the moat isn't landing. Optimizing MAU encourages a free-forever product; optimizing free→Solo conversion forces the entire team to make the upgrade moment crisp.

Target by stage:

| Stage | Target |
|---|---|
| Private testing (now) | N/A — manual onboarding, qualitative feedback only |
| First public 30 days | 5% free→Solo by day 30 |
| Months 2-3 | 8% free→Solo by day 30, 12% by day 60 |
| Months 4-6 | 12% free→Solo by day 30, 18% by day 60, 22% by day 90 |
| Steady state (post 6mo) | 15% free→Solo by day 30, 25% by day 90 |

---

## v1 launch success criteria (from MASTER_PRD §11)

| # | Criterion | Threshold | Why |
|---|---|---|---|
| L1 | Active free users in 30 days | ≥ 100 | Proves the funnel works (organic + branded-PDF distribution) |
| L2 | Paid Solo conversions in 60 days | ≥ 10 (10% of L1) | Proves moat is worth $2.99 |
| L3 | Paid Advanced conversions in 90 days | ≥ 1 | Proves AI tier demand |
| L4 | Crash-free user rate (Android, daily) | ≥ 99.5% | NFR-R1 baseline |
| L5 | Median time-to-first-invoice for new free users | < 10 min | Onboarding friction acceptable |

If L1 fails → distribution problem (branded PDFs not landing, no organic signal).
If L1 passes but L2 fails → moat not landing, free tier too generous, or PLAN Compiler value not visible.
If L2 passes but L3 fails → SmithAI value not visible / Solo users content.
If L4 fails → quality halt; fix before scaling spend.

---

## Funnel metrics (free → paid, instrumented)

```
[1] App install
  ↓
[2] Account created (signup completed)
  ↓
[3] First job created
  ↓
[4] First invoice drafted
  ↓
[5] First invoice PDF generated (Smith Net branded)
  ↓
[6] First invoice sent (Smith Net branded — passive distribution starts here)
  ↓
[7] Second job attempted (hits "1 active job" gate → upgrade CTA shown)
       OR 6th PDF send attempted (hits "5 PDFs/mo" gate → upgrade CTA shown)
       OR PLAN Compiler preview opened (sees grayed-out compiled artifact)
  ↓
[8] Solo trial started (14-day, no CC)
  ↓
[9] Trial converted to paid Solo (CC entered)
  ↓
[10] AI Assistant tab opened (Solo user) → Advanced upgrade CTA shown
  ↓
[11] Solo upgraded to Advanced
  ↓
[12] Crew invite attempted (Advanced user) → Enterprise upgrade CTA shown
  ↓
[13] Advanced upgraded to Enterprise
```

**Per-step conversion targets (steady state):**

| Step | Conversion | Target |
|---|---|---|
| [1] → [2] | install → signup | 60% |
| [2] → [3] | signup → first job | 80% |
| [3] → [4] | first job → first invoice draft | 70% |
| [4] → [5] | draft → PDF generated | 90% |
| [5] → [6] | PDF generated → sent | 95% |
| [6] → [7] | sent → gate hit | 50% (the rest don't yet need a 2nd job or 6th PDF) |
| [7] → [8] | gate hit → trial started | 50% |
| [8] → [9] | trial → paid Solo | 35% |
| [9] → [10] | Solo → AI tab opened | 40% |
| [10] → [11] | AI CTA → Advanced | 25% |
| [11] → [12] | Advanced → crew invite attempted | 20% |
| [12] → [13] | crew CTA → Enterprise | 30% |

**Compounded:** install → Solo = 60% × 80% × 70% × 90% × 95% × 50% × 50% × 35% ≈ **2.5%**. That's the baseline. Each lever above moves the number — instrument them all so we know which to pull.

---

## Leading indicators (catch problems before they hit revenue)

| Indicator | Signal | Sample at |
|---|---|---|
| Crash-free session rate (Android) | < 99.9% → halt rollout | weekly |
| Time-to-first-invoice (median) | rising > 15 min → onboarding broke | weekly |
| Mesh peer connect rate (BT/WiFi-Direct success) | < 80% → MeshService regression | per release |
| SmithAI cold-load time (median) | > 30s → model bloat or device support shrinking | per release |
| Supabase realtime reconnect rate | > 5% reconnect failures → backend or transport issue | weekly |
| Free user gate-hit rate (any of: active-job cap, PDF cap, PLAN preview opened) | < 30% by day 14 → free tier too generous | weekly |
| Solo trial start rate (per gate hit) | < 40% → CTA copy or upgrade UX is broken | weekly |
| Trial conversion rate (paid CC entered) | < 30% → Solo value isn't matching trial expectation | per cohort |
| Advanced upgrade rate (per Solo) | < 20% by day 90 → SmithAI value isn't landing | per cohort |
| Refund / cancel rate within 7 days | > 10% → over-promising at upgrade | per cohort |
| Support tickets per 100 MAU | > 5 → product friction | monthly |
| NPS from paid users | < 30 → product-market fit gap | quarterly |

---

## Tier-gate event taxonomy (telemetry — NFR-OB3 + NFR-OB5)

Every tier gate fires a structured event. Build the upgrade UI from the data, not the other way around.

| Event | Fired when | Properties |
|---|---|---|
| `gate_hit.active_job_cap` | Free user attempts 2nd active job | tier, user_id_hash, current_active_jobs, time_since_signup |
| `gate_hit.pdf_send_cap` | Free user attempts 6th PDF in a month | tier, user_id_hash, sends_this_month, time_since_signup |
| `gate_hit.plan_compiler_preview` | Free or Solo user opens PLAN preview pane | tier, user_id_hash, viewed_compiled_preview, time_since_signup |
| `gate_hit.ai_tab` | Free or Solo user opens AI Assistant section | tier, user_id_hash, time_since_signup |
| `gate_hit.crew_invite` | Free / Solo / Advanced user attempts colleague invite | tier, user_id_hash, time_since_signup |
| `tier_upgrade.cta_shown` | Upgrade CTA rendered | from_tier, to_tier, trigger_event, gate_id |
| `tier_upgrade.cta_clicked` | User taps CTA | from_tier, to_tier, trigger_event, gate_id |
| `tier_upgrade.trial_started` | 14-day Solo trial begins | from_tier, has_cc |
| `tier_upgrade.paid_converted` | Subscription begins charging | from_tier, to_tier, trigger_event_at_trial_start |
| `tier_downgrade.canceled` | Subscription canceled | from_tier, to_tier, days_active, ltv_usd |

**No PII in these events** (per NFR-OB4) — `user_id_hash` only.

---

## What we will NOT measure (scope discipline)

- **Vanity:** total users, total downloads, total messages sent — these don't predict revenue.
- **Engagement for engagement's sake:** session length, screens per session — irrelevant unless tied to a tier gate or upgrade event.
- **AI usage time:** SmithAI is a paid feature, not a product KPI. We measure its uptake (Solo→Advanced upgrade) not its dwell time.
- **Mesh popularity:** the mesh is a *resilience* feature. Measure connect-success rate (NFR), not adoption rate.

---

## When a metric tells us to change the spec

| Observation | Likely root cause | Spec change |
|---|---|---|
| Free→Solo at day 30 < 5% AND `gate_hit.pdf_send_cap` < 10% | Cap too generous, users not hitting it | Tighten PDF cap or active-job cap (Step 1.5 retune) |
| Free→Solo at day 30 < 5% AND `gate_hit.*` > 50% | Cap working, but Solo value unclear | Strengthen PLAN Compiler preview pane visual |
| Solo→Advanced at day 90 < 5% | SmithAI not compelling at $9.99 | Re-evaluate Advanced bundle (add Advanced template clarity, AI demos) |
| Refund rate > 10% in 7 days | Trial over-promised | Tighten trial onboarding to set realistic expectation |
| Mesh connect rate < 80% on a release | MeshService regression | Halt rollout, fix in patch |
| `gate_hit.plan_compiler_preview` opens < 20% of Free MAU | Preview not discoverable | Promote preview to Dashboard tile, not buried in plans tab |

---

## Reporting cadence

| Report | Frequency | Audience |
|---|---|---|
| Funnel + tier-gate dashboard | Daily | Founder |
| Crash-free + perf + mesh health | Per release | Founder + any dev |
| Cohort retention + LTV | Weekly | Founder |
| NPS + support themes | Monthly | Founder |
| Quarterly review against L1-L5 | Quarterly | Founder |
