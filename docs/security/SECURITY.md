# Smith Net — Security Specification

**Threat model framing:** Zero-trust at the network edge; authority validators at every state mutation; cryptographic seals over deterministic outputs; on-device AI to keep PII off cloud.

**Compliance posture (v1):** OWASP Top 10 (2021), GDPR-ready data export+delete, CCPA opt-out flow, App Store / Play Store billing-policy compliance. No HIPAA / PCI scope.

---

## 1. Authentication (C-01)

| Property | Setting |
|---|---|
| Mechanism | JWT (HS256) for access + refresh |
| Access token TTL | 7 days |
| Refresh token TTL | 30 days |
| Refresh rotation | enforced — old refresh token becomes invalid after exchange |
| Password storage | `bcrypt` cost 10 (`SALT_ROUNDS = 10`) |
| Min password length | 6 (**raise to 8 + complexity for v1 launch**) |
| Email verification | not yet enforced (**add for v1 launch**) |
| 2FA / MFA | out-of-scope v1; scoped for v2 |
| Session invalidation | on password change, on logout, on role change |
| Failed-login lockout | rate-limited (20 req/15min) — no per-account lockout currently (**add: 5 fails → 15min cooldown**) |

**JWT secret management:**
- Currently: `process.env.JWT_SECRET || 'smith-net-dev-secret-change-in-production'`
- **Production requirement:** secret loaded from environment / secrets manager; refuse to start if `JWT_SECRET === 'smith-net-dev-secret-change-in-production'`
- Rotation cadence: every 90 days; previous secret kept for grace window of 1 access-token TTL (7 days) so existing tokens still verify

**JWT claims:**
```json
{
  "userId": "uuid",
  "email": "user@example.com",
  "role": "solo|team|lead|foreman|enterprise|admin",
  "tier": "open|solo|advanced|enterprise",      // ADD in Step 11
  "entitlements": <bitmask>,                     // ADD in Step 11 — derived
  "iat": <epoch>,
  "exp": <epoch>
}
```

## 2. Authorization (C-02 Role Engine)

### Roles
- `SOLO` — individual, basic features
- `TEAM_MEMBER` — can join orgs
- `TEAM_LEAD` — can manage team
- `FOREMAN` — full team management
- `ENTERPRISE` — enterprise admin
- `ADMIN` — system admin

### Permissions (16, defined in `auth.ts` `Permission` enum)
Messaging / channels / media / mesh / admin / org categories.
Role → Permission mapping is in `ROLE_PERMISSIONS` (compile-time constant).

### Middleware
- `authenticateToken(req, res, next)` — validates JWT, attaches `req.user`
- `requireRole(role)(req, res, next)` — refuses if `req.user.role !== role`
- `requirePermission(perm)(req, res, next)` — refuses if `!ROLE_PERMISSIONS[req.user.role].includes(perm)`

**Audit on denial:** every `requirePermission` failure emits `SECURITY.PERMISSION_DENIED`.

### Tier-based authorization (Step 11 to add)
Tier check is **separate from role check**. Same role across tiers; tier governs feature ceilings (active jobs, PDF sends, AI tab, crew). Server-authoritative (per ARCHITECTURE.md §7).

## 3. Transport security

| Surface | Protection |
|---|---|
| Public ingress | TLS 1.3 terminated at Tailscale Funnel (or reverse proxy of choice) |
| Backend ↔ Postgres | TLS required; verify-full mode (cert-pinned in prod) |
| WebSocket | WSS in production; same JWT auth as REST; `auth` message required after connect |
| Mobile ↔ backend | TLS only; `BACKEND_URL_PRIMARY` MUST be `https://` in production builds |
| Mesh transport (BLE / WiFi-Direct) | Payload encryption — **VERIFY in `MeshService.kt`** (currently flagged as architecture gap G8) |
| Supabase Realtime (legacy path) | WSS via Supabase; default-on |

**CORS:** currently `origin: '*'` — **tighten to known origins** before public launch:
```js
cors({ origin: ['https://portal.smithnet.app', 'https://smithnet.app', /^smithnet:\/\//], credentials: true })
```

## 4. Rate limiting

| Surface | Limit | Window |
|---|---|---|
| `/api/*` (global) | 300 req | 60s per IP |
| `/api/auth/*` (tighter) | 20 req | 15min per IP |
| `/api/health` | unlimited | excluded by `skip` |

`X-Forwarded-For` honored (`app.set('trust proxy', 1)`) so the bucket is the originating IP, not the proxy loopback.

