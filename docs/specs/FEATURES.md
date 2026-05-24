# Smith Net — Feature Inventory

Each feature is scored against the Hormozi Value Equation:
- **DO** = Dream Outcome contribution (1-5)
- **PL** = Perceived Likelihood added (1-5)
- **TD** = Time Delay reduced (1-5)
- **EF** = Effort/Sacrifice reduced (1-5)
- **Score** = (DO × PL) ÷ (TD-inverted × EF-inverted), normalized 1-10
- **Tier** = first paid tier that unlocks it (Free / Solo / Adv / Ent)
- **Status** = ✅ shipped / 🟡 in progress / ❓ unclear / 📋 planned

---

## Domain 1: Plans & Execution (the moat)

| # | Feature | DO | PL | TD | EF | Score | Tier | Status |
|---|---|---|---|---|---|---|---|---|
| 1.1 | **PLAN Compiler** — compile a job plan to deterministic execution artifact | 5 | 5 | 5 | 5 | 10 | Solo | ❓ |
| 1.2 | **Cord-based state model** — work-state as chain of cord transitions | 5 | 5 | 4 | 4 | 9 | Solo | ❓ |
| 1.3 | Plan editor (visual / form-based authoring of a plan before compile) | 4 | 4 | 4 | 4 | 8 | Solo | 📋 |
| 1.4 | Plan templates (start from a template, edit, compile) | 4 | 4 | 4 | 4 | 8 | Solo | 📋 |
| 1.5 | Plan preview pane (Free tier) — read-only, greyed-out, with upgrade CTA | 3 | 3 | 5 | 5 | 7 | Free | 📋 |
| 1.6 | AI plan-authoring assist (Advanced) — SmithAI helps draft a plan from a job brief | 4 | 4 | 4 | 5 | 8 | Adv | 📋 |
| 1.7 | Cord history / audit log per job | 3 | 4 | 3 | 3 | 6 | Solo | 📋 |

## Domain 2: Jobs & Clients

| # | Feature | DO | PL | TD | EF | Score | Tier | Status |
|---|---|---|---|---|---|---|---|---|
| 2.1 | Create / edit / close a job | 5 | 5 | 5 | 5 | 10 | Free | ✅ |
| 2.2 | Per-job trade picker (121 trades) | 3 | 3 | 4 | 4 | 6 | Free | ✅ |
| 2.3 | Searchable trade picker (full 121-entry list) | 3 | 3 | 4 | 4 | 6 | Free | ✅ |
| 2.4 | Client / contact records | 4 | 4 | 4 | 4 | 8 | Free | ✅ |
| 2.5 | Job persistence + cross-midnight clock | 4 | 5 | 4 | 4 | 8 | Free | ✅ |
| 2.6 | Live financials per job (running total) | 4 | 4 | 4 | 4 | 8 | Free | ✅ |
| 2.7 | Active-job cap enforcement (Free = 1, paid = unlimited) | 3 | 4 | 5 | 5 | 8 | Free | 📋 |

## Domain 3: Invoicing & Payment

| # | Feature | DO | PL | TD | EF | Score | Tier | Status |
|---|---|---|---|---|---|---|---|---|
| 3.1 | Standard invoice template + PDF generation | 5 | 5 | 5 | 5 | 10 | Free | 🟡 |
| 3.2 | Advanced invoice template (richer line items, branding, terms) | 4 | 4 | 4 | 4 | 8 | Adv | 🟡 |
| 3.3 | Enterprise invoice template (multi-payer, milestone billing) | 4 | 4 | 3 | 3 | 7 | Ent | 🟡 |
| 3.4 | Invoice email send (with Smith Net branding on Free) | 5 | 5 | 5 | 5 | 10 | Free | 📋 |
| 3.5 | PDF send cap enforcement (Free = 5/mo) | 3 | 4 | 5 | 5 | 8 | Free | 📋 |
| 3.6 | Payment status tracking (sent / viewed / paid) | 4 | 4 | 3 | 3 | 7 | Solo | 📋 |
| 3.7 | Stripe / Square integration for payment collection | 4 | 4 | 3 | 3 | 7 | Solo | 📋 |
| 3.8 | Expense capture per job (already shipped — visible to SmithAI) | 4 | 4 | 4 | 4 | 8 | Free | ✅ |

## Domain 4: Comms & Channels

