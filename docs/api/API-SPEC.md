# Smith Net — API Specification

**Backend:** Hetzner-hosted Express (Node ≥ 18) + raw `pg` + `ws`. Behind Tailscale Funnel reverse proxy.
**Base URL (prod):** TBD (Tailscale Funnel hostname); set in Android via `BuildConfig.BACKEND_URL_PRIMARY`.
**Base URL (dev):** `http://localhost:3030`
**Auth model:** JWT (access 7d, refresh 30d). All `/api/*` endpoints (with documented exceptions) require `Authorization: Bearer <token>` middleware (`authenticateToken`).
**Rate limits:** 300 req/min/IP global; 20 req/15min/IP on `/api/auth/*`.
**CORS:** currently `*` — tighten for production.
**Content-Type:** `application/json` for request and response bodies (except media uploads).

> **F1.1 ✅ closed (2026-05-01):** legacy `X-User-Id` "simplified auth" header removed from all endpoints. `authenticateToken` middleware is mounted on `apiRouter` globally; identity comes from JWT only. Deprecation guard logs `[deprecated-header]` warnings if any client still sends the legacy headers.

---

## 1. Authentication (C-01)

### `POST /api/auth/register`
Body: `{ email, password, displayName }`
- Password ≥ 6 chars
- Default role: `solo`
- Default tier: `open` (post Step 11 — currently no tier column)

Response 201: `{ user: PublicUser, accessToken, refreshToken }`
Response 400: `{ error }`
Side effect: audit `USER_REGISTER`.

### `POST /api/auth/login`
Body: `{ email, password }`
Response 200: `{ user: PublicUser, accessToken, refreshToken }`
Response 401: `{ error: 'Invalid credentials' }` + audit `USER_LOGIN_FAILED`.
On success: audit `USER_LOGIN`.

### `POST /api/auth/refresh`
Body: `{ refreshToken }`
Response 200: `{ accessToken, refreshToken }` (refresh rotated).
Side effect: audit `TOKEN_REFRESH`.

### `GET /api/auth/me`
Header: `Authorization: Bearer <accessToken>`
Response 200: `PublicUser` (`{ id, email, displayName, role, tier?, ... }`)

### `PATCH /api/auth/me`
Body: partial `{ displayName?, phone?, trade?, hourly_rate? }`
Response 200: `PublicUser`. Side effect: audit `USER_PROFILE_UPDATE`.

### `POST /api/auth/logout`
Body: `{ refreshToken }` (to invalidate)
Response 204. Side effect: audit `USER_LOGOUT`.

---

## 2. Engagements

### `POST /api/engagements`
Body: `CreateEngagementRequest` `{ name, description?, clientName?, location?, intent }`
Response 201: `Engagement`

### `GET /api/engagements?status=active|converted|archived`
Response 200: `Engagement[]`

### `PATCH /api/engagements/:id`
Body: partial `Engagement`
Response 200: `Engagement`

### `POST /api/engagements/:id/convert`
Converts engagement → first Intent (auto-generates intent_version v1 in `draft`).
Response 200: `{ engagement, intent, intentVersion }`

---

## 3. Intent (the deterministic-pipeline entry — moat)

### `POST /api/intents`
Body: `{ scopeStatement, parties: string[], intendedJobIds?: string[] }`
Authority: `intentAuthority.validateIntentCreation` (scope non-empty, parties ≥ 1)
Response 201: `{ intent, version }` (version starts in `draft`)
Response 400: `{ error }`

### `POST /api/intents/:versionId/propose`
Transitions `draft` → `proposed`.
Response 200: `IntentVersion` with `status: 'proposed'`
Response 400: `{ error }` if not in `draft`.

### `POST /api/intents/:versionId/confirm`
Body: `{ confirmerId }` (must be one of `parties`)
Authority: `intentAuthority.validateIntentConfirmation`
Response 200: `IntentVersion` with `status: 'confirmed'`, `confirmed_at`, `confirmed_by`
Response 400/403: `{ error }`

