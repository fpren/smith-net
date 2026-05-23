import { utcMonthStart } from '../invoiceSendsService';

describe('utcMonthStart', () => {
  it('returns the first instant of the month in UTC', () => {
    expect(utcMonthStart(new Date('2026-05-23T18:45:30.123Z')).toISOString())
      .toBe('2026-05-01T00:00:00.000Z');
  });

  it('is stable at month end and month start (UTC)', () => {
    expect(utcMonthStart(new Date('2026-01-31T23:59:59.999Z')).toISOString())
      .toBe('2026-01-01T00:00:00.000Z');
    expect(utcMonthStart(new Date('2026-12-01T00:00:00.000Z')).toISOString())
      .toBe('2026-12-01T00:00:00.000Z');
  });

  it('uses UTC fields near the UTC-midnight boundary', () => {
    expect(utcMonthStart(new Date('2026-03-01T00:30:00.000Z')).toISOString())
      .toBe('2026-03-01T00:00:00.000Z');
  });
});
