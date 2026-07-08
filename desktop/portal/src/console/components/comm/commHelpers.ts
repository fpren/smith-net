// desktop/portal/src/console/components/comm/commHelpers.ts
// Small shared helpers for the comm surface.

import type { Message, Presence } from '../../../types';

/** 8-char public id -> "A1B2-C3D4" for display. Tolerates already-formatted. */
export function formatPublicId(id: string | null | undefined): string {
  if (!id) return '--------';
  const raw = id.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  return raw.length === 8 ? `${raw.slice(0, 4)}-${raw.slice(4)}` : raw;
}

/** Normalize user input (paste/type) to a bare 8-char id, or null if invalid. */
export function normalizePublicId(input: string): string | null {
  const raw = input.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  return /^[A-Z0-9]{8}$/.test(raw) ? raw : null;
}

/** Presence status -> ring/dot color (console palette). null = no dot. */
export function presenceColor(p: Presence | undefined): string | null {
  if (!p) return null;
  if (p.status === 'online') return 'var(--color-ok)';
  if (p.status === 'away') return 'var(--color-warn)';
  return null; // offline -> no dot
}

/**
 * Activity-feed direction marker for a channel's last message, relative to the
 * viewer. No telephony model exists, so this is derived from message direction:
 *   [>] last message was outgoing (sent by me)
 *   [<] last message was incoming (sent by someone else)
 *   [x] incoming + unread (the "missed" analogue)
 */
export function directionMarker(
  last: Message | undefined,
  selfId: string | undefined,
  unread: number,
): '[>]' | '[<]' | '[x]' | '' {
  if (!last) return '';
  const outgoing = !!selfId && last.senderId === selfId;
  if (outgoing) return '[>]';
  return unread > 0 ? '[x]' : '[<]';
}

export function markerColor(marker: string): string {
  if (marker === '[x]') return 'var(--color-danger)';
  if (marker === '[>]') return 'var(--color-text-dim)';
  return 'var(--color-text-muted)';
}
