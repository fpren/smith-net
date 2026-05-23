import { requireCap, lowestUnlimitedTierFor } from '../middleware/requireCap';
import { CAP_LIMITS_BY_TIER } from '../entitlements';

function mockRes() {
  const res: any = { statusCode: 0, body: undefined };
  res.status = (c: number) => { res.statusCode = c; return res; };
  res.json = (b: any) => { res.body = b; return res; };
  return res;
}

describe('requireCap', () => {
  it('calls next() when under the cap (open, active_jobs=1, current 0)', async () => {
    const count = jest.fn().mockResolvedValue(0);
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u1', tier: 'open' } };
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count })(req, res, next);
    expect(count).toHaveBeenCalledWith('u1');
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith();
    expect(res.statusCode).toBe(0);
  });

  it('refuses with the numeric 403 at the active_jobs cap', async () => {
    const count = jest.fn().mockResolvedValue(1);
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u1', tier: 'open' } };
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count })(req, res, next);
    expect(res.statusCode).toBe(403);
    expect(res.body).toEqual({
      error: 'Tier cap reached: active_job_cap',
      code: 'tier_gate_exceeded',
      gate_id: 'active_job_cap',
      current_tier: 'open',
      limit: 1,
      current: 1,
      details: { target_tier: 'solo' },
    });
    expect(next).not.toHaveBeenCalled();
  });

  it('refuses with the numeric 403 at the pdf_sends cap', async () => {
    const count = jest.fn().mockResolvedValue(5);
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u9', tier: 'open' } };
    await requireCap({ capKey: 'pdf_sends_per_month', gateId: 'pdf_send_cap', count })(req, res, next);
    expect(res.statusCode).toBe(403);
    expect(res.body).toEqual({
      error: 'Tier cap reached: pdf_send_cap',
      code: 'tier_gate_exceeded',
      gate_id: 'pdf_send_cap',
      current_tier: 'open',
      limit: 5,
      current: 5,
      details: { target_tier: 'solo' },
    });
    expect(next).not.toHaveBeenCalled();
  });

  it('short-circuits unlimited tiers without invoking the counter', async () => {
    const count = jest.fn();
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u2', tier: 'solo' } };
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count })(req, res, next);
    expect(count).not.toHaveBeenCalled();
    expect(next).toHaveBeenCalledTimes(1);
    expect(res.statusCode).toBe(0);
  });

  it('returns 401 when req.user is missing', async () => {
    const count = jest.fn();
    const next = jest.fn();
    const res = mockRes();
    const req: any = {};
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count })(req, res, next);
    expect(res.statusCode).toBe(401);
    expect(res.body).toEqual({ error: 'Authentication required' });
    expect(count).not.toHaveBeenCalled();
    expect(next).not.toHaveBeenCalled();
  });

  it('fails closed: counter error goes to next(err), not an allow', async () => {
    const boom = new Error('db down');
    const count = jest.fn().mockRejectedValue(boom);
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u1', tier: 'open' } };
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count })(req, res, next);
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith(boom);
    expect(res.statusCode).toBe(0);
  });
});

describe('lowestUnlimitedTierFor', () => {
  it('returns solo for both count caps', () => {
    expect(lowestUnlimitedTierFor('active_jobs')).toBe('solo');
    expect(lowestUnlimitedTierFor('pdf_sends_per_month')).toBe('solo');
  });
});

describe('CAP_LIMITS_BY_TIER', () => {
  it('matches the tier-gating cap matrix', () => {
    expect(CAP_LIMITS_BY_TIER.open).toEqual({ active_jobs: 1, pdf_sends_per_month: 5 });
    expect(CAP_LIMITS_BY_TIER.solo).toEqual({ active_jobs: null, pdf_sends_per_month: null });
    expect(CAP_LIMITS_BY_TIER.advanced).toEqual({ active_jobs: null, pdf_sends_per_month: null });
    expect(CAP_LIMITS_BY_TIER.enterprise).toEqual({ active_jobs: null, pdf_sends_per_month: null });
  });
});
