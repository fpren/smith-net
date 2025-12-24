/**
 * Authentication API Routes
 * Register, Login, Token Refresh, Profile
 */

import { Router, Response } from 'express';
import {
  userStore,
  generateTokens,
  refreshAccessToken,
  toPublicUser,
  authenticateToken,
  requirePermission,
  requireRole,
  AuthenticatedRequest,
  UserRole,
  Permission,
} from './auth';
import { auditLog, AuditAction } from './auditLog';

export const authRouter = Router();

// ════════════════════════════════════════════════════════════════════
// REGISTER
// ════════════════════════════════════════════════════════════════════

authRouter.post('/register', async (req, res) => {
  try {
    const { email, password, displayName } = req.body;

    if (!email || !password || !displayName) {
      return res.status(400).json({ error: 'Email, password, and displayName are required' });
    }

    if (password.length < 6) {
      return res.status(400).json({ error: 'Password must be at least 6 characters' });
    }

    // Create user with default Solo role
    const user = await userStore.createUser(email, password, displayName, UserRole.SOLO);
    const tokens = generateTokens(user);

    // Audit log
    auditLog.log(AuditAction.USER_REGISTER, user.id, { email });

    res.status(201).json({
      user: toPublicUser(user),
      ...tokens,
    });
  } catch (e: any) {
    console.error('[Auth] Register error:', e.message);
    res.status(400).json({ error: e.message });
  }
});

// ════════════════════════════════════════════════════════════════════
// LOGIN
// ════════════════════════════════════════════════════════════════════

authRouter.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required' });
    }

    const user = await userStore.verifyPassword(email, password);
    if (!user) {
      auditLog.log(AuditAction.USER_LOGIN_FAILED, 'unknown', { email });
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    const tokens = generateTokens(user);

    // Audit log
    auditLog.log(AuditAction.USER_LOGIN, user.id, { email });

    res.json({
      user: toPublicUser(user),
      ...tokens,
    });
  } catch (e: any) {
    console.error('[Auth] Login error:', e.message);
    res.status(500).json({ error: 'Login failed' });
  }
});

// ════════════════════════════════════════════════════════════════════
// REFRESH TOKEN
// ════════════════════════════════════════════════════════════════════

authRouter.post('/refresh', (req, res) => {
  try {
    const { refreshToken } = req.body;

    if (!refreshToken) {
      return res.status(400).json({ error: 'Refresh token is required' });
    }

    const tokens = refreshAccessToken(refreshToken);
    if (!tokens) {
      return res.status(401).json({ error: 'Invalid or expired refresh token' });
    }

    res.json(tokens);
  } catch (e: any) {
    console.error('[Auth] Refresh error:', e.message);
    res.status(500).json({ error: 'Token refresh failed' });
  }
});

// ════════════════════════════════════════════════════════════════════
// GET CURRENT USER PROFILE
// ════════════════════════════════════════════════════════════════════

authRouter.get('/me', authenticateToken, (req: AuthenticatedRequest, res: Response) => {
  res.json({ user: req.user });
});

// ════════════════════════════════════════════════════════════════════
// UPDATE PROFILE
// ════════════════════════════════════════════════════════════════════

authRouter.patch('/me', authenticateToken, (req: AuthenticatedRequest, res: Response) => {
  try {
    const { displayName } = req.body;
    const userId = req.user!.id;

    const updates: any = {};
    if (displayName) updates.displayName = displayName;

    const updated = userStore.updateUser(userId, updates);
    if (!updated) {
      return res.status(404).json({ error: 'User not found' });
    }

    auditLog.log(AuditAction.USER_PROFILE_UPDATE, userId, { updates });

    res.json({ user: toPublicUser(updated) });
  } catch (e: any) {
    console.error('[Auth] Update profile error:', e.message);
    res.status(500).json({ error: 'Update failed' });
  }
});

// ════════════════════════════════════════════════════════════════════
// LOGOUT (Revoke refresh token)
// ════════════════════════════════════════════════════════════════════

authRouter.post('/logout', authenticateToken, (req: AuthenticatedRequest, res: Response) => {
  const { refreshToken } = req.body;
  
  if (refreshToken) {
    userStore.revokeRefreshToken(refreshToken);
  }

  auditLog.log(AuditAction.USER_LOGOUT, req.user!.id, {});

  res.json({ success: true });
});

// ════════════════════════════════════════════════════════════════════
// ADMIN: LIST USERS
// ════════════════════════════════════════════════════════════════════

authRouter.get(
  '/users',
  authenticateToken,
  requirePermission(Permission.MANAGE_USERS),
  (req: AuthenticatedRequest, res: Response) => {
    const users = userStore.getAllUsers().map(toPublicUser);
    res.json({ users });
  }
);

// ════════════════════════════════════════════════════════════════════
// ADMIN: UPDATE USER ROLE
// ════════════════════════════════════════════════════════════════════

authRouter.patch(
  '/users/:userId/role',
  authenticateToken,
  requirePermission(Permission.MANAGE_ROLES),
  (req: AuthenticatedRequest, res: Response) => {
    try {
      const { userId } = req.params;
      const { role } = req.body;

      if (!Object.values(UserRole).includes(role)) {
        return res.status(400).json({ error: 'Invalid role' });
      }

      // Prevent demoting self
      if (userId === req.user!.id && role !== req.user!.role) {
        return res.status(400).json({ error: 'Cannot change your own role' });
      }

      const updated = userStore.updateUser(userId, { role });
      if (!updated) {
        return res.status(404).json({ error: 'User not found' });
      }

      auditLog.log(AuditAction.USER_ROLE_CHANGE, req.user!.id, {
        targetUserId: userId,
        newRole: role,
      });

      res.json({ user: toPublicUser(updated) });
    } catch (e: any) {
      console.error('[Auth] Update role error:', e.message);
      res.status(500).json({ error: 'Update failed' });
    }
  }
);

console.log('[Auth] Auth routes initialized');
