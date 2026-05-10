# Operator Console — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the chassis — backend cookie auth, frontend Tailwind + test stack, console auth module, `ConsoleShell`, and a placeholder `/console` route that shows the logged-in user + role.

**Architecture:** Add httpOnly cookies to existing JWT auth (cookies set in *addition* to JSON body, so Android keeps working). On the frontend, extend `desktop/portal/` with a new `console/` directory tree. Add Tailwind + Vitest + RTL + MSW. Build the auth module bottom-up (store → client → forms → shell → routes). Tier gate is **role-based** for now (`FOREMAN | ENTERPRISE | ADMIN`) — the marketing "Advanced+" tier maps to those roles until billing/subscription data exists.

**Tech Stack:**
- Backend: Express, `jsonwebtoken`, `cookie-parser` (new), Jest tests
- Frontend: React 18 + Vite 5 + TypeScript 5.3, Tailwind CSS (new), Vitest + @testing-library/react + MSW (new), Zustand 5, react-router-dom 6

**Spec:** `docs/superpowers/specs/2026-05-10-operator-console-design.md`

**Scope boundaries (what this plan does NOT do):**
- No `/api/jobs`, `/api/crew`, `/api/clients`, `/api/chat` endpoints — that's Plan 2
- No WebSocket dispatcher — that's Plan 3
- No JobBoard, Map, Crew, ClientLookup, Chat routes — Plans 3-5
- No DNS / subdomain setup — Plan 6
- No removing Supabase code — Plan 6 follow-up

---

## File Structure

**Backend (modify + create):**
- `backend/package.json` — add `cookie-parser`, `@types/cookie-parser`
- `backend/src/server.ts` — register `cookieParser()` middleware
- `backend/src/auth.ts` — extend `authenticateToken` to read token from `smithnet_access` cookie when `Authorization` header is absent; add `setAuthCookies(res, tokens)` + `clearAuthCookies(res)` helpers
- `backend/src/authRoutes.ts` — call `setAuthCookies()` on register/login/refresh; call `clearAuthCookies()` on logout
- `backend/src/__tests__/auth-cookie.test.ts` — new integration test file

**Frontend (modify):**
- `desktop/portal/package.json` — add `tailwindcss`, `postcss`, `autoprefixer`, `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`, `jsdom`, `msw`, `zod`
- `desktop/portal/vite.config.ts` — add Vitest config block; add `@console/*` alias
- `desktop/portal/tsconfig.json` — add `@console/*` path
- `desktop/portal/tailwind.config.js` (new) — content paths + theme tokens from `consoleTheme`
- `desktop/portal/postcss.config.js` (new)
- `desktop/portal/src/console/index.css` (new) — Tailwind directives + console base styles
- `desktop/portal/src/main.tsx` — import the new index.css
- `desktop/portal/src/App.tsx` — add `/console/*` routes

**Frontend (create — new console tree):**
- `desktop/portal/src/console/theme/consoleTheme.ts` — design tokens
- `desktop/portal/src/console/components/ui/Button.tsx`
- `desktop/portal/src/console/components/ui/Card.tsx`
- `desktop/portal/src/console/components/ui/Input.tsx`
- `desktop/portal/src/console/components/ui/Badge.tsx`
- `desktop/portal/src/console/components/ui/Modal.tsx`
- `desktop/portal/src/console/components/ui/__tests__/Button.test.tsx`
- `desktop/portal/src/console/components/ui/__tests__/Card.test.tsx`
- `desktop/portal/src/console/components/ui/__tests__/Input.test.tsx`
- `desktop/portal/src/console/components/ui/__tests__/Badge.test.tsx`
- `desktop/portal/src/console/components/ui/__tests__/Modal.test.tsx`
- `desktop/portal/src/console/auth/authStore.ts` — Zustand store
- `desktop/portal/src/console/auth/authClient.ts` — fetch wrapper for `/api/auth/*`
- `desktop/portal/src/console/auth/LoginForm.tsx`
- `desktop/portal/src/console/auth/RegisterForm.tsx`
- `desktop/portal/src/console/auth/RequireAuth.tsx` — route guard
- `desktop/portal/src/console/auth/__tests__/authStore.test.ts`
- `desktop/portal/src/console/auth/__tests__/authClient.test.ts`
- `desktop/portal/src/console/auth/__tests__/LoginForm.test.tsx`
- `desktop/portal/src/console/auth/__tests__/RegisterForm.test.tsx`
- `desktop/portal/src/console/auth/__tests__/RequireAuth.test.tsx`
- `desktop/portal/src/console/ConsoleShell.tsx`
- `desktop/portal/src/console/__tests__/ConsoleShell.test.tsx`
- `desktop/portal/src/console/routes/PlaceholderConsoleRoute.tsx`
- `desktop/portal/src/console/routes/__tests__/PlaceholderConsoleRoute.test.tsx`
- `desktop/portal/src/console/test/setup.ts` — Vitest setup
- `desktop/portal/src/console/test/msw-handlers.ts` — MSW handlers for `/api/auth/*`
- `desktop/portal/src/console/test/msw-server.ts` — MSW Node server bootstrap

---

## Task 1: Backend — add `cookie-parser` dependency

**Files:**
- Modify: `backend/package.json`

- [ ] **Step 1: Install cookie-parser + types**

```bash
cd backend && npm install cookie-parser && npm install -D @types/cookie-parser
```

- [ ] **Step 2: Verify installed**

```bash
cd backend && grep -E '"cookie-parser"|"@types/cookie-parser"' package.json
```

Expected: both lines present.

- [ ] **Step 3: Commit**

```bash
git add backend/package.json backend/package-lock.json
git commit -m "deps(backend): add cookie-parser for console httpOnly cookie auth"
```

---

## Task 2: Backend — register cookie-parser middleware in server

**Files:**
- Modify: `backend/src/server.ts` (top imports + middleware chain near `app.use(express.json...)`)

- [ ] **Step 1: Read current server.ts imports + middleware**

```bash
grep -n "import\|app.use" backend/src/server.ts | head -30
```

Note where `express.json()` is registered — cookie-parser goes near it.

- [ ] **Step 2: Add import and middleware**

Add to imports (top of file):

```ts
import cookieParser from 'cookie-parser';
```

Add to middleware chain (right after `app.use(express.json(...))`):

```ts
app.use(cookieParser());
```

- [ ] **Step 3: Boot the server and confirm no startup errors**

```bash
cd backend && npm run dev
```

Expected: server starts, no errors mentioning cookie-parser. Then Ctrl+C.

- [ ] **Step 4: Commit**

```bash
git add backend/src/server.ts
git commit -m "feat(backend): register cookie-parser middleware"
```

---

## Task 3: Backend — extend `authenticateToken` to accept token from cookie

**Files:**
- Modify: `backend/src/auth.ts:567-597` (the `authenticateToken` function and `AuthenticatedRequest` interface)
- Test: `backend/src/__tests__/auth-cookie.test.ts` (new)

- [ ] **Step 1: Write the failing test**

Create `backend/src/__tests__/auth-cookie.test.ts`:

```ts
import request from 'supertest';
import { app } from '../server';
import { userStore, generateTokens, UserRole } from '../auth';

describe('Cookie-based authentication', () => {
  let accessToken: string;

  beforeAll(async () => {
    const user = await userStore.createUser(
      'cookie-test@example.com',
      'password123',
      'Cookie Tester',
      UserRole.FOREMAN
    );
    accessToken = generateTokens(user).accessToken;
  });

  it('accepts token from smithnet_access cookie when Authorization header is absent', async () => {
    const res = await request(app)
      .get('/api/auth/me')
      .set('Cookie', [`smithnet_access=${accessToken}`]);
    expect(res.status).toBe(200);
    expect(res.body.user.email).toBe('cookie-test@example.com');
  });

  it('still accepts Authorization Bearer header when cookie is absent', async () => {
    const res = await request(app)
      .get('/api/auth/me')
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
  });

  it('returns 401 when neither cookie nor header is present', async () => {
    const res = await request(app).get('/api/auth/me');
    expect(res.status).toBe(401);
  });
});
```

- [ ] **Step 2: Verify test imports work — server.ts must export `app`**

```bash
grep -n "export.*\bapp\b" backend/src/server.ts
```

If `app` is not exported, modify `backend/src/server.ts` to add `export` to `const app = express()` (or wherever app is created). If already exported, skip.

