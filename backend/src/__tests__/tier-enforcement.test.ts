import { requireTier, requireEntitlement } from '../middleware/requireEntitlement';

function mockRes() {
  const res: any = { statusCode: 0, body: undefined };
  res.status = (c: number) => { res.statusCode = c; return res; };
  res.json = (b: any) => { res.body = b; return res; };
  return res;
}
function run(mw: any, user: any) {
  const req: any = { user };
  const res = mockRes();
  let nexted = false;
  mw(req, res, () => { nexted = true; });
  return { res, nexted };
}

describe('requireTier', () => {
  it('401 when no user', () => {
    const { res, nexted } = run(requireTier('solo', 'plan_compiler'), undefined);
    expect(res.statusCode).toBe(401);
    expect(nexted).toBe(false);
  });
  it('403 structured when below min', () => {
    const { res, nexted } = run(requireTier('solo', 'plan_compiler'), { tier: 'open' });
    expect(nexted).toBe(false);
    expect(res.statusCode).toBe(403);
    expect(res.body).toEqual({
      error: 'Tier gate: plan_compiler',
      code: 'tier_gate_exceeded',
      gate_id: 'plan_compiler',
      current_tier: 'open',
      details: { target_tier: 'solo' },
    });
  });
  it('next() at or above min', () => {
    expect(run(requireTier('solo', 'g'), { tier: 'solo' }).nexted).toBe(true);
    expect(run(requireTier('solo', 'g'), { tier: 'enterprise' }).nexted).toBe(true);
  });
  it('403 fail-closed on an unrecognized tier', () => {
    const { res, nexted } = run(requireTier('solo', 'plan_compiler'), { tier: 'bogus' } as any);
    expect(nexted).toBe(false);
    expect(res.statusCode).toBe(403);
  });
});

describe('requireEntitlement', () => {
  it('401 when no user', () => {
    expect(run(requireEntitlement('plan_compiler', 'g'), undefined).res.statusCode).toBe(401);
  });
  it('plan_compiler: open 403 (target solo), solo+ next', () => {
    const mw = requireEntitlement('plan_compiler', 'plan_compiler');
    const r = run(mw, { tier: 'open' });
    expect(r.res.statusCode).toBe(403);
    expect(r.res.body.details.target_tier).toBe('solo');
    expect(run(mw, { tier: 'solo' }).nexted).toBe(true);
    expect(run(mw, { tier: 'enterprise' }).nexted).toBe(true);
  });
  it('smithai_on_device: solo 403 (target advanced), advanced+ next', () => {
    const mw = requireEntitlement('smithai_on_device', 'ai_tab');
    const r = run(mw, { tier: 'solo' });
    expect(r.res.statusCode).toBe(403);
    expect(r.res.body.details.target_tier).toBe('advanced');
    expect(run(mw, { tier: 'advanced' }).nexted).toBe(true);
  });
  it('crew_multiuser: advanced 403 (target enterprise), enterprise next', () => {
    const mw = requireEntitlement('crew_multiuser', 'crew_invite');
    const r = run(mw, { tier: 'advanced' });
    expect(r.res.statusCode).toBe(403);
    expect(r.res.body.details.target_tier).toBe('enterprise');
    expect(run(mw, { tier: 'enterprise' }).nexted).toBe(true);
  });
});
