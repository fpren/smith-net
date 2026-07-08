import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse, delay } from 'msw';
import { server } from '../../test/msw-server';
import { httpCall } from '../httpCall';
import { useAuthStore } from '../../auth/authStore';
import { useToastStore } from '../../stores/toastStore';

function seedAuthedUser() {
  useAuthStore.setState({
    user: {
      id: 'u-1',
      email: 'foreman@example.com',
      displayName: 'Test Foreman',
      role: 'foreman',
      emailVerified: true,
    },
  });
}

describe('httpCall', () => {
  beforeEach(() => {
    seedAuthedUser();
    useToastStore.setState({ toasts: [] });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('(a) 401 then refresh 200 then retry 200 -> ok:true, exactly one refresh call', async () => {
    let widgetCalls = 0;
    let refreshCalls = 0;

    server.use(
      http.get('/api/widgets', () => {
        widgetCalls += 1;
        if (widgetCalls === 1) {
          return HttpResponse.json({ error: 'unauthorized' }, { status: 401 });
        }
        return HttpResponse.json({ widget: { id: 'w-1' } });
      }),
      http.post('/api/auth/refresh', () => {
        refreshCalls += 1;
        return HttpResponse.json({ accessToken: 'a2', refreshToken: 'r2', expiresIn: 604800 });
      }),
    );

    const result = await httpCall<{ widget: { id: string } }>('/api/widgets');

    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.data.widget.id).toBe('w-1');
    }
    expect(refreshCalls).toBe(1);
    expect(widgetCalls).toBe(2);
  });

  it('(b) 401 + refresh 401 -> failure, toast pushed, redirect called', async () => {
    server.use(
      http.get('/api/widgets', () => HttpResponse.json({ error: 'unauthorized' }, { status: 401 })),
      http.post('/api/auth/refresh', () => HttpResponse.json({ error: 'invalid refresh token' }, { status: 401 })),
    );

    const assignSpy = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { ...window.location, assign: assignSpy },
      writable: true,
      configurable: true,
    });

    const result = await httpCall('/api/widgets');

    expect(result.ok).toBe(false);
    expect(useAuthStore.getState().user).toBeNull();
    expect(useToastStore.getState().toasts[0]?.message).toBe('Session expired — sign in again');
    expect(useToastStore.getState().toasts[0]?.tone).toBe('error');
    expect(assignSpy).toHaveBeenCalledWith('/console/login');
  });

  it('(c) two concurrent 401 requests -> one refresh (single-flight)', async () => {
    let refreshCalls = 0;
    let refreshed = false;

    server.use(
      http.get('/api/widgets', () => {
        if (!refreshed) {
          return HttpResponse.json({ error: 'unauthorized' }, { status: 401 });
        }
        return HttpResponse.json({ ok: true, id: 'x' });
      }),
      http.post('/api/auth/refresh', async () => {
        refreshCalls += 1;
        // Small delay so both concurrent 401s are guaranteed to observe
        // refreshed=false and both attempt to trigger a refresh before
        // this one resolves -- that's the single-flight race we're testing.
        await delay(20);
        refreshed = true;
        return HttpResponse.json({ accessToken: 'a2', refreshToken: 'r2', expiresIn: 604800 });
      }),
    );

    const [r1, r2] = await Promise.all([httpCall('/api/widgets'), httpCall('/api/widgets')]);

    expect(r1.ok).toBe(true);
    expect(r2.ok).toBe(true);
    expect(refreshCalls).toBe(1);
  });

  it('(d) /api/auth/login 401 -> no refresh attempted', async () => {
    let refreshCalls = 0;

    server.use(
      http.post('/api/auth/login', () => HttpResponse.json({ error: 'Invalid credentials' }, { status: 401 })),
      http.post('/api/auth/refresh', () => {
        refreshCalls += 1;
        return HttpResponse.json({ accessToken: 'a2', refreshToken: 'r2', expiresIn: 604800 });
      }),
    );

    const result = await httpCall('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'x@x.com', password: 'wrong' }),
    });

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.status).toBe(401);
      expect(result.error).toBe('Invalid credentials');
    }
    expect(refreshCalls).toBe(0);
  });
});