- [ ] **Step 3: Run the test — should fail because cookie is not read yet**

```bash
cd backend && npx jest auth-cookie -t "accepts token from smithnet_access cookie"
```

Expected: FAIL on first test (cookie case returns 401).

- [ ] **Step 4: Modify `authenticateToken` to read from cookie**

In `backend/src/auth.ts`, replace the `authenticateToken` function (currently lines ~576-597):

```ts
export function authenticateToken(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  const authHeader = req.headers.authorization;
  const headerToken = authHeader?.startsWith('Bearer ') ? authHeader.slice(7) : null;
  // Browser clients use httpOnly cookie; Android uses Bearer header. Either is fine.
  const cookieToken = (req as any).cookies?.smithnet_access || null;
  const token = headerToken || cookieToken;

  if (!token) {
    return res.status(401).json({ error: 'No token provided' });
  }

  const payload = verifyToken(token);
  if (!payload || payload.type !== 'access') {
    return res.status(401).json({ error: 'Invalid or expired token' });
  }

  const user = userStore.getUserById(payload.userId);
  if (!user || !user.isActive) {
    return res.status(401).json({ error: 'User not found or inactive' });
  }

  req.user = toPublicUser(user);
  req.token = token;
  next();
}
```

Also update `optionalAuth` (just below) similarly:

```ts
export function optionalAuth(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  const authHeader = req.headers.authorization;
  const headerToken = authHeader?.startsWith('Bearer ') ? authHeader.slice(7) : null;
  const cookieToken = (req as any).cookies?.smithnet_access || null;
  const token = headerToken || cookieToken;

  if (token) {
    const payload = verifyToken(token);
    if (payload && payload.type === 'access') {
      const user = userStore.getUserById(payload.userId);
      if (user && user.isActive) {
        req.user = toPublicUser(user);
        req.token = token;
      }
    }
  }

  next();
}
```

- [ ] **Step 5: Run all three test cases**

```bash
cd backend && npx jest auth-cookie
```

Expected: all 3 PASS.

- [ ] **Step 6: Run the full auth test suite to confirm no regressions**

```bash
cd backend && npx jest auth
```

Expected: existing `auth-middleware`, `api-auth-integration`, `email-verification`, `password-lockout` tests all still pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/auth.ts backend/src/server.ts backend/src/__tests__/auth-cookie.test.ts
git commit -m "feat(auth): authenticateToken reads token from smithnet_access cookie"
```

---

## Task 4: Backend — add `setAuthCookies` + `clearAuthCookies` helpers

**Files:**
- Modify: `backend/src/auth.ts` (add new exports near the token management section)
- Test: `backend/src/__tests__/auth-cookie.test.ts` (extend)

- [ ] **Step 1: Write the failing test**

Append to `backend/src/__tests__/auth-cookie.test.ts`:

```ts
describe('setAuthCookies / clearAuthCookies', () => {
  it('login response sets httpOnly access + refresh cookies', async () => {
    await userStore.createUser('login-cookie@example.com', 'password123', 'X', UserRole.FOREMAN);
    const res = await request(app)
      .post('/api/auth/login')
      .send({ email: 'login-cookie@example.com', password: 'password123' });
    expect(res.status).toBe(200);
    const cookies = res.headers['set-cookie'] || [];
    const accessCookie = cookies.find((c: string) => c.startsWith('smithnet_access='));
    const refreshCookie = cookies.find((c: string) => c.startsWith('smithnet_refresh='));
    expect(accessCookie).toBeDefined();
    expect(refreshCookie).toBeDefined();
    expect(accessCookie).toMatch(/HttpOnly/);
    expect(accessCookie).toMatch(/SameSite=Strict/);
    expect(refreshCookie).toMatch(/HttpOnly/);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd backend && npx jest auth-cookie -t "login response sets httpOnly"
```

Expected: FAIL — no `set-cookie` header on login response.

- [ ] **Step 3: Add the helpers**

In `backend/src/auth.ts`, add after the `refreshAccessToken` function (around line 536):

```ts
// ════════════════════════════════════════════════════════════════════
// COOKIE HELPERS (for browser clients — console)
// ════════════════════════════════════════════════════════════════════

const ACCESS_COOKIE_NAME = 'smithnet_access';
const REFRESH_COOKIE_NAME = 'smithnet_refresh';
const ACCESS_COOKIE_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;   // 7d
const REFRESH_COOKIE_MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000; // 30d

export function setAuthCookies(res: Response, tokens: AuthTokens): void {
  const baseOpts = {
    httpOnly: true,
    secure: IS_PRODUCTION,
    sameSite: 'strict' as const,
    path: '/api',
  };

  res.cookie(ACCESS_COOKIE_NAME, tokens.accessToken, {
    ...baseOpts,
    maxAge: ACCESS_COOKIE_MAX_AGE_MS,
  });
  res.cookie(REFRESH_COOKIE_NAME, tokens.refreshToken, {
    ...baseOpts,
    path: '/api/auth',
    maxAge: REFRESH_COOKIE_MAX_AGE_MS,
  });
}

export function clearAuthCookies(res: Response): void {
  res.clearCookie(ACCESS_COOKIE_NAME, { path: '/api' });
  res.clearCookie(REFRESH_COOKIE_NAME, { path: '/api/auth' });
}
```

- [ ] **Step 4: Wire helpers into login route**

In `backend/src/authRoutes.ts`, find the imports block (lines 9-21) and add `setAuthCookies` + `clearAuthCookies`:

```ts
import {
  userStore,
  generateTokens,
  refreshAccessToken,
  toPublicUser,
  authenticateToken,
  requirePermission,
  validatePassword,
  AuthenticatedRequest,
  UserRole,
  Permission,
  EMAIL_RESEND_COOLDOWN_MS,
  setAuthCookies,
  clearAuthCookies,
} from './auth';
```

In the login route (around line 159), call `setAuthCookies` right before `res.json(...)`:

```ts
    const tokens = generateTokens(result.user);

    // Audit log
    auditLog.log(AuditAction.USER_LOGIN, result.user.id, { email });

    setAuthCookies(res, tokens);

    res.json({
      user: toPublicUser(result.user),
      ...tokens,
    });
```

- [ ] **Step 5: Run the test**

```bash
cd backend && npx jest auth-cookie -t "login response sets httpOnly"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/auth.ts backend/src/authRoutes.ts backend/src/__tests__/auth-cookie.test.ts
git commit -m "feat(auth): setAuthCookies helper + login sets httpOnly cookies"
```

---

## Task 5: Backend — register + refresh also set cookies; logout clears them

**Files:**
- Modify: `backend/src/authRoutes.ts` (register, refresh, logout handlers)
- Test: `backend/src/__tests__/auth-cookie.test.ts` (extend)

- [ ] **Step 1: Write the failing tests**

Append to `auth-cookie.test.ts`:

```ts
it('register response sets httpOnly cookies', async () => {
  const res = await request(app)
    .post('/api/auth/register')
    .send({ email: 'reg-cookie@example.com', password: 'password123', displayName: 'R' });
  expect(res.status).toBe(201);
  const cookies = res.headers['set-cookie'] || [];
  expect(cookies.find((c: string) => c.startsWith('smithnet_access='))).toBeDefined();
  expect(cookies.find((c: string) => c.startsWith('smithnet_refresh='))).toBeDefined();
});

it('refresh response sets fresh httpOnly cookies', async () => {
  const user = await userStore.createUser('refresh-c@example.com', 'password123', 'R', UserRole.FOREMAN);
  const { refreshToken } = generateTokens(user);
  const res = await request(app)
    .post('/api/auth/refresh')
    .send({ refreshToken });
  expect(res.status).toBe(200);
  const cookies = res.headers['set-cookie'] || [];
  expect(cookies.find((c: string) => c.startsWith('smithnet_access='))).toBeDefined();
});

it('logout clears auth cookies', async () => {
  const user = await userStore.createUser('logout-c@example.com', 'password123', 'L', UserRole.FOREMAN);
  const { accessToken, refreshToken } = generateTokens(user);
  const res = await request(app)
    .post('/api/auth/logout')
    .set('Cookie', [`smithnet_access=${accessToken}`])
    .send({ refreshToken });
  expect(res.status).toBe(200);
  const cookies = res.headers['set-cookie'] || [];
  // clearCookie emits Set-Cookie with Expires in the past
  const cleared = cookies.find((c: string) => c.startsWith('smithnet_access=') && /Expires=/.test(c));
  expect(cleared).toBeDefined();
});
```

- [ ] **Step 2: Run tests — expect 3 failures**

```bash
cd backend && npx jest auth-cookie
```

Expected: 3 new tests FAIL.

- [ ] **Step 3: Wire `setAuthCookies` into register route**

In `backend/src/authRoutes.ts`, find the register handler (line ~92). Right before `res.status(201).json(...)`:

```ts
    setAuthCookies(res, tokens);

    res.status(201).json({
      user: toPublicUser(user),
      ...tokens,
      requiresEmailVerification: true,
    });
```

- [ ] **Step 4: Wire `setAuthCookies` into refresh route**

In the refresh handler (line ~178), right before `res.json(tokens)`:

```ts
    setAuthCookies(res, tokens);

    res.json(tokens);
```

- [ ] **Step 5: Wire `clearAuthCookies` into logout route**

In the logout handler (line ~306), right before `res.json({ success: true })`:

```ts
  clearAuthCookies(res);

  res.json({ success: true });
```

- [ ] **Step 6: Run tests**

```bash
cd backend && npx jest auth-cookie
```

Expected: all 6 tests in the file PASS.

- [ ] **Step 7: Run full auth suite for regressions**

```bash
cd backend && npx jest auth
```

Expected: all auth tests still pass.

- [ ] **Step 8: Commit**

```bash
git add backend/src/authRoutes.ts backend/src/__tests__/auth-cookie.test.ts
git commit -m "feat(auth): register/refresh set cookies; logout clears them"
```

---

## Task 6: Frontend — install + configure Tailwind

**Files:**
- Modify: `desktop/portal/package.json`
- Create: `desktop/portal/tailwind.config.js`
- Create: `desktop/portal/postcss.config.js`
- Create: `desktop/portal/src/console/index.css`
- Modify: `desktop/portal/src/main.tsx`

- [ ] **Step 1: Install Tailwind deps**

```bash
cd desktop/portal && npm install -D tailwindcss@^3.4.0 postcss@^8.4.0 autoprefixer@^10.4.0
```

- [ ] **Step 2: Create `desktop/portal/tailwind.config.js`**

```js
/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        'console-bg': '#F4F2EE',
        'console-surface': '#FAFAF8',
        'console-border': '#E8E4DE',
        'console-text': '#2A2520',
        'console-text-muted': '#5C5347',
        'console-accent': '#9A6F2E',
        'console-ok': '#5A8C76',
        'console-warn': '#8C5A2E',
        'console-danger': '#8C3A3A',
      },
      fontFamily: {
        mono: ['"JetBrains Mono"', '"SF Mono"', 'Consolas', 'monospace'],
      },
    },
  },
  plugins: [],
};
```

- [ ] **Step 3: Create `desktop/portal/postcss.config.js`**

```js
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
```

- [ ] **Step 4: Create `desktop/portal/src/console/index.css`**

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  html, body {
    background-color: theme('colors.console-bg');
    color: theme('colors.console-text');
    font-family: theme('fontFamily.mono');
  }
}
```

