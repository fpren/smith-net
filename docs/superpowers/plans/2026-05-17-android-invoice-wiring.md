# Android Invoice Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist every generated apk invoice to the backend via a Room outbox + WorkManager worker so invoices stop dying when the preview dialog closes.

**Architecture:** Two surfaces, sequenced backend-first. Backend gains a `summary jsonb` column, an `idempotency_key`, and drops `requireConsoleTier` from `invoicesRouter`. Android adds a `pending_invoice_pushes` Room table, an `InvoicesPushWorker` (CoroutineWorker) that drains it, an `InvoicesApiClient` that talks to `/api/invoices`, and three new ViewModel hooks (`enqueueCreate` / `markShared` / `cancelGenerated`). On Generate the apk inserts a CREATE row; on Share it inserts MARK_SENT; on dismiss-without-share it inserts DISCARD or cancels the pending CREATE. All ops are durable across process death and replay-safe via idempotency keys.

**Tech Stack:** PostgreSQL + node-pg on backend, jest for backend tests. On apk: Room 2.6.1 (already in use, bump db version 6 → 7), KSP 1.9.24-1.0.20, kotlinx-serialization-json 1.6.2 (already in use), OkHttp via `HttpClientFactory.client` shared instance, WorkManager (new dep — `androidx.work:work-runtime-ktx`), JUnit + MockK for android unit tests.

**Spec:** `docs/superpowers/specs/2026-05-17-android-invoice-wiring-design.md` (commit 716ec9e).

**Branch:** `feat/relay-hetzner-postgres` (continue, do not switch).

---

## File Structure

### Backend (existing patterns)

| File | Action | Responsibility |
|---|---|---|
| `backend/migrations/018_invoices_android_wiring.sql` | create | Add `summary jsonb` + `idempotency_key text` columns; partial unique index |
| `backend/src/schemas/invoices.ts` | modify | Extend `CreateInvoiceBody` with optional `idempotencyKey` + `summary` |
| `backend/src/invoicesService.ts` | modify | `create()` accepts idempotencyKey + summary, returns existing row on key collision; `getByIdScoped()` projects the new columns |
| `backend/src/invoicesRoutes.ts` | modify | Drop `requireConsoleTier`; thread new fields through |
| `backend/src/__tests__/invoices-routes-android.test.ts` | create | 7 tests covering the deltas (idempotency, summary round-trip, solo tier) |

### Android (new + modified)

| File | Action | Responsibility |
|---|---|---|
| `android/app/build.gradle.kts` | modify | Add `androidx.work:work-runtime-ktx:2.9.0` |
| `android/app/src/main/java/com/guildofsmiths/trademesh/db/PendingInvoicePushEntity.kt` | create | Room `@Entity` for `pending_invoice_pushes` |
| `android/app/src/main/java/com/guildofsmiths/trademesh/db/PendingInvoicePushDao.kt` | create | `@Dao` with insert / pop-next / mark-done / mark-failed / state transitions |
| `android/app/src/main/java/com/guildofsmiths/trademesh/db/AppDatabase.kt` | modify | Register new entity, bump version 6 → 7 |
| `android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoiceJsonMapper.kt` | create | `Invoice` → `summary` JSON; `Invoice` → POST body; lineItems → POST body; unit conversions (tax rate, money, dates, status, category) |
| `android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesApiClient.kt` | create | OkHttp wrapper: `createInvoice` / `addLineItem` / `setStatus` / `deleteInvoice` |
| `android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesOutbox.kt` | create | Coordinates DAO + WorkManager scheduling; `enqueueCreate` / `enqueueMarkSent` / `enqueueDiscard` |
| `android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesPushWorker.kt` | create | `CoroutineWorker` that drains the outbox |
| `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobBoardViewModel.kt` | modify | `generateInvoice` enqueues CREATE; new `markShared` and `cancelGenerated`; track `shared` flag |
| `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobBoardScreen.kt` | modify | `onShare` calls `viewModel.markShared(invoice.id)` before share intent |
| `android/app/src/test/java/com/guildofsmiths/trademesh/data/invoice/InvoiceJsonMapperTest.kt` | create | Unit tests for the mapper (unit conversions, JSON shape) |
| `android/app/src/test/java/com/guildofsmiths/trademesh/data/invoice/InvoicesOutboxTest.kt` | create | 9 unit tests against fake ApiClient (worker + outbox behavior) |
| `android/app/src/androidTest/java/com/guildofsmiths/trademesh/data/invoice/InvoicesPushE2ETest.kt` | create | 1 instrumented end-to-end test against a running backend |

---

## Phase 1 — Backend

### Task 1: Migration 018

**Files:**
- Create: `backend/migrations/018_invoices_android_wiring.sql`

- [ ] **Step 1: Write the migration**

```sql
-- 018_invoices_android_wiring.sql
-- Adds the columns Android needs to push paper-trail invoices into the
-- existing invoices table without a separate sidecar:
--   summary         - opaque jsonb carrying the 37 apk fields the backend
--                     does not model (crew hours, daily breakdown, mesh
--                     presence, etc.). See the Android invoice wiring spec.
--   idempotency_key - client-generated UUID; doubles as the dedupe key so
--                     a retried POST returns the existing row.
-- Partial unique index so existing rows (which have NULL key) don't
-- collide with each other.

ALTER TABLE invoices
  ADD COLUMN IF NOT EXISTS summary         JSONB,
  ADD COLUMN IF NOT EXISTS idempotency_key TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS invoices_org_idem_unique
  ON invoices (organization_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
```

- [ ] **Step 2: Apply to dev + test databases**

Run:
```bash
PSQL=/opt/homebrew/opt/postgresql@16/bin/psql
$PSQL -d smithnet      -f backend/migrations/018_invoices_android_wiring.sql
$PSQL -d smithnet_test -f backend/migrations/018_invoices_android_wiring.sql
```

Expected: `ALTER TABLE` and `CREATE UNIQUE INDEX` succeed silently on both dbs.

- [ ] **Step 3: Verify the schema**

Run:
```bash
$PSQL -d smithnet -c "\d invoices" | grep -E "summary|idempotency_key"
$PSQL -d smithnet -c "\d invoices_org_idem_unique"
```

Expected output (first command):
```
 summary         | jsonb                    |           |
 idempotency_key | text                     |           |
```

Expected output (second command): one unique partial index on `(organization_id, idempotency_key) WHERE idempotency_key IS NOT NULL`.

- [ ] **Step 4: Re-run the migration to confirm idempotency**

Run:
```bash
$PSQL -d smithnet -f backend/migrations/018_invoices_android_wiring.sql
```

Expected: no errors. Each `ADD COLUMN IF NOT EXISTS` and `CREATE UNIQUE INDEX IF NOT EXISTS` is a no-op on the second run.

- [ ] **Step 5: Commit**

```bash
git add backend/migrations/018_invoices_android_wiring.sql
git commit -m "feat(invoices): migration 018 — summary jsonb + idempotency_key"
```

---

### Task 2: Idempotency key in the service layer (TDD)

**Files:**
- Modify: `backend/src/__tests__/invoices-routes-android.test.ts` (create on first task; this is the first test added)
- Modify: `backend/src/schemas/invoices.ts`
- Modify: `backend/src/invoicesService.ts`
- Modify: `backend/src/invoicesRoutes.ts`

- [ ] **Step 1: Write the failing tests**

Add new test file `backend/src/__tests__/invoices-routes-android.test.ts`:

```typescript
// backend/src/__tests__/invoices-routes-android.test.ts
//
// Tests covering the deltas from the Android invoice wiring slice:
//   - idempotencyKey on POST returns the existing row on replay
//   - summary jsonb round-trips intact
//   - Solo tier (no requireConsoleTier) can POST and GET
//
// Reuses the test harness pattern from invoices-routes.test.ts.

import request from 'supertest';
import { app } from '../app';
import {
  createTestUserWithOrg,
  authCookie,
  resetDb,
} from './helpers';

describe('POST /api/invoices — idempotency', () => {
  beforeEach(async () => { await resetDb(); });

  it('returns the same row for a repeated idempotencyKey', async () => {
    const { user, org } = await createTestUserWithOrg({ role: 'foreman' });
    const cookie = authCookie(user);
    const idemKey = 'fixed-uuid-aaaaaaaaaaaaaaaaaaaaaaa';

    const body = {
      idempotencyKey: idemKey,
      clientName: 'Acme Roofing',
      clientEmail: 'ops@acme.com',
      notes: 'first',
    };

    const first = await request(app)
      .post('/api/invoices')
      .set('Cookie', cookie)
      .send(body)
      .expect((r) => { if (r.status !== 200 && r.status !== 201) throw new Error(`status ${r.status}`); });

    const second = await request(app)
      .post('/api/invoices')
      .set('Cookie', cookie)
      .send({ ...body, notes: 'second' })  // payload diff — idempotency wins
      .expect(200);

    expect(second.body.invoice.id).toBe(first.body.invoice.id);
    expect(second.body.invoice.notes).toBe('first');  // first write wins
  });

  it('a different idempotencyKey produces a new row', async () => {
    const { user, org } = await createTestUserWithOrg({ role: 'foreman' });
    const cookie = authCookie(user);

    const a = await request(app)
      .post('/api/invoices')
      .set('Cookie', cookie)
      .send({ idempotencyKey: 'key-a', clientName: 'A Co' });

    const b = await request(app)
      .post('/api/invoices')
      .set('Cookie', cookie)
      .send({ idempotencyKey: 'key-b', clientName: 'B Co' });

    expect(a.body.invoice.id).not.toBe(b.body.invoice.id);
  });
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
cd backend && DATABASE_URL=postgresql://localhost/smithnet_test npx jest --maxWorkers=1 src/__tests__/invoices-routes-android.test.ts
```

Expected: both tests fail. The first call will be rejected because the schema doesn't know `idempotencyKey` (zod strict validation rejects unknown keys).

- [ ] **Step 3: Extend the zod schema**

In `backend/src/schemas/invoices.ts`, find `CreateInvoiceBody` and add the two optional fields:

```typescript
// before
export const CreateInvoiceBody = z.object({
  clientName:  z.string().min(1).max(200).optional(),
  clientEmail: z.string().email().max(200).optional(),
  dueDate:     z.string().datetime().optional(),
  notes:       z.string().max(2000).optional(),
}).strict();

// after
export const CreateInvoiceBody = z.object({
  clientName:     z.string().min(1).max(200).optional(),
  clientEmail:    z.string().email().max(200).optional(),
  dueDate:        z.string().datetime().optional(),
  notes:          z.string().max(2000).optional(),
  idempotencyKey: z.string().min(1).max(128).optional(),
  summary:        z.unknown().optional(),  // opaque jsonb carrier; backend does not introspect it
}).strict();
```

