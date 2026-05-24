# Smith Net — Technical Specification

**Sigma step:** 8 — Technical Specification
**Persona:** Distinguished Engineer
**Status:** retrofit (existing codebase + Sigma planning of net-new work)
**Audience:** any engineer joining this project should be able to ship from this doc + the linked specs.

This is the consolidated build-ready blueprint. Every section cross-references the canonical source. **This doc never duplicates content** — it summarizes + binds + decides.

---

## 0. The TL;DR

**Smith Net is a deterministic job-execution platform for contractors.**

- **Primary client:** Android (Kotlin + Jetpack Compose). 55K+ LOC shipped.
- **Secondary client:** Desktop portal (Vite + React + Zustand + Tailwind). Online-only.
- **Backend:** Hetzner-hosted Express + raw `pg` + `ws`. Behind Tailscale Funnel.
- **Database:** self-hosted Postgres (canonical) + Supabase Postgres (legacy / desktop auth only).
- **Moat:** deterministic Intent → SummaryArtifact → LedgerEntry pipeline (SHA256-sealed, supersession chain, multi-authority validators). Plus VectorClock + Cord state model for distributed conflict-free sync.
- **Tier ladder:** Free / Solo $2.99 / Advanced $9.99 / Enterprise $50. One hero feature per tier. Founder pricing at 1000/100/10 seats.
- **Critical v1 launch blockers:** 19 items tracked (B1-B19 in DEV-READINESS.md §4) — primarily security (X-User-Id removal), tier resolver + billing, and tier-gate UI.

---

## 1. Architecture (canonical: `ARCHITECTURE.md`)

### 1.1 System layers

```
[Android Compose UI] ── BoundaryEngine ──┬── MeshService (BLE + WiFi-Direct, P2P)
                                          ├── ChatManager (WS to Hetzner)
                                          └── GatewayClient (relay path)
                                                │
                                                ▼
[Express on Hetzner] ── Phase-0 Components C-01..C-05 ── Multi-Authority Validators
                                                │
                                                ▼
[Self-hosted Postgres] ── 11 entity domains, append-only audit, SHA256-sealed Ledger

[Supabase] ── desktop portal auth + legacy sync path (BuildConfig.SUPABASE_ENABLED=false default)
```

### 1.2 The Phase-0 Components (declared in `backend/src/server.ts`)

| ID | Module | Files |
|---|---|---|
| C-01 | Authentication & Identity | `auth.ts`, `authRoutes.ts`, `identityResolver.ts` |
| C-02 | Role Engine | `auth.ts` (UserRole enum × Permission enum × ROLE_PERMISSIONS map) |
| C-03 | Schema & Boundary Engine | `messageBus.ts`, `channelRegistry.ts`, `presenceManager.ts`, `gatewayManager.ts`, `wsHandler.ts` (server) + `engine/BoundaryEngine.kt` (Android) |
| C-04 | Vendor-Neutral LLM Interface | `llmInterface.ts` (OpenAI / Anthropic / local / mock providers) |
| C-05 | Data Retention Core | `auditLog.ts` (25+ audit actions, SHA256 per entry, retention policies) |

### 1.3 The deterministic moat pipeline

```
Engagement → Intent → IntentVersion (draft → proposed → confirmed → superseded)
   → Synthesizer (synthesisAuthority validates) → SummaryArtifact (with serial)
      → Ledger (ledgerAuthority + computeHash) → LedgerEntry (immutable, supersession chain)
         → Outputs: Invoice, Report, Public link
```

**Authority pattern:** `intentAuthority`, `synthesisAuthority`, `ledgerAuthority` are pure validators. Every state mutation calls `validate*` first.

**Determinism guarantee:** Same `(intentVersionId, jobIds[], timeEntryIds[], chatMessageIds[])` → same SummaryArtifact byte-for-byte → same SHA256 hash. Re-runnable. Tamper-detectable via `/api/ledger/verify/:entryId`.

---

