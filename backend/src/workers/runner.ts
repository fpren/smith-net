/**
 * Phase 3 Slice 1: worker process entrypoint.
 *
 * Run via `npm run worker`. Connects to the same Postgres as the api
 * process and loops over each registered worker's tick() function.
 * Sleeps 1s when no work; exits cleanly on SIGTERM / SIGINT.
 *
 * Phase 3 ships 3 workers; Phase 4 adds daemons inside the same runner.
 */

import { tick as geocodeTick } from './geocodeWorker';
import { tick as auditFlushTick } from './auditFlushWorker';
import { tick as emailTick } from './emailWorker';
import { baseLogger } from '../log';

const WORKER_ID = `${process.pid}@${process.env.HOSTNAME ?? 'host'}`;
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

baseLogger.info({ event: 'worker_starting', workerId: WORKER_ID }, 'worker starting');
void loop('geocode', geocodeTick);
void loop('audit_flush', auditFlushTick);
void loop('email', emailTick);
// All Phase 3 workers registered. Phase 4 adds daemons inside this runner.