**Planned additions:**
- Per-user rate limit (in addition to per-IP) for authenticated routes — defends against credential-stuffing across IPs
- Tighter limit on `/api/synthesize` and `/api/ledger/seal` (heavy ops) — 10 req/min/user

## 5. Input validation

**Current state:** ad-hoc body checks (e.g., `if (!email || !password) ...`). 
**v1 requirement:** add `zod` schema validation at every endpoint boundary. Reject early, return `{ error: 'validation', code: 'validation', details: zodErrors }`.

**Specific concerns:**
- SQL injection: backend uses parameterized queries (`pg.query` with `$1, $2 ...`) — **good**; no string concatenation found in code survey. Maintain discipline.
- Mass assignment: `PATCH` endpoints currently spread `req.body` in some places — restrict to whitelisted fields per endpoint.
- File uploads: `multer` is used; **enforce size limits + MIME-type allow-list** (currently relies on default config).

## 6. Determinism & cryptographic sealing

The deterministic execution pipeline (Engagement → Intent → SummaryArtifact → LedgerEntry) is the security-sensitive core. See ARCHITECTURE.md §4 + SCHEMA.md §6.

**Sealing properties:**
- `computeHash(artifact)`: SHA256 over canonicalized JSON of artifact fields (excluding `id`, `created_at` — only content fields)
- Re-running synthesis with the same Intent + Job IDs + Time Entry IDs MUST produce the same hash
- `ledger_entries.sha256_hash` is the **proof of the artifact's content at sealing time**
- Any post-seal mutation of the artifact is detectable via `/api/ledger/verify/:entryId`

**Supersession chain integrity:**
- A new entry's `supersedes` must point to an entry whose `superseded_by` is NULL — prevents forking the chain
- Once `superseded_by` is set, the entry becomes immutable (no `UPDATE` allowed via service layer; enforce at DB-trigger level for v1.5)

**Threat:** an admin with DB access could mutate `summary_artifacts` directly. Detection: `/api/ledger/verify/:entryId`. Prevention (v2): blockchain anchor (column `blockchain_ref` already exists for this; not yet wired).

## 7. Data privacy & residency

| Data class | Where it lives | Access |
|---|---|---|
| Account + auth | Hetzner Postgres (canonical) + Supabase Auth (legacy desktop portal) | RLS / role-based service layer |
| Channel messages (online) | `messages` table (Hetzner) + Supabase `messages` (legacy) | service-layer check vs `channel_members` |
| Channel messages (mesh, ephemeral) | **Device-local only** — never sent to cloud per `ChannelPersistence.EPHEMERAL` | n/a |
| Media files | `images/`, `voice/`, `files/` on backend disk | static-served; **add signed URLs for v1** |
| Audit log | File-based (current) — move to DB in Step 11 | admin-role only |
| AI inference | **On-device only** (Llama via JNI on Android) — no cloud round-trip for SmithAI | n/a |
| AI training data | None — Smith Net does not train models on user data |
| Tier / billing state | Hetzner Postgres (`subscriptions`) + provider (Stripe / Play) | self only |
| Telemetry | `gate_hit_events` table | aggregated; no PII; uses `user_id_hash` |

**Privacy gating** (already enforced in code):
- `commit 4ce8733`: solo users no longer see crew data or crew insights — extend principle to ALL queries
- `commit 146a78e`: scoped colleague add + search with privacy gating
- Settings: privacy + location toggles (Signal-style)

**Cross-tenant isolation:** every query that joins `jobs` / `messages` / `intents` MUST scope by `created_by` or `assigned_to` or party membership. Service layer enforces; RLS as defense-in-depth.

## 8. Audit logging (C-05)

`auditLog.ts` already implemented:
- `AuditAction` enum: 25+ event types (auth / messaging / channels / mesh / admin / security)
- `AuditEntry` includes `checksum: string` — SHA256 hash for tamper detection per entry
- Currently file-based; **move to DB in Step 11** for queryability + retention

**Retention policies (declared, enforcement TBD per gap G9):**
| Action category | Retention | Compress after |
|---|---|---|
| Auth events | 90 days | 30 days |
| Messaging events | 30 days | 7 days |
| Channel events | 90 days | 30 days |
| Admin actions | 7 years (compliance) | 90 days |
| Security alerts | 7 years (compliance) | 90 days |

**Tamper detection:**
- Each `AuditEntry` has a `checksum: SHA256(...)` over its fields
- Periodic verifier job (planned) re-computes and alerts on mismatch
- Append-only at file level (chmod write-only); planned: append-only at DB level via trigger

