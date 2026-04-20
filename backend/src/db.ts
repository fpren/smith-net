/**
 * Postgres client — self-hosted replacement for Supabase persistence.
 *
 * Active when DATABASE_URL is set in the environment. Falls back to
 * a null client so legacy Supabase code paths continue to work until
 * they are individually migrated.
 */

import { Pool } from 'pg';

const DATABASE_URL = process.env.DATABASE_URL || '';

export const pg: Pool | null = DATABASE_URL
  ? new Pool({ connectionString: DATABASE_URL, max: 20, idleTimeoutMillis: 30_000 })
  : null;

if (pg) {
  pg.on('error', (err) => console.error('[pg] pool error:', err));
  pg.connect()
    .then((c) => {
      c.query('SELECT 1').then(() => {
        c.release();
        console.log('[pg] connected');
      });
    })
    .catch((err) => console.error('[pg] initial connect failed:', err.message));
} else {
  console.warn('[pg] DATABASE_URL not set — Postgres persistence disabled');
}

export function isPgEnabled(): boolean {
  return pg !== null;
}
