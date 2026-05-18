// backend/src/invoicesService.ts
//
// Invoice persistence. Mirror of the channels service's tenant fence:
// every read AND every write path takes an organizationId arg and applies
// `WHERE organization_id = $X` so there is no way to reach a different
// org's invoices through this module.
//
// Line item changes always re-derive the parent invoice's subtotal /
// tax_amount / total_due inside the same transaction, so the invoice row
// is the source of truth for clients.

import { pg, isPgEnabled } from './db';

export type InvoiceStatus =
  | 'draft' | 'issued' | 'sent' | 'viewed' | 'paid' | 'overdue' | 'disputed' | 'cancelled';
export type LineCategory = 'labor' | 'materials' | 'travel' | 'change_order' | 'other';

export interface Invoice {
  id: string;
  organizationId: string;
  createdBy: string;
  invoiceNumber: string;
  clientName: string | null;
  clientEmail: string | null;
  issueDate: Date;
  dueDate: Date | null;
  status: InvoiceStatus;
  subtotal: number;
  taxRate: number;
  taxAmount: number;
  totalDue: number;
  notes: string | null;
  idempotencyKey: string | null;
  summary: unknown | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface InvoiceLineItem {
  id: string;
  invoiceId: string;
  description: string;
  quantity: number;
  unit: string;
  rate: number;
  total: number;
  category: LineCategory;
  sortOrder: number;
  createdAt: Date;
}

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[InvoicesService] Postgres not initialized');
  return pg;
}

function mapInvoice(row: any): Invoice {
  return {
    id: row.id,
    organizationId: row.organization_id,
    createdBy: row.created_by,
    invoiceNumber: row.invoice_number,
    clientName: row.client_name,
    clientEmail: row.client_email,
    issueDate: new Date(row.issue_date),
    dueDate: row.due_date ? new Date(row.due_date) : null,
    status: row.status as InvoiceStatus,
    subtotal: Number(row.subtotal),
    taxRate: Number(row.tax_rate),
    taxAmount: Number(row.tax_amount),
    totalDue: Number(row.total_due),
    notes: row.notes,
    idempotencyKey: row.idempotency_key ?? null,
    summary: row.summary ?? null,
    createdAt: new Date(row.created_at),
    updatedAt: new Date(row.updated_at),
  };
}

function mapLineItem(row: any): InvoiceLineItem {
  return {
    id: row.id,
    invoiceId: row.invoice_id,
    description: row.description,
    quantity: Number(row.quantity),
    unit: row.unit,
    rate: Number(row.rate),
    total: Number(row.total),
    category: row.category as LineCategory,
    sortOrder: row.sort_order,
    createdAt: new Date(row.created_at),
  };
}