## 9. Channel access control

**Visibility levels (from `types.ts`):**
- `public` — anyone discoverable; auto-join on POST /messages
- `private` — visible only to `memberIds`; non-members get 403
- `restricted` — visible to all but join requires approval (`requiresApproval = true`); requesters land in `pendingRequests`

**Block list:** `blockedUsers[]` overrides any other access.
**Allow list:** `allowedUsers[]` for restricted channels.

**Tamper-resistance:** all access state lives in the channel row + RLS / service-layer checks; no client-side trust. Audit each access decision (`CHANNEL_MEMBER_ADDED` / `CHANNEL_MEMBER_REMOVED`).

## 10. AI security boundaries

**Hard rules:**
1. SmithAI runs **on-device only** for v1. No round-trip to OpenAI / Anthropic for SmithAI features.
2. The vendor-neutral `llmInterface.ts` (C-04) supports cloud LLMs but is reserved for **server-side helpers** (e.g., auto-quote engine, intent draft generation) where the user has **explicitly invoked** an AI feature. Default config: `LLMProvider.MOCK` until production env explicitly selects a provider.
3. AI **cannot** mutate sealed artifacts or ledger entries. AI can suggest a new IntentVersion (status: `draft, auto_generated: true`) but a human must `propose` and `confirm`.
4. AI input/output must **never** be persisted to telemetry or analytics with raw user content. Only anonymized event types (e.g., `ai.suggestion_generated`).
5. SmithAI memory store on-device must be wipeable via "Clear messages on this device" (already exists for messages — extend to AI memory).
6. Solo-mode SmithAI **must not** see crew data, even if loaded in memory (already enforced per `4ce8733`).

**Prompt injection mitigation (server-side LLM calls):**
- Treat all user-supplied content as untrusted input
- Fence user content in prompts with explicit delimiters
- Never include full Auth tokens / secrets / DB connection strings in prompts
- Log full prompt + response for admin audit (audit category: `llm.invocation`)

## 11. Mesh & gateway security

**MeshService (Android — BLE + WiFi-Direct):**
- Pairing: BLE bond required for write; broadcast scan is open
- Payload encryption: **GAP G8** — verify AES-GCM (or equivalent) is in place before public launch
- Replay protection: include `vector_clock` + monotonic `timestamp`; reject duplicates by `messageId`
- Battery: gated on `work mode = ON` (already implemented)
- Discovery: 2-byte `mesh_hash` per channel limits routing surface (not membership)

**Gateway (one phone relays for offline peers):**
- Peers explicitly opt-in via `Permission.GATEWAY_RELAY` (already in role engine)
- Gateway-relayed messages tagged `origin: 'gateway'`; recipient client decides display semantics
- Gateway operator's phone never sees plaintext if mesh encryption is on

## 12. Billing & financial-data security

**v1-launch blocker:** subscription handling lives in Stripe / Play Billing — never store full PANs or CC numbers in Smith Net.

**Stripe webhook security:**
- Verify Stripe signature on every `/webhooks/stripe` call (`stripe.webhooks.constructEvent`)
- Reject any webhook without valid signature
- Idempotency: store `event.id` to dedupe re-deliveries

**Play Billing:**
- Verify purchase tokens server-side via Google Play Developer API
- Subscription state mirrored into `subscriptions` table on real-time-developer-notification

**Invoice + proposal public pages (`/p/:uuid`, `/i/:uuid`):**
- UUID is the access control (sufficient for v1; entropy ≈ 122 bits)
- No auth required (intentional — clients without accounts must view)
- Rate-limit per-UUID (e.g., 60 views/min) to prevent enumeration / DoS
- Track `viewed_at`; surface in app for the contractor

## 13. OWASP Top 10 (2021) coverage

