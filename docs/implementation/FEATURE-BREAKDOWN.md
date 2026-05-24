# Smith Net — Feature Breakdown (Shape Up + Story Map + Betting Table)

**Sigma step:** 10 — Feature Breakdown
**Methodology:** Shape Up (Basecamp) for shaping bets, Story Mapping (Patton) for spanning the journey, INVEST criteria for each pitch, Betting Table for prioritization.

**Inputs:**
- 19 launch blockers (B1-B19 in `DEV-READINESS.md §4`)
- 14 security gaps (S1-S14 in `SECURITY.md §17`)
- 12 net-new components + 8 critical flows (Step 5)
- ~20-22 PRDs estimated in `TRACEABILITY-MATRIX.md §15`

**Output:** the prioritized betting table that Step 11 PRDs will execute against.

---

## 1. Shape Up appetites

| Appetite | Time box | Use for |
|---|---|---|
| **Small batch** | 2 weeks (10 working days) | Surgical fixes, isolated components, 1-2 engineers |
| **Big batch** | 6 weeks (30 working days) | Cross-cutting features, full vertical slices, 1-3 engineers |
| **Cool-down** | 2 weeks between cycles | Bug fixes, polish, planning next bets |

For Smith Net at this stage: assume **1-2 engineers active** (founder-led + occasional contractor). Most pitches sized small-batch; the big bets reserved for the moat-adjacent work.

---

## 2. Story map (the contractor journey, top to bottom)

```
BACKBONE (the contractor's day)
  Sign up    Onboard    Day 1 work    Bid     Job runs    Invoice    Get paid    Repeat

WALKING SKELETON (Free / Open — current MVP, mostly shipping)
  ├── Auth       ├── Onboarding   ├── Create job   ├── Generate proposal   ├── Pipeline   ├── Generate invoice   ├── (manual)
  ├── Welcome    ├── Trade picker ├── Time entry   ├── (sketch)            ├── (status)   ├── Send w/ branding   ├── Mark paid
  └── A4 (NEW)   └── Profile      └── Materials    └── Public link         └── Comms      └── PDF cap (NEW)      └── Status badge

TIER UNLOCKS (added by Step 11 — what makes paying tiers worth paying)
  Solo     PLAN Compiler UI surfacing + cord state in jobs/comms + unlimited caps + no branding
  Adv      SmithAI lock removal + AI proactive suggestions + Advanced invoice template
  Ent      Crew accounts + shared jobs + Enterprise invoice template + dispatch upgrade

PLATFORM SUBSTRATE (cross-cutting — earlier the better)
  Auth hardening (S1, S5, S6) → Server-authoritative tier resolver → Stripe + Play Billing →
  Telemetry sink → Audit log to DB → CORS + secret hardening (S2, S4) → Mesh encryption verify (S3)
```

---

## 3. The pitches (12 bets shaped from the 19 blockers)

Each pitch follows Shape Up's pitch template:
- **Problem:** what's broken / missing
- **Appetite:** small batch / big batch
- **Solution sketch:** the agreed-upon scope
- **Rabbit holes:** what NOT to chase
- **No-gos:** what's explicitly out of scope
- **INVEST check:** Independent / Negotiable / Valuable / Estimable / Small / Testable

---

### PITCH 1 — Auth security hardening (S1, S2, S4, S5, S6)

**Problem:** several Intent/Synthesis/Ledger/Channel endpoints accept `X-User-Id` as "simplified auth." JWT secret has dev fallback in code. CORS is `origin: '*'`. Password floor is 6 chars. No email verification. Together: cannot publicly launch without these closed.

**Appetite:** Small batch (2 weeks)

**Solution sketch:**
- Remove `X-User-Id` parsing from all routes; replace with `authenticateToken` middleware everywhere
- Add `JWT_SECRET` startup check: refuse boot if equals `'smith-net-dev-secret-change-in-production'` AND `NODE_ENV=production`
- Lock CORS to `['https://portal.smithnet.app', 'https://smithnet.app', /^smithnet:\/\//]` (allow regex for Android scheme)
- Raise password floor to 8 chars + require digit + letter
- Add per-account lockout: 5 fails → 15min cooldown (use `gate_hit_events`-like table for failed_logins)
- Email verification via existing `nodemailer` or similar — block all non-`/auth/*` routes until verified

**Rabbit holes:**
- DON'T add 2FA (out of scope v1)
- DON'T migrate to OAuth providers
- DON'T rewrite the JWT scheme (HS256 stays)

**No-gos:**
- Multi-tenancy / SAML / SSO — post-launch
- Password reset email flow already exists (left untouched) — only enhance lockout

