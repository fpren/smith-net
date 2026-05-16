/**
 * Phase 3 Slice 1: geocode worker.
 *
 * Claims kind='geocode' jobs, calls Nominatim, UPDATEs the jobs row.
 * Replaces the Plan 4 fire-and-forget geocode pattern in jobsService.
 */

import { pg, isPgEnabled } from '../db';
import { claimNext, complete, fail } from '../queue/queue';
import { requestLogger } from '../log';

const KIND = 'geocode';
const NOMINATIM_URL = 'https://nominatim.openstreetmap.org/search';

interface GeocodePayload { job_id: string; address: string; }

export async function tick(workerId: string): Promise<boolean> {
  if (!isPgEnabled() || !pg) return false;
  const job = await claimNext(KIND, workerId);
  if (!job) return false;

  const payload = job.payload as unknown as GeocodePayload;
  try {
    const url = `${NOMINATIM_URL}?format=json&q=${encodeURIComponent(payload.address)}`;
    const res = await fetch(url, { headers: { 'User-Agent': 'smith-net/1.0' } });
    if (!res.ok) throw new Error(`nominatim ${res.status}`);
    const arr = (await res.json()) as Array<{ lat: string; lon: string }>;
    if (!arr.length) throw new Error('nominatim no_match');

    await pg.query(
      `UPDATE jobs
          SET latitude = $2::double precision,
              longitude = $3::double precision,
              geocoded_at = NOW(),
              updated_at = NOW()
        WHERE id = $1`,
      [payload.job_id, arr[0].lat, arr[0].lon]
    );

    await complete(job.id);
    requestLogger().info({ event: 'geocode_succeeded', jobId: job.id, lat: arr[0].lat, lon: arr[0].lon }, 'geocode succeeded');
    return true;
  } catch (err) {
    await fail(job.id, err as Error, { attempts: job.attempts, maxAttempts: job.max_attempts });
    requestLogger().warn({ event: 'geocode_failed', jobId: job.id, attempts: job.attempts, err: (err as Error).message }, 'geocode failed');
    return true;
  }
}
