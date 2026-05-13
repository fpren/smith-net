// backend/src/__tests__/geocoder.test.ts
import { geocodeLocation, __resetGeocoderState } from '../geocoder';

describe('geocodeLocation', () => {
  beforeEach(() => {
    __resetGeocoderState();
    (global as any).fetch = jest.fn();
  });
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('returns lat/lng on 200 with a result', async () => {
    (global as any).fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [{ lat: '40.748817', lon: '-73.985428' }],
    });
    const result = await geocodeLocation('Empire State Building, NYC');
    expect(result).toEqual({ lat: 40.748817, lng: -73.985428 });
    const call = (global.fetch as jest.Mock).mock.calls[0];
    expect(call[0]).toContain('https://nominatim.openstreetmap.org/search');
    expect(call[0]).toContain(encodeURIComponent('Empire State Building, NYC'));
    expect(call[1].headers['User-Agent']).toMatch(/SmithNet/);
  });

  it('returns null when 200 with empty array', async () => {
    (global as any).fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [],
    });
    const result = await geocodeLocation('asdfasdfasdfasdf');
    expect(result).toBeNull();
  });

  it('returns null on 429 rate-limit', async () => {
    (global as any).fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 429,
      json: async () => ({}),
    });
    const result = await geocodeLocation('x');
    expect(result).toBeNull();
  });

  it('returns null on network error', async () => {
    (global as any).fetch = jest.fn().mockRejectedValue(new Error('network down'));
    const result = await geocodeLocation('x');
    expect(result).toBeNull();
  });

  it('respects the 1 req/sec rate limit between calls', async () => {
    (global as any).fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [{ lat: '1', lon: '2' }],
    });

    const t0 = Date.now();
    await geocodeLocation('a');
    await geocodeLocation('b');
    const elapsed = Date.now() - t0;
    // Two calls back-to-back must be >= ~1.1s apart.
    expect(elapsed).toBeGreaterThanOrEqual(1000);
  }, 5000);
});
