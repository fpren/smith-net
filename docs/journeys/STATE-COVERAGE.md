# Smith Net — State Coverage

**Scope:** every state of the 12 net-new UI surfaces from UX-DESIGN.md. Existing screens are not in scope (they're already shipping).

**Aesthetic:** all states render in light mode only (per `TradeMeshTheme` forced light). All colors from EXTRACTED-PATTERNS.md §3 — no others.

State types tracked per surface: `default / loading / empty / partial / cap-approached / cap-hit / locked / dismissed / converted / error / disabled / disconnected`. Not every surface has every state — only what applies.

---

## N1 — Trial banner

| State | When | Render |
|---|---|---|
| Hidden | No trial active OR paid (any tier) | banner not in tree |
| Solo trial day 1-7 | trial active, > 7 days left | `SOLO TRIAL · ${daysLeft} DAYS LEFT · TAP TO LOCK FOUNDER PRICING` (captionBold, surface bg, primary-blue background-tint? — NO, surface bg only) |
| Solo trial day 8-12 | trial, 3-7 days | `SOLO TRIAL · ${daysLeft} DAYS LEFT · ${X} FOUNDER SPOTS LEFT` |
| Solo trial day 13-14 | trial, ≤ 2 days | `SOLO TRIAL ENDS IN ${daysLeft} DAYS · TAP TO STAY SOLO` (no color change, just text) |
| Advanced trial day 1-14 | trial, > 16 days | `ADVANCED TRIAL · ${daysLeft} DAYS · SMITHAI IS LEARNING YOUR JOBS` |
| Advanced trial day 15-28 | trial, 3-15 days | `ADVANCED TRIAL · ${daysLeft} DAYS LEFT · ${X} LIFETIME SPOTS LEFT` |
| Advanced trial day 29-30 | trial, ≤ 2 days | `ADVANCED TRIAL ENDS IN ${daysLeft} DAYS · TAP TO KEEP SMITHAI` |
| Enterprise trial day 1-13 | CC captured, charge pending | `ENTERPRISE TRIAL · ${daysLeft} DAYS · INVITE YOUR CREW` |
| Trial expired (post Solo trial, no convert) | day 15 with no CC | `TRIAL ENDED · YOU'RE ON OPEN · TAP TO REACTIVATE` |
| Trial expired (post Advanced) | day 31 no CC, was Solo paid | `ADVANCED TRIAL ENDED · YOU'RE BACK ON SOLO` |
| Trial expired (post Enterprise) | day 31 no charge, was Advanced paid | `ENTERPRISE TRIAL ENDED · YOU'RE BACK ON ADVANCED` |
| Network unavailable | counter can't refresh | text shows last known; muted dot indicates stale |
| Founder seats exhausted (mid-trial) | the seat reservation expires unconverted AND someone else takes the last | text degrades to remove `${X} SPOTS LEFT` part |

**Tap target:** the entire banner row.
**Dismissable:** No. Only auto-dismisses on conversion or trial-end.

---

## N2 / N3 / N10 / N11 — Locked-feature overlay (single component)

Same component, four content variants. States below apply to every variant.

| State | When | Render |
|---|---|---|
| Default | overlay opens cold | top card (surface bg, padded), bottom 40% dimmed preview (alpha 0.4) |
| With founder counter | seats remain | counter pill below price line: `● ${X} OF ${total} ${BONUS_NAME} LEFT` |
| With seats < 100 | running low | counter goes muted (text color `#656D76`, dot stays green) — implies urgency without color screaming |
| Founder seats exhausted | 0 left | counter changes to `0 OF ${total} SPOTS — STANDARD PRICING NOW` (gray dot) — never lie about scarcity |
| Loading (counter fetching) | first paint | counter row shows `· · · LOADING SPOTS · · ·` (literal dots, monospace) for max 800ms |
| Server unavailable for counter | counter API failed | counter row hides; CTA still works |
| CTA primary action focused | TalkBack focus | accessibility hint: "Try free 14 days, no credit card required, opens trial flow" |
| CTA tapped | user converts | overlay dismisses immediately; trial-flow screen takes over (handled by N7 path) |
| Secondary "Maybe later" tapped | user dismisses | overlay closes; if part of an attempt (N4/N5/N10/N11), Toast confirms dismissal |
| Background dimmed area tapped | user dismisses | same as Maybe later |
| Reduced motion | accessibility setting on | no fade-in; appears instantly |
| Network offline | upgrade attempted | CTA shows `[ NO CONNECTION — OFFLINE ]` (disabled) instead of activating trial; reverts when network returns |

**Per-variant content (table):**

| Variant | Title | Body | CTA primary |
|---|---|---|---|
| N2/N3 PLAN Compiler | `PLAN COMPILER` | "Your plan, compiled. Runs the same way every time." | `TRY SOLO FREE 14 DAYS — NO CC` |
| N10 SmithAI | `SMITHAI` | "On-device. No cloud. Watches your jobs and helps without ever sending data anywhere." | `TRY ADVANCED FREE 30 DAYS — NO CC` |
| N11 Crew Mode | `CREW MODE` | "Bring your crew on the same plan. $50/mo for the whole team — not per seat." | `START 14-DAY ENTERPRISE TRIAL` |
| N4 Active job cap | `ONE ACTIVE JOB AT A TIME` | "Smith Net Open caps at 1. Close your active job to start another, or unlock unlimited with Solo." | `TRY SOLO FREE — NO CC` |

---

## N4 — Active-job cap soft wall (uses overlay component, content variant)

| State | When | Render |
|---|---|---|
| Hidden | user is paid OR has 0 active jobs | n/a |
| Cap-approached (1 active) | Free user, 1 active, no attempt | n/a — no proactive warning per Principle 2 |
| Cap-hit (Save attempt) | Free user, 1 active, taps Save on a 2nd | Overlay (N4 variant) |
| Already-on-trial | trial active | n/a — Solo+ has no cap |
| Server returns 403 | always | overlay shows; telemetry fires `gate_hit.active_job_cap` |
| Network unavailable when attempting | offline | local check still warns: overlay shows immediately (UX) but the actual job creation queues offline; on reconnect, server cap re-evaluates and may reject (then a Toast notifies) |
| Race: user closes existing job in another window before tapping Save | rare | server allows the create on Save; overlay never fires |

---

## N5 — PDF send cap counter + cap-hit overlay

| State | When | Render (in send dialog footer) |
|---|---|---|
| Hidden | paid tier (no cap) | n/a |
| 0 sends used this month | new month, no sends | `0 of 5 free sends used this month` (muted caption) |
| 1-3 sends used | progress | `${count} of 5 free sends used this month` |
| 4 sends used | one left | `4 of 5 free sends used this month — 1 left` (muted caption, slightly emphasized) |
| 5 sends used (about to hit cap) | the 5th send is being attempted | counter updates AFTER successful send to `5 of 5 free sends used this month — full` |
| 6th attempt | cap hit | overlay (N5 content) fires; Telemetry: `gate_hit.pdf_send_cap` |
| 6th attempt + queued send | user dismisses upgrade | Toast: `Send saved as draft. Will send Day 1 of next month if still on Open.` (server schedules) |
| Network unavailable for cap check | client thinks within cap, server says no | server returns 403 with cap details; client reconciles + shows overlay after-the-fact |
| Month boundary mid-attempt | very rare | server uses server-time, not client-time, for cap window |

---

## N6 — Founder seats counter pill (embedded in N2/N3/N10/N11)

| State | When | Render |
|---|---|---|
| Live count, > 100 left | seats plenty | `● 747 OF 1000 FOUNDER SPOTS LEFT` (green dot, body text) |
| Live count, 11-100 left | running low | `● ${X} OF 1000 FOUNDER SPOTS LEFT` (green dot, muted text color) |
| Live count, 1-10 left | almost gone | `● ${X} OF 1000 FOUNDER SPOTS LEFT` (green dot, primary color text — not red, the existing app doesn't do red urgency) |
| 0 left | exhausted | `0 OF 1000 SPOTS — STANDARD PRICING NOW` (gray dot, muted text); upgrade CTA is still active but at standard price |
| Loading | first paint | `· · · LOADING SPOTS · · ·` |
| Stale (no recent server push) | > 60s since last update | dot color = green but slightly muted (50% alpha) |
| Server unavailable | API down | counter hides entirely; upgrade CTA still works |
| Reservation held (user clicked CTA, server holds 10min) | optimistic update | local count decrements by 1 immediately; reconciles with server on checkout completion |

---

## N7 — Tier selection / pricing screen

| State | When | Render |
|---|---|---|
| Default | user opens screen | 4 tier sections (Free, Solo, Advanced, Enterprise), separated by `ConsoleSeparator` |
| Current tier | always one is current | section gets `surface-variant` background tint + `[ CURRENT TIER ]` label instead of CTA |
| Tier with active trial | trial in progress | section shows `[ TRIAL — ${daysLeft} DAYS LEFT ]` instead of CTA |
| Tier above current | upgrade options | full CTAs visible: trial + immediate-pay |
| Tier below current | downgrade options | only `[ DOWNGRADE TO ${tier} ]` — secondary action; confirmation dialog before action |
| Founder pricing visible | seats remain | per-tier counter pill below price |
| Founder pricing exhausted | seats gone | "founder lifetime price" line removed; standard price shows |
| Annual toggle off (default) | viewing monthly | shows monthly prices |
| Annual toggle on | tap toggle | shows annual prices + "save $X / yr" lines per tier |
| Loading | first paint, fetching entitlements | tier sections render skeletons (3 lines per section, monospace dashes) — max 800ms |
| Network unavailable | API down | screen still renders from local cache (entitlements last-known); CTA shows `[ NO CONNECTION — TRY AGAIN ]` (disabled) |
| Anchor table at bottom | always visible | rendered as a single mono row: `JobTread $199 · Knowify $78 · ServiceTitan $398 · Smith Net Solo $2.99` |

---

## N8 — Subscription detail screen

| State | When | Render |
|---|---|---|
| Open tier | user is Free | minimal screen: `CURRENT TIER · Smith Net Open · $0/mo`, then a `> Upgrade` chevron row at top, then `> Delete account` |
| Solo paid | user is paid Solo | full layout per UX-DESIGN.md §3 N8 |
| Advanced paid | user is paid Advanced | similar to Solo, with AI-status row inserted (e.g., `SMITHAI · Loaded · 1.2GB on device`) |
| Enterprise paid | user is paid Enterprise | adds CREW row (count of crew members) + `> Manage crew` chevron |
| Trial active | any trial | tier row shows trial banner echo `(TRIAL — ${days} LEFT)` and `Next bill` row shows `${trialEndDate} (charge if you keep)` |
| Cancelled, period not ended | cancelled subscription, before period end | tier row shows `Smith Net Solo (canceling May 30)`; `Next bill` row shows `none — cancels at period end`; new row `> Reactivate subscription` |
| Founder pricing locked | flag set | new row in `FOUNDER PRICING` section: `● Locked at $${price}/${cadence} for life` |
| Payment method missing (Solo trial in flight) | trial active, no CC | row shows `> Add payment method (locks Solo at trial end)` |
| Payment method declined | last bill failed | red-text row (this IS one of the very few places we use red, per Material `error`): `Payment failed — update card to keep Solo`; tap → update flow |
| Loading | first paint | sections render skeletons |
| Network unavailable | API down | shows last-known state from local cache; muted bar at top: `Offline — last updated ${time}` |

---

## N9 — Branded PDF stamp (server-side)

This isn't a Compose state machine; it's a render-time conditional. States:

| State | When | Render |
|---|---|---|
| Stamp present | tier = Open at PDF render time | footer block (HTML): `Sent via Smith Net — smithnet.app` + email signature `Sent via Smith Net (smithnet.app)` |
| Stamp absent | tier = Solo / Advanced / Enterprise at PDF render time | no footer block; email signature is whatever the user configured |
| Tier downgrade between draft and send | user downgrades before send completes | server uses tier at SEND time (deterministic) |
| Tier upgrade mid-month, retroactive sends | already-sent PDFs | not retroactively un-stamped (already sent to client) |

---

## N10 — Settings > AI Assistant section (Solo lock state)

| State | When | Render |
|---|---|---|
| Free or Solo, AI section locked | tier < Advanced | section title `AI ASSISTANT` (existing), body row `● Locked — Advanced tier` + sub-line `Tap to learn what SmithAI does`, secondary line `[ Try Advanced free 30 days — no CC ]` (text-link style) |
| Advanced+, AI section unlocked | tier ≥ Advanced | existing AI section UI unchanged (Load button, model state, etc.) |
| Trial active, model still downloading | trial day 1-2 | progress UI from existing pattern (`LinearProgressIndicator` per existing settings) |
| Trial active, model loaded, AI ALIVE | normal | existing UI |
| Trial expired, downgraded to Solo | yesterday paid Adv, today paid Solo | section returns to lock state; existing model file remains on device but isn't loaded; small caption: `Model file kept (1.2GB) — re-upgrade to enable` + `[ Free up space ]` action |

---

## N11 — Crew invite lock state

| State | When | Render |
|---|---|---|
| Solo or Advanced taps `+ INVITE COLLEAGUE` | trial: not yet started | overlay (N11 variant) fires |
| Enterprise tapping `+ INVITE COLLEAGUE` | tier = Enterprise | existing invite flow runs unchanged |
| Trial active, mid-onboarding crew | day 1-14 of Enterprise trial | existing flow runs; trial banner reminds days left |
| Crew invite limit edge case | post-launch only — Enterprise has unlimited per pricing-config.json | n/a v1 |

---

## N12 — Tier-gate Toast

Standard Android Toast, no state machine. Two variants:

| Variant | When | Render |
|---|---|---|
| Dismiss confirm | user dismisses an overlay | Toast: `Maybe later. Free tier active.` (LENGTH_SHORT) |
| Cap reminder mid-session | repeat attempt at same gate within session | Toast: `Cap reached. Upgrade in Settings > Subscription.` (LENGTH_SHORT) |

---

## Cross-surface: connectivity states

The app already handles online/offline robustly. Net-new UI inherits the existing pattern but adds two specific behaviors:

| Surface | Online behavior | Offline behavior |
|---|---|---|
| N1 Trial banner | live counter | shows last-known (no error) |
| N2-N4, N10, N11 overlays | CTA enabled | CTA disabled, replaced with `[ NO CONNECTION — OFFLINE ]` |
| N5 PDF cap | server-authoritative | client shows last-known; if offline send is queued, counter increments locally; on reconnect, server reconciles |
| N6 Founder seats | live | counter hidden if stale > 60s without refresh |
| N7 pricing screen | live | last-cached entitlements + warning bar `Offline — pricing may be stale` |
| N8 subscription | live | last-cached + `Offline — last updated ${time}` bar |
| N9 PDF stamp | n/a (server-side) | n/a |

---

## State coverage completeness check

| Net-new surface | States enumerated | Missing? |
|---|---|---|
| N1 Trial banner | 13 | none |
| N2/N3/N10/N11 overlay | 11 | none |
| N4 active-job cap | 7 | none |
| N5 PDF cap | 9 | none |
| N6 founder counter | 8 | none |
| N7 pricing screen | 12 | none |
| N8 subscription detail | 11 | none |
| N9 PDF stamp | 4 | none |
| N10 AI lock state | 5 | none |
| N11 crew invite lock | 4 | none |
| N12 Toast | 2 | none |

**Total enumerated states: 86.** Step 7 (Interface States) will turn this into a render-able spec per state.
