# Smith Net — Dev Readiness (retrofit assessment)

**Method:** code survey of `/Users/fegensprenelon/smith-net` (Android, backend, desktop/portal, supabase-migrations) + last 25 commits + `AGENT_INITIALIZATION_README.md` + `THE GOS INSRUCTIOS/` legacy docs.

**Verdict:** Comms layer + mesh transport + AI lifecycle are **shipped to private testing**. Tier gating + invoicing PDF/email + the **PLAN Compiler / cord state moat** are the v1 launch blockers.

---

## 1. What's shipped (✅)

### 1.1 Android client
- Job lifecycle: create / edit / close + persistence + cross-midnight clock + live financials *(commit `3ba1138`)*
- Per-job trade picker matching onboarding *(commit `198cf19`)*
- Searchable trade picker with full 121 entries *(commit `ada0477`)*
- Settings: privacy + location toggles, Signal-style ((●))/((○)) indicators *(commit `e001b41`)*
- Comms: themed conversation UI, channel delete + archive, "Clear messages on this device", de-dup #general *(commits `952f0bd`, `ee08982`, `ba758c1`, `a1e8009`)*
- Channels: ephemeral routing via Supabase broadcast (no cloud persistence), KEEP HISTORY toggle, ChannelPersistence enum + schema column *(commits `58135b1`, `7529059`, `15dad60`, `7c3fee6`)*
- Mesh: BT/Wi-Fi-Direct service, work-mode gating, recovery from `ADVERTISE_FAILED_ALREADY_STARTED` *(commits `8213bc7`, `b3b22b1`)*
- Connection status: declutter + time alignment, OFFLINE→ONLINE auto-promote *(commits `5119abd`, `4ca62f8`, `9c3e1ec`)*
- Dashboard: Quick Actions de-duped vs bottom nav, GETTING STARTED tiles gated on real state *(commits `1039492`, `2b2e02a`)*
- Beta: `BuildFlags.SEED_DEMO_DATA` gating *(commit `8ad3cd1`)*
- Colleagues: scoped add + search with privacy gating *(commit `146a78e`)*
- Lint hygiene: dead SDK checks + unused resources trimmed *(commit `76eca4b`)*

### 1.2 SmithAI (on-device AI — Advanced tier feature)
Per `AGENT_INITIALIZATION_README.md`:
- `LlamaInference.loadModel()` lifecycle integrated
- `AgentInitializer.wakeAgent()` orchestrating SLEEPING → WAKING → ALIVE
- 4-state machine including `RULE_BASED_FALLBACK` for battery / model-fail
- Context gathering (jobs, messages, time entries, prefs)
- Memory building (job patterns, comm patterns, time patterns)
- Proactive setup (ambient observation, suggestion engine, context refresh)
- Tool integration (web search, weather, code exec)
- Solo-vs-crew context awareness — no crew data leaks to solo *(commit `4ce8733`)*
- SmithAI sees expenses + schedule, name unified, solo check-ins *(commit `150de83`)*
- Settings > AI Assistant "Load" button entry point

### 1.3 Backend (`backend/`) — much larger than v1.0 of this doc indicated
- Express 4 + `ws` 8 + raw `pg` driver + JWT + bcryptjs + multer + express-rate-limit
- TypeScript build pipeline (`tsc --skipLibCheck`); ts-node-dev for local dev
- Node ≥ 18 enforced via `engines`
- **39 source files**, structured as labeled "Phase 0 Components" (declared in `server.ts`):
  - **C-01 Authentication & Identity** — `auth.ts`, `authRoutes.ts`, `identityResolver.ts` (JWT + bcrypt + 6 roles + 16 permissions)
  - **C-02 Role Engine** — `auth.ts` (`UserRole`, `Permission`, `ROLE_PERMISSIONS` map, `requirePermission`/`requireRole` middleware)
  - **C-03 Schema & Boundary Engine** — `messageBus.ts`, `channelRegistry.ts`, `presenceManager.ts`, `gatewayManager.ts`, `wsHandler.ts`
  - **C-04 Vendor-Neutral LLM Interface** — `llmInterface.ts` (OpenAI / Anthropic / local / mock providers)
  - **C-05 Data Retention Core** — `auditLog.ts` (25+ audit actions, SHA256 per entry, retention policies declared)
