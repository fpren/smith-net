# Adapting Onfleet's sync queue + proof-of-work to Smith Net

Status: design note (not yet scheduled into a phase)
Date: 2026-06-09
Scope: Android (`/android`) + Hetzner backend (`/backend`)
Source material: teardown of Onfleet Driver 2.6.4 and Rippling 3.0.41 (notes only; no code lifted)

---

## TL;DR

Onfleet and Rippling independently ship the same shape Smith Net already
committed to: persist intent locally as events, drain to the server through a
queue, reconcile with typed conflicts. The teardown does not introduce a new
architecture -- it validates ours and hands us three concrete, codeable
patterns:

1. **A general outbox** -- generalize the existing `InvoicesOutbox` into a
   transport-agnostic sync queue that any producer (proof-of-work, chat,
   location) writes into. This is the open "offline outbox/sync" roadmap item.
2. **Proof-of-work completion gating** -- a job cannot seal to a `LedgerEntry`
   until a server-declared set of requirements (photo / signature / barcode /
   note / reason) is satisfied. Each satisfied requirement is a producer into
   the SummaryArtifact.
3. **On-job-only location** -- bind the GPS lifecycle to the active-job
   lifecycle (we already have `location_points` with a battery column and a
   bounded buffer). This is how we get Onfleet's location value without
   Onfleet's battery drain.

The one place Onfleet's pattern must NOT be copied verbatim is ordering: a
naive FIFO outbox breaks the deterministic Ledger. The outbox carries Lamport
ordering fields and the Ledger seals over the canonical artifact, never over
queue-arrival order. Details in section 4.

---

## 1. What already exists (do not rebuild)

The codebase is further along than "greenfield outbox." Inventory:

| Concern | Existing asset | Notes |
|---|---|---|
| Outbox row + worker | `db/PendingInvoicePushEntity`, `db/PendingInvoicePushDao`, `data/invoice/InvoicesOutbox` | status-as-lock (`pending`/`in_flight`/`done`/`failed`/`cancelled`), atomic `markInFlight`/`cancelIfPending`, WorkManager drain with `NetworkType.CONNECTED`. This is our outbox prototype -- it just happens to be invoice-shaped. |
| Append-only log | `data/CordEntry`, `db/CordEntity`, `db/CordDao` | content-addressed id (SHA-256, 22-char), Lamport ts + authorCounter ordering, RSA signature, INSERT-OR-IGNORE collision resistance, no UPDATE/DELETE. |
| Deterministic seal | `backend/src/ledgerCanonical.ts` (`encodeLedgerArtifactV2`, `ledgerHashV2`), `core/LedgerCanon.kt` + parity test | v2 canonical encoding: length-prefixed UTF-8, integer minor units (no float in hashed bytes), id arrays sorted by utf-8 bytes, ROM-or-host parity. |
| Message reconciliation | `service/ReconciliationEngine.kt`, `backend/src/reconciliationEngine.ts` | `missingOnClient` / `missingOnServer` / `mergedClock`, vector-clock merge, causal sort (`sortByCausalOrder`). Today scoped to `message_bus_messages`. |
| Transport routing | `engine/BoundaryEngine` | mesh vs gateway vs IP decision, queue-media-when-offline, sync-on-reconnect, `createCordEntryIfNeeded` already classifies WORK_LOG / DECISION / COMMAND. |
| Location capture | `db/LocationPointEntity` | per-user, bounded to last 2000 points, `batteryPct` column, `source` in {gps,network,beacon}. Schema is ready; lifecycle binding is the gap. |
| Backend job/shift schema | migrations `016_tasks`, `028_shifts_time_entry_fields`, `029_shifts_task`, `031_jobs_stage`, `033_job_expenses` | jobs, shifts, time-entry fields, job stages already exist. |

Implication: this is mostly **generalization and wiring**, not new
infrastructure. The risk is duplicating the outbox, not lacking one.

---

## 2. The unifying model: one queue, many producers

Onfleet's `syncEvents` package (a Room `SyncRoomDatabase` + `SyncQueueDao` with
`StartSyncEventInt` / `CompleteSyncEventInt`) carries task lifecycle AND
location through one pipe. Map that onto Smith Net:

