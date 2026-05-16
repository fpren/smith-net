# Phase 3 Slice 3 — email worker + Phase 3 closeout

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Move the SMTP send off the request path. Register + resend-verification handlers enqueue a `kind='email'` job; the worker builds the body and calls `emailService.sendEmail`. Closes audit weak point #2 and tags `phase-3`.

**Architecture:** Worker `emailWorker.ts` handles a `verification` subkind today (future subkinds: password reset, invoice, notification). Payload carries the canonical inputs (recipient, display name, token, kind). Worker imports `emailService.sendEmail`, builds body via helpers that move from `authRoutes.ts` into the worker, calls send. Routes drop their `sendEmail` import — only `isEmailLive()` (a non-IO predicate) stays.

**Tech Stack:** TypeScript, Express, pg, nodemailer (existing), jest with real Postgres.

**Scope guardrails:**
- Two production callers: `register` (fire-and-forget today, becomes await enqueue) and `resend-verification` (await today, becomes await enqueue). No new endpoints.
- The current `register` does `sendVerificationEmail(...).catch(...)` without await. Under the new contract the route awaits the enqueue, which is ~1ms. Tests that assert on register response timing are unaffected.
- `resend-verification` previously returned `{ ok, dryRun: !isEmailLive() }` based on whether SMTP was configured. Same response shape; `isEmailLive()` reading is unchanged (same env at the route, since the worker runs in the same process tree).
- Dedupe key: `email:verify:<userId>:<token>` — same userId+token within a 24h TTL hits dedupe (good — prevents accidental double-send from register-then-resend race).
- The audit weak point annotation in `docs/smith-net-architecture-audit.md` happens in the closeout commit.

---

## File Structure

**Create:**
- `backend/src/workers/emailWorker.ts` — `tick(workerId)` handles `kind='email'`
- `backend/src/__tests__/email-worker.test.ts` — tick happy path, retry on simulated send failure, dedupe

**Modify:**
- `backend/src/authRoutes.ts` — replace `sendEmail` import; remove local `sendVerificationEmail` helper; routes call `enqueue({kind:'email', ...})`
- `backend/src/workers/runner.ts` — register `emailTick`
- `OPERATIONS.md` — note email worker behavior + dryRun semantics
- `docs/smith-net-architecture-audit.md` — annotate weak point #2 as `[closed in phase-3, commit <SHA>]`

---

## Task 0: Baseline

- [ ] Run backend tests:

```bash
cd /Users/fegensprenelon/smith-net/backend
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npm test 2>&1 | grep -E "Tests:|Test Suites:" | tail -4
```
Expected: 23 suites, 175 tests, all green.

---

## Task 1: emailWorker.tick

**Files:**
- Create: `backend/src/workers/emailWorker.ts`

The body-builder helpers (`buildVerificationLink`, `buildVerificationEmail`) currently live in `authRoutes.ts` — copy them into the worker so the route doesn't need to retain them after Task 2.

- [ ] **Step 1:** Read the current helpers from `backend/src/authRoutes.ts` (around lines 50–80) — `buildVerificationLink`, `buildVerificationEmail`, `sendVerificationEmail`. The first two are pure; the third is what we're replacing.

- [ ] **Step 2:** Write `backend/src/workers/emailWorker.ts`:

```typescript
// backend/src/workers/emailWorker.ts
//
// Phase 3 Slice 3: email worker.
//
// Drains kind='email' jobs and dispatches by subkind. Today only
// 'verification' is supported; future subkinds: password reset, invoice,
// notification.
//
// Why a worker: SMTP send can take seconds-to-minutes on transient errors,
// and the Phase 3 rule says no inline fire-and-forget. The register and
// resend-verification routes enqueue and return immediately.

import { pg, isPgEnabled } from '../db';
import { claimNext, complete, fail } from '../queue/queue';
import { sendEmail } from '../emailService';
import { requestLogger } from '../log';

const KIND = 'email';

type EmailSubkind = 'verification';

interface VerificationPayload {
  subkind: 'verification';
  to: string;
  displayName: string;
  token: string;
  // The route knows the public base URL; pass it in so the worker doesn't
  // duplicate env-lookup logic.
  baseUrl: string;
}

type EmailPayload = VerificationPayload; // Union when more subkinds land.

function buildVerificationEmail(displayName: string, link: string) {
  const subject = 'Verify your Smith Net account';
  const text = [
    `Hi ${displayName},`,
    '',
    'Tap to verify your email and finish setting up Smith Net:',
    link,
    '',
    'This link expires in 24 hours. If you did not create a Smith Net account, ignore this email.',
    '',
    '— Smith Net',
  ].join('\n');
  const html = `<p>Hi ${displayName},</p>
