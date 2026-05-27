// backend/src/materialsService.ts
//
// Per-job materials checklist with cost capture. Mirrors the APK Material
// data class. Owner check is enforced at route level (requireJobOwner for
// list/create; requireMaterialOwner for patch/delete).

import { pg, isPgEnabled } from './db';
import { auditLog, AuditAction } from './auditLog';
import { v4 as uuidv4 } from 'uuid';

export interface Material {
  id: string;
  jobId: string;
  name: string;
  notes: string | null;
  checked: boolean;
  checkedAt: Date | null;
  quantity: number;
  unit: string;
  unitCost: number;
  vendor: string | null;
  createdAt: Date;
  updatedAt: Date;
}

export class NotFoundError extends Error {
  constructor(message: string = 'Material not found') {
    super(message);
    this.name = 'NotFoundError';
  }
}

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[MaterialsService] Postgres client not initialized');
  return pg;
}

function mapRow(row: any): Material {
  return {
    id: row.id,
    jobId: row.job_id,
    name: row.name,
    notes: row.notes,
    checked: row.checked,
    checkedAt: row.checked_at ? new Date(row.checked_at) : null,
    quantity: Number(row.quantity),
    unit: row.unit,
    unitCost: Number(row.unit_cost),
    vendor: row.vendor,
    createdAt: new Date(row.created_at),
    updatedAt: new Date(row.updated_at),
  };
}

export async function listByJob(jobId: string): Promise<Material[]> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM materials WHERE job_id = $1 ORDER BY created_at ASC`,
    [jobId],
  );
  return rows.map(mapRow);
}

export async function getById(id: string): Promise<Material | null> {
  const db = requirePg();
  const { rows } = await db.query(`SELECT * FROM materials WHERE id = $1`, [id]);
  return rows.length === 0 ? null : mapRow(rows[0]);
}

export interface CreateMaterialInput {
  jobId: string;
  name: string;
  notes?: string;
  quantity?: number;
  unit?: string;
  unitCost?: number;
  vendor?: string;
}

export async function create(input: CreateMaterialInput, actorId: string): Promise<Material> {
  const db = requirePg();
  const id = uuidv4();
  const { rows } = await db.query(
    `INSERT INTO materials
       (id, job_id, name, notes, quantity, unit, unit_cost, vendor)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
     RETURNING *`,
    [
      id, input.jobId, input.name,
      input.notes ?? null,
      input.quantity ?? 1,
      input.unit ?? 'ea',
      input.unitCost ?? 0,
      input.vendor ?? null,
    ],
  );
  const m = mapRow(rows[0]);
  await auditLog.log(AuditAction.MATERIAL_CREATED, actorId, {
    materialId: m.id, jobId: m.jobId, name: m.name,
  });
  return m;
}

export interface UpdateMaterialPatch {
  name?: string;
  notes?: string | null;
  quantity?: number;
  unit?: string;
  unitCost?: number;
  vendor?: string | null;
  checked?: boolean;
}

export async function update(id: string, patch: UpdateMaterialPatch, actorId: string): Promise<Material | null> {
  const db = requirePg();
  const sets: string[] = [];
  const vals: any[] = [];
  let i = 1;
  for (const [col, key] of [
    ['name', 'name'], ['notes', 'notes'], ['quantity', 'quantity'],
    ['unit', 'unit'], ['unit_cost', 'unitCost'], ['vendor', 'vendor'],
  ] as const) {
    if (key in patch) { sets.push(`${col} = $${i++}`); vals.push((patch as any)[key]); }
  }
  if ('checked' in patch) {
    sets.push(`checked = $${i++}`);
    vals.push(patch.checked);
    sets.push(`checked_at = $${i++}`);
    vals.push(patch.checked ? new Date() : null);
  }
  if (sets.length === 0) return getById(id);
  sets.push(`updated_at = NOW()`);
  vals.push(id);
  const { rows } = await db.query(
    `UPDATE materials SET ${sets.join(', ')} WHERE id = $${i} RETURNING *`,
    vals,
  );
  if (rows.length === 0) return null;
  const m = mapRow(rows[0]);
  await auditLog.log(AuditAction.MATERIAL_UPDATED, actorId, {
    materialId: m.id, jobId: m.jobId, fields: Object.keys(patch),
  });
  return m;
}

export async function hardDelete(id: string, actorId: string): Promise<boolean> {
  const db = requirePg();
  const existing = await getById(id);
  if (!existing) return false;
  await db.query(`DELETE FROM materials WHERE id = $1`, [id]);
  await auditLog.log(AuditAction.MATERIAL_DELETED, actorId, {
    materialId: id, jobId: existing.jobId,
  });
  return true;
}
