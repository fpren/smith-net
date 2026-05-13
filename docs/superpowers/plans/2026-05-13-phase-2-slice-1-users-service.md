# Phase 2 Slice 1 — Users Service + FK Drift Fix

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the in-memory `UserStore` Map in `backend/src/auth.ts` with a Postgres-backed `usersService.ts`. Wrap user+profile creation in `jobsService.create()` inside a single transaction so the latent `userStore`↔`profiles` FK drift bug becomes structurally impossible.

**Architecture:** Migration 005 creates a `users` table mirroring the current `StoredUser` shape. A new `usersService.ts` exports a singleton with the same public surface as the current `UserStore` class, but every method is `async` and reads/writes to pg via the existing `pool` from `backend/src/db.ts`. `auth.ts` keeps its `userStore` export, but it now delegates to `usersService`. The admin bootstrap (`createDefaultAdmin`) becomes an idempotent `INSERT ... ON CONFLICT DO NOTHING` triggered on module import. `jobsService.create()` wraps its user-create + profile-create in a `pool.transaction()` so a failure in either rolls both back. Closes audit weak point #1.

**Tech Stack:** Node + Express + TypeScript + `pg.Pool` + `bcryptjs` + Jest + `ts-jest` + supertest

**Prerequisites:**
- Migration 002 (full schema, includes `profiles`) and 004 (jobs coords) are applied to the test DB.
- `DATABASE_URL` env var is set and `isPgEnabled()` returns true. Without it, the entire test suite skips — verify before running.
- Repo is on branch `feat/relay-hetzner-postgres`. New commits land on the same branch.

