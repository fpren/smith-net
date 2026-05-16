/**
 * Phase 3.5 Slice 1: location reporting + crew listing routes.
 *
 * POST /api/presence/location  — UPSERT crew_positions (gated on open shift)
 * GET  /api/crew/positions     — list positions for users with open shifts (foreman+)
 *
 * Mounted at `/api` (not `/api/presence` or `/api/crew`) because this router
 * owns sibling paths under both prefixes.
 */

import { Router, Response } from 'express';
import { authenticateToken, AuthenticatedRequest, UserRole } from './auth';
import { crewPositionService } from './crewPositionService';
import { auditLog, AuditAction } from './auditLog';
import { requestLogger } from './log';

export const presenceLocationRouter = Router();

function isFiniteNumber(x: unknown): x is number {
  return typeof x === 'number' && Number.isFinite(x);
}

// Foreman-tier roles authorised to read the crew's positions.
// UserRole enum in auth.ts: SOLO, TEAM_MEMBER, TEAM_LEAD, FOREMAN, ENTERPRISE, ADMIN.
const FOREMAN_ROLES: ReadonlySet<UserRole> = new Set<UserRole>([
  UserRole.FOREMAN,
  UserRole.ENTERPRISE,
  UserRole.ADMIN,
]);

presenceLocationRouter.post(
  '/presence/location',
  authenticateToken,
  async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user!.id;
    const { lat, lng, accuracy_m, battery_pct } = req.body ?? {};
    if (!isFiniteNumber(lat) || !isFiniteNumber(lng)) {
      return res.status(400).json({ error: 'lat and lng are required numbers' });
    }
    if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
      return res.status(400).json({ error: 'lat/lng out of range' });
    }

    const open = await crewPositionService.getCurrentShift(userId);
    if (!open) {
      return res.status(403).json({ error: 'no open shift' });
    }

    try {
      const pos = await crewPositionService.upsertPosition(
        userId,
        {
          lat,
          lng,
          accuracy_m: isFiniteNumber(accuracy_m) ? accuracy_m : undefined,
          battery_pct: isFiniteNumber(battery_pct) ? Math.round(battery_pct) : undefined,
        },
        open.source
      );
      await auditLog.log(AuditAction.LOCATION_REPORTED, userId, {
        lat: pos.latitude,
        lng: pos.longitude,
        accuracy_m: pos.accuracy_m,
        source: open.source,
      });
      requestLogger().info(
        { event: 'location_reported', userId, lat: pos.latitude, lng: pos.longitude },
        'location reported'
      );
      return res.status(200).json(pos);
    } catch (err) {
      if ((err as Error).message?.match(/no open shift/i)) {
        return res.status(403).json({ error: 'no open shift' });
      }
      throw err;
    }
  }
);

presenceLocationRouter.get(
  '/crew/positions',
  authenticateToken,
  async (req: AuthenticatedRequest, res: Response) => {
    const role = req.user!.role as UserRole;
    if (!FOREMAN_ROLES.has(role)) {
      return res.status(403).json({ error: 'foreman role required' });
    }
    const positions = await crewPositionService.listOpenPositions();
    return res.status(200).json(positions);
  }
);
