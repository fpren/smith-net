---
name: smith-net-architecture
description: Architecture conventions for Smith Net — Hetzner Express is canonical backend (Supabase is legacy), 5 Phase-0 components (C-01 through C-05), multi-authority validator pattern, Intent → SummaryArtifact → Ledger pipeline. Use when working in /backend/, modifying API routes, adding services, or making architectural decisions in this codebase.
---

# Smith Net — Architecture conventions

This skill activates when working anywhere in the Smith Net codebase that touches backend services, schema, or system design.

## Authoritative backend = Hetzner Express + self-hosted Postgres

- **Primary backend:** Express on Hetzner Cloud, exposed via Tailscale Funnel. Driver: raw `pg@8` (no ORM).
- **Migrations canonical dir:** `backend/migrations/` (NOT `supabase/migrations/`).
- **Supabase is LEGACY** — `BuildConfig.SUPABASE_ENABLED=false` by default. Used only for desktop portal auth. Do NOT add new features against Supabase.
- **Two migration trees coexist** — when adding new tables, write to `backend/migrations/` only.

## The 5 Phase-0 Components (declared in `backend/src/server.ts`)

When adding new server code, place it under the right component:

| ID | Module | Files |
|---|---|---|
| C-01 | Authentication & Identity | `auth.ts`, `authRoutes.ts`, `identityResolver.ts` |
| C-02 | Role Engine | `auth.ts` (UserRole + Permission + ROLE_PERMISSIONS) |
| C-03 | Schema & Boundary Engine | `messageBus.ts`, `channelRegistry.ts`, `presenceManager.ts`, `gatewayManager.ts`, `wsHandler.ts` (server) + Android `engine/BoundaryEngine.kt` |
| C-04 | Vendor-Neutral LLM Interface | `llmInterface.ts` only — abstracts OpenAI/Anthropic/local/mock |
| C-05 | Data Retention Core | `auditLog.ts`, retention crons |

## Multi-authority validator pattern

State mutations follow the pattern: **call `validate*Authority` first; mutate only if `valid: true`.**

| Authority | File | Validates |
|---|---|---|
| `intentAuthority` | `intentAuthority.ts` | Intent creation (scope, parties), confirmation, versioning |
| `synthesisAuthority` | `synthesisAuthority.ts` | Synthesis preconditions + artifact post-conditions |
| `ledgerAuthority` | `ledgerAuthority.ts` | Sealing, amendment, hash computation |

**When adding new state-mutating endpoints, follow the same pattern.** Don't inline validation in handlers; extract to a sibling `*Authority.ts`.

## The deterministic execution pipeline (the moat — protect at all costs)

```
Engagement → Intent → IntentVersion (draft → proposed → confirmed → superseded)
   → Synthesizer (validates) → SummaryArtifact (with serial)
      → Ledger (computeHash → SHA256) → LedgerEntry (immutable, supersession chain)
```

Sealed `ledger_entries` are immutable. Verification via `/api/ledger/verify/:entryId` re-computes hash and detects tampering. **Don't add code paths that bypass this pipeline.** AI features may suggest drafts (`auto_generated=true`) but cannot mutate confirmed/sealed state.

## Critical NFRs that govern architecture decisions

- **NFR-D1 to D5** (determinism): same inputs → same outputs; cord transitions append-only; AI never required to advance a transition
- **NFR-O1 to O6** (offline): core flows must work fully offline on Android; mesh routes when offline; reconciliation via vector clocks (no last-write-wins)
- **NFR-S1 to S11** (security): TLS 1.3, JWT 7d access + 30d refresh, bcrypt cost 10, RLS or service-layer authorization on every read
- **NFR-P6** (perf): backend p95 < 300ms read, < 800ms write

## Multi-transport message routing (BoundaryEngine on Android)

- Online + Hetzner reachable → ChatManager via WS
- Offline + peers in BLE/WiFi-Direct range → MeshService P2P
- Bridge → GatewayClient relays
- Reconnect → ReconciliationEngine via `/api/reconcile`

`MessageOrigin` enum: `online | mesh | gateway | online+mesh`. `TransportType` enum: `MESH | ONLINE | GATEWAY | SUPABASE`.

## Trade-extension pattern

`backend/src/electricianTools.ts` is the **template** for per-trade packs. New trade packs (plumber, HVAC, etc.) follow the same pattern: own types + own tables + own UI screens — they sit ALONGSIDE the core, never INSIDE the Intent/Artifact/Ledger pipeline. Trade is metadata; core stays trade-agnostic.

## Don't do

- ❌ Add features to Supabase migrations dir (`supabase/migrations/`)
- ❌ Bypass multi-authority validators in service code
- ❌ Mutate sealed `ledger_entries` after the supersession chain advances
- ❌ Use `X-User-Id` header for identity (removed in F1.1; use `req.user.id` from `authenticateToken` middleware)
- ❌ Touch `pricingTiers.ts` (legacy 3-6-9 pyramid — being retired by F2.x; use new tier resolver)
- ❌ Add ORM (project deliberately uses raw `pg` with parameterized queries)
- ❌ Add WebSocket message types without registering in `WSMessageType` enum (`backend/src/types.ts`)

## Linked specs

- `docs/architecture/ARCHITECTURE.md` — full system design
- `docs/database/SCHEMA.md` — all entity domains
- `docs/api/API-SPEC.md` — REST + WS endpoints
- `docs/technical/TECHNICAL-SPEC.md` — consolidated build-ready blueprint
- `docs/prds/F2.x` — tier resolver (replaces legacy pricingTiers)