```
producers                         outbox (Room)                drain               server
---------                         -------------                -----               ------
proof-of-work completion  ---\
chat outbound             -----\   sync_outbox table   --->  SyncDrainWorker  --> BoundaryEngine
location (on-job only)    -----/   (op, payload,             (WorkManager,        -> Hetzner /api/*
invoice push (existing)   ---/      lamport, status)          NetworkType.CONNECTED)  -> Ledger seal
```

The existing `pending_invoice_pushes` table is the prototype of `sync_outbox`.
Two viable paths:

- **Path A (recommended): generalize in place.** Rename the concept to a
  `sync_outbox` table whose `op` namespace widens beyond invoices
  (`INVOICE_CREATE`, `JOB_COMPLETE`, `CHAT_SEND`, `LOCATION_BATCH`, ...). Keep
  the status-as-lock machinery verbatim -- it already solves the race that
  matters (`enqueueDiscard` vs a worker that just claimed the row).
- **Path B: leave invoices alone, add a sibling table.** Lower blast radius,
  but you maintain two drains and two workers forever. Reject unless the
  invoice pipeline is considered frozen.

Recommendation: **Path A**, because the whole point of the teardown finding is
"one queue, many producers." Two queues is the anti-pattern.

### Generalized row (superset of `PendingInvoicePushEntity`)

```
sync_outbox
  id            TEXT PK     -- CREATE-class ops: client UUID = idempotency key; else fresh UUID
  op            TEXT        -- namespaced: "JOB_COMPLETE" | "CHAT_SEND" | "LOCATION_BATCH" | "INVOICE_CREATE" | ...
  entityId      TEXT        -- the local domain id this op refers to (jobId, channelId, ...)
  payloadJson   TEXT?       -- op-specific body; null for marker ops
  backendId     TEXT?       -- server id, populated after success
  status        TEXT        -- pending | in_flight | done | failed | cancelled  (unchanged semantics)
  attempts      INT
  lastError     TEXT?
  -- NEW, ordering-critical (see section 4):
  lamportTs     INT         -- snapshot of LamportClock.tick() at enqueue time
  authorId      TEXT        -- producing actor
  authorCounter INT         -- tie-break within (authorId, lamportTs)
  createdAt     INT
  updatedAt     INT
  index(status, createdAt)
  index(lamportTs, authorId, authorCounter)   -- mirrors cord_entries ordering index
```