- [ ] **Step 5: Import CSS in main.tsx**

Modify `desktop/portal/src/main.tsx` — add the CSS import after the existing imports:

```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import './console/index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
    <App />
    </BrowserRouter>
  </React.StrictMode>,
)
```

- [ ] **Step 6: Verify dev server runs and Tailwind compiles**

```bash
cd desktop/portal && npm run dev
```

Expected: server starts on http://localhost:5173, no Tailwind/PostCSS errors in console. Visit the URL in a browser — the body should now use the warm off-white background. Then Ctrl+C.

- [ ] **Step 7: Commit**

```bash
git add desktop/portal/package.json desktop/portal/package-lock.json desktop/portal/tailwind.config.js desktop/portal/postcss.config.js desktop/portal/src/console/index.css desktop/portal/src/main.tsx
git commit -m "feat(portal): install Tailwind, add console design tokens + base styles"
```

---

## Task 7: Frontend — install Vitest + RTL + MSW + jsdom

**Files:**
- Modify: `desktop/portal/package.json`
- Modify: `desktop/portal/vite.config.ts`
- Create: `desktop/portal/src/console/test/setup.ts`
- Create: `desktop/portal/src/console/test/msw-handlers.ts`
- Create: `desktop/portal/src/console/test/msw-server.ts`

- [ ] **Step 1: Install test deps**

```bash
cd desktop/portal && npm install -D vitest@^1.6.0 @testing-library/react@^15.0.0 @testing-library/jest-dom@^6.4.0 @testing-library/user-event@^14.5.0 jsdom@^24.0.0 msw@^2.3.0 zod@^3.23.0
```

- [ ] **Step 2: Add `test` script and Vitest config to `vite.config.ts`**

Replace `desktop/portal/vite.config.ts` with:

```ts
/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src/dashboard'),
      '@console': path.resolve(__dirname, './src/console'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:3030',
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/console/test/setup.ts',
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
```

- [ ] **Step 3: Add `@console/*` path to `tsconfig.json`**

Modify `desktop/portal/tsconfig.json` `paths`:

```json
"paths": {
  "@/*": ["src/dashboard/*"],
  "@console/*": ["src/console/*"]
}
```

- [ ] **Step 4: Add test scripts to `package.json`**

Modify the `scripts` block of `desktop/portal/package.json`:

```json
"scripts": {
  "dev": "vite",
  "build": "vite build",
  "preview": "vite preview",
  "test": "vitest",
  "test:run": "vitest run"
}
```

- [ ] **Step 5: Create `desktop/portal/src/console/test/setup.ts`**

```ts
import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './msw-server';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
```

- [ ] **Step 6: Create `desktop/portal/src/console/test/msw-handlers.ts`**

```ts
import { http, HttpResponse } from 'msw';

export const handlers = [
  http.post('/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };
    if (body.email === 'foreman@example.com' && body.password === 'password123') {
      return HttpResponse.json(
        {
          user: {
            id: 'user-1',
            email: 'foreman@example.com',
            displayName: 'Test Foreman',
            role: 'foreman',
            permissions: [],
            emailVerified: true,
          },
          accessToken: 'fake-access',
          refreshToken: 'fake-refresh',
          expiresIn: 604800,
        },
        {
          headers: {
            'set-cookie': 'smithnet_access=fake-access; HttpOnly; SameSite=Strict; Path=/api',
          },
        }
      );
    }
    return HttpResponse.json({ error: 'Invalid credentials' }, { status: 401 });
  }),

  http.get('/api/auth/me', () => {
    return HttpResponse.json({
      user: {
        id: 'user-1',
        email: 'foreman@example.com',
        displayName: 'Test Foreman',
        role: 'foreman',
        permissions: [],
        emailVerified: true,
      },
    });
  }),

  http.post('/api/auth/refresh', () => {
    return HttpResponse.json({
      accessToken: 'fake-access-2',
      refreshToken: 'fake-refresh-2',
      expiresIn: 604800,
    });
  }),

  http.post('/api/auth/logout', () => {
    return HttpResponse.json({ success: true });
  }),

  http.post('/api/auth/register', async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string; displayName: string };
    return HttpResponse.json(
      {
        user: {
          id: 'user-new',
          email: body.email,
          displayName: body.displayName,
          role: 'solo',
          permissions: [],
          emailVerified: false,
        },
        accessToken: 'fake-access',
        refreshToken: 'fake-refresh',
        expiresIn: 604800,
        requiresEmailVerification: true,
      },
      { status: 201 }
    );
  }),
];
```

- [ ] **Step 7: Create `desktop/portal/src/console/test/msw-server.ts`**

```ts
import { setupServer } from 'msw/node';
import { handlers } from './msw-handlers';

export const server = setupServer(...handlers);
```

- [ ] **Step 8: Smoke test that Vitest runs**

Create temp file `desktop/portal/src/console/test/smoke.test.ts`:

```ts
import { describe, it, expect } from 'vitest';

describe('vitest setup', () => {
  it('runs a trivial test', () => {
    expect(1 + 1).toBe(2);
  });
});
```

Run:

```bash
cd desktop/portal && npm run test:run -- smoke
```

Expected: 1 test PASSES. Then delete the smoke file:

```bash
rm desktop/portal/src/console/test/smoke.test.ts
```