<p>Tap to verify your email and finish setting up Smith Net:</p>
<p><a href="${link}">${link}</a></p>
<p>This link expires in 24 hours. If you did not create a Smith Net account, ignore this email.</p>
<p>— Smith Net</p>`;
  return { subject, text, html };
}

export async function tick(workerId: string): Promise<boolean> {
  if (!isPgEnabled() || !pg) return false;
  const job = await claimNext(KIND, workerId);
  if (!job) return false;

  const p = job.payload as unknown as EmailPayload;
  try {
    switch (p.subkind) {
      case 'verification': {
        const link = `${p.baseUrl.replace(/\/+$/, '')}/api/auth/verify-email?token=${encodeURIComponent(p.token)}`;
        const { subject, text, html } = buildVerificationEmail(p.displayName, link);
        const r = await sendEmail({ to: p.to, subject, text, html });
        if (!r.ok && !r.dryRun) {
          throw new Error(r.error ?? 'sendEmail returned ok=false');
        }
        break;
      }
      default: {
        // Unknown subkind — fail terminally rather than poison the queue.
        throw new Error(`unknown email subkind: ${(p as { subkind?: string }).subkind ?? 'undefined'}`);
      }
    }

    await complete(job.id);
    requestLogger().info(
      { event: 'email_sent', jobId: job.id, subkind: p.subkind, to: p.to },
      'email sent'
    );
    return true;
  } catch (err) {
    await fail(job.id, err as Error, { attempts: job.attempts, maxAttempts: job.max_attempts });
    requestLogger().warn(
      { event: 'email_send_failed', jobId: job.id, attempts: job.attempts, err: (err as Error).message },
      'email send failed'
    );
    return true;
  }
}
```

- [ ] **Step 3:** Type-check

```bash
cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit 2>&1 | head -10
```
Expected: zero errors related to this file.

- [ ] **Step 4:** Commit

```bash
cd /Users/fegensprenelon/smith-net
git add backend/src/workers/emailWorker.ts
git commit -m "feat(worker): emailWorker — drains kind='email' jobs, dispatches by subkind"
```

---

## Task 2: Route handlers enqueue instead of sending

**Files:**
- Modify: `backend/src/authRoutes.ts`

- [ ] **Step 1:** Read `authRoutes.ts` to find:
  - Import of `sendEmail` and `isEmailLive` at top
  - `buildVerificationLink` and `buildVerificationEmail` helpers
  - `sendVerificationEmail` local helper
  - Register flow that calls it (around line 122)
  - Resend-verification flow that calls it (around line 276)

- [ ] **Step 2:** Update the import line. Drop `sendEmail`; keep `isEmailLive` (the response includes a `dryRun` flag based on it):

```typescript
import { isEmailLive } from './emailService';
```

Add the queue import:
```typescript
import { enqueue } from './queue/queue';
```

- [ ] **Step 3:** Delete the local `sendVerificationEmail` helper. The `buildVerificationLink` helper can stay (it's used to render the link in `verify-failed` template, or wherever it's referenced — check) OR be removed if unused. The `buildVerificationEmail` helper is now duplicated in the worker; delete it from `authRoutes.ts`.

After deletions, run a grep to confirm no orphans:
```bash
grep -n "sendVerificationEmail\|buildVerificationEmail\|sendEmail" backend/src/authRoutes.ts
```
Expected: empty.

- [ ] **Step 4:** Replace the register-flow send (around line 122) with an enqueue:

```typescript
// F1.4: Enqueue the verification email send. The route returns immediately;
// emailWorker drains kind='email' jobs and calls SMTP. dedupeKey survives
// the (userId, token) tuple — if register races a resend, only one send
// reaches SMTP.
if (user.emailVerificationToken) {
  await userStore.recordVerificationSendAttempt(user.id);
  await enqueue({
    kind: 'email',
    dedupeKey: `email:verify:${user.id}:${user.emailVerificationToken}`,
    payload: {
      subkind: 'verification',
      to: user.email,
      displayName: user.displayName,
      token: user.emailVerificationToken,
      baseUrl: process.env.PUBLIC_BASE_URL ?? process.env.APP_BASE_URL ?? 'http://localhost:3030',
    },
  });
}
```

- [ ] **Step 5:** Replace the resend-verification send (around line 276) with the same enqueue shape:

```typescript
await enqueue({
  kind: 'email',
  dedupeKey: `email:verify:${userId}:${newToken}`,
  payload: {
    subkind: 'verification',
    to: stored.email,
    displayName: stored.displayName,
    token: newToken,
    baseUrl: process.env.PUBLIC_BASE_URL ?? process.env.APP_BASE_URL ?? 'http://localhost:3030',
  },
});
await auditLog.log(AuditAction.USER_PROFILE_UPDATE, userId, { event: 'verification_resent' });

res.json({ ok: true, dryRun: !isEmailLive() });
```

- [ ] **Step 6:** Type-check

```bash
cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit 2>&1 | head -10
```
Expected: zero errors.

- [ ] **Step 7:** Run the existing auth test suites — they must still pass since route response shape is unchanged:

```bash
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npm test -- --testPathPattern='(auth|email-verification|auth-cookie)' 2>&1 | grep -E "Tests:|Test Suites:" | tail -4
```
Expected: same counts as baseline. If anything fails, fix in this task before moving on.

- [ ] **Step 8:** Commit

```bash
cd /Users/fegensprenelon/smith-net
git add backend/src/authRoutes.ts
git commit -m "refactor(auth): register + resend-verification enqueue email job instead of inline SMTP"
```

---

## Task 3: Register emailTick in runner

**Files:**
- Modify: `backend/src/workers/runner.ts`

- [ ] **Step 1:** Add the import:

```typescript
import { tick as emailTick } from './emailWorker';
```

Add the loop after `audit_flush`:
```typescript
void loop('email', emailTick);
// All Phase 3 workers registered. Phase 4 adds daemons inside this runner.
```

(Remove the trailing `// Email worker registers in Slice 3.` comment.)

- [ ] **Step 2:** Smoke-run

```bash
cd /Users/fegensprenelon/smith-net/backend
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npx tsx -e "import('./src/workers/runner.ts').then(() => setTimeout(() => process.exit(0), 1500))" 2>&1 | tail -5
```
Expected: `worker_starting` log line, exits clean, no errors.

- [ ] **Step 3:** Commit

```bash
cd /Users/fegensprenelon/smith-net
git add backend/src/workers/runner.ts
git commit -m "feat(worker): register emailTick in runner"
```

---

## Task 4: Tests for emailWorker

**Files:**
- Create: `backend/src/__tests__/email-worker.test.ts`

Mock the SMTP transport (or `sendEmail`) so tests don't need real SMTP. Three cases:
1. Happy path: enqueue → tick → claimed + completed; sendEmail called once with correct body.
2. Retry: sendEmail throws → row state='failed' with backoff; eventually succeeds on a clean retry.
3. Dedupe: two enqueues with same `dedupeKey` yield one row, second returns `created: false`.

- [ ] **Step 1:** Write the test file:

```typescript
import { pg, isPgEnabled } from '../db';
import { enqueue } from '../queue/queue';
import { tick as emailTick } from '../workers/emailWorker';
import * as emailService from '../emailService';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanQueue() {
  if (!isPgEnabled()) return;
  await pg!.query(`DELETE FROM background_jobs WHERE kind='email'`);
}

function makePayload(token: string, userId = 'u-1', email = 'user@example.com') {
  return {
    subkind: 'verification',
    to: email,
    displayName: 'Tester',
    token,
    baseUrl: 'https://api.smithnet.test',
  } as const;
}

describeDb('emailWorker', () => {
  let sendSpy: jest.SpyInstance;
  beforeEach(async () => {
    await cleanQueue();
    sendSpy = jest.spyOn(emailService, 'sendEmail');
  });
  afterEach(() => { sendSpy.mockRestore(); });
  afterAll(async () => { await pg?.end(); });

  it('happy path: enqueue + tick succeeds and calls sendEmail once with the verification link', async () => {
    sendSpy.mockResolvedValue({ ok: true, dryRun: false });
    const token = 'happy-token-1';
    const enq = await enqueue({
      kind: 'email',
      dedupeKey: `email:verify:u-1:${token}`,
      payload: makePayload(token),
    });
    expect(enq.created).toBe(true);

    const did = await emailTick('test-worker');
    expect(did).toBe(true);
    expect(sendSpy).toHaveBeenCalledTimes(1);
    const call = sendSpy.mock.calls[0][0];
    expect(call.to).toBe('user@example.com');
    expect(call.subject).toMatch(/verify/i);
    expect(call.text).toContain(`https://api.smithnet.test/api/auth/verify-email?token=${token}`);

    const row = await pg!.query(
      `SELECT state, finished_at FROM background_jobs WHERE id=$1`,
      [enq.id]
    );
    expect(row.rows[0].state).toBe('succeeded');
    expect(row.rows[0].finished_at).not.toBeNull();
  });

  it('retry path: sendEmail throws, row state=failed, scheduled_at pushed out for backoff', async () => {
    sendSpy.mockRejectedValueOnce(new Error('SMTP transient'));
    const token = 'retry-token-1';
    const enq = await enqueue({
      kind: 'email',
      dedupeKey: `email:verify:u-2:${token}`,
      payload: makePayload(token, 'u-2', 'retry@example.com'),
    });

    const did = await emailTick('test-worker');
    expect(did).toBe(true);

    const row = await pg!.query<{ state: string; attempts: number; last_error: string; scheduled_at: Date }>(
      `SELECT state, attempts, last_error, scheduled_at FROM background_jobs WHERE id=$1`,
      [enq.id]
    );
    expect(row.rows[0].state).toBe('failed');
    expect(row.rows[0].attempts).toBe(1);
    expect(row.rows[0].last_error).toMatch(/SMTP transient/);
    expect(row.rows[0].scheduled_at.getTime()).toBeGreaterThan(Date.now());

    // Force scheduled_at back to now so the next claim picks it up, then succeed.
    sendSpy.mockResolvedValueOnce({ ok: true, dryRun: false });
    await pg!.query(`UPDATE background_jobs SET scheduled_at=NOW(), state='queued' WHERE id=$1`, [enq.id]);
    const did2 = await emailTick('test-worker');
    expect(did2).toBe(true);
    const row2 = await pg!.query(`SELECT state FROM background_jobs WHERE id=$1`, [enq.id]);
    expect(row2.rows[0].state).toBe('succeeded');
  });

  it('dedupe: two enqueues with the same dedupeKey yield one row', async () => {
    const token = 'dedupe-token-1';
    const a = await enqueue({
      kind: 'email',
      dedupeKey: `email:verify:u-3:${token}`,
      payload: makePayload(token, 'u-3'),
    });
    const b = await enqueue({
      kind: 'email',
      dedupeKey: `email:verify:u-3:${token}`,
      payload: makePayload(token, 'u-3'),
    });
    expect(a.created).toBe(true);
    expect(b.created).toBe(false);
    expect(b.id).toBe(-1);

    const count = await pg!.query<{ c: string }>(
      `SELECT COUNT(*)::text AS c FROM background_jobs WHERE kind='email' AND dedupe_key=$1`,
      [`email:verify:u-3:${token}`]
    );
    expect(parseInt(count.rows[0].c, 10)).toBe(1);
  });
});
```

- [ ] **Step 2:** Run the new test

```bash
cd /Users/fegensprenelon/smith-net/backend
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npx jest --testPathPattern=email-worker.test 2>&1 | tail -20
```
Expected: 3 tests pass.

- [ ] **Step 3:** Run the full suite

```bash
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npm test 2>&1 | grep -E "Tests:|Test Suites:" | tail -4
```
Expected: 24 suites (was 23 + new), 178 tests (was 175 + 3 new), all green.

- [ ] **Step 4:** Commit

```bash
cd /Users/fegensprenelon/smith-net
git add backend/src/__tests__/email-worker.test.ts
git commit -m "test(email): emailWorker happy path + retry + dedupe (3 tests)"
```

---

## Task 5: Closeout — audit annotation, OPERATIONS.md, tags

**Files:**
- Modify: `OPERATIONS.md` — append email-worker section
- Modify: `docs/smith-net-architecture-audit.md` — annotate weak point #2 if the doc exists

- [ ] **Step 1:** Append to `OPERATIONS.md`:

```markdown
## Email worker (Phase 3 Slice 3)