- **Multi-authority validation pattern** — `intentAuthority.ts`, `synthesisAuthority.ts`, `ledgerAuthority.ts` (separates "doing" from "deciding-if-allowed")
- **Deterministic execution pipeline** — `intentService.ts` → `synthesizer.ts` → `ledger.ts` (writes `intents` → `summary_artifacts` → `ledger_entries` with SHA256 sealing)
- **Reconciliation** — `reconciliationEngine.ts` + `vectorClock.ts` (merge / compare / serialize for online↔offline sync)
- **Trade extension pattern** — `electricianTools.ts` (CircuitDiagram, ElectricalChecklist with NEC/OSHA refs, MaterialEstimate, NECCheck) — **template for per-trade packs**
- **Output generation** — `outputGenerator.ts`, `reportAssembler.ts`, `reportRenderer.ts`, `reportOutput.ts`, `invoiceGenerator.ts`, `invoiceLinks.ts` (HTML→PDF via templates)
- **Pricing** — `pricingTiers.ts` (legacy 3-6-9 pyramid: solo/foreman/enterprise/nation × standard/hybrid; **to retire** — replaced by Free/Solo/Advanced/Enterprise ladder per pricing-config.json)
- **Auto-quote** — `autoQuoteEngine.ts`
- **Public-facing pages** — `/p/:uuid` (proposals via `templates/proposal.html`), `/i/:uuid` (invoices via `templates/invoice.html`)
- **Admin** — `adminRoutes.ts` (DATA_EXPORT, DATA_PURGE, role changes — preserves seed admin)
- **Wages** — `wageData.ts`, `payrollDocuments.ts`
- **Storage** — `mediaHandler.ts` (multer, IMAGES_DIR / VOICE_DIR / FILES_DIR + `cleanupOldMedia` background)
- **Reverse-proxy aware** — `app.set('trust proxy', 1)` (Tailscale Funnel ingress)

### 1.4 Databases — TWO of them

**Authoritative: self-hosted Postgres (Hetzner)** — backend uses raw `pg` driver
- `backend/migrations/002_full_schema.sql` defines: `intents`, `intent_versions`, `summary_artifacts`, `ledger_entries`, `proposals` (full + public-facing), `invoice_links`, `jobs`, `time_entries`, `materials`, plus admin-cleanup placeholders
- Includes pgcrypto (`gen_random_uuid()`)

**Legacy / optional: Supabase Postgres** — `BuildConfig.SUPABASE_ENABLED = false` by default
- 10 migrations in `supabase/migrations/`: `000_profiles_only`, `001_initial_schema`, `002_message_bus`, `003_intent_synthesizer_ledger`, `004_artifact_serial_sequence`, `005_proposals`, `006_invoice_links`, `007_wage_data`, `008_profiles_discoverability`, `009_channel_persistence`
- Supabase still used for **desktop portal auth** (`@supabase/auth-ui-react`) and as legacy chat path

**Surface dir (legacy / pre-Intent):** `supabase-migrations/` has `002_add_media_support.sql` and `003_add_plan_management.sql` — the latter defines the OLD `plans` table model (now superseded by `intents`; the `Plan` interface is `@deprecated Use Intent instead` in `types.ts`).

### 1.5 Desktop portal (`desktop/portal/`) — secondary, online-only
- Vite + React 18 + Zustand + react-router-dom + Tailwind (clsx + tailwind-merge)
- Supabase JS + Supabase Auth UI integrated
- "Global chat via Supabase" framing — the desktop portal is the one place Supabase Realtime is the primary path
- **Status: in-progress** — uncommitted edits in `App.tsx`, `tsconfig.json`, `vite.config.ts`, `package.json`, `package-lock.json`; new untracked dir `src/dashboard/`

### 1.6 Android client (`android/`) — primary client, much larger than v1.0 indicated
- Package: `com.guildofsmiths.trademesh`
- 55,683 lines of Kotlin across `ui/`, `service/`, `data/`, `engine/`, `ai/`, `db/`
- **`engine/BoundaryEngine.kt` (1,545 lines)** — dual-path router (mesh vs online) singleton; this is the Android side of the Schema & Boundary Engine (C-03)
- **`data/CordEntry`, `data/CordRepository`, `data/CordMessageClass`, `data/VectorClock`** — the cord-based state model primitives (built; not just designed)
- **AI subsystem** — `AISupervisor.kt` (994), `AIRouter.kt` (788), `AgentInitializer.kt` (612), `AmbientRuleEngine.kt` (489)
- **Plan UI** — `ui/plan/IntentComponents.kt` (the Intent UI surface)
- **Invoice UI** — `ui/invoice/InvoiceTypes.kt` (925 lines), `InvoiceScreen.kt` (580), `InvoiceBolHtmlRenderer.kt` (556)
- **Time tracking** — `TimeTrackingScreen.kt` (779), `TimeTrackingViewModel.kt` (748)
- Hetzner backend wired via `BuildConfig.BACKEND_URL_PRIMARY`; Supabase Realtime path gated on `BuildConfig.SUPABASE_ENABLED` (default false)

