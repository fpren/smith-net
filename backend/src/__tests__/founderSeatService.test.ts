import { FOUNDER_POOLS, FOUNDER_BONUS_IDS, HOLD_MINUTES } from '../founderSeatService';
import { ReserveFounderSeatBody } from '../schemas/founderSeats';

describe('FOUNDER_POOLS', () => {
  it('is 1000 / 100 / 10 per pool', () => {
    expect(FOUNDER_POOLS).toEqual({
      solo_founder_pricing_lock: 1000,
      advanced_lifetime_template_library: 100,
      enterprise_founder_annual_pricing: 10,
    });
  });
});

describe('FOUNDER_BONUS_IDS / HOLD_MINUTES', () => {
  it('has 3 bonus ids and a 10-minute hold', () => {
    expect(FOUNDER_BONUS_IDS).toHaveLength(3);
    expect(HOLD_MINUTES).toBe(10);
  });
});

describe('ReserveFounderSeatBody', () => {
  it('accepts each known bonus id', () => {
    for (const id of FOUNDER_BONUS_IDS) {
      expect(ReserveFounderSeatBody.safeParse({ bonusId: id }).success).toBe(true);
    }
  });
  it('rejects unknown bonus id, missing, and unknown keys', () => {
    expect(ReserveFounderSeatBody.safeParse({ bonusId: 'nope' }).success).toBe(false);
    expect(ReserveFounderSeatBody.safeParse({}).success).toBe(false);
    expect(ReserveFounderSeatBody.safeParse({ bonusId: 'solo_founder_pricing_lock', foo: 1 }).success).toBe(false);
  });
});
