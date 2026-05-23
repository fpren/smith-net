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
import { requestLogger } from './log';
import {
  StoredUser,
  UserRole,
  LoginResult,
  validatePassword,
} from './auth';
import type { Tier } from './entitlements';
import { roleToTier } from './tierResolver';

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
  tier: string;
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
    tier: (r.tier as Tier) ?? 'open',
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

    // Tenant isolation slice 1: every new user starts as their own org of one.
    // Invite/join flow (which reassigns organization_id to a foreman's org)
    // is a follow-up plan; for now organization_id always equals id at create.
    const result = await db.query<UserRow>(
      `INSERT INTO users (
         id, email, password_hash, display_name, role, tier, organization_id,
         is_active, mfa_enabled, failed_login_count,
         email_verification_token, email_verification_expires_at
       ) VALUES ($1, $2, $3, $4, $5, $8, $1, TRUE, FALSE, 0, $6, $7)
       RETURNING *`,
      [id, email.toLowerCase(), passwordHash, displayName, role, token, expires, roleToTier(role)]
    );

    requestLogger().info({ event: 'user_created', email, role }, 'user created');
    return rowToUser(result.rows[0]);
  }

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
      tier: 'tier',
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

  async bootstrapAdmin(): Promise<void> {
    const db = requirePg();
    const rawPassword = process.env.DEFAULT_ADMIN_PASSWORD || 'admin123';
    const isDefault = !process.env.DEFAULT_ADMIN_PASSWORD;
    const passwordHash = await bcrypt.hash(rawPassword, SALT_ROUNDS);

    const result = await db.query(
      `INSERT INTO users (
         id, email, password_hash, display_name, role, tier, organization_id,
         is_active, mfa_enabled, failed_login_count,
         email_verified_at
       ) VALUES ('admin-001', 'admin@smithnet.local', $1, 'System Admin', 'admin', $2, 'admin-001',
                 TRUE, FALSE, 0, NOW())
       ON CONFLICT (id) DO NOTHING`,
      [passwordHash, roleToTier('admin')]
    );

    if ((result.rowCount ?? 0) > 0) {
      if (isDefault) {
        requestLogger().warn({ event: 'admin_bootstrap_default_password' }, 'admin bootstrapped with default password — set DEFAULT_ADMIN_PASSWORD for production');
      } else {
        requestLogger().info({ event: 'admin_bootstrap_env_password' }, 'admin bootstrapped from DEFAULT_ADMIN_PASSWORD env');
      }
    }
  }
}

export const usersService = new UsersService();

// Run admin bootstrap once at import. Idempotent — safe to import many times.
if (isPgEnabled()) {
  usersService.bootstrapAdmin().catch((err) => {
    requestLogger().error({ event: 'admin_bootstrap_failed', err }, 'admin bootstrap failed');
  });
}
