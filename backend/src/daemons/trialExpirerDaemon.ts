// backend/src/daemons/trialExpirerDaemon.ts
//
// Sub-project 4: hourly trial expiry. Reverts users.tier to each due trial's
// previous_tier and emits tier_upgrade.trial_expired. Mirrors cleanupDaemon
// (exports INTERVAL_MS + tick; registered in workers/runner.ts).

import { isPgEnabled, pg } from '../db';
import { expireDueTrials } from '../trialService';
import { emitGateHit } from '../telemetryService';
import { requestLogger } from '../log';

export const INTERVAL_MS = 60 * 60 * 1000; // hourly

export async function tick(): Promise<void> {
  if (!isPgEnabled() || !pg) return;
  const expired = await expireDueTrials();
  for (const t of expired) {
    await emitGateHit(t.userId, 'tier_upgrade.trial_expired', t.previousTier, { trial_tier: t.tier });
  }
  if (expired.length > 0) {
    requestLogger().info({ event: 'trials_expired', count: expired.length }, 'expired due trials');
  }
}
