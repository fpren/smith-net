# Phase 3 Slice 2 — audit-flush worker

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Move the synchronous `audit_entries` write off the request path. `auditLog.log()` enqueues a `kind='audit_flush'` job and returns immediately; a worker drains the queue under `pg_advisory_xact_lock(42)` and writes the SHA-chained row.

**Architecture:** New worker `auditFlushWorker.ts` registered in `runner.ts`. `auditLog.log()` keeps responsibility for id generation and body composition but stops touching pg; instead it calls `queue.enqueue({kind:'audit_flush', payload: bodyWithAuditId})`. The worker reproduces the chain-hash logic that used to live in `AuditLogManager`. JSONL buffering moves to a callback the worker invokes after successful INSERT.

**Tech Stack:** TypeScript, Express, pg (node-postgres), jest, real Postgres via `DATABASE_URL`.

**Scope guardrails:**
- 17 production callers of `auditLog.log()` already discard the return value — verified by grep. Production callers need ZERO code changes.
- One test file (`auditChain.test.ts`) reads the return value's `.checksum`. That test needs rewriting around `audit_entries` rows + a drain helper.
- Other route tests fire `auditLog.log()` implicitly and don't read `audit_entries`. They keep working without changes, BUT the `background_jobs` table accumulates `kind='audit_flush'` rows across tests. We add a global cleanup in jest's test setup (or per-suite where needed).
- The pg advisory lock semantics from Phase 2 stay intact — the worker still uses `pg_advisory_xact_lock(42)`. Concurrency guarantees unchanged.

---

## File Structure

**Create:**
- `backend/src/workers/auditFlushWorker.ts` — `tick(workerId)` claim/lock/insert/complete
- `backend/src/__tests__/audit-flush-worker.test.ts` — drain helper + chain integrity + concurrent + retry tests

**Modify:**
- `backend/src/auditLog.ts` — `log()` becomes enqueue; chain logic deleted; add `bufferFromWorker(entry)` for JSONL
- `backend/src/workers/runner.ts` — register `auditFlushTick` next to `geocodeTick`
- `backend/src/__tests__/auditChain.test.ts` — rewrite around drain helper + pg row reads
- `backend/src/__tests__/jest.setup.ts` if it exists, OR add per-suite cleanup that truncates `background_jobs` where `kind='audit_flush'` to keep test runs hermetic

---

## Task 0: Baseline check

**Files:** none

- [ ] **Step 1:** Verify 173 backend tests pass

```bash
cd /Users/fegensprenelon/smith-net/backend
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npm test 2>&1 | tail -10
```
Expected: `Test Suites: 23 passed, 23 total / Tests: 173 passed, 173 total`. If anything fails, stop and surface.

- [ ] **Step 2:** No commit (verification only).

---

## Task 1: Change `auditLog.log()` to enqueue; add `bufferFromWorker`

**Files:**
- Modify: `backend/src/auditLog.ts`

Goal: keep `log()` async (callers still `await` it), but its only pg interaction becomes `enqueue({kind:'audit_flush', payload})`. The worker (Task 2) does the chain hashing + INSERT + JSONL.

- [ ] **Step 1:** Edit signature

Change the return type:
```typescript
async log(
  action: AuditAction,
  actorId: string,
  metadata: Record<string, any> = {},
  options: { targetId?: string; ip?: string; userAgent?: string } = {}
): Promise<{ auditId: string; queued: true }> {
```

- [ ] **Step 2:** Replace the body with an enqueue path

Add at the top of the file:
```typescript
import { enqueue } from './queue/queue';
```

Replace the `log()` body. The dev-mode no-pg path stays (keeps in-memory + JSONL behavior) — but returns the same `{auditId, queued: true}` shape. The pg path becomes:

```typescript
async log(
  action: AuditAction,
  actorId: string,
  metadata: Record<string, any> = {},
  options: { targetId?: string; ip?: string; userAgent?: string } = {}
): Promise<{ auditId: string; queued: true }> {
  const auditId = `audit-${Date.now()}-${++this.entryCounter}`;
  const timestamp = Date.now();

  if (!isPgEnabled() || !pg) {
    // Dev mode: keep the previous in-memory + JSONL fallback.
    this.logInMemoryOnly(auditId, timestamp, action, actorId, metadata, options);
    return { auditId, queued: true };
  }

  // Production mode: enqueue. Worker will compute chain + INSERT.
  await enqueue({
    kind: 'audit_flush',
    dedupeKey: auditId,
    payload: {
      auditId,
      timestamp,
      action,
      actorId,
      targetId: options.targetId ?? null,
      metadata,
      ip: options.ip ?? null,
      userAgent: options.userAgent ?? null,
    },
  });
  return { auditId, queued: true };
}
```

