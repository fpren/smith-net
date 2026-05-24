# Smith Net — Master PRD

| | |
|---|---|
| **Project** | Smith Net (aka TradeMesh, Guild of Smiths) |
| **Version** | 1.0 (retrofit baseline, Sigma Step 1) |
| **Date** | 2026-04-30 |
| **Owner** | Fegens Prenelon |
| **Stage** | Pre-launch / private testing (admins + handful of test users) |
| **Run mode** | Retrofit — spec extracted from existing codebase + interview |

---

## 1. Mission

Give every contractor — solo, crew, or GC managing subs — a tool that **runs their job exactly the way they planned it**, on every device, with or without internet, for less than a lunch.

## 2. The one-line product

**Smith Net is a deterministic job-execution platform for contractors. You compile your plan once; it runs the same way every time, across every device, online or off.**

## 3. Hormozi Value Equation (the offer math)

| Variable | Smith Net delivers |
|---|---|
| **Dream outcome** | "My business runs the way I plan it. I bid, I work, I invoice, I get paid — without re-typing anything, without internet failing me, without an app crashing mid-job-site." |
| **Perceived likelihood of success** | Very high — *deterministic* PLAN Compiler means the plan that runs on Tuesday is the same plan that ran on Monday. No AI hallucination, no surprise behavior. App is already shipping on Android with real backend. |
| **Time delay** | Near-zero — open app, tap job, invoice in <30s. Mesh fallback means zero waiting on cell signal. |
| **Effort & sacrifice** | Low — $2.99/mo to unlock the moat. No setup, no training, no contracts. Free tier exists. |

**Score:** Dream Outcome (high) × Likelihood (very high) ÷ Delay (low) × Sacrifice (low) = **strong offer**.

## 4. Target users (universal contractor — no segment default)

| Segment | Primary need |
|---|---|
| **Solo tradesperson** | Bid → work → invoice → get paid, with phone in pocket on a job site that may have no signal. |
| **Small crew (2-10)** | Coordinate the same things across crew members on-site, with mesh fallback when site has no service. |
| **GC managing subs** | Plan a multi-trade job, deploy the plan to subs, see it execute deterministically without managing each sub's app. |

**Geography:** North America first, global eventually. Currency / tax / lien-handling = English / USD / North-American norms for v1.

## 5. The moat (what protects this from copycats)

1. **Deterministic PLAN Compiler** — plans compile to a runtime artifact that executes the same way every time, no AI required at runtime. Competitors are building AI-runtime workflows that drift.
2. **Cord-based state model** — work-state coordinated across mesh + cloud as a chain of cord transitions, not a CRUD update bag. Hard to replicate without re-architecting from scratch.
3. **Integrated jobs + invoicing** — single app, not a stack of integrations. No Zapier middleware.
4. **Trade-agnostic + contractor-focused** — the 121-trade picker is metadata; the platform doesn't fork per trade. Wide TAM, single product surface.
5. **Price floor below the category** — Solo at $2.99 is a fraction of JobTread/Knowify/Houzz Pro entry tiers (typically $50-$199/mo).

