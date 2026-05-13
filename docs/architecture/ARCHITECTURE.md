# Smith Net — Architecture

**Status:** retrofit (extracted from existing code as of 2026-04-30)
**Sigma step:** 2 — System Design
**Persona:** Principal Fellow

> **Update 2026-05-13.** The operational sections of this doc (workers, daemons, audit chain, LLM placement, in-memory stores) are superseded by the architecture audit at `../smith-net-architecture-audit.md` and its five companion docs (daemon-worker-queue, token-optimization, automation-map, agent-boundaries, implementation-roadmap). The audit identifies ten weak points in the current backend and defines the 7-phase build order to close them. Project-root `CLAUDE.md` locks in the two non-negotiable rules ("no inline LLM in routes; no inline fire-and-forget") that come out of the audit. The diagrams and route descriptions below remain accurate as a snapshot of the 2026-04-30 retrofit; trust the audit doc for the forward direction.

---

## 1. One-page picture

```
                    ┌──────────────────────────────────────────────────────┐
                    │                  SMITH NET CLIENTS                    │
                    ├───────────────────────────┬───────────────────────────┤
                    │  Android (primary client) │  Desktop Portal (online)  │
                    │  com.guildofsmiths.       │  Vite + React + Zustand   │
                    │  trademesh                │                           │
                    │                           │                           │
                    │  ┌─────────────────────┐  │  ┌─────────────────────┐  │
                    │  │ BoundaryEngine      │  │  │ Supabase Auth UI    │  │
                    │  │ (dual-path router)  │  │  │ Realtime subs       │  │
                    │  └──┬──────────┬───────┘  │  └─────────────────────┘  │
                    │     │          │          │                           │
                    │  ┌──▼──┐    ┌──▼──┐       │                           │
                    │  │MESH │    │CHAT │       │                           │
                    │  │BLE+ │    │ WS  │       │                           │
                    │  │WiFi │    │     │       │                           │
                    │  └──┬──┘    └──┬──┘       │                           │
                    │  ┌──▼──┐    ┌──▼──┐       │                           │
                    │  │Cord │    │Vector│      │                           │
                    │  │State│    │Clock │      │                           │
                    │  └─────┘    └─────┘       │                           │
                    │  ┌─────────────────────┐  │                           │
                    │  │ SmithAI (Llama on-  │  │                           │
                    │  │ device) — Adv tier  │  │                           │
                    │  └─────────────────────┘  │                           │
                    └───────────┬───────────────┴────────────┬──────────────┘
                                │                             │
                                │ WebSocket + REST            │ WebSocket + REST
                                │ (BACKEND_URL_PRIMARY)       │
                                │                             │
                    ┌───────────▼─────────────────────────────▼──────────────┐
                    │            HETZNER PRIMARY BACKEND (Express)           │
                    │  Phase-0 components:                                   │
                    │  C-01 Auth & Identity   C-02 Role Engine              │
                    │  C-03 Schema/Boundary   C-04 Vendor-neutral LLM       │
                    │  C-05 Data Retention (audit log)                      │
                    │                                                        │
                    │  Multi-authority validators:                          │
                    │  intentAuthority → synthesisAuthority → ledgerAuth.   │
                    │                                                        │
                    │  Reconciliation: vectorClock + reconciliationEngine   │
                    │  Realtime: ws (WebSocket) + presenceManager           │
                    │  Trade extensions: electricianTools (pattern)         │
                    └───────────────────────┬────────────────────────────────┘
                                            │
                                            │ pg (raw, no ORM)
                                            │
                    ┌───────────────────────▼────────────────────────────────┐
                    │       SELF-HOSTED POSTGRES (Hetzner) — primary         │
                    │  intents / intent_versions                             │
                    │  summary_artifacts (sealed via SHA256)                 │
                    │  ledger_entries (immutable, supersession chain)        │
                    │  channels / messages / message_bus_messages            │
                    │  proposals / invoice_links / wage_data                 │
                    │  jobs / time_entries / materials / profiles            │
                    └────────────────────────────────────────────────────────┘

                    ┌────────────────────────────────────────────────────────┐
                    │   SUPABASE (legacy / optional, OFF by default)         │
                    │   BuildConfig.SUPABASE_ENABLED = false                 │
                    │   - global chat realtime (alt path)                    │
                    │   - desktop portal auth (still used)                   │
                    │   - supabase/migrations/* schema                       │
                    └────────────────────────────────────────────────────────┘
```