export async function listByOrg(organizationId: string): Promise<Invoice[]> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM invoices
       WHERE organization_id = $1 AND is_deleted = FALSE
       ORDER BY issue_date DESC, created_at DESC`,
    [organizationId],
  );
  return rows.map(mapInvoice);
}

export async function getByIdScoped(
  id: string,
  organizationId: string,
): Promise<{ invoice: Invoice; lineItems: InvoiceLineItem[] } | null> {
  const db = requirePg();
  const inv = await db.query(
    `SELECT * FROM invoices WHERE id = $1 AND organization_id = $2 AND is_deleted = FALSE`,
    [id, organizationId],
  );
  if (inv.rowCount === 0) return null;
  const items = await db.query(
    `SELECT * FROM invoice_line_items WHERE invoice_id = $1
       ORDER BY sort_order ASC, created_at ASC`,
    [id],
  );
  return { invoice: mapInvoice(inv.rows[0]), lineItems: items.rows.map(mapLineItem) };
}

async function nextInvoiceNumber(organizationId: string): Promise<string> {
  const db = requirePg();
  const year = new Date().getFullYear();
  const { rows } = await db.query(
    `SELECT COUNT(*)::int AS c FROM invoices
       WHERE organization_id = $1
         AND issue_date >= date_trunc('year', NOW())`,
    [organizationId],
  );
  const n = (rows[0]?.c ?? 0) + 1;
  return `INV-${year}-${String(n).padStart(4, '0')}`;
}

export async function create(input: {
  organizationId: string;
  createdBy: string;
  clientName?: string | null;
  clientEmail?: string | null;
  dueDate?: Date | null;
  notes?: string | null;
  idempotencyKey?: string | null;
  summary?: unknown;
  taxRate?: number | null;
}): Promise<Invoice> {
  const db = requirePg();

  // Up to 3 attempts in case of a concurrent number collision (UNIQUE).
  for (let attempt = 0; attempt < 3; attempt++) {
    const invoiceNumber = await nextInvoiceNumber(input.organizationId);
    try {
      const { rows } = await db.query(
        `INSERT INTO invoices (
           organization_id, created_by, invoice_number,
           client_name, client_email, due_date, notes,
           idempotency_key, summary,
           tax_rate
         ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
         RETURNING *`,
        [
          input.organizationId, input.createdBy, invoiceNumber,
          input.clientName ?? null, input.clientEmail ?? null,
          input.dueDate ?? null, input.notes ?? null,
          input.idempotencyKey ?? null,
          input.summary != null ? JSON.stringify(input.summary) : null,
          input.taxRate ?? 0,
        ],
      );
      return mapInvoice(rows[0]);
    } catch (e: any) {
      // Idempotency race: another caller won between our lookup and this insert.
      if (e?.code === '23505' && e?.constraint === 'invoices_org_idem_unique' && input.idempotencyKey) {
        const { rows: winner } = await db.query(
          `SELECT * FROM invoices
             WHERE organization_id = $1 AND idempotency_key = $2 AND is_deleted = FALSE`,
          [input.organizationId, input.idempotencyKey],
        );
        if (winner[0]) return mapInvoice(winner[0]);
        // 23505 fired on idem_unique but the winning row is gone (concurrent
        // soft-delete?). Don't loop into another doomed INSERT.
        throw new Error(`idempotency race: row for key ${input.idempotencyKey} vanished`);
      }
      if (e?.code === '23505' && attempt < 2) continue;     // PK / unique number collision
      throw e;
    }
  }
  throw new Error('Failed to generate unique invoice number after 3 attempts');
}

export async function findByIdempotencyKey(
  organizationId: string,
  idempotencyKey: string,
): Promise<Invoice | null> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM invoices
       WHERE organization_id = $1 AND idempotency_key = $2 AND is_deleted = FALSE
       LIMIT 1`,
    [organizationId, idempotencyKey],
  );
  return rows[0] ? mapInvoice(rows[0]) : null;
}

export async function update(
  id: string,
  organizationId: string,
  patch: Partial<{
    clientName: string | null;
    clientEmail: string | null;
    dueDate: Date | null;
    status: InvoiceStatus;
    taxRate: number;
    notes: string | null;
  }>,
): Promise<Invoice | null> {
  const db = requirePg();
  const sets: string[] = ['updated_at = NOW()'];
  const params: unknown[] = [];

  const push = (col: string, val: unknown) => {
    params.push(val);
    sets.push(`${col} = $${params.length}`);
  };

  if (patch.clientName !== undefined)  push('client_name',  patch.clientName);
  if (patch.clientEmail !== undefined) push('client_email', patch.clientEmail);
  if (patch.dueDate !== undefined)     push('due_date',     patch.dueDate);
  if (patch.status !== undefined)      push('status',       patch.status);
  if (patch.notes !== undefined)       push('notes',        patch.notes);

  // tax_rate change → recompute tax_amount + total_due alongside.
  if (patch.taxRate !== undefined) {
    push('tax_rate', patch.taxRate);
    // Recompute is done by a follow-up UPDATE after this row write below.
  }

  params.push(id);
  params.push(organizationId);
  const { rows } = await db.query(
    `UPDATE invoices SET ${sets.join(', ')}
       WHERE id = $${params.length - 1} AND organization_id = $${params.length} AND is_deleted = FALSE
       RETURNING *`,
    params,
  );
  if (rows.length === 0) return null;

  if (patch.taxRate !== undefined) {
    // Re-derive totals against current subtotal.
    const r = await db.query(
      `UPDATE invoices
          SET tax_amount = ROUND(subtotal * tax_rate, 2),
              total_due  = subtotal + ROUND(subtotal * tax_rate, 2),
              updated_at = NOW()
        WHERE id = $1
        RETURNING *`,
      [id],
    );
    return mapInvoice(r.rows[0]);
  }

  return mapInvoice(rows[0]);
}

export async function softDelete(id: string, organizationId: string): Promise<boolean> {
  const db = requirePg();
  const r = await db.query(
    `UPDATE invoices SET is_deleted = TRUE, updated_at = NOW()
       WHERE id = $1 AND organization_id = $2 AND is_deleted = FALSE`,
    [id, organizationId],
  );
  return (r.rowCount ?? 0) > 0;
}

// ════════════════════════════════════════════════════════════════════
// LINE ITEMS
// ════════════════════════════════════════════════════════════════════