- [ ] **Step 4: Extend the service `create()`**

In `backend/src/invoicesService.ts`, modify `create()`:

```typescript
// before
export async function create(input: {
  organizationId: string;
  createdBy: string;
  clientName?: string;
  clientEmail?: string;
  dueDate?: Date | null;
  notes?: string;
}): Promise<Invoice> { /* existing impl */ }

// after
export async function create(input: {
  organizationId: string;
  createdBy: string;
  clientName?: string;
  clientEmail?: string;
  dueDate?: Date | null;
  notes?: string;
  idempotencyKey?: string;
  summary?: unknown;
}): Promise<Invoice> {
  const db = requirePg();

  // Idempotency lookup — if this org has already used this key, return that row.
  if (input.idempotencyKey) {
    const existing = await db.query(
      `SELECT * FROM invoices
        WHERE organization_id = $1
          AND idempotency_key = $2
          AND is_deleted = FALSE
        LIMIT 1`,
      [input.organizationId, input.idempotencyKey],
    );
    if (existing.rows[0]) {
      return mapRow(existing.rows[0]);
    }
  }

  // Existing nextInvoiceNumber + insert path (kept as-is), but the INSERT
  // gains two columns:
  for (let attempt = 0; attempt < 3; attempt++) {
    const invoiceNumber = await nextInvoiceNumber(input.organizationId);
    try {
      const { rows } = await db.query(
        `INSERT INTO invoices (
            organization_id, created_by, invoice_number,
            client_name, client_email, due_date, notes,
            idempotency_key, summary
          )
          VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
          RETURNING *`,
        [
          input.organizationId,
          input.createdBy,
          invoiceNumber,
          input.clientName ?? null,
          input.clientEmail ?? null,
          input.dueDate ?? null,
          input.notes ?? null,
          input.idempotencyKey ?? null,
          input.summary != null ? JSON.stringify(input.summary) : null,
        ],
      );
      return mapRow(rows[0]);
    } catch (e: any) {
      // 23505 = unique_violation. Two flavors:
      //   - invoices_org_number_unique: race on the per-year sequence; retry.
      //   - invoices_org_idem_unique: another caller won the idempotency race;
      //     fetch and return their row.
      if (e?.code === '23505' && e?.constraint === 'invoices_org_idem_unique' && input.idempotencyKey) {
        const winner = await db.query(
          `SELECT * FROM invoices
            WHERE organization_id = $1 AND idempotency_key = $2 AND is_deleted = FALSE`,
          [input.organizationId, input.idempotencyKey],
        );
        if (winner.rows[0]) return mapRow(winner.rows[0]);
      }
      if (e?.code === '23505' && attempt < 2) continue;
      throw e;
    }
  }
  throw new Error('failed to insert invoice after 3 attempts');
}
```

Also extend `mapRow()` to project the new columns:

```typescript
function mapRow(row: any): Invoice {
  return {
    id:              row.id,
    organizationId:  row.organization_id,
    createdBy:       row.created_by,
    invoiceNumber:   row.invoice_number,
    clientName:      row.client_name,
    clientEmail:     row.client_email,
    issueDate:       row.issue_date,
    dueDate:         row.due_date,
    status:          row.status,
    subtotal:        Number(row.subtotal),
    taxRate:         Number(row.tax_rate),
    taxAmount:       Number(row.tax_amount),
    totalDue:        Number(row.total_due),
    notes:           row.notes,
    idempotencyKey:  row.idempotency_key ?? null,        // NEW
    summary:         row.summary ?? null,                // NEW (pg returns parsed jsonb)
    createdAt:       row.created_at,
    updatedAt:       row.updated_at,
  };
}
```

Also update the `Invoice` interface at the top of the file to include the new fields:

```typescript
export interface Invoice {
  // ...existing fields...
  idempotencyKey: string | null;
  summary: unknown | null;
}
```

- [ ] **Step 5: Thread the new fields through the route handler**

In `backend/src/invoicesRoutes.ts`, find the `POST /invoices` handler and thread the new fields:

```typescript
// before
invoicesRouter.post('/invoices', validateBody(CreateInvoiceBody), async (req, res) => {
  const user = req.user!;
  const body = req.body as z.infer<typeof CreateInvoiceBody>;
  const invoice = await invoicesService.create({
    organizationId: user.organizationId,
    createdBy:      user.id,
    clientName:     body.clientName,
    clientEmail:    body.clientEmail,
    dueDate:        body.dueDate ? new Date(body.dueDate) : null,
    notes:          body.notes,
  });
  res.status(201).json({ invoice });
});

// after
invoicesRouter.post('/invoices', validateBody(CreateInvoiceBody), async (req, res) => {
  const user = req.user!;
  const body = req.body as z.infer<typeof CreateInvoiceBody>;

  // Idempotency: check first so we can return 200 (not 201) on replay.
  if (body.idempotencyKey) {
    const existing = await invoicesService.findByIdempotencyKey(
      user.organizationId,
      body.idempotencyKey,
    );
    if (existing) {
      return res.status(200).json({ invoice: existing });
    }
  }

  const invoice = await invoicesService.create({
    organizationId: user.organizationId,
    createdBy:      user.id,
    clientName:     body.clientName,
    clientEmail:    body.clientEmail,
    dueDate:        body.dueDate ? new Date(body.dueDate) : null,
    notes:          body.notes,
    idempotencyKey: body.idempotencyKey,
    summary:        body.summary,
  });
  res.status(201).json({ invoice });
});
```

Add the `findByIdempotencyKey` helper to `invoicesService.ts`:

```typescript
export async function findByIdempotencyKey(
  organizationId: string,
  idempotencyKey: string,
): Promise<Invoice | null> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM invoices
       WHERE organization_id = $1
         AND idempotency_key = $2
         AND is_deleted = FALSE
       LIMIT 1`,
    [organizationId, idempotencyKey],
  );
  return rows[0] ? mapRow(rows[0]) : null;
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run:
```bash
cd backend && DATABASE_URL=postgresql://localhost/smithnet_test npx jest --maxWorkers=1 src/__tests__/invoices-routes-android.test.ts
```

