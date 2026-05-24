---
name: smith-net-security
description: Security boundaries for Smith Net — JWT 7d/30d, bcrypt cost 10, no X-User-Id header (use authenticateToken middleware), CORS allowlist, zod validation everywhere, mesh AES-GCM + signing + replay protection, append-only audit log with SHA256 checksums. Use when handling auth, adding endpoints, modifying middleware, working on mesh transport, or making security-affecting changes.
---

# Smith Net — Security boundaries

This skill activates whenever auth, authorization, transport security, audit logging, or input validation is involved.

## Auth model (don't reinvent)

- **JWT HS256.** Access TTL 7 days, refresh TTL 30 days. **Refresh rotation enforced.**
- **bcrypt cost 10** for password hashing. Min password length 8 + must contain digit AND letter.
- **Per-account lockout:** 5 failed logins → 15-min cooldown.
- **Email verification** required before: starting trials, sending invoices/proposals.
- **No 2FA / MFA v1.** Scoped for v2.

JWT secret: hard-fails boot in production if missing OR equals dev fallback OR < 32 chars.

## CRITICAL: never use `X-User-Id` header for identity

**The `X-User-Id` "simplified auth" header has been removed (per F1.1).** Identity comes from `req.user` populated by `authenticateToken` middleware ONLY.

```typescript
// ✅ CORRECT
const profileId = (req as AuthenticatedRequest).user!.id;

// ❌ FORBIDDEN
const profileId = req.headers['x-user-id'] as string || 'anonymous';
```

If you find yourself adding a route that doesn't use `authenticateToken`, the only valid exemptions are:
- `/api/auth/{register,login,refresh}` (no auth yet by design)
- `/api/health` (public health check)
- `/p/:uuid` and `/i/:uuid` (public proposal/invoice pages — UUID is access control)
- `/webhooks/*` (provider signature verification, not JWT)

## Role authorization (C-02 Role Engine)

6 roles: `SOLO`, `TEAM_MEMBER`, `TEAM_LEAD`, `FOREMAN`, `ENTERPRISE`, `ADMIN`. 16 permissions. Map at compile-time in `auth.ts`.

Middleware:
- `authenticateToken` — populates `req.user`
- `requireRole(role)` — refuses if mismatch
- `requirePermission(perm)` — checks role's permission list
- `requireTier(min)` — tier minimum (per F2.2)
- `requireCap({...})` — per-tier cap enforcement (per F6.1)

**Audit on every PERMISSION_DENIED.**

## CORS lockdown (per F1.2)

```typescript
const ALLOWED_ORIGINS = [
  /^https:\/\/portal\.smithnet\.app$/,
  /^https:\/\/smithnet\.app$/,
  /^https:\/\/.*\.smithnet\.app$/,
  /^smithnet:\/\//,                   // Android scheme
];
```

Never use `origin: '*'` in production. Add `localhost` only when `NODE_ENV=development`.

## Zod validation at every endpoint

**Every authenticated POST/PATCH endpoint MUST use `validateBody(schema)` middleware.** Schemas live in `backend/src/schemas/`.

```typescript
// Use .strict() to reject unknown fields (mass-assignment defense)
const CreateJobBody = z.object({
  title: z.string().min(1).max(200),
  description: z.string().max(5000).optional(),
}).strict();

apiRouter.post('/jobs', validateBody(CreateJobBody), handler);
```

Validation error response shape:
```json
{ "error": "Validation failed", "code": "validation", "details": { ... } }
```

## Webhook security (per F3.1, F3.2)

- **Stripe:** verify `Stripe-Signature` header via `stripe.webhooks.constructEvent` with raw body. Refuse unsigned. Use `provider_webhook_events` table for idempotency (PK = `event.id`).
- **Play Billing:** verify Pub/Sub JWT in RTDN message via `google-auth-library`.
- Both write to `provider_webhook_events` for dedup.

## Mesh transport security (per F12.1)

- **AES-GCM-256** encryption with 96-bit IV
- **HMAC-SHA256** signing (encrypt-then-MAC)
- **Replay defender:** 5-min window, messageId dedup (LRU 10k entries)
- **Frame format:** `[version][messageId][timestamp][ciphertext][HMAC]`
- Pairing key from BLE bond; stored in Android Keystore
- All security failures emit `SECURITY.SECURITY_ALERT` audit

## Audit log (per F11.1)

