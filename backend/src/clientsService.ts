// backend/src/clientsService.ts
import { pg, isPgEnabled } from './db';
import { v4 as uuidv4 } from 'uuid';

export interface Client {
  id: string;
  ownerId: string;
  name: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  company: string | null;
  notes: string | null;
  createdAt: Date;
  updatedAt: Date;
}

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[ClientsService] Postgres client not initialized');
  return pg;
}

function mapClientRow(row: any): Client {
  return {
    id: row.id,
    ownerId: row.owner_id,
    name: row.name,
    email: row.email,
    phone: row.phone,
    address: row.address,
    company: row.company,
    notes: row.notes,
    createdAt: new Date(row.created_at),
    updatedAt: new Date(row.updated_at),
  };
}

export interface CreateClientInput {
  ownerId: string;
  name: string;
  email?: string;
  phone?: string;
  address?: string;
  company?: string;
  notes?: string;
}

export async function create(input: CreateClientInput): Promise<Client> {
  const db = requirePg();
  const id = uuidv4();
  const now = new Date();
  const { rows } = await db.query(
    `INSERT INTO clients (id, owner_id, name, email, phone, address, company, notes, created_at, updated_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $9)
     RETURNING *`,
    [id, input.ownerId, input.name, input.email ?? null, input.phone ?? null,
     input.address ?? null, input.company ?? null, input.notes ?? null, now]
  );
  return mapClientRow(rows[0]);
}

export async function listByOwner(ownerId: string, q?: string): Promise<Client[]> {
  const db = requirePg();
  if (q && q.trim()) {
    const { rows } = await db.query(
      `SELECT * FROM clients
        WHERE owner_id = $1 AND is_deleted = FALSE AND (name ILIKE $2 OR company ILIKE $2)
        ORDER BY lower(name) ASC`,
      [ownerId, `%${q.trim()}%`]
    );
    return rows.map(mapClientRow);
  }
  const { rows } = await db.query(
    `SELECT * FROM clients WHERE owner_id = $1 AND is_deleted = FALSE ORDER BY lower(name) ASC`,
    [ownerId]
  );
  return rows.map(mapClientRow);
}

// Owner-scoped get; returns null for missing OR cross-owner OR soft-deleted.
export async function getById(ownerId: string, id: string): Promise<Client | null> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM clients WHERE id = $1 AND owner_id = $2 AND is_deleted = FALSE`,
    [id, ownerId]
  );
  return rows.length === 0 ? null : mapClientRow(rows[0]);
}

export interface UpdateClientPatch {
  name?: string;
  email?: string | null;
  phone?: string | null;
  address?: string | null;
  company?: string | null;
  notes?: string | null;
}

export async function update(ownerId: string, id: string, patch: UpdateClientPatch): Promise<Client | null> {
  const db = requirePg();
  const sets: string[] = [];
  const vals: any[] = [];
  let i = 1;
  for (const [col, key] of [['name','name'],['email','email'],['phone','phone'],
       ['address','address'],['company','company'],['notes','notes']] as const) {
    if (key in patch) { sets.push(`${col} = $${i++}`); vals.push((patch as any)[key]); }
  }
  if (sets.length === 0) return getById(ownerId, id);
  sets.push(`updated_at = now()`);
  vals.push(id, ownerId);
  const { rows } = await db.query(
    `UPDATE clients SET ${sets.join(', ')}
      WHERE id = $${i++} AND owner_id = $${i++} AND is_deleted = FALSE
      RETURNING *`,
    vals
  );
  return rows.length === 0 ? null : mapClientRow(rows[0]);
}

export async function softDelete(ownerId: string, id: string): Promise<boolean> {
  const db = requirePg();
  const { rowCount } = await db.query(
    `UPDATE clients SET is_deleted = TRUE, updated_at = now()
      WHERE id = $1 AND owner_id = $2 AND is_deleted = FALSE`,
    [id, ownerId]
  );
  return (rowCount ?? 0) > 0;
}