- [ ] **Step 9: Commit**

```bash
git add desktop/portal/package.json desktop/portal/package-lock.json desktop/portal/vite.config.ts desktop/portal/tsconfig.json desktop/portal/src/console/test/
git commit -m "feat(portal): install Vitest + RTL + MSW + zod; wire @console alias"
```

---

## Task 8: Frontend — `consoleTheme.ts` tokens

**Files:**
- Create: `desktop/portal/src/console/theme/consoleTheme.ts`

This is a pure constants file. No test needed.

- [ ] **Step 1: Create the theme file**

```ts
/**
 * Console design tokens — keep in sync with tailwind.config.js colors.
 * Components reference these tokens via Tailwind classes (e.g. `bg-console-surface`)
 * OR via this object when they need the raw value (rare — usually for inline styles).
 */

export const consoleTheme = {
  colors: {
    bg: '#F4F2EE',
    surface: '#FAFAF8',
    border: '#E8E4DE',
    text: '#2A2520',
    textMuted: '#5C5347',
    accent: '#9A6F2E',
    ok: '#5A8C76',
    warn: '#8C5A2E',
    danger: '#8C3A3A',
  },
  glyphs: {
    online: '((+))',
    offline: '(( ))',
    error: '[ERR]',
    ok: '[OK]',
    warn: '[!]',
    arrow: '->',
    bullet: '*',
  },
  spacing: {
    xs: '0.25rem',
    sm: '0.5rem',
    md: '1rem',
    lg: '1.5rem',
    xl: '2rem',
  },
} as const;

export type ConsoleTheme = typeof consoleTheme;
```

- [ ] **Step 2: Commit**

```bash
git add desktop/portal/src/console/theme/consoleTheme.ts
git commit -m "feat(console): add consoleTheme design tokens"
```

---

## Task 9: Frontend — `ui/Button.tsx`

**Files:**
- Create: `desktop/portal/src/console/components/ui/Button.tsx`
- Test: `desktop/portal/src/console/components/ui/__tests__/Button.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { Button } from '../Button';

describe('Button', () => {
  it('renders children', () => {
    render(<Button>Click me</Button>);
    expect(screen.getByRole('button', { name: 'Click me' })).toBeInTheDocument();
  });

  it('fires onClick when clicked', async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Click</Button>);
    await userEvent.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('is disabled when disabled prop is true', () => {
    render(<Button disabled>X</Button>);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('applies primary variant styles by default', () => {
    render(<Button>X</Button>);
    expect(screen.getByRole('button').className).toMatch(/bg-console-accent/);
  });

  it('applies secondary variant when variant=secondary', () => {
    render(<Button variant="secondary">X</Button>);
    expect(screen.getByRole('button').className).toMatch(/bg-console-surface/);
  });
});
```

- [ ] **Step 2: Run — expect import error**

```bash
cd desktop/portal && npm run test:run -- Button
```

Expected: FAIL — `Button` not exported.

- [ ] **Step 3: Implement `Button.tsx`**

```tsx
import { ButtonHTMLAttributes, forwardRef } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

type Variant = 'primary' | 'secondary' | 'danger';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
}

const VARIANT_CLASSES: Record<Variant, string> = {
  primary: 'bg-console-accent text-white hover:opacity-90',
  secondary: 'bg-console-surface text-console-text border border-console-border hover:bg-console-bg',
  danger: 'bg-console-danger text-white hover:opacity-90',
};

export const Button = forwardRef<HTMLButtonElement, Props>(function Button(
  { variant = 'primary', className, children, ...rest },
  ref
) {
  return (
    <button
      ref={ref}
      className={twMerge(
        clsx(
          'px-4 py-2 font-mono text-sm transition-opacity disabled:opacity-50 disabled:cursor-not-allowed',
          VARIANT_CLASSES[variant],
          className
        )
      )}
      {...rest}
    >
      {children}
    </button>
  );
});
```

- [ ] **Step 4: Run tests**

```bash
cd desktop/portal && npm run test:run -- Button
```

Expected: all 5 PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/components/ui/Button.tsx desktop/portal/src/console/components/ui/__tests__/Button.test.tsx
git commit -m "feat(console): Button UI primitive with primary/secondary/danger variants"
```

---

## Task 10: Frontend — `ui/Card.tsx`

**Files:**
- Create: `desktop/portal/src/console/components/ui/Card.tsx`
- Test: `desktop/portal/src/console/components/ui/__tests__/Card.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Card } from '../Card';

describe('Card', () => {
  it('renders children', () => {
    render(<Card><span>content</span></Card>);
    expect(screen.getByText('content')).toBeInTheDocument();
  });

  it('renders title when provided', () => {
    render(<Card title="My Card">x</Card>);
    expect(screen.getByText('My Card')).toBeInTheDocument();
  });

  it('applies surface + border classes', () => {
    const { container } = render(<Card>x</Card>);
    expect(container.firstChild).toHaveClass('bg-console-surface');
    expect(container.firstChild).toHaveClass('border');
  });
});
```

- [ ] **Step 2: Run — expect failure**

```bash
cd desktop/portal && npm run test:run -- Card
```

Expected: FAIL.

- [ ] **Step 3: Implement `Card.tsx`**

```tsx
import { HTMLAttributes, ReactNode } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

interface Props extends HTMLAttributes<HTMLDivElement> {
  title?: ReactNode;
}

export function Card({ title, className, children, ...rest }: Props) {
  return (
    <div
      className={twMerge(clsx('bg-console-surface border border-console-border p-4', className))}
      {...rest}
    >
      {title !== undefined && (
        <div className="font-mono text-xs uppercase tracking-wide text-console-text-muted mb-2">
          {title}
        </div>
      )}
      {children}
    </div>
  );
}
```

- [ ] **Step 4: Run**

```bash
cd desktop/portal && npm run test:run -- Card
```

Expected: all 3 PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/components/ui/Card.tsx desktop/portal/src/console/components/ui/__tests__/Card.test.tsx
git commit -m "feat(console): Card UI primitive with optional title"
```

---

## Task 11: Frontend — `ui/Input.tsx`

**Files:**
- Create: `desktop/portal/src/console/components/ui/Input.tsx`
- Test: `desktop/portal/src/console/components/ui/__tests__/Input.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { Input } from '../Input';

describe('Input', () => {
  it('renders with label', () => {
    render(<Input label="Email" />);
    expect(screen.getByText('Email')).toBeInTheDocument();
    expect(screen.getByRole('textbox')).toBeInTheDocument();
  });

  it('forwards value + onChange', async () => {
    const onChange = vi.fn();
    render(<Input label="Email" value="" onChange={onChange} />);
    await userEvent.type(screen.getByRole('textbox'), 'a');
    expect(onChange).toHaveBeenCalled();
  });

  it('shows error message when error prop is set', () => {
    render(<Input label="Email" error="bad email" />);
    expect(screen.getByText('bad email')).toBeInTheDocument();
  });

  it('uses type=password when type is password', () => {
    render(<Input label="Pwd" type="password" />);
    expect(screen.getByLabelText('Pwd')).toHaveAttribute('type', 'password');
  });
});
```

- [ ] **Step 2: Run — expect failure**

```bash
cd desktop/portal && npm run test:run -- Input
```

Expected: FAIL.

- [ ] **Step 3: Implement `Input.tsx`**

```tsx
import { InputHTMLAttributes, forwardRef, useId } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}

export const Input = forwardRef<HTMLInputElement, Props>(function Input(
  { label, error, className, id, ...rest },
  ref
) {
  const autoId = useId();
  const inputId = id ?? autoId;

  return (
    <label htmlFor={inputId} className="flex flex-col gap-1 font-mono text-sm">
      <span className="text-console-text-muted text-xs uppercase tracking-wide">{label}</span>
      <input
        ref={ref}
        id={inputId}
        className={twMerge(
          clsx(
            'bg-console-bg border border-console-border px-3 py-2 text-console-text',
            'focus:outline-none focus:border-console-accent',
            error && 'border-console-danger',
            className
          )
        )}
        {...rest}
      />
      {error && <span className="text-console-danger text-xs">{error}</span>}
    </label>
  );
});
```

- [ ] **Step 4: Run**

```bash
cd desktop/portal && npm run test:run -- Input
```

