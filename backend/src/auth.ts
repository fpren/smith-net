/**
 * C-01: Authentication & Identity
 * C-02: Role Engine
 * 
 * JWT-based authentication with role-based access control.
 * Supports Solo → Team → Enterprise progression.
 */

import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { v4 as uuidv4 } from 'uuid';
import { Request, Response, NextFunction } from 'express';

// ════════════════════════════════════════════════════════════════════
// CONFIGURATION
// ════════════════════════════════════════════════════════════════════

const JWT_SECRET = process.env.JWT_SECRET || 'smith-net-dev-secret-change-in-production';
const JWT_EXPIRES_IN = '7d';
const REFRESH_TOKEN_EXPIRES_IN = '30d';
const SALT_ROUNDS = 10;

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
}

export interface PublicUser {
  id: string;
  email: string;
  displayName: string;
  role: UserRole;
  organizationId?: string;
  permissions: Permission[];
}

export interface TokenPayload {
  userId: string;
  email: string;
  role: UserRole;
  type: 'access' | 'refresh';
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

// ════════════════════════════════════════════════════════════════════
// IN-MEMORY USER STORE (Replace with DB in production)
// ════════════════════════════════════════════════════════════════════

class UserStore {
  private users: Map<string, StoredUser> = new Map();
  private emailIndex: Map<string, string> = new Map(); // email -> id
  private refreshTokens: Map<string, string> = new Map(); // token -> userId

  constructor() {
    // Create default admin user
    this.createDefaultAdmin();
  }

  private async createDefaultAdmin() {
    const adminId = 'admin-001';
    const passwordHash = await bcrypt.hash('admin123', SALT_ROUNDS);
    
    const admin: StoredUser = {
      id: adminId,
      email: 'admin@smithnet.local',
      passwordHash,
      displayName: 'System Admin',
      role: UserRole.ADMIN,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      isActive: true,
      mfaEnabled: false,
    };
    
    this.users.set(adminId, admin);
    this.emailIndex.set(admin.email, adminId);
    console.log('[Auth] Default admin user created: admin@smithnet.local / admin123');
  }

  async createUser(
    email: string,
    password: string,
    displayName: string,
    role: UserRole = UserRole.SOLO
  ): Promise<StoredUser> {
    // Check if email exists
    if (this.emailIndex.has(email.toLowerCase())) {
      throw new Error('Email already registered');
    }

    const id = uuidv4();
    const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);

    const user: StoredUser = {
      id,
      email: email.toLowerCase(),
      passwordHash,
      displayName,
      role,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      isActive: true,
      mfaEnabled: false,
    };

    this.users.set(id, user);
    this.emailIndex.set(email.toLowerCase(), id);

    console.log(`[Auth] User created: ${email} (${role})`);
    return user;
  }

  async verifyPassword(email: string, password: string): Promise<StoredUser | null> {
    const userId = this.emailIndex.get(email.toLowerCase());
    if (!userId) return null;

    const user = this.users.get(userId);
    if (!user || !user.isActive) return null;

    const isValid = await bcrypt.compare(password, user.passwordHash);
    if (!isValid) return null;

    // Update last login
    user.lastLoginAt = Date.now();
    this.users.set(userId, user);

    return user;
  }

  getUserById(id: string): StoredUser | undefined {
    return this.users.get(id);
  }

  getUserByEmail(email: string): StoredUser | undefined {
    const userId = this.emailIndex.get(email.toLowerCase());
    return userId ? this.users.get(userId) : undefined;
  }

  updateUser(id: string, updates: Partial<StoredUser>): StoredUser | undefined {
    const user = this.users.get(id);
    if (!user) return undefined;

    const updated = { ...user, ...updates, updatedAt: Date.now() };
    this.users.set(id, updated);
    return updated;
  }

  storeRefreshToken(token: string, userId: string) {
    this.refreshTokens.set(token, userId);
  }

  validateRefreshToken(token: string): string | undefined {
    return this.refreshTokens.get(token);
  }

  revokeRefreshToken(token: string) {
    this.refreshTokens.delete(token);
  }

  getAllUsers(): StoredUser[] {
    return Array.from(this.users.values());
  }
}

export const userStore = new UserStore();

// ════════════════════════════════════════════════════════════════════
// TOKEN MANAGEMENT
// ════════════════════════════════════════════════════════════════════

export function generateTokens(user: StoredUser): AuthTokens {
  const accessPayload: TokenPayload = {
    userId: user.id,
    email: user.email,
    role: user.role,
    type: 'access',
  };

  const refreshPayload: TokenPayload = {
    userId: user.id,
    email: user.email,
    role: user.role,
    type: 'refresh',
  };

  const accessToken = jwt.sign(accessPayload, JWT_SECRET, { expiresIn: JWT_EXPIRES_IN });
  const refreshToken = jwt.sign(refreshPayload, JWT_SECRET, { expiresIn: REFRESH_TOKEN_EXPIRES_IN });

  // Store refresh token
  userStore.storeRefreshToken(refreshToken, user.id);

  return {
    accessToken,
    refreshToken,
    expiresIn: 7 * 24 * 60 * 60, // 7 days in seconds
  };
}

export function verifyToken(token: string): TokenPayload | null {
  try {
    const payload = jwt.verify(token, JWT_SECRET) as TokenPayload;
    return payload;
  } catch (e) {
    return null;
  }
}

export function refreshAccessToken(refreshToken: string): AuthTokens | null {
  const payload = verifyToken(refreshToken);
  if (!payload || payload.type !== 'refresh') return null;

  // Verify refresh token is still valid in store
  const storedUserId = userStore.validateRefreshToken(refreshToken);
  if (storedUserId !== payload.userId) return null;

  const user = userStore.getUserById(payload.userId);
  if (!user || !user.isActive) return null;

  // Revoke old refresh token and generate new ones
  userStore.revokeRefreshToken(refreshToken);
  return generateTokens(user);
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
export function authenticateToken(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  const authHeader = req.headers.authorization;
  const token = authHeader?.startsWith('Bearer ') ? authHeader.slice(7) : null;

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

/**
 * Middleware to optionally authenticate - doesn't fail if no token.
 */
export function optionalAuth(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  const authHeader = req.headers.authorization;
  const token = authHeader?.startsWith('Bearer ') ? authHeader.slice(7) : null;

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