## 2. Database (canonical: `SCHEMA.md`)

### 2.1 Authoritative store

- **Self-hosted Postgres on Hetzner.** Driver: `pg@8` (raw, parameterized queries, no ORM).
- **Migrations:** `backend/migrations/` (Hetzner canonical) + `supabase/migrations/` (10 files, legacy/optional).
- **Extensions:** `pgcrypto` (`gen_random_uuid()`).

### 2.2 Entity domains (11)

| Domain | Tables | Source |
|---|---|---|
| Identity & access | profiles, organizations | `SCHEMA.md §4` |
| Channels & messages | channels, channel_members, messages, message_bus_messages | `SCHEMA.md §5` |
| Deterministic pipeline | engagements, intents, intent_versions, summary_artifacts, ledger_entries | `SCHEMA.md §6` |
| Jobs | jobs, tasks, time_entries, materials | `SCHEMA.md §7` |
| Outputs | proposals, invoice_links, invoices, reports, plan_outputs, plan_snapshots | `SCHEMA.md §8` |
| Audit | audit_log (file→DB in Step 11) | `SCHEMA.md §9` |
| Trade extensions | circuit_diagrams, electrical_checklists, ... (electricianTools pattern) | `SCHEMA.md §10` |
| Tier enforcement (NEW) | profiles.tier columns, subscriptions, founder_seats, gate_hit_events | `SCHEMA.md §11` |

### 2.3 Migration plan (M1-M8 in `SCHEMA.md §14`)

| # | Migration | Owner |
|---|---|---|
| M1 | add_tier_columns_to_profiles | Step 11 |
| M2 | create_subscriptions_table | Step 11 |
| M3 | create_founder_seats_table | Step 11 |
| M4 | create_gate_hit_events_table | Step 11 |
| M5 | add_template_to_invoices | Step 11 |
| M6 | migrate_plans_to_intents (data) | Step 11 |
| M7 | tighten_rls_policies | Step 11 |
| M8 | move_audit_log_to_db | Step 11 |

### 2.4 Indexes (current + planned)

`SCHEMA.md §12` — 12 indexes total, hot paths covered: messages by channel + time, intent_versions by intent + status, ledger_entries by serial + sealed_at, public-page UUIDs.

---

## 3. API (canonical: `API-SPEC.md`)

### 3.1 Surface count

- **REST endpoints:** ~50 across 18 sections (Auth, Engagements, Intents, Synthesis, Ledger, Jobs, Tasks, Time, Channels, Reconciliation, Media, Proposals, Invoices, Reports, Tier, Telemetry, Admin, Public).
- **WebSocket events:** 21 message types (`API-SPEC.md §18`).
- **Public-facing:** `/p/:uuid` (proposals), `/i/:uuid` (invoices), `/api/health`.

### 3.2 Authentication

- JWT (HS256). Access TTL 7d, refresh TTL 30d, refresh rotation enforced.
- All `/api/*` requires `Authorization: Bearer <token>` middleware (`authenticateToken`) — except `/api/auth/{register,login,refresh}` and `/api/health`.
- **CRITICAL gap (B1/S1):** several Intent/Synthesis/Ledger/Channel endpoints currently use `X-User-Id` "simplified auth" header. **MUST replace before public launch.**

### 3.3 Error format (canonical)

```json
{
  "error": "human-readable message",
  "code": "machine_code",
  "details": { ... }
}
```

Standard codes: `unauthenticated` (401), `forbidden` (403), `not_found` (404), `validation` (400), **`tier_gate_exceeded` (403)** with `gate_id`, `limit`, `current`, `current_tier`, `rate_limit_exceeded` (429), `conflict` (409), `server_error` (500).

### 3.4 Tier-gate response contract

When a tier-gated capability is attempted by an under-tiered user, server returns:

```json
HTTP 403
{
  "error": "tier_gate_exceeded",
  "code": "tier_gate_exceeded",
  "gate_id": "active_job_cap",
  "current_tier": "open",
  "limit": 1,
  "current": 1,
  "details": { "target_tier": "solo" }
}
```