## 2. Vocabulary (internal code → external marketing)

| Internal code term | External / marketing |
|---|---|
| `Intent` + `IntentVersion` (statuses: draft, proposed, confirmed, superseded) | a "PLAN" |
| `SummaryArtifact` (synthesizer output) | a "compiled PLAN" |
| `LedgerEntry` (SHA256-sealed, supersession chain) | the "PLAN Compiler" output |
| `intentAuthority` + `synthesisAuthority` + `ledgerAuthority` | the "PLAN Compiler" engine |
| `VectorClock` + `CordEntry` + `CordRepository` | "cord-based state model" |
| `BoundaryEngine` (Android) + `messageBus` (backend) | the "Smith Mesh" routing |
| `electricianTools.ts` | a "trade pack" (one of N) |
| `LlamaInference` + `AISupervisor` + `AmbientRuleEngine` | "SmithAI" |
| `RULE_BASED_FALLBACK` state | "deterministic baseline" (Free tier hero) |

**Rule for docs:** internal architecture/schema/API docs use code terms; user-facing docs (USP, OFFER_ARCHITECTURE, marketing copy, app UI) use external terms.

## 3. Phase-0 Components (declared in `backend/src/server.ts`)

| Component | Code module | Responsibility |
|---|---|---|
| **C-01** Authentication & Identity | `auth.ts`, `authRoutes.ts`, `identityResolver.ts` | JWT (7d access, 30d refresh), bcrypt (10 rounds), email/password |
| **C-02** Role Engine | `auth.ts` (`UserRole`, `Permission`, `ROLE_PERMISSIONS` map) | 6 roles × 16 permissions; role→permission lookup; middleware `requirePermission`, `requireRole` |
| **C-03** Schema & Boundary Engine | `BoundaryEngine.kt` (Android), `messageBus.ts`, `channelRegistry.ts` (backend) | Multi-transport routing (mesh / chat / gateway); access-control on channels (public/private/restricted); cord/vector-clock sync |
| **C-04** Vendor-Neutral LLM Interface | `llmInterface.ts` | Abstraction over OpenAI / Anthropic / local Llama / mock; `LLMProvider` enum, `ILLMProvider` interface |
| **C-05** Data Retention Core | `auditLog.ts`, `archiveService.ts` | Append-only audit log with per-entry SHA256 checksum; retention policies; data export/purge |

## 4. The deterministic execution pipeline (the moat)