---

## 2. Major correction from v1.0 of this doc

**v1.0 said the PLAN Compiler + cord state model were unclear / unbuilt. That was wrong.**

| Marketing term | Internal code | Status |
|---|---|---|
| **"PLAN Compiler"** | `intentService` → `synthesizer` → `ledger` pipeline (with `intentAuthority` / `synthesisAuthority` / `ledgerAuthority` validators); produces SHA256-sealed `summary_artifacts` → `ledger_entries` | ✅ **substantially built** — backend pipeline + DB schema + validators all in place. Public API endpoints + Android UI surfacing TBD. |
| **"cord-based state model"** | `VectorClock` (backend `vectorClock.ts` + Android `data/VectorClock`) + `CordEntry` / `CordRepository` / `CordMessageClass` (Android `data/`) + `ReconciliationEngine` (both sides) | ✅ **substantially built** — vector-clock merge + compare + serialize + reconciliation all working. Used by message bus today; needs to be wired into Intent/Artifact lifecycle in Step 11. |

**What this means for Step 11 PRDs:**
- The moat is mostly built — Step 11 work is **wiring + UI surfacing + tier-gating**, not net-new pipeline construction
- That dramatically shortens the path to a paid Solo launch
- Real net-new build is: Stripe/Play Billing + tier resolver + entitlements service + locked-feature CTAs + telemetry hooks + invoice PDF templates (Standard already partial via `invoiceLinks.ts`; Advanced + Enterprise are net-new)

---

## 3. In progress (🟡)

| Area | Evidence | Notes |
|---|---|---|
| Desktop portal dashboard | uncommitted `src/dashboard/` + modified `App.tsx` | Target for online-only secondary client |
| Standard invoice PDF + email send | `invoiceLinks.ts` + `templates/invoice.html` + `mediaHandler` exist; backend can render | UI to wire into Free-tier flow with branding stamp + send cap |
| Tier resolver | legacy `pricingTiers.ts` exists (3-6-9 pyramid — to retire); new ladder schema TBD | Step 11: build `subscriptions` table + `/api/me/entitlements` endpoint |

---

## 4. Not built (📋 — v1 launch blockers — REVISED)

The original "PLAN Compiler / cord state" entries are removed (already built). Real blockers:

| # | Blocker | Owner step |
|---|---|---|
| B1 | Replace `X-User-Id` "simplified auth" header with `authenticateToken` middleware on Intent/Synthesis/Ledger/Channel endpoints | Step 11 (security G1/S1) |
| B2 | Subscription billing — Stripe (web/desktop) + Play Billing (Android) + webhooks | Step 11 |
| B3 | `/api/me/entitlements` + tier resolver + JWT tier claims | Step 11 |
| B4 | `subscriptions` + `founder_seats` + `gate_hit_events` tables (per SCHEMA §11) | Step 11 |
| B5 | Active-job cap + PDF-send cap enforcement (server-side refusal with `tier_gate_exceeded` 403) | Step 11 |
| B6 | Locked-feature CTA UI (PLAN preview pane, AI tab gate, crew invite gate) | Step 11 |
| B7 | Smith Net branding stamp on Free-tier PDFs + email signatures | Step 11 |
| B8 | 14-day Solo trial without CC (start-trial endpoint + UI) | Step 11 |
| B9 | Advanced + Enterprise invoice template HTML/PDF | Step 11 |
| B10 | Multi-user crew accounts + shared jobs across crew | Step 11 (Enterprise tier) |
| B11 | AI tab gating with upgrade CTA (Solo → Advanced trigger) | Step 11 |
| B12 | Tier-gate telemetry events (`gate_hit.*`) | Step 11 |
| B13 | Tighten CORS + lock JWT secret + fail-closed on dev secret in prod | Step 11 (security S2/S4) |
| B14 | Verify mesh payload encryption (audit `MeshService.kt`) | Step 11 (security S3/S12) |
| B15 | Add `zod` validation at every endpoint boundary | Step 11 (security S7) |
| B16 | Public-page rate limiting per-UUID + view-tracking | Step 11 |
| B17 | Stripe webhook signature verification + Play Billing token verification | Step 11 (when billing ships, S9/S10) |
| B18 | Migrate audit log from file-based to DB-backed; enforce retention | Step 11 (G9/S8) |
| B19 | Migration: `add_tier_columns_to_profiles` + data migration `plans → intents` | Step 11 (per SCHEMA §14) |

