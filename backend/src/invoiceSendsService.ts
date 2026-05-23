// backend/src/invoiceSendsService.ts
//
// The "send" domain for invoices: the monthly send counter (pdf_sends_per_month
// cap) and the atomic, org-fenced send mutation. invoice_sends is append-only:
// one row per send action.

import { pg, isPgEnabled } from './db';

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[InvoiceSendsService] Postgres client not initialized');
  return pg;
}

/** First-of-month in UTC, so the cap window is deterministic regardless of server TZ. */
export function utcMonthStart(now: Date = new Date()): Date {
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
}

/** Count send actions by this user in the current calendar month (UTC). */
export async function countSendsThisMonth(userId: string): Promise<number> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT COUNT(*)::int AS c FROM invoice_sends
       WHERE sent_by = $1 AND sent_at >= $2`,
    [userId, utcMonthStart()]
  );
  return rows[0].c;
}

export interface SendResult {
  invoiceId: string;
  sentAt: Date;
}

/**
 * Atomically mark an invoice 'sent' (org-fenced, mirroring invoicesService) and
 * append one invoice_sends row. Returns null if no invoice matches the
 * org + id + not-deleted fence (route -> 404). Either both writes land or neither.
 */
export async function sendInvoice(
  invoiceId: string,
  organizationId: string,
  sentBy: string
): Promise<SendResult | null> {
  const db = requirePg();
  const client = await db.connect();
  try {
    await client.query('BEGIN');
    const upd = await client.query(
      `UPDATE invoices SET status = 'sent', updated_at = now()
         WHERE id = $1 AND organization_id = $2 AND is_deleted = false
         RETURNING id`,
      [invoiceId, organizationId]
    );
    if ((upd.rowCount ?? 0) === 0) {     // no row matched the org fence -> not found
      await client.query('ROLLBACK');
      return null;
    }
    const ins = await client.query(
      `INSERT INTO invoice_sends (invoice_id, sent_by) VALUES ($1, $2) RETURNING sent_at`,
      [invoiceId, sentBy]
    );
    await client.query('COMMIT');
    return { invoiceId, sentAt: new Date(ins.rows[0].sent_at) };
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
  }
}