- [ ] **Step 3:** Update `logInMemoryOnly` signature

The dev-mode path needs to accept the auditId+timestamp generated above:

```typescript
private logInMemoryOnly(
  auditId: string,
  timestamp: number,
  action: AuditAction,
  actorId: string,
  metadata: Record<string, any>,
  options: { targetId?: string; ip?: string; userAgent?: string }
): void {
  const prevChecksum = this.entries.length > 0
    ? this.entries[this.entries.length - 1].checksum
    : null;
  const entry: Omit<AuditEntry, 'checksum'> = {
    id: auditId,
    timestamp,
    action,
    actorId,
    targetId: options.targetId,
    metadata,
    ip: options.ip,
    userAgent: options.userAgent,
    prevChecksum,
  };
  const full: AuditEntry = { ...entry, checksum: this.generateChecksum(entry) };
  this.entries.push(full);
  this.bufferJsonl(full);
}
```

Return type changed from `AuditEntry` to `void` (callers don't use it).

- [ ] **Step 4:** Add `bufferFromWorker`

After the worker writes a row to pg it calls back here to seed the JSONL buffer and the in-memory mirror used by `query()` / `verifyIntegrity()` / `getStats()`:

```typescript
/**
 * Called by the auditFlushWorker after a successful pg INSERT.
 * Mirrors the row into the in-memory cache and JSONL buffer so that
 * query() / verifyIntegrity() / getStats() continue to work.
 */
bufferFromWorker(entry: AuditEntry): void {
  this.entries.push(entry);
  this.bufferJsonl(entry);
}
```

- [ ] **Step 5:** Delete the now-dead inline-write code path

Remove the `client = await pg.connect() / BEGIN / pg_advisory_xact_lock / INSERT / COMMIT` block that was inside `log()`. That logic is being moved to the worker (Task 2). Keep all of: enum, RetentionPolicy, generateChecksum, bufferJsonl, flushJsonl, flushNow, query, verifyIntegrity, getStats, cleanupOldEntries, getRetentionPolicy.

- [ ] **Step 6:** Type-check

```bash
cd /Users/fegensprenelon/smith-net/backend
npx tsc --noEmit
```
Expected: zero errors. If any caller has `const e = await auditLog.log(...)` and reads `e.checksum` or `e.id` — that's a caller that needs updating. We grep'd in advance: none in production. If tsc surfaces one, fix the caller to drop the access.

- [ ] **Step 7:** Commit

```bash
git add backend/src/auditLog.ts
git commit -m "refactor(audit): log() enqueues audit_flush job instead of inline pg write"
```

---

## Task 2: `auditFlushWorker.tick`

**Files:**
- Create: `backend/src/workers/auditFlushWorker.ts`

The chain hashing logic moves here verbatim from the old `auditLog.log`. The exported function is `tick(workerId): Promise<boolean>`.

- [ ] **Step 1:** Write the worker

```typescript
// backend/src/workers/auditFlushWorker.ts
//
// Phase 3 Slice 2: audit-flush worker. Drains kind='audit_flush' jobs,
// computes the SHA chain hash under pg_advisory_xact_lock(42), and writes
// the row to audit_entries.
//
// Replaces the inline write that lived in auditLog.log() through Phase 2.

import crypto from 'crypto';
import { pg, isPgEnabled } from '../db';
import { claimNext, complete, fail } from '../queue/queue';
import { auditLog, AuditAction, AuditEntry } from '../auditLog';
import { requestLogger } from '../log';

const KIND = 'audit_flush';

interface AuditFlushPayload {
  auditId: string;
  timestamp: number;
  action: AuditAction;
  actorId: string;
  targetId: string | null;
  metadata: Record<string, any>;
  ip: string | null;
  userAgent: string | null;
}

function computeHash(prev: string | null, p: AuditFlushPayload): string {
  const body = JSON.stringify({
    id: p.auditId,
    timestamp: p.timestamp,
    action: p.action,
    actorId: p.actorId,
    targetId: p.targetId ?? undefined,
    metadata: p.metadata,
  });
  return crypto.createHash('sha256').update((prev ?? '') + body).digest('hex');
}

export async function tick(workerId: string): Promise<boolean> {
  if (!isPgEnabled() || !pg) return false;
  const job = await claimNext(KIND, workerId);
  if (!job) return false;

  const p = job.payload as unknown as AuditFlushPayload;
  const client = await pg.connect();
  try {
    await client.query('BEGIN');
    // Constant key 42 — there's only one audit chain.
    await client.query('SELECT pg_advisory_xact_lock(42)');

    const prevR = await client.query<{ hash: string }>(
      `SELECT hash FROM audit_entries ORDER BY id DESC LIMIT 1`
    );
    const prevHash = prevR.rows[0]?.hash ?? null;
    const hash = computeHash(prevHash, p);

    await client.query(
      `INSERT INTO audit_entries
         (audit_id, actor_id, target_id, action, metadata, ip, user_agent, prev_hash, hash)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
       ON CONFLICT (audit_id) DO NOTHING`,
      [p.auditId, p.actorId, p.targetId, p.action, p.metadata, p.ip, p.userAgent, prevHash, hash]
    );
    await client.query('COMMIT');

    // Mirror into in-memory cache + JSONL so query()/verifyIntegrity()/getStats() still work.
    const entry: AuditEntry = {
      id: p.auditId,
      timestamp: p.timestamp,
      action: p.action,
      actorId: p.actorId,
      targetId: p.targetId ?? undefined,
      metadata: p.metadata,
      ip: p.ip ?? undefined,
      userAgent: p.userAgent ?? undefined,
      prevChecksum: prevHash,
      checksum: hash,
    };
    auditLog.bufferFromWorker(entry);

    await complete(job.id);
    requestLogger().info(
      { event: 'audit_flushed', jobId: job.id, auditId: p.auditId },
      'audit row flushed'
    );
    return true;
  } catch (err) {
    try { await client.query('ROLLBACK'); } catch { /* ignore */ }
    await fail(job.id, err as Error, { attempts: job.attempts, maxAttempts: job.max_attempts });
    requestLogger().warn(
      { event: 'audit_flush_failed', jobId: job.id, attempts: job.attempts, err: (err as Error).message },
      'audit flush failed'
    );
    return true;
  } finally {
    client.release();
  }
}
```

The `ON CONFLICT (audit_id) DO NOTHING` is defensive — if a worker dies after the INSERT commits but before `complete()` runs, the row stays in `state='running'` (and the operator's stuck-row reset puts it back to `queued`, then a fresh worker re-tries the same payload). The unique index on `audit_id` prevents a duplicate row; `claimNext` increments `attempts`, so `fail()` only marks dead when the attempt count exceeds max.

- [ ] **Step 2:** Type-check

```bash
npx tsc --noEmit
```
Expected: zero errors.

- [ ] **Step 3:** Commit

```bash
git add backend/src/workers/auditFlushWorker.ts
git commit -m "feat(worker): auditFlushWorker — drains audit_flush jobs, writes SHA-chained row"
```

---

## Task 3: Register worker in runner

**Files:**
- Modify: `backend/src/workers/runner.ts`

- [ ] **Step 1:** Add the registration

Replace the import block:
```typescript
import { tick as geocodeTick } from './geocodeWorker';
import { tick as auditFlushTick } from './auditFlushWorker';
import { baseLogger } from '../log';
```

Replace the `void loop` line at the bottom:
```typescript
void loop('geocode', geocodeTick);
void loop('audit_flush', auditFlushTick);
// Email worker registers in Slice 3.
```

- [ ] **Step 2:** Smoke-run

```bash
cd /Users/fegensprenelon/smith-net/backend
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' timeout 5 npx tsx src/workers/runner.ts 2>&1 | tail -10
```
Expected: log lines `worker_starting`. Exits clean (timeout terminates it). No tick errors.

- [ ] **Step 3:** Commit

```bash
git add backend/src/workers/runner.ts
git commit -m "feat(worker): register auditFlushTick in runner"
```

---

## Task 4: Rewrite `auditChain.test.ts`

**Files:**
- Modify: `backend/src/__tests__/auditChain.test.ts`

The Phase 2 test expected `log()` to return `AuditEntry` with `.checksum`. Under the new contract, the test must drive the worker directly and read from pg.

- [ ] **Step 1:** Add a drain helper

Replace the whole file contents:

```typescript
import crypto from 'crypto';
import { pg, isPgEnabled } from '../db';
import { auditLog, AuditAction } from '../auditLog';
import { tick as auditFlushTick } from '../workers/auditFlushWorker';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanAudit() {
  if (!isPgEnabled()) return;
  await pg!.query('TRUNCATE audit_entries RESTART IDENTITY');
  await pg!.query(`DELETE FROM background_jobs WHERE kind='audit_flush'`);
}

/**
 * Drive the worker tick repeatedly until no queued/running audit_flush rows remain.
 * Throws if it can't drain in `timeoutMs`.
 */
async function drainAuditFlush(timeoutMs = 5000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const r = await pg!.query<{ count: string }>(
      `SELECT COUNT(*)::text AS count FROM background_jobs
        WHERE kind='audit_flush' AND state IN ('queued','running')`
    );
    if (parseInt(r.rows[0].count, 10) === 0) return;
    const did = await auditFlushTick('test-worker');
    if (!did) await new Promise((res) => setTimeout(res, 25));
  }
  throw new Error('audit_flush did not drain within timeout');
}

interface PgAuditRow {
  audit_id: string;
  actor_id: string;
  target_id: string | null;
  action: string;
  metadata: Record<string, any>;
  prev_hash: string | null;
  hash: string;
}

function computeHashFromRow(prev: string | null, row: PgAuditRow, timestamp: number): string {
  // Mirror auditFlushWorker.computeHash.
  const body = JSON.stringify({
    id: row.audit_id,
    timestamp,
    action: row.action,
    actorId: row.actor_id,
    targetId: row.target_id ?? undefined,
    metadata: row.metadata,
  });
  return crypto.createHash('sha256').update((prev ?? '') + body).digest('hex');
}

describeDb('auditFlushWorker chain validation', () => {
  beforeEach(cleanAudit);
  afterAll(async () => { await pg?.end(); });

  it('10 sequential log() calls drain to a valid SHA chain', async () => {
    const enqueued: { auditId: string; timestamp: number }[] = [];
    for (let i = 0; i < 10; i++) {
      const before = Date.now();
      const r = await auditLog.log(AuditAction.USER_LOGIN, `actor-${i}`, { i });
      const after = Date.now();
      // We don't know the worker's eventual timestamp, but the payload carries
      // the timestamp the log() emitted — bounded to [before, after].
      enqueued.push({ auditId: r.auditId, timestamp: (before + after) / 2 });
    }

    await drainAuditFlush();

    const rows = await pg!.query<PgAuditRow & { id: number }>(
      `SELECT id, audit_id, actor_id, target_id, action, metadata, prev_hash, hash
         FROM audit_entries ORDER BY id ASC`
    );
    expect(rows.rowCount).toBe(10);

    // Verify chain integrity: each row's prev_hash matches the previous row's hash.
    for (let i = 0; i < rows.rows.length; i++) {
      const expectedPrev = i === 0 ? null : rows.rows[i - 1].hash;
      expect(rows.rows[i].prev_hash).toBe(expectedPrev);
    }

    // Spot-check IDs match what enqueue produced.
    for (let i = 0; i < rows.rows.length; i++) {
      expect(rows.rows[i].audit_id).toBe(enqueued[i].auditId);
    }
  });

  it('concurrent log() (5 in parallel) drain to a valid chain in some order', async () => {
    await Promise.all(
      [0, 1, 2, 3, 4].map((i) => auditLog.log(AuditAction.USER_LOGIN, `concurrent-${i}`, { i }))
    );
    await drainAuditFlush();
    const rows = await pg!.query<{ prev_hash: string | null; hash: string }>(
      `SELECT prev_hash, hash FROM audit_entries ORDER BY id ASC`
    );
    expect(rows.rowCount).toBe(5);
    // Advisory lock serializes writers — chain must be intact regardless of enqueue order.
    for (let i = 0; i < rows.rows.length; i++) {
      const expectedPrev = i === 0 ? null : rows.rows[i - 1].hash;
      expect(rows.rows[i].prev_hash).toBe(expectedPrev);
    }
  });

  it('flushNow does not throw', async () => {
    await auditLog.log(AuditAction.USER_LOGIN, 'a', { test: 'buffer' });
    await drainAuditFlush();
    expect(() => auditLog.flushNow()).not.toThrow();
  });
});
```

- [ ] **Step 2:** Run the rewritten test

```bash
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npx jest --testPathPattern=auditChain.test 2>&1 | tail -15
```
Expected: 3 passing tests.

- [ ] **Step 3:** Commit

```bash
git add backend/src/__tests__/auditChain.test.ts
git commit -m "test(audit): rewrite chain test against pg + drain helper"
```

---

## Task 5: Hermetic cleanup across other test suites

**Files:**
- Modify: any `beforeEach` / `beforeAll` in test suites that triggered `auditLog.log()` (via routes) and now leak `audit_flush` rows.

The leak is silent — tests still pass — but accumulating `audit_flush` rows in pg between runs is ugly and could mask future bugs. Add cleanup to the suites that already truncate other state.

- [ ] **Step 1:** Inventory the impacted suites

```bash
grep -rln "beforeEach\|beforeAll" backend/src/__tests__/*.test.ts | xargs grep -l "auditLog\|routes" 2>/dev/null | head -20
```

For each suite that already truncates pg state (e.g., `cleanShifts`, `cleanAudit`), add:
```sql
DELETE FROM background_jobs WHERE kind = 'audit_flush';
```

Candidates to update (based on the production grep):
- `shifts-routes.test.ts` — already has `cleanShifts`, add the DELETE
- `presence-location-routes.test.ts` — already has `cleanShifts`, add the DELETE
- Other route tests that fire audit logs and care about hermetic pg state

- [ ] **Step 2:** Run the full suite to ensure no regression

```bash
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npm test 2>&1 | grep -E "Tests:|Test Suites:" | tail -4
```
Expected: 23 suites, 173+ tests (added 1–2 new tests in Task 4), all green.

- [ ] **Step 3:** Commit

```bash
git add backend/src/__tests__/
git commit -m "test(audit): truncate background_jobs.audit_flush between suites for hermeticity"
```

---

## Task 6: Closeout

**Files:**
- Modify: `OPERATIONS.md` — annotate that `auditLog.log()` is now async-enqueue (visible lag); same stuck-row reset recipe applies to `kind='audit_flush'`.

- [ ] **Step 1:** Append note to OPERATIONS.md

```markdown
## Audit chain visibility lag (Phase 3 Slice 2)

`auditLog.log()` no longer writes synchronously. It enqueues a `kind='audit_flush'`
row; the worker drains under `pg_advisory_xact_lock(42)` and INSERTs into
`audit_entries`. Typical lag: <100ms. Implications:

- A request handler that emits an audit cannot read the resulting row in the
  same handler — query `background_jobs` first if it must.
- The stuck-row recipe at the top of this file applies to `audit_flush` jobs
  as well: if a worker dies mid-INSERT (after `claimNext` but before
  `complete`), the row sits in `state='running'`. The advisory lock is
  transaction-scoped so a crashed worker releases it automatically; the
  manual reset puts the row back to `queued` for the next worker.
```

- [ ] **Step 2:** Final test sweep

```bash
cd /Users/fegensprenelon/smith-net/backend
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npm test 2>&1 | tail -6
```
Expected: 23 suites green, 174+ tests green (2 new from Task 4).

- [ ] **Step 3:** Commit + tag

```bash
git add OPERATIONS.md
git commit -m "$(cat <<'EOF'
chore(phase-3): close slice 2 — audit-flush worker

Phase 3 Slice 2 — audit-flush worker — complete.

Moves the synchronous audit_entries write off the request path:
- auditLog.log() returns { auditId, queued: true } after enqueue
- auditFlushWorker drains under pg_advisory_xact_lock(42) and writes
  the SHA-chained row
- bufferFromWorker callback mirrors into in-memory cache + JSONL
  so query()/verifyIntegrity()/getStats() keep working

Production callers unchanged (all 17 already discarded the return value).
auditChain.test.ts rewritten around a drainAuditFlush() helper that drives
auditFlushTick until queued/running rows hit zero, then asserts against
pg's audit_entries directly.

Tested: 23 suites, 174+ tests, all green.

OPERATIONS.md annotated with visibility-lag note; stuck-row recipe
applies to audit_flush jobs as well.
EOF
)"
git tag -a phase-3-slice-2 -m "Phase 3 Slice 2 — audit-flush worker"
```

---

## Done criteria

- `auditLog.log()` returns `{ auditId, queued: true }` and never touches pg in non-dev paths
- `workers/auditFlushWorker.ts` exists and is registered in `runner.ts`
- `auditChain.test.ts` drains via `drainAuditFlush()` and verifies chain from `audit_entries`
- Backend test count goes from 173 → 174+ (~2 new tests), all green
- `phase-3-slice-2` tag exists

## Self-review checklist

- [ ] Every step shows the actual code or command, no placeholders
- [ ] Type names consistent: `auditId` (not `id` or `auditID`) across log(), payload, worker, tests
- [ ] No production caller of `auditLog.log()` reads `.checksum` / `.id` (verified by pre-task grep)
- [ ] The advisory lock semantic stays the same (key 42, transaction-scoped)
- [ ] Dev-mode no-pg path still returns the new shape and writes in-memory + JSONL
- [ ] OPERATIONS.md visibility-lag note added
