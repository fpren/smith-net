// desktop/portal/src/console/api/materialsClient.ts
// Mirrors tasksClient shape: typed result + thin wrapper around fetch.

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
  const res = await fetch(path, {
    method: opts.method ?? 'GET',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });
  if (res.status === 204) return { ok: true } as MaterialsResult<T>;
  if (!res.ok) {
    const errBody = await res.json().catch(() => ({ error: res.statusText }));
    return { ok: false, status: res.status, error: errBody.error ?? res.statusText, details: errBody.details, code: errBody.code };
  }
  const data = (await res.json()) as T;
  return { ok: true, ...data } as MaterialsResult<T>;
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
