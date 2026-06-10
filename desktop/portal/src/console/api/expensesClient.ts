// desktop/portal/src/console/api/expensesClient.ts
// Mirrors materialsClient shape: typed result + thin wrapper around fetch.
import { mutate } from '../offline/outbox';
import { useAuthStore } from '../auth/authStore';

export interface Expense {
  id: string;
  jobId: string;
  category: string;
  description: string;
  amount: number;
  vendor: string | null;
  notes: string | null;
  expenseDate: string | null;
  createdAt: string;
  updatedAt: string;
}

export type ExpensesResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string; details?: any; code?: string };

/** W6: an outbox-routed mutation result -- normal, or queued offline for replay. */
export type ExpensesOutboxResult<T> = ExpensesResult<T> | { ok: true; queued: true };

async function outboxMutate<T>(path: string, method: string, body: unknown, label: string): Promise<ExpensesOutboxResult<T>> {
  const profileId = useAuthStore.getState().user?.id ?? 'anon';
  const r = await mutate<T>({ profileId, method, path, body, label });
  if (r.queued) return { ok: true, queued: true };
  if (r.ok) return { ok: true, ...((r.data ?? {}) as T) } as ExpensesResult<T>;
  return { ok: false, status: r.status ?? 0, error: r.error ?? 'Request failed' };
}

async function call<T>(path: string, opts: { method?: string; body?: any } = {}): Promise<ExpensesResult<T>> {
  const res = await fetch(path, {
    method: opts.method ?? 'GET',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });
  if (res.status === 204) return { ok: true } as ExpensesResult<T>;
  if (!res.ok) {
    const errBody = await res.json().catch(() => ({ error: res.statusText }));
    return { ok: false, status: res.status, error: errBody.error ?? res.statusText, details: errBody.details, code: errBody.code };
  }
  const data = (await res.json()) as T;
  return { ok: true, ...data } as ExpensesResult<T>;
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

export interface UpdateExpenseInput {
  category?: string;
  description?: string;
  amount?: number;
  vendor?: string | null;
  notes?: string | null;
  expenseDate?: string | null;
}

export const expensesClient = {
  listForJob: (jobId: string) =>
    call<{ expenses: Expense[] }>(`/api/jobs/${encodeURIComponent(jobId)}/expenses`),
  create: (input: CreateExpenseInput) =>
    outboxMutate<{ expense: Expense }>(`/api/expenses`, 'POST', input, 'expense:create'),
  update: (id: string, patch: UpdateExpenseInput) =>
    call<{ expense: Expense }>(`/api/expenses/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch }),
  delete: (id: string) =>
    call<Record<string, never>>(`/api/expenses/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