**See SECURITY.md §17 for the full 14-item security gap list and ARCHITECTURE.md §14 for the 10-item architecture gap list.**

## 5. Tech debt / known issues (REVISED)

| Item | Source | Severity |
|---|---|---|
| `pricingTiers.ts` 3-6-9 pyramid conflicts with new $0/$2.99/$9.99/$50 ladder | `backend/src/pricingTiers.ts` | **High** — retire in Step 11 |
| Dual `Plan` model (legacy `Plan` interface marked `@deprecated`, new `Intent` model live) | `backend/src/types.ts` | Medium — consolidate in Step 11 |
| Two migration trees (`backend/migrations/` Hetzner + `supabase/migrations/` legacy) | repo layout | Low — Hetzner canonical, document Supabase as legacy |
| `electricianTools` is the only trade pack; UI surfacing TBD | `backend/src/electricianTools.ts` | Medium — pattern set, more packs in Step 11+ |
| `X-User-Id` "simplified auth" header still used on some routes | `backend/src/api.ts` | **Critical** — replace before public launch (B1/S1) |
| CORS `origin: '*'` | `backend/src/server.ts` | **High** — tighten (B13/S2) |
| Hardcoded `JWT_SECRET` fallback in code | `backend/src/auth.ts` | **High** — fail-closed in prod (B13/S4) |
| Dirty working tree (5 modified files in `desktop/portal/`) | `git status` | Low — in-progress feature |
| No top-level `package.json` / no monorepo manager | repo layout | Medium — manual coordination |
| Bun not installed | Phase A | Low — optional |
| Greptile MCP failed to connect | MCP discovery | Low — unrelated to Sigma |
| No automated tests visible at top-level | survey | Medium — Step 11 will require TDD on net-new work |
| Backend has no ORM (raw `pg`) | `backend/package.json` | Medium — but discipline is good (parameterized queries throughout); maintain or migrate to a thin query builder later |
| Audit log file-based, retention not enforced | `backend/src/auditLog.ts` | Medium — move to DB (B18/S8) |

## 6. Stage-readiness checklist for v1 paid launch (REVISED)

| # | Gate | Status |
|---|---|---|
| 1 | Free tier delivers without crashing | 🟡 ~85% there (Android shipped to private testing) |
| 2 | Solo tier unlocks PLAN Compiler (Intent → Artifact → Ledger) + cord state (VectorClock + Cord*) | ✅ **moat is built**; UI surfacing + tier gate is the work |
| 3 | Advanced tier unlocks SmithAI + Advanced invoice template | 🟡 SmithAI built, Advanced template HTML net-new |
| 4 | Enterprise tier unlocks crew + Enterprise template | 📋 not yet built |
| 5 | Subscription billing works on Play Store + web | 📋 not yet built |
| 6 | Tier resolver server-authoritative | 📋 not yet built |
| 7 | Tier-gate telemetry instrumented | 📋 not yet built |
| 8 | Standard invoice PDF + email works end-to-end | 🟡 server-side render exists, send + cap not wired |
| 9 | Smith Net branding on Free PDFs/email | 📋 |
| 10 | Locked-feature CTAs in place | 📋 |
| 11 | All Intent/Synthesis/Ledger endpoints use JWT (no `X-User-Id` header) | ❌ **critical security gap** |
| 12 | CORS locked; JWT secret managed | ❌ **critical security gap** |
| 13 | Mesh payload encryption verified | ❓ unverified |

**Revised honest read:** v1 paid launch is **closer than v1.0 of this doc claimed** because the moat (Intent → Artifact → Ledger) is substantially built. The critical path is: (a) fix the `X-User-Id` security gap, (b) build subscription billing + tier resolver, (c) wire UI for tier gating + branding + CTAs, (d) build the missing invoice templates. Step 10 shapes; Step 11 produces buildable PRDs against `B1-B19`.