Client uses `gate_id` to invoke the correct `LockedFeatureOverlay` variant.

---

## 4. Frontend (Android — canonical: `EXTRACTED-PATTERNS.md` + `DESIGN-SYSTEM.md` + `WIREFRAME-SPEC.md`)

### 4.1 Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3 deps imported, but custom Composables used — see `DESIGN-SYSTEM.md §9`)
- **Theme API:** `ConsoleTheme.*` (NOT `MaterialTheme.*`)
- **Font:** `FontFamily.Monospace` only
- **Mode:** light only (`TradeMeshTheme` forces `LightColorScheme` regardless of system setting)

### 4.2 Net-new components (Step 5 introduced)

| Component | File path | Tokens (`DESIGN-TOKENS.md §9`) |
|---|---|---|
| `LockedFeatureOverlay` | `ui/components/LockedFeatureOverlay.kt` | `components.lockedFeatureOverlay` |
| `TrialBanner` | `ui/components/TrialBanner.kt` | `components.trialBanner` |
| `FounderSeatsCounter` | `ui/components/FounderSeatsCounter.kt` | `components.founderSeatsCounter` |
| `TierUpgradeCTA` | `ui/components/TierUpgradeCTA.kt` | (uses lockedFeatureOverlay tokens) |
| `EntitlementLock` | `ui/components/EntitlementLock.kt` | `components.entitlementLock` |
| `PdfSendCounterFooter` | `ui/components/PdfSendCounterFooter.kt` | `components.pdfSendCounterFooter` |
| `GateHitToast` | `ui/components/GateHitToast.kt` | (uses Toast — system) |
| `TierPricingScreen` | `ui/subscription/TierPricingScreen.kt` | `components.tierPricingScreen` |
| `SubscriptionDetailScreen` | `ui/subscription/SubscriptionDetailScreen.kt` | `components.subscriptionDetailScreen` |
| `CancelSubscriptionDialog` | `ui/subscription/CancelSubscriptionDialog.kt` | (custom Composable, no Material AlertDialog) |
| `DeleteAccountDialog` | `ui/subscription/DeleteAccountDialog.kt` | (custom; only place `{color.error}` used in interactive text) |
| `WelcomeToOpenScreen` | `ui/WelcomeToOpenScreen.kt` | section pattern |

### 4.3 Net-new wiring into existing screens (`WIREFRAME-SPEC.md §11`)

- **`Q2 SettingsScreen.kt`:** SUBSCRIPTION row above PROFILE; AI ASSISTANT section conditional on `entitlements.smithAI`
- **`D3 NewJobFlow.kt`:** Save handler interprets 403 tier_gate_exceeded → invokes N4 overlay
- **`H1 InvoiceScreen.kt` + `G4 InvoicePreviewBottomSheet.kt`:** counter footer; Send handler interprets 403 → N5 overlay
- **`E1 PlanScreen.kt`:** tier check at top; if Open → N3 overlay with dimmed live preview
- **`C1 DashboardScreen.kt`:** tier-aware UPGRADE quick-action tile via `getQuickActions()`
- **`MainActivity.kt`:** TrialBanner host above nav

### 4.4 Component effort (`WIREFRAME-SPEC.md §15`)

**~25 engineering days** for net-new Android + server work, before testing/QA/copy/polish (multiplier ~1.4×).

---

## 5. Frontend (Desktop portal)

### 5.1 Stack

- **Build:** Vite 5
- **Framework:** React 18 + TypeScript
- **State:** Zustand
- **Routing:** react-router-dom 6
- **Styling:** Tailwind (via `clsx` + `tailwind-merge`) — to bind to the same `DESIGN-TOKENS.md` palette
- **Auth:** Supabase Auth UI
- **Data:** Supabase JS

### 5.2 Status

In-progress. `desktop/portal/src/dashboard/` exists uncommitted. Online-only path. Mesh + on-device AI not applicable.