Expected: all 4 PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/components/ui/Input.tsx desktop/portal/src/console/components/ui/__tests__/Input.test.tsx
git commit -m "feat(console): Input UI primitive with label + error states"
```

---

## Task 12: Frontend — `ui/Badge.tsx`

**Files:**
- Create: `desktop/portal/src/console/components/ui/Badge.tsx`
- Test: `desktop/portal/src/console/components/ui/__tests__/Badge.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Badge } from '../Badge';

describe('Badge', () => {
  it('renders children', () => {
    render(<Badge>FOREMAN</Badge>);
    expect(screen.getByText('FOREMAN')).toBeInTheDocument();
  });

  it('applies tone-based styling — default tone', () => {
    const { container } = render(<Badge>x</Badge>);
    expect(container.firstChild).toHaveClass('bg-console-surface');
  });

  it('applies ok tone when tone=ok', () => {
    const { container } = render(<Badge tone="ok">x</Badge>);
    expect((container.firstChild as HTMLElement).className).toMatch(/text-console-ok/);
  });

  it('applies danger tone when tone=danger', () => {
    const { container } = render(<Badge tone="danger">x</Badge>);
    expect((container.firstChild as HTMLElement).className).toMatch(/text-console-danger/);
  });
});
```

- [ ] **Step 2: Run — expect failure**

```bash
cd desktop/portal && npm run test:run -- Badge
```

Expected: FAIL.

- [ ] **Step 3: Implement `Badge.tsx`**

```tsx
import { HTMLAttributes } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

type Tone = 'default' | 'ok' | 'warn' | 'danger';

interface Props extends HTMLAttributes<HTMLSpanElement> {
  tone?: Tone;
}

const TONE_CLASSES: Record<Tone, string> = {
  default: 'bg-console-surface text-console-text border-console-border',
  ok: 'bg-console-surface text-console-ok border-console-ok',
  warn: 'bg-console-surface text-console-warn border-console-warn',
  danger: 'bg-console-surface text-console-danger border-console-danger',
};

export function Badge({ tone = 'default', className, children, ...rest }: Props) {
  return (
    <span
      className={twMerge(
        clsx(
          'inline-block border px-2 py-0.5 text-xs font-mono uppercase tracking-wide',
          TONE_CLASSES[tone],
          className
        )
      )}
      {...rest}
    >
      {children}
    </span>
  );
}
```

- [ ] **Step 4: Run**

```bash
cd desktop/portal && npm run test:run -- Badge
```

Expected: all 4 PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/components/ui/Badge.tsx desktop/portal/src/console/components/ui/__tests__/Badge.test.tsx
git commit -m "feat(console): Badge UI primitive with tone variants"
```

---

## Task 13: Frontend — `ui/Modal.tsx`

**Files:**
- Create: `desktop/portal/src/console/components/ui/Modal.tsx`
- Test: `desktop/portal/src/console/components/ui/__tests__/Modal.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { Modal } from '../Modal';

describe('Modal', () => {
  it('does not render when open is false', () => {
    render(<Modal open={false} onClose={() => {}} title="X">body</Modal>);
    expect(screen.queryByText('body')).not.toBeInTheDocument();
  });

  it('renders title + body when open', () => {
    render(<Modal open onClose={() => {}} title="My Modal">body</Modal>);
    expect(screen.getByText('My Modal')).toBeInTheDocument();
    expect(screen.getByText('body')).toBeInTheDocument();
  });

  it('calls onClose when backdrop clicked', async () => {
    const onClose = vi.fn();
    render(<Modal open onClose={onClose} title="X">b</Modal>);
    await userEvent.click(screen.getByTestId('modal-backdrop'));
    expect(onClose).toHaveBeenCalled();
  });

  it('does NOT call onClose when content clicked', async () => {
    const onClose = vi.fn();
    render(<Modal open onClose={onClose} title="X">b</Modal>);
    await userEvent.click(screen.getByText('b'));
    expect(onClose).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run — expect failure**

```bash
cd desktop/portal && npm run test:run -- Modal
```

Expected: FAIL.

- [ ] **Step 3: Implement `Modal.tsx`**

```tsx
import { ReactNode } from 'react';

interface Props {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
}

export function Modal({ open, onClose, title, children }: Props) {
  if (!open) return null;

  return (
    <div
      data-testid="modal-backdrop"
      onClick={onClose}
      className="fixed inset-0 bg-black/40 flex items-center justify-center z-50"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="bg-console-surface border border-console-border p-6 min-w-[320px] max-w-[600px] font-mono"
      >
        <div className="text-xs uppercase tracking-wide text-console-text-muted mb-3">{title}</div>
        {children}
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Run**

```bash
cd desktop/portal && npm run test:run -- Modal
```

Expected: all 4 PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/components/ui/Modal.tsx desktop/portal/src/console/components/ui/__tests__/Modal.test.tsx
git commit -m "feat(console): Modal UI primitive with backdrop close"
```

---

## Task 14: Frontend — `auth/authStore.ts`

**Files:**
- Create: `desktop/portal/src/console/auth/authStore.ts`
- Test: `desktop/portal/src/console/auth/__tests__/authStore.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { describe, it, expect, beforeEach } from 'vitest';
import { useAuthStore, type ConsoleUser } from '../authStore';

const fakeUser: ConsoleUser = {
  id: 'u1',
  email: 'f@example.com',
  displayName: 'Foreman',
  role: 'foreman',
  emailVerified: true,
};

describe('authStore', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it('starts with no user and isAuthenticated=false', () => {
    const s = useAuthStore.getState();
    expect(s.user).toBeNull();
    expect(s.isAuthenticated()).toBe(false);
  });

  it('setUser puts the user in state and isAuthenticated becomes true', () => {
    useAuthStore.getState().setUser(fakeUser);
    const s = useAuthStore.getState();
    expect(s.user).toEqual(fakeUser);
    expect(s.isAuthenticated()).toBe(true);
  });

  it('clear removes the user', () => {
    useAuthStore.getState().setUser(fakeUser);
    useAuthStore.getState().clear();
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('hasConsoleAccess returns true for foreman, enterprise, admin', () => {
    const set = (role: ConsoleUser['role']) =>
      useAuthStore.getState().setUser({ ...fakeUser, role });
    set('foreman');
    expect(useAuthStore.getState().hasConsoleAccess()).toBe(true);
    set('enterprise');
    expect(useAuthStore.getState().hasConsoleAccess()).toBe(true);
    set('admin');
    expect(useAuthStore.getState().hasConsoleAccess()).toBe(true);
  });

  it('hasConsoleAccess returns false for solo, team, lead', () => {
    const set = (role: ConsoleUser['role']) =>
      useAuthStore.getState().setUser({ ...fakeUser, role });
    set('solo');
    expect(useAuthStore.getState().hasConsoleAccess()).toBe(false);
    set('team');
    expect(useAuthStore.getState().hasConsoleAccess()).toBe(false);
    set('lead');
    expect(useAuthStore.getState().hasConsoleAccess()).toBe(false);
  });
});
```

- [ ] **Step 2: Run — expect failure**

```bash
cd desktop/portal && npm run test:run -- authStore
```

Expected: FAIL.

- [ ] **Step 3: Implement `authStore.ts`**

```ts
import { create } from 'zustand';

export type ConsoleRole = 'solo' | 'team' | 'lead' | 'foreman' | 'enterprise' | 'admin';

export interface ConsoleUser {
  id: string;
  email: string;
  displayName: string;
  role: ConsoleRole;
  emailVerified: boolean;
}

interface AuthState {
  user: ConsoleUser | null;
  setUser: (u: ConsoleUser) => void;
  clear: () => void;
  isAuthenticated: () => boolean;
  hasConsoleAccess: () => boolean;
}

const CONSOLE_ROLES: ConsoleRole[] = ['foreman', 'enterprise', 'admin'];

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  setUser: (u) => set({ user: u }),
  clear: () => set({ user: null }),
  isAuthenticated: () => get().user !== null,
  hasConsoleAccess: () => {
    const user = get().user;
    return user !== null && CONSOLE_ROLES.includes(user.role);
  },
}));
```

- [ ] **Step 4: Run**

```bash
cd desktop/portal && npm run test:run -- authStore
```

Expected: all 6 PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/auth/authStore.ts desktop/portal/src/console/auth/__tests__/authStore.test.ts
git commit -m "feat(console): authStore with hasConsoleAccess role gate"
```

