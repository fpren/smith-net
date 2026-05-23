/**
 * C-01: Authentication & Identity
 * C-02: Role Engine
 * 
 * JWT-based authentication with role-based access control.
 * Supports Solo → Team → Enterprise progression.
 */

import jwt from 'jsonwebtoken';
import { Request, Response, NextFunction } from 'express';
import { resolveEntitlements } from './tierResolver';
import type { Tier } from './entitlements';

// ════════════════════════════════════════════════════════════════════
// CONFIGURATION
// ════════════════════════════════════════════════════════════════════

// F1.2: hard-fail at boot if JWT_SECRET is missing, weak, or the dev fallback in prod.
// Tokens minted with a guessable secret are forgeable, so we refuse to start rather than
// silently issue insecure tokens. Dev still gets a usable fallback when NODE_ENV !== 'production'.
const DEV_JWT_FALLBACK = 'smith-net-dev-secret-change-in-production';
const IS_PRODUCTION = process.env.NODE_ENV === 'production';

function resolveJwtSecret(): string {
  const fromEnv = process.env.JWT_SECRET;

  if (!fromEnv) {
    if (IS_PRODUCTION) {
      throw new Error('[FATAL] JWT_SECRET env var is required in production. See docs/ops/SECRETS.md.');
    }
    console.warn('[Auth] JWT_SECRET unset — using dev fallback. NEVER deploy this way.');
    return DEV_JWT_FALLBACK;
  }

  if (IS_PRODUCTION && fromEnv === DEV_JWT_FALLBACK) {
    throw new Error('[FATAL] JWT_SECRET is set to the dev fallback in production. Generate a real secret (see docs/ops/SECRETS.md).');
  }

  if (fromEnv.length < 32) {
    throw new Error(`[FATAL] JWT_SECRET must be at least 32 characters (got ${fromEnv.length}). See docs/ops/SECRETS.md.`);
  }

  return fromEnv;
}

const JWT_SECRET = resolveJwtSecret();
// Refresh tokens get a separate secret when provided, so rotating the access secret
// doesn't invalidate every active refresh token (and vice versa). Falls back to JWT_SECRET.
const REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || JWT_SECRET;
const JWT_EXPIRES_IN = '7d';
const REFRESH_TOKEN_EXPIRES_IN = '30d';
const SALT_ROUNDS = 10;

// F1.3: password floor + per-account lockout policy.
const MIN_PASSWORD_LENGTH = 8;
const MAX_FAILED_LOGINS = 5;
const LOCKOUT_DURATION_MS = 15 * 60 * 1000;
// Constant-time delay for the "no such user" path so attackers can't enumerate
// emails by timing the difference between bcrypt-verify and a fast 401.
const ENUMERATION_DELAY_MS = 200;

export type PasswordValidationResult =
  | { valid: true }
  | { valid: false; reason: string };

export function validatePassword(password: string): PasswordValidationResult {
  if (typeof password !== 'string' || password.length < MIN_PASSWORD_LENGTH) {
    return { valid: false, reason: `Password must be at least ${MIN_PASSWORD_LENGTH} characters` };
  }
  if (!/[a-zA-Z]/.test(password)) {
    return { valid: false, reason: 'Password must contain at least one letter' };
  }
  if (!/[0-9]/.test(password)) {
    return { valid: false, reason: 'Password must contain at least one digit' };
  }
  return { valid: true };
}

export type LoginResult =
  | { ok: true; user: StoredUser }
  | { ok: false; reason: 'invalid_credentials' }
  | { ok: false; reason: 'locked'; retryMinutes: number };

// F1.4: email verification token TTL + resend throttle.
export const EMAIL_VERIFICATION_TTL_MS = 24 * 60 * 60 * 1000; // 24h
export const EMAIL_RESEND_COOLDOWN_MS = 60 * 1000; // 1 send per minute per account

// ════════════════════════════════════════════════════════════════════
// C-02: ROLE DEFINITIONS
// ════════════════════════════════════════════════════════════════════