### `POST /api/intents/:intentId/versions`
Creates a new version that supersedes the current confirmed version.
Body: `{ scopeStatement, parties, intendedJobIds? }`
Authority: `validateIntentVersion` (no cycles in supersession DAG)
Response 201: `IntentVersion` with `version_number = prev + 1`, `supersedes: prev.id`. Prior version's `superseded_by` is set.

### `POST /api/intents/auto-generate`
AI-assisted Intent draft from a free-text job brief.
Body: `{ jobBrief, suggestedParties? }`
Calls `llmInterface` (vendor-neutral). **AI fills draft fields only** — human still must `propose` and `confirm`.
Response 201: `{ intent, version }` with `auto_generated: true`.

### `GET /api/intents/:intentId`
Response 200: `{ intent, versions: IntentVersion[] }` (full history including superseded)

### `GET /api/intents?status=draft|proposed|confirmed&createdBy=:uid`
Response 200: `Intent[]` (with currentVersion expanded)

---

## 4. Synthesis (the synthesizer — produces Summary Artifact)

### `POST /api/synthesize`
Body: `{ intentVersionId, jobIds: string[], timeEntryIds: string[], chatMessageIds?: string[] }`
Preconditions (`synthesisAuthority.validateSynthesisInputs`):
- IntentVersion must be `confirmed`
- jobIds must reference closed jobs
- timeEntryIds must reference closed entries

Side effects:
1. Pulls `work_performed` from `tasks` of the listed jobs
2. Pulls `labor_recorded` from `time_entries`
3. Pulls `materials_used` from `materials` linked to jobs
4. Pulls `contextual_notes` from `messages` listed in `chatMessageIds`
5. Computes `total_hours`, `total_cost` (using `profiles.hourly_rate` per user)
6. Assigns next `serial` from `artifact_serial_sequence` (mig 004)
7. Inserts `summary_artifacts` row

Response 201: `SummaryArtifact`
Response 400: `{ error }`

### `GET /api/artifacts/:serial`
Response 200: `SummaryArtifact`

### `GET /api/artifacts?intentId=:id`
Response 200: `SummaryArtifact[]`

---

## 5. Ledger (the cryptographic seal)

### `POST /api/ledger/seal`
Body: `{ artifactId, actorUuid }`
Authority: `ledgerAuthority.validateSealing` (artifact valid + not already sealed)
Computes `sha256_hash` via `computeHash(artifact)`.
Inserts `ledger_entries` row.
Response 201: `LedgerEntry`
Response 400: `{ error }`

### `POST /api/ledger/amend`
Body: `{ newArtifactId, priorEntryId, actorUuid }`
Authority: `ledgerAuthority.validateAmendment` (prior not already superseded)
Side effect: prior entry's `superseded_by` is set to new entry's id.
Response 201: `LedgerEntry` with `supersedes: priorEntryId`

### `GET /api/ledger/:entryId`
Response 200: `LedgerEntry`
Response 404: `{ error }`

### `GET /api/ledger/artifact/:serial/latest`
Returns the latest non-superseded ledger entry for an artifact serial.
Response 200: `LedgerEntry | null`

### `GET /api/ledger/artifact/:serial/chain`
Full supersession chain (oldest → newest).
Response 200: `LedgerEntry[]`

### `GET /api/ledger/verify/:entryId`
Re-computes the hash from the current `summary_artifacts` row and compares to the stored `sha256_hash`. **Tamper detection.**
Response 200: `{ entryId, expected, actual, valid: boolean }`

---

## 6. Jobs

### `POST /api/jobs`
Body: `{ title, description?, clientName?, ..., status?: 'todo' }`
Tier-gate: Free tier — refuse if user already has 1 active (non-archived) job. Server returns:
```
HTTP 403 { error: 'tier_gate_exceeded', gate_id: 'active_job_cap', current_tier: 'open', limit: 1, current: 1 }
```
Response 201: `Job`

### `GET /api/jobs?status=...&assignedTo=:uid`
Response 200: `Job[]`

### `GET /api/jobs/:id`
Response 200: `Job` (with `tasks`, `time_entries`, `materials` expanded)