**Non-moat features (don't confuse):** SmithAI on-device assistant (a paid value-add), 121 trade picker (metadata), Bluetooth/Wi-Fi-Direct mesh (resilience layer for the deterministic platform).

## 6. Tier ladder (final — see stack-profile.json for full schema)

| Tier | $/mo | One hero feature unlocked |
|---|---|---|
| Free (Smith Net Open) | $0 | Deterministic baseline taste — basic invoicing + 1 active job + branded PDFs (drives passive distribution) |
| Solo | $2.99 | **PLAN Compiler + cord state model** (the moat) |
| Advanced | $9.99 | **SmithAI on-device + Advanced invoice template** |
| Enterprise | $50 | **Crew / multi-user + Enterprise invoice template** |

**Design principle:** one hero feature per tier. Each upgrade is a single emotional unlock, not a bundle.

## 7. In-scope for v1 launch

- Android client (primary) — jobs, invoicing, comms, mesh, deterministic execution
- Desktop portal (secondary, online-only) — global chat / web access
- Backend API — auth, sync, file storage, webhooks
- Supabase Postgres — primary data store
- Free / Solo / Advanced / Enterprise tier gating
- Standard + Advanced + Enterprise invoice templates
- PDF generation + email delivery + Smith Net branding (Free tier only)

## 8. Out-of-scope for v1 launch (explicit non-goals)

- iOS client (Android first, iOS = post-launch)
- Web app for end-customer of the contractor (separate product)
- Trade-specific feature trees (e.g. plumbing-only inspection forms) — trade is metadata, not a fork
- AI required at runtime for any free or Solo flow — AI lives in Advanced and above
- Vertical-by-vertical pricing (one price ladder for all trades)
- Consumer / homeowner-facing features
- Step 9 Landing Page (deferred per user)
- Built-in tax filing (link out to TurboTax / refer to CPA)

## 9. Assumptions (flagging things that, if wrong, change scope)

| # | Assumption | If wrong → impact |
|---|---|---|
| A1 | Solo contractors do 1-10 jobs/mo (median 2-3) | Free tier "1 active job" cap is wrong; tune to median once instrumented |
| A2 | Branding on PDFs is acceptable to free users | Drop PDF branding, keep email-only branding |
| A3 | $2.99 → $9.99 → $50 ladder is final | Re-do tier hero alignment if any price moves |
| A4 | NA-first is acceptable to early test users | If first paying users are non-NA, accelerate i18n / multi-currency |
| A5 | Android-first market acceptance | If iOS-first contractors push back, reorder roadmap |

## 10. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Free tier cannibalizes Solo (gap is razor-thin) | High | "1 active job" + visible PLAN Compiler preview drives upgrade at exact moment of need |
| PLAN Compiler complexity blocks v1 ship | Medium | Already partly built (migration `003_add_plan_management.sql`); spike remaining work in Step 10 |
| Mesh networking battery drain alienates users | Medium | `MeshService` already has work-mode gating; instrument battery telemetry |
| AI on-device too slow / RAM-hungry on low-end Android | Medium | `RULE_BASED_FALLBACK` path already exists; advertise minimum spec for Advanced |
| Competitors drop price to match | Low | $2.99 floor is uneconomic for category leaders with sales teams; price floor is a moat |
| Apple App Store delay for iOS | Low | iOS is out-of-scope v1 |

## 11. Success criteria for v1 launch (full detail in SUCCESS-METRICS.md)

- 100 active free users within 30 days of public launch
- 10+ paid Solo conversions within 60 days (10% free→Solo at any point in 60 days)
- 1+ paid Advanced conversion within 90 days (proves AI tier demand)
- < 5% crash-free rate violation on Android
- Median time-to-first-invoice < 10 minutes for new free users

## 12. Milestones (high level — full detail in DEV-READINESS.md)

| # | Milestone | Status |
|---|---|---|
| M1 | Android MVP with jobs/invoicing/mesh/comms | ✅ shipped to private testing |
| M2 | Backend API + Supabase data plane | ✅ shipped |
| M3 | Desktop portal (online-only) | 🟡 in progress (uncommitted changes in `desktop/portal/`) |
| M4 | Free / Solo / Advanced / Enterprise tier gating | 🟡 partial (migration 003 added plan_management table; UI gating TBD) |
| M5 | PLAN Compiler — deterministic execution runtime | ❓ unclear from code survey — needs Step 2 deep-dive |
| M6 | Cord-based state model — full coverage across jobs + comms + invoicing | ❓ unclear — needs Step 2 |
| M7 | Public launch (Free + Solo + Advanced + Enterprise live) | 📋 planned, no date |
| M8 | iOS client | 📋 post-launch |

## 13. Linked specs

- [USP.md](USP.md) — unique selling proposition + competitive table
- [FEATURES.md](FEATURES.md) — feature inventory with Hormozi scoring
- [NFRS.md](NFRS.md) — non-functional requirements
- [DEV-READINESS.md](DEV-READINESS.md) — what's built vs. what's left
- [../ops/SUCCESS-METRICS.md](../ops/SUCCESS-METRICS.md) — KPIs and leading indicators
- [../stack-profile.json](../stack-profile.json) — machine-readable stack & tier schema

## 14. Step 1.5 trigger

Confirmed: project is monetized with a tier ladder + tiered invoice templates + plan_management migration. **Step 1.5 (Offer Architecture) WILL run** to refine pricing presentation, free trial mechanics, and grand-slam offer structure.