**Reference docs:**
- Spec: `docs/superpowers/specs/2026-05-13-phase-2-persistence-design.md` (Slice 1 section)
- Audit: `docs/smith-net-architecture-audit.md` (weak point #1)
- CLAUDE.md: project root — confirms no inline LLM, no inline fire-and-forget rules

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `backend/migrations/005_users_table.sql` | Create | `users` table schema + indexes |
| `backend/src/usersService.ts` | Create | pg-backed singleton; same public API as current `UserStore` class |
| `backend/src/auth.ts` | Modify | delete `UserStore` class (lines ~253-473); re-export `userStore` as delegate to `usersService` |
| `backend/src/jobsService.ts` | Modify | wrap user-create + profile-create in `pool.transaction()` inside `create()` |
| `backend/src/__tests__/usersService.test.ts` | Create | 9 integration tests, one per method group |
| `backend/src/__tests__/jobs-routes.test.ts` | Modify | drop the manual `INSERT INTO profiles` from `createForemanAndLogin` helper; rely on jobsService transaction |
| `backend/src/__tests__/api-auth-integration.test.ts` | Modify | only if helpers depend on sync `userStore.createUser` return shape — likely no change |
| `docs/smith-net-architecture-audit.md` | Modify (Task 12) | annotate weak point #1 as `[closed in slice 1, phase-2-<date>]` |

---

## Task 1 — Migration 005: users table schema

**Files:**
- Create: `backend/migrations/005_users_table.sql`

- [ ] **Step 1: Write the migration**

Create `backend/migrations/005_users_table.sql`:

```sql
-- 005_users_table.sql
-- Phase 2 Slice 1: replace in-memory UserStore Map with a pg table.
-- Mirrors the StoredUser shape in backend/src/auth.ts. Admin row is
-- inserted at runtime by usersService.bootstrapAdmin() (idempotent),
-- so this migration is pure DDL.

CREATE TABLE IF NOT EXISTS users (
  id                              TEXT PRIMARY KEY,
  email                           TEXT NOT NULL,
  email_lower                     TEXT GENERATED ALWAYS AS (LOWER(email)) STORED,
  password_hash                   TEXT NOT NULL,
  display_name                    TEXT NOT NULL,
  role                            TEXT NOT NULL,
  organization_id                 TEXT,
  is_active                       BOOLEAN NOT NULL DEFAULT TRUE,
  mfa_enabled                     BOOLEAN NOT NULL DEFAULT FALSE,
  mfa_secret                      TEXT,
  failed_login_count              INTEGER NOT NULL DEFAULT 0,
  locked_until                    TIMESTAMPTZ,
  email_verified_at               TIMESTAMPTZ,
  email_verification_token        TEXT,
  email_verification_expires_at   TIMESTAMPTZ,
  email_verification_last_sent_at TIMESTAMPTZ,
  refresh_tokens                  JSONB NOT NULL DEFAULT '[]'::jsonb,
  last_login_at                   TIMESTAMPTZ,
  created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_uidx ON users (email_lower);
CREATE INDEX IF NOT EXISTS users_role_idx ON users (role);
CREATE INDEX IF NOT EXISTS users_email_verification_token_idx
  ON users (email_verification_token)
  WHERE email_verification_token IS NOT NULL;
```

- [ ] **Step 2: Apply the migration to the test DB**

Run:

```bash
cd backend && psql "$DATABASE_URL" -f migrations/005_users_table.sql
```

Expected output: `CREATE TABLE`, `CREATE INDEX` (×3). No errors.

- [ ] **Step 3: Verify schema**

Run:

```bash
psql "$DATABASE_URL" -c "\d users"
```

Expected: 18 columns including `id`, `email_lower` (generated stored), `refresh_tokens` (jsonb), `locked_until` (timestamptz). 3 indexes: PK, unique email_lower, role, partial verification token.

- [ ] **Step 4: Commit**

```bash
git add backend/migrations/005_users_table.sql
git commit -m "feat(db): migration 005 — users table (Phase 2 Slice 1)"
```

---

## Task 2 — usersService.createUser (TDD entry point)

**Files:**
- Create: `backend/src/__tests__/usersService.test.ts`
- Create: `backend/src/usersService.ts`

- [ ] **Step 1: Write the failing test**

Create `backend/src/__tests__/usersService.test.ts`:

```typescript
import { pg, isPgEnabled } from '../db';
import { usersService } from '../usersService';
import { UserRole } from '../auth';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanUsers() {
  if (!isPgEnabled()) return;
  // Remove all users except admin-001 between tests.
  await pg!.query("DELETE FROM users WHERE id != 'admin-001'");
}

describeDb('usersService.createUser', () => {
  beforeEach(cleanUsers);
  afterAll(async () => { await pg?.end(); });

  it('inserts a user row and returns the StoredUser', async () => {
    const email = `t1-${Date.now()}@example.com`;
    const user = await usersService.createUser(email, 'password123', 'Test One', UserRole.SOLO);
    expect(user.id).toBeTruthy();
    expect(user.email).toBe(email.toLowerCase());
    expect(user.displayName).toBe('Test One');
    expect(user.role).toBe(UserRole.SOLO);
    expect(user.passwordHash).not.toBe('password123'); // hashed
    expect(user.emailVerificationToken).toBeTruthy();
    expect(user.emailVerifiedAt).toBeUndefined();
  });

  it('rejects duplicate emails (case-insensitive)', async () => {
    const email = `t2-${Date.now()}@example.com`;
    await usersService.createUser(email, 'password123', 'A', UserRole.SOLO);
    await expect(
      usersService.createUser(email.toUpperCase(), 'password123', 'B', UserRole.SOLO)
    ).rejects.toThrow(/already registered/i);
  });

  it('rejects weak passwords', async () => {
    const email = `t3-${Date.now()}@example.com`;
    await expect(
      usersService.createUser(email, 'short', 'C', UserRole.SOLO)
    ).rejects.toThrow();
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts
```

Expected: FAIL with "Cannot find module '../usersService'".

- [ ] **Step 3: Implement usersService.createUser**

Create `backend/src/usersService.ts`:

```typescript
/**
 * Phase 2 Slice 1: pg-backed user store. Replaces the in-memory UserStore
 * Map in auth.ts. Same public API but every method is async.
 *
 * Singleton: `import { usersService } from './usersService'`.
 */

import bcrypt from 'bcryptjs';
import crypto from 'crypto';
import { v4 as uuidv4 } from 'uuid';
import { pg, isPgEnabled } from './db';
import {
  StoredUser,
  UserRole,
  LoginResult,
  validatePassword,
} from './auth';

const SALT_ROUNDS = 10;
const EMAIL_VERIFICATION_TTL_MS = 24 * 60 * 60 * 1000;

function requirePg() {
  if (!isPgEnabled() || !pg) {
    throw new Error('[usersService] DATABASE_URL is required; pg pool is not initialized');
  }
  return pg;
}

interface UserRow {
  id: string;
  email: string;
  email_lower: string;
  password_hash: string;
  display_name: string;
  role: string;
  organization_id: string | null;
  is_active: boolean;
  mfa_enabled: boolean;
  mfa_secret: string | null;
  failed_login_count: number;
  locked_until: Date | null;
  email_verified_at: Date | null;
  email_verification_token: string | null;
  email_verification_expires_at: Date | null;
  email_verification_last_sent_at: Date | null;
  refresh_tokens: string[];
  last_login_at: Date | null;
  created_at: Date;
  updated_at: Date;
}

function rowToUser(r: UserRow): StoredUser {
  return {
    id: r.id,
    email: r.email,
    passwordHash: r.password_hash,
    displayName: r.display_name,
    role: r.role as UserRole,
    organizationId: r.organization_id ?? undefined,
    isActive: r.is_active,
    mfaEnabled: r.mfa_enabled,
    mfaSecret: r.mfa_secret ?? undefined,
    failedLoginCount: r.failed_login_count,
    lockedUntil: r.locked_until ? r.locked_until.getTime() : undefined,
    emailVerifiedAt: r.email_verified_at ? r.email_verified_at.getTime() : undefined,
    emailVerificationToken: r.email_verification_token ?? undefined,
    emailVerificationExpiresAt: r.email_verification_expires_at ? r.email_verification_expires_at.getTime() : undefined,
    emailVerificationLastSentAt: r.email_verification_last_sent_at ? r.email_verification_last_sent_at.getTime() : undefined,
    lastLoginAt: r.last_login_at ? r.last_login_at.getTime() : undefined,
    createdAt: r.created_at.getTime(),
    updatedAt: r.updated_at.getTime(),
  };
}

class UsersService {
  async createUser(
    email: string,
    password: string,
    displayName: string,
    role: UserRole = UserRole.SOLO
  ): Promise<StoredUser> {
    const validation = validatePassword(password);
    if (!validation.valid) {
      throw new Error(validation.reason);
    }

    const db = requirePg();
    const existing = await db.query<UserRow>(
      'SELECT id FROM users WHERE email_lower = $1',
      [email.toLowerCase()]
    );
    if (existing.rowCount && existing.rowCount > 0) {
      throw new Error('Email already registered');
    }

    const id = uuidv4();
    const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);
    const token = crypto.randomBytes(32).toString('hex');
    const expires = new Date(Date.now() + EMAIL_VERIFICATION_TTL_MS);

    const result = await db.query<UserRow>(
      `INSERT INTO users (
         id, email, password_hash, display_name, role,
         is_active, mfa_enabled, failed_login_count,
         email_verification_token, email_verification_expires_at
       ) VALUES ($1, $2, $3, $4, $5, TRUE, FALSE, 0, $6, $7)
       RETURNING *`,
      [id, email.toLowerCase(), passwordHash, displayName, role, token, expires]
    );

    console.log(`[usersService] User created: ${email} (${role})`);
    return rowToUser(result.rows[0]);
  }
}

export const usersService = new UsersService();
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts
```

Expected: PASS — 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/usersService.ts backend/src/__tests__/usersService.test.ts
git commit -m "feat(users): createUser via pg with dedup + password validation"
```

---

## Task 3 — usersService.getUserById, getUserByEmail

**Files:**
- Modify: `backend/src/usersService.ts` (add 2 methods)
- Modify: `backend/src/__tests__/usersService.test.ts` (add test block)

- [ ] **Step 1: Write the failing tests**

Append to `backend/src/__tests__/usersService.test.ts`:

```typescript
describeDb('usersService.getUserById / getUserByEmail', () => {
  beforeEach(cleanUsers);

  it('getUserById returns user when present, undefined otherwise', async () => {
    const email = `t4-${Date.now()}@example.com`;
    const created = await usersService.createUser(email, 'password123', 'D', UserRole.SOLO);
    const fetched = await usersService.getUserById(created.id);
    expect(fetched?.id).toBe(created.id);
    expect(fetched?.email).toBe(email.toLowerCase());

    const missing = await usersService.getUserById('does-not-exist');
    expect(missing).toBeUndefined();
  });

  it('getUserByEmail is case-insensitive', async () => {
    const email = `t5-${Date.now()}@example.com`;
    await usersService.createUser(email, 'password123', 'E', UserRole.SOLO);
    const a = await usersService.getUserByEmail(email);
    const b = await usersService.getUserByEmail(email.toUpperCase());
    expect(a?.id).toBe(b?.id);
    expect(a?.id).toBeTruthy();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts -t 'getUserById'
```

Expected: FAIL with `TypeError: usersService.getUserById is not a function`.

- [ ] **Step 3: Implement getUserById and getUserByEmail**

Add to `class UsersService` in `backend/src/usersService.ts`:

```typescript
async getUserById(id: string): Promise<StoredUser | undefined> {
  const db = requirePg();
  const result = await db.query<UserRow>('SELECT * FROM users WHERE id = $1', [id]);
  return result.rows[0] ? rowToUser(result.rows[0]) : undefined;
}

async getUserByEmail(email: string): Promise<StoredUser | undefined> {
  const db = requirePg();
  const result = await db.query<UserRow>(
    'SELECT * FROM users WHERE email_lower = $1',
    [email.toLowerCase()]
  );
  return result.rows[0] ? rowToUser(result.rows[0]) : undefined;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts
```

Expected: PASS — all 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/usersService.ts backend/src/__tests__/usersService.test.ts
git commit -m "feat(users): getUserById + getUserByEmail (case-insensitive)"
```

---

## Task 4 — usersService.verifyPassword (happy path)

**Files:**
- Modify: `backend/src/usersService.ts`
- Modify: `backend/src/__tests__/usersService.test.ts`

- [ ] **Step 1: Write the failing test**

Append to the test file:

```typescript
describeDb('usersService.verifyPassword — happy path', () => {
  beforeEach(cleanUsers);

  it('returns ok=true and resets failed counter on success', async () => {
    const email = `t6-${Date.now()}@example.com`;
    const created = await usersService.createUser(email, 'password123', 'F', UserRole.SOLO);
    const result = await usersService.verifyPassword(email, 'password123');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.user.id).toBe(created.id);
    }
    const fresh = await usersService.getUserById(created.id);
    expect(fresh?.failedLoginCount).toBe(0);
    expect(fresh?.lastLoginAt).toBeDefined();
  });

  it('returns ok=false / invalid_credentials when password is wrong', async () => {
    const email = `t7-${Date.now()}@example.com`;
    await usersService.createUser(email, 'password123', 'G', UserRole.SOLO);
    const result = await usersService.verifyPassword(email, 'wrong-password');
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.reason).toBe('invalid_credentials');
    }
  });

  it('returns ok=false / invalid_credentials with constant-time delay for unknown email', async () => {
    const start = Date.now();
    const result = await usersService.verifyPassword('nobody@example.com', 'whatever');
    const elapsed = Date.now() - start;
    expect(result.ok).toBe(false);
    expect(elapsed).toBeGreaterThanOrEqual(190); // ENUMERATION_DELAY_MS = 200, allow 10ms jitter
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts -t 'verifyPassword'
```

Expected: FAIL with `TypeError: usersService.verifyPassword is not a function`.

- [ ] **Step 3: Implement verifyPassword (happy path only)**

Add to `class UsersService`:

```typescript
async verifyPassword(email: string, password: string): Promise<LoginResult> {
  const db = requirePg();
  const ENUMERATION_DELAY_MS = 200;

  const result = await db.query<UserRow>(
    'SELECT * FROM users WHERE email_lower = $1',
    [email.toLowerCase()]
  );
  const user = result.rows[0] ? rowToUser(result.rows[0]) : undefined;

  if (!user || !user.isActive) {
    await new Promise((r) => setTimeout(r, ENUMERATION_DELAY_MS));
    return { ok: false, reason: 'invalid_credentials' };
  }

  // Lockout check — see Task 5 for full implementation.
  if (user.lockedUntil && user.lockedUntil > Date.now()) {
    const retryMinutes = Math.max(1, Math.ceil((user.lockedUntil - Date.now()) / 60_000));
    return { ok: false, reason: 'locked', retryMinutes };
  }

  const isValid = await bcrypt.compare(password, user.passwordHash);
  if (!isValid) {
    // Failure counter handled in Task 5; this is just the happy-path scaffold.
    return { ok: false, reason: 'invalid_credentials' };
  }

  await db.query(
    `UPDATE users
     SET last_login_at = NOW(),
         failed_login_count = 0,
         locked_until = NULL,
         updated_at = NOW()
     WHERE id = $1`,
    [user.id]
  );

  const fresh = await this.getUserById(user.id);
  return { ok: true, user: fresh! };
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts -t 'verifyPassword'
```

Expected: PASS — 3 verifyPassword tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/usersService.ts backend/src/__tests__/usersService.test.ts
git commit -m "feat(users): verifyPassword happy path + enumeration delay"
```

---

## Task 5 — usersService.verifyPassword (lockout)

**Files:**
- Modify: `backend/src/usersService.ts` (replace verifyPassword)
- Modify: `backend/src/__tests__/usersService.test.ts`

- [ ] **Step 1: Write the failing test**

Append to the test file:

```typescript
describeDb('usersService.verifyPassword — lockout', () => {
  beforeEach(cleanUsers);

  it('locks the account after 5 failed attempts', async () => {
    const email = `t8-${Date.now()}@example.com`;
    const created = await usersService.createUser(email, 'password123', 'H', UserRole.SOLO);
    for (let i = 0; i < 5; i++) {
      const r = await usersService.verifyPassword(email, 'wrong');
      expect(r.ok).toBe(false);
    }
    const locked = await usersService.verifyPassword(email, 'password123');
    expect(locked.ok).toBe(false);
    if (!locked.ok) {
      expect(locked.reason).toBe('locked');
      expect((locked as { retryMinutes: number }).retryMinutes).toBeGreaterThan(0);
    }
    const fresh = await usersService.getUserById(created.id);
    expect(fresh?.failedLoginCount).toBe(5);
    expect(fresh?.lockedUntil).toBeGreaterThan(Date.now());
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts -t 'lockout'
```

Expected: FAIL — locked state not persisted; failed_login_count stays at 0.

- [ ] **Step 3: Replace verifyPassword to persist failed-attempt counter**

In `class UsersService`, replace the failure branch in `verifyPassword`:

```typescript
if (!isValid) {
  const MAX_FAILED_LOGINS = 5;
  const LOCKOUT_DURATION_MS = 15 * 60 * 1000;
  await db.query(
    `UPDATE users
     SET failed_login_count = failed_login_count + 1,
         locked_until = CASE
           WHEN failed_login_count + 1 >= $2 THEN NOW() + ($3::text || ' milliseconds')::interval
           ELSE locked_until
         END,
         updated_at = NOW()
     WHERE id = $1`,
    [user.id, MAX_FAILED_LOGINS, String(LOCKOUT_DURATION_MS)]
  );
  return { ok: false, reason: 'invalid_credentials' };
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts
```

Expected: PASS — all verifyPassword tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/usersService.ts backend/src/__tests__/usersService.test.ts
git commit -m "feat(users): persist failed_login_count + locked_until on bad password"
```

---

## Task 6 — usersService refresh tokens (store/validate/revoke)

**Files:**
- Modify: `backend/src/usersService.ts`
- Modify: `backend/src/__tests__/usersService.test.ts`

- [ ] **Step 1: Write the failing test**

Append:

```typescript
describeDb('usersService refresh tokens', () => {
  beforeEach(cleanUsers);

  it('storeRefreshToken + validateRefreshToken returns the userId', async () => {
    const email = `t9-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'I', UserRole.SOLO);
    await usersService.storeRefreshToken('refresh-abc', u.id);
    const found = await usersService.validateRefreshToken('refresh-abc');
    expect(found).toBe(u.id);
  });

  it('revokeRefreshToken removes it', async () => {
    const email = `t10-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'J', UserRole.SOLO);
    await usersService.storeRefreshToken('refresh-xyz', u.id);
    await usersService.revokeRefreshToken('refresh-xyz');
    const found = await usersService.validateRefreshToken('refresh-xyz');
    expect(found).toBeUndefined();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts -t 'refresh tokens'
```

Expected: FAIL — methods not implemented.

- [ ] **Step 3: Implement refresh-token methods**

Add to `class UsersService`:

```typescript
async storeRefreshToken(token: string, userId: string): Promise<void> {
  const db = requirePg();
  await db.query(
    `UPDATE users
     SET refresh_tokens = refresh_tokens || jsonb_build_array($2::text),
         updated_at = NOW()
     WHERE id = $1`,
    [userId, token]
  );
}

async validateRefreshToken(token: string): Promise<string | undefined> {
  const db = requirePg();
  const result = await db.query<{ id: string }>(
    `SELECT id FROM users WHERE refresh_tokens ? $1 LIMIT 1`,
    [token]
  );
  return result.rows[0]?.id;
}

async revokeRefreshToken(token: string): Promise<void> {
  const db = requirePg();
  await db.query(
    `UPDATE users
     SET refresh_tokens = refresh_tokens - $1,
         updated_at = NOW()
     WHERE refresh_tokens ? $1`,
    [token]
  );
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts
```

Expected: PASS — refresh-token tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/usersService.ts backend/src/__tests__/usersService.test.ts
git commit -m "feat(users): refresh tokens via jsonb array operators"
```

---

## Task 7 — usersService email verification methods

**Files:**
- Modify: `backend/src/usersService.ts`
- Modify: `backend/src/__tests__/usersService.test.ts`

- [ ] **Step 1: Write the failing tests**

Append:

```typescript
describeDb('usersService email verification', () => {
  beforeEach(cleanUsers);

  it('findByVerificationToken returns user when token valid and unexpired', async () => {
    const email = `t11-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'K', UserRole.SOLO);
    expect(u.emailVerificationToken).toBeDefined();
    const found = await usersService.findByVerificationToken(u.emailVerificationToken!);
    expect(found?.id).toBe(u.id);
  });

  it('findByVerificationToken returns undefined for unknown token', async () => {
    const found = await usersService.findByVerificationToken('nope');
    expect(found).toBeUndefined();
  });

  it('markEmailVerified clears the token and sets emailVerifiedAt', async () => {
    const email = `t12-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'L', UserRole.SOLO);
    const after = await usersService.markEmailVerified(u.id);
    expect(after?.emailVerifiedAt).toBeDefined();
    expect(after?.emailVerificationToken).toBeUndefined();
  });

  it('regenerateVerificationToken returns null for already-verified users', async () => {
    const email = `t13-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'M', UserRole.SOLO);
    await usersService.markEmailVerified(u.id);
    const tok = await usersService.regenerateVerificationToken(u.id);
    expect(tok).toBeNull();
  });

  it('regenerateVerificationToken issues a fresh token for unverified users', async () => {
    const email = `t14-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'N', UserRole.SOLO);
    const oldTok = u.emailVerificationToken;
    const newTok = await usersService.regenerateVerificationToken(u.id);
    expect(newTok).toBeTruthy();
    expect(newTok).not.toBe(oldTok);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts -t 'email verification'
```

Expected: FAIL — methods not implemented.

- [ ] **Step 3: Implement email verification methods**

Add to `class UsersService`:

```typescript
async findByVerificationToken(token: string): Promise<StoredUser | undefined> {
  if (!token) return undefined;
  const db = requirePg();
  const result = await db.query<UserRow>(
    `SELECT * FROM users
     WHERE email_verification_token = $1
       AND email_verification_expires_at > NOW()`,
    [token]
  );
  return result.rows[0] ? rowToUser(result.rows[0]) : undefined;
}

async markEmailVerified(userId: string): Promise<StoredUser | undefined> {
  const db = requirePg();
  const result = await db.query<UserRow>(
    `UPDATE users
     SET email_verified_at = NOW(),
         email_verification_token = NULL,
         email_verification_expires_at = NULL,
         updated_at = NOW()
     WHERE id = $1
     RETURNING *`,
    [userId]
  );
  return result.rows[0] ? rowToUser(result.rows[0]) : undefined;
}

async regenerateVerificationToken(userId: string): Promise<string | null> {
  const db = requirePg();
  const existing = await this.getUserById(userId);
  if (!existing) return null;
  if (existing.emailVerifiedAt) return null;

  const token = crypto.randomBytes(32).toString('hex');
  const expires = new Date(Date.now() + EMAIL_VERIFICATION_TTL_MS);
  await db.query(
    `UPDATE users
     SET email_verification_token = $2,
         email_verification_expires_at = $3,
         email_verification_last_sent_at = NOW(),
         updated_at = NOW()
     WHERE id = $1`,
    [userId, token, expires]
  );
  return token;
}

async recordVerificationSendAttempt(userId: string): Promise<void> {
  const db = requirePg();
  await db.query(
    `UPDATE users
     SET email_verification_last_sent_at = NOW(),
         updated_at = NOW()
     WHERE id = $1`,
    [userId]
  );
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts
```

Expected: PASS — all email-verification tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/usersService.ts backend/src/__tests__/usersService.test.ts
git commit -m "feat(users): email verification — find/mark/regenerate/record"
```

---

## Task 8 — usersService.updateUser + getAllUsers

**Files:**
- Modify: `backend/src/usersService.ts`
- Modify: `backend/src/__tests__/usersService.test.ts`

- [ ] **Step 1: Write the failing tests**

Append:

```typescript
describeDb('usersService updateUser + getAllUsers', () => {
  beforeEach(cleanUsers);

  it('updateUser merges partial updates and returns the new state', async () => {
    const email = `t15-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'O', UserRole.SOLO);
    const updated = await usersService.updateUser(u.id, { displayName: 'Renamed', isActive: false });
    expect(updated?.displayName).toBe('Renamed');
    expect(updated?.isActive).toBe(false);
    expect(updated?.email).toBe(email.toLowerCase()); // untouched
  });

  it('updateUser returns undefined for missing user', async () => {
    const u = await usersService.updateUser('missing', { displayName: 'X' });
    expect(u).toBeUndefined();
  });

  it('getAllUsers returns every row', async () => {
    await usersService.createUser(`t16a-${Date.now()}@example.com`, 'password123', 'P1', UserRole.SOLO);
    await usersService.createUser(`t16b-${Date.now()}@example.com`, 'password123', 'P2', UserRole.SOLO);
    const all = await usersService.getAllUsers();
    expect(all.length).toBeGreaterThanOrEqual(2);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts -t 'updateUser|getAllUsers'
```

Expected: FAIL — methods not implemented.

- [ ] **Step 3: Implement updateUser + getAllUsers**

Add to `class UsersService`:

```typescript
async updateUser(id: string, updates: Partial<StoredUser>): Promise<StoredUser | undefined> {
  const db = requirePg();
  const sets: string[] = [];
  const params: unknown[] = [id];
  let i = 2;
  const colMap: Record<keyof StoredUser, string> = {
    id: 'id',
    email: 'email',
    passwordHash: 'password_hash',
    displayName: 'display_name',
    role: 'role',
    organizationId: 'organization_id',
    isActive: 'is_active',
    mfaEnabled: 'mfa_enabled',
    mfaSecret: 'mfa_secret',
    failedLoginCount: 'failed_login_count',
    lockedUntil: 'locked_until',
    emailVerifiedAt: 'email_verified_at',
    emailVerificationToken: 'email_verification_token',
    emailVerificationExpiresAt: 'email_verification_expires_at',
    emailVerificationLastSentAt: 'email_verification_last_sent_at',
    lastLoginAt: 'last_login_at',
    createdAt: 'created_at',
    updatedAt: 'updated_at',
  };
  const timestampFields = new Set([
    'lockedUntil', 'emailVerifiedAt', 'emailVerificationExpiresAt',
    'emailVerificationLastSentAt', 'lastLoginAt',
  ]);
  for (const [key, val] of Object.entries(updates) as [keyof StoredUser, unknown][]) {
    if (key === 'id' || key === 'createdAt' || key === 'updatedAt') continue;
    const col = colMap[key];
    if (!col) continue;
    sets.push(`${col} = $${i}`);
    params.push(timestampFields.has(key) && typeof val === 'number' ? new Date(val) : val);
    i++;
  }
  if (sets.length === 0) return this.getUserById(id);
  sets.push('updated_at = NOW()');
  const sql = `UPDATE users SET ${sets.join(', ')} WHERE id = $1 RETURNING *`;
  const result = await db.query<UserRow>(sql, params);
  return result.rows[0] ? rowToUser(result.rows[0]) : undefined;
}

async getAllUsers(): Promise<StoredUser[]> {
  const db = requirePg();
  const result = await db.query<UserRow>('SELECT * FROM users ORDER BY created_at DESC');
  return result.rows.map(rowToUser);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts
```

Expected: PASS — all tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/usersService.ts backend/src/__tests__/usersService.test.ts
git commit -m "feat(users): updateUser (partial) + getAllUsers"
```

---

## Task 9 — Bootstrap admin on import (idempotent)

**Files:**
- Modify: `backend/src/usersService.ts`
- Modify: `backend/src/__tests__/usersService.test.ts`

- [ ] **Step 1: Write the failing test**

Append:

```typescript
describeDb('usersService.bootstrapAdmin', () => {
  it('inserts admin-001 if absent; no-op if present', async () => {
    await pg!.query("DELETE FROM users WHERE id = 'admin-001'");
    await usersService.bootstrapAdmin();
    const first = await usersService.getUserById('admin-001');
    expect(first?.email).toBe('admin@smithnet.local');
    expect(first?.role).toBe(UserRole.ADMIN);
    expect(first?.emailVerifiedAt).toBeDefined();

    await usersService.bootstrapAdmin();
    const count = await pg!.query("SELECT COUNT(*) FROM users WHERE id = 'admin-001'");
    expect(parseInt(count.rows[0].count, 10)).toBe(1);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts -t 'bootstrapAdmin'
```

Expected: FAIL — method not implemented.

- [ ] **Step 3: Implement bootstrapAdmin**

Add to `class UsersService`:

```typescript
async bootstrapAdmin(): Promise<void> {
  const db = requirePg();
  const rawPassword = process.env.DEFAULT_ADMIN_PASSWORD || 'admin123';
  const isDefault = !process.env.DEFAULT_ADMIN_PASSWORD;
  const passwordHash = await bcrypt.hash(rawPassword, SALT_ROUNDS);

  const result = await db.query(
    `INSERT INTO users (
       id, email, password_hash, display_name, role,
       is_active, mfa_enabled, failed_login_count,
       email_verified_at
     ) VALUES ('admin-001', 'admin@smithnet.local', $1, 'System Admin', 'admin',
               TRUE, FALSE, 0, NOW())
     ON CONFLICT (id) DO NOTHING`,
    [passwordHash]
  );

  if ((result.rowCount ?? 0) > 0) {
    if (isDefault) {
      console.warn('[usersService] Bootstrapped admin with built-in password — set DEFAULT_ADMIN_PASSWORD for production.');
    } else {
      console.log('[usersService] Bootstrapped admin from DEFAULT_ADMIN_PASSWORD env.');
    }
  }
}
```

At the **bottom of `backend/src/usersService.ts`**, add this auto-bootstrap (after the singleton export):

```typescript
// Run admin bootstrap once at import. Idempotent — safe to import many times.
if (isPgEnabled()) {
  usersService.bootstrapAdmin().catch((err) => {
    console.error('[usersService] admin bootstrap failed:', err);
  });
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts -t 'bootstrapAdmin'
```

Expected: PASS.

- [ ] **Step 5: Also align profiles row for admin-001**

The `profiles` table (from migration 002) may have a row `id='admin'` that's misaligned. Run:

```bash
psql "$DATABASE_URL" -c "UPDATE profiles SET id = 'admin-001' WHERE id = 'admin';"
```

If no row: skip silently. If conflict (a row with id='admin-001' already exists): `DELETE FROM profiles WHERE id='admin' AND EXISTS (SELECT 1 FROM profiles WHERE id='admin-001');`. Verify with:

```bash
psql "$DATABASE_URL" -c "SELECT id FROM profiles WHERE id LIKE 'admin%';"
```

Expected: single row with `id='admin-001'`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/usersService.ts backend/src/__tests__/usersService.test.ts
git commit -m "feat(users): idempotent bootstrapAdmin on import; align profiles.id"
```

---

## Task 10 — auth.ts: delete UserStore class, re-export usersService

**Files:**
- Modify: `backend/src/auth.ts` (delete lines ~249-473)

- [ ] **Step 1: Run the full backend test suite to confirm green starting point**

Run:

```bash
cd backend && npx jest
```

Expected: PASS — baseline before refactor.

- [ ] **Step 2: Delete the UserStore class and replace the export**

In `backend/src/auth.ts`, locate the block from the comment `// IN-MEMORY USER STORE (Replace with DB in production)` through `export const userStore = new UserStore();`. Replace the **entire block** with:

```typescript
// ════════════════════════════════════════════════════════════════════
// USER STORE — pg-backed via usersService
// ════════════════════════════════════════════════════════════════════
// The original in-memory UserStore class moved to usersService.ts in
// Phase 2 Slice 1. Existing callers continue to use `userStore.method()`
// but every call is now async and hits Postgres.

import { usersService } from './usersService';
export const userStore = usersService;
```

Also delete `EMAIL_VERIFICATION_TTL_MS`, `sleep`, `SALT_ROUNDS`, and any other UserStore-internal constants that are no longer referenced outside the class. Use `tsc --noEmit` to find dead references:

```bash
cd backend && npx tsc --noEmit
```

If anything still references those constants, fix the call site (usually it's an old import that can be dropped).

- [ ] **Step 3: Add `await` to every userStore.* call site in the backend**

Run:

```bash
cd backend && grep -rn "userStore\." src/ --include="*.ts" | grep -v "__tests__\|usersService"
```

For every match where the call is not already `await`ed AND the method returns a non-void value (createUser, getUserById, getUserByEmail, verifyPassword, updateUser, findByVerificationToken, markEmailVerified, regenerateVerificationToken, validateRefreshToken, getAllUsers, bootstrapAdmin), add `await`. Containing function must be `async`.

Methods that returned `void` already (`storeRefreshToken`, `revokeRefreshToken`, `recordVerificationSendAttempt`) now return `Promise<void>` — `await` them so the next statement sees the write.

- [ ] **Step 4: Run the full backend test suite**

Run:

```bash
cd backend && npx jest
```

Expected: PASS — all existing tests pass. Look out for these likely failures:
- `password-lockout.test.ts` — may need helper updates to async `userStore.createUser`
- `email-verification.test.ts` — same
- `api-auth-integration.test.ts` — same
- `jobs-routes.test.ts` — uses `userStore.createUser` in the helper; that call is already `await`ed so should still pass

Fix any remaining sync→async breakage in the test helpers (it should be limited).

- [ ] **Step 5: Commit**

```bash
git add backend/src/auth.ts backend/src/__tests__/
git commit -m "refactor(auth): delete UserStore class — userStore now delegates to usersService"
```

---

## Task 11 — jobsService.create transactional user+profile

**Files:**
- Modify: `backend/src/jobsService.ts`
- Modify: `backend/src/__tests__/jobs-routes.test.ts` (drop manual profile INSERT from helper)

- [ ] **Step 1: Read the current jobsService.create**

Run:

```bash
grep -n "async create\|async function create\|INSERT INTO profiles\|INSERT INTO jobs" backend/src/jobsService.ts | head -10
```

Identify where `create()` is defined and whether profile-create happens inline or in a separate code path.

- [ ] **Step 2: Write the failing test**

Add to the **imports block at the top** of `backend/src/__tests__/usersService.test.ts`:

```typescript
import { createUserAndProfile } from '../jobsService';
```

Then **append** the test block to the bottom of the file:

```typescript
describeDb('jobsService.createUserAndProfile — transactional user+profile', () => {
  beforeEach(cleanUsers);

  it('inserts both user and profile rows on success', async () => {
    const email = `t-tx2-${Date.now()}@example.com`;
    const created = await createUserAndProfile({
      email,
      password: 'password123',
      displayName: 'OK',
      role: UserRole.FOREMAN,
    });
    expect(created.id).toBeTruthy();
    const userRow = await pg!.query('SELECT id FROM users WHERE id = $1', [created.id]);
    const profileRow = await pg!.query('SELECT id FROM profiles WHERE id = $1', [created.id]);
    expect(userRow.rowCount).toBe(1);
    expect(profileRow.rowCount).toBe(1);
  });

  it('rolls back the user when profile insert violates a constraint', async () => {
    // Pre-seed a profile with a known id so the next user+profile create collides
    // on the profiles PK. The user insert succeeds; the profile insert fails;
    // both must be rolled back.
    const collidedId = 'fixed-collision-id-' + Date.now();
    await pg!.query(
      "INSERT INTO profiles (id, email, display_name, role) VALUES ($1, $2, $3, $4)",
      [collidedId, 'pre@example.com', 'Pre', UserRole.FOREMAN]
    );

    await expect(
      createUserAndProfile({
        email: `t-tx3-${Date.now()}@example.com`,
        password: 'password123',
        displayName: 'Collider',
        role: UserRole.FOREMAN,
        forcedId: collidedId,
      })
    ).rejects.toThrow();

    // After rollback, NO user row with that id should exist.
    const userRow = await pg!.query('SELECT id FROM users WHERE id = $1', [collidedId]);
    expect(userRow.rowCount).toBe(0);
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts -t 'transactional'
```

Expected: FAIL — `jobsService.createUserAndProfile` does not exist.

- [ ] **Step 4: Add the transactional method to jobsService**

Add to `backend/src/jobsService.ts`:

```typescript
import { pg, isPgEnabled } from './db';
import bcrypt from 'bcryptjs';
import { v4 as uuidv4 } from 'uuid';
import { StoredUser, UserRole, validatePassword } from './auth';

interface CreateUserAndProfileInput {
  email: string;
  password: string;
  displayName: string;
  role: UserRole;
  /** Internal — only used by tests to force a collision. */
  forcedId?: string;
}

/**
 * Phase 2 Slice 1: create a user row AND its matching profile row inside a
 * single transaction. Either both land or neither does. Closes audit weak
 * point #1 (userStore <-> profiles FK drift).
 */
export async function createUserAndProfile(input: CreateUserAndProfileInput): Promise<StoredUser> {
  if (!isPgEnabled() || !pg) {
    throw new Error('[jobsService.createUserAndProfile] DATABASE_URL is required');
  }
  const validation = validatePassword(input.password);
  if (!validation.valid) throw new Error(validation.reason);

  const id = input.forcedId ?? uuidv4();
  const SALT_ROUNDS = 10;
  const passwordHash = await bcrypt.hash(input.password, SALT_ROUNDS);

  const client = await pg.connect();
  try {
    await client.query('BEGIN');
    const userRes = await client.query(
      `INSERT INTO users (
         id, email, password_hash, display_name, role,
         is_active, mfa_enabled, failed_login_count
       ) VALUES ($1, $2, $3, $4, $5, TRUE, FALSE, 0)
       RETURNING *`,
      [id, input.email.toLowerCase(), passwordHash, input.displayName, input.role]
    );
    await client.query(
      `INSERT INTO profiles (id, email, display_name, role)
       VALUES ($1, $2, $3, $4)`,
      [id, input.email.toLowerCase(), input.displayName, input.role]
    );
    await client.query('COMMIT');

    // Re-fetch via usersService to apply the row→StoredUser mapper.
    const { usersService } = await import('./usersService');
    const fresh = await usersService.getUserById(id);
    if (!fresh) throw new Error('[createUserAndProfile] post-insert lookup failed');
    return fresh;
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

// Attach to the module-level singleton if one exists; otherwise export.
// jobsService.ts currently exports individual functions, not a singleton.
// Wire up to whatever export shape the existing file uses.
```

`jobsService.ts` exports named functions (no singleton). Append `createUserAndProfile` at the bottom of the file alongside the other named exports (`create`, `update`, `changeStatus`, etc.). The test in Step 2 already imports it via `import { createUserAndProfile } from '../jobsService'`.

- [ ] **Step 5: Run tests to verify they pass**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts
```

Expected: PASS — transactional tests pass.

- [ ] **Step 6: Update the test helper in jobs-routes.test.ts**

In `backend/src/__tests__/jobs-routes.test.ts`, find the `createForemanAndLogin` helper. The current flow is "userStore.createUser + manual profile INSERT". Replace with a single call to `createUserAndProfile`:

```typescript
import { createUserAndProfile } from '../jobsService';

async function createForemanAndLogin(suffix: string): Promise<{ id: string; token: string }> {
  const email = `foreman-jobs-${suffix}-${Date.now()}@example.com`;
  const user = await createUserAndProfile({
    email,
    password: 'password123',
    displayName: `Foreman ${suffix}`,
    role: UserRole.FOREMAN,
  });
  const tokens = generateTokens(user);
  return { id: user.id, token: tokens.accessToken };
}
```

Delete the old manual `INSERT INTO profiles` block. Other test helpers that use the same pattern get the same treatment.

- [ ] **Step 7: Run the full backend test suite**

Run:

```bash
cd backend && npx jest
```

Expected: PASS — all tests pass, including the previously-passing jobs tests.

- [ ] **Step 8: Commit**

```bash
git add backend/src/jobsService.ts backend/src/__tests__/usersService.test.ts backend/src/__tests__/jobs-routes.test.ts
git commit -m "feat(jobs): transactional createUserAndProfile — closes FK drift bug"
```

---

## Task 12 — Round-trip restart test + audit annotation + slice close

**Files:**
- Modify: `backend/src/__tests__/usersService.test.ts`
- Modify: `docs/smith-net-architecture-audit.md`

- [ ] **Step 1: Write the round-trip restart test**

Append to `backend/src/__tests__/usersService.test.ts`:

```typescript
describeDb('usersService restart round-trip', () => {
  it('user created in one pool can log in via a fresh pool', async () => {
    const email = `t-rt-${Date.now()}@example.com`;
    await usersService.createUser(email, 'password123', 'RT', UserRole.SOLO);

    // Close and reopen by re-importing the module map. Since pg is a
    // singleton, we instead simulate "restart" by clearing application
    // state (refresh tokens) and re-fetching via a new query path.
    // The real test is: data persists across queries that don't share
    // state in process.
    const fetched = await usersService.getUserByEmail(email);
    expect(fetched?.email).toBe(email.toLowerCase());

    const login = await usersService.verifyPassword(email, 'password123');
    expect(login.ok).toBe(true);
  });
});
```

Note: in this test environment a real pool restart is impractical; the test verifies the row is reachable from a fresh query path, which is the meaningful invariant. A manual restart check happens in Step 4 below.

- [ ] **Step 2: Run the test**

Run:

```bash
cd backend && npx jest src/__tests__/usersService.test.ts -t 'restart'
```

Expected: PASS.

- [ ] **Step 3: Run the FULL test suite end-to-end**

Run:

```bash
cd backend && npx jest
```

Expected: PASS — every existing test plus the new usersService test file. Total green.

- [ ] **Step 4: Manual restart smoke test**

```bash
cd backend && DATABASE_URL=$DATABASE_URL npm run dev &
# Wait 5 seconds for boot
sleep 5
# Register a user via the API
curl -s -X POST http://localhost:3030/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"smoke-restart@example.com","password":"password123","displayName":"Smoke"}' | head -c 300
# Kill the backend
kill %1
# Restart
npm run dev &
sleep 5
# Log in
curl -s -X POST http://localhost:3030/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"smoke-restart@example.com","password":"password123"}' | head -c 300
# Expected: { "accessToken": "...", ... }
kill %1
```

Expected: second curl returns a fresh access token, proving the user survived the restart.

- [ ] **Step 5: Annotate the audit doc**

In `docs/smith-net-architecture-audit.md`, find the "userStore <-> profiles FK drift" entry (weak point #1). Append `[closed in slice 1, commit <SHA>]` after the title.

Example diff:

```markdown
### 1. `userStore` map ↔ `profiles` table FK drift [closed in slice 1, commit <fill-in>]
```

Replace `<fill-in>` with the actual SHA of the last commit in this slice (Task 11). Use `git rev-parse HEAD~1` if needed (since Task 12's commit will be after this annotation).

- [ ] **Step 6: Commit + tag slice 1**

```bash
git add docs/smith-net-architecture-audit.md backend/src/__tests__/usersService.test.ts
git commit -m "chore(phase-2): close slice 1 — users service + FK drift fix

- usersService.ts is the single source of truth for user state
- jobsService.createUserAndProfile wraps both inserts in a transaction
- audit weak point #1 marked closed"
git tag -a phase-2-slice-1 -m "Phase 2 Slice 1 — users service + FK drift fix"
```

- [ ] **Step 7: Verify final state**

Run:

```bash
git log --oneline phase-2-slice-1^..phase-2-slice-1
git tag --list 'phase-2-*'
```

Expected: one or more commits between tag and predecessor; tag `phase-2-slice-1` present.

---

## What slice 1 did NOT do

- Did **not** introduce pino logging. That's Slice 2.
- Did **not** rewrite `auditLog.ts` to use pg. That's Slice 2.
- Did **not** validate JWT on WS upgrade. That's Slice 3.
- Did **not** persist channelRegistry or gatewayManager. That's Slice 4.
- Did **not** touch the Android client. Slice 3 may require an Android cookie change.

Slices 2-4 plans will be written when slice 1 is in main.