| # | Feature | DO | PL | TD | EF | Score | Tier | Status |
|---|---|---|---|---|---|---|---|---|
| 4.1 | Per-job channels | 4 | 4 | 4 | 4 | 8 | Free | ✅ |
| 4.2 | Ephemeral channels (no cloud persistence, broadcast-only routing) | 3 | 4 | 4 | 4 | 7 | Free | ✅ |
| 4.3 | KEEP HISTORY toggle per channel | 3 | 4 | 4 | 4 | 7 | Free | ✅ |
| 4.4 | Channel delete + archive (cascade local + remote) | 3 | 4 | 4 | 4 | 7 | Free | ✅ |
| 4.5 | "Clear messages on this device" action | 3 | 4 | 4 | 4 | 7 | Free | ✅ |
| 4.6 | Single-#general collapse on Supabase sync (de-dup) | 3 | 4 | 3 | 3 | 6 | Free | ✅ |
| 4.7 | Themed conversation UI (Console colors) | 2 | 3 | 3 | 3 | 5 | Free | ✅ |
| 4.8 | Signal-style toggle dots ((●))/((○)) | 2 | 3 | 3 | 3 | 5 | Free | ✅ |

## Domain 5: Mesh / Connectivity

| # | Feature | DO | PL | TD | EF | Score | Tier | Status |
|---|---|---|---|---|---|---|---|---|
| 5.1 | Bluetooth + Wi-Fi-Direct mesh transport (MeshService) | 5 | 5 | 5 | 5 | 10 | Free | ✅ |
| 5.2 | Work-mode gating on mesh service (battery saver) | 4 | 4 | 4 | 4 | 8 | Free | ✅ |
| 5.3 | Mesh recovery from `ADVERTISE_FAILED_ALREADY_STARTED` | 3 | 5 | 4 | 4 | 7 | Free | ✅ |
| 5.4 | Mesh bridge to Supabase (online/offline crossover) | 5 | 5 | 4 | 4 | 9 | Free | ✅ |
| 5.5 | Connection status declutter (concise indicator) | 2 | 3 | 3 | 3 | 5 | Free | ✅ |
| 5.6 | OFFLINE → ONLINE auto-promotion when Supabase realtime up | 3 | 4 | 4 | 4 | 7 | Free | ✅ |

## Domain 6: AI (SmithAI on-device, paid feature — Advanced floor)

| # | Feature | DO | PL | TD | EF | Score | Tier | Status |
|---|---|---|---|---|---|---|---|---|
| 6.1 | LlamaInference model loading + lifecycle (SLEEPING → WAKING → ALIVE) | 5 | 4 | 3 | 3 | 8 | Adv | ✅ |
| 6.2 | RULE_BASED_FALLBACK path (battery-saver / model-fail) | 4 | 5 | 4 | 4 | 8 | Free* | ✅ |
| 6.3 | Context gathering (jobs, messages, time entries, prefs) | 4 | 4 | 3 | 3 | 7 | Adv | ✅ |
| 6.4 | Memory building (job patterns, comm patterns, time patterns) | 4 | 4 | 3 | 3 | 7 | Adv | ✅ |
| 6.5 | Proactive suggestion engine | 4 | 3 | 4 | 4 | 7 | Adv | ✅ |
| 6.6 | Tool integration (web search, weather, code exec) | 3 | 3 | 3 | 3 | 6 | Adv | ✅ |
| 6.7 | Solo-vs-crew context awareness (no crew data leaks to solo) | 4 | 5 | 4 | 4 | 8 | Adv | ✅ |
| 6.8 | "Load model" button in Settings > AI Assistant | 3 | 4 | 4 | 4 | 7 | Adv | ✅ |
| 6.9 | AI tab gated for Free + Solo with upgrade CTA | 3 | 3 | 5 | 5 | 7 | Adv | 📋 |

*RULE_BASED_FALLBACK is the deterministic rule engine — it's the "AI-free" baseline available to all tiers. SmithAI itself (Llama-based) is Advanced+.

## Domain 7: Crew & Permissions

| # | Feature | DO | PL | TD | EF | Score | Tier | Status |
|---|---|---|---|---|---|---|---|---|
| 7.1 | Single-user mode (Free / Solo / Advanced) | 5 | 5 | 5 | 5 | 10 | Free | ✅ |
| 7.2 | Multi-user crew accounts | 5 | 4 | 3 | 3 | 8 | Ent | 📋 |
| 7.3 | Colleague invites + scoped add | 4 | 4 | 3 | 3 | 7 | Ent | ✅ |
| 7.4 | Privacy gating (search visibility, location toggle) | 4 | 5 | 4 | 4 | 8 | Free | ✅ |
| 7.5 | Shared jobs across crew | 5 | 4 | 3 | 3 | 8 | Ent | 📋 |
| 7.6 | Solo-mode hides crew UI (no Quick Actions duplication) | 3 | 4 | 4 | 4 | 7 | Free | ✅ |

