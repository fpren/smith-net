// desktop/portal/src/console/api/httpCall.ts
//
// Shared fetch core for every console resource client. Centralizes the
// "401 -> silent refresh -> retry once -> (session expired)" flow so no
// per-resource client hand-rolls it.
//
// Single-flight: N concurrent 401s share ONE authClient.refresh() call --
// the first 401 kicks off the refresh, every other concurrent 401 awaits
// the same in-flight promise instead of firing its own.
//
// /api/auth/* paths are exempt from the refresh branch: authClient's own
// login/refresh/register/me calls must never trigger a refresh-of-refresh
// loop. authClient.ts does NOT go through httpCall for this reason -- it
// keeps its own postJson/getJson/patchJson fetch core.

import { authClient } from '../auth/authClient';
import { useAuthStore } from '../auth/authStore';
import { useToastStore } from '../stores/toastStore';

export type HttpCallResult<T> =
  | { ok: true; data: T }
  | { ok: false; status: number; error: string; body?: any };

const AUTH_PREFIX = '/api/auth/';

function isAuthPath(path: string): boolean {
  return path.startsWith(AUTH_PREFIX);
}

// Module-level shared promise -- this IS the single-flight mechanism.
// While non-null, any concurrent 401 awaits the same refresh instead of
// starting its own. Cleared in .finally() so the next 401 (later in time,
// once the token is stale again) starts a fresh refresh.
let refreshPromise: Promise<boolean> | null = null;

function refreshOnce(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = authClient
      .refresh()
      .then((r) => r.ok)
      .catch(() => false)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

async function parseJsonBody(res: Response): Promise<any> {
  if (res.status === 204) return null;
  return res.json().catch(() => null);
}

// Fires once per expiry event, not once per failing request: a dashboard
// load runs several fetches in parallel and each would otherwise stack its
// own toast + redirect. location.assign is a full navigation, so module
// state (and this flag) resets with the next page load — no re-arm needed.
let sessionExpiredHandled = false;

function sessionExpired(): void {
  if (sessionExpiredHandled) return;
  sessionExpiredHandled = true;
  useAuthStore.getState().clear();
  useToastStore.getState().push({
    message: 'Session expired — sign in again',
    tone: 'error',
    duration: 4000,
  });
  window.location.assign('/console/login');
}

/** Test hook: re-arm the session-expired guard between cases. */
export function resetSessionExpiredGuard(): void {
  sessionExpiredHandled = false;
}

/**
 * Fetch with credentials:'include'. On a 401 from a non-/api/auth/* path:
 * refresh once (single-flight across concurrent callers), retry the
 * original request once. If the retry still 401s, or the refresh itself
 * failed, the session is dead: clear the auth store, push an error toast,
 * redirect to /console/login, and return the failure.
 */
export async function httpCall<T>(path: string, init?: RequestInit): Promise<HttpCallResult<T>> {
  const doFetch = () => fetch(path, { credentials: 'include', ...init });

  let res = await doFetch();

  if (res.status === 401 && !isAuthPath(path)) {
    const refreshed = await refreshOnce();
    if (refreshed) {
      res = await doFetch();
    }
    if (!refreshed || res.status === 401) {
      const body = await parseJsonBody(res);
      sessionExpired();
      return {
        ok: false,
        status: res.status,
        error: (body && body.error) || 'Session expired',
        body,
      };
    }
  }

  if (res.status === 204) {
    return { ok: true, data: undefined as unknown as T };
  }

  if (!res.ok) {
    const body = await parseJsonBody(res);
    return {
      ok: false,
      status: res.status,
      error: (body && body.error) || res.statusText || 'Request failed',
      body,
    };
  }

  const data = (await res.json()) as T;
  return { ok: true, data };
}