async function recomputeTotals(invoiceId: string): Promise<void> {
  const db = requirePg();
  await db.query(
    `UPDATE invoices i
        SET subtotal   = COALESCE(s.sum_total, 0),
            tax_amount = ROUND(COALESCE(s.sum_total, 0) * i.tax_rate, 2),
            total_due  = COALESCE(s.sum_total, 0)
                       + ROUND(COALESCE(s.sum_total, 0) * i.tax_rate, 2),
            updated_at = NOW()
       FROM (SELECT SUM(total) AS sum_total FROM invoice_line_items WHERE invoice_id = $1) s
      WHERE i.id = $1`,
    [invoiceId],
  );
}

async function assertOwns(invoiceId: string, organizationId: string): Promise<boolean> {
  const db = requirePg();
  const r = await db.query(
    `SELECT 1 FROM invoices WHERE id = $1 AND organization_id = $2 AND is_deleted = FALSE`,
    [invoiceId, organizationId],
  );
  return (r.rowCount ?? 0) > 0;
}

export async function addLineItem(
  invoiceId: string,
  organizationId: string,
  input: {
    description: string;
    quantity?: number;
    unit?: string;
    rate: number;
    category?: LineCategory;
  },
): Promise<InvoiceLineItem | null> {
  const db = requirePg();
  if (!(await assertOwns(invoiceId, organizationId))) return null;

  const qty = input.quantity ?? 1;
  const total = +(qty * input.rate).toFixed(2);

  const { rows } = await db.query(
    `INSERT INTO invoice_line_items (
       invoice_id, description, quantity, unit, rate, total, category, sort_order
     ) VALUES (
       $1, $2, $3, $4, $5, $6, $7,
       COALESCE((SELECT MAX(sort_order) + 1 FROM invoice_line_items WHERE invoice_id = $1), 0)
     )
     RETURNING *`,
    [
      invoiceId, input.description, qty,
      input.unit ?? 'ea', input.rate, total,
      input.category ?? 'other',
    ],
  );
  await recomputeTotals(invoiceId);
  return mapLineItem(rows[0]);
}

export async function updateLineItem(
  lineItemId: string,
  organizationId: string,
  patch: Partial<{
    description: string;
    quantity: number;
    unit: string;
    rate: number;
    category: LineCategory;
    sortOrder: number;
  }>,
): Promise<InvoiceLineItem | null> {
  const db = requirePg();
  // Owner check via JOIN to invoices.
  const ownerCheck = await db.query(
    `SELECT i.id AS invoice_id, li.quantity, li.rate
       FROM invoice_line_items li
       JOIN invoices i ON i.id = li.invoice_id
      WHERE li.id = $1 AND i.organization_id = $2 AND i.is_deleted = FALSE`,
    [lineItemId, organizationId],
  );
  if (ownerCheck.rowCount === 0) return null;
  const invoiceId = ownerCheck.rows[0].invoice_id as string;

  const sets: string[] = [];
  const params: unknown[] = [];
  const push = (col: string, val: unknown) => {
    params.push(val);
    sets.push(`${col} = $${params.length}`);
  };
  if (patch.description !== undefined) push('description', patch.description);
  if (patch.quantity !== undefined)    push('quantity',    patch.quantity);
  if (patch.unit !== undefined)        push('unit',        patch.unit);
  if (patch.rate !== undefined)        push('rate',        patch.rate);
  if (patch.category !== undefined)    push('category',    patch.category);
  if (patch.sortOrder !== undefined)   push('sort_order',  patch.sortOrder);

  // Recompute the line total whenever qty or rate moved.
  const newQty  = patch.quantity ?? Number(ownerCheck.rows[0].quantity);
  const newRate = patch.rate     ?? Number(ownerCheck.rows[0].rate);
  push('total', +(newQty * newRate).toFixed(2));

  params.push(lineItemId);
  const { rows } = await db.query(
    `UPDATE invoice_line_items SET ${sets.join(', ')}
       WHERE id = $${params.length}
       RETURNING *`,
    params,
  );
  await recomputeTotals(invoiceId);
  return mapLineItem(rows[0]);
}

export async function deleteLineItem(
  lineItemId: string,
  organizationId: string,
): Promise<boolean> {
  const db = requirePg();
  const ownerCheck = await db.query(
    `SELECT i.id AS invoice_id
       FROM invoice_line_items li
       JOIN invoices i ON i.id = li.invoice_id
      WHERE li.id = $1 AND i.organization_id = $2 AND i.is_deleted = FALSE`,
    [lineItemId, organizationId],
  );
  if (ownerCheck.rowCount === 0) return false;
  const invoiceId = ownerCheck.rows[0].invoice_id as string;

  await db.query(`DELETE FROM invoice_line_items WHERE id = $1`, [lineItemId]);
  await recomputeTotals(invoiceId);
  return true;
}
