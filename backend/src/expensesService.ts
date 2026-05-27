// backend/src/expensesService.ts
//
// Per-job expense lines. Mirrors materialsService.ts shape. Ownership at
// routes (requireJobOwner for create; requireExpenseOwner for patch/delete).

import { pg, isPgEnabled } from './db';
import { auditLog, AuditAction } from './auditLog';
import { v4 as uuidv4 } from 'uuid';

export interface Expense {
  id: string;
  jobId: string;
  category: string;
  description: string;
  amount: number;
  vendor: string | null;
  notes: string | null;
  expenseDate: string | null;  // ISO YYYY-MM-DD or null
  createdAt: Date;
  updatedAt: Date;
}

export class NotFoundError extends Error {
  constructor(message: string = 'Expense not found') {
    super(message);
    this.name = 'NotFoundError';
  }
}

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[ExpensesService] Postgres client not initialized');
  return pg;
}

function mapRow(row: any): Expense {
  return {
    id: row.id,
    jobId: row.job_id,
    category: row.category,
    description: row.description,
    amount: Number(row.amount),
    vendor: row.vendor,
    notes: row.notes,
    expenseDate: row.expense_date
      ? (row.expense_date instanceof Date
          ? row.expense_date.toISOString().slice(0, 10)
          : String(row.expense_date).slice(0, 10))
      : null,
    createdAt: new Date(row.created_at),
    updatedAt: new Date(row.updated_at),
  };
}

export async function listByJob(jobId: string): Promise<Expense[]> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM job_expenses WHERE job_id = $1 ORDER BY created_at ASC`,
    [jobId],
  );
  return rows.map(mapRow);
}

export async function getById(id: string): Promise<Expense | null> {
  const db = requirePg();
  const { rows } = await db.query(`SELECT * FROM job_expenses WHERE id = $1`, [id]);
  return rows.length === 0 ? null : mapRow(rows[0]);
}

export interface CreateExpenseInput {
  jobId: string;
  category: string;
  description: string;
  amount: number;
  vendor?: string;
  notes?: string;
  expenseDate?: string;
}

export async function create(input: CreateExpenseInput, actorId: string): Promise<Expense> {
  const db = requirePg();
  const id = uuidv4();
  const { rows } = await db.query(
    `INSERT INTO job_expenses
       (id, job_id, category, description, amount, vendor, notes, expense_date)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
     RETURNING *`,
    [
      id, input.jobId, input.category, input.description, input.amount,
      input.vendor ?? null, input.notes ?? null, input.expenseDate ?? null,
    ],
  );
  const e = mapRow(rows[0]);
  await auditLog.log(AuditAction.EXPENSE_CREATED, actorId, {
    expenseId: e.id, jobId: e.jobId, category: e.category, amount: e.amount,
  });
  return e;
}

export interface UpdateExpensePatch {
  category?: string;
  description?: string;
  amount?: number;
  vendor?: string | null;
  notes?: string | null;
  expenseDate?: string | null;
}

export async function update(id: string, patch: UpdateExpensePatch, actorId: string): Promise<Expense | null> {
  const db = requirePg();
  const sets: string[] = [];
  const vals: any[] = [];
  let i = 1;
  for (const [col, key] of [
    ['category', 'category'], ['description', 'description'], ['amount', 'amount'],
    ['vendor', 'vendor'], ['notes', 'notes'], ['expense_date', 'expenseDate'],
  ] as const) {
    if (key in patch) { sets.push(`${col} = $${i++}`); vals.push((patch as any)[key]); }
  }
  if (sets.length === 0) return getById(id);
  sets.push(`updated_at = NOW()`);
  vals.push(id);
  const { rows } = await db.query(
    `UPDATE job_expenses SET ${sets.join(', ')} WHERE id = $${i} RETURNING *`,
    vals,
  );
  if (rows.length === 0) return null;
  const e = mapRow(rows[0]);
  await auditLog.log(AuditAction.EXPENSE_UPDATED, actorId, {
    expenseId: e.id, jobId: e.jobId, fields: Object.keys(patch),
  });
  return e;
}

export async function hardDelete(id: string, actorId: string): Promise<boolean> {
  const db = requirePg();
  const existing = await getById(id);
  if (!existing) return false;
  await db.query(`DELETE FROM job_expenses WHERE id = $1`, [id]);
  await auditLog.log(AuditAction.EXPENSE_DELETED, actorId, {
    expenseId: id, jobId: existing.jobId,
  });
  return true;
}