```
┌──────────────────────────────────────────────────────────────────┐
│  USER ACTION:  "Start a job for Mrs. Lee — kitchen rewire"       │
└────────────────────────────┬─────────────────────────────────────┘
                             ▼
                   ┌─────────────────────┐
                   │   Engagement (DB)   │  loose intent capture
                   │   intent: "..."     │  status: active
                   └──────────┬──────────┘
                              ▼
                   ┌─────────────────────┐
                   │   Intent  (DB)      │  one row, one tracker
                   └──────────┬──────────┘
                              ▼
                   ┌─────────────────────┐
                   │ IntentVersion (DB)  │  v1 status: draft
                   │  scope_statement    │
                   │  intended_job_ids[] │
                   │  parties[]          │
                   └──────────┬──────────┘
                              │  validateIntentCreation()  ← intentAuthority.ts
                              ▼  (scope must be non-empty + parties ≥ 1)
                   ┌─────────────────────┐
                   │   v1 status: proposed│
                   └──────────┬──────────┘
                              │  validateIntentConfirmation()
                              ▼  (party must explicitly confirm)
                   ┌─────────────────────┐
                   │   v1 status: confirmed│
                   │   confirmed_by      │
                   │   confirmed_at      │
                   └──────────┬──────────┘
                              │
   (work happens; jobs close; time_entries close; messages logged)
                              │
                              ▼
                   ┌─────────────────────┐
                   │ Synthesizer (be)    │  validateSynthesisInputs()
                   │  inputs:            │  ← synthesisAuthority.ts
                   │    confirmed Intent │   (confirmed Intent +
                   │    closed Job IDs   │    ≥1 closed Job +
                   │    closed TimeIDs   │    ≥1 closed TimeEntry)
                   │    chat msg IDs     │
                   └──────────┬──────────┘
                              ▼
                   ┌─────────────────────┐
                   │ SummaryArtifact (DB)│
                   │  serial (UNIQUE)    │  ← artifact_serial_sequence
                   │  scope_statement    │   (mig 004)
                   │  work_performed[]   │
                   │  labor_recorded[]   │
                   │  materials_used[]   │
                   │  contextual_notes[] │
                   │  total_hours        │
                   │  total_cost         │
                   │  job_ids[]          │
                   │  time_entry_ids[]   │
                   │  chat_message_ids[] │
                   └──────────┬──────────┘
                              ▼
                   ┌─────────────────────┐
                   │ LedgerEntry (DB)    │  ← ledger.seal()
                   │  artifact_serial    │   computeHash() → SHA256
                   │  artifact_id        │   ledgerAuthority.validateSealing()
                   │  sha256_hash        │
                   │  actor_uuid         │
                   │  blockchain_ref     │  (optional anchor — future)
                   │  sealed_at          │
                   │  supersedes (FK)    │  ← amend() chains supersession
                   │  superseded_by (FK) │
                   └──────────┬──────────┘
                              ▼
                   ┌─────────────────────┐
                   │ Outputs (PDF/email) │
                   │   - Report (HTML→PDF)│
                   │   - Invoice (HTML→PDF)│
                   │   - Public proposal page (/p/:uuid)│
                   │   - Public invoice page (/i/:uuid) │
                   └─────────────────────┘
```

**Why this is "deterministic":**
- Every input to the synthesizer is an immutable ID (Intent version, Job ID, TimeEntry ID, Message ID).
- Synthesis produces a Summary Artifact with a deterministic SHA256 hash of its content.
- The Ledger Entry stores that hash. Re-running the synthesis with the same inputs MUST produce the same hash.
- Supersession chain (`supersedes` / `superseded_by`) creates an auditable history of every change.
- AI is **never** a step in this pipeline — synthesis is a pure function over IDs.

## 5. Multi-authority validation pattern

The system separates **doing** from **deciding-if-allowed-to-do**. Each authority is a pure validator:

| Authority | File | Validates |
|---|---|---|
| `intentAuthority` | `intentAuthority.ts` | Intent creation (scope non-empty, parties ≥ 1), confirmation (status = proposed, confirmer is a party), versioning (supersession is allowed) |
| `synthesisAuthority` | `synthesisAuthority.ts` | Synthesis preconditions (Intent confirmed, ≥ 1 closed Job, ≥ 1 closed Time Entry) and artifact post-conditions (has serial, scope, intent ref, jobs, time entries) |
| `ledgerAuthority` | `ledgerAuthority.ts` | Sealing (artifact valid + not already sealed), amendment (prior entry not superseded), hash computation |

**Pattern:** every state-change endpoint calls `validate*` first, then mutates only if `valid: true`. This makes every transition auditable from a single function.

## 6. Multi-transport message routing (Smith Mesh)

```
                    ┌──────────────────────────────┐
                    │  BoundaryEngine.kt (Android) │
                    └──────────────┬───────────────┘
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        ▼                          ▼                          ▼
  ┌──────────┐              ┌──────────┐              ┌──────────┐
  │   MESH   │              │  ONLINE  │              │ GATEWAY  │
  │  BLE +   │              │   WS to  │              │  Relays  │
  │WiFi-Direc│              │  Hetzner │              │  via app │
  └────┬─────┘              └────┬─────┘              └────┬─────┘
       │                         │                         │
       └────────────┬────────────┴────────────┬────────────┘
                    ▼                         ▼
              ┌──────────────────────────────────┐
              │   ReconciliationEngine          │
              │   ┌──────────────────────┐      │
              │   │  VectorClock merge   │      │
              │   │  duplicate detection │      │
              │   │  ordering            │      │
              │   └──────────────────────┘      │
              └──────────────────────────────────┘
                              ▼
              ┌──────────────────────────────────┐
              │  MessageBusRepository (local DB) │
              │  message_bus_messages (server)   │
              └──────────────────────────────────┘
```

