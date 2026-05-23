jest.mock('../telemetryService');

import { requireCap } from '../middleware/requireCap';
import { emitGateHit } from '../telemetryService';

const emitMock = emitGateHit as jest.MockedFunction<typeof emitGateHit>;

function mockRes() {
  const res: any = { statusCode: 0, body: undefined };
  res.status = (c: number) => { res.statusCode = c; return res; };
  res.json = (b: any) => { res.body = b; return res; };
  return res;
}

beforeEach(() => {
  emitMock.mockReset();
  emitMock.mockResolvedValue(undefined);
});

describe('requireCap telemetry emit', () => {
  it('emits gate_hit on the 403 (at cap)', async () => {
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u1', tier: 'open' } };
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count: async () => 1 })(req, res, next);
    expect(res.statusCode).toBe(403);
    expect(emitMock).toHaveBeenCalledTimes(1);
    expect(emitMock).toHaveBeenCalledWith('u1', 'gate_hit.active_job_cap', 'open', { limit: 1, current: 1 });
    expect(next).not.toHaveBeenCalled();
  });

  it('does not emit when under the cap', async () => {
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u1', tier: 'open' } };
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count: async () => 0 })(req, res, next);
    expect(emitMock).not.toHaveBeenCalled();
    expect(next).toHaveBeenCalledTimes(1);
  });

  it('does not emit for unlimited tiers', async () => {
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u2', tier: 'solo' } };
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count: async () => 99 })(req, res, next);
    expect(emitMock).not.toHaveBeenCalled();
    expect(next).toHaveBeenCalledTimes(1);
  });
});