export enum UserRole {
  SOLO = 'solo',           // Individual user - basic features
  TEAM_MEMBER = 'team',    // Team member - can join orgs
  TEAM_LEAD = 'lead',      // Team lead - can manage team
  FOREMAN = 'foreman',     // Foreman - full team management
  ENTERPRISE = 'enterprise', // Enterprise admin
  ADMIN = 'admin'          // System admin
}

export enum Permission {
  // Messaging
  SEND_MESSAGE = 'send_message',
  DELETE_OWN_MESSAGE = 'delete_own_message',
  DELETE_ANY_MESSAGE = 'delete_any_message',
  
  // Channels
  CREATE_CHANNEL = 'create_channel',
  DELETE_CHANNEL = 'delete_channel',
  MANAGE_CHANNEL_MEMBERS = 'manage_channel_members',
  CLEAR_CHANNEL = 'clear_channel',
  
  // Media
  SEND_MEDIA = 'send_media',
  
  // Mesh
  USE_MESH = 'use_mesh',
  GATEWAY_RELAY = 'gateway_relay',
  
  // Admin
  MANAGE_USERS = 'manage_users',
  VIEW_AUDIT_LOGS = 'view_audit_logs',
  MANAGE_ROLES = 'manage_roles',
  
  // Organization
  CREATE_ORG = 'create_org',
  MANAGE_ORG = 'manage_org',
  INVITE_MEMBERS = 'invite_members'
}

// Role → Permissions mapping
const ROLE_PERMISSIONS: Record<UserRole, Permission[]> = {
  [UserRole.SOLO]: [
    Permission.SEND_MESSAGE,
    Permission.DELETE_OWN_MESSAGE,
    Permission.CREATE_CHANNEL,
    Permission.SEND_MEDIA,
    Permission.USE_MESH,
  ],
  [UserRole.TEAM_MEMBER]: [
    Permission.SEND_MESSAGE,
    Permission.DELETE_OWN_MESSAGE,
    Permission.CREATE_CHANNEL,
    Permission.SEND_MEDIA,
    Permission.USE_MESH,
  ],
  [UserRole.TEAM_LEAD]: [
    Permission.SEND_MESSAGE,
    Permission.DELETE_OWN_MESSAGE,
    Permission.DELETE_ANY_MESSAGE,
    Permission.CREATE_CHANNEL,
    Permission.MANAGE_CHANNEL_MEMBERS,
    Permission.CLEAR_CHANNEL,
    Permission.SEND_MEDIA,
    Permission.USE_MESH,
    Permission.INVITE_MEMBERS,
  ],
  [UserRole.FOREMAN]: [
    Permission.SEND_MESSAGE,
    Permission.DELETE_OWN_MESSAGE,
    Permission.DELETE_ANY_MESSAGE,
    Permission.CREATE_CHANNEL,
    Permission.DELETE_CHANNEL,
    Permission.MANAGE_CHANNEL_MEMBERS,
    Permission.CLEAR_CHANNEL,
    Permission.SEND_MEDIA,
    Permission.USE_MESH,
    Permission.GATEWAY_RELAY,
    Permission.INVITE_MEMBERS,
    Permission.VIEW_AUDIT_LOGS,
  ],
  [UserRole.ENTERPRISE]: [
    Permission.SEND_MESSAGE,
    Permission.DELETE_OWN_MESSAGE,
    Permission.DELETE_ANY_MESSAGE,
    Permission.CREATE_CHANNEL,
    Permission.DELETE_CHANNEL,
    Permission.MANAGE_CHANNEL_MEMBERS,
    Permission.CLEAR_CHANNEL,
    Permission.SEND_MEDIA,
    Permission.USE_MESH,
    Permission.GATEWAY_RELAY,
    Permission.MANAGE_USERS,
    Permission.VIEW_AUDIT_LOGS,
    Permission.CREATE_ORG,
    Permission.MANAGE_ORG,
    Permission.INVITE_MEMBERS,
  ],
  [UserRole.ADMIN]: Object.values(Permission), // All permissions
};

// ════════════════════════════════════════════════════════════════════
// USER MODEL
// ════════════════════════════════════════════════════════════════════