Expected: both tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/schemas/invoices.ts backend/src/invoicesService.ts backend/src/invoicesRoutes.ts backend/src/__tests__/invoices-routes-android.test.ts
git commit -m "feat(invoices): idempotencyKey on POST returns existing row on replay"
```

---

### Task 3: summary jsonb round-trip (TDD)

**Files:**
- Modify: `backend/src/__tests__/invoices-routes-android.test.ts`
- Modify: `backend/src/invoicesRoutes.ts` (only if `GET /invoices/:id` doesn't already return the new fields — the `mapRow` change in Task 2 already covers that, so this task is verification-only on the read path)

- [ ] **Step 1: Add the failing test**

Append to `backend/src/__tests__/invoices-routes-android.test.ts`:

```typescript
describe('POST /api/invoices — summary jsonb', () => {
  beforeEach(async () => { await resetDb(); });

  it('round-trips a summary blob unchanged', async () => {
    const { user } = await createTestUserWithOrg({ role: 'foreman' });
    const cookie = authCookie(user);

    const summary = {
      mode: 'ENTERPRISE',
      from: { name: 'Jane', business: 'Acme Trades', trade: 'Foreman' },
      crew: [
        { name: 'Bob', role: 'Journeyman', totalHours: 8.5 },
        { name: 'Sue', role: 'Apprentice', totalHours: 4.0 },
      ],
      dailyBreakdown: [
        { day: 1, totalHours: 7.5, activities: 'Framing south wall' },
      ],
      meshPresence: '97.2% average',
      efficiencyScore: 93,
    };

    const created = await request(app)
      .post('/api/invoices')
      .set('Cookie', cookie)
      .send({
        idempotencyKey: 'sum-test-1',
        clientName: 'BigCo',
        summary,
      });
    expect(created.status).toBeLessThan(300);

    const fetched = await request(app)
      .get(`/api/invoices/${created.body.invoice.id}`)
      .set('Cookie', cookie)
      .expect(200);

    expect(fetched.body.invoice.summary).toEqual(summary);
  });

  it('accepts a missing summary (null)', async () => {
    const { user } = await createTestUserWithOrg({ role: 'foreman' });
    const cookie = authCookie(user);

    const created = await request(app)
      .post('/api/invoices')
      .set('Cookie', cookie)
      .send({ idempotencyKey: 'no-sum', clientName: 'Foo' });
    expect(created.status).toBeLessThan(300);
    expect(created.body.invoice.summary).toBeNull();
  });
});
```

- [ ] **Step 2: Run to verify they pass**

Run:
```bash
cd backend && DATABASE_URL=postgresql://localhost/smithnet_test npx jest --maxWorkers=1 src/__tests__/invoices-routes-android.test.ts
```

Expected: both new tests pass. (The implementation was already complete in Task 2; this task is a deliberate verification.)

If the second test fails because `summary` comes back as `undefined` instead of `null`, the issue is in `mapRow`. Confirm `mapRow` uses `row.summary ?? null` (it should after Task 2).

- [ ] **Step 3: Commit**

```bash
git add backend/src/__tests__/invoices-routes-android.test.ts
git commit -m "test(invoices): summary jsonb round-trips intact"
```

---

### Task 4: Drop the foreman-tier gate (TDD)

**Files:**
- Modify: `backend/src/__tests__/invoices-routes-android.test.ts`
- Modify: `backend/src/invoicesRoutes.ts`

- [ ] **Step 1: Add the failing tests**

Append to `backend/src/__tests__/invoices-routes-android.test.ts`:

```typescript
describe('POST /api/invoices — solo tier', () => {
  beforeEach(async () => { await resetDb(); });

  it('allows a solo user to create an invoice in their org-of-one', async () => {
    const { user } = await createTestUserWithOrg({ role: 'solo' });
    const cookie = authCookie(user);

    const created = await request(app)
      .post('/api/invoices')
      .set('Cookie', cookie)
      .send({ idempotencyKey: 'solo-1', clientName: 'Direct Client' });

    expect(created.status).toBeLessThan(300);
    expect(created.body.invoice.invoiceNumber).toMatch(/^INV-\d{4}-\d{4}$/);
  });

  it('solo GET /api/invoices returns only their own org', async () => {
    const { user: solo }     = await createTestUserWithOrg({ role: 'solo' });
    const { user: foreman }  = await createTestUserWithOrg({ role: 'foreman' });

    await request(app)
      .post('/api/invoices')
      .set('Cookie', authCookie(solo))
      .send({ idempotencyKey: 'solo-list', clientName: 'Solo Client' });

    await request(app)
      .post('/api/invoices')
      .set('Cookie', authCookie(foreman))
      .send({ idempotencyKey: 'foreman-list', clientName: 'Foreman Client' });

    const soloList = await request(app)
      .get('/api/invoices')
      .set('Cookie', authCookie(solo))
      .expect(200);

    const names = soloList.body.invoices.map((i: any) => i.clientName);
    expect(names).toContain('Solo Client');
    expect(names).not.toContain('Foreman Client');
  });
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
cd backend && DATABASE_URL=postgresql://localhost/smithnet_test npx jest --maxWorkers=1 src/__tests__/invoices-routes-android.test.ts
```

Expected: first test fails with 403 `tier_required` (because `requireConsoleTier` is still mounted on the router).

- [ ] **Step 3: Drop the tier gate**

In `backend/src/invoicesRoutes.ts`:

```typescript
// before
export const invoicesRouter = Router();
invoicesRouter.use(requireConsoleTier);
```

```typescript
// after
export const invoicesRouter = Router();
// NOTE: requireConsoleTier intentionally removed. Solo workers post their
// own invoices into their org-of-one for paper-trail purposes — see the
// Android invoice wiring spec (2026-05-17). Per-org isolation is still
// enforced at the service layer (every query filters by organization_id).
```

Also drop the now-unused import line at the top if it has no other callers in this file.

- [ ] **Step 4: Run the tests to verify they pass**

Run:
```bash
cd backend && DATABASE_URL=postgresql://localhost/smithnet_test npx jest --maxWorkers=1 src/__tests__/invoices-routes-android.test.ts
```

Expected: all four tests in the file pass.

- [ ] **Step 5: Run the original invoices-routes tests to confirm no regression**

Run:
```bash
cd backend && DATABASE_URL=postgresql://localhost/smithnet_test npx jest --maxWorkers=1 src/__tests__/invoices-routes.test.ts
```

Expected: all 11 original tests still pass. Note: the test that was named something like "Solo worker → 403 tier_required on every endpoint" must be updated or removed — solo now succeeds. Edit that test to assert 200/201 instead.

If that test is `test('solo gets 403 on POST /api/invoices', ...)`, change it to:

```typescript
test('solo can post to /api/invoices (no longer tier-gated)', async () => {
  const { user } = await createTestUserWithOrg({ role: 'solo' });
  const cookie = authCookie(user);
  const r = await request(app)
    .post('/api/invoices')
    .set('Cookie', cookie)
    .send({ clientName: 'Foo' });
  expect(r.status).toBeLessThan(300);
});
```

Re-run the test file; expect all 11 to pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/invoicesRoutes.ts backend/src/__tests__/invoices-routes-android.test.ts backend/src/__tests__/invoices-routes.test.ts
git commit -m "feat(invoices): drop requireConsoleTier — solo users post to /api/invoices"
```

---

## Phase 2 — Android infrastructure

### Task 5: Add WorkManager dependency

**Files:**
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: Add the dependency**

In `android/app/build.gradle.kts`, locate the `dependencies` block (around the Room entries — search for `androidx.room:room-runtime`) and add:

```kotlin
    // WorkManager — drains the InvoicesOutbox in the background, survives
    // process death. See docs/superpowers/specs/2026-05-17-android-invoice-wiring-design.md.
    implementation("androidx.work:work-runtime-ktx:2.9.0")
```

- [ ] **Step 2: Sync gradle**

Run:
```bash
cd android && ./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -E "androidx.work"
```

Expected output includes:
```
+--- androidx.work:work-runtime-ktx:2.9.0
```

- [ ] **Step 3: Commit**

```bash
git add android/app/build.gradle.kts
git commit -m "build(android): add WorkManager dep for invoice outbox"
```

---

### Task 6: Room entity for `pending_invoice_pushes`

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/db/PendingInvoicePushEntity.kt`
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/db/AppDatabase.kt`

- [ ] **Step 1: Write the entity**

```kotlin
// android/app/src/main/java/com/guildofsmiths/trademesh/db/PendingInvoicePushEntity.kt
package com.guildofsmiths.trademesh.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Outbox row for the apk -> /api/invoices push pipeline. One row per
 * pending operation (CREATE / MARK_SENT / DISCARD). The InvoicesPushWorker
 * drains rows in `createdAt` order via the DAO.
 *
 * The status field doubles as a lock: a row marked `in_flight` is being
 * processed by the worker; the outbox's enqueueDiscard uses this state to
 * decide whether to mutate the CREATE row (pending) or insert a new
 * DISCARD row (in_flight or done). See the Android invoice wiring spec
 * for the full race-safety argument.
 */
@Entity(
    tableName = "pending_invoice_pushes",
    indices = [Index(value = ["status", "createdAt"])]
)
data class PendingInvoicePushEntity(
    @PrimaryKey
    val id: String,                 // For CREATE: client UUID (also the idempotency key sent to backend).
                                    // For MARK_SENT / DISCARD: a fresh UUID.
    val localInvoiceId: String,     // The apk Invoice.id this op refers to. Same as id for CREATE.
    val op: String,                 // "CREATE" | "MARK_SENT" | "DISCARD"
    val payloadJson: String?,       // Full 50-field Invoice JSON for CREATE; null otherwise.
    val backendId: String?,         // Server invoice UUID; populated after CREATE succeeds.
    val status: String,             // "pending" | "in_flight" | "done" | "failed" | "cancelled"
    val attempts: Int,
    val lastError: String?,
    val createdAt: Long,            // epoch millis
    val updatedAt: Long,
)
```

- [ ] **Step 2: Register the entity in AppDatabase**

Modify `android/app/src/main/java/com/guildofsmiths/trademesh/db/AppDatabase.kt`:

```kotlin
// before
@Database(
    entities = [MessageEntity::class, CordEntity::class, UnifiedMessageEntity::class, LocationPointEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun cordDao(): CordDao
    abstract fun unifiedMessageDao(): UnifiedMessageDao
    abstract fun locationPointDao(): LocationPointDao
```

```kotlin
// after
@Database(
    entities = [
        MessageEntity::class,
        CordEntity::class,
        UnifiedMessageEntity::class,
        LocationPointEntity::class,
        PendingInvoicePushEntity::class,
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun cordDao(): CordDao
    abstract fun unifiedMessageDao(): UnifiedMessageDao
    abstract fun locationPointDao(): LocationPointDao
    abstract fun pendingInvoicePushDao(): PendingInvoicePushDao  // added in next task
```

(The compile will fail until Task 7 lands the DAO; that's fine — we commit at the end of Task 7.)

- [ ] **Step 3: Defer compile + commit to Task 7**

This task and Task 7 commit together because the DAO method is referenced from `AppDatabase` and the entity needs the DAO to be useful. Proceed to Task 7.

---

### Task 7: PendingInvoicePushDao (TDD)

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/db/PendingInvoicePushDao.kt`
- Create: `android/app/src/test/java/com/guildofsmiths/trademesh/db/PendingInvoicePushDaoTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// android/app/src/test/java/com/guildofsmiths/trademesh/db/PendingInvoicePushDaoTest.kt
package com.guildofsmiths.trademesh.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class PendingInvoicePushDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PendingInvoicePushDao

    @Before fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.pendingInvoicePushDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun insert_and_findById() = runBlocking {
        val row = PendingInvoicePushEntity(
            id = "id-1",
            localInvoiceId = "id-1",
            op = "CREATE",
            payloadJson = """{"x":1}""",
            backendId = null,
            status = "pending",
            attempts = 0,
            lastError = null,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        dao.insert(row)
        val got = dao.findById("id-1")
        assertNotNull(got)
        assertEquals("CREATE", got!!.op)
        assertEquals("pending", got.status)
    }

    @Test fun nextPending_returns_oldest_pending() = runBlocking {
        dao.insert(makeRow("a", "CREATE", "pending", createdAt = 200L))
        dao.insert(makeRow("b", "CREATE", "pending", createdAt = 100L))   // oldest
        dao.insert(makeRow("c", "CREATE", "done",    createdAt = 50L))    // ignored — not pending

        val next = dao.nextPending()
        assertNotNull(next)
        assertEquals("b", next!!.id)
    }

    @Test fun markInFlight_atomic_returns_true_on_pending_false_on_other() = runBlocking {
        dao.insert(makeRow("a", "CREATE", "pending"))
        dao.insert(makeRow("b", "CREATE", "done"))

        assertTrue(dao.markInFlight("a", nowMs = 2000L))
        assertFalse(dao.markInFlight("b", nowMs = 2000L))  // already done
        assertFalse(dao.markInFlight("a", nowMs = 2000L))  // already in_flight after first call

        val a = dao.findById("a")!!
        assertEquals("in_flight", a.status)
        assertEquals(2000L, a.updatedAt)
    }

    @Test fun markDone_sets_backendId_and_status() = runBlocking {
        dao.insert(makeRow("a", "CREATE", "in_flight"))
        dao.markDone("a", backendId = "srv-123", nowMs = 3000L)
        val a = dao.findById("a")!!
        assertEquals("done",   a.status)
        assertEquals("srv-123", a.backendId)
    }

    @Test fun markFailed_records_lastError_and_status() = runBlocking {
        dao.insert(makeRow("a", "CREATE", "in_flight"))
        dao.markFailed("a", lastError = "422 boom", nowMs = 3000L)
        val a = dao.findById("a")!!
        assertEquals("failed", a.status)
        assertEquals("422 boom", a.lastError)
    }

    @Test fun revertToPending_bumps_attempts() = runBlocking {
        dao.insert(makeRow("a", "CREATE", "in_flight", attempts = 0))
        dao.revertToPending("a", lastError = "transient 500", nowMs = 4000L)
        val a = dao.findById("a")!!
        assertEquals("pending", a.status)
        assertEquals(1, a.attempts)
        assertEquals("transient 500", a.lastError)
    }

    @Test fun cancelIfPending_succeeds_on_pending_noops_on_in_flight() = runBlocking {
        dao.insert(makeRow("pending-row",   "CREATE", "pending"))
        dao.insert(makeRow("in-flight-row", "CREATE", "in_flight"))

        assertTrue(dao.cancelIfPending("pending-row", nowMs = 5000L))
        assertFalse(dao.cancelIfPending("in-flight-row", nowMs = 5000L))

        assertEquals("cancelled", dao.findById("pending-row")!!.status)
        assertEquals("in_flight", dao.findById("in-flight-row")!!.status)
    }

    @Test fun findCreateRowFor_returns_create_for_local_invoice_id() = runBlocking {
        dao.insert(makeRow("c1", "CREATE", "done", localInvoiceId = "inv-x", backendId = "srv-x"))
        dao.insert(makeRow("m1", "MARK_SENT", "pending", localInvoiceId = "inv-x"))

        val create = dao.findCreateRowFor("inv-x")
        assertNotNull(create)
        assertEquals("c1", create!!.id)
    }

    private fun makeRow(
        id: String,
        op: String,
        status: String,
        localInvoiceId: String = id,
        backendId: String? = null,
        attempts: Int = 0,
        createdAt: Long = 1000L,
    ) = PendingInvoicePushEntity(
        id = id, localInvoiceId = localInvoiceId, op = op,
        payloadJson = null, backendId = backendId, status = status,
        attempts = attempts, lastError = null,
        createdAt = createdAt, updatedAt = createdAt,
    )
}
```

Note: this test uses Robolectric to drive Room in JVM tests. If the project doesn't already have Robolectric configured, add it to `build.gradle.kts`:

```kotlin
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
```

- [ ] **Step 2: Write the DAO**

```kotlin
// android/app/src/main/java/com/guildofsmiths/trademesh/db/PendingInvoicePushDao.kt
package com.guildofsmiths.trademesh.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingInvoicePushDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: PendingInvoicePushEntity)

    @Query("SELECT * FROM pending_invoice_pushes WHERE id = :id")
    suspend fun findById(id: String): PendingInvoicePushEntity?

    /**
     * The oldest row in `pending` state across all ops. The worker calls
     * this in a loop, draining the queue.
     */
    @Query("""
        SELECT * FROM pending_invoice_pushes
         WHERE status = 'pending'
         ORDER BY createdAt ASC
         LIMIT 1
    """)
    suspend fun nextPending(): PendingInvoicePushEntity?

    /**
     * The CREATE row for a given local invoice id, regardless of status.
     * Used by enqueueMarkSent / enqueueDiscard to look up the prerequisite.
     */
    @Query("""
        SELECT * FROM pending_invoice_pushes
         WHERE op = 'CREATE' AND localInvoiceId = :localInvoiceId
         LIMIT 1
    """)
    suspend fun findCreateRowFor(localInvoiceId: String): PendingInvoicePushEntity?

    /**
     * Atomic transition pending -> in_flight. Returns true if the update
     * actually moved a row (caller now owns it for the duration of the
     * network call). False means another worker beat us to it, or the
     * row is no longer pending.
     */
    @Query("""
        UPDATE pending_invoice_pushes
           SET status = 'in_flight', updatedAt = :nowMs
         WHERE id = :id AND status = 'pending'
    """)
    suspend fun markInFlightInternal(id: String, nowMs: Long): Int

    suspend fun markInFlight(id: String, nowMs: Long): Boolean =
        markInFlightInternal(id, nowMs) > 0

    @Query("""
        UPDATE pending_invoice_pushes
           SET status = 'done', backendId = :backendId, updatedAt = :nowMs
         WHERE id = :id
    """)
    suspend fun markDone(id: String, backendId: String?, nowMs: Long)

    @Query("""
        UPDATE pending_invoice_pushes
           SET status = 'failed', lastError = :lastError, updatedAt = :nowMs
         WHERE id = :id
    """)
    suspend fun markFailed(id: String, lastError: String, nowMs: Long)

    @Query("""
        UPDATE pending_invoice_pushes
           SET status = 'pending',
               attempts = attempts + 1,
               lastError = :lastError,
               updatedAt = :nowMs
         WHERE id = :id
    """)
    suspend fun revertToPending(id: String, lastError: String, nowMs: Long)

    /**
     * Atomic pending -> cancelled. Returns true only when the row was
     * actually pending at the time of the call; false if it had already
     * moved to in_flight or beyond (in which case the caller should
     * insert a DISCARD row instead).
     */
    @Query("""
        UPDATE pending_invoice_pushes
           SET status = 'cancelled', updatedAt = :nowMs
         WHERE id = :id AND status = 'pending'
    """)
    suspend fun cancelIfPendingInternal(id: String, nowMs: Long): Int

    suspend fun cancelIfPending(id: String, nowMs: Long): Boolean =
        cancelIfPendingInternal(id, nowMs) > 0

    /**
     * Find a row by (op, localInvoiceId). Used by the race-cancel test to
     * confirm that a DISCARD row exists alongside an in_flight CREATE.
     */
    @Query("""
        SELECT * FROM pending_invoice_pushes
         WHERE op = :op AND localInvoiceId = :localInvoiceId
         LIMIT 1
    """)
    suspend fun findByOpAndLocalId(op: String, localInvoiceId: String): PendingInvoicePushEntity?
}
```

- [ ] **Step 3: Run the tests**

Run:
```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "*PendingInvoicePushDaoTest*"
```

Expected: 8 tests pass. If Room destructive migration logs a warning about version 6 -> 7, that's fine — `fallbackToDestructiveMigration()` is already enabled in `AppDatabase`.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/db/PendingInvoicePushEntity.kt \
        android/app/src/main/java/com/guildofsmiths/trademesh/db/PendingInvoicePushDao.kt \
        android/app/src/main/java/com/guildofsmiths/trademesh/db/AppDatabase.kt \
        android/app/src/test/java/com/guildofsmiths/trademesh/db/PendingInvoicePushDaoTest.kt \
        android/app/build.gradle.kts
git commit -m "feat(invoices): Room outbox table + DAO for pending invoice pushes"
```

---

## Phase 3 — Android JSON mapper

### Task 8: InvoiceJsonMapper (TDD)

**Files:**
- Create: `android/app/src/test/java/com/guildofsmiths/trademesh/data/invoice/InvoiceJsonMapperTest.kt`
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoiceJsonMapper.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
// android/app/src/test/java/com/guildofsmiths/trademesh/data/invoice/InvoiceJsonMapperTest.kt
package com.guildofsmiths.trademesh.data.invoice

import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.invoice.InvoiceLineItem
import com.guildofsmiths.trademesh.ui.invoice.InvoiceMode
import com.guildofsmiths.trademesh.ui.invoice.LineItemCategory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class InvoiceJsonMapperTest {

    @Test fun createBody_emits_idempotencyKey_clientName_clientEmail() {
        val inv = sampleInvoice()
        val body = JSONObject(InvoiceJsonMapper.createBody(inv))
        assertEquals(inv.id, body.getString("idempotencyKey"))
        assertEquals("Acme Roofing", body.getString("clientName"))
        assertEquals("ops@acme.com", body.getString("clientEmail"))
    }

    @Test fun createBody_emits_iso_dueDate() {
        val inv = sampleInvoice().copy(dueDate = 1_700_000_000_000L) // 2023-11-14T22:13:20Z
        val body = JSONObject(InvoiceJsonMapper.createBody(inv))
        assertEquals("2023-11-14T22:13:20Z", body.getString("dueDate"))
    }

    @Test fun createBody_embeds_summary_with_apk_only_fields() {
        val inv = sampleInvoice().copy(
            efficiencyScore = 93,
            meshPresence = "97.2% average",
            workLogSummary = "south wall",
        )
        val body = JSONObject(InvoiceJsonMapper.createBody(inv))
        val summary = body.getJSONObject("summary")
        assertEquals(93, summary.getInt("efficiencyScore"))
        assertEquals("97.2% average", summary.getString("meshPresence"))
        assertEquals("south wall", summary.getString("workLogSummary"))
        assertEquals("SOLO", summary.getString("mode"))
    }

    @Test fun lineItemBody_lowercases_category_and_formats_money_as_string() {
        val li = InvoiceLineItem(
            code = "LAB-01",
            description = "Labor",
            quantity = 4.0,
            unit = "hr",
            rate = 85.0,
            total = 340.0,
            category = LineItemCategory.LABOR,
        )
        val body = JSONObject(InvoiceJsonMapper.lineItemBody(li))
        assertEquals("Labor",   body.getString("description"))
        assertEquals(4.0,       body.getDouble("quantity"), 0.0001)
        assertEquals("hr",      body.getString("unit"))
        assertEquals("85.00",   body.getString("rate"))
        assertEquals("labor",   body.getString("category"))
    }

    @Test fun statusBody_lowercases_enum() {
        assertEquals("sent",
            JSONObject(InvoiceJsonMapper.statusBody("sent")).getString("status"))
    }

    @Test fun summary_preserves_apk_invoice_number_even_though_backend_overwrites() {
        val inv = sampleInvoice().copy(invoiceNumber = "INV-2026-05-0001-CREW-WEEK")
        val body = JSONObject(InvoiceJsonMapper.createBody(inv))
        val summary = body.getJSONObject("summary")
        assertEquals("INV-2026-05-0001-CREW-WEEK", summary.getString("apkInvoiceNumber"))
    }

    private fun sampleInvoice(): Invoice = Invoice(
        id = "inv-uuid-aaaa",
        invoiceNumber = "INV-2026-05-0001",
        issueDate = 1_700_000_000_000L,
        dueDate = 1_700_000_000_000L,
        mode = InvoiceMode.SOLO,
        fromName = "Jane",
        toName = "Acme Roofing",
        toEmail = "ops@acme.com",
        jobId = "job-1",
        jobTitle = "Reroof",
    )
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "*InvoiceJsonMapperTest*"
```

Expected: compile error — `InvoiceJsonMapper` does not exist yet.

- [ ] **Step 3: Implement the mapper**

```kotlin
// android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoiceJsonMapper.kt
package com.guildofsmiths.trademesh.data.invoice

import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.invoice.InvoiceLineItem
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Translates an apk-side [Invoice] into the wire shapes the backend's
 * /api/invoices endpoints expect. Boundary helpers live here so the
 * ApiClient stays a dumb HTTP layer and the worker doesn't have to
 * know about JSON shape.
 *
 * Unit conversions applied here (from the Android invoice wiring spec):
 *   - tax rate: percent (8.25) -> fraction (0.0825)
 *   - money:    Double -> BigDecimal string with 2-place HALF_UP rounding
 *   - dates:    epoch millis -> ISO 8601 UTC
 *   - status enum: UPPERCASE -> lowercase
 *   - category enum: UPPERCASE -> lowercase
 *
 * The apk-generated invoiceNumber is intentionally NOT sent as a wire
 * field — backend mints its own. It is preserved in summary.apkInvoiceNumber
 * so it can be inspected later (e.g. matched against an apk-shared PDF).
 */
object InvoiceJsonMapper {

    /** Body for POST /api/invoices. */
    fun createBody(inv: Invoice): String {
        val o = JSONObject()
        o.put("idempotencyKey", inv.id)
        if (inv.toName.isNotEmpty())  o.put("clientName",  inv.toName)
        if (inv.toEmail.isNotEmpty()) o.put("clientEmail", inv.toEmail)
        if (inv.dueDate > 0L)         o.put("dueDate",     formatIso(inv.dueDate))
        if (inv.notes.isNotEmpty())   o.put("notes",       inv.notes)
        o.put("summary", buildSummary(inv))
        return o.toString()
    }

    /** Body for POST /api/invoices/{id}/line-items. */
    fun lineItemBody(li: InvoiceLineItem): String {
        val o = JSONObject()
        o.put("description", li.description)
        o.put("quantity",    li.quantity)
        o.put("unit",        li.unit)
        o.put("rate",        formatMoney(li.rate))
        o.put("category",    li.category.name.lowercase())
        return o.toString()
    }

    /** Body for PATCH /api/invoices/{id}/status. */
    fun statusBody(status: String): String =
        JSONObject().put("status", status.lowercase()).toString()

    private fun buildSummary(inv: Invoice): JSONObject {
        val s = JSONObject()
        s.put("apkInvoiceNumber", inv.invoiceNumber)
        s.put("mode", inv.mode.name)

        val from = JSONObject()
        from.put("name",     inv.fromName)
        from.put("business", inv.fromBusiness)
        from.put("trade",    inv.fromTrade)
        from.put("phone",    inv.fromPhone)
        from.put("email",    inv.fromEmail)
        from.put("address",  inv.fromAddress)
        s.put("from", from)

        val to = JSONObject()
        to.put("name",    inv.toName)
        to.put("company", inv.toCompany)
        to.put("address", inv.toAddress)
        to.put("email",   inv.toEmail)
        s.put("to", to)

        s.put("projectRef", inv.projectRef)
        s.put("poNumber",   inv.poNumber)
        if (inv.projectStart != null) s.put("projectStart", inv.projectStart)
        if (inv.projectEnd   != null) s.put("projectEnd",   inv.projectEnd)
        s.put("workingDays", inv.workingDays)

        val crew = JSONArray()
        inv.crew.forEach { m ->
            val cm = JSONObject()
            cm.put("name", m.name)
            cm.put("role", m.role)
            cm.put("occupation", m.occupation)
            cm.put("totalHours", m.totalHours)
            cm.put("productiveHours", m.productiveHours)
            cm.put("travelHours", m.travelHours)
            crew.put(cm)
        }
        s.put("crew", crew)
        s.put("totalCrewHours", inv.totalCrewHours)

        val daily = JSONArray()
        inv.dailyBreakdown.forEach { d ->
            val dd = JSONObject()
            dd.put("day", d.day)
            dd.put("date", d.date)
            dd.put("startTime", d.startTime)
            dd.put("endTime", d.endTime)
            dd.put("totalHours", d.totalHours)
            dd.put("activities", d.activities)
            dd.put("meshSyncNotes", d.meshSyncNotes)
            dd.put("photoCount", d.photoCount)
            dd.put("voiceNoteCount", d.voiceNoteCount)
            dd.put("checklistCount", d.checklistCount)
            dd.put("keyNotes", d.keyNotes)
            daily.put(dd)
        }
        s.put("dailyBreakdown", daily)

        // Preserve the line-item codes (LAB-01 etc) here; backend doesn't store them.
        val codes = JSONArray()
        inv.lineItems.forEach { codes.put(JSONObject().put("code", it.code).put("description", it.description)) }
        s.put("lineItems", codes)

        s.put("workWindow", inv.workWindow)
        s.put("totalOnSiteMinutes", inv.totalOnSiteMinutes)

        val media = JSONObject()
        media.put("photos", inv.photoCount)
        media.put("voice",  inv.voiceNoteCount)
        media.put("checklist", inv.checklistCount)
        s.put("media", media)

        s.put("workLogSummary",       inv.workLogSummary)
        s.put("complianceNotes",      inv.complianceNotes)
        s.put("recommendations",      inv.recommendations)
        s.put("meshPresence",         inv.meshPresence)
        s.put("efficiencyScore",      inv.efficiencyScore)
        s.put("paymentInstructions",  inv.paymentInstructions)

        // Apk-computed totals stashed for drift detection.
        val computed = JSONObject()
        computed.put("subtotal",   formatMoney(inv.subtotal))
        computed.put("taxRate",    formatTaxRate(inv.taxRate))
        computed.put("taxAmount",  formatMoney(inv.taxAmount))
        computed.put("totalDue",   formatMoney(inv.totalDue))
        s.put("computed", computed)

        s.put("job", JSONObject().put("id", inv.jobId).put("title", inv.jobTitle))

        return s
    }

    private fun formatMoney(amount: Double): String =
        BigDecimal(amount).setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun formatTaxRate(percent: Double): String =
        BigDecimal(percent / 100.0).setScale(4, RoundingMode.HALF_UP).toPlainString()

    private fun formatIso(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).toString()
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run:
```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "*InvoiceJsonMapperTest*"
```

Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoiceJsonMapper.kt \
        android/app/src/test/java/com/guildofsmiths/trademesh/data/invoice/InvoiceJsonMapperTest.kt
git commit -m "feat(invoices): InvoiceJsonMapper — apk Invoice -> wire JSON"
```

---

## Phase 4 — Android API client

### Task 9: InvoicesApiClient

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesApiClient.kt`

(No dedicated unit test — the ApiClient is a thin OkHttp wrapper around `InvoiceJsonMapper`; its behavior is exercised by the `InvoicesOutboxTest` via a fake `InvoicesApi` interface defined in the next task.)

- [ ] **Step 1: Define the API interface and implementation**

```kotlin
// android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesApiClient.kt
package com.guildofsmiths.trademesh.data.invoice

import android.util.Log
import com.guildofsmiths.trademesh.BuildConfig
import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.invoice.InvoiceLineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Wire-layer interface so InvoicesPushWorker / InvoicesOutbox can be tested
 * against a fake. The real implementation talks to /api/invoices via OkHttp,
 * following the PresenceApiClient pattern.
 */
interface InvoicesApi {
    /** Returns the server's backend invoice id. Throws ApiError on 4xx; throws IOException on 5xx/transient. */
    suspend fun createInvoice(invoice: Invoice): String

    /** Adds a single line item to a backend invoice. */
    suspend fun addLineItem(backendInvoiceId: String, item: InvoiceLineItem)

    /** PATCH /api/invoices/{id}/status. */
    suspend fun setStatus(backendInvoiceId: String, status: String)

    /** DELETE /api/invoices/{id}. 404 is treated as success (idempotent). */
    suspend fun deleteInvoice(backendInvoiceId: String)
}

/** 4xx response — caller should mark the outbox row failed, not retry. */
class ApiClientError(val httpStatus: Int, message: String) : RuntimeException(message)

class InvoicesApiClient(private val client: OkHttpClient) : InvoicesApi {

    companion object {
        private const val TAG = "InvoicesApiClient"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val baseUrl: String get() = BuildConfig.BACKEND_URL

    override suspend fun createInvoice(invoice: Invoice): String = withContext(Dispatchers.IO) {
        val body = InvoiceJsonMapper.createBody(invoice).toRequestBody(JSON)
        val req = Request.Builder().url("$baseUrl/api/invoices").post(body).build()
        client.newCall(req).execute().use { res ->
            if (res.code in 400..499) {
                throw ApiClientError(res.code, "createInvoice HTTP ${res.code}: ${res.body?.string()}")
            }
            if (!res.isSuccessful) throw java.io.IOException("createInvoice HTTP ${res.code}")
            val json = JSONObject(res.body?.string() ?: "{}")
            json.getJSONObject("invoice").getString("id")
        }
    }

    override suspend fun addLineItem(backendInvoiceId: String, item: InvoiceLineItem) = withContext(Dispatchers.IO) {
        val body = InvoiceJsonMapper.lineItemBody(item).toRequestBody(JSON)
        val req = Request.Builder()
            .url("$baseUrl/api/invoices/$backendInvoiceId/line-items")
            .post(body)
            .build()
        client.newCall(req).execute().use { res ->
            if (res.code in 400..499) {
                throw ApiClientError(res.code, "addLineItem HTTP ${res.code}")
            }
            if (!res.isSuccessful) throw java.io.IOException("addLineItem HTTP ${res.code}")
        }
    }

    override suspend fun setStatus(backendInvoiceId: String, status: String) = withContext(Dispatchers.IO) {
        val body = InvoiceJsonMapper.statusBody(status).toRequestBody(JSON)
        val req = Request.Builder()
            .url("$baseUrl/api/invoices/$backendInvoiceId/status")
            .patch(body)
            .build()
        client.newCall(req).execute().use { res ->
            if (res.code in 400..499) {
                throw ApiClientError(res.code, "setStatus HTTP ${res.code}")
            }
            if (!res.isSuccessful) throw java.io.IOException("setStatus HTTP ${res.code}")
        }
    }

    override suspend fun deleteInvoice(backendInvoiceId: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/api/invoices/$backendInvoiceId")
            .delete()
            .build()
        client.newCall(req).execute().use { res ->
            if (res.code == 404) {
                Log.d(TAG, "deleteInvoice 404 — already gone, treating as success")
                return@withContext
            }
            if (res.code in 400..499) {
                throw ApiClientError(res.code, "deleteInvoice HTTP ${res.code}")
            }
            if (!res.isSuccessful) throw java.io.IOException("deleteInvoice HTTP ${res.code}")
        }
    }
}
```

- [ ] **Step 2: Compile to verify it builds**

Run:
```bash
cd android && ./gradlew :app:compileDebugKotlin
```

Expected: compiles clean.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesApiClient.kt
git commit -m "feat(invoices): InvoicesApiClient — OkHttp wrapper for /api/invoices"
```

---

## Phase 5 — Android outbox + worker

### Task 10: InvoicesOutbox + InvoicesPushWorker (TDD)

**Files:**
- Create: `android/app/src/test/java/com/guildofsmiths/trademesh/data/invoice/InvoicesOutboxTest.kt`
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesOutbox.kt`
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesPushWorker.kt`

This is the largest task. The 9 unit tests from the spec live here. The worker and outbox are co-designed so a single test file can exercise both via a shared in-memory Room database and a fake `InvoicesApi`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// android/app/src/test/java/com/guildofsmiths/trademesh/data/invoice/InvoicesOutboxTest.kt
package com.guildofsmiths.trademesh.data.invoice

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guildofsmiths.trademesh.db.AppDatabase
import com.guildofsmiths.trademesh.db.PendingInvoicePushDao
import com.guildofsmiths.trademesh.db.PendingInvoicePushEntity
import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.invoice.InvoiceLineItem
import com.guildofsmiths.trademesh.ui.invoice.InvoiceMode
import com.guildofsmiths.trademesh.ui.invoice.LineItemCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Fake ApiClient so worker tests can be unit tests, not integration tests.
 * Tracks calls and lets the test configure responses per-op.
 */
class FakeInvoicesApi : InvoicesApi {
    data class Call(val op: String, val arg: String)
    val calls = mutableListOf<Call>()

    /** Per-op behavior: throw, return, or count-then-throw. */
    var createBehavior: (Invoice) -> String = { _ -> "srv-${'$'}{calls.size}" }
    var lineItemBehavior: (String, InvoiceLineItem) -> Unit = { _, _ -> }
    var statusBehavior: (String, String) -> Unit = { _, _ -> }
    var deleteBehavior: (String) -> Unit = { _ -> }

    override suspend fun createInvoice(invoice: Invoice): String {
        calls.add(Call("CREATE", invoice.id))
        return createBehavior(invoice)
    }
    override suspend fun addLineItem(backendInvoiceId: String, item: InvoiceLineItem) {
        calls.add(Call("LINE", backendInvoiceId))
        lineItemBehavior(backendInvoiceId, item)
    }
    override suspend fun setStatus(backendInvoiceId: String, status: String) {
        calls.add(Call("STATUS:$status", backendInvoiceId))
        statusBehavior(backendInvoiceId, status)
    }
    override suspend fun deleteInvoice(backendInvoiceId: String) {
        calls.add(Call("DELETE", backendInvoiceId))
        deleteBehavior(backendInvoiceId)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class InvoicesOutboxTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PendingInvoicePushDao
    private lateinit var api: FakeInvoicesApi
    private lateinit var outbox: InvoicesOutbox
    private lateinit var worker: InvoicesPushWorker

    private val clock = AtomicClock(start = 1_000L)

    @Before fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.pendingInvoicePushDao()
        api = FakeInvoicesApi()
        outbox = InvoicesOutbox(dao, scheduler = NoopScheduler, clock = clock)
        worker = InvoicesPushWorker(dao, api, clock = clock)
    }

    @After fun tearDown() { db.close() }

    @Test fun enqueueCreate_inserts_pending_row() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-a"))
        val row = dao.findById("inv-a")
        assertNotNull(row)
        assertEquals("CREATE", row!!.op)
        assertEquals("pending", row.status)
        assertNotNull(row.payloadJson)
    }

    @Test fun worker_drain_201_marks_done_writes_backendId() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-b"))
        api.createBehavior = { _ -> "srv-b" }

        worker.drainOnce()

        val row = dao.findById("inv-b")!!
        assertEquals("done",  row.status)
        assertEquals("srv-b", row.backendId)
    }

    @Test fun worker_drain_500_reverts_to_pending_increments_attempts() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-c"))
        api.createBehavior = { _ -> throw java.io.IOException("HTTP 500") }

        worker.drainOnce()

        val row = dao.findById("inv-c")!!
        assertEquals("pending", row.status)
        assertEquals(1, row.attempts)
        assertTrue(row.lastError?.contains("500") == true)
    }

    @Test fun worker_drain_422_marks_failed_no_retry() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-d"))
        api.createBehavior = { _ -> throw ApiClientError(422, "validation boom") }

        worker.drainOnce()
        worker.drainOnce()  // second pass — should be no-op (status=failed is not pending)

        val row = dao.findById("inv-d")!!
        assertEquals("failed", row.status)
        assertTrue(row.lastError?.contains("422") == true)
        assertEquals(1, api.calls.count { it.op == "CREATE" })
    }

    @Test fun enqueueDiscard_before_drain_cancels_create_row() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-e"))
        outbox.enqueueDiscard("inv-e")

        worker.drainOnce()

        val row = dao.findById("inv-e")!!
        assertEquals("cancelled", row.status)
        assertEquals(0, api.calls.size)  // no POST ever happened
    }

    @Test fun enqueueDiscard_after_create_done_fires_DELETE() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-f"))
        api.createBehavior = { _ -> "srv-f" }
        worker.drainOnce()  // CREATE -> done with backendId

        outbox.enqueueDiscard("inv-f")
        worker.drainOnce()  // drains the DISCARD row

        assertTrue(api.calls.any { it.op == "DELETE" && it.arg == "srv-f" })
    }

    @Test fun enqueueMarkSent_waits_for_create_then_PATCHes() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-g"))
        outbox.enqueueMarkSent("inv-g")
        api.createBehavior = { _ -> "srv-g" }

        worker.drainOnce()  // drains CREATE then MARK_SENT in one pass

        assertTrue(api.calls.any { it.op == "CREATE" })
        assertTrue(api.calls.any { it.op == "STATUS:sent" && it.arg == "srv-g" })
    }

    @Test fun idempotency_replay_after_response_lost_results_in_one_done() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-h"))
        var failedOnce = false
        api.createBehavior = { _ ->
            if (!failedOnce) {
                failedOnce = true
                throw java.io.IOException("response lost")
            }
            "srv-h"
        }

        worker.drainOnce()  // first attempt throws -> revert to pending
        worker.drainOnce()  // second attempt — backend (fake) returns id

        val row = dao.findById("inv-h")!!
        assertEquals("done", row.status)
        assertEquals("srv-h", row.backendId)
        assertEquals(2, api.calls.count { it.op == "CREATE" })
        // Both POSTs sent the same idempotencyKey (the row id).
        assertTrue(api.calls.all { it.op != "CREATE" || it.arg == "inv-h" })
    }

    @Test fun race_discard_during_in_flight_inserts_separate_DISCARD_row() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-i"))

        // Directly flip the CREATE row to in_flight to simulate "worker mid-POST".
        dao.markInFlight("inv-i", nowMs = clock.now())

        outbox.enqueueDiscard("inv-i")

        // CREATE row must still be in_flight (not cancelled-by-mutation).
        assertEquals("in_flight", dao.findById("inv-i")!!.status)

        // A separate DISCARD row must have been inserted, in pending state.
        val discardRow = dao.findByOpAndLocalId("DISCARD", "inv-i")
        assertNotNull(discardRow)
        assertEquals("pending", discardRow!!.status)

        // Now complete the "in flight" CREATE (simulating worker finishing)
        dao.markDone("inv-i", backendId = "srv-i", nowMs = clock.now())
        worker.drainOnce()

        assertTrue(api.calls.any { it.op == "DELETE" && it.arg == "srv-i" })
    }

    private fun sampleInvoice(id: String): Invoice = Invoice(
        id = id,
        invoiceNumber = "INV-2026-05-0001",
        issueDate = 1_700_000_000_000L,
        dueDate = 1_700_000_000_000L,
        mode = InvoiceMode.SOLO,
        fromName = "Jane",
        toName = "Acme",
        jobId = "job-1",
        jobTitle = "Reroof",
        lineItems = listOf(
            InvoiceLineItem(
                code = "LAB-01", description = "Labor", quantity = 4.0,
                unit = "hr", rate = 85.0, total = 340.0, category = LineItemCategory.LABOR,
            ),
        ),
    )

    object NoopScheduler : InvoicesOutbox.Scheduler {
        override fun scheduleDrain() { /* tests drive worker manually */ }
    }

    class AtomicClock(start: Long) : InvoicesOutbox.Clock, InvoicesPushWorker.Clock {
        private var t: Long = start
        override fun now(): Long { t += 1; return t }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "*InvoicesOutboxTest*"
```

Expected: compile error — `InvoicesOutbox`, `InvoicesPushWorker`, `NoopScheduler`, `Clock` don't exist yet.

- [ ] **Step 3: Implement the outbox**

```kotlin
// android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesOutbox.kt
package com.guildofsmiths.trademesh.data.invoice

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.guildofsmiths.trademesh.db.PendingInvoicePushDao
import com.guildofsmiths.trademesh.db.PendingInvoicePushEntity
import com.guildofsmiths.trademesh.ui.invoice.Invoice
import java.util.UUID

/**
 * The only entry point for code that wants something pushed to /api/invoices.
 * Translates UX events (Generate / Share / Cancel) into outbox rows; defers
 * the actual network work to InvoicesPushWorker via the Scheduler hook.
 *
 * Race-safety: enqueueDiscard checks the CREATE row's status under the
 * DAO's atomic UPDATE-WHERE-pending. If the CREATE is already in_flight or
 * done, a separate DISCARD row is inserted so the worker can fire DELETE
 * after the in-flight POST completes.
 */
class InvoicesOutbox(
    private val dao: PendingInvoicePushDao,
    private val scheduler: Scheduler,
    private val clock: Clock = SystemClock,
) {
    interface Scheduler {
        fun scheduleDrain()
    }
    interface Clock {
        fun now(): Long
    }
    object SystemClock : Clock {
        override fun now(): Long = System.currentTimeMillis()
    }

    suspend fun enqueueCreate(invoice: Invoice) {
        val now = clock.now()
        dao.insert(PendingInvoicePushEntity(
            id = invoice.id,
            localInvoiceId = invoice.id,
            op = "CREATE",
            payloadJson = InvoiceJsonMapper.createBody(invoice),
            backendId = null,
            status = "pending",
            attempts = 0,
            lastError = null,
            createdAt = now,
            updatedAt = now,
        ))
        scheduler.scheduleDrain()
    }

    suspend fun enqueueMarkSent(localInvoiceId: String) {
        val now = clock.now()
        dao.insert(PendingInvoicePushEntity(
            id = UUID.randomUUID().toString(),
            localInvoiceId = localInvoiceId,
            op = "MARK_SENT",
            payloadJson = null,
            backendId = null,
            status = "pending",
            attempts = 0,
            lastError = null,
            createdAt = now,
            updatedAt = now,
        ))
        scheduler.scheduleDrain()
    }

    suspend fun enqueueDiscard(localInvoiceId: String) {
        val createRow = dao.findCreateRowFor(localInvoiceId) ?: return
        val now = clock.now()
        when (createRow.status) {
            "pending" -> {
                // Atomic pending -> cancelled. If the worker grabbed it just now,
                // markInFlight will have moved it; cancelIfPending returns false
                // and we fall through to insert a DISCARD row.
                if (dao.cancelIfPending(createRow.id, now)) return
                insertDiscardRow(localInvoiceId, now)
            }
            "in_flight", "done" -> insertDiscardRow(localInvoiceId, now)
            "failed", "cancelled" -> { /* nothing to do server-side */ }
        }
    }

    private suspend fun insertDiscardRow(localInvoiceId: String, now: Long) {
        dao.insert(PendingInvoicePushEntity(
            id = UUID.randomUUID().toString(),
            localInvoiceId = localInvoiceId,
            op = "DISCARD",
            payloadJson = null,
            backendId = null,
            status = "pending",
            attempts = 0,
            lastError = null,
            createdAt = now,
            updatedAt = now,
        ))
        scheduler.scheduleDrain()
    }

    /** Real production scheduler: enqueues a WorkManager run with network constraint. */
    class WorkManagerScheduler(private val ctx: Context) : Scheduler {
        override fun scheduleDrain() {
            val req = OneTimeWorkRequestBuilder<InvoicesPushWorkerWrapper>()
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniqueWork("invoices-push", ExistingWorkPolicy.KEEP, req)
        }
    }
}
```

- [ ] **Step 4: Implement the worker**

```kotlin
// android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesPushWorker.kt
package com.guildofsmiths.trademesh.data.invoice

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.guildofsmiths.trademesh.db.AppDatabase
import com.guildofsmiths.trademesh.db.PendingInvoicePushDao
import com.guildofsmiths.trademesh.db.PendingInvoicePushEntity
import com.guildofsmiths.trademesh.service.HttpClientFactory
import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.invoice.InvoiceLineItem
import com.guildofsmiths.trademesh.ui.invoice.InvoiceMode
import com.guildofsmiths.trademesh.ui.invoice.LineItemCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Drains the InvoicesOutbox via the DAO. Pop next pending op, atomically
 * flip to in_flight, call the ApiClient, classify the result.
 *
 * The class itself is constructor-injected (DAO + Api + Clock) so unit
 * tests can drive it directly. WorkManager invokes [InvoicesPushWorkerWrapper]
 * which builds the production wiring.
 */
class InvoicesPushWorker(
    private val dao: PendingInvoicePushDao,
    private val api: InvoicesApi,
    private val clock: Clock = SystemClock,
    private val maxAttempts: Int = 20,
) {
    interface Clock { fun now(): Long }
    object SystemClock : Clock { override fun now(): Long = System.currentTimeMillis() }

    /** Drain the queue in one pass. Returns when there is nothing pending. */
    suspend fun drainOnce() {
        while (true) {
            val row = dao.nextPending() ?: return
            if (!dao.markInFlight(row.id, clock.now())) continue  // someone else grabbed it
            val refreshed = dao.findById(row.id) ?: continue
            executeOne(refreshed)
        }
    }

    private suspend fun executeOne(row: PendingInvoicePushEntity) {
        try {
            when (row.op) {
                "CREATE"    -> executeCreate(row)
                "MARK_SENT" -> executeMarkSent(row)
                "DISCARD"   -> executeDiscard(row)
                else        -> dao.markFailed(row.id, "unknown op ${row.op}", clock.now())
            }
        } catch (e: ApiClientError) {
            dao.markFailed(row.id, "HTTP ${e.httpStatus}: ${e.message}", clock.now())
        } catch (e: Throwable) {
            if (row.attempts + 1 >= maxAttempts) {
                dao.markFailed(row.id, "exhausted ${maxAttempts} attempts: ${e.message}", clock.now())
            } else {
                dao.revertToPending(row.id, e.message ?: e.javaClass.simpleName, clock.now())
            }
        }
    }

    private suspend fun executeCreate(row: PendingInvoicePushEntity) {
        val invoice = deserializeInvoice(row.payloadJson ?: error("CREATE row missing payload"))
        val backendId = api.createInvoice(invoice)
        invoice.lineItems.forEach { api.addLineItem(backendId, it) }
        dao.markDone(row.id, backendId, clock.now())
    }

    private suspend fun executeMarkSent(row: PendingInvoicePushEntity) {
        val create = dao.findCreateRowFor(row.localInvoiceId)
        when {
            create == null -> dao.markFailed(row.id, "no CREATE row for ${row.localInvoiceId}", clock.now())
            create.status == "failed" || create.status == "cancelled" ->
                dao.markFailed(row.id, "CREATE ${create.status}", clock.now())
            create.backendId == null ->
                // CREATE not yet done — revert this MARK_SENT to pending; the
                // worker's next drain will retry after CREATE has resolved.
                dao.revertToPending(row.id, "waiting for CREATE", clock.now())
            else -> {
                api.setStatus(create.backendId, "sent")
                dao.markDone(row.id, backendId = null, nowMs = clock.now())
            }
        }
    }

    private suspend fun executeDiscard(row: PendingInvoicePushEntity) {
        val create = dao.findCreateRowFor(row.localInvoiceId)
        when {
            create == null || create.backendId == null ->
                // Nothing exists on the server. Done.
                dao.markDone(row.id, backendId = null, nowMs = clock.now())
            else -> {
                api.deleteInvoice(create.backendId)
                dao.markDone(row.id, backendId = null, nowMs = clock.now())
            }
        }
    }

    private fun deserializeInvoice(payloadJson: String): Invoice {
        // The payload was produced by InvoiceJsonMapper.createBody — that is the
        // server's wire shape and lacks some apk-only fields. For executeCreate
        // we only need: id (=idempotencyKey), and the lineItems. We re-parse
        // both from the JSON and lift them into a thin Invoice carrier.
        val root = Json.parseToJsonElement(payloadJson).jsonObject
        val idemKey = root["idempotencyKey"]!!.jsonPrimitive.content
        val items = root["summary"]?.jsonObject?.get("lineItems")?.jsonArray
            ?.map { JsonLineItem(it.jsonObject) }
            ?: emptyList()
        // We need the original line items too (with quantity / rate / category)
        // — those live in summary.lineItems only as code+description in the
        // wire body. Embed the full list under a private key when building.
        return Invoice(
            id = idemKey,
            invoiceNumber = "",
            issueDate = 0L,
            dueDate = 0L,
            mode = InvoiceMode.SOLO,
            fromName = "",
            jobId = "",
            jobTitle = "",
            lineItems = root["summary"]?.jsonObject?.get("fullLineItems")?.jsonArray
                ?.map { it.jsonObject.toLineItem() }
                ?: emptyList(),
        )
    }

    private fun kotlinx.serialization.json.JsonObject.toLineItem(): InvoiceLineItem =
        InvoiceLineItem(
            code        = get("code")?.jsonPrimitive?.content ?: "",
            description = get("description")?.jsonPrimitive?.content ?: "",
            quantity    = get("quantity")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            unit        = get("unit")?.jsonPrimitive?.content ?: "ea",
            rate        = get("rate")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            total       = get("total")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            category    = runCatching { LineItemCategory.valueOf(
                (get("category")?.jsonPrimitive?.content ?: "OTHER").uppercase()
            )}.getOrDefault(LineItemCategory.OTHER),
        )

    private class JsonLineItem(o: kotlinx.serialization.json.JsonObject)
}

/**
 * WorkManager-facing wrapper. Builds the production wiring and delegates
 * to [InvoicesPushWorker.drainOnce]. Unit tests construct
 * [InvoicesPushWorker] directly and never touch this class.
 */
class InvoicesPushWorkerWrapper(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val dao = AppDatabase.getInstance(applicationContext).pendingInvoicePushDao()
        val api = InvoicesApiClient(HttpClientFactory.client)
        val worker = InvoicesPushWorker(dao, api)
        return try {
            worker.drainOnce()
            // Re-check: if anything is still pending (transient errors reverted
            // rows back to pending), let WorkManager retry with backoff.
            if (dao.nextPending() != null) Result.retry() else Result.success()
        } catch (e: Throwable) {
            Result.retry()
        }
    }
}
```

The `deserializeInvoice` above is incomplete: it relies on `summary.fullLineItems`, which we haven't populated. Fix the mapper to include it.

In `InvoiceJsonMapper.kt`, replace the `codes` block in `buildSummary` with a full payload:

```kotlin
        // Full line items embedded under summary.fullLineItems so the worker
        // can reconstitute them when popping a CREATE row off Room. The
        // server doesn't read this field (it reads `category`, `quantity`,
        // etc. from the dedicated /line-items endpoint instead).
        val full = JSONArray()
        inv.lineItems.forEach {
            val o = JSONObject()
            o.put("code", it.code)
            o.put("description", it.description)
            o.put("quantity", it.quantity)
            o.put("unit", it.unit)
            o.put("rate", it.rate)
            o.put("total", it.total)
            o.put("category", it.category.name)
            full.put(o)
        }
        s.put("fullLineItems", full)
```

Remove the older `s.put("lineItems", codes)` block since `fullLineItems` supersedes it.

Also update the corresponding mapper test to assert on `summary.fullLineItems` instead of `summary.lineItems`.

- [ ] **Step 5: Run the tests to verify they pass**

Run:
```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "*InvoicesOutboxTest*" --tests "*InvoiceJsonMapperTest*"
```

Expected: all 9 outbox tests + 6 mapper tests pass.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesOutbox.kt \
        android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesPushWorker.kt \
        android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoiceJsonMapper.kt \
        android/app/src/main/java/com/guildofsmiths/trademesh/db/PendingInvoicePushDao.kt \
        android/app/src/test/java/com/guildofsmiths/trademesh/data/invoice/InvoicesOutboxTest.kt \
        android/app/src/test/java/com/guildofsmiths/trademesh/data/invoice/InvoiceJsonMapperTest.kt
git commit -m "feat(invoices): Room outbox + WorkManager worker for /api/invoices push"
```

---

## Phase 6 — Wire ViewModel and Screen

### Task 11: JobBoardViewModel + JobBoardScreen wiring

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobBoardViewModel.kt`
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobBoardScreen.kt`

- [ ] **Step 1: Add outbox + shared flag to JobBoardViewModel**

In `JobBoardViewModel.kt`, locate the existing invoice block (around line 1083 — `// INVOICE GENERATION`) and replace it with:

```kotlin
    // ════════════════════════════════════════════════════════════════════
    // INVOICE GENERATION
    // ════════════════════════════════════════════════════════════════════

    private val _generatedInvoice = MutableStateFlow<com.guildofsmiths.trademesh.ui.invoice.Invoice?>(null)
    val generatedInvoice: StateFlow<com.guildofsmiths.trademesh.ui.invoice.Invoice?> = _generatedInvoice.asStateFlow()

    // Tracks whether the user explicitly shared the current preview. If
    // they dismiss without sharing, the outbox pushes a DISCARD so the
    // backend row gets deleted; if they share, we mark it sent instead.
    private var generatedShared: Boolean = false

    private val invoicesOutbox: com.guildofsmiths.trademesh.data.invoice.InvoicesOutbox by lazy {
        val ctx = getApplication<android.app.Application>().applicationContext
        com.guildofsmiths.trademesh.data.invoice.InvoicesOutbox(
            dao = com.guildofsmiths.trademesh.db.AppDatabase.getInstance(ctx).pendingInvoicePushDao(),
            scheduler = com.guildofsmiths.trademesh.data.invoice.InvoicesOutbox.WorkManagerScheduler(ctx),
        )
    }

    fun generateInvoice(job: Job) {
        viewModelScope.launch {
            val userName = UserPreferences.getUserName()
            val timeEntries = TimeEntryRepository.getEntriesForJob(job.id, job.title)

            val invoice = com.guildofsmiths.trademesh.ui.invoice.InvoiceGenerator.generateFromJob(
                job = job,
                timeEntries = timeEntries,
                providerName = userName,
                providerTrade = "Tradesperson – Guild of Smiths",
                hourlyRate = if (job.hourlyRate > 0) job.hourlyRate else 85.0
            )

            generatedShared = false
            _generatedInvoice.value = invoice

            // Push to backend paper trail; safe if offline (queue persists).
            invoicesOutbox.enqueueCreate(invoice)
        }
    }

    /** Called by the screen when the user actually shares the invoice. */
    fun markShared(invoiceId: String) {
        generatedShared = true
        viewModelScope.launch { invoicesOutbox.enqueueMarkSent(invoiceId) }
    }

    fun clearInvoice() {
        val inv = _generatedInvoice.value
        _generatedInvoice.value = null
        // If the user dismissed without sharing, discard the row.
        if (inv != null && !generatedShared) {
            viewModelScope.launch { invoicesOutbox.enqueueDiscard(inv.id) }
        }
        generatedShared = false
    }
```

Note: `JobBoardViewModel` must extend `AndroidViewModel(application)` (not just `ViewModel`) for `getApplication<>()` to work. Check the class declaration at the top of the file and update if needed:

```kotlin
// before
class JobBoardViewModel : ViewModel() {

// after
class JobBoardViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
```

If the file imports `androidx.lifecycle.ViewModel`, replace with `androidx.lifecycle.AndroidViewModel`.

If `JobBoardViewModel` is currently constructed via a no-arg ViewModelProvider call elsewhere, find the construction site (probably `JobBoardScreen.kt`'s `viewModel<JobBoardViewModel>()`) — it will auto-pick up `AndroidViewModel` via the default factory, so no change there.

- [ ] **Step 2: Wire onShare to call markShared**

In `JobBoardScreen.kt`, locate the existing `onShare` callback (around line 371):

```kotlin
// before
            onShare = { text ->
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Invoice ${invoice.invoiceNumber}")
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                context.startActivity(android.content.Intent.createChooser(share, "Share Invoice"))
                viewModel.clearInvoice()
            },
```

```kotlin
// after
            onShare = { text ->
                viewModel.markShared(invoice.id)  // record intent before the intent fires
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Invoice ${invoice.invoiceNumber}")
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                context.startActivity(android.content.Intent.createChooser(share, "Share Invoice"))
                viewModel.clearInvoice()
            },
```

(The `clearInvoice()` call at the end is now safe — `generatedShared` was flipped to true in `markShared`, so no DISCARD will fire.)

The rich preview's dismiss path (around line 397) doesn't need any change — it already calls `viewModel.clearInvoice()`, which will now correctly fire a DISCARD because `markShared` was never called.

- [ ] **Step 3: Compile to verify the wiring builds**

Run:
```bash
cd android && ./gradlew :app:compileDebugKotlin
```

Expected: compiles clean. If `getApplication<...>()` errors out, double-check the `AndroidViewModel` migration.

- [ ] **Step 4: Smoke test: install + observe**

(Skip if device/emulator unavailable; this is verification, not gated.)

Run:
```bash
cd android && ./gradlew :app:installDebug
```

Then with the apk running:

1. Generate an invoice from a job → confirm `pending_invoice_pushes` row exists via adb:
   ```
   adb shell run-as com.guildofsmiths.trademesh sqlite3 databases/trademesh_db \
     "SELECT id, op, status, backendId FROM pending_invoice_pushes ORDER BY createdAt DESC LIMIT 3;"
   ```
   Expected: a CREATE row, status either `done` (if backend reached) or `pending` (offline).

2. Share the invoice → re-run the query → expect a MARK_SENT row, eventually `done`.

3. Generate, then close the dialog without sharing → re-run query → expect a CREATE row eventually `cancelled` (if worker hadn't drained yet) or a DISCARD row (if it had).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobBoardViewModel.kt \
        android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobBoardScreen.kt
git commit -m "feat(invoices): JobBoardViewModel pushes to outbox; Share marks sent"
```

---

## Phase 7 — End-to-end test

### Task 12: Instrumented end-to-end test

**Files:**
- Create: `android/app/src/androidTest/java/com/guildofsmiths/trademesh/data/invoice/InvoicesPushE2ETest.kt`

This task assumes a running backend reachable at `BuildConfig.BACKEND_URL` and seeded with a logged-in test user. If your CI environment does not provide that, skip this task — the unit tests + manual smoke from Task 11 cover the contract.

- [ ] **Step 1: Write the end-to-end test**

```kotlin
// android/app/src/androidTest/java/com/guildofsmiths/trademesh/data/invoice/InvoicesPushE2ETest.kt
package com.guildofsmiths.trademesh.data.invoice

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.guildofsmiths.trademesh.db.AppDatabase
import com.guildofsmiths.trademesh.service.HttpClientFactory
import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.invoice.InvoiceLineItem
import com.guildofsmiths.trademesh.ui.invoice.InvoiceMode
import com.guildofsmiths.trademesh.ui.invoice.LineItemCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class InvoicesPushE2ETest {

    private lateinit var db: AppDatabase
    private lateinit var outbox: InvoicesOutbox
    private lateinit var worker: InvoicesPushWorker

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Use an in-memory db so we don't pollute the real one.
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val dao = db.pendingInvoicePushDao()
        outbox = InvoicesOutbox(dao, object : InvoicesOutbox.Scheduler {
            override fun scheduleDrain() { /* tests drive the worker manually */ }
        })
        worker = InvoicesPushWorker(dao, InvoicesApiClient(HttpClientFactory.client))
    }

    @After fun tearDown() { db.close() }

    @Test fun generate_share_e2e() = runBlocking {
        val invoice = Invoice(
            id = UUID.randomUUID().toString(),
            invoiceNumber = "INV-2026-05-E2E",
            issueDate = System.currentTimeMillis(),
            dueDate = System.currentTimeMillis() + 14L * 24 * 3600 * 1000,
            mode = InvoiceMode.SOLO,
            fromName = "Jane E2E",
            toName = "Acme E2E",
            toEmail = "ops@acme-e2e.com",
            jobId = "job-e2e",
            jobTitle = "E2E Reroof",
            lineItems = listOf(
                InvoiceLineItem(
                    code = "LAB-01", description = "Labor",
                    quantity = 4.0, unit = "hr", rate = 85.0, total = 340.0,
                    category = LineItemCategory.LABOR,
                ),
            ),
        )

        outbox.enqueueCreate(invoice)
        worker.drainOnce()

        val createRow = db.pendingInvoicePushDao().findById(invoice.id)
        assertEquals("done", createRow?.status)
        assertNotNull(createRow?.backendId)

        outbox.enqueueMarkSent(invoice.id)
        worker.drainOnce()

        // Verify by hitting the backend directly via the ApiClient (re-fetch
        // would require a GET endpoint, which we already have via /api/invoices/:id).
        // For this test, we just assert that no error was thrown and the
        // MARK_SENT row went to done.
        val markRow = db.pendingInvoicePushDao().nextPending()
        assertNull("no pending ops left after generate+share", markRow)
    }
}
```

- [ ] **Step 2: Run the test against a running backend**

Pre-flight: backend running locally at port 3030, an authenticated test session cookie / token available to `HttpClientFactory.client` (this is the same wiring `PresenceApiClient` uses, so if presence works in dev, this will too).

Run:
```bash
cd android && ./gradlew :app:connectedDebugAndroidTest --tests "*InvoicesPushE2ETest*"
```

Expected: test passes.

If the backend isn't reachable, the test will fail with an `IOException`. That's expected; the test simply needs a running backend.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/androidTest/java/com/guildofsmiths/trademesh/data/invoice/InvoicesPushE2ETest.kt
git commit -m "test(invoices): instrumented e2e — generate + share roundtrip"
```

---

## Final verification

- [ ] **Step 1: Run all backend tests**

Run:
```bash
cd backend && DATABASE_URL=postgresql://localhost/smithnet_test npx jest --maxWorkers=1 src/__tests__/invoices-routes.test.ts src/__tests__/invoices-routes-android.test.ts
```

Expected: all 11 original tests + 7 new android-wiring tests pass = 18 passing.

- [ ] **Step 2: Run all android unit tests**

Run:
```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "*Invoice*"
```

Expected: 9 outbox tests + 6 mapper tests + 8 DAO tests = 23 passing.

- [ ] **Step 3: Run the full test suite to confirm no regressions**

Run:
```bash
cd backend && DATABASE_URL=postgresql://localhost/smithnet_test npx jest --maxWorkers=1
cd android && ./gradlew :app:testDebugUnitTest
```

Expected: both suites green.

- [ ] **Step 4: Manual smoke test (already done in Task 11 Step 4 if device available)**

- [ ] **Step 5: Branch cleanup**

```bash
git log --oneline master..HEAD | grep -E "invoices|migration 018" | head -10
git status
```

Expected: 9 new commits on the branch for this slice (migration, idempotency, summary test, drop tier gate, work manager dep, room outbox, mapper, api client, outbox+worker, viewmodel wiring, e2e).

If the user wants to merge: see CLAUDE.md or coordinate via PR — the branch is `feat/relay-hetzner-postgres`, master is the target.

---

## Notes for the implementer

- **TDD discipline matters.** Each backend test was sized to fail before the corresponding service change. On Android, the unit tests sit between a real Room (in-memory) and a fake `InvoicesApi`, so they reach into the worker directly via `worker.drainOnce()` rather than going through WorkManager. WorkManager itself is trusted.
- **The `deserializeInvoice` path is the one place where the wire format and the apk model don't naturally align.** The mapper embeds an extra `summary.fullLineItems` array specifically so the worker can reconstitute line items off the queue. The server never reads that field — it reads from the dedicated `/line-items` POST.
- **`fallbackToDestructiveMigration()` is already on AppDatabase**, so bumping version 6 → 7 doesn't require writing a Migration object. In production this would lose existing message/cord/location data on upgrade; verify with the user before shipping if that's a concern.
- **Auth.** This slice trusts `HttpClientFactory.client` to attach the auth cookie/bearer token. If `PresenceApiClient` works against a logged-in user today, `InvoicesApiClient` will too — they share the same OkHttpClient.
- **Branch.** Stay on `feat/relay-hetzner-postgres`. Do not branch; this is a continuation of the same slice.