**Origin enum** (`MessageOrigin`): `online` | `mesh` | `gateway` | `online+mesh`
**Transport enum** (`TransportType`): `MESH` | `ONLINE` | `GATEWAY` | `SUPABASE`

**Reconciliation flow** (`reconciliationEngine.ts`):
1. Client sends: `{channelId, localMessageIds[], localClock}`
2. Server returns: `{missingOnClient[], missingOnServer[], mergedClock}`
3. Client uploads `missingOnServer`; server saves with `ON CONFLICT (id) DO UPDATE`
4. Client merges `missingOnClient` into local store with vector-clock ordering
5. Both sides advance to `mergedClock`

**Conflict resolution:** vector clocks tell us if events are concurrent (`compare()` returns 0); concurrent events are kept ordered by `(timestamp, id)` as a deterministic tiebreaker. No last-write-wins.

## 7. Tier enforcement architecture

The pricing-config.json ladder ($0/$2.99/$9.99/$50) is the **canonical** ladder going forward. The existing in-code `pricingTiers.ts` (solo/foreman/enterprise/nation × standard/hybrid 3-6-9 pyramid) is **internal pricing math only** — must be retired or hidden in Step 11.

```
┌─────────────────────────────────────────────────────────────┐
│  Client (Android / Desktop)                                  │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  ENTITLEMENT GATE (UI)                              │    │
│  │  reads tier from JWT claim or /api/me/tier          │    │
│  │  gates: active_jobs, pdf_sends, AI tab, crew invite │    │
│  └──────────────────────────────────────────────────────┘    │
└────────────────────────────┬─────────────────────────────────┘
                             │ JWT carries tier claim
                             ▼
┌─────────────────────────────────────────────────────────────┐
│  Backend                                                     │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  TIER RESOLVER (server-authoritative)                │    │
│  │  Pulls subscription from Stripe (web) or             │    │
│  │  Play Billing (Android), reconciles with profile.    │    │
│  │  Mutates JWT on tier change.                         │    │
│  │  Enforces hard limits (active_jobs, pdf_sends)       │    │
│  │  in service-layer middleware (refuse beyond cap).    │    │
│  └──────────────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  TELEMETRY HOOKS (per gate)                          │    │
│  │  emits gate_hit.* events (per pricing-config.json    │    │
│  │  tier_gates_to_telemetry_event_map)                  │    │
│  └──────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

**Server-authoritative principle:** clients can hide locked features in UI for UX, but every limit (active job count, PDF send count, AI invocation, crew member add) MUST also be checked server-side. Refuse beyond cap with a `403 tier_gate_exceeded` error and structured `gate_id`.

## 8. AI architecture (SmithAI — Advanced tier feature)

**On-device only by design.** The point is: data stays on the contractor's phone.

| Layer | Module | Purpose |
|---|---|---|
| Inference | `LlamaInference` (cpp via JNI) | Llama.cpp wrapper; loads quantized GGUF model from local storage |
| Lifecycle | `AgentInitializer.kt` | SLEEPING → WAKING (model load 0%→100%) → ALIVE; or → RULE_BASED_FALLBACK on failure / battery |
| Routing | `AIRouter.kt` | Decides per-query: route to Llama, to rule engine, or refuse |
| Supervisor | `AISupervisor.kt` | Long-lived agent loop; tool-calling; ambient observation |
| Rules (free baseline) | `AmbientRuleEngine.kt` | Deterministic rule engine; available to ALL tiers as fallback / Free-tier baseline |
| Backend abstraction | `llmInterface.ts` (C-04) | Vendor-neutral interface for any LATER server-side AI usage; supports Anthropic / OpenAI / local / mock |

**Key invariant:** SmithAI **never** mutates Intent / SummaryArtifact / Ledger state. AI may *suggest* edits to draft Intents (e.g. "Draft a scope statement for kitchen rewire") but mutation always goes through `intentService` validators. This preserves NFR-D5 (determinism does not depend on AI).

## 9. Trade extensions (per-trade packs)

`electricianTools.ts` is the **pattern**:
- Per-trade types: `CircuitDiagram`, `ElectricalChecklist`, `MaterialEstimate`, `NECCheck`
- Per-trade compliance refs (NEC, OSHA codes)
- Per-trade material catalog
- Embedded in Smith UI via dedicated screens

**Future trade packs follow the same template:**
| File | Trade | Compliance refs |
|---|---|---|
| `electricianTools.ts` | Electrician | NEC, OSHA 1926 Subpart K |
| `plumberTools.ts` (planned) | Plumber | UPC, IPC |
| `hvacTools.ts` (planned) | HVAC | IMC, ACCA Manual J |
| `carpenterTools.ts` (planned) | Carpenter | IRC, IBC |
| `roofingTools.ts` (planned) | Roofer | NRCA, IBC Ch. 15 |

**Architectural rule:** trade packs ADD endpoints + UI screens; they do NOT alter the deterministic pipeline (Intent/Artifact/Ledger). They sit alongside the core, not inside it. The 121-trade picker remains metadata; activating a trade pack is a feature flag per profile.

## 10. Deployment topology

| Environment | What runs | Where |
|---|---|---|
| **Production** | Express backend (Hetzner) + self-hosted Postgres + Tailscale Funnel for ingress | Hetzner Cloud (primary), exposes via Tailscale Funnel |
| **Production (alt path)** | Supabase (auth + storage + optional realtime) | supabase.com (legacy / optional) |
| **Mobile** | Android app | User devices (Play Store internal testing) |
| **Mobile (planned)** | iOS app | Out-of-scope v1 |
| **Web** | Desktop portal (Vite static build) | Likely Vercel/Netlify (TBD) |
| **Dev** | Local Express + local Postgres (or Supabase) + Android emulator / device | Developer machines |

**Ingress note:** `app.set('trust proxy', 1)` confirms the backend sits behind a reverse proxy (Tailscale Funnel, Cloudflare, or similar). Rate limit (`express-rate-limit`) buckets by `X-Forwarded-For`.

**CORS:** currently `origin: '*'` for ease of mobile/desktop dev. **Tighten before public launch** — limit to known mobile + desktop portal origins.

## 11. Auth flow (current state)

```
Client                                Backend                   Postgres
  │  POST /api/auth/register             │                         │
  │  {email, password, displayName}      │                         │
  ├─────────────────────────────────────►│ validate, hash bcrypt   │
  │                                      ├────────────────────────►│ INSERT profile
  │  201 {user, accessToken, refreshT}   │                         │
  ◄──────────────────────────────────────┤◄────────────────────────│
  │                                      │                         │
  │  Subsequent: Authorization: Bearer <accessToken>               │
  │  authenticateToken middleware verifies JWT, attaches user      │
  │  requirePermission(P) checks role's perms                      │
  │                                      │                         │
  │  POST /api/auth/refresh              │                         │
  │  {refreshToken}                      │                         │
  ├─────────────────────────────────────►│ verify refresh, rotate  │
  │  200 {accessToken, refreshToken}     │                         │
  ◄──────────────────────────────────────┤                         │
