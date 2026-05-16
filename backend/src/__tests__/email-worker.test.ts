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
    expect(call.text).toContain(`https://api.smithnet.test/api/auth/verify?token=${token}`);

    const row = await pg!.query<{ state: string; finished_at: Date | null }>(
      `SELECT state, finished_at FROM background_jobs WHERE id=$1`,
      [enq.id]
    );
    expect(row.rows[0].state).toBe('succeeded');
    expect(row.rows[0].finished_at).not.toBeNull();
  });

  it('retry path: sendEmail throws, row state=failed with backoff, then succeeds on next claim', async () => {
    sendSpy.mockRejectedValueOnce(new Error('SMTP transient'));
    const token = 'retry-token-1';
    const enq = await enqueue({
      kind: 'email',
      dedupeKey: `email:verify:u-2:${token}`,
      payload: makePayload(token, 'u-2', 'retry@example.com'),
    });

    const did = await emailTick('test-worker');
    expect(did).toBe(true);

    const row = await pg!.query<{
      state: string; attempts: number; last_error: string; scheduled_at: Date;
    }>(
      `SELECT state, attempts, last_error, scheduled_at FROM background_jobs WHERE id=$1`,
      [enq.id]
    );
    expect(row.rows[0].state).toBe('failed');
    expect(row.rows[0].attempts).toBe(1);
    expect(row.rows[0].last_error).toMatch(/SMTP transient/);
    expect(row.rows[0].scheduled_at.getTime()).toBeGreaterThan(Date.now());

    // Force scheduled_at back to NOW so the next claim picks it up, then succeed.
    sendSpy.mockResolvedValueOnce({ ok: true, dryRun: false });
    await pg!.query(
      `UPDATE background_jobs SET scheduled_at=NOW(), state='queued' WHERE id=$1`,
      [enq.id]
    );
    const did2 = await emailTick('test-worker');
    expect(did2).toBe(true);
    const row2 = await pg!.query<{ state: string }>(
      `SELECT state FROM background_jobs WHERE id=$1`,
      [enq.id]
    );
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
