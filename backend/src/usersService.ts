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