### `PATCH /api/jobs/:id`
Body: partial `Job`
Tier check on status change to `in_progress` (counts as "active").

### `POST /api/jobs/:id/close`
Sets `status: 'done'`, `completed_at: NOW()`. Frees up an active-job slot for Free tier users.

### `POST /api/jobs/:id/archive`
Sets `status: 'archived'`. Cannot be re-opened.

---

## 7. Tasks (sub-items)

### `POST /api/jobs/:jobId/tasks`
Body: `{ title, description?, assignedTo? }`

### `PATCH /api/tasks/:id`
Body: partial `Task`. Setting `status: 'done'` triggers `completed_at`.

### `DELETE /api/tasks/:id`

---

## 8. Time Entries

### `POST /api/time-entries`
Body: `{ jobId, durationMinutes, startedAt?, endedAt? }`
Response 201: `TimeEntry`

### `GET /api/time-entries?jobId=:id&userId=:uid`
Response 200: `TimeEntry[]`

### `PATCH /api/time-entries/:id`

### `DELETE /api/time-entries/:id`
Soft delete only; entries referenced by sealed artifacts cannot be deleted.

---

## 9. Channels & Messages (C-03 boundary)

### `POST /api/channels`
Body: `CreateChannelRequest` `{ name, type: 'broadcast'|'group'|'dm', visibility?, memberIds?, requiresApproval? }`
Side effect: WS broadcast `channel_created`; refresh subscriptions for affected members.
Response 201: `Channel`

### `GET /api/channels`
Response 200: `Channel[]` (filtered by membership / visibility)

### `PATCH /api/channels/:id/visibility`
Body: `UpdateChannelVisibilityPayload` `{ visibility, requiresApproval? }`
Response 200: `Channel`

### `POST /api/channels/:id/access/request`
For `restricted` channels — request access.
Body: `AccessRequestPayload`
Response 200: `{ status: 'pending' }`

### `POST /api/channels/:id/access/respond`
Channel owner approves/denies request.
Body: `AccessResponsePayload` `{ requesterId, approve: boolean }`

### `PATCH /api/channels/:id/access`
Body: `UpdateChannelAccessPayload` `{ userId, allow: boolean }`
Owner adds/removes from allowed/blocked lists.

### `DELETE /api/channels/:id`
Soft delete (`is_deleted = true`). Cascades local-only message clears via WS event `channel_deleted`.

### `POST /api/channels/:id/clear`
Wipes all messages in the channel (server + broadcasts to clients to clear local).
Side effect: WS `channel_cleared` event with timestamp.

### `POST /api/channels/:channelId/messages`
Body: `InjectMessageRequest` `{ content, origin: MessageOrigin }`
Response 201: `Message`. Server publishes to `messageBus` + broadcasts via WS.

### `GET /api/channels/:channelId/messages?before=:ts&limit=:n`
Response 200: `Message[]`

### `DELETE /api/messages/:id`
Soft delete (`is_deleted = true`).

---

## 10. Reconciliation (online ↔ offline sync)

