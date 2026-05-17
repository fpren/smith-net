/**
 * Authentication API Routes
 * Register, Login, Token Refresh, Profile
 */

import { Router, Response } from 'express';
import path from 'path';
import fs from 'fs';
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
import { createUserAndProfile } from './jobsService';
import { auditLog, AuditAction } from './auditLog';
import { isEmailLive } from './emailService';
import { enqueue } from './queue/queue';
import { requestLogger } from './log';
import { validateBody, validateQuery } from './middleware/validate';
import {
  RegisterBody,
  LoginBody,
  RefreshBody,
  VerifyQuery,
  ResendVerificationBody,
  UpdateProfileBody,
  LogoutBody,
  UpdateUserRoleBody,
} from './schemas/auth';

// F1.4: where the user lands when they click the verify link in an email.
// Defaults to the dev backend; in prod set PUBLIC_BASE_URL=<Tailscale Funnel URL>
// in the systemd .env so the email points at the publicly reachable host.
const PUBLIC_BASE_URL = process.env.PUBLIC_BASE_URL || `http://localhost:${process.env.PORT || 3030}`;

// Phase 3 Slice 3: helpers above (buildVerificationLink, buildVerificationEmail,
// sendVerificationEmail) moved into workers/emailWorker.ts. Routes now enqueue
// a kind='email' job and return; the worker builds the body and calls SMTP.

async function enqueueVerificationEmail(to: string, displayName: string, token: string, userId: string): Promise<void> {
  await enqueue({
    kind: 'email',
    dedupeKey: `email:verify:${userId}:${token}`,
    payload: {
      subkind: 'verification',
      to,
      displayName,
      token,
      baseUrl: PUBLIC_BASE_URL,
    },
  });
}

function renderVerifyFailed(res: Response, status: number, reason: string): void {
  try {
    const tplPath = path.join(__dirname, 'templates', 'verify-failed.html');
    const html = fs.readFileSync(tplPath, 'utf8').replace('{{reason}}', reason);
    res.status(status).type('html').send(html);
  } catch {
    res.status(status).type('text').send(`Verification failed: ${reason}`);
  }
}

export const authRouter = Router();

// ════════════════════════════════════════════════════════════════════
// REGISTER
// ════════════════════════════════════════════════════════════════════