`authRoutes.ts` no longer calls SMTP directly. Register and
resend-verification enqueue a `kind='email'` job with subkind
`verification`; the emailWorker dispatches and calls `emailService.sendEmail`.

Dedupe key: `email:verify:<userId>:<token>`. If register races a resend
the second enqueue returns `created: false` and only one send reaches SMTP.

If SMTP env (`SMTP_USER` + `SMTP_APP_PASSWORD`) is unset, `sendEmail`
runs in dry-run mode and logs the body to the worker's stdout — useful
for grabbing the verification link in dev. The route's response still
includes `dryRun: !isEmailLive()` so the client knows whether real mail
was attempted.

Retry: a `sendEmail` failure marks the row `state='failed'` with
exponential backoff (`60 * 3^attempts` seconds, capped at 6h). After
`max_attempts=5`, the row goes to `state='dead'` and stays there for
operator review. Same stuck-row recipe applies.
```

- [ ] **Step 2:** Annotate the audit doc IF it exists

```bash
ls docs/smith-net-architecture-audit.md 2>/dev/null
```
If present, grep for "weak point #2" or "Weak point #2" and append `[closed in phase-3, commit <fill-in-after-commit>]`. Use a placeholder for now; resolve the SHA in the final commit message.

If the audit doc does NOT exist, skip this step and surface in the report — the spec mentions it but it's a nice-to-have.

- [ ] **Step 3:** Final test sweep

```bash
cd /Users/fegensprenelon/smith-net/backend
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npm test 2>&1 | grep -E "Tests:|Test Suites:" | tail -4
```
Expected: 24 suites, 178 tests, all green.

- [ ] **Step 4:** Closeout commit + tags

```bash
cd /Users/fegensprenelon/smith-net
git add OPERATIONS.md docs/smith-net-architecture-audit.md 2>/dev/null
git commit -m "$(cat <<'EOF'
chore(phase-3): close slice 3 + Phase 3 — email worker

Phase 3 — Queues + Workers — complete.

Slice 3 ships:
- workers/emailWorker.ts dispatches kind='email' jobs by subkind
  (verification today; password reset / invoice / notification later)
- authRoutes.ts register + resend-verification handlers enqueue
  instead of calling SMTP inline; the body-builder helpers move into
  the worker
- runner.ts registers emailTick alongside geocode + audit_flush
- 3 new tests (happy, retry-with-backoff, dedupe)
- OPERATIONS.md annotated; audit doc weak point #2 marked closed

Phase 3 closes audit weak point #2 (no background-job system).
Three workers running:
- geocode (Slice 1)
- audit_flush (Slice 2)
- email (Slice 3)

Production deploy uses two processes: npm run dev + npm run worker.
EOF
)"
git tag -a phase-3-slice-3 -m "Phase 3 Slice 3 — email worker"
git tag -a phase-3 -m "Phase 3 — Queues + Workers (geocode, audit_flush, email)"
```

---

## Done criteria

- `emailWorker.tick` exists and handles `subkind='verification'`
- `authRoutes.ts` no longer imports `sendEmail` from `emailService` (grep verified)
- `runner.ts` registers `email` loop
- Backend tests up from 175 → 178+ (3 new), all green
- `OPERATIONS.md` has the email-worker section
- `phase-3-slice-3` and `phase-3` tags exist

## Self-review checklist

- [ ] Worker file paths absolute, imports complete
- [ ] Both route call sites updated (register + resend-verification)
- [ ] Worker payload shape matches what tests + routes produce (especially `baseUrl`)
- [ ] Dedupe key format identical at enqueue sites and in tests
- [ ] No emoji anywhere