- **Append-only DB table** with triggers blocking UPDATE / DELETE
- **Per-entry SHA256 checksum** for tamper detection (verifier cron weekly)
- **Retention policies enforced via daily cron**:
  - `auth` events: 90 days
  - `messaging` events: 30 days
  - `channel` events: 90 days
  - `admin` + `security`: 7 years (compliance — never auto-deleted)

`AuditAction` enum has 25+ event types. Use the right one — don't add new ones casually.

## Channel access control

Three visibility levels (`ChannelVisibility` enum):
- `public` — discoverable; auto-join on POST /messages
- `private` — visible only to `memberIds`; non-members get 403
- `restricted` — visible to all; join requires approval

`blockedUsers[]` overrides everything. `allowedUsers[]` for restricted. All access decisions audited.

## Cross-tenant isolation

Every query that joins jobs / messages / intents MUST scope by `created_by` OR `assigned_to` OR org membership. Per F10.3 `visibilityScope` helper. RLS on Supabase tables; service-layer checks on Hetzner tables.

## Crew data leakage rule (already enforced for AI; extend everywhere)

Solo users must never see crew data. `commit 4ce8733` enforced this in `AISupervisor`; the principle extends to ALL queries that touch `org_id` data. Extension via `buildJobVisibilityClause` and similar (per F10.3).

## OWASP Top 10 (2021) — what we cover

| # | Risk | Status |
|---|---|---|
| A01 | Broken Access Control | C-02 + tier middleware (F1.1, F6.1) |
| A02 | Cryptographic Failures | bcrypt 10, SHA256 audit/ledger, TLS 1.3, AES-GCM mesh |
| A03 | Injection | Parameterized `pg.query` everywhere, zod at boundaries |
| A04 | Insecure Design | Multi-authority validators, deterministic pipeline |
| A05 | Security Misconfiguration | Hard-fail dev secret in prod, CORS lockdown |
| A06 | Vulnerable Components | npm audit + Dependabot in CI |
| A07 | Identification & Auth Failures | bcrypt + JWT + rotation + lockout + email verification |
| A08 | Data Integrity Failures | SHA256 over ledger + audit; supersession chain |
| A09 | Security Logging | auditLog DB-backed (F11.1) with retention + tamper detection |
| A10 | SSRF | n/a (server doesn't fetch user-controlled URLs) |

## Public-page security (per F8.1, FLOW-8)

- UUID is access control (122-bit entropy via `replace(gen_random_uuid()::text, '-', '')`)
- Per-UUID rate limit: 60 views/min
- No PII in URL — UUID only
- HTTPS enforced via Tailscale Funnel
- `viewed_at` set on first view; not updated subsequently

## Rate limits (already configured)

- `/api/*` global: 300 req/min/IP
- `/api/auth/*`: 20 req/15min/IP
- `/api/synthesize` + `/api/ledger/seal` (planned tightening): 10 req/min/user
- `/i/:uuid` + `/p/:uuid`: 60/min/UUID
- New: per-user rate limit on `/api/me/start-trial`: 5/min

## Don't do

- ❌ Add `X-User-Id` header parsing in any new route
- ❌ Use `req.headers` for identity beyond debugging
- ❌ Set CORS `origin: '*'` in production code
- ❌ Skip `validateBody(...)` on a new POST/PATCH endpoint
- ❌ Use `JWT_SECRET || 'fallback'` pattern (hard-fail instead)
- ❌ Bypass webhook signature verification
- ❌ Add a route that mutates audit log entries
- ❌ Add a tier check only client-side (always server-side too)
- ❌ Spread `req.body` in a handler (whitelist explicitly per zod schema)
- ❌ Log full JWT tokens, passwords, or refresh tokens (mask if needed)
- ❌ Use `error` color for any interactive UI element except delete-dialog text
- ❌ Persist any field marked PII in `gate_hit_events.metadata` (uses `user_id_hash` only)

## When in doubt

If a security decision feels close-to-the-line, refer to:
- `docs/security/SECURITY.md` — full threat model + 14-item gap list (S1-S14)
- `docs/prds/F1.1` through `F1.5` — auth hardening PRDs
- `docs/prds/F6.1`, `F11.1`, `F12.1` — enforcement PRDs

## Linked specs

- `docs/security/SECURITY.md` — full security spec
- `docs/architecture/ARCHITECTURE.md §11, §13, §14` — auth flow + security boundary
- `docs/specs/NFRS.md §5` — NFR-S1 through S11
- `docs/prds/PRD-INDEX.md` — security PRDs across cycles
