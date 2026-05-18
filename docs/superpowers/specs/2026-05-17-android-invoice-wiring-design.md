# Android Invoice Wiring Design

**Date:** 2026-05-17
**Status:** Approved for plan
**Branch:** feat/relay-hetzner-postgres
**Follows:** commit 1f93bfc (backend /api/invoices CRUD + portal route)

## Context

The backend gained real invoice persistence on 1f93bfc — migration 017 + `invoicesRouter` + `/console/invoices` route in the portal. The Android apk was deliberately left out of that slice: it still generates invoices in-memory via `JobBoardViewModel.generateInvoice(job)` → `InvoiceGenerator.generateFromJob(...)`, holds the result in a `MutableStateFlow<Invoice?>`, renders to text/PDF/HTML on demand, and forgets it as soon as the preview dialog closes. Nothing is locally persisted; nothing reaches the backend.

The Android `Invoice` type carries 50 fields (`android/app/src/main/java/com/guildofsmiths/trademesh/ui/invoice/InvoiceTypes.kt`), most of them derived from the source `Job` + `TimeEntry` data (crew hours, daily breakdown, mesh presence, photo/voice counts, work log summary, efficiency score, compliance notes, recommendations, payment instructions). The backend invoice schema is the deliberately small 13-field subset committed in 017.

## Goal

Persist every invoice the user actually generates on Android, so it stops dying with the preview dialog. A foreman or solo trades-person should be able to look at the backend later (via the web portal, or via a future apk read-back screen, or via direct DB query) and see exactly which invoices they pushed out, with enough detail to reconstruct the rich apk-side rendering.

Out of scope:
- Bidirectional sync (the apk does not pull invoices created elsewhere).
- A read-back / list / detail screen on the apk (no new Android UI).
- Backfill of historical invoices that were generated before this slice ships (they were never persisted, so there is nothing to backfill).
- Cross-device "see the same invoices on web and phone" parity (a follow-up slice).
- Re-architecting `InvoiceGenerator` or the rendering pipeline.

## Decisions (locked during brainstorm 2026-05-17)

1. **Primary goal:** "Just stop losing them" — paper trail only. No new apk screens.
2. **Tier rule:** Open `/api/invoices` to solo users too. Drop `requireConsoleTier` from `invoicesRouter`. Web's UI-level `RequireForemanTier` on `/console/invoices` is left alone — orthogonal.
3. **Fidelity:** Backend gains a `summary jsonb` column. Apk pushes the structured 13 fields AND the full 50-field invoice as JSON in `summary`. Backend treats it as opaque storage.
4. **Push timing:** On Generate (every apk tap of "Generate invoice"), with auto-delete if the preview dismisses without Share. Share is the explicit "I used this" signal.
5. **Offline behavior:** Local Room outbox table drained by a WorkManager `CoroutineWorker`. Mirrors the backend's `background_jobs` pattern in spirit. Preview always works regardless of network; queue drains when network returns.
6. **Idempotency:** Apk-generated UUID doubles as the idempotency key. Backend stores it in `invoices.idempotency_key` with `UNIQUE (organization_id, idempotency_key)`. Replayed POSTs return the existing row.

## Architecture

