// desktop/portal/src/console/api/materialsClient.ts
// Mirrors tasksClient shape: typed result + thin wrapper around fetch.
import { httpCall } from './httpCall';

export interface Material {
  id: string;
  jobId: string;
  name: string;
  notes: string | null;
  checked: boolean;
  checkedAt: string | null;
  quantity: number;
  unit: string;
  unitCost: number;
  vendor: string | null;
  createdAt: string;
  updatedAt: string;
}

export type MaterialsResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string; details?: any; code?: string };

async function call<T>(path: string, opts: { method?: string; body?: any } = {}): Promise<MaterialsResult<T>> {
  const r = await httpCall<T>(path, {
    method: opts.method ?? 'GET',
    headers: { 'Content-Type': 'application/json' },
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });
  if (!r.ok) {
    const errBody = r.body ?? {};
    return { ok: false, status: r.status, error: r.error, details: errBody.details, code: errBody.code };
  }
  return { ok: true, ...((r.data ?? {}) as T) } as MaterialsResult<T>;
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

export interface UpdateMaterialInput {
  name?: string;
  notes?: string | null;
  quantity?: number;
  unit?: string;
  unitCost?: number;
  vendor?: string | null;
  checked?: boolean;
}

export const materialsClient = {
  listForJob: (jobId: string) =>
    call<{ materials: Material[] }>(`/api/jobs/${encodeURIComponent(jobId)}/materials`),
  create: (input: CreateMaterialInput) =>
    call<{ material: Material }>(`/api/materials`, { method: 'POST', body: input }),
  update: (id: string, patch: UpdateMaterialInput) =>
    call<{ material: Material }>(`/api/materials/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch }),
  delete: (id: string) =>
    call<Record<string, never>>(`/api/materials/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
