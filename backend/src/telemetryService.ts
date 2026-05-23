// backend/src/telemetryService.ts
//
// The single writer of gate_hit_events (F5.2). PII-free: it hashes the profile
// id itself (user_id_hash = SHA256(profile.id)), so a raw id can never reach the
// table. Emission is best-effort -- awaited by callers but never throws -- so a
// telemetry failure cannot break a gated response.

import { pg, isPgEnabled } from './db';
import { sha256HexGated } from './sha256Gate';
import { requestLogger } from './log';

/** The F5.2 allowlist. Server-authoritative: only these events are accepted. */
export const ALLOWED_EVENTS = [
  'gate_hit.active_job_cap',
  'gate_hit.pdf_send_cap',
  'gate_hit.plan_compiler_preview',
  'gate_hit.plan_compiler_preview_dismissed',
  'gate_hit.ai_tab',
  'gate_hit.crew_invite',
  'tier_upgrade.cta_shown',
  'tier_upgrade.cta_clicked',
  'tier_upgrade.cta_dismissed',
  'tier_upgrade.trial_started',
  'tier_upgrade.trial_expired',
  'tier_upgrade.paid_converted',
  'tier_downgrade.canceled',
  'funnel.signup',
  'funnel.first_invoice_sent',
] as const;

export type GateEvent = typeof ALLOWED_EVENTS[number];

export function isAllowedEvent(e: string): e is GateEvent {
  return (ALLOWED_EVENTS as readonly string[]).includes(e);
}

/** Keys forbidden in metadata (mass-PII defense). */
export const PII_KEYS = new Set([
  'email', 'name', 'display_name', 'displayName',
  'profile_id', 'profileId', 'id', 'user_id', 'userId', 'phone',
]);

export function hasPiiKey(metadata: Record<string, unknown>): boolean {
  return Object.keys(metadata).some((k) => PII_KEYS.has(k));
}

/** SHA256(profile.id) hex. The ONLY way an id enters the table -- never raw. */
export function hashUserId(profileId: string): string {
  return sha256HexGated(Buffer.from(profileId, 'utf8'));
}

/**
 * Best-effort durable emit. Awaited by callers but NEVER throws: no-ops without
 * a DB, skips unknown events, drops PII keys, swallows query errors.
 */
export async function emitGateHit(
  profileId: string,
  event: string,
  currentTier: string,
  metadata: Record<string, unknown> = {},
): Promise<void> {
  try {
    if (!isPgEnabled() || !pg) return;
    if (!isAllowedEvent(event)) {
      requestLogger().warn({ event: 'gate_hit_unknown_event', gateEvent: event }, 'skipped unknown gate event');
      return;
    }
    const safe: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(metadata)) {
      if (!PII_KEYS.has(k)) safe[k] = v;
    }
    await pg.query(
      `INSERT INTO gate_hit_events (event, user_id_hash, current_tier, metadata)
         VALUES ($1, $2, $3, $4)`,
      [event, hashUserId(profileId), currentTier, JSON.stringify(safe)],
    );
  } catch (e) {
    requestLogger().error({ event: 'gate_hit_emit_error', err: e }, 'gate hit emit failed');
  }
}