**INVEST check:**
- ✅ Independent (no dependency on tier work)
- ✅ Negotiable (could ship just S1+S2+S4 if time-pressed)
- ✅ Valuable (unblocks public launch)
- ✅ Estimable (small batch confidently)
- ✅ Small (one engineer, 2 weeks)
- ✅ Testable (security audit + integration tests)

**Risk:** breaking existing client calls that depend on `X-User-Id`. Mitigation: dual-path during rollout, instrument deprecation warning in logs for 1 release before hard removal.

---

### PITCH 2 — Tier resolver + entitlements + JWT claims (B3, B4 partial)

**Problem:** tier doesn't exist as a server-authoritative concept. No way to enforce caps. No way for client to know what user can do.

**Appetite:** Big batch (6 weeks)

**Solution sketch:**
- Migration M1: add `profiles.tier`, `tier_expires_at`, `trial_*` columns
- Migration M2: create `subscriptions` table (per `SCHEMA.md §11`)
- New service: `tierResolver.ts` (loads tier from `subscriptions` reconciled with `profiles`)
- New endpoint: `GET /api/me/entitlements` returning `Entitlements` object per `API-SPEC.md §15`
- Add `tier` and `entitlements` claims to JWT
- JWT rotation on tier change (server pushes new token in response)
- Middleware `requireTier(minTier)` for tier-gated endpoints
- Server enforcement at: POST /api/jobs (cap check), POST /api/invoices/:id/send (cap check), POST /api/synthesize (Solo+ check), POST /api/colleagues/invite (Ent check)

**Rabbit holes:**
- DON'T integrate Stripe yet (PITCH 3 separate)
- DON'T build the upgrade UI yet (PITCH 4 separate)
- DON'T migrate legacy `pricingTiers.ts` 3-6-9 pyramid yet (separate cleanup)

**No-gos:**
- Multi-tier-per-user (e.g., per-org tiering) — post-launch
- Tier-aware feature flags from a remote service — server-side static for v1

**INVEST check:**
- ⚠️ Independent — depends on PITCH 1 (auth must be solid first)
- ✅ Negotiable
- ✅ Valuable (substrate for everything)
- ✅ Estimable
- ⚠️ Small — borderline; may need to spike timing
- ✅ Testable

**Risk:** schema changes to live DB. Mitigation: M1/M2 migrations are additive (no data loss); subscriptions table starts empty; existing users get default `tier='open'`.

---

### PITCH 3 — Stripe + Play Billing integration (B2, S9, S10)

**Problem:** no way to collect money. Stripe needed for web/desktop; Play Billing required by Play Store policy for Android in-app purchases.

**Appetite:** Big batch (6 weeks)

**Solution sketch:**
- Stripe: `POST /api/me/upgrade` → Stripe Checkout session → redirect; `/webhooks/stripe` handles `checkout.session.completed`, `invoice.paid`, `customer.subscription.deleted`, `customer.subscription.updated`
- Webhook signature verification (`stripe.webhooks.constructEvent`) — refuse unsigned
- Idempotency: store `event.id` to dedupe re-deliveries
- Play Billing: client-side BillingClient + `POST /api/me/upgrade-play` with purchase token; server verifies via Google Play Developer API; `/webhooks/play-billing` handles RTDN
- Subscription state mirrored into `subscriptions` table (atomic update via transaction)
- Cancel: `POST /api/me/cancel` → provider cancel-at-period-end; reactivate symmetric
- Trial-to-paid: when CC entered during trial, subscription transitions `trialing → active`
- Stripe Tax for v1 (defer custom tax handling)

**Rabbit holes:**
- DON'T build a custom payment UI in-app — use Stripe Checkout web view
- DON'T integrate Apple In-App Purchase (iOS out of scope v1)
- DON'T support multi-currency v1 (USD only)

**No-gos:**
- Custom payment processor (Square, Braintree) — Stripe is the choice
- Crypto payment — never
- Refund automation — Enterprise refunds issued manually per pricing-config.json

**INVEST check:**
- ⚠️ Independent — depends on PITCH 2 (tier resolver) and PITCH 1 (auth)
- ✅ Negotiable (could ship Stripe-only first, Play Billing in cool-down)
- ✅ Valuable (revenue!)
- ⚠️ Estimable — Play Billing always has surprises
- ❌ Small — confidently big batch; may need 2 engineers
- ✅ Testable (Stripe + Play test modes)

**Risk:** Play Billing real-time-developer-notifications can be unreliable. Mitigation: poll-based reconciler runs hourly as backstop.

