import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import { pg, isPgEnabled } from '../db';

/**
 * Generic idempotency for create mutations (W6 offline outbox support).
 *
 * A request that carries an `Idempotency-Key` header is processed at most once
 * per (key, scope=user id). The first request runs the handler and the response
 * (status + JSON body) is cached; any replay returns the cached response instead
 * of running the handler again -- so the portal's offline outbox can safely
 * retry a create that may already have succeeded, without duplicating rows.
 *
 * No key, or no DB, => pass through unchanged (the feature is additive).
 * A concurrent replay while the first is still in-flight gets 409 in_progress;
 * the outbox treats that as "retry later".
 */
export function idempotency() {
  return async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    const key = req.header('Idempotency-Key');
    if (!key || !isPgEnabled() || !pg) return next();

    const scope = req.user?.id ?? 'anon';

    // Look for an existing record.
    const existing = await pg.query(
      `SELECT status, response FROM idempotency_keys WHERE key = $1 AND scope = $2`,
      [key, scope]
    );
    if (existing.rows.length) {
      const row = existing.rows[0];
      if (row.status === 'completed' && row.response) {
        const cached = row.response as { status: number; body: any };
        return res.status(cached.status).json(cached.body);
      }
      return res.status(409).json({ error: 'Request already in progress', code: 'idempotency_in_progress' });
    }

    // Claim the key. The PK makes this atomic: a racing request hits 23505.
    try {
      await pg.query(
        `INSERT INTO idempotency_keys (key, scope, status) VALUES ($1, $2, 'in_progress')`,
        [key, scope]
      );
    } catch (e: any) {
      if (e?.code === '23505') {
        return res.status(409).json({ error: 'Request already in progress', code: 'idempotency_in_progress' });
      }
      return next(e);
    }

    // Capture the JSON response so replays can return it verbatim.
    const originalJson = res.json.bind(res);
    res.json = (body: any) => {
      const status = res.statusCode;
      // Only cache success; leave failures un-cached so a transient error can be
      // genuinely retried with the same key.
      if (status >= 200 && status < 300) {
        pg!.query(
          `UPDATE idempotency_keys SET status = 'completed', response = $1::jsonb
             WHERE key = $2 AND scope = $3`,
          [JSON.stringify({ status, body }), key, scope]
        ).catch(() => { /* best-effort cache; response still returns */ });
      } else {
        pg!.query(`DELETE FROM idempotency_keys WHERE key = $1 AND scope = $2`, [key, scope])
          .catch(() => { /* best-effort */ });
      }
      return originalJson(body);
    };

    next();
  };
}