---

## Task 15: Frontend — `auth/authClient.ts`

**Files:**
- Create: `desktop/portal/src/console/auth/authClient.ts`
- Test: `desktop/portal/src/console/auth/__tests__/authClient.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { describe, it, expect } from 'vitest';
import { authClient } from '../authClient';

describe('authClient', () => {
  it('login returns user on valid credentials', async () => {
    const result = await authClient.login('foreman@example.com', 'password123');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.user.email).toBe('foreman@example.com');
      expect(result.user.role).toBe('foreman');
    }
  });

  it('login returns error on invalid credentials', async () => {
    const result = await authClient.login('foreman@example.com', 'wrong');
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.status).toBe(401);
    }
  });

  it('me returns the current user', async () => {
    const result = await authClient.me();
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.user.email).toBe('foreman@example.com');
    }
  });

  it('register returns a new user on success', async () => {
    const result = await authClient.register('new@example.com', 'password123', 'New User');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.user.email).toBe('new@example.com');
    }
  });

  it('logout returns ok', async () => {
    const result = await authClient.logout();
    expect(result.ok).toBe(true);
  });
});
```

- [ ] **Step 2: Run — expect failure**

```bash
cd desktop/portal && npm run test:run -- authClient
```

Expected: FAIL.

- [ ] **Step 3: Implement `authClient.ts`**

```ts
import type { ConsoleUser } from './authStore';

export type AuthResult<T> =
  | { ok: true } & T
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
```

- [ ] **Step 4: Run**

```bash
cd desktop/portal && npm run test:run -- authClient
```

Expected: all 5 PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/auth/authClient.ts desktop/portal/src/console/auth/__tests__/authClient.test.ts
git commit -m "feat(console): authClient fetch wrappers for /api/auth/*"
```

---

## Task 16: Frontend — `auth/LoginForm.tsx`

**Files:**
- Create: `desktop/portal/src/console/auth/LoginForm.tsx`
- Test: `desktop/portal/src/console/auth/__tests__/LoginForm.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { LoginForm } from '../LoginForm';
import { useAuthStore } from '../authStore';

describe('LoginForm', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it('renders email + password fields and submit button', () => {
    render(<MemoryRouter><LoginForm /></MemoryRouter>);
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument();
  });

  it('submits with valid credentials and stores user', async () => {
    render(<MemoryRouter><LoginForm /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText(/email/i), 'foreman@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'password123');
    await userEvent.click(screen.getByRole('button', { name: /log in/i }));
    await waitFor(() => {
      expect(useAuthStore.getState().user?.email).toBe('foreman@example.com');
    });
  });

  it('shows error on invalid credentials', async () => {
    render(<MemoryRouter><LoginForm /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText(/email/i), 'foreman@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: /log in/i }));
    await waitFor(() => {
      expect(screen.getByText(/invalid credentials/i)).toBeInTheDocument();
    });
  });
});
```

- [ ] **Step 2: Run — expect failure**

```bash
cd desktop/portal && npm run test:run -- LoginForm
```

Expected: FAIL.

- [ ] **Step 3: Implement `LoginForm.tsx`**

```tsx
import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { authClient } from './authClient';
import { useAuthStore } from './authStore';

export function LoginForm() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const setUser = useAuthStore((s) => s.setUser);
  const navigate = useNavigate();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    const result = await authClient.login(email, password);
    setBusy(false);
    if (!result.ok) {
      setError(result.error || 'Invalid credentials');
      return;
    }
    setUser(result.user);
    navigate('/console');
  }

  return (
    <Card title="Console Login" className="max-w-sm mx-auto mt-16">
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <Input
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <Input
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {error && <div className="text-console-danger text-xs">{error}</div>}
        <Button type="submit" disabled={busy}>
          {busy ? 'Logging in...' : 'Log in'}
        </Button>
      </form>
    </Card>
  );
}
```

- [ ] **Step 4: Run**

```bash
cd desktop/portal && npm run test:run -- LoginForm
```

Expected: all 3 PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/auth/LoginForm.tsx desktop/portal/src/console/auth/__tests__/LoginForm.test.tsx
git commit -m "feat(console): LoginForm posts to /api/auth/login and stores user"
```

---

## Task 17: Frontend — `auth/RegisterForm.tsx`

**Files:**
- Create: `desktop/portal/src/console/auth/RegisterForm.tsx`
- Test: `desktop/portal/src/console/auth/__tests__/RegisterForm.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { RegisterForm } from '../RegisterForm';
import { useAuthStore } from '../authStore';

describe('RegisterForm', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it('renders name + email + password fields and submit button', () => {
    render(<MemoryRouter><RegisterForm /></MemoryRouter>);
    expect(screen.getByLabelText(/display name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
  });

  it('submits and stores user', async () => {
    render(<MemoryRouter><RegisterForm /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText(/display name/i), 'New User');
    await userEvent.type(screen.getByLabelText(/email/i), 'new@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'password123');
    await userEvent.click(screen.getByRole('button', { name: /create account/i }));
    await waitFor(() => {
      expect(useAuthStore.getState().user?.email).toBe('new@example.com');
    });
  });

  it('blocks submit when password is too short (client check)', async () => {
    render(<MemoryRouter><RegisterForm /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText(/display name/i), 'X');
    await userEvent.type(screen.getByLabelText(/email/i), 'x@x.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'short');
    await userEvent.click(screen.getByRole('button', { name: /create account/i }));
    expect(screen.getByText(/at least 8/i)).toBeInTheDocument();
    expect(useAuthStore.getState().user).toBeNull();
  });
});
```

- [ ] **Step 2: Run — expect failure**

```bash
cd desktop/portal && npm run test:run -- RegisterForm
```

Expected: FAIL.

- [ ] **Step 3: Implement `RegisterForm.tsx`**

```tsx
import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { authClient } from './authClient';
import { useAuthStore } from './authStore';

function validatePasswordClient(p: string): string | null {
  if (p.length < 8) return 'Password must be at least 8 characters';
  if (!/[a-zA-Z]/.test(p)) return 'Password must contain at least one letter';
  if (!/[0-9]/.test(p)) return 'Password must contain at least one digit';
  return null;
}

export function RegisterForm() {
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const setUser = useAuthStore((s) => s.setUser);
  const navigate = useNavigate();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const pwErr = validatePasswordClient(password);
    if (pwErr) {
      setError(pwErr);
      return;
    }
    setBusy(true);
    const result = await authClient.register(email, password, displayName);
    setBusy(false);
    if (!result.ok) {
      setError(result.error || 'Registration failed');
      return;
    }
    setUser(result.user);
    navigate('/console');
  }

  return (
    <Card title="Create Console Account" className="max-w-sm mx-auto mt-16">
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <Input
          label="Display Name"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          required
        />
        <Input
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <Input
          label="Password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {error && <div className="text-console-danger text-xs">{error}</div>}
        <Button type="submit" disabled={busy}>
          {busy ? 'Creating...' : 'Create account'}
        </Button>
      </form>
    </Card>
  );
}
```

- [ ] **Step 4: Run**

```bash
cd desktop/portal && npm run test:run -- RegisterForm
```

Expected: all 3 PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/auth/RegisterForm.tsx desktop/portal/src/console/auth/__tests__/RegisterForm.test.tsx
git commit -m "feat(console): RegisterForm with client password validation"
```

---

## Task 18: Frontend — `auth/RequireAuth.tsx` route guard

**Files:**
- Create: `desktop/portal/src/console/auth/RequireAuth.tsx`
- Test: `desktop/portal/src/console/auth/__tests__/RequireAuth.test.tsx`

This component checks the authStore on mount. If not authenticated, it tries `authClient.me()` once (to re-hydrate from the httpOnly cookie if the user reloaded the page). If still not authenticated, it redirects to `/console/login`. If authenticated but lacks console access (role-wise), it shows an upgrade screen.

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { RequireAuth } from '../RequireAuth';
import { useAuthStore } from '../authStore';

function Protected() {
  return <div>protected content</div>;
}

function LoginStub() {
  return <div>login page</div>;
}

function renderWithRouter(initial: string) {
  return render(
    <MemoryRouter initialEntries={[initial]}>
      <Routes>
        <Route path="/console/login" element={<LoginStub />} />
        <Route
          path="/console/*"
          element={
            <RequireAuth>
              <Protected />
            </RequireAuth>
          }
        />
      </Routes>
    </MemoryRouter>
  );
}

describe('RequireAuth', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it('shows children when user has console access', async () => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'f@x.com', displayName: 'F', role: 'foreman', emailVerified: true,
    });
    renderWithRouter('/console');
    await waitFor(() => expect(screen.getByText('protected content')).toBeInTheDocument());
  });

  it('shows upgrade screen for solo role', async () => {
    useAuthStore.getState().setUser({
      id: 'u2', email: 's@x.com', displayName: 'S', role: 'solo', emailVerified: true,
    });
    renderWithRouter('/console');
    await waitFor(() =>
      expect(screen.getByText(/console requires advanced/i)).toBeInTheDocument()
    );
  });

  it('hydrates from /api/auth/me on mount when authStore is empty (cookie path)', async () => {
    // authStore is empty; MSW handler for /api/auth/me returns the test foreman user.
    renderWithRouter('/console');
    await waitFor(() => expect(screen.getByText('protected content')).toBeInTheDocument());
  });
});
```

