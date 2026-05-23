import { TRIAL_DAYS, trialExpiry, isTrialUpgrade } from '../trialService';
import { StartTrialBody } from '../schemas/me';

describe('TRIAL_DAYS', () => {
  it('is 14 for solo, 30 for advanced', () => {
    expect(TRIAL_DAYS).toEqual({ solo: 14, advanced: 30 });
  });
});

describe('trialExpiry', () => {
  it('adds the tier duration in days', () => {
    const now = new Date('2026-05-01T00:00:00.000Z');
    expect(trialExpiry('solo', now).toISOString()).toBe('2026-05-15T00:00:00.000Z');
    expect(trialExpiry('advanced', now).toISOString()).toBe('2026-05-31T00:00:00.000Z');
  });
});

describe('isTrialUpgrade', () => {
  it('allows only a tier strictly above the current tier', () => {
    expect(isTrialUpgrade('open', 'solo')).toBe(true);
    expect(isTrialUpgrade('open', 'advanced')).toBe(true);
    expect(isTrialUpgrade('solo', 'advanced')).toBe(true);
    expect(isTrialUpgrade('solo', 'solo')).toBe(false);
    expect(isTrialUpgrade('advanced', 'solo')).toBe(false);
    expect(isTrialUpgrade('enterprise', 'advanced')).toBe(false);
  });
});

describe('StartTrialBody', () => {
  it('accepts solo and advanced', () => {
    expect(StartTrialBody.safeParse({ tier: 'solo' }).success).toBe(true);
    expect(StartTrialBody.safeParse({ tier: 'advanced' }).success).toBe(true);
  });
  it('rejects enterprise, open, missing, and unknown keys', () => {
    expect(StartTrialBody.safeParse({ tier: 'enterprise' }).success).toBe(false);
    expect(StartTrialBody.safeParse({ tier: 'open' }).success).toBe(false);
    expect(StartTrialBody.safeParse({}).success).toBe(false);
    expect(StartTrialBody.safeParse({ tier: 'solo', foo: 1 }).success).toBe(false);
  });
});