---

### PITCH 4 — Tier-gate UI: 7 components + 3 net-new screens + lock overlays (B5, B6, B11, parts of B9)

**Problem:** no UI exists for tier upgrades. Users have no path from cap-hit to conversion.

**Appetite:** Big batch (6 weeks)

**Solution sketch:**
Build per `WIREFRAME-SPEC.md §1-§13`:
- Components: `LockedFeatureOverlay`, `TrialBanner`, `FounderSeatsCounter`, `TierUpgradeCTA`, `EntitlementLock`, `PdfSendCounterFooter`, `GateHitToast`
- Screens: `TierPricingScreen` (N7), `SubscriptionDetailScreen` (N8), `WelcomeToOpenScreen` (A4)
- Dialogs: `CancelSubscriptionDialog`, `DeleteAccountDialog`
- Wire into existing screens (`Q2 SettingsScreen`, `D3 NewJobFlow`, `H1 InvoiceScreen`, `G4 InvoicePreviewBottomSheet`, `E1 PlanScreen`, `C1 DashboardScreen`, `MainActivity`)
- All components consume `DesignTokens.kt` (auto-generated from `DESIGN-TOKENS.md`)
- All states from `STATE-SPEC.md` (108 enumerated)
- All micro-interactions from `MICRO-INTERACTIONS.md`

**Rabbit holes:**
- DON'T add a dark variant for any component (light-only)
- DON'T import Material Buttons / Dialogs / Snackbar
- DON'T build the desktop portal versions — Android first; portal in cool-down

**No-gos:**
- Animated decorations (springs, confetti, hero transitions) — forbidden by DESIGN-SYSTEM
- Custom illustrations / spot art for empty states — text-only
- Founder seat counter UI for non-Smith Net pages

**INVEST check:**
- ⚠️ Independent — depends on PITCH 2 (entitlements API) and PITCH 3 (billing for trial-start to actually work end-to-end)
- ✅ Negotiable (could ship N1+N4+N5+N7 first, others in second cycle)
- ✅ Valuable (the revenue funnel)
- ✅ Estimable (~25 dev days per Step 5 estimate)
- ❌ Small — big batch confirmed
- ✅ Testable (Compose UI tests + E2E)

**Risk:** scope creep on TierPricingScreen polish. Mitigation: ship MVP version per WIREFRAMES.md exactly; polish in cool-down.

---

### PITCH 5 — Founder seats reservation + telemetry sink (B12, parts of B4)

**Problem:** founder pricing needs server-authoritative atomic reservation. Telemetry events need a sink.

**Appetite:** Small batch (2 weeks)

**Solution sketch:**
- Migration M3: `founder_seats` table (per SCHEMA §11)
- Migration M4: `gate_hit_events` table
- Service: `founderSeatService.ts` — `reserve(bonusId, profileId)` returns `Reservation` with 10-min `expires_at`; `claim(reservationId)` finalizes; `release(reservationId)` returns to pool
- Concurrency: use Postgres `SELECT ... FOR UPDATE SKIP LOCKED` to avoid double-reserve
- Endpoint: `GET /api/founder-seats/:bonusId` returns `{remaining, total}` (cached 5s)
- WS push: `founder_seats_changed` broadcast on every claim/release
- Endpoint: `POST /api/telemetry/gate-hit` accepts `{event, currentTier, metadata}`, server stamps `user_id_hash = SHA256(profile.id)`, inserts row
- No PII in events — verified by sanitizer

**Rabbit holes:**
- DON'T build admin UI for managing seats (PITCH 11 cool-down)
- DON'T pre-create all 1110 seats; mint as needed
- DON'T include profile.id in telemetry rows — only the SHA256 hash

**No-gos:**
- Allowing manual seat creation outside the bonus_id system
- Real-time analytics dashboard — sink only for v1; query offline

**INVEST check:**
- ✅ Independent (no dependency on UI)
- ✅ Negotiable
- ✅ Valuable (locks the founder pricing mechanic)
- ✅ Estimable
- ✅ Small
- ✅ Testable (concurrency tests at 100 parallel reservations → exactly 1000 succeed)

**Risk:** WS push misses → counter goes stale on clients. Mitigation: client polls every 60s as backstop.

---

### PITCH 6 — Server-side enforcement + tier-gate 403 contract (B5)

**Problem:** server doesn't actually refuse over-cap actions. Client UI can be bypassed.

**Appetite:** Small batch (2 weeks)