### 5.3 Token consumption

The desktop portal MUST consume the same color palette + spacing scale + typography (monospace family). Net-new tier-gate UI (TierPricingScreen, SubscriptionDetailScreen) ports from Android Compose to React/Tailwind in Step 11+, using the same token references.

---

## 6. Tier resolution & enforcement

### 6.1 Server-authoritative principle

Tier is sourced from `profiles.tier` (mirror) reconciled with `subscriptions` (truth). Stripe webhooks (`/webhooks/stripe`) and Play Billing real-time-developer-notifications (`/webhooks/play-billing`) are the only sources of subscription state mutation.

### 6.2 JWT claims (post Step 11)

```json
{
  "userId": "uuid",
  "email": "user@example.com",
  "role": "solo|team|lead|foreman|enterprise|admin",
  "tier": "open|solo|advanced|enterprise",
  "entitlements": <bitmask>,
  "iat": <epoch>, "exp": <epoch>
}
```

`tier` and `entitlements` are NEW. Existing claims unchanged.

### 6.3 Client-side gate UI (UX, not authority)

Per `UX-DESIGN.md §4`:
- **Role gates HIDE** (existing — `if (RoleContext.can(Permission.GATEWAY_RELAY)) { ... }`)
- **Tier gates SHOW + LOCK + CTA** (new — `LockedFeatureOverlay` + chevron pattern)

Both must be paired with **server enforcement** — UI gating is for UX only; client cannot be trusted.

### 6.4 Per-tier capability matrix

See `pricing-config.json` — full machine-readable. Quick view:

| Capability | Open | Solo | Advanced | Enterprise |
|---|---|---|---|---|
| Active jobs | 1 | unlimited | unlimited | unlimited |
| PDF sends/mo | 5 | unlimited | unlimited | unlimited |
| PLAN Compiler (Intent → Artifact → Ledger) | ❌ | ✅ | ✅ | ✅ |
| Cord-based state model | ❌ | ✅ | ✅ | ✅ |
| SmithAI on-device | ❌ | ❌ | ✅ | ✅ |
| Standard invoice template | ✅ | ✅ | ✅ | ✅ |
| Advanced invoice template | ❌ | ❌ | ✅ | ✅ |
| Enterprise invoice template | ❌ | ❌ | ❌ | ✅ |
| Crew / multi-user | ❌ | ❌ | ❌ | ✅ |
| Smith Net branding on PDFs | yes (forced) | no | no | no |

### 6.5 Founder seats

| Tier | Bonus | Cap |
|---|---|---|
| Solo | Founder Pricing Lock ($2.99/mo for life) | 1000 |
| Advanced | Lifetime Template Library | 100 |
| Enterprise | Founder Annual Pricing ($500/yr vs $600) | 10 |

Server enforces atomically (concurrency-safe). 10-min hold on CTA tap. Counter pushed via WS `founder_seats_changed` event.

---

## 7. Authentication & Authorization (canonical: `SECURITY.md`)

### 7.1 Auth model

- bcrypt cost 10 (raise password floor 6 → 8 + complexity in Step 11)
- JWT HS256, access 7d, refresh 30d with rotation
- No 2FA v1; scoped for v2
- Per-account lockout: NOT YET (add 5 fails → 15min in Step 11)

### 7.2 Authorization

- 6 roles: SOLO, TEAM_MEMBER, TEAM_LEAD, FOREMAN, ENTERPRISE, ADMIN
- 16 permissions across messaging / channels / media / mesh / admin / org categories
- Role → Permission compile-time map in `auth.ts`
- Middleware: `authenticateToken`, `requireRole(role)`, `requirePermission(perm)`
- Audit on every PERMISSION_DENIED

### 7.3 Tier authorization (NEW)

Separate from role. Same role across tiers. Tier governs feature ceilings. Server check at every tier-gated endpoint (Step 11 PRDs).

---