```
JobBoardViewModel.generateInvoice(job)
  builds Invoice (unchanged generator code)
  sets _generatedInvoice                        ← preview opens, as today
  + invoicesOutbox.enqueueCreate(localId, payload)   NEW

JobBoardScreen.onShare(text)
  + viewModel.markShared(localId)               NEW
  + invoicesOutbox.enqueueMarkSent(localId)
  fires Android share intent (unchanged)

JobBoardScreen.onDismiss without share
  + invoicesOutbox.enqueueDiscard(localId)      NEW
  clears preview (unchanged)

InvoicesOutbox (Room)
  table pending_invoice_pushes
    id              TEXT PRIMARY KEY            (client UUID, also idempotency key for op=CREATE)
    local_invoice_id TEXT NOT NULL              (the apk Invoice.id; same value as id for CREATE rows; for MARK_SENT/DISCARD it's the apk invoice the op refers to)
    op              TEXT NOT NULL               ('CREATE' | 'MARK_SENT' | 'DISCARD')
    payload_json    TEXT                        (full 50-field Invoice JSON; only for CREATE)
    backend_id      TEXT                        (server invoice UUID; written after CREATE succeeds)
    status          TEXT NOT NULL DEFAULT 'pending'   ('pending' | 'in_flight' | 'done' | 'failed' | 'cancelled')
    attempts        INTEGER NOT NULL DEFAULT 0
    last_error      TEXT
    created_at      INTEGER NOT NULL            (epoch millis)
    updated_at      INTEGER NOT NULL

  index (status, created_at) for the worker's "next pending op" scan.

InvoicesPushWorker (CoroutineWorker)
  constraint: NetworkType.CONNECTED
  policy: backoff exponential (10s base, 30m max), max 20 attempts
  loop (single worker run drains oldest-first):
    pop the next op by atomic UPDATE pending → in_flight
    if op is dependent (MARK_SENT/DISCARD) and the CREATE row's status is
      pending or in_flight in another transaction (shouldn't happen given
      serial drain, but defensive): skip (leave row pending), keep going
    if op's CREATE prerequisite is failed: mark this op cancelled, keep going
    call InvoicesApiClient.execute(op)
    classify result:
      2xx        → mark op done (for CREATE, also write backend_id)
      transient  → revert to pending, bump attempts; if attempts < 20
                   return Result.retry() and bail out of the loop
      4xx        → mark op failed, last_error populated, keep going
  The atomic pending → in_flight transition is what makes enqueueDiscard
  race-safe: if a user cancels while the worker is mid-flight, the
  outbox sees status=in_flight (not pending) and inserts a real DISCARD
  row instead of mutating CREATE. The worker finishes its POST, writes
  backend_id, marks CREATE done; the DISCARD row then drains and fires
  DELETE against the backend_id.

InvoicesApiClient (OkHttp; follows PresenceApiClient shape)
  POST   /api/invoices                    → returns server invoice (backend_id, server invoice number)
  POST   /api/invoices/{id}/line-items   (one call per line item)
  PATCH  /api/invoices/{id}/status        { status: "sent" }
  DELETE /api/invoices/{id}

Backend deltas (migration 018)
  ALTER TABLE invoices
    ADD COLUMN summary         JSONB,
    ADD COLUMN idempotency_key TEXT;
  CREATE UNIQUE INDEX invoices_org_idem_unique
    ON invoices (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
  (drop tier gate)
  invoicesRouter no longer calls .use(requireConsoleTier).
  POST handler accepts optional idempotencyKey + summary; if a row with
  the same (organization_id, idempotency_key) exists, return it
  (200) instead of inserting a duplicate.
```

Boundary check: the outbox is the only component that knows about the network. The ViewModel doesn't grow new responsibilities — it just calls into the outbox after building the invoice. `InvoicesApiClient` is a thin HTTP layer. The worker owns retry. Each unit has one clear job and can be tested independently with fakes.

## Data flow

### Generate

