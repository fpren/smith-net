// desktop/portal/src/console/api/clientsClient.ts
import { httpCall } from './httpCall';

export interface Client {
  id: string;
  ownerId: string;
  name: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  company: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export type ClientsResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string; details?: any; code?: string };

interface JsonInit { method?: string; body?: unknown }

async function call<T>(path: string, init: JsonInit = {}): Promise<ClientsResult<T>> {
  const r = await httpCall<T>(path, {
    method: init.method ?? 'GET',
    headers: init.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });
  if (!r.ok) {
    const e = r.body ?? {};
    return { ok: false, status: r.status, error: r.error, details: e.details, code: e.code };
  }
  return { ok: true, ...((r.data ?? {}) as T) } as ClientsResult<T>;
}

interface ListResp { clients: Client[] }
interface OneResp { client: Client; jobs: any[] }
interface MutateResp { client: Client }

export interface CreateClientInput {
  name: string; email?: string; phone?: string; address?: string; company?: string; notes?: string;
}
export interface UpdateClientInput {
  name?: string; email?: string | null; phone?: string | null;
  address?: string | null; company?: string | null; notes?: string | null;
}

export const clientsClient = {
  list: (q?: string) => call<ListResp>(`/api/clients${q ? `?q=${encodeURIComponent(q)}` : ''}`),
  getById: (id: string) => call<OneResp>(`/api/clients/${encodeURIComponent(id)}`),
  create: (input: CreateClientInput) => call<MutateResp>('/api/clients', { method: 'POST', body: input }),
  update: (id: string, patch: UpdateClientInput) =>
    call<MutateResp>(`/api/clients/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch }),
  remove: (id: string) => call<{}>(`/api/clients/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