**Solution sketch:**
- Middleware `requireTierCap(capName, getCurrentFn)` — refuses with structured 403 if `getCurrentFn(req) >= entitlements.caps[capName]`
- Apply to: `POST /api/jobs` (active_job_cap), `POST /api/invoices/:id/send` (pdf_send_cap), `POST /api/synthesize` (no cap; just tier ≥ Solo check)
- 403 response contract per `TECHNICAL-SPEC §3.4`: `{error, code, gate_id, current_tier, limit, current, details: {target_tier}}`
- Counter for PDF sends: per-user month-to-date count maintained as denormalized `profiles.pdf_sends_this_month` (reset by cron at month boundary) for fast cap check
- Active job count: query `jobs WHERE created_by=$uid AND status NOT IN ('done','archived')` — index already exists
- Telemetry: every 403 inserts `gate_hit_events` row

**Rabbit holes:**
- DON'T pre-warn user as they approach cap (per UX Principle 2 — no proactive nag)
- DON'T cache cap state in JWT (changes too fast)
- DON'T build a per-tier rate limit beyond the existing rate-limiter

**No-gos:**
- "Soft caps" with grace allowance
- Cap reset on user request (caps only reset via subscription change or month-end cron)

**INVEST check:**
- ⚠️ Independent — depends on PITCH 2 (entitlements) and PITCH 5 (telemetry sink)
- ✅ Negotiable
- ✅ Valuable
- ✅ Estimable
- ✅ Small
- ✅ Testable

**Risk:** PDF cap counter drift if cron fails. Mitigation: nightly job re-derives from actual send_log; reset is idempotent.

---

### PITCH 7 — Trial mechanics (no-CC start, expiry cron, downgrade flow) (B8, FLOW-6)

**Problem:** trial system doesn't exist. Need 14-day Solo + 30-day Advanced + 14-day Enterprise (with CC) trials, expiry handling, downgrade preserving data.

**Appetite:** Small batch (2 weeks)

**Solution sketch:**
- Endpoint: `POST /api/me/start-trial { targetTier }` — validates: no prior trial of same tier, founder seat reservable, current tier < target. On success: `subscriptions` row `trialing`, `profiles.tier_expires_at = NOW()+duration`, JWT rolled with new tier claim
- Cron: `trialExpirer` runs hourly; SELECT subscriptions WHERE status='trialing' AND current_period_end <= NOW() AND no payment_method; transitions to expired + tier downgrade (target = previous-paid tier OR Open)
- FCM push on expiry
- Client-side: 401 with `tier_changed` triggers JWT refresh
- Data preservation: NEVER delete; caps reapply but data accessible
- Reactivation: any cap-hit re-fires existing overlay flow with founder pricing IF seats remain

**Rabbit holes:**
- DON'T add "win-back" emails beyond day+1 follow-up
- DON'T support trial extension (one-shot only)
- DON'T let users start a 2nd trial of same tier ever

**No-gos:**
- "Pause subscription" feature
- Refund mid-trial (no charge during trial = nothing to refund)

**INVEST check:**
- ⚠️ Independent — depends on PITCH 2 (tier resolver), PITCH 5 (founder seats)
- ✅ Negotiable (could ship Solo trial only first)
- ✅ Valuable
- ✅ Estimable
- ✅ Small
- ✅ Testable (time-travel tests for cron)

**Risk:** clock skew in cron causing premature/delayed expiry. Mitigation: use server time (NOW()) consistently; test boundary conditions.

---

### PITCH 8 — Branded PDF stamp + send queue (B7, FLOW-1, FLOW-8)

**Problem:** Free tier needs Smith Net branding on PDFs/emails (passive distribution). PDF cap needs a queued-send mechanism so users don't lose work at month-end.

**Appetite:** Small batch (2 weeks)

**Solution sketch:**
- Edit `templates/invoice.html` and `templates/proposal.html`: add `{{#if isOpenTier}}` branded footer block (per WIREFRAME-SPEC §12)
- Edit email send: append `--\nSent via Smith Net (smithnet.app)` to body when tier=Open
- Server `/api/invoices/:id/send` looks up tier at send time (deterministic — see FLOW-9 edge case); locks stamp tier-state
- New: `pending_sends` table — queues 6th+ PDF for next month Day 1; cron `sendQueueProcessor` runs hourly
- Cap reset: monthly cron resets `profiles.pdf_sends_this_month=0` and triggers `sendQueueProcessor` to flush queued sends
- Public-page rate limit per UUID: 60 views/min via `express-rate-limit`

**Rabbit holes:**
- DON'T add branding customization options (Solo+ just removes the stamp; no alternate brand)
- DON'T add queue prioritization — FIFO simple
- DON'T support per-recipient tracking beyond `viewed_at`