### `POST /api/reconcile`
Body: `ReconciliationRequest` `{ channelId, localMessageIds: string[], localClock: VectorClockState }`
Server:
1. Loads server messages for channel
2. Returns `missingOnClient` (messages server has but client doesn't)
3. Returns `missingOnServer` (ids client claims but server doesn't have)
4. Merges vector clocks

Response 200: `ReconciliationResponse` `{ missingOnClient, missingOnServer, mergedClock }`

### `POST /api/reconcile/upload`
Body: `{ messages: UnifiedMessage[] }` (the `missingOnServer` from prior reconcile)
Side effect: `acceptClientMessages` — `INSERT ... ON CONFLICT (id) DO UPDATE`.
Response 204.

---

## 11. Media (uploads + serving)

### `POST /api/media/upload`
Multipart form-data: `file` (multer)
Optional: `messageId` to associate with a message.
Response 201: `{ url, filename, size, mediaType }`
Audit: `MESSAGE_MEDIA_UPLOAD`

### Static serving
- `GET /media/images/:filename`
- `GET /media/voice/:filename`
- `GET /media/files/:filename`

### Background cleanup
`cleanupOldMedia` runs periodically — deletes orphaned uploads.

---

## 12. Proposals & Invoices

### `POST /api/proposals`
Body: `CreateProposalBody` (fields per `proposals` table)
Response 201: `Proposal` (with public `uuid` URL slug)

### `GET /api/proposals?jobId=:id&status=...`
Response 200: `Proposal[]`

### `PATCH /api/proposals/:id`

### `POST /api/proposals/:id/send`
Generates HTML/PDF + queues email to `client_email`. Free tier: stamps Smith Net branding on PDF + email signature; counts toward 5/mo PDF send cap.

### `POST /api/invoices`
Body: `{ planId | intentId, lineItems, subtotal, tax, total, dueDate, template: 'standard'|'advanced'|'enterprise' }`
Tier-gate: `template: 'advanced'` → Advanced+; `template: 'enterprise'` → Enterprise.

### `GET /api/invoices?status=draft|sent|paid|overdue`

### `POST /api/invoices/:id/send`
Generates PDF (template-specific) + emails. Free-tier branding stamp + 5/mo PDF cap as proposals.

---

## 13. Public-facing pages (no auth)

### `GET /p/:uuid` — Proposal public page
Server-rendered HTML; uses `templates/proposal.html`. Records `viewed_at` on first load.

### `GET /i/:uuid` — Invoice public page
Server-rendered HTML; uses `templates/invoice.html`. Records `viewed_at` on first load.

### `GET /api/health`
Response 200: `{ status: 'ok', version, ts }`. Excluded from rate limit.

---

## 14. Reports

### `POST /api/reports`
Body: `{ planId | intentId, title, content, totalHours? }`

### `GET /api/reports?planId=...`

### `POST /api/reports/:id/render`
Renders narrative HTML/PDF.

---

## 15. Tier resolution (Step 11 to add)

### `GET /api/me/entitlements`
Response 200:
```
{
  tier: 'open' | 'solo' | 'advanced' | 'enterprise',
  trialing: boolean,
  trialEndsAt?: number,
  founderPricingLocked: boolean,
  caps: {
    activeJobs: number | 'unlimited',
    pdfSendsPerMonth: number | 'unlimited',
    advancedTemplate: boolean,
    enterpriseTemplate: boolean,
    smithAI: boolean,
    crew: boolean
  },
  founderSeatsRemaining: { solo: number, advanced: number, enterprise: number }
}
```

### `POST /api/me/upgrade`
Body: `{ targetTier, cadence: 'monthly'|'annual', paymentMethodToken }` (Stripe / Play Billing token)
Triggers Stripe / Play Billing subscription creation. Server reconciles with `subscriptions` table on webhook.

### `POST /api/me/cancel`
Cancels subscription at end of current period (no proration).

### `POST /api/me/start-trial`
Body: `{ targetTier: 'solo'|'advanced' }` (no CC required)
Sets `trial_started_at`, `trial_tier`. Locks founder pricing if seats remain.

### `POST /webhooks/stripe`
Stripe webhook handler — updates `subscriptions` on `checkout.session.completed`, `invoice.paid`, `customer.subscription.deleted`.

### `POST /webhooks/play-billing`
Play Billing real-time developer notification handler.

---

## 16. Telemetry

### `POST /api/telemetry/gate-hit`
Body: `{ event, currentTier, metadata? }`
Server adds `user_id_hash` (SHA256 of profile.id), inserts `gate_hit_events` row.
Used for tier-gate analytics (per SUCCESS-METRICS.md).

---

## 17. Admin (`/api/admin/*`, requires `ADMIN` role)

Routes mounted via `adminRouter`. Examples:
- `POST /api/admin/users/:id/role` — change role (audit `USER_ROLE_CHANGE`)
- `POST /api/admin/data/export` — export user data (audit `DATA_EXPORT`)
- `POST /api/admin/data/purge` — purge all-but-admin (audit `DATA_PURGE`; preserves seed admin)

---

## 18. WebSocket Protocol

**Endpoint:** `ws://<host>/ws` (or `wss://` in prod)
**Auth:** first message after connect: `{ type: 'auth', payload: { token: <accessToken> } }`. Server replies `auth_ok` or `auth_error`.

### Message types (`WSMessageType`)

| Type | Direction | Payload |
|---|---|---|
| `auth` | C→S | `{ token }` |
| `auth_ok` | S→C | `{ user: PublicUser }` |
| `auth_error` | S→C | `{ error }` |
| `message` | C→S, S→C | `Message` |
| `message_ack` | S→C | `{ messageId }` |
| `message_deleted` | S→C | `{ messageId }` |
| `channel_created` | S→C | `Channel` |
| `channel_updated` | S→C | `Channel` |
| `channel_deleted` | S→C | `{ channelId }` |
| `channel_cleared` | S→C | `{ channelId, clearedAt }` |
| `channel_subscribed` | S→C | `{ channelId }` |
| `channels_updated` | S→C | `Channel[]` |
| `presence_update` | S→C | `Presence` |
| `gateway_connect` | S→C | `GatewayRelay` |
| `gateway_disconnect` | S→C | `{ relayId }` |
| `gateway_message` | S→C | `Message` (origin = `gateway`) |
| `message_read` | C→S | `{ messageId }` |
| `typing_start` / `typing_stop` | C→S, S→C | `{ channelId }` |
| `error` | S→C | `{ code, message }` |

**All WS messages are wrapped:**
```
{
  type: WSMessageType,
  payload: unknown,
  timestamp: number  // epoch ms
}
```

---

## 19. Error format (REST)

```
{
  "error": "human-readable message",
  "code": "machine_code",     // optional
  "details": { ... }          // optional, schema per code
}
```

Standard codes:
- `unauthenticated` (401)
- `forbidden` (403)
- `not_found` (404)
- `validation` (400 — payload doesn't match schema)
- `tier_gate_exceeded` (403 — see Jobs / Invoices examples; includes `gate_id`, `limit`, `current`, `current_tier`)
- `rate_limit_exceeded` (429)
- `conflict` (409 — e.g., supersession cycle)
- `server_error` (500)

---

## 20. Headers

| Header | Purpose |
|---|---|
| `Authorization: Bearer <accessToken>` | required on all `/api/*` except `/api/auth/{register,login,refresh}` and `/api/health` (also required on `/api/admin/*` + `/api/media/*`). Server enforces via `authenticateToken` middleware mounted on `apiRouter`. |
| `X-Forwarded-For` | trusted (proxy `trust proxy = 1`); used for rate-limit bucketing |
| `X-Request-Id` (planned) | correlation id for tracing |

> **F1.1 status (2026-05-01):** legacy `X-User-Id` and `X-User-Name` headers REMOVED from CORS allowedHeaders and from all backend handlers. A deprecation guard logs `[deprecated-header]` warnings when any client still sends them. Headers are ignored — identity comes from JWT only. After one release window with zero `[deprecated-header]` log lines, delete the deprecation guard middleware.

---

## 21. Versioning

**v1 strategy:** no version in path (current). When v2 ships:
- v1 endpoints remain at `/api/*` (frozen)
- v2 endpoints at `/api/v2/*`
- Breaking changes go in v2

**SemVer for the contract:** documented in repo's `CHANGELOG.md` (one entry per API change).

---

## 22. Deferred / planned endpoints

| # | Endpoint | When | Why |
|---|---|---|---|
| D1 | `/api/me/entitlements`, `/api/me/upgrade`, `/api/me/cancel`, `/api/me/start-trial` | Step 11 | Tier resolver — v1-launch blocker |
| D2 | `/webhooks/stripe`, `/webhooks/play-billing` | Step 11 | Subscription state sync |
| D3 | `/api/telemetry/gate-hit` | Step 11 | Funnel analytics |
| D4 | `/api/trade-packs/electrician/*` (and others) | Step 11+ | Per-trade extension routes |
| D5 | `/api/admin/founder-seats` | Step 11 | View / mint founder-seat caps |
| D6 | `/api/audit/*` | Step 11 | Query audit log (currently file-only) |