1. User taps "Generate invoice" on a job row in `JobBoardScreen`.
2. `JobBoardViewModel.generateInvoice(job)` runs (today's code), builds the `Invoice` object, sets `_generatedInvoice.value = invoice`. Preview dialog opens.
3. ViewModel calls `invoicesOutbox.enqueueCreate(localId = invoice.id, payload = invoice)`.
4. Outbox inserts a row `(id=localId, op='CREATE', payload_json=<invoice as JSON>, status='pending', attempts=0)`.
5. Outbox calls `WorkManager.enqueueUniqueWork("invoices-push", ExistingWorkPolicy.KEEP, OneTimeWorkRequest<InvoicesPushWorker>(...))`. If a worker is already queued, it picks up the new row on its next iteration.
6. Worker, when scheduled and online, pops the CREATE row, calls `apiClient.createInvoice(payload)`.
7. ApiClient POSTs `/api/invoices` with body:
   ```
   {
     "idempotencyKey": "<localId>",
     "clientName":     "<payload.toName>",
     "clientEmail":    "<payload.toEmail>",
     "dueDate":        "<ISO from payload.dueDate>",
     "notes":          "<payload.notes>",
     "summary":        <full Invoice as JSON>
   }
   ```
8. Backend looks up `(organization_id, idempotency_key)`:
   - Match exists → return that row, status 200.
   - No match → insert new invoice row + line_items in one transaction, return row, status 201.
9. ApiClient then POSTs each line item to `/api/invoices/{backend_id}/line-items` with the apk's structured line items (description, quantity, unit, rate, category). Backend re-derives subtotal/tax/total in the same handler (existing behavior from 1f93bfc).
10. Worker writes `backend_id` onto the outbox row and marks `status='done'`.

### Share

1. User taps Share in the preview dialog.
2. `JobBoardScreen.onShare` callback fires.
3. ViewModel calls `invoicesOutbox.enqueueMarkSent(localId)`.
4. Outbox inserts a row `(id=<new UUID>, op='MARK_SENT', local_invoice_id=localId, status='pending')`. The MARK_SENT row's prerequisite is the CREATE row for the same `local_invoice_id`.
5. Standard Android share intent fires (unchanged from today's code). The Share sheet picks email / Drive / Telegram / etc. — that's outside the scope of this slice.
6. ViewModel marks the in-memory invoice as `shared=true` so the `clearInvoice()` call that follows does not also enqueue a DISCARD.
7. Worker drains oldest-first within a single run, so CREATE (inserted at step 4 of the Generate flow) is processed before MARK_SENT (inserted at step 4 here). After CREATE completes in the same drain pass, MARK_SENT is next and finds `backend_id` set; ApiClient calls `PATCH /api/invoices/{backend_id}/status { "status": "sent" }`. If MARK_SENT is somehow popped while CREATE is still `pending` or `in_flight` (defensive — shouldn't happen given serial drain), the worker logs and leaves MARK_SENT `pending`; the next worker run retries. If CREATE has already failed, MARK_SENT is marked `cancelled`.

### Cancel (dismiss without share)

1. User taps outside the dialog or hits back without sharing.
2. `JobBoardScreen.onDismiss` fires. If `shared` is false, ViewModel calls `invoicesOutbox.enqueueDiscard(localId)`.
3. Outbox runs an atomic check-and-act against the CREATE row's `status`:
   - `pending` (worker has not popped it yet) → flip status to `cancelled`. No DISCARD row inserted; no network call ever happens. Done.
   - `in_flight` (worker is mid-POST) → insert DISCARD row `(op='DISCARD', local_invoice_id=localId, status='pending')`. The worker will finish its POST, write `backend_id`, mark CREATE `done`; the DISCARD row then drains and fires `DELETE /api/invoices/{backend_id}`.
   - `done` (backend_id known) → insert DISCARD row. Worker fires DELETE on next iteration.
   - `failed` → no server-side row exists, so nothing to delete. Do nothing; the CREATE row's `failed` state already records that nothing landed.
   - `cancelled` → DISCARD is redundant (the worker never ran). Do nothing.

### Idempotency

POST is retry-safe. The apk always sends the same `idempotencyKey` (the local invoice UUID, durable in the outbox row's `id`). If a POST succeeds server-side but the response is lost to the network (worker killed mid-response, mobile radio drop), the next retry hits the same unique key, the backend returns the existing row, and the worker writes the backend_id and moves on. No duplicate.

### Ordering

Outbox drain is sequential by `created_at`. MARK_SENT and DISCARD are dependent on the CREATE row with the matching `local_invoice_id`. The worker checks the prerequisite before popping a dependent op; if the prerequisite isn't `done` yet, the worker either (a) drains the prerequisite first if it's also pending and earlier in the queue (the natural order), or (b) requeues the dependent op and lets the next iteration retry. This prevents `PATCH 404` because PATCH ran before CREATE finished.

## Field mapping

The apk's 50-field `Invoice` maps to 13 columns on the backend plus the `summary jsonb` carrier.

| Android field | Backend destination | Notes |
|---|---|---|
| `id` | `invoices.idempotency_key` | Doubles as idempotency key |
| `invoiceNumber` | DROPPED | Backend mints its own `INV-YYYY-NNNN` via `nextInvoiceNumber()` from 1f93bfc |
| `issueDate` (Long ms) | `invoices.issue_date` | Convert to ISO TIMESTAMPTZ at ApiClient |
| `dueDate` (Long ms) | `invoices.due_date` | Convert to ISO |
| `status` | `invoices.status` | Always `'draft'` on POST; MARK_SENT flips to `'sent'` |
| `mode` (SOLO\|ENTERPRISE) | `summary.mode` | Backend doesn't model modes |
| `fromName`, `fromBusiness`, `fromTrade`, `fromPhone`, `fromEmail`, `fromAddress` | `summary.from.{...}` | Backend has no provider columns |
| `toName` | `invoices.client_name` | |
| `toEmail` | `invoices.client_email` | |
| `toCompany`, `toAddress` | `summary.to.{company,address}` | |
| `projectRef`, `poNumber` | `summary.{projectRef,poNumber}` | |
| `projectStart`, `projectEnd`, `workingDays` | `summary.project.{start,end,workingDays}` | |
| `crew[]` (`CrewMemberHours`) | `summary.crew[]` | |
| `totalCrewHours` | `summary.totalCrewHours` | |
| `dailyBreakdown[]` (`DailyWorkSummary`) | `summary.dailyBreakdown[]` | |
| `lineItems[]` (`InvoiceLineItem`) | `invoice_line_items` rows | One POST per item to `/api/invoices/{id}/line-items` |
| `lineItems[].code` | `summary.lineItems[i].code` | Backend table has no `code` column; preserved in jsonb only |
| `lineItems[].description`, `quantity`, `unit`, `rate`, `total`, `category` | `invoice_line_items.{...}` | 1:1 mapping; `category` enum is lowercased (LABOR → `'labor'`) |
| `subtotal`, `taxRate`, `taxAmount`, `totalDue` | `summary.computed.{...}` | Backend recomputes from line items; apk values stashed for drift detection |
| `jobId`, `jobTitle` | `summary.job.{id,title}` | |
| `workWindow` | `summary.workWindow` | |
| `totalOnSiteMinutes` | `summary.totalOnSiteMinutes` | |
| `photoCount`, `voiceNoteCount`, `checklistCount` | `summary.media.{photos,voice,checklist}` | |
| `workLogSummary` | `summary.workLogSummary` | |
| `complianceNotes`, `recommendations` | `summary.{complianceNotes,recommendations}` | |
| `meshPresence` | `summary.meshPresence` | |
| `efficiencyScore` | `summary.efficiencyScore` | |
| `paymentInstructions` | `summary.paymentInstructions` | Kept separate from `notes` so the structured TEXT column stays free-form |
| `notes` | `invoices.notes` | |

### Unit conversions at the ApiClient boundary

- **Tax rate.** Android: `8.25` (percent). Backend: `0.0825` (fraction). ApiClient divides by 100 on POST. (Apk side of the boundary remains percent for display consistency; only the wire format changes.)
- **Money.** Android: `Double`. Backend: `NUMERIC(12,2)`. Serialize via `BigDecimal.setScale(2, RoundingMode.HALF_UP).toPlainString()` so the float→numeric path doesn't introduce `0.06999999`.
- **Dates.** Android: epoch millis. Backend: `TIMESTAMPTZ` accepting ISO 8601. ApiClient formats via `Instant.ofEpochMilli(...).toString()`.
- **Status enum.** Android: `InvoiceStatus.DRAFT` (UPPERCASE). Backend: `'draft'` (lowercase). ApiClient lowercases.
- **Category enum.** Same lowercasing rule.

### Invoice number reconciliation

The apk-generated `invoiceNumber` (e.g. `INV-2025-12-0789-CREW-WEEK`) is **not** sent to the backend and is **not** stored server-side. The backend mints its own number on POST. The apk preview shows the apk-generated number until the CREATE op completes; once `backend_id` and the server number are known, the outbox row stores the server number, and any later UI that displays the invoice (none in v1) uses the server number.

**Known consequence.** If the user shares the invoice while the preview is open and the network is still down, the shared PDF carries the apk-generated number, which won't match the backend's number assigned later. For "just stop losing them," this is acceptable. A future slice that builds an apk read-back screen will need to surface the server number prominently.

## Error handling

| Failure | Worker action | User-visible signal |
|---|---|---|
| Network down / DNS / timeout / connection reset | `Result.retry()` — WorkManager schedules backoff (10s, 30s, 1m, 5m, 30m cap) | None. Preview already works. |
| 5xx server | `Result.retry()` — same backoff | None |
| 401 unauthorized | Outbox row stays `pending`. Worker emits one log line, returns `Result.retry()` with long backoff. The apk's existing auth refresh path (used today by `PresenceApiClient`) reauthenticates on next foreground. No invoice-specific re-auth flow added. | None |
| 409 idempotency hit (POST replayed after success-but-network-lost) | Backend returns 200 + existing row. Worker treats as success, writes `backend_id`, marks `status='done'`. Indistinguishable from first-try success. | None |
| 4xx validation (400, 422) | Mark row `status='failed'`. Attempts frozen. `last_error` populated. No further retry. Crash/error sink logs the body. | None in v1. (Foreground "sync issues" indicator is a future slice — see Out of scope.) |
| 403 forbidden | Same as 4xx. Should not happen once tier gate is dropped. | None |
| DELETE returns 404 | Mark DISCARD row `done`. Idempotent — the row is gone either way. | None |
| MARK_SENT before CREATE has `backend_id` | Worker requeues MARK_SENT. Backoff prevents tight-loop. | None |
| Worker exceeds 20 attempts | Mark row `status='failed'`. `last_error` populated. | None in v1. |

**Queue cap:** none in v1. A foreman generates on the order of 5 invoices/day; if telemetry later shows pending counts above ~100 per device, revisit.

## Testing

### Backend tests

`backend/src/__tests__/invoices-routes-android.test.ts` (new):

1. Migration 018 adds `summary jsonb` + `idempotency_key text` with `UNIQUE (organization_id, idempotency_key) WHERE idempotency_key IS NOT NULL`. Re-running the migration is idempotent.
2. `POST /api/invoices` with `idempotencyKey`, called twice with identical payload → returns the same row both times, no duplicate, invoice number sequence not advanced.
3. `POST /api/invoices` with `idempotencyKey`, called twice with different payload → second call still returns the first row (idempotency wins; payload diff is silently ignored in v1).
4. `POST /api/invoices` with `summary` jsonb → row round-trips; `GET /api/invoices/:id` returns identical jsonb (deep equality).
5. Solo user (no `requireConsoleTier`) `POST /api/invoices` succeeds with status 201.
6. Solo user GET → only sees their own org-of-one's invoices.
7. Existing 11 invoices-routes tests from 1f93bfc still pass (sanity check — migration shouldn't break them).

### Android tests

`android/app/src/test/java/com/guildofsmiths/trademesh/data/invoice/InvoicesOutboxTest.kt` (new):

1. `outbox.enqueueCreate(localId, payload)` inserts a `pending` row with `op='CREATE'`, populates `payload_json`.
2. Worker drain: fake ApiClient returns 201 → row marked `done`, `backend_id` populated.
3. Worker drain: fake ApiClient returns 500 → row stays `pending`, `attempts` incremented, no exception thrown out of the worker.
4. Worker drain: fake ApiClient returns 422 → row marked `failed`, `last_error` populated, no further retries on subsequent drain calls.
5. `enqueueDiscard` before CREATE drains → CREATE row marked `cancelled`, no POST attempted (fake ApiClient observes zero calls).
6. `enqueueDiscard` after CREATE done → DELETE row inserted, worker fires DELETE against `backend_id`.
7. `enqueueMarkSent` with CREATE still pending → MARK_SENT row stays `pending` across drain iterations until CREATE has `backend_id`, then PATCH fires.
8. Idempotency: worker mid-POST is killed (simulated by throwing from fake client after the network call but before the response is parsed), restart, replay → fake ApiClient sees two POSTs with the same `idempotencyKey`, backend (fake) returns 200 the second time, outbox ends with exactly one `done` row.
9. Race: directly set a CREATE row's `status='in_flight'` via the DAO (simulating a worker mid-POST), call `outbox.enqueueDiscard(localId)`. Expect: CREATE row stays `in_flight` (not mutated to `cancelled`); a DISCARD row is inserted with `status='pending'`. Then complete the CREATE (DAO update to `done`, set `backend_id`), drain the worker — expect DELETE called against `backend_id`.

`android/app/src/androidTest/java/com/guildofsmiths/trademesh/data/invoice/InvoicesPushE2ETest.kt` (new, instrumented):

1. End-to-end against a real test backend instance: generate → share → mark_sent. Row exists in db with `status='sent'`, `summary` jsonb matches the apk payload, `line_items` count and totals match.

### Out of scope tests (deliberately not added)

- Apk read-back screen (no UI added in this slice).
- Cross-device sync (no UI added).
- WorkManager exact-timing tests — we trust the framework. Outbox unit tests use a direct worker invocation (`InvoicesPushWorker.doWork()` against a fake `ApiClient`); they do not schedule through WorkManager.

## Loose ends and known caveats

1. **Apk-generated invoice number is thrown away.** Backend mints its own. If the user shares before reconnect, the shared PDF carries the apk number, which won't match the backend's number assigned later. Acceptable for "just stop losing them" but worth surfacing if/when an apk read-back screen is built.
2. **4xx-failed branch is silent in v1.** If the backend rejects (validation rule we forgot, schema drift), the user has no way to know their paper trail didn't land. Foreground "sync issues" indicator is a future slice.
3. **No backfill.** Devices that generated invoices before this slice ships have nothing to push — those invoices were never persisted on-device either, so they are already lost. New behavior starts at install/upgrade.

## File touchpoints

### Backend (new + modified)

- `backend/migrations/018_invoices_android_wiring.sql` (new) — adds `summary jsonb`, `idempotency_key text`, the partial unique index.
- `backend/src/invoicesRoutes.ts` (modify) — drop `requireConsoleTier`. Accept optional `idempotencyKey` and `summary` on POST. Idempotency lookup before insert.
- `backend/src/invoicesService.ts` (modify) — `create()` accepts `idempotencyKey?` and `summary?`; on conflict by `(organization_id, idempotency_key)`, return existing row rather than throw. `getByIdScoped()` returns `summary` and `idempotencyKey` in the projection.
- `backend/src/schemas/invoices.ts` (modify) — `CreateInvoiceBody` accepts optional `idempotencyKey: string` and `summary: unknown` (zod `.passthrough()` on summary; we treat it opaque).
- `backend/src/__tests__/invoices-routes-android.test.ts` (new) — the 7 backend tests listed above.

### Android (new + modified)

- `android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesOutbox.kt` (new) — Room database, DAO, entity for `pending_invoice_pushes`.
- `android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesApiClient.kt` (new) — OkHttp wrapper, follows `PresenceApiClient` pattern. Methods: `createInvoice`, `addLineItem`, `setStatus`, `deleteInvoice`. Owns the unit conversions (tax rate, money, dates, status enum).
- `android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesPushWorker.kt` (new) — `CoroutineWorker`. Drains the outbox.
- `android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoiceJsonMapper.kt` (new) — serializes the apk `Invoice` object to the JSON shape the backend's `summary` jsonb expects, and to the structured POST body.
- `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobBoardViewModel.kt` (modify) — `generateInvoice` calls `invoicesOutbox.enqueueCreate` after setting `_generatedInvoice`. New `markShared(localId)` and `cancelGenerated(localId)` methods. `clearInvoice()` calls `cancelGenerated` if `shared=false`.
- `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobBoardScreen.kt` (modify) — `onShare` calls `viewModel.markShared(invoice.id)` before firing the Android share intent.
- `android/app/build.gradle.kts` (modify) — add `androidx.room:room-runtime`, `androidx.room:room-ktx`, `androidx.work:work-runtime-ktx` if not already present; KSP plugin for Room compiler.
- `android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesModule.kt` (new) — wires together `Room.databaseBuilder` for the outbox + provides the `InvoicesOutbox` singleton + ensures the worker is registered with WorkManager on app boot.
- `android/app/src/main/java/com/guildofsmiths/trademesh/MainActivity.kt` or the existing app init path (modify) — call `InvoicesModule.initialize(context)` on cold start (mirrors how `PresenceApiClient` and other singletons are wired today).
- `android/app/src/test/java/com/guildofsmiths/trademesh/data/invoice/InvoicesOutboxTest.kt` (new) — 9 unit tests against a fake ApiClient.
- `android/app/src/androidTest/java/com/guildofsmiths/trademesh/data/invoice/InvoicesPushE2ETest.kt` (new) — one instrumented end-to-end test.

## Verification

After implementation:

1. `cd backend && DATABASE_URL=postgresql://localhost/smithnet_test npx jest --maxWorkers=1 src/__tests__/invoices-routes-android.test.ts` — 7 new tests pass.
2. `cd backend && DATABASE_URL=postgresql://localhost/smithnet_test npx jest --maxWorkers=1 src/__tests__/invoices-routes.test.ts` — original 11 still pass.
3. `cd android && ./gradlew :app:testDebugUnitTest --tests "*InvoicesOutboxTest*"` — 9 outbox unit tests pass.
4. `cd android && ./gradlew :app:connectedDebugAndroidTest --tests "*InvoicesPushE2ETest*"` (against a running backend) — 1 instrumented test passes.
5. Manual smoke against the running apk + backend:
   - Generate invoice on a job → check db: `SELECT id, invoice_number, status, idempotency_key, summary->'mode' FROM invoices ORDER BY created_at DESC LIMIT 1;` — row exists, `status='draft'`, `summary` is populated.
   - Share the invoice → re-check db: same row, `status='sent'`.
   - Generate invoice → close without share → re-check db: row was created then deleted (or never created if the worker hadn't run yet); list endpoint does not return it.
   - Generate invoice in airplane mode → check outbox table on device: row is `pending`. Re-enable network → backend now has the row.
   - Apk forced-close mid-POST (kill via adb am force-stop) → reopen, give it ~30s → backend has exactly one row for the idempotency key.

## Future slices (not blocked by this one)

- **Apk read-back screen.** Adds list + detail in the apk, pulling from `/api/invoices`. Surfaces the server-assigned invoice number.
- **Foreground sync-issues indicator.** Shows the user when an outbox row is in `failed` state, so the silent-4xx caveat goes away.
- **Cross-device parity.** Edits made on the web reflect on the apk and vice versa. Requires bidirectional sync and conflict handling.
- **Apk-side editing.** Today the apk generates from job data; it does not let the user tweak fields before sending. A "review and edit" step would map onto the web's PATCH endpoints.