## 8. Determinism contract (canonical: `FLOW-4-plan-compose-to-seal.md`)

### 8.1 NFR-D1 through D5 (must hold)

| NFR | Property | Enforcement |
|---|---|---|
| D1 | Same compiled plan → identical execution traces across runs/devices | `synthesizer.synthesize()` is pure of `(intentVersionId, jobIds, timeEntryIds, chatMessageIds)` |
| D2 | Cord transitions append-only | DB trigger (Step 11) prevents UPDATE on non-NULL `superseded_by` |
| D3 | Same SummaryArtifact byte-for-byte → same `computeHash` | canonicalized JSON (sorted keys, no whitespace, fixed numeric formatting) |
| D4 | Plan compiled at version V runs on any client supporting compiler version ≥ V | semver compatibility on compiler artifact |
| D5 | AI never required to advance a cord transition | `intentService` validators reject AI as confirmer; AI is observation/suggestion only |

### 8.2 Verify endpoint

`/api/ledger/verify/:entryId` re-computes hash from current `summary_artifacts` row. Tamper detection.

### 8.3 Critical security tests (must pass before public launch — `FLOW-4`)

| Test | Pass criteria |
|---|---|
| Determinism stability under load | 1000 parallel synthesize calls, same inputs → 1000 identical artifacts |
| Hash collision check | 100k sample artifacts → 0 SHA256 collisions |
| Tamper detection latency | Verify endpoint p95 < 500ms |
| Supersession DAG integrity | 100 random amend chains → no cycles |
| RLS / authorization | Non-party user attempting to read another user's intent → 403 |

---

## 9. Connectivity & sync (canonical: `FLOW-5-online-offline-sync.md`)

### 9.1 Transport selection (BoundaryEngine on Android)

```
[Android client]
   ├── Online + Hetzner reachable: ChatManager via WS to BACKEND_URL_PRIMARY
   ├── Offline + peers in BLE/WiFi-Direct range: MeshService P2P
   ├── Bridge (one device online + one offline): GatewayClient relays
   └── Reconnect after offline period: ReconciliationEngine.reconcile via /api/reconcile
```

### 9.2 Vector clock invariants

- Every `UnifiedMessage` carries `vectorClock: { deviceId: counter }`.
- `vectorClock.merge(a, b)` returns max-per-key.
- `vectorClock.compare(a, b)` returns `-1 | 0 | 1` (concurrent = 0).
- Concurrent events kept ordered by `(timestamp, id)` deterministically across all clients. **No last-write-wins.**

### 9.3 Ephemeral channels

- `ChannelPersistence.EPHEMERAL` channels NEVER persist server-side.
- Mesh-routed messages on ephemeral channels: zero database insertion at any point.
- Audit: NO `MESSAGE_SENT` events for ephemeral channels (privacy by design).

---

## 10. AI (canonical: `ARCHITECTURE.md §8` + `FLOW-3-ai-tab-to-advanced.md`)

### 10.1 SmithAI is Advanced+ tier only

- On-device only — Llama via `LlamaInference` JNI wrapper.
- Lifecycle: SLEEPING → WAKING (model load 0%-100%) → ALIVE | RULE_BASED_FALLBACK.
- Model size: ~2GB (downloaded on opt-in only, NOT bundled in APK).
- Solo tier sees `EntitlementLock` (N10) instead of AI section.

### 10.2 RULE_BASED_FALLBACK

- Available to **all tiers** as the deterministic baseline.
- Activates when: model load fails, low battery, low spec device.
- This is what's behind the "deterministic baseline taste" Free-tier hero.

### 10.3 AI never mutates pipeline

- AI may suggest a draft IntentVersion (`auto_generated=true`).
- Human MUST `propose` and `confirm` before synthesis.
- Synthesis is a pure function over IDs — AI is not in the synthesis path.
- This preserves NFR-D5.

---

## 11. Trade extensions (canonical: `SCHEMA.md §10`)

`electricianTools.ts` is the **template** for per-trade packs:

- Per-trade interfaces (e.g., `CircuitDiagram`, `ElectricalChecklist`, `MaterialEstimate`, `NECCheck`)
- Per-trade DB tables (e.g., `circuit_diagrams`)
- Per-trade UI (sit alongside core, do NOT alter Intent/Artifact/Ledger pipeline)

Future packs follow same pattern: `plumberTools.ts`, `hvacTools.ts`, `carpenterTools.ts`, `roofingTools.ts`. The 121-trade picker is metadata; trade-pack activation is a feature flag per profile.

---

## 12. Observability

### 12.1 Logging

- Structured JSON on backend
- Log level configurable per env
- Currently no aggregator (gap G10) — add Loki/Promtail or Cloudwatch in Step 11

### 12.2 Crash reporting (Android)

- Existing setup TBD — add Crashlytics or equivalent in Step 11
- Crash-free user rate target: ≥ 99.5% daily (NFR-R1)

### 12.3 Telemetry (NEW — Step 11)

Per `pricing-config.json` `tier_gates_to_telemetry_event_map`:

| Event | Trigger |
|---|---|
| `gate_hit.active_job_cap` | Free user attempts 2nd active job |
| `gate_hit.pdf_send_cap` | Free user attempts 6th PDF send |
| `gate_hit.plan_compiler_preview` | Free or Solo user opens PLAN preview |
| `gate_hit.ai_tab` | Free or Solo opens AI Assistant section |
| `gate_hit.crew_invite` | Free/Solo/Advanced attempts colleague invite |
| `tier_upgrade.cta_shown` | Upgrade CTA rendered |
| `tier_upgrade.cta_clicked` | User taps CTA |
| `tier_upgrade.trial_started` | Trial activated |
| `tier_upgrade.paid_converted` | CC entered → paid subscription |
| `tier_downgrade.canceled` | User cancels subscription |

**No PII in events** (per NFR-OB4) — `user_id_hash = SHA256(profile.id)` only.

### 12.4 Audit log

- Currently file-backed (`auditLog.ts`)
- Move to DB in Step 11 (gap G9/S8) for queryability + retention enforcement
- Per-entry SHA256 checksum for tamper detection

---

## 13. Performance budgets (canonical: `NFRS.md §3` + `MICRO-INTERACTIONS.md §19`)

| Metric | Target |
|---|---|
| App cold start to first interactive screen (Android) | < 2s on Pixel 6 / Snapdragon 778 class |
| Job list scroll | 60fps for ≤ 1000 jobs |
| Invoice PDF generation | < 3s standard, < 5s Advanced/Enterprise |
| SmithAI cold-load | < 30s on supported devices |
| SmithAI inference | median < 5s, p95 < 15s |
| Backend API p95 | < 300ms read, < 800ms write |
| Supabase Realtime broadcast | < 1s when both online |
| Overlay appear after CTA tap | < 200ms |
| Status pill recompose | < 16ms (one frame) |
| Tier resolver re-fetch | < 500ms |

---

## 14. Security (canonical: `SECURITY.md`)

### 14.1 14-item gap list (S1-S14) — must address before public launch

| ID | Gap | Severity |
|---|---|---|
| S1 | Replace `X-User-Id` header with JWT middleware | Critical |
| S2 | Lock CORS to known origins | High |
| S3 | Verify mesh payload encryption (AES-GCM) | High |
| S4 | Refuse to start with dev `JWT_SECRET` in production | High |
| S5 | Raise password floor 6 → 8 + complexity, add per-account lockout | Medium |
| S6 | Email verification on signup | Medium |
| S7 | `zod` schema validation at every endpoint | Medium |
| S8 | Move audit log to DB; enforce retention policies | Medium |
| S9 | Stripe webhook signature verification | Critical (when billing ships) |
| S10 | Play Billing token verification | Critical (when billing ships) |
| S11 | Tighten Hetzner-side service-layer authorization | High |
| S12 | Sign mesh payloads + replay protection | High |
| S13 | `/api/ledger/verify` periodic background job | Medium |
| S14 | Move secrets to a secrets manager | Medium |

