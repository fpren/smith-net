// backend/src/daemons/founderSeatsExpirerDaemon.ts
//
// Sub-project 5: release expired founder-seat holds (10-min holds) back to
// available. Housekeeping -- reserve()/getAllCounts() already treat expired
// holds as available, so correctness does not depend on this cadence.

import { isPgEnabled, pg } from '../db';
import { releaseExpiredHolds } from '../founderSeatService';
import { requestLogger } from '../log';

export const INTERVAL_MS = 60 * 1000; // 60s

export async function tick(): Promise<void> {
  if (!isPgEnabled() || !pg) return;
  const released = await releaseExpiredHolds();
  if (released > 0) {
    requestLogger().info({ event: 'founder_holds_released', count: released }, 'released expired founder holds');
  }
}
