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
}

export const usersService = new UsersService();
