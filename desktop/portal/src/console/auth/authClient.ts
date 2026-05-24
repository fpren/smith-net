import type { ConsoleUser } from './authStore';

export type AuthResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string };

interface UserResponse {
  user: ConsoleUser & { permissions?: string[] };
}

interface AuthLoginResponse extends UserResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

async function postJson<T>(path: string, body: unknown): Promise<AuthResult<T>> {
  const res = await fetch(path, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    const errBody = await res.json().catch(() => ({ error: res.statusText }));
    return { ok: false, status: res.status, error: errBody.error || 'Request failed' };
  }

  const data = (await res.json()) as T;
  return { ok: true, ...data } as AuthResult<T>;
}

async function getJson<T>(path: string): Promise<AuthResult<T>> {
  const res = await fetch(path, { credentials: 'include' });
  if (!res.ok) {
    const errBody = await res.json().catch(() => ({ error: res.statusText }));
    return { ok: false, status: res.status, error: errBody.error || 'Request failed' };
  }
  const data = (await res.json()) as T;
  return { ok: true, ...data } as AuthResult<T>;
}

async function patchJson<T>(path: string, body: unknown): Promise<AuthResult<T>> {
  const res = await fetch(path, {
    method: 'PATCH',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const errBody = await res.json().catch(() => ({ error: res.statusText }));
    return { ok: false, status: res.status, error: errBody.error || 'Request failed' };
  }
  const data = (await res.json()) as T;
  return { ok: true, ...data } as AuthResult<T>;
}

export const authClient = {
  login: (email: string, password: string) =>
    postJson<AuthLoginResponse>('/api/auth/login', { email, password }),

  register: (email: string, password: string, displayName: string) =>
    postJson<AuthLoginResponse>('/api/auth/register', { email, password, displayName }),

  refresh: () =>
    postJson<{ accessToken: string; refreshToken: string; expiresIn: number }>(
      '/api/auth/refresh',
      {}
    ),

  me: () => getJson<UserResponse>('/api/auth/me'),

  updateProfile: (displayName: string) =>
    patchJson<UserResponse>('/api/auth/me', { displayName }),

  // Self-service WORK MODE switch (backend whitelist: solo | foreman only).
  updateWorkMode: (mode: 'solo' | 'foreman') =>
    patchJson<UserResponse>('/api/users/me/work-mode', { mode }),

  // Org invites: foreman-tier generates a one-time code; anyone redeems one.
  createOrgInvite: () =>
    postJson<{ code: string; expiresAt: string }>('/api/auth/org/invites', {}),
  joinOrg: (code: string) => postJson<UserResponse>('/api/auth/org/join', { code }),

  logout: () => postJson<{ success: boolean }>('/api/auth/logout', {}),
};