authRouter.post('/register', validateBody(RegisterBody), async (req, res) => {
  try {
    // Body shape + email format already validated by zod; password POLICY
    // (≥8, letter, digit) is a separate concern with its own error code.
    const { email, password, displayName } = req.body as RegisterBody;

    const passwordCheck = validatePassword(password);
    if (!passwordCheck.valid) {
      return res.status(400).json({ error: passwordCheck.reason, code: 'weak_password' });
    }

    // Phase 3.5 follow-up: use the transactional helper so the profiles row
    // lands atomically with the users row. Without this, the register handler
    // would only INSERT into users and downstream features that FK to
    // profiles(id) — shifts, jobs.foreman_id — would 500 on first use.
    const user = await createUserAndProfile({ email, password, displayName, role: UserRole.SOLO });
    const tokens = await generateTokens(user);

    // Audit log
    await auditLog.log(AuditAction.USER_REGISTER, user.id, { email });

    // F1.4: send verification email (non-blocking — failures don't fail register).
    // The token was generated inside createUser; mark the send-attempt timestamp
    // so the resend cooldown applies even to this initial send.
    if (user.emailVerificationToken) {
      await userStore.recordVerificationSendAttempt(user.id);
      await enqueueVerificationEmail(user.email, user.displayName, user.emailVerificationToken, user.id);
    }

    setAuthCookies(res, tokens);

    res.status(201).json({
      user: toPublicUser(user),
      ...tokens,
      requiresEmailVerification: true,
    });
  } catch (e: any) {
    requestLogger().error({ event: 'register_error', err: e }, 'register error');
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

    const result = await userStore.verifyPassword(email, password);

    if (!result.ok) {
      if (result.reason === 'locked') {
        await auditLog.log(AuditAction.SECURITY_ALERT, 'unknown', {
          event: 'login_blocked_locked',
          email,
        });
        return res.status(429).json({
          error: 'Account temporarily locked',
          code: 'account_locked',
          retry_after_minutes: result.retryMinutes,
        });
      }
      await auditLog.log(AuditAction.USER_LOGIN_FAILED, 'unknown', { email });
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    const tokens = await generateTokens(result.user);

    // Audit log
    await auditLog.log(AuditAction.USER_LOGIN, result.user.id, { email });

    setAuthCookies(res, tokens);

    res.json({
      user: toPublicUser(result.user),
      ...tokens,
    });
  } catch (e: any) {
    requestLogger().error({ event: 'login_error', err: e }, 'login error');
    res.status(500).json({ error: 'Login failed' });
  }
});

// ════════════════════════════════════════════════════════════════════
// REFRESH TOKEN
// ════════════════════════════════════════════════════════════════════

authRouter.post('/refresh', async (req, res) => {
  try {
    const { refreshToken } = req.body;

    if (!refreshToken) {
      return res.status(400).json({ error: 'Refresh token is required' });
    }

    const tokens = await refreshAccessToken(refreshToken);
    if (!tokens) {
      return res.status(401).json({ error: 'Invalid or expired refresh token' });
    }

    setAuthCookies(res, tokens);

    res.json(tokens);
  } catch (e: any) {
    requestLogger().error({ event: 'refresh_error', err: e }, 'refresh error');
    res.status(500).json({ error: 'Token refresh failed' });
  }
});

// ════════════════════════════════════════════════════════════════════
// F1.4: VERIFY EMAIL (clicked from email; renders an HTML page)
// ════════════════════════════════════════════════════════════════════

authRouter.get('/verify', async (req, res) => {
  const token = typeof req.query.token === 'string' ? req.query.token : '';

  if (!token) {
    return renderVerifyFailed(res, 400, 'Missing verification token.');
  }

  const user = await userStore.findByVerificationToken(token);
  if (!user) {
    // Could be: expired, already-consumed, or never existed. We don't
    // distinguish — disclosing "already used" leaks that the email exists.
    return renderVerifyFailed(res, 400, 'This link is invalid or expired. Request a new one from the app.');
  }

  await userStore.markEmailVerified(user.id);
  await auditLog.log(AuditAction.USER_PROFILE_UPDATE, user.id, { event: 'email_verified' });

  try {
    const tplPath = path.join(__dirname, 'templates', 'verified.html');
    const html = fs.readFileSync(tplPath, 'utf8');
    res.type('html').send(html);
  } catch {
    res.type('text').send('Email verified.');
  }
});

// ════════════════════════════════════════════════════════════════════
// F1.4: RESEND VERIFICATION (authenticated; rate-limited per-account)
// ════════════════════════════════════════════════════════════════════

authRouter.post('/resend-verification', authenticateToken, async (req: AuthenticatedRequest, res) => {
  const userId = req.user!.id;
  const stored = await userStore.getUserById(userId);
  if (!stored) return res.status(404).json({ error: 'User not found' });

  // Already verified? Treat as success no-op so we don't leak state.
  if (stored.emailVerifiedAt) {
    return res.json({ ok: true, alreadyVerified: true });
  }

  // Per-account cooldown — defends both against accidental double-tap and
  // intentional spam. Uses lastSentAt set by recordVerificationSendAttempt /
  // regenerateVerificationToken.
  const lastSent = stored.emailVerificationLastSentAt ?? 0;
  const elapsed = Date.now() - lastSent;
  if (elapsed < EMAIL_RESEND_COOLDOWN_MS) {
    const retryAfterSeconds = Math.ceil((EMAIL_RESEND_COOLDOWN_MS - elapsed) / 1000);
    return res.status(429).json({
      error: 'Too many resend requests',
      code: 'resend_throttled',
      retry_after_seconds: retryAfterSeconds,
    });
  }

  const newToken = await userStore.regenerateVerificationToken(userId);
  if (!newToken) {
    // Race: verified between the early-return and now.
    return res.json({ ok: true, alreadyVerified: true });
  }

  await enqueueVerificationEmail(stored.email, stored.displayName, newToken, userId);
  await auditLog.log(AuditAction.USER_PROFILE_UPDATE, userId, { event: 'verification_resent' });

  res.json({ ok: true, dryRun: !isEmailLive() });
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

authRouter.patch('/me', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  try {
    const { displayName } = req.body;
    const userId = req.user!.id;

    const updates: any = {};
    if (displayName) updates.displayName = displayName;

    const updated = await userStore.updateUser(userId, updates);
    if (!updated) {
      return res.status(404).json({ error: 'User not found' });
    }

    await auditLog.log(AuditAction.USER_PROFILE_UPDATE, userId, { updates });

    res.json({ user: toPublicUser(updated) });
  } catch (e: any) {
    requestLogger().error({ event: 'update_profile_error', err: e }, 'update profile error');
    res.status(500).json({ error: 'Update failed' });
  }
});

// ════════════════════════════════════════════════════════════════════
// LOGOUT (Revoke refresh token)
// ════════════════════════════════════════════════════════════════════

authRouter.post('/logout', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  const { refreshToken } = req.body;

  if (refreshToken) {
    await userStore.revokeRefreshToken(refreshToken);
  }

  await auditLog.log(AuditAction.USER_LOGOUT, req.user!.id, {});

  clearAuthCookies(res);

  res.json({ success: true });
});

// ════════════════════════════════════════════════════════════════════
// ADMIN: LIST USERS
// ════════════════════════════════════════════════════════════════════

authRouter.get(
  '/users',
  authenticateToken,
  requirePermission(Permission.MANAGE_USERS),
  async (req: AuthenticatedRequest, res: Response) => {
    const users = (await userStore.getAllUsers()).map(toPublicUser);
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
  async (req: AuthenticatedRequest, res: Response) => {
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

      const updated = await userStore.updateUser(userId, { role });
      if (!updated) {
        return res.status(404).json({ error: 'User not found' });
      }

      await auditLog.log(AuditAction.USER_ROLE_CHANGE, req.user!.id, {
        targetUserId: userId,
        newRole: role,
      });

      res.json({ user: toPublicUser(updated) });
    } catch (e: any) {
      requestLogger().error({ event: 'update_role_error', err: e }, 'update role error');
      res.status(500).json({ error: 'Update failed' });
    }
  }
);

// ════════════════════════════════════════════════════════════════════
// SELF-SERVICE: UPDATE WORK MODE (solo / foreman)
// ════════════════════════════════════════════════════════════════════
//
// The Android onboarding "How do you work?" step lets users pick between
// solo and crew/foreman. This route lets the authenticated user persist
// that choice to their own row without needing MANAGE_ROLES. The whitelist
// prevents privilege escalation — anything outside {solo, foreman} is 400.

authRouter.patch(
  '/users/me/work-mode',
  authenticateToken,
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const { mode } = req.body ?? {};
      const mapping: Record<string, UserRole> = {
        solo: UserRole.SOLO,
        foreman: UserRole.FOREMAN,
      };
      const role = mapping[mode];
      if (!role) {
        return res.status(400).json({ error: 'mode must be "solo" or "foreman"' });
      }

      const userId = req.user!.id;
      const updated = await userStore.updateUser(userId, { role });
      if (!updated) {
        return res.status(404).json({ error: 'User not found' });
      }

      await auditLog.log(AuditAction.USER_ROLE_CHANGE, userId, {
        targetUserId: userId,
        newRole: role,
        via: 'work-mode-self-service',
      });

      res.json({ user: toPublicUser(updated) });
    } catch (e: any) {
      requestLogger().error({ event: 'work_mode_update_error', err: e }, 'work-mode update error');
      res.status(500).json({ error: 'Update failed' });
    }
  }
);