`createdAt` stays the **drain** order (wall clock, fine for "what do I POST
next"). `lamportTs/authorId/authorCounter` is the **causal** order the server
applies before sealing. These are different jobs; keep both.

---

## 3. Proof-of-work completion (the Onfleet `ui/tasks/complete` pattern)

Onfleet gates task completion behind a server-declared `Requirements` set
(`RequirementState`, `CustomRequirement`, `RequiredBarcode`,
`CompletionReasonOption`, `FailureReason`) with sub-flows for signature,
camera, barcode, age verification. Adapt to Smith Net's pipeline:

- A job carries a `requirements: Requirement[]` declared by the backend (per
  org / per job stage). Mirrors Onfleet's org-vs-task requirement split and
  fits our server-authoritative-caps posture.
- Each requirement, when satisfied, produces an artifact fragment:
  - photo / signature / barcode -> an `Attachment` + a `chatMessageId` or
    `workPerformed` entry,
  - completion reason / failure reason -> a `contextualNotes` entry (typed,
    never free text -- matches Onfleet's enum'd reasons and our determinism
    discipline).
- "Mark job done" is blocked in the UI until `RequirementState` is all-satisfied
  (Compose: derive a `canComplete` from the requirement states; no Material
  widgets -- ConsoleTheme per the design rules).
- On completion the producer enqueues a single `JOB_COMPLETE` outbox op whose
  payload references the satisfied requirement artifacts. The server validates
  the set server-side (never trust the client's "all satisfied"), then folds
  the fragments into the `SummaryArtifact` (`workPerformed`, `laborRecorded`,
  `materialsUsed`, `contextualNotes`, `jobIds`, `timeEntryIds`,
  `chatMessageIds`) and seals via `ledgerHashV2`.

Key adaptation vs Onfleet: Onfleet's completion is the terminal state. Ours is
terminal **and** sealed -- the requirement set is the precondition for a
`SummaryArtifact` that hashes deterministically. So requirement payloads must be
normalized to the same canonical discipline the Ledger uses (integer minor
units for any money/hours, sorted id sets). Do the normalization in the
synthesizer, not the client.

Tier note: which requirement types are available (e.g. barcode scan, custom
requirements) is a natural tier-gated capability. Use the existing structured
403 contract rather than hiding UI, so the upgrade path is legible.

---

## 4. Determinism: the one thing NOT to copy from Onfleet

Onfleet's queue is effectively FIFO -- it replays events in enqueue order and
the server is the order of record. If Smith Net did that, two devices that
completed jobs offline in different real-time orders could produce different
`SummaryArtifact` byte streams and therefore different seals -- breaking
NFR-D (bit-for-bit reproducibility).

Rules for the generalized outbox so the moat holds:

1. **The outbox never determines Ledger order.** It determines POST order only.
   The server orders folded events by `(lamportTs, authorId, authorCounter)` --
   the exact triple `cord_entries` and `CordEntry.orderingKey()` already use --
   before building the artifact.
2. **Stamp Lamport at enqueue, not at drain.** The producing actor calls
   `LamportClock.tick()` when it enqueues, and on receiving any remote event
   calls `LamportClock.update(received)`. Draining later (possibly hours later,
   possibly reordered by WorkManager) must not change the stamp.
3. **Seal over the canonical artifact, never over the queue.** `JOB_COMPLETE`
   carries the raw fragments; `encodeLedgerArtifactV2` + `ledgerHashV2` run on
   the synthesized artifact server-side. The queue is transport; the seal is
   content. Keep them disjoint.
4. **Idempotency = content address.** Reuse `generateSemanticMessageId`
   (authorId|messageClass|payload) so a retried drain of the same op is a no-op
   at the cord layer (`INSERT OR IGNORE`), exactly like the existing
   `pending_invoice_pushes` CREATE-id-as-idempotency-key trick.
5. **Reconciliation stays vector-clock-based.** Extend the existing
   `ReconciliationEngine` (`missingOnClient`/`missingOnServer`/`mergedClock`)
   from messages to the generalized event stream rather than inventing a second
   reconciliation path. Conflicts get typed per op (Onfleet's
   `OfflineEventConflict` lesson: a re-submitted job conflicts differently than
   an edited break), but resolution still merges clocks and causal-sorts.

If these five hold, the outbox is purely a delivery mechanism and the
determinism proof is unaffected -- the Ledger sees the same canonical artifact
regardless of when or in what order the queue drained.

---

## 5. On-job-only location (battery)

Onfleet drains battery because its location foreground service is unbound from
any task -- wake-locked for the whole shift. The user's chosen requirement is
"only while on a job." Implementation:

- GPS capture is a child of active-job/clocked-in state, not a standalone
  always-on service. Start on job-start, stop on job-complete. Minutes per job,
  not hours per shift.
- Writes go to the existing `location_points` (already bounded to 2000/user,
  already has `batteryPct`). Periodically a `LOCATION_BATCH` outbox op drains
  the buffer to the server -- batched, not per-fix, so the radio sleeps.
- Use FusedLocationProvider with a coarse interval + a geofence/significant-
  change trigger; pause capture when activity-recognition reports stationary.
  None of this needs Onfleet's `StatusService`.
- Location is NOT Ledger-sealed by default (it is telemetry, not a financial
  record) unless a requirement explicitly references a geo-fix as proof
  (e.g. "arrived on site"), in which case that single fix becomes a requirement
  artifact and follows section 3.

Privacy/per-profile rule: location rows are per-profile-scoped like everything
Hetzner-synced; on-job-only also minimizes what is ever captured, which is the
right default for the ethos.

---

## 6. Portal parity (PWA equivalent)

Parity rule: the portal must match the APK feature-for-feature and stay
mobile-friendly (no desktop-only layouts). The backend and the determinism
rules in section 4 are **client-agnostic** -- the server cannot tell which
client an op came from, and must not need to. So parity is achieved by the
portal producing the *same op rows with the same ordering fields*, not by
sharing client code.

### What already exists in the portal (do not rebuild)

| Concern | Existing asset | Notes |
|---|---|---|
| Per-profile read cache | `src/console/offline/db.ts` | IndexedDB via `idb`, keyed `${profileId}:${collection}`, schema-version-stamped envelope, best-effort degrade-to-no-cache. Mirrors the APK's per-profile isolation and schema-version invalidation. |
| Store hydration | `src/console/offline/persistStore.ts`, `useOfflinePersistence.ts` | zustand store hydrate-on-init + debounced write-through. **Read path only.** |
| Precache + nav fallback | `vite.config.ts` `VitePWA` + Workbox | `autoUpdate`, precache globs, `navigateFallback` to the SPA shell, denylist for `/api`, `/p/`, `/i/`, `/media/` (those must reach the server). |

The gap is exactly the symmetric one to the APK: the **read** cache exists, the
**write** outbox does not. Workbox is present but only precaching, not running a
background-sync queue.

### The mapping (APK -> portal)

| Piece | APK (`/android`) | Portal (`/desktop/portal`) |
|---|---|---|
| Outbox store | Room `sync_outbox` table | IndexedDB object store `sync_outbox` (new store in the existing `smithnet-offline` db, via `idb`) |
| Row shape | `PendingInvoicePushEntity` superset | identical fields incl. `op`, `entityId`, `payloadJson`, `status`, `lamportTs`, `authorId`, `authorCounter` |
| Drain trigger | WorkManager + `NetworkType.CONNECTED` | Workbox Background Sync **where supported**, plus a foreground fallback drain (see below) |
| Status-as-lock | atomic `UPDATE ... WHERE status='pending'` | claim inside a single `readwrite` idb transaction; the SW drain is single-threaded so the race surface is smaller, but still claim-then-POST |
| Idempotency key | CREATE id = client UUID; cord `generateSemanticMessageId` | **same** content-address scheme -- the server dedups identically regardless of client |
| Lamport clock | `LamportClock` (Kotlin) | a TS `LamportClock` with the same tick/update rule, persisted per-profile in IndexedDB so it survives reload |

The non-negotiable for parity: the portal stamps `(lamportTs, authorId,
authorCounter)` at enqueue using the same rule as the APK, and uses the same
content-address idempotency key. Then a `JOB_COMPLETE` enqueued from a phone and
one enqueued from the portal are indistinguishable to the synthesizer, fold into
the same canonical `SummaryArtifact`, and seal to the same hash. That is what
"parity" has to mean here -- not just matching screens, but matching the bytes
that reach the Ledger.

### iOS Safari caveat (this is the real constraint)

Service Worker Background Sync is Chromium-only. Safari / iOS Safari do not
support it, and the portal must be mobile-friendly -- which includes iPhone. So
Background Sync is an enhancement, not the mechanism. The mechanism is a
**foreground drain**: on `online` event, on tab focus/visibilitychange, and on
app start, replay `sync_outbox` rows oldest-first. Background Sync, where
available, just lets a drain also fire while the tab is backgrounded. Build the
foreground drain first; treat Background Sync as the Chromium bonus.

Consequence: the portal cannot guarantee an offline-composed op leaves the
device until the user reopens the tab while online. Document this as a known
weaker guarantee than the APK (which WorkManager can drain without the app in
foreground). It does not affect correctness -- Lamport stamps are taken at
enqueue, so a late drain still seals in the right causal order -- only latency.

### Proof-of-work completion on the portal

Per the portable-artifact direction, build the requirement-gating UI **once in
React** and treat the portal as the reuse target rather than reimplementing the
flow twice. Web equivalents of Onfleet's capture sub-flows:

- photo: `getUserMedia` / file input (`capture` attr on mobile),
- signature: canvas pointer-events (no library needed; stays ConsoleTheme),
- barcode: `BarcodeDetector` where available, file-upload + server OCR fallback
  elsewhere (Safari lacks `BarcodeDetector`),
- completion / failure reason: the same enum'd options as the APK -- typed,
  never free text.

Gating logic (`canComplete` derived from `RequirementState`) is pure and shared
in spirit with the APK; only the capture widgets differ by platform.

### Location

The portal does **not** capture location -- background geolocation on web is
unreliable and contradicts the on-job-only battery goal. The portal *consumes*
synced `location_points` (read-only, per-profile) for display only. Capture is
APK-only. (Restates section 5; flagged here so parity is not misread as "portal
must also track GPS.")

## 7. Rippling contributions (UI layer, independent of the queue)

These ride on top and can land in any order; they touch no moat code:

- **More-tab role-adaptive nav** -- a small fixed set of primary tabs + a
  data-driven "More" overflow whose items vary by tier/role. Fits the
  role-adaptive UI direction. Rebuild in Compose/ConsoleTheme.
- **Bulk approvals** -- foreman approving many crew time entries at once. Each
  approval is a `DECISION`-class producer into the same outbox; the bulk
  gesture just enqueues N ops.

---

## 8. Suggested build order

1. Generalize `pending_invoice_pushes` -> `sync_outbox` (Path A); keep invoices
   working through the widened table. Add Lamport columns + index.
2. Move invoice ops onto the generalized op namespace (proves the
   generalization against a known-good producer before adding new ones).
3. Proof-of-work completion: backend requirement declaration + server-side
   validation + synthesizer fold; Compose completion flow gated on
   `RequirementState`. First genuinely new producer.
4. On-job-only location: lifecycle binding + `LOCATION_BATCH` drain.
5. Chat outbound as a producer (fold `MessageRepository` pending-sync into the
   one outbox instead of `BoundaryEngine.syncMeshMessagesToChat`'s bespoke path).
6. Rippling nav + bulk approvals (UI, anytime).

Portal parity track (can run alongside, shares the backend from step 1):

P1. Add a `sync_outbox` object store to the portal's `smithnet-offline` db with
    the same row shape + Lamport fields; port `LamportClock` to TS (persisted
    per-profile).
P2. Foreground drain on `online` / focus / app-start (oldest-first, claim-then-
    POST). Wire Workbox Background Sync as the Chromium-only enhancement after.
P3. Move portal invoice writes (and any other current direct POSTs) onto the
    outbox -- parity with APK step 2, and proves the portal queue against a
    known producer.
P4. Proof-of-work completion UI in React (the build-once reuse target); web
    capture widgets per section 6.
P5. Consume synced `location_points` read-only for display.

Gate before starting: confirm against the daemon/worker-queue plan
(`docs/smith-net-daemon-worker-queue-plan.md`) that the backend side of these
ops is a `background_jobs` row per CLAUDE.md Rule 1/2 -- the Android outbox
POSTs, the route enqueues, the worker does the side effect. No inline LLM, no
fire-and-forget.

---

## 9. Open questions

- Does the generalized outbox subsume `BoundaryEngine`'s media queue and
  mesh-pending-sync, or do those stay separate for the mesh path? (Leaning:
  fold them, but mesh has no "server backendId," so the op set differs.)
- Requirement declaration source of truth: per-org config table vs per-job-stage
  (migration `031_jobs_stage`)? Onfleet supports both (org + task level).
- Where does the synthesizer run for `JOB_COMPLETE` -- inline in the route
  (cheap, deterministic, CPU-bound -> allowed by the Rule 1 exceptions) or in
  the worker? Probably inline since it is pure and fast, but confirm against the
  queue plan.
- Portal: should `sync_outbox` live in the existing `smithnet-offline` IndexedDB
  (bump `DB_VERSION`, add the store in `upgrade`) or a separate db? Leaning same
  db, new store, so per-profile clear (`clearProfile`) covers it for free -- but
  confirm the read-cache and the write-queue want the same lifecycle on logout.
- Portal: is the weaker drain guarantee on iOS Safari (no Background Sync ->
  foreground-only drain) acceptable for the foreman/dispatch use case, or does
  that push dispatch-critical writes to APK-only? Product call, not a code one.