### 14.2 OWASP Top 10 (2021)

Coverage matrix in `SECURITY.md §13`. Strong on: A04 (insecure design — multi-authority pattern), A08 (data integrity — SHA256 + supersession). Weak on: A01 (X-User-Id), A05 (CORS + JWT secret), A03 (zod validation needed).

---

## 15. Deployment topology

| Environment | What runs | Where |
|---|---|---|
| Production | Express + self-hosted Postgres | Hetzner Cloud, exposed via Tailscale Funnel |
| Production (alt) | Supabase (auth + storage + optional realtime) | supabase.com — for desktop portal auth + legacy chat |
| Mobile (Android) | Smith Net app | Play Store internal-testing → public release |
| Web (desktop portal) | Vite static build | Vercel / Netlify (TBD) |
| Dev | Local Express + local Postgres + emulator/device | developer machines |

### 15.1 CORS

Currently `origin: '*'`. **Lock to known origins before public launch** (S2):
```js
cors({ origin: ['https://portal.smithnet.app', 'https://smithnet.app', /^smithnet:\/\//], credentials: true })
```

### 15.2 Reverse proxy

`app.set('trust proxy', 1)` — Tailscale Funnel sits in front. `X-Forwarded-For` honored for rate-limit bucketing.

### 15.3 Rate limits

- `/api/*` global: 300 req/min/IP
- `/api/auth/*`: 20 req/15min/IP
- `/api/synthesize` and `/api/ledger/seal` (planned tightening): 10 req/min/user
- `/i/:uuid`, `/p/:uuid` (public pages): 60 req/min/UUID

---

## 16. Testing strategy

### 16.1 Test pyramid

| Layer | Coverage target | Tools |
|---|---|---|
| Unit (backend) | 80% on validators (intent / synthesis / ledger / pricing) | jest + ts-jest |
| Unit (Android) | 70% on view models + repositories | JUnit + Robolectric |
| Integration (backend) | every API endpoint with happy + error paths | jest + supertest + test pg DB |
| E2E (Android) | critical flows F1-F8 | Espresso + Compose UI test |
| Determinism | 100% of synthesize/seal paths | dedicated tests with fixed inputs → byte-equality assertions |
| Concurrency | founder seat reservation, intent supersession | parallel test harness |
| Security | OWASP Top 10 coverage | `npm audit` in CI + manual pen test pre-launch |

### 16.2 BDD scenarios

28 Gherkin scenarios across `docs/prds/flows/FLOW-1` through `FLOW-8`. Convert to executable specs (e.g., Cucumber-jvm or jest-cucumber) in Step 11.

### 16.3 Smoke test budget

< 5 min in CI (NFR-DV4). Includes auth, job CRUD, intent → seal happy path, message send via WS, mesh transport stub.

---

## 17. Build & release

### 17.1 Reproducibility

- All builds reproducible from clean checkout + lockfile (NFR-DV1)
- Backend: `npm ci` + `tsc`
- Android: gradle wrapper pinned in `gradle/wrapper/gradle-wrapper.properties`

### 17.2 Migrations

- Auto-run on backend boot in dev
- Manual + reviewed in prod (NFR-DV2)
- Idempotent + reversible

### 17.3 Release channels

| Channel | Trigger |
|---|---|
| Internal testing (Android) | every commit to `main` (current state) |
| Closed beta | TBD post-Step 11 |
| Public launch | TBD post-S1-S14 closure + B1-B19 closure |

---

## 18. Step 11 PRD scope (forward look)

Per `TRACEABILITY-MATRIX.md §15` — **~20-22 vertical-slice PRDs**. Each = database + service + UI + tests + BDD. Estimated 2-3 implementer-weeks per PRD.

