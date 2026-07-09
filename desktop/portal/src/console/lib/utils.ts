// desktop/portal/src/console/lib/utils.ts
//
// Visual-lift helpers — ported from the dashboard module so its UI
// primitives work in the console.

import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/** Tailwind class merger. */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

/** First letter(s) from a name. "James Park" -> "JP", "Alice" -> "A". */
export function initials(name: string): string {
  if (!name) return '';
  return name
    .split(/\s+/)
    .map((p) => p[0])
    .filter(Boolean)
    .join('')
    .slice(0, 2)
    .toUpperCase();
}

/** Darken a hex color for gradient endpoints. Returns rgb(...) string. */
export function darkenHex(hex: string, amount = 28): string {
  const h = hex.replace('#', '');
  const r = Math.max(0, parseInt(h.slice(0, 2), 16) - amount);
  const g = Math.max(0, parseInt(h.slice(2, 4), 16) - amount);
  const b = Math.max(0, parseInt(h.slice(4, 6), 16) - amount);
  return `rgb(${r},${g},${b})`;
}

/** Pseudo-random but deterministic accent color from a string id. Used by
 *  Avatar so each user gets a stable color without a backend field. Returns
 *  a `var(--sn-avatar-aN)` reference into the Task 1 avatar palette (design
 *  System v2) instead of a literal hex, so the palette can be retuned in one
 *  place (`design/tokens.json`) without touching consumers. */
const AVATAR_TOKEN_COUNT = 6; // --sn-avatar-a1 .. a6

export function accentForId(id: string): string {
  let hash = 0;
  for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) | 0;
  const n = (Math.abs(hash) % AVATAR_TOKEN_COUNT) + 1;
  return `var(--sn-avatar-a${n})`;
}

/** Stable accent color for a user role. Used by Chip in CrewCard etc.
 *  Maps each v1 literal hex to the sn-* token whose JOB it most closely
 *  matched (see task-8-report.md for the full per-role reasoning):
 *    - foreman was literally the old v1 accent hex (#9A6F2E) -> sn-accent
 *    - team was literally the old v1 "ok" hex (#5A8C76) -> sn-status-online
 *    - admin's gray was explicitly "calm, not alarming" -> sn-ink-muted
 *    - solo's olive shared warn's warm-brown family -> sn-attention
 *    - enterprise (dusty blue) and lead (muted purple) were both cool hues
 *      with no direct token match -> sn-accent (nearest cool-toned token) */
export function colorForRole(role: string): string {
  switch (role) {
    case 'admin':      return 'var(--sn-ink-muted)';
    case 'enterprise': return 'var(--sn-accent)';
    case 'foreman':    return 'var(--sn-accent)';
    case 'lead':       return 'var(--sn-accent)';
    case 'team':       return 'var(--sn-status-online)';
    case 'solo':
    default:           return 'var(--sn-attention)';
  }
}