- [ ] **Step 2: Run — expect failure**

```bash
cd desktop/portal && npm run test:run -- RequireAuth
```

Expected: FAIL.

- [ ] **Step 3: Implement `RequireAuth.tsx`**

```tsx
import { ReactNode, useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { authClient } from './authClient';
import { useAuthStore } from './authStore';
import { Card } from '../components/ui/Card';

interface Props {
  children: ReactNode;
}

type HydrationState = 'pending' | 'done';

export function RequireAuth({ children }: Props) {
  const user = useAuthStore((s) => s.user);
  const hasConsoleAccess = useAuthStore((s) => s.hasConsoleAccess);
  const setUser = useAuthStore((s) => s.setUser);
  const [hydration, setHydration] = useState<HydrationState>(user ? 'done' : 'pending');

  useEffect(() => {
    if (user) {
      setHydration('done');
      return;
    }
    let cancelled = false;
    authClient.me().then((result) => {
      if (cancelled) return;
      if (result.ok) setUser(result.user);
      setHydration('done');
    });
    return () => {
      cancelled = true;
    };
  }, [user, setUser]);

  if (hydration === 'pending') {
    return <div className="font-mono text-console-text-muted p-8">Loading...</div>;
  }

  if (!user) {
    return <Navigate to="/console/login" replace />;
  }

  if (!hasConsoleAccess()) {
    return (
      <Card title="Upgrade Required" className="max-w-md mx-auto mt-16">
        <p className="text-sm">
          The Console requires Advanced or Enterprise tier. Your current role is{' '}
          <span className="uppercase">{user.role}</span>.
        </p>
      </Card>
    );
  }

  return <>{children}</>;
}
```

- [ ] **Step 4: Run**

```bash
cd desktop/portal && npm run test:run -- RequireAuth
```

Expected: all 3 PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/auth/RequireAuth.tsx desktop/portal/src/console/auth/__tests__/RequireAuth.test.tsx
git commit -m "feat(console): RequireAuth guard with cookie re-hydration + role gate"
```

---

## Task 19: Frontend — `ConsoleShell.tsx`

**Files:**
- Create: `desktop/portal/src/console/ConsoleShell.tsx`
- Test: `desktop/portal/src/console/__tests__/ConsoleShell.test.tsx`

The shell wraps console routes with a header (showing user email + role badge + logout button) and a left nav (placeholder for now — actual nav items get added in later plans).

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ConsoleShell } from '../ConsoleShell';
import { useAuthStore } from '../auth/authStore';

describe('ConsoleShell', () => {
  beforeEach(() => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'f@x.com', displayName: 'Foreman F', role: 'foreman', emailVerified: true,
    });
  });

  it('renders the user display name and role badge', () => {
    render(<MemoryRouter><ConsoleShell><div>child</div></ConsoleShell></MemoryRouter>);
    expect(screen.getByText('Foreman F')).toBeInTheDocument();
    expect(screen.getByText(/foreman/i)).toBeInTheDocument();
  });

  it('renders children in the main pane', () => {
    render(<MemoryRouter><ConsoleShell><div>child content</div></ConsoleShell></MemoryRouter>);
    expect(screen.getByText('child content')).toBeInTheDocument();
  });

  it('logout button clears the authStore', async () => {
    render(<MemoryRouter><ConsoleShell><div>x</div></ConsoleShell></MemoryRouter>);
    await userEvent.click(screen.getByRole('button', { name: /log out/i }));
    await waitFor(() => expect(useAuthStore.getState().user).toBeNull());
  });
});
```

- [ ] **Step 2: Run — expect failure**

```bash
cd desktop/portal && npm run test:run -- ConsoleShell
```

Expected: FAIL.

- [ ] **Step 3: Implement `ConsoleShell.tsx`**

```tsx
import { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge } from './components/ui/Badge';
import { Button } from './components/ui/Button';
import { authClient } from './auth/authClient';
import { useAuthStore } from './auth/authStore';

interface Props {
  children: ReactNode;
}

export function ConsoleShell({ children }: Props) {
  const user = useAuthStore((s) => s.user);
  const clear = useAuthStore((s) => s.clear);
  const navigate = useNavigate();

  async function onLogout() {
    await authClient.logout();
    clear();
    navigate('/console/login');
  }

  return (
    <div className="min-h-screen flex flex-col font-mono">
      <header className="border-b border-console-border bg-console-surface flex items-center justify-between px-6 py-3">
        <div className="flex items-center gap-3">
          <span className="text-console-accent text-sm tracking-wide">SMITH NET / CONSOLE</span>
        </div>
        <div className="flex items-center gap-3 text-sm">
          {user && (
            <>
              <span className="text-console-text">{user.displayName}</span>
              <Badge tone="ok">{user.role}</Badge>
              <Button variant="secondary" onClick={onLogout}>Log out</Button>
            </>
          )}
        </div>
      </header>
      <div className="flex flex-1">
        <nav className="w-48 border-r border-console-border bg-console-surface p-4 text-sm text-console-text-muted">
          <div className="uppercase tracking-wide text-xs mb-2">Nav</div>
          <div className="text-console-text-muted/60">{'(routes coming soon)'}</div>
        </nav>
        <main className="flex-1 p-6">{children}</main>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Run**

```bash
cd desktop/portal && npm run test:run -- ConsoleShell
```

Expected: all 3 PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/ConsoleShell.tsx desktop/portal/src/console/__tests__/ConsoleShell.test.tsx
git commit -m "feat(console): ConsoleShell header with user + role badge + logout"
```

---

## Task 20: Frontend — `routes/PlaceholderConsoleRoute.tsx`

**Files:**
- Create: `desktop/portal/src/console/routes/PlaceholderConsoleRoute.tsx`
- Test: `desktop/portal/src/console/routes/__tests__/PlaceholderConsoleRoute.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { PlaceholderConsoleRoute } from '../PlaceholderConsoleRoute';
import { useAuthStore } from '../../auth/authStore';

describe('PlaceholderConsoleRoute', () => {
  beforeEach(() => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'f@x.com', displayName: 'F', role: 'foreman', emailVerified: true,
    });
  });

  it('greets the user by display name', () => {
    render(<PlaceholderConsoleRoute />);
    expect(screen.getByText(/welcome, F/i)).toBeInTheDocument();
  });

  it('shows the user email and role', () => {
    render(<PlaceholderConsoleRoute />);
    expect(screen.getByText(/f@x.com/i)).toBeInTheDocument();
    expect(screen.getByText(/foreman/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run — expect failure**

```bash
cd desktop/portal && npm run test:run -- PlaceholderConsoleRoute
```

Expected: FAIL.

- [ ] **Step 3: Implement `PlaceholderConsoleRoute.tsx`**

```tsx
import { Card } from '../components/ui/Card';
import { useAuthStore } from '../auth/authStore';