## Domain 8: Onboarding & Settings

| # | Feature | DO | PL | TD | EF | Score | Tier | Status |
|---|---|---|---|---|---|---|---|---|
| 8.1 | Onboarding trade picker matching new-job picker | 3 | 4 | 4 | 4 | 7 | Free | ✅ |
| 8.2 | Profile with 121-entry trade list | 3 | 4 | 4 | 4 | 7 | Free | ✅ |
| 8.3 | Settings > Privacy + Location toggles (Signal-style) | 3 | 4 | 4 | 4 | 7 | Free | ✅ |
| 8.4 | Dashboard "GETTING STARTED" tiles gated on real state | 3 | 4 | 5 | 5 | 8 | Free | ✅ |
| 8.5 | Beta seed-data behind `BuildFlags.SEED_DEMO_DATA` | 3 | 5 | 4 | 4 | 7 | Free | ✅ |

## Domain 9: Tier gating + monetization plumbing

| # | Feature | DO | PL | TD | EF | Score | Tier | Status |
|---|---|---|---|---|---|---|---|---|
| 9.1 | `plan_management` schema (migration 003) | 4 | 5 | 4 | 4 | 9 | — | ✅ |
| 9.2 | Tier resolver (server-side authority on user's tier) | 4 | 5 | 4 | 4 | 9 | — | 📋 |
| 9.3 | In-app purchase / subscription billing (Play Store + web) | 5 | 5 | 4 | 4 | 9 | — | 📋 |
| 9.4 | 14-day Solo trial without CC | 4 | 4 | 5 | 5 | 9 | Free | 📋 |
| 9.5 | Locked-feature CTA system (PLAN Compiler preview, AI tab, crew invite) | 4 | 4 | 4 | 4 | 8 | Free | 📋 |
| 9.6 | Smith Net branding stamp on Free PDFs and emails | 3 | 4 | 5 | 5 | 8 | Free | 📋 |

## Domain 10: Desktop portal (online-only secondary client)

| # | Feature | DO | PL | TD | EF | Score | Tier | Status |
|---|---|---|---|---|---|---|---|---|
| 10.1 | Web auth via Supabase Auth UI | 3 | 4 | 4 | 4 | 7 | Free | 🟡 |
| 10.2 | Global chat sync via Supabase realtime | 3 | 4 | 3 | 3 | 6 | Free | 🟡 |
| 10.3 | Dashboard view of jobs / invoicing | 4 | 4 | 4 | 4 | 8 | Free | 🟡 |
| 10.4 | (No mesh — desktop is online-only) | — | — | — | — | — | — | — |

---

## Top 10 features by Hormozi score

| Rank | Feature | Score | Status | Why it matters |
|---|---|---|---|---|
| 1 | PLAN Compiler | 10 | ❓ | The moat. v1 launch blocker. |
| 1 | Job CRUD | 10 | ✅ | Foundation. Already done. |
| 1 | Standard invoice + PDF | 10 | 🟡 | Free tier hero. v1 launch blocker. |
| 1 | Mesh transport | 10 | ✅ | Connectivity moat. Already done. |
| 1 | Single-user mode | 10 | ✅ | Universal. Done. |
| 1 | Invoice email send | 10 | 📋 | Free tier hero. v1 launch blocker. |
| 7 | Cord state model | 9 | ❓ | The moat (companion to PLAN Compiler). |
| 7 | Mesh bridge to Supabase | 9 | ✅ | Connectivity glue. Done. |
| 7 | plan_management schema | 9 | ✅ | Tier plumbing. Done. |
| 7 | Tier resolver | 9 | 📋 | Tier plumbing. v1 launch blocker. |
| 7 | Subscription billing | 9 | 📋 | Tier plumbing. v1 launch blocker. |
| 7 | 14-day Solo trial | 9 | 📋 | Conversion driver. v1 launch blocker. |

## v1 launch blockers (📋 high-score features not yet built)

1. PLAN Compiler (1.1) + Cord state model (1.2) — clarify status in Step 2
2. Standard invoice PDF + email send (3.1, 3.4)
3. Tier resolver + subscription billing (9.2, 9.3)
4. 14-day Solo trial without CC (9.4)
5. Locked-feature CTA system (9.5)
6. Smith Net branding stamp (9.6)
7. Active-job cap + PDF send cap enforcement (2.7, 3.5)
8. AI tab gating with upgrade CTA (6.9)

These flow into Step 10 (Feature Breakdown) and Step 11 (PRD Generation).