| Domain | PRDs | Notes |
|---|---|---|
| Tier gating (Domain 9) | 8-10 | Subscription, entitlements, founder seats, all 12 N1-N12 surfaces |
| Invoicing (3.2-3.5) | 3 | Advanced + Enterprise templates + branding stamp |
| Plans UI surfacing (1.5-1.6) | 2 | Compose UI, sealed-artifact actions |
| AI (6.9) | 1 | Lock state + downgrade-friendly |
| Crew (7.2-7.5) | 3 | Crew invite, shared jobs, dispatch upgrade |
| Security cleanup (S1, S7) | 2 | X-User-Id removal + zod validation |
| Audit log to DB (S8) | 1 | Move + retention |
| **Total** | **~20-22** | |

---

## 19. Critical decisions that govern Step 11+

| # | Decision | Rationale |
|---|---|---|
| 1 | Hetzner Express is the canonical backend | Already shipped + actively used; Supabase is legacy |
| 2 | Light mode forced everywhere | Brand identity + outdoor readability; codify in `Theme.kt` (already done) |
| 3 | Custom Composables, not Material widgets | Brand consistency; ConsoleTheme is the API |
| 4 | Monospace everywhere | Brand DNA |
| 5 | Tier ladder $0 / $2.99 / $9.99 / $50 (one hero per tier) | Replaces legacy 3-6-9 pyramid in `pricingTiers.ts`; retire that |
| 6 | AI = Advanced floor (NOT Solo) | $9.99 differentiator vs $2.99 entry |
| 7 | Free tier = AI-free deterministic baseline | Don't give away SmithAI; prove determinism instead |
| 8 | No-CC trial at Solo + Advanced; CC required at Enterprise | Lower-tier friction kills conversion; Enterprise has budget |
| 9 | Founder pricing real and exhaustible (no fake scarcity) | Trust > tactics |
| 10 | Cancel = one tap, no friction | "Contractor is the boss" principle |
| 11 | Tier gates SHOW + LOCK (UI) + ENFORCE (server). Role gates HIDE (existing) | Different incentive design per gate type |
| 12 | Vocabulary: code uses Intent / SummaryArtifact / Ledger; marketing uses "PLAN Compiler" | Internal precision + external clarity |
| 13 | Trade-agnostic core + per-trade extension packs | One product, infinite scope |
| 14 | electricianTools is the template; future trade packs replicate the pattern | Don't fork the platform per trade |
| 15 | Step 9 (Landing Page) deferred indefinitely | User direction — focus on app infrastructure |
| 16 | Step 8 ends here; Step 10 (Feature Breakdown) shapes the work; Step 11 PRDs ship | Sigma sequence |

---

## 20. Linked specs

- `MASTER_PRD.md`, `USP.md`, `FEATURES.md`, `NFRS.md`, `DEV-READINESS.md` (Step 1)
- `OFFER_ARCHITECTURE.md`, `pricing-config.json` (Step 1.5)
- `ARCHITECTURE.md`, `SCHEMA.md`, `API-SPEC.md`, `SECURITY.md` (Step 2)
- `EXTRACTED-PATTERNS.md`, `INSPIRATION.md`, `UX-DESIGN.md`, `USER-JOURNEYS.md`, `STATE-COVERAGE.md`, `WIREFRAMES.md` (Step 3)
- `SCREEN-INVENTORY.md`, `FLOW-TREE.md`, `FLOW-DIAGRAMS.md`, `TRACEABILITY-MATRIX.md`, `ZERO-OMISSION-CERTIFICATE.md` (Step 4)
- `WIREFRAME-SPEC.md`, `FLOW-1.md` ... `FLOW-8.md` (Step 5)
- `DESIGN-SYSTEM.md`, `DESIGN-TOKENS.md` (Step 6)
- `STATE-SPEC.md`, `MICRO-INTERACTIONS.md` (Step 7)
- `SUCCESS-METRICS.md` (Step 1, ops/)
- `ENVIRONMENT-SETUP.md` (Step 0, ops/)
- `stack-profile.json` (Step 1, root)