| # | Risk | Coverage |
|---|---|---|
| A01 Broken Access Control | C-02 role engine; channel `visibility`; service-layer checks; RLS on Supabase | ⚠️ G1: `X-User-Id` header use must be removed; ⚠️ Hetzner equivalent of RLS = service-layer audits needed per endpoint |
| A02 Cryptographic Failures | bcrypt(10), SHA256 audit/ledger, TLS 1.3 | ⚠️ verify mesh encryption (G8); raise password floor to 8+complexity |
| A03 Injection | Parameterized `pg.query`; no string concat; need `zod` for input validation | ⚠️ add zod everywhere |
| A04 Insecure Design | Multi-authority validators; deterministic pipeline; HITL on confirmation | ✅ pattern is sound |
| A05 Security Misconfiguration | `JWT_SECRET` fallback in code is dev-only; CORS `*` is dev-only | ⚠️ fail-closed on dev secret in prod; lock CORS |
| A06 Vulnerable Components | npm audit clean (TBD verify); pin major versions | ⚠️ add `npm audit` + Dependabot to CI |
| A07 Identification & Auth Failures | bcrypt + JWT + refresh rotation; rate-limited auth route | ⚠️ add per-account lockout + email verification + (later) MFA |
| A08 Software & Data Integrity Failures | SHA256 over ledger entries + audit entries; supersession chain | ✅ strong; add CI signing of release binaries |
| A09 Security Logging & Monitoring | `auditLog.ts` exists; structured | ⚠️ G9: enforce retention, move to DB, add alerting |
| A10 Server-Side Request Forgery | none currently — server doesn't fetch user-controlled URLs | n/a |

## 14. Incident response

**Trigger conditions (planned):**
- > 100 failed logins / minute (potential credential stuffing)
- Audit checksum mismatch on any entry
- Ledger verify failure (artifact mutation detected)
- New admin role assignment (any)
- Unusual `DATA_EXPORT` or `DATA_PURGE` action
- `SECURITY.SECURITY_ALERT` event of any kind

**Notification channels (planned):**
- Email to founder (immediate)
- PagerDuty / Opsgenie (post-launch)
- Status page update (post-launch)

**Containment playbook (manual for v1, automated post-launch):**
1. Isolate affected user (revoke tokens, set `is_active = false`)
2. Snapshot affected DB state for forensics
3. Audit log query for actor's recent activity
4. Notify other affected users if data leak suspected
5. Public disclosure per CCPA / GDPR timelines if applicable

## 15. Data export & deletion (GDPR/CCPA)

**Export endpoint:** `POST /api/me/data/export` (Step 11)
- Returns ZIP: profile, jobs, time_entries, messages (in channels user is member of), proposals, invoices, intents, artifacts, ledger entries owned, audit log entries (where actor)
- Audit: `DATA_EXPORT`

**Delete endpoint:** `POST /api/me/account/delete` (Step 11)
- Soft-delete: `profiles.is_active = false`, anonymize `display_name`, `email` to `<deleted-uuid>@deleted`
- Hard-delete after 30-day cooling-off (config)
- Cannot delete: sealed `ledger_entries` (immutable), audit log entries (compliance requirement)
- Audit: `USER_DEACTIVATED`, then `DATA_PURGE` after cooling-off

## 16. Penetration testing & security review cadence

| Cadence | Activity |
|---|---|
| Pre-public-launch | Internal security review against this doc + OWASP checklist |
| Pre-public-launch | Third-party pen test (1-2 day engagement, scope: REST + WS + mobile) |
| Quarterly post-launch | Internal review + dependency audit |
| Annually | Full third-party pen test |
| On every major architecture change | Targeted review of changed surface |

## 17. Tracking issues — security gaps to close before public launch

| ID | Gap | Severity | Owner step |
|---|---|---|---|
| S1 | Replace `X-User-Id` header with `authenticateToken` middleware (architecture G1) | Critical | Step 11 |
| S2 | Lock CORS to known origins (architecture G2) | High | Step 11 |
| S3 | Verify mesh payload encryption (architecture G8) | High | Step 11 |
| S4 | Refuse to start with dev `JWT_SECRET` in production | High | Step 11 |
| S5 | Raise password floor to 8 chars + complexity, add per-account lockout | Medium | Step 11 |
| S6 | Add email verification on signup | Medium | Step 11 |
| S7 | Add `zod` schema validation at every endpoint | Medium | Step 11 |
| S8 | Move audit log to DB; enforce retention policies (architecture G9) | Medium | Step 11 |
| S9 | Add Stripe webhook signature verification | Critical (when billing ships) | Step 11 |
| S10 | Add Play Billing token verification | Critical (when billing ships) | Step 11 |
| S11 | Tighten Hetzner-side RLS-equivalent service-layer checks per endpoint | High | Step 11 audit |
| S12 | Sign mesh payloads + replay protection check | High | Step 11 |
| S13 | Implement `/api/ledger/verify` periodic background job | Medium | Step 11 |
| S14 | Move `JWT_SECRET` and DB credentials to a secrets manager (not env) | Medium | Step 8 (deployment) |
