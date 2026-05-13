// backend/src/geocoder.ts
//
// Thin Nominatim client. Best-effort async geocoding called from jobsService.
// Soft-fails on rate-limit, network error, or empty results — the caller proceeds
// without a pin if no coords come back.

const NOMINATIM_BASE = 'https://nominatim.openstreetmap.org/search';
const USER_AGENT = 'SmithNet/1.0 (operator console)';
const RATE_LIMIT_MS = 1100; // 1.1s gap → ≤1 req/sec safely

let nextAllowedAt = 0;

/**
 * Test-only — resets the rate-limit clock so tests start from a clean state.
 */
export function __resetGeocoderState(): void {
  nextAllowedAt = 0;
}

export async function geocodeLocation(text: string): Promise<{ lat: number; lng: number } | null> {
  // Token-bucket wait
  const now = Date.now();
  if (now < nextAllowedAt) {
    await new Promise((r) => setTimeout(r, nextAllowedAt - now));
  }
  nextAllowedAt = Math.max(Date.now(), nextAllowedAt) + RATE_LIMIT_MS;

  try {
    const url = `${NOMINATIM_BASE}?q=${encodeURIComponent(text)}&format=json&limit=1`;
    const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT } });
    if (!res.ok) {
      console.warn(`[Geocoder] non-2xx ${res.status} for: ${text}`);
      return null;
    }
    const arr = (await res.json()) as Array<{ lat: string; lon: string }>;
    if (!Array.isArray(arr) || arr.length === 0) {
      console.warn(`[Geocoder] no result for: ${text}`);
      return null;
    }
    const lat = parseFloat(arr[0].lat);
    const lng = parseFloat(arr[0].lon);
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
      console.warn(`[Geocoder] non-finite coords for: ${text}`);
      return null;
    }
    return { lat, lng };
  } catch (e: any) {
    console.warn(`[Geocoder] error for "${text}": ${e.message}`);
    return null;
  }
}
