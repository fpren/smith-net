import {
  ALLOWED_EVENTS, isAllowedEvent, hasPiiKey, hashUserId, emitGateHit,
} from '../telemetryService';
import { sha256HexGated } from '../sha256Gate';
import { GateHitBody } from '../schemas/telemetry';

describe('hashUserId', () => {
  it('is the sha256 hex of the id, 64 lowercase hex chars', () => {
    const h = hashUserId('u1');
    expect(h).toBe(sha256HexGated(Buffer.from('u1', 'utf8')));
    expect(h).toMatch(/^[0-9a-f]{64}$/);
  });
  it('is deterministic, differs per id, and is never the raw id', () => {
    expect(hashUserId('u1')).toBe(hashUserId('u1'));
    expect(hashUserId('u1')).not.toBe(hashUserId('u2'));
    expect(hashUserId('u1')).not.toBe('u1');
  });
});

describe('ALLOWED_EVENTS / isAllowedEvent', () => {
  it('has the 15 F5.2 events', () => {
    expect(ALLOWED_EVENTS).toHaveLength(15);
  });
  it('accepts known events and rejects unknown', () => {
    expect(isAllowedEvent('gate_hit.active_job_cap')).toBe(true);
    expect(isAllowedEvent('gate_hit.pdf_send_cap')).toBe(true);
    expect(isAllowedEvent('tier_upgrade.cta_clicked')).toBe(true);
    expect(isAllowedEvent('gate_hit.bogus')).toBe(false);
  });
});

describe('hasPiiKey', () => {
  it('flags PII keys', () => {
    expect(hasPiiKey({ email: 'x' })).toBe(true);
    expect(hasPiiKey({ profileId: 'x' })).toBe(true);
  });
  it('passes non-PII metadata', () => {
    expect(hasPiiKey({ limit: 1, current: 1 })).toBe(false);
  });
});

describe('GateHitBody', () => {
  it('accepts a known event with scalar metadata', () => {
    expect(GateHitBody.safeParse({ event: 'gate_hit.ai_tab', metadata: { x: 1 } }).success).toBe(true);
  });
  it('accepts a known event with no metadata', () => {
    expect(GateHitBody.safeParse({ event: 'gate_hit.crew_invite' }).success).toBe(true);
  });
  it('rejects an unknown event', () => {
    expect(GateHitBody.safeParse({ event: 'nope' }).success).toBe(false);
  });
  it('rejects PII metadata', () => {
    expect(GateHitBody.safeParse({ event: 'gate_hit.ai_tab', metadata: { email: 'a@b.c' } }).success).toBe(false);
  });
  it('rejects unknown top-level keys', () => {
    expect(GateHitBody.safeParse({ event: 'gate_hit.ai_tab', foo: 1 }).success).toBe(false);
  });
});

describe('emitGateHit', () => {
  it('resolves to a no-op without a DB and never throws', async () => {
    await expect(
      emitGateHit('u1', 'gate_hit.active_job_cap', 'open', { limit: 1, current: 1 }),
    ).resolves.toBeUndefined();
  });
});
