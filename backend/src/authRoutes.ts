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
import { auditLog, AuditAction } from './auditLog';
import { sendEmail, isEmailLive } from './emailService';
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

function buildVerificationLink(token: string): string {
  return `${PUBLIC_BASE_URL.replace(/\/+$/, '')}/api/auth/verify?token=${encodeURIComponent(token)}`;
}

function buildVerificationEmail(displayName: string, link: string): { subject: string; text: string; html: string } {
  const subject = 'Verify your Smith Net email';
  const text = [
    `Hi ${displayName},`,
    '',
    'Tap to verify your email and finish setting up Smith Net:',
    '',
    link,
    '',
    'This link expires in 24 hours. If you did not create a Smith Net account, ignore this email.',
    '',
    '— Smith Net',
  ].join('\n');
  const html = `<p>Hi ${displayName},</p>
<p>Tap to verify your email and finish setting up Smith Net:</p>
<p><a href="${link}">${link}</a></p>
<p>This link expires in 24 hours. If you did not create a Smith Net account, ignore this email.</p>
<p>— Smith Net</p>`;
  return { subject, text, html };
}

async function sendVerificationEmail(to: string, displayName: string, token: string): Promise<void> {
  const link = buildVerificationLink(token);
  const { subject, text, html } = buildVerificationEmail(displayName, link);
  const result = await sendEmail({ to, subject, text, html });
  if (!result.ok) {
    // Don't fail the request — registration succeeded; user can request resend.
    console.warn('[Auth] Verification email send failed (non-fatal):', result.error);
  }
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

    const user = await userStore.createUser(email, password, displayName, UserRole.SOLO);
    const tokens = generateTokens(user);

    // Audit log
    auditLog.log(AuditAction.USER_REGISTER, user.id, { email });

    // F1.4: send verification email (non-blocking — failures don't fail register).
    // The token was generated inside createUser; mark the send-attempt timestamp
    // so the resend cooldown applies even to this initial send.
    if (user.emailVerificationToken) {
      userStore.recordVerificationSendAttempt(user.id);
      sendVerificationEmail(user.email, user.displayName, user.emailVerificationToken)
        .catch((err) => console.warn('[Auth] Verification email error (non-fatal):', err));
    }

    setAuthCookies(res, tokens);

    res.status(201).json({
      user: toPublicUser(user),
      ...tokens,
      requiresEmailVerification: true,
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

    const result = await userStore.verifyPassword(email, password);

    if (!result.ok) {
      if (result.reason === 'locked') {
        auditLog.log(AuditAction.SECURITY_ALERT, 'unknown', {
          event: 'login_blocked_locked',
          email,
        });
        return res.status(429).json({
          error: 'Account temporarily locked',
          code: 'account_locked',
          retry_after_minutes: result.retryMinutes,
        });
      }
      auditLog.log(AuditAction.USER_LOGIN_FAILED, 'unknown', { email });
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    const tokens = generateTokens(result.user);

    // Audit log
    auditLog.log(AuditAction.USER_LOGIN, result.user.id, { email });

    setAuthCookies(res, tokens);

    res.json({
      user: toPublicUser(result.user),
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

    setAuthCookies(res, tokens);

    res.json(tokens);
  } catch (e: any) {
    console.error('[Auth] Refresh error:', e.message);
    res.status(500).json({ error: 'Token refresh failed' });
  }
});

// ════════════════════════════════════════════════════════════════════
// F1.4: VERIFY EMAIL (clicked from email; renders an HTML page)
// ════════════════════════════════════════════════════════════════════

authRouter.get('/verify', (req, res) => {
  const token = typeof req.query.token === 'string' ? req.query.token : '';

  if (!token) {
    return renderVerifyFailed(res, 400, 'Missing verification token.');
  }

  const user = userStore.findByVerificationToken(token);
  if (!user) {
    // Could be: expired, already-consumed, or never existed. We don't
    // distinguish — disclosing "already used" leaks that the email exists.
    return renderVerifyFailed(res, 400, 'This link is invalid or expired. Request a new one from the app.');
  }

  userStore.markEmailVerified(user.id);
  auditLog.log(AuditAction.USER_PROFILE_UPDATE, user.id, { event: 'email_verified' });

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
  const stored = userStore.getUserById(userId);
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

  const newToken = userStore.regenerateVerificationToken(userId);
  if (!newToken) {
    // Race: verified between the early-return and now.
    return res.json({ ok: true, alreadyVerified: true });
  }

  await sendVerificationEmail(stored.email, stored.displayName, newToken);
  auditLog.log(AuditAction.USER_PROFILE_UPDATE, userId, { event: 'verification_resent' });

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