// ════════════════════════════════════════════════════════════════════
// ORG INVITE & JOIN
// ════════════════════════════════════════════════════════════════════
//
// Lets a foreman generate a one-time 8-char code and another user redeem
// it to join the foreman's org. The joiner's organization_id reassigns
// to the foreman's; their role flips to team_member so the crew-list
// role filter stops hiding them. See organizationInviteService for the
// transactional consumption logic.

import { organizationInviteService, InviteError } from './organizationInviteService';

// Roles allowed to issue invites and read the org member list. Mirrors the
// foreman-tier set in presenceLocationRoutes.ts.
const ORG_ADMIN_ROLES: ReadonlySet<UserRole> = new Set<UserRole>([
  UserRole.FOREMAN,
  UserRole.ENTERPRISE,
  UserRole.ADMIN,
]);

authRouter.post(
  '/org/invites',
  authenticateToken,
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      if (!ORG_ADMIN_ROLES.has(req.user!.role as UserRole)) {
        return res.status(403).json({ error: 'foreman role required' });
      }
      const organizationId = req.user!.organizationId;
      if (!organizationId) {
        return res.status(401).json({ error: 'user missing organization_id' });
      }
      const invite = await organizationInviteService.createInvite(req.user!.id, organizationId);
      await auditLog.log(AuditAction.ORG_INVITE_CREATED, req.user!.id, {
        organizationId,
        code: invite.code,
        expiresAt: invite.expiresAt.toISOString(),
      });
      res.json({ code: invite.code, expiresAt: invite.expiresAt.toISOString() });
    } catch (e: any) {
      requestLogger().error({ event: 'org_invite_create_error', err: e }, 'org invite create error');
      res.status(500).json({ error: 'Invite creation failed' });
    }
  }
);

authRouter.post(
  '/org/join',
  authenticateToken,
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const { code } = req.body ?? {};
      if (typeof code !== 'string' || !code.trim()) {
        return res.status(400).json({ error: 'code is required' });
      }
      const result = await organizationInviteService.acceptInvite(
        req.user!.id,
        req.user!.role as UserRole,
        code
      );
      // Refetch so the response carries the new role + organizationId.
      const fresh = await userStore.getUserById(req.user!.id);
      if (!fresh) {
        return res.status(404).json({ error: 'User not found' });
      }
      await auditLog.log(AuditAction.ORG_MEMBER_JOINED, req.user!.id, {
        organizationId: result.organizationId,
        newRole: result.newRole,
      });
      res.json({ user: toPublicUser(fresh) });
    } catch (e: any) {
      if (e instanceof InviteError) {
        return res.status(e.status).json({ error: e.message });
      }
      requestLogger().error({ event: 'org_join_error', err: e }, 'org join error');
      res.status(500).json({ error: 'Join failed' });
    }
  }
);

authRouter.get(
  '/org/members',
  authenticateToken,
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      if (!ORG_ADMIN_ROLES.has(req.user!.role as UserRole)) {
        return res.status(403).json({ error: 'foreman role required' });
      }
      const organizationId = req.user!.organizationId;
      if (!organizationId) {
        return res.status(401).json({ error: 'user missing organization_id' });
      }
      const members = await organizationInviteService.listMembers(organizationId);
      res.json({ members });
    } catch (e: any) {
      requestLogger().error({ event: 'org_members_error', err: e }, 'org members error');
      res.status(500).json({ error: 'List failed' });
    }
  }
);

requestLogger().info({ event: 'auth_routes_initialized' }, 'auth routes initialized');