**No-gos:**
- Letting the contractor opt out of the branding stamp on Free
- Embedding ads in the stamp footer (it's our brand only)

**INVEST check:**
- ✅ Independent (template edits)
- ✅ Negotiable (queue could ship later if cap counter hard-stop is acceptable v1)
- ✅ Valuable (passive distribution)
- ✅ Estimable
- ✅ Small
- ✅ Testable (HTML snapshot tests)

**Risk:** queued sends accidentally never flush. Mitigation: queue inspector endpoint for admin; alert on queue length > 100.

---

### PITCH 9 — Advanced + Enterprise invoice templates (B9 cont'd)

**Problem:** only Standard template exists. Advanced and Enterprise tier value depends on visibly richer templates.

**Appetite:** Small batch (2 weeks)

**Solution sketch:**
- New template: `templates/invoice-advanced.html` — line-item richness (notes per item, before/after photos, tax breakdown, terms section, payment status timeline)
- New template: `templates/invoice-enterprise.html` — multi-payer (split bill across N parties), milestone billing (% complete), draw schedule
- Add `invoices.template` column (migration M5)
- H1 InvoiceScreen template selector — locked rows for non-entitled tiers (per UX-DESIGN §3 H1 cross-tier visibility)
- Server validates tier matches selected template at send time
- Render path: per-template HTML → PDF

**Rabbit holes:**
- DON'T allow user to customize template HTML directly (post-launch)
- DON'T add WYSIWYG template editor
- DON'T support custom logos in v1 (templates are fixed-brand)

**No-gos:**
- More than 3 templates (one per tier; no proliferation)
- White-label removal at any tier

**INVEST check:**
- ⚠️ Independent — depends on tier resolver for template gating
- ✅ Negotiable (could ship Advanced first, Enterprise second)
- ✅ Valuable
- ✅ Estimable
- ✅ Small
- ✅ Testable (template snapshot tests)

**Risk:** scope creep on template visual design. Mitigation: extract minimum-viable Enterprise features (multi-payer, milestone) for v1; "draw schedule" can be deferred.

---

### PITCH 10 — Crew accounts + shared jobs + Enterprise dispatch (B10)

**Problem:** Enterprise hero feature is crew. Currently no multi-user. N11 lock fires but trial→paid path doesn't actually unlock anything functional.

**Appetite:** Big batch (6 weeks)

**Solution sketch:**
- Migration: extend `organizations` table (currently Supabase-only) into Hetzner schema; add `org_members` join table
- Endpoint: `POST /api/orgs` (create on first Enterprise upgrade) + `POST /api/orgs/:id/invite { email }` + `POST /api/orgs/:id/accept`
- Crew membership flows into existing `RoleContext` for client-side gating
- Shared jobs: `jobs.org_id` FK; `D1 JobBoardScreen` filter `WHERE created_by=me OR org_id=me.org_id` for Enterprise users
- O1 DispatchScreen: enable for Enterprise + Foreman+ role combo (currently role-gated only)
- Crew-aware SmithAI: existing `4ce8733` privacy gating extended — Enterprise crew can opt-in to share AI insights
- N11 lock removal for Enterprise tier (existing colleague invite flow becomes accessible)
- Onboarding kit (printed cards) — separate operational item, not engineering

**Rabbit holes:**
- DON'T build org-level billing (single Enterprise subscription per org)
- DON'T support cross-org collaboration v1
- DON'T add per-crew-member roles beyond existing 6 (FOREMAN, LEAD, etc.)

**No-gos:**
- SAML / SSO for crew invitations
- Bulk crew import via CSV (manual invites only v1)
- Removing colleagues immediately delete their data — soft-only

**INVEST check:**
- ⚠️ Independent — depends on PITCH 2 (tier resolver to gate Enterprise) and PITCH 4 (UI)
- ⚠️ Negotiable — Enterprise launch could be deferred 1 cycle if Solo + Advanced demand validates first
- ✅ Valuable (Enterprise revenue)
- ⚠️ Estimable — multi-user always has surprises
- ❌ Small — big batch
- ✅ Testable

**Risk:** highest-effort pitch with the smallest near-term revenue (Enterprise is small TAM). Defer to second cycle if Solo conversion lags.

---

### PITCH 11 — Audit log to DB + retention enforcement (B18, S8)

**Problem:** audit log is file-based, retention not enforced. Compliance + queryability gap.

**Appetite:** Small batch (2 weeks)

**Solution sketch:**
- Migration M8: `audit_log` table per `SCHEMA.md §9`
- Modify `auditLog.ts`: dual-write (file + DB) for transition window, then DB-only
- Cron `auditRetentionEnforcer` runs daily — applies retention policies per `AuditAction` category
- Per-entry SHA256 checksum already computed; verify on retention scan
- Admin endpoint: `GET /api/audit?action=...&actor=...&since=...` (admin role only)
- Periodic job: `auditChecksumVerifier` runs weekly — alerts on mismatch (tamper signal)

**Rabbit holes:**
- DON'T migrate historical file-based logs (archive separately)
- DON'T build admin search UI (CLI only v1)
- DON'T integrate with SIEM v1

**No-gos:**
- Retention policy changes per tenant — global policies only
- Letting audit logs be deleted by user (only retention cron)

**INVEST check:**
- ✅ Independent
- ✅ Negotiable
- ✅ Valuable (compliance)
- ✅ Estimable
- ✅ Small
- ✅ Testable

**Risk:** large initial DB migration if many file logs exist. Mitigation: only migrate forward — archive backward.

---

### PITCH 12 — Mesh encryption verification + signing + replay protection (S3, S12)

**Problem:** SECURITY.md asserts mesh payloads should be encrypted, but verification is needed. Plus message signing + replay protection not yet confirmed.

**Appetite:** Small batch (2 weeks)

**Solution sketch:**
- Audit `MeshService.kt` send/receive paths — confirm AES-GCM (or equivalent) is in use
- If not: add it. Use device-pair keys established via BLE pairing
- Add per-message signing: HMAC-SHA256 with shared key
- Replay protection: include monotonic `timestamp` + `messageId` (UUIDv4) in payload; receiver dedupes by `messageId` for 5-min window
- Document in `SECURITY.md` (update section 11)
- Penetration test: simulate replay attack + tampered payload → must fail

**Rabbit holes:**
- DON'T re-architect mesh transport
- DON'T add forward secrecy v1 (per-pair keys are fine for v1)
- DON'T support encrypted-at-rest for mesh storage v1 (use SQLite default)

**No-gos:**
- Custom crypto primitives (use Android `javax.crypto` / `BouncyCastle` standard)
- Skipping the audit and just claiming it's encrypted

**INVEST check:**
- ✅ Independent
- ✅ Negotiable (could time-box the audit; hard fixes scoped if discovered)
- ✅ Valuable (security)
- ⚠️ Estimable — depends on what audit reveals
- ✅ Small
- ✅ Testable (red-team)

**Risk:** audit reveals encryption is missing entirely. Worst case: 4 more weeks of work (escalate to second cycle). Best case: confirms it's already there + just needs documenting.

---

## 4. Pitches NOT made (explicitly deferred)

| Pitch | Why deferred |
|---|---|
| iOS client | out-of-scope v1 per MASTER_PRD §8 |
| Landing page | out-of-scope per user direction (Step 9 skipped) |
| Trade pack #2 (plumbing/HVAC) | template exists (electricianTools); next pack is post-launch |
| Multi-currency / i18n | NA-first per MASTER_PRD §3 |
| Custom plan template editor | post-launch |
| In-app payment customer portal | use Stripe Checkout web view v1 |
| Admin web UI | CLI / direct DB access v1 |
| Real-time analytics dashboard | telemetry sink only v1 |
| `pricingTiers.ts` 3-6-9 retire | low-impact tech debt; flag-only deletion in cool-down |
| Plan-to-Intent data migration (M6) | only matters if old `plans` table has real data; defer |
| K2/K3 ChannelListScreen consolidation | low-impact; cool-down |
| `electricianTools.ts` UI surfacing | no Step-3 wireframes; defer to trade-pack-launch cycle |
| Annual pricing UI toggle in N7 | nice-to-have; ship with monthly only first |
| 2FA / MFA | post-launch |
| GDPR data export endpoint | needed for compliance but not until first EU user |
| GDPR delete account flow | needed but not v1-launch blocker |

---

## 5. The betting table (Step 11 execution order)

Sized as Shape Up cycles. Two engineers in cycle = parallel pitches; one engineer = serial.

### Cycle 1 (~6 weeks) — substrate

| Pitch | Slot | Reason |
|---|---|---|
| **PITCH 1** Auth security hardening | 2 weeks (small batch) | Unblocks public launch; low risk; nothing else can ship publicly without it |
| **PITCH 2** Tier resolver + entitlements + JWT | 6 weeks (big batch, parallel with PITCH 1's last 4 weeks) | Substrate for all tier work |

**End of Cycle 1:** auth is hard, tier system exists in the DB + JWT. No revenue yet, but all subsequent pitches can build on these.

### Cycle 2 (~6 weeks) — revenue

| Pitch | Slot | Reason |
|---|---|---|
| **PITCH 5** Founder seats + telemetry sink | 2 weeks (small batch) | Quick infrastructure; runs in parallel |
| **PITCH 3** Stripe + Play Billing | 6 weeks (big batch) | Money plumbing |
| **PITCH 6** Server enforcement + 403 contract | 2 weeks (small batch, second slot) | Wire the caps to actually refuse — runs after PITCH 5 |

**End of Cycle 2:** users CAN pay. Caps ARE enforced. Founder seats ARE atomic. Telemetry IS flowing. **But the upgrade UX doesn't exist yet** — so revenue would come from API calls only. Bridge cycle.

### Cycle 3 (~6 weeks) — UX

| Pitch | Slot | Reason |
|---|---|---|
| **PITCH 4** Tier-gate UI (12 components + screens + dialogs + wiring) | 6 weeks (big batch) | The conversion funnel; the customer-facing payoff |
| **PITCH 7** Trial mechanics | 2 weeks (small batch, second slot) | Can ride alongside PITCH 4's UI work |
| **PITCH 8** Branded PDF stamp + send queue | 2 weeks (small batch, third slot) | Surgical |

**End of Cycle 3:** **paying customers can convert end-to-end.** This is the launchable state. Free → Solo trial → paid Solo → cap-hit → upgrade to Advanced → and so on.

### Cycle 4 (~6 weeks) — Advanced + Enterprise + security catch-up

| Pitch | Slot | Reason |
|---|---|---|
| **PITCH 9** Advanced + Enterprise templates | 2 weeks (small batch) | Adv tier value visible |
| **PITCH 12** Mesh encryption verify + signing | 2 weeks (small batch, second slot) | Pre-public-launch security gate |
| **PITCH 11** Audit log to DB | 2 weeks (small batch, third slot) | Compliance posture |

**End of Cycle 4:** Solo + Advanced launchable. Public launch security gates closed. Audit observable.

### Cycle 5 (~6 weeks) — Enterprise

| Pitch | Slot | Reason |
|---|---|---|
| **PITCH 10** Crew + shared jobs + dispatch upgrade | 6 weeks (big batch) | Defer until Solo + Advanced revenue validates Enterprise demand |

**End of Cycle 5:** Enterprise tier launchable. Full ladder live.

### Cool-down (between cycles)

- Bug fixes
- `pricingTiers.ts` retirement (PR + delete)
- K2/K3 consolidation
- Performance polish
- Doc updates
- Plan next cycle

---

## 6. Stop-the-line conditions

If any of these happen mid-cycle, halt + reshape:

| Condition | Response |
|---|---|
| Determinism test fails | Halt all work; investigate; fix or roll back |
| Mesh encryption audit reveals plaintext | Halt PITCH 12; reshape with extra appetite |
| Stripe webhook signature verification missed | Halt PITCH 3; cannot ship billing without this |
| `X-User-Id` removal breaks production traffic | Roll back; instrument deprecation warning; serialize the rollout |
| Founder seat reservation isn't atomic under load | Halt PITCH 5; redesign with explicit DB advisory lock |
| Tier resolver caches wrong values | Halt PITCH 2 deploy; dump cache; reshape |

---

## 7. Pitches × launch blockers traceability

| Blocker | Pitch |
|---|---|
| B1 X-User-Id removal | PITCH 1 |
| B2 Stripe + Play Billing | PITCH 3 |
| B3 Entitlements endpoint | PITCH 2 |
| B4 Tier tables (subscriptions, founder_seats, gate_hit_events) | PITCH 2 (M1, M2) + PITCH 5 (M3, M4) |
| B5 Cap enforcement | PITCH 6 |
| B6 Locked-feature CTAs | PITCH 4 |
| B7 Branding stamp | PITCH 8 |
| B8 14-day no-CC trial | PITCH 7 |
| B9 Adv + Ent templates | PITCH 9 |
| B10 Crew + shared jobs | PITCH 10 |
| B11 AI tab gate | PITCH 4 (EntitlementLock component) |
| B12 Telemetry events | PITCH 5 |
| B13 CORS + JWT secret | PITCH 1 |
| B14 Mesh encryption verify | PITCH 12 |
| B15 zod validation | PITCH 1 (S7 included scope) |
| B16 Public-page rate limit | PITCH 8 |
| B17 Webhook verification | PITCH 3 |
| B18 Audit log to DB | PITCH 11 |
| B19 plans → intents migration | NOT IN ANY PITCH (deferred — only matters if real data; flag-only delete in cool-down) |

**Coverage: 18 of 19 launch blockers in the betting table.** B19 deferred (low-impact).

---

## 8. Story-map → pitch coverage

Every story-map cell in §2 is owned by at least one pitch:

| Story map cell | Pitch(es) |
|---|---|
| Sign up + Onboarding (incl. A4) | PITCH 4 (WelcomeToOpenScreen) |
| Day 1 work (jobs / time / materials) | already shipping |
| Bid (proposal / public link) | already shipping |
| Job runs (pipeline / status / comms) | already shipping (mesh sync = PITCH 12 verify) |
| Invoice (generate / template / send) | PITCH 8 (stamp), PITCH 9 (templates) |
| Get paid (Stripe link, status) | PITCH 3 |
| Tier unlocks (PLAN / AI / Crew) | PITCH 4 (UI) + PITCH 6 (enforcement) |
| Substrate (auth / tier / billing / telemetry / audit / mesh) | PITCH 1, 2, 3, 5, 11, 12 |

**No orphan cells.**

---

## 9. INVEST roll-up

| Pitch | I | N | V | E | S | T |
|---|---|---|---|---|---|---|
| 1 Auth security | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 2 Tier resolver | ⚠️ (depends 1) | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| 3 Stripe + Play | ⚠️ (depends 1, 2) | ✅ | ✅ | ⚠️ | ❌ | ✅ |
| 4 Tier-gate UI | ⚠️ (depends 2, 3) | ✅ | ✅ | ✅ | ❌ | ✅ |
| 5 Founder seats + telemetry | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 6 Server enforcement | ⚠️ (depends 2, 5) | ✅ | ✅ | ✅ | ✅ | ✅ |
| 7 Trial mechanics | ⚠️ (depends 2, 5) | ✅ | ✅ | ✅ | ✅ | ✅ |
| 8 PDF stamp + queue | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 9 Adv + Ent templates | ⚠️ (depends 2) | ✅ | ✅ | ✅ | ✅ | ✅ |
| 10 Crew + dispatch | ⚠️ (depends 2, 4) | ⚠️ (deferrable) | ✅ | ⚠️ | ❌ | ✅ |
| 11 Audit log to DB | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 12 Mesh encryption verify | ✅ | ✅ | ✅ | ⚠️ | ✅ | ✅ |

**Healthy distribution:** mostly small batches, big bets reserved for true cross-cutting work (PITCH 2, 3, 4, 10).

---

## 10. What goes into Step 11 PRDs

For each pitch in the betting table, Step 11 generates **one or more vertical-slice PRDs** (database + service + UI + tests + BDD scenarios). Sized estimate:

| Pitch | Estimated PRDs |
|---|---|
| 1 Auth security | 5 (one per gap S1, S2, S4, S5, S6) |
| 2 Tier resolver | 2 (schema migration + service layer) |
| 3 Stripe + Play | 3 (Stripe + Play + webhook handlers) |
| 4 Tier-gate UI | 6 (one per logical UI cluster) |
| 5 Founder seats + telemetry | 2 (founder service + telemetry sink) |
| 6 Server enforcement | 1 (single middleware + 4 endpoint integrations) |
| 7 Trial mechanics | 1 (start-trial endpoint + cron + downgrade) |
| 8 PDF stamp + queue | 2 (stamp + send queue) |
| 9 Adv + Ent templates | 2 (one per template) |
| 10 Crew + dispatch | 4 (orgs, invites, shared jobs, dispatch upgrade) |
| 11 Audit log to DB | 1 |
| 12 Mesh encryption verify | 1 (audit + fix bundle) |
| **Total estimated PRDs** | **30** |

(Higher than the earlier ~20-22 estimate from `TRACEABILITY-MATRIX.md` because Pitch 1 alone breaks into 5 sub-PRDs, and Pitch 4 into 6.)

---

## 11. Linked specs

- `DEV-READINESS.md §4` — 19 launch blockers (B1-B19) — all but B19 covered
- `SECURITY.md §17` — 14 security gaps (S1-S14) — all covered
- `WIREFRAME-SPEC.md §15` — engineering effort estimate (~25 days net-new) — broadly consistent with this betting table
- `TRACEABILITY-MATRIX.md §15` — earlier PRD scope estimate (~20-22) — refined here to 30
- All Step 5 per-flow PRDs (FLOW-1 through FLOW-8) — feed Step 11 PRD authoring
- `pricing-config.json` — drives PITCH 5 (founder seats), PITCH 6 (caps), PITCH 7 (trial durations)