export interface StoredUser {
  id: string;
  email: string;
  passwordHash: string;
  displayName: string;
  role: UserRole;
  organizationId?: string;
  createdAt: number;
  updatedAt: number;
  lastLoginAt?: number;
  isActive: boolean;
  mfaEnabled: boolean;
  mfaSecret?: string;
  // F1.3 lockout state. When userStore moves to Postgres, these map to
  // failed_login_count INT NOT NULL DEFAULT 0 and locked_until TIMESTAMPTZ.
  failedLoginCount?: number;
  lockedUntil?: number; // epoch ms; absent = not locked
  // F1.4 email verification state. Maps to:
  // email_verified_at TIMESTAMPTZ, email_verification_token TEXT,
  // email_verification_expires_at TIMESTAMPTZ, email_verification_last_sent_at TIMESTAMPTZ.
  emailVerifiedAt?: number;
  emailVerificationToken?: string;
  emailVerificationExpiresAt?: number;
  emailVerificationLastSentAt?: number;
}

export interface PublicUser {
  id: string;
  email: string;
  displayName: string;
  role: UserRole;
  organizationId?: string;
  permissions: Permission[];
  // F1.4: clients use this to render the "verify your email" banner and gate UX.
  emailVerified: boolean;
}

export interface TokenPayload {
  userId: string;
  email: string;
  role: UserRole;
  type: 'access' | 'refresh';
  tier?: Tier;
  entitlementsHash?: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

// ════════════════════════════════════════════════════════════════════
// USER STORE — pg-backed via usersService (Phase 2 Slice 1)
// ════════════════════════════════════════════════════════════════════
// The original in-memory UserStore class moved to usersService.ts in
// Phase 2 Slice 1. Existing callers continue to use `userStore.method()`
// but every call is now async and hits Postgres.

import { usersService } from './usersService';
export const userStore = usersService;

// ════════════════════════════════════════════════════════════════════
// TOKEN MANAGEMENT
// ════════════════════════════════════════════════════════════════════

export async function generateTokens(user: StoredUser): Promise<AuthTokens> {
  const ent = resolveEntitlements(user.role);
  const accessPayload: TokenPayload = {
    userId: user.id,
    email: user.email,
    role: user.role,
    type: 'access',
    tier: ent.tier,
    entitlementsHash: ent.entitlementsHash,
  };

  const refreshPayload: TokenPayload = {
    userId: user.id,
    email: user.email,
    role: user.role,
    type: 'refresh',
  };

  const accessToken = jwt.sign(accessPayload, JWT_SECRET, { expiresIn: JWT_EXPIRES_IN });
  const refreshToken = jwt.sign(refreshPayload, REFRESH_SECRET, { expiresIn: REFRESH_TOKEN_EXPIRES_IN });

  // Store refresh token (now hits Postgres via usersService)
  await userStore.storeRefreshToken(refreshToken, user.id);

  return {
    accessToken,
    refreshToken,
    expiresIn: 7 * 24 * 60 * 60, // 7 days in seconds
  };
}

export function verifyToken(token: string): TokenPayload | null {
  // Try the access secret first (the common case), then the refresh secret.
  // When JWT_REFRESH_SECRET is unset both are the same constant, so this is a no-op cost.
  try {
    return jwt.verify(token, JWT_SECRET) as TokenPayload;
  } catch {
    if (REFRESH_SECRET === JWT_SECRET) return null;
    try {
      return jwt.verify(token, REFRESH_SECRET) as TokenPayload;
    } catch {
      return null;
    }
  }
}

export async function refreshAccessToken(refreshToken: string): Promise<AuthTokens | null> {
  const payload = verifyToken(refreshToken);
  if (!payload || payload.type !== 'refresh') return null;

  // Verify refresh token is still valid in store
  const storedUserId = await userStore.validateRefreshToken(refreshToken);
  if (storedUserId !== payload.userId) return null;

  const user = await userStore.getUserById(payload.userId);
  if (!user || !user.isActive) return null;

  // Revoke old refresh token and generate new ones
  await userStore.revokeRefreshToken(refreshToken);
  return generateTokens(user);
}

// ════════════════════════════════════════════════════════════════════
// COOKIE HELPERS (for browser clients — operator console)
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
  };

  res.cookie(ACCESS_COOKIE_NAME, tokens.accessToken, {
    ...baseOpts,
    path: '/api',
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

// ════════════════════════════════════════════════════════════════════
// PERMISSION HELPERS
// ════════════════════════════════════════════════════════════════════

export function getRolePermissions(role: UserRole): Permission[] {
  return ROLE_PERMISSIONS[role] || [];
}

export function hasPermission(user: PublicUser | StoredUser, permission: Permission): boolean {
  const permissions = ROLE_PERMISSIONS[user.role];
  return permissions?.includes(permission) ?? false;
}

export function toPublicUser(user: StoredUser): PublicUser {
  return {
    id: user.id,
    email: user.email,
    displayName: user.displayName,
    role: user.role,
    organizationId: user.organizationId,
    permissions: getRolePermissions(user.role),
    emailVerified: !!user.emailVerifiedAt,
  };
}

// ════════════════════════════════════════════════════════════════════
// EXPRESS MIDDLEWARE
// ════════════════════════════════════════════════════════════════════

export interface AuthenticatedRequest extends Request {
  user?: PublicUser;
  token?: string;
}

/**
 * Middleware to authenticate JWT token.
 * Adds user to request if valid, otherwise returns 401.
 */
export async function authenticateToken(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  const authHeader = req.headers.authorization;
  const headerToken = authHeader?.startsWith('Bearer ') ? authHeader.slice(7) : null;
  // Browser clients use the httpOnly cookie; Android uses the Bearer header. Either is fine.
  const cookieToken = req.cookies?.smithnet_access || null;
  const token = headerToken || cookieToken;

  if (!token) {
    return res.status(401).json({ error: 'No token provided' });
  }

  const payload = verifyToken(token);
  if (!payload || payload.type !== 'access') {
    return res.status(401).json({ error: 'Invalid or expired token' });
  }

  const user = await userStore.getUserById(payload.userId);
  if (!user || !user.isActive) {
    return res.status(401).json({ error: 'User not found or inactive' });
  }

  req.user = toPublicUser(user);
  req.token = token;
  next();
}

/**
 * Middleware to optionally authenticate - doesn't fail if no token.
 */
export async function optionalAuth(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  const authHeader = req.headers.authorization;
  const headerToken = authHeader?.startsWith('Bearer ') ? authHeader.slice(7) : null;
  const cookieToken = req.cookies?.smithnet_access || null;
  const token = headerToken || cookieToken;

  if (token) {
    const payload = verifyToken(token);
    if (payload && payload.type === 'access') {
      const user = await userStore.getUserById(payload.userId);
      if (user && user.isActive) {
        req.user = toPublicUser(user);
        req.token = token;
      }
    }
  }

  next();
}

/**
 * F1.4: gate routes that should be reachable only by users with verified email.
 * Apply downstream of authenticateToken. Currently exported for use by future
 * routes (start-trial, invoice send, proposal send) — see F1.4 PRD §4.4.
 */
export function requireVerifiedEmail(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  if (!req.user) {
    return res.status(401).json({ error: 'Authentication required' });
  }
  if (!req.user.emailVerified) {
    return res.status(403).json({
      error: 'Email not verified',
      code: 'email_not_verified',
    });
  }
  next();
}

/**
 * Middleware to require specific permission.
 */
export function requirePermission(permission: Permission) {
  return (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    if (!req.user) {
      return res.status(401).json({ error: 'Authentication required' });
    }

    if (!hasPermission(req.user, permission)) {
      return res.status(403).json({ error: 'Insufficient permissions' });
    }

    next();
  };
}

/**
 * Middleware to require specific role or higher.
 */
export function requireRole(...roles: UserRole[]) {
  return (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    if (!req.user) {
      return res.status(401).json({ error: 'Authentication required' });
    }

    if (!roles.includes(req.user.role)) {
      return res.status(403).json({ error: 'Insufficient role' });
    }

    next();
  };
}

console.log('[Auth] Authentication module initialized');