export function PlaceholderConsoleRoute() {
  const user = useAuthStore((s) => s.user);
  if (!user) return null;

  return (
    <Card title="Console — Foundation Ship" className="max-w-2xl">
      <p className="text-sm mb-4">Welcome, {user.displayName}. The chassis is up.</p>
      <dl className="text-sm grid grid-cols-[10ch_1fr] gap-y-1">
        <dt className="text-console-text-muted">email</dt>
        <dd>{user.email}</dd>
        <dt className="text-console-text-muted">role</dt>
        <dd className="uppercase">{user.role}</dd>
        <dt className="text-console-text-muted">verified</dt>
        <dd>{user.emailVerified ? 'yes' : 'no'}</dd>
      </dl>
      <p className="text-xs text-console-text-muted mt-6">
        Next: backend endpoint gap-fill (Plan 2), then WS + Job Board (Plan 3).
      </p>
    </Card>
  );
}
```

- [ ] **Step 4: Run**

```bash
cd desktop/portal && npm run test:run -- PlaceholderConsoleRoute
```

Expected: all 2 PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/routes/PlaceholderConsoleRoute.tsx desktop/portal/src/console/routes/__tests__/PlaceholderConsoleRoute.test.tsx
git commit -m "feat(console): PlaceholderConsoleRoute showing user identity"
```

---

## Task 21: Frontend — wire `/console/*` routes into `App.tsx`

**Files:**
- Modify: `desktop/portal/src/App.tsx`

- [ ] **Step 1: Read current App.tsx**

```bash
cat desktop/portal/src/App.tsx
```

Confirm the current shape — Routes with `/`, `/auth/callback`, `/portal`, `/dashboard`.

- [ ] **Step 2: Add console route imports + entries**

Replace `desktop/portal/src/App.tsx` with:

```tsx
import { Routes, Route } from 'react-router-dom';
import Auth from './Auth';
import AuthCallback from './AuthCallback';
import Portal from './Portal';
import DashboardApp from './dashboard/App';
import { LoginForm } from './console/auth/LoginForm';
import { RegisterForm } from './console/auth/RegisterForm';
import { RequireAuth } from './console/auth/RequireAuth';
import { ConsoleShell } from './console/ConsoleShell';
import { PlaceholderConsoleRoute } from './console/routes/PlaceholderConsoleRoute';

/**
 * Guild of Smiths Web Portal
 * Routes between authentication, the chat portal, the dashboard, and the
 * operator console (foundation only — feature routes added in later plans).
 */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Auth />} />
      <Route path="/auth/callback" element={<AuthCallback onAuthSuccess={() => {}} />} />
      <Route path="/portal" element={<Portal />} />
      <Route path="/dashboard" element={<DashboardApp />} />

      <Route path="/console/login" element={<LoginForm />} />
      <Route path="/console/register" element={<RegisterForm />} />
      <Route
        path="/console"
        element={
          <RequireAuth>
            <ConsoleShell>
              <PlaceholderConsoleRoute />
            </ConsoleShell>
          </RequireAuth>
        }
      />
    </Routes>
  );
}
```

- [ ] **Step 3: Run the full test suite**

```bash
cd desktop/portal && npm run test:run
```

Expected: all tests PASS (no new tests in this step, but existing tests must not break).

- [ ] **Step 4: Run TypeScript check**

```bash
cd desktop/portal && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/App.tsx
git commit -m "feat(console): wire /console/login, /console/register, /console routes"
```

---

## Task 22: Manual browser walkthrough

This task has no automated test — it's the spec-mandated manual verification before declaring the foundation done.

- [ ] **Step 1: Start the backend**

```bash
cd backend && npm run dev
```

Expected: backend running on port 3030. Leave running.

- [ ] **Step 2: In a second terminal, start the frontend**

```bash
cd desktop/portal && npm run dev
```

Expected: portal running on http://localhost:5173.

- [ ] **Step 3: Visit `/console` in a browser — expect redirect to login**

Open http://localhost:5173/console. You should see the login form (RequireAuth tried `/api/auth/me`, got 401, redirected to `/console/login`).

- [ ] **Step 4: Register a new Foreman-tier account**

The default registration creates a `SOLO` role user (per `createUser` in `backend/src/auth.ts:103`). For testing console access, we need a FOREMAN-or-higher user. Two options:

**Option A (preferred):** use the seeded admin account that's already in `userStore`.
- Email: `admin@smithnet.local`
- Password: whatever `DEFAULT_ADMIN_PASSWORD` is set to, or `admin123` if unset.

Go to http://localhost:5173/console/login. Enter the admin credentials. Click "Log in." You should be navigated to `/console` and see:
- Header: `SMITH NET / CONSOLE`, your display name, role badge `ADMIN`, logout button
- Main pane: Card titled "Console — Foundation Ship" with your email, role, verified status

**Option B (only if you don't have admin):** register a Solo user, then manually update the role via the admin route. Out of scope for this walkthrough — use Option A.

- [ ] **Step 5: Verify cookie was set**

Open browser DevTools -> Application -> Cookies -> http://localhost:5173. You should see:
- `smithnet_access` with `HttpOnly` checked
- `smithnet_refresh` with `HttpOnly` checked

- [ ] **Step 6: Reload the page — verify session persists**

Hit reload on `/console`. You should remain logged in (RequireAuth calls `me()`, the httpOnly cookie is sent automatically, the user is rehydrated into authStore).

- [ ] **Step 7: Log out — verify cookies cleared and redirect happens**

Click "Log out" in the header. You should be redirected to `/console/login`. Check DevTools cookies — the cookies should be gone (or have `Expires` in the past).

- [ ] **Step 8: Verify tier gate — log in as a Solo user**

Register a new account via http://localhost:5173/console/register (any unique email + valid password). After register, you should land at `/console` and see the **"Upgrade Required"** card with `Your current role is SOLO`, not the Foundation Ship card.

- [ ] **Step 9: Verify legacy routes still work**

Visit http://localhost:5173/portal and http://localhost:5173/dashboard. They should still render (Supabase auth flow, untouched by this plan).

- [ ] **Step 10: Sanity-check Android still works**

Open the Android app. Log in. Verify chat / job board / dashboard still load (Bearer header path unchanged by cookie additions).

If all 10 steps pass, the foundation is done.

---

## Task 23: Final cleanup commit

- [ ] **Step 1: Run the full test suites one more time**

```bash
cd backend && npx jest && cd ../desktop/portal && npm run test:run
```

Expected: ALL tests pass on both sides.

- [ ] **Step 2: Run TypeScript check on both sides**

```bash
cd backend && npx tsc --noEmit && cd ../desktop/portal && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Confirm git status is clean**

```bash
git status
```

Expected: no uncommitted files from this plan. Any pre-existing untracked files (e.g., the SmithAI work in `android/`) are fine — they predate this plan.

- [ ] **Step 4: Print commit summary**

```bash
git log --oneline $(git merge-base HEAD master)..HEAD
```

Should show ~20 commits from this plan, plus the spec commit from earlier.

---

## Self-Review Notes

**Spec coverage:**
- Section 1 (Architecture overview) — covered in Tasks 6, 7, 21 (URL/routing/brand/tier-gate)
- Section 2 (Components) — covered in Tasks 8-20 (theme, ui/, auth/, ConsoleShell, route)
- Section 3 (Data flow) — partial: auth flow covered (Tasks 14-18). Job/crew/chat data flow deferred to Plan 3.
- Section 4 (Auth migration) — covered in Tasks 1-5 (backend cookies) and Tasks 14-18 (frontend auth)
- Section 5 (Error handling) — partial: 401 transparent refresh deferred to Plan 2 (when there are real API calls to refresh-retry against). LoginForm error display + RegisterForm error display covered (Tasks 16-17).
- Section 6 (Testing) — covered: Vitest+RTL+MSW stack installed in Task 7, tests written in every TDD task.

**Out-of-spec items intentionally deferred:**
- Real "Advanced" tier check (currently role-based: FOREMAN+) — needs billing/subscription data
- 401 refresh-and-retry wrapper — Plan 2 (no console API calls yet to need it)
- Tailwind class verification via runtime in tests — relying on tailwind-merge + class-string regex checks

**Placeholder scan:** none found. Every step has actual code or actual commands.

**Type consistency:**
- `ConsoleUser` defined in `authStore.ts` (Task 14), consumed by `authClient.ts` (Task 15) and components — consistent
- `ConsoleRole` enum strings match `UserRole` enum values in `backend/src/auth.ts:95-102` — `'solo' | 'team' | 'lead' | 'foreman' | 'enterprise' | 'admin'`
- `AuthLoginResponse` shape matches backend `authRoutes.ts` login response: `{ user, accessToken, refreshToken, expiresIn }`

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-10-operator-console-foundation.md`. Two execution options:**

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
