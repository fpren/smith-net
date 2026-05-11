/**
 * F1.5: zod schemas for /api/auth/* routes.
 *
 * Strict mode (`.strict()`) is the default — unknown fields are rejected so
 * mass-assignment attacks (e.g. POST /register with `role: 'admin'`) fail
 * fast instead of being silently dropped.
 *
 * Notes on overlap with the in-handler validators:
 * - Password POLICY (≥8, letter, digit) lives in auth.ts:validatePassword.
 *   The schema only enforces "is a non-empty string in a sane length range" —
 *   the policy validator runs after, with a richer error message + code.
 * - Email duplicate-detection lives in userStore.createUser. Schema only
 *   normalizes (lowercase + format check).
 * - Login password has NO min length: existing accounts with grandfathered
 *   short passwords must still be able to authenticate.
 */

import { z } from 'zod';

export const RegisterBody = z
  .object({
    email: z.string().trim().toLowerCase().email().max(254),
    password: z.string().min(1).max(200),
    displayName: z.string().trim().min(1).max(100),
  })
  .strict();
export type RegisterBody = z.infer<typeof RegisterBody>;

export const LoginBody = z
  .object({
    email: z.string().trim().toLowerCase().email().max(254),
    password: z.string().min(1).max(200),
  })
  .strict();
export type LoginBody = z.infer<typeof LoginBody>;

export const RefreshBody = z
  .object({
    refreshToken: z.string().min(1).max(4096),
  })
  .strict();
export type RefreshBody = z.infer<typeof RefreshBody>;

export const VerifyQuery = z
  .object({
    token: z.string().min(1).max(256),
  })
  .strict();
export type VerifyQuery = z.infer<typeof VerifyQuery>;

// /resend-verification accepts no body. Empty strict object rejects any payload.
export const ResendVerificationBody = z.object({}).strict();
export type ResendVerificationBody = z.infer<typeof ResendVerificationBody>;

export const UpdateProfileBody = z
  .object({
    displayName: z.string().trim().min(1).max(100).optional(),
  })
  .strict();
export type UpdateProfileBody = z.infer<typeof UpdateProfileBody>;

// Logout accepts an optional refreshToken so we can revoke the right one.
export const LogoutBody = z
  .object({
    refreshToken: z.string().min(1).max(4096).optional(),
  })
  .strict();
export type LogoutBody = z.infer<typeof LogoutBody>;

// Admin endpoints
export const UpdateUserRoleBody = z
  .object({
    role: z.enum(['solo', 'team', 'lead', 'foreman', 'enterprise', 'admin']),
  })
  .strict();
export type UpdateUserRoleBody = z.infer<typeof UpdateUserRoleBody>;
