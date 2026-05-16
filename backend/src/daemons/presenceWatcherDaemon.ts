// backend/src/daemons/presenceWatcherDaemon.ts
//
// Phase 4 Slice 2: presence watcher daemon.
// 60s cadence. Audit-log only — never auto-ends shifts.
//
// Two signals:
//   stale_presence    — open shift + last GPS report > 30 min ago
//   ultra_long_shift  — open shift duration > 16 h
//
// Dedupe: per-process Set keyed by `${event}:${shift_id}`. A given shift
// emits each event at most once per daemon process lifetime. Worker restart
// clears the set; an at-most-monthly worker restart cadence is fine for
// this signal density.

import { pg, isPgEnabled } from '../db';
import { auditLog, AuditAction } from '../auditLog';
import { requestLogger } from '../log';

export const INTERVAL_MS = 60_000;
const STALE_GPS_MIN = 30;
const ULTRA_LONG_HOURS = 16;

const warned = new Set<string>();

// Test helper: clear the dedupe set between tests so each test gets a fresh slate.
export function __resetWarnedForTests(): void {
  warned.clear();
}

interface StalePresenceRow {
  shift_id: string;
  user_id: string;
  started_at: Date;
  last_position_at: Date | null;
}

interface UltraLongRow {
  shift_id: string;
  user_id: string;
  started_at: Date;
}

export async function tick(): Promise<void> {
  if (!isPgEnabled() || !pg) return;

  // Find open shifts whose latest GPS is older than the threshold (or has no
  // GPS at all but is older than the threshold — never reported a position).
  const stale = await pg.query<StalePresenceRow>(
    `SELECT s.id AS shift_id, s.user_id, s.started_at, p.recorded_at AS last_position_at
       FROM shifts s
       LEFT JOIN crew_positions p ON p.user_id = s.user_id
      WHERE s.ended_at IS NULL
        AND (
          (p.recorded_at IS NULL AND s.started_at < NOW() - ($1::int * INTERVAL '1 minute'))
          OR p.recorded_at < NOW() - ($1::int * INTERVAL '1 minute')
        )`,
    [STALE_GPS_MIN]
  );

  for (const row of stale.rows) {
    const key = `stale_presence:${row.shift_id}`;
    if (warned.has(key)) continue;
    warned.add(key);

    requestLogger().warn(
      { event: 'stale_presence', shiftId: row.shift_id, userId: row.user_id, lastPositionAt: row.last_position_at },
      'open shift with stale GPS'
    );
    await auditLog.log(AuditAction.ADMIN_ACTION, 'presenceWatcherDaemon', {
      event: 'stale_presence',
      shift_id: row.shift_id,
      user_id: row.user_id,
      last_position_at: row.last_position_at,
      threshold_min: STALE_GPS_MIN,
    });
  }

  // Ultra-long open shift — operator hint that a user probably forgot to clock out.
  const long = await pg.query<UltraLongRow>(
    `SELECT id AS shift_id, user_id, started_at
       FROM shifts
      WHERE ended_at IS NULL
        AND started_at < NOW() - ($1::int * INTERVAL '1 hour')`,
    [ULTRA_LONG_HOURS]
  );

  for (const row of long.rows) {
    const key = `ultra_long_shift:${row.shift_id}`;
    if (warned.has(key)) continue;
    warned.add(key);

    requestLogger().warn(
      { event: 'ultra_long_shift', shiftId: row.shift_id, userId: row.user_id, startedAt: row.started_at },
      'open shift exceeds duration threshold'
    );
    await auditLog.log(AuditAction.ADMIN_ACTION, 'presenceWatcherDaemon', {
      event: 'ultra_long_shift',
      shift_id: row.shift_id,
      user_id: row.user_id,
      started_at: row.started_at,
      threshold_hours: ULTRA_LONG_HOURS,
    });
  }
}
