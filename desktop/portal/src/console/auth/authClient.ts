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

  logout: () => postJson<{ success: boolean }>('/api/auth/logout', {}),
};
