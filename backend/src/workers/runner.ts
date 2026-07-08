/**
 * Phase 3 Slice 1 / Phase 4 Slice 1: worker + daemon entrypoint.
 *
 * Run via `npm run worker`. Connects to the same Postgres as the api
 * process and runs:
 *   - workers: tick() returns true if it did work; runner sleeps 1s
 *     when no work.
 *   - daemons: tick() runs on a fixed cadence regardless of work.
 *
 * Exits cleanly on SIGTERM / SIGINT.
 */

// Load .env before any worker module reads process.env (db.ts reads
// DATABASE_URL at import time). Must stay the first import. No-op in
// production, where the platform injects real env vars.
import 'dotenv/config';
import { tick as geocodeTick } from './geocodeWorker';
import { tick as auditFlushTick } from './auditFlushWorker';
import { tick as emailTick } from './emailWorker';
import { tick as heartbeatTick, INTERVAL_MS as HEARTBEAT_MS } from '../daemons/heartbeatDaemon';
import { tick as queueWatcherTick, INTERVAL_MS as QUEUE_WATCHER_MS } from '../daemons/queueWatcherDaemon';
import { tick as cleanupTick, INTERVAL_MS as CLEANUP_MS } from '../daemons/cleanupDaemon';
import { tick as presenceWatcherTick, INTERVAL_MS as PRESENCE_WATCHER_MS } from '../daemons/presenceWatcherDaemon';
import { tick as trialExpirerTick, INTERVAL_MS as TRIAL_EXPIRER_MS } from '../daemons/trialExpirerDaemon';
import { tick as founderSeatsExpirerTick, INTERVAL_MS as FOUNDER_SEATS_EXPIRER_MS } from '../daemons/founderSeatsExpirerDaemon';
import { baseLogger } from '../log';
import { initSmithCore } from '../core/smithCore';

const WORKER_ID = `${process.pid}@${process.env.HOSTNAME ?? 'host'}`;
const REGISTERED_KINDS = ['geocode', 'audit_flush', 'email'];
const SHUTDOWN = { stop: false };

process.on('SIGTERM', () => { baseLogger.info({ event: 'worker_sigterm' }, 'worker received SIGTERM'); SHUTDOWN.stop = true; });
process.on('SIGINT',  () => { baseLogger.info({ event: 'worker_sigint' },  'worker received SIGINT');  SHUTDOWN.stop = true; });

async function loop(kind: string, fn: (id: string) => Promise<boolean>) {
  while (!SHUTDOWN.stop) {
    const did = await fn(WORKER_ID).catch((e) => {
      baseLogger.error({ event: 'worker_tick_error', kind, err: e }, 'worker tick error');
      return false;
    });
    if (!did) await new Promise((r) => setTimeout(r, 1000));
  }
  baseLogger.info({ event: 'worker_loop_stopped', kind }, 'worker loop stopped');
}

async function daemonLoop(name: string, intervalMs: number, fn: () => Promise<void>) {
  // Fire once immediately so a fresh process appears in worker_heartbeats
  // (and other daemons take a first pass) without waiting the full interval.
  while (!SHUTDOWN.stop) {
    const startedAt = Date.now();
    await fn().catch((e) =>
      baseLogger.error({ event: 'daemon_tick_error', name, err: e }, 'daemon tick error')
    );
    const elapsed = Date.now() - startedAt;
    const wait = Math.max(0, intervalMs - elapsed);
    if (wait > 0) await new Promise((r) => setTimeout(r, wait));
  }
  baseLogger.info({ event: 'daemon_loop_stopped', name }, 'daemon loop stopped');
}

async function main() {
  baseLogger.info({ event: 'worker_starting', workerId: WORKER_ID }, 'worker starting');

  try {
    await initSmithCore();
    baseLogger.info({ event: 'smithcore_ready' }, 'smithcore ROM loaded');
  } catch (e) {
    baseLogger.warn({ event: 'smithcore_init_failed', err: e }, 'smithcore ROM not loaded; falling back to node crypto');
  }

  void loop('geocode', geocodeTick);
  void loop('audit_flush', auditFlushTick);
  void loop('email', emailTick);

  void daemonLoop('heartbeat',     HEARTBEAT_MS,     () => heartbeatTick(WORKER_ID, REGISTERED_KINDS));
  void daemonLoop('queue_watcher', QUEUE_WATCHER_MS, queueWatcherTick);
  void daemonLoop('cleanup',          CLEANUP_MS,          cleanupTick);
  void daemonLoop('presence_watcher', PRESENCE_WATCHER_MS, presenceWatcherTick);
  void daemonLoop('trial_expirer', TRIAL_EXPIRER_MS, trialExpirerTick);
  void daemonLoop('founder_seats_expirer', FOUNDER_SEATS_EXPIRER_MS, founderSeatsExpirerTick);
}

main().catch((e) => {
  baseLogger.fatal({ event: 'worker_main_error', err: e }, 'worker failed to start');
  process.exit(1);
});