```

**JWT claims (current):** `userId, email, role`. **Add for tier gating:** `tier, entitlements` (entitlements is a derived bitmask of unlocked features).

**Known gap:** some `apiRouter` endpoints currently use `req.headers['x-user-id']` as a "simplified auth" header. **This is a v1-launch blocker** — replace with `authenticateToken` middleware on every Intent/Synthesis/Ledger/Channel endpoint before public launch.

## 12. Connectivity & sync rules

| Scenario | Behavior |
|---|---|
| Both clients online + Hetzner reachable | WS via `ChatManager` → backend; backend distributes; both clients see in < 1s |
| One client offline, both on same job site | BLE/WiFi-Direct mesh via `MeshService`; payload encrypted; ephemeral channels never leave the mesh |
| Both offline | Mesh-only; reconcile when either client regains connectivity |
| Phone joins back online after mesh-only period | `BoundaryEngine.relayToMesh` + `ReconciliationEngine.reconcile`: client uploads `missingOnServer`, server returns `missingOnClient`, vector clock merged |
| `BuildConfig.SUPABASE_ENABLED = true` (legacy mode) | Supabase Realtime sync runs in parallel to Hetzner WS; `SupabaseChat` keeps both in lockstep |
| Mesh service fails to advertise (`ADVERTISE_FAILED_ALREADY_STARTED`) | Self-recover via `MeshService` retry logic (already shipped) |
| User leaves work mode | Mesh service auto-pauses (battery preservation) |
| User toggles privacy | `UserPreferences` updates; reads gated server-side via RLS / role check |

## 13. Security boundary (full detail in SECURITY.md)

| Surface | Control |
|---|---|
| All `/api/*` traffic | TLS 1.3 (Tailscale Funnel terminates), rate-limited to 300/min/IP, `/api/auth/*` to 20/15min/IP |
| Auth | JWT 7d access + 30d refresh; bcrypt cost 10; refresh rotation enforced |
| Mesh transport | Encrypted payload (AES-GCM) — verify and document in SECURITY.md |
| Channel access | `ChannelVisibility` enum: public / private / restricted; `requiresApproval` flag; allowed/blocked/pending lists |
| Ledger integrity | SHA256 per artifact; supersession chain; auditLog also SHA256-checksummed per entry |
| Crew data leakage | Already enforced in SmithAI (`commit 4ce8733`); extend to all queries |
| RLS | Supabase RLS enabled on legacy tables; **add equivalent service-layer authorization for Hetzner-pg tables** (currently many endpoints accept `x-user-id` from headers) |

## 14. Known gaps & v1-launch blockers (architecture-level)

| # | Gap | Severity | Mitigation |
|---|---|---|---|
| G1 | `x-user-id` "simplified auth" header on Intent/Synthesis/Ledger endpoints | Critical | Replace with JWT middleware before public launch |
| G2 | CORS `origin: '*'` | High | Allow-list specific origins for production |
| G3 | Server-authoritative tier resolver | Critical | Build per pricing-config.json schema |
| G4 | `electricianTools` is the only trade pack; UI surfacing TBD | Medium | Pattern is set; per-trade packs follow |
| G5 | Two `Plan` models in code (legacy `Plan` table + new `Intent`) | Medium | Plan table is `@deprecated`; remove or migrate in Step 11 |
| G6 | Two migration trees (`backend/migrations/` Hetzner + `supabase/migrations/` legacy) | Low | Document Hetzner as canonical; keep Supabase for desktop portal auth only |
| G7 | `pricingTiers.ts` 3-6-9 pyramid conflicts with $0/$2.99/$9.99/$50 ladder | High | Retire pyramid; implement new ladder per pricing-config.json (Step 11) |
| G8 | Mesh payload encryption claim — verify in code | High | Audit `MeshService` send/receive paths; document in SECURITY.md |
| G9 | Audit log retention policies declared but enforcement TBD | Medium | Wire `archiveService` to actually rotate per policy |
| G10 | No formal observability stack (logs structured but no aggregator) | Medium | Add Loki/Promtail or Cloudwatch — Step 8 |

## 15. Linked specs

- [SCHEMA.md](../database/SCHEMA.md) — entity model, all tables, vocabulary mapping
- [API-SPEC.md](../api/API-SPEC.md) — REST + WS endpoints, request/response schemas
- [SECURITY.md](../security/SECURITY.md) — auth, RLS, audit, encryption
- [../specs/MASTER_PRD.md](../specs/MASTER_PRD.md) — product mission
- [../specs/FEATURES.md](../specs/FEATURES.md) — feature inventory (will be re-scored against this architecture in Step 10)
- [../specs/NFRS.md](../specs/NFRS.md) — non-functional requirements
- [../stack-profile.json](../stack-profile.json) — machine-readable stack (NEEDS UPDATE — Hetzner primary, not Supabase)
