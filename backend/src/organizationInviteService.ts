/**
 * Org invite service: a foreman generates a one-time 8-char code; the joiner
 * POSTs the code and is moved into the foreman's organization_id. Their role
 * flips to team_member (unless they were already a foreman, in which case they
 * stay foreman — multi-foreman orgs are allowed).
 *
 * Backed by the organization_invites table (migration 014). All consumption
 * happens in a single transaction so a half-moved user is impossible.
 */

import { pg, isPgEnabled } from './db';
import { UserRole } from './auth';
import { generateInviteCode } from './inviteCodeGenerator';

const INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

function requirePg() {
  if (!isPgEnabled() || !pg) {
    throw new Error('[organizationInviteService] DATABASE_URL is required; pg pool is not initialized');
  }
  return pg;
}

export class InviteError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
    this.name = 'InviteError';
  }
}

export class OrgError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
    this.name = 'OrgError';
  }
}

export interface InviteRecord {
  code: string;
  expiresAt: Date;
}

export interface OrgMember {
  id: string;
  email: string;
  displayName: string;
  role: string;
}

class OrganizationInviteService {
  async createInvite(foremanId: string, organizationId: string): Promise<InviteRecord> {
    const db = requirePg();
    // Retry on PK collision. With a 32^8 alphabet a single attempt is
    // essentially guaranteed to work, but 5 keeps the math airtight.
    for (let attempt = 0; attempt < 5; attempt++) {
      const code = generateInviteCode();
      const expiresAt = new Date(Date.now() + INVITE_TTL_MS);
      try {
        await db.query(
          `INSERT INTO organization_invites (code, organization_id, created_by, expires_at)
           VALUES ($1, $2, $3, $4)`,
          [code, organizationId, foremanId, expiresAt]
        );
        return { code, expiresAt };
      } catch (e: any) {
        if (e?.code === '23505') continue; // PK violation — try a new code
        throw e;
      }
    }
    throw new Error('failed to generate unique invite code after 5 attempts');
  }

  async acceptInvite(
    joinerId: string,
    joinerRole: UserRole,
    code: string
  ): Promise<{ organizationId: string; newRole: UserRole }> {
    const db = requirePg();
    const normalized = code.trim().toUpperCase();
    if (!normalized) {
      throw new InviteError('code is required', 400);
    }
    const client = await db.connect();
    try {
      await client.query('BEGIN');
      const invite = await client.query<{
        organization_id: string;
        expires_at: Date;
        consumed_at: Date | null;
      }>(
        `SELECT organization_id, expires_at, consumed_at
           FROM organization_invites
          WHERE code = $1
          FOR UPDATE`,
        [normalized]
      );
      if (invite.rowCount === 0) {
        throw new InviteError('invite not found', 404);
      }
      const row = invite.rows[0];
      if (row.consumed_at) {
        throw new InviteError('invite already used', 409);
      }
      if (row.expires_at.getTime() <= Date.now()) {
        throw new InviteError('invite expired', 410);
      }

      // Foremen stay foremen (multi-foreman orgs allowed); everyone else flips
      // to team_member so the `u.role <> 'solo'` crew-list filter stops hiding
      // them on their new foreman's map.
      const newRole = joinerRole === UserRole.FOREMAN ? UserRole.FOREMAN : UserRole.TEAM_MEMBER;

      await client.query(
        `UPDATE users
            SET organization_id = $2,
                role = $3,
                updated_at = NOW()
          WHERE id = $1`,
        [joinerId, row.organization_id, newRole]
      );
      // Existing crew_positions rows must follow the user to the new org —
      // otherwise the foreman won't see them until the joiner posts a fresh
      // location.
      await client.query(
        `UPDATE crew_positions
            SET organization_id = $2
          WHERE user_id = $1`,
        [joinerId, row.organization_id]
      );
      await client.query(
        `UPDATE organization_invites
            SET consumed_at = NOW(),
                consumed_by = $1
          WHERE code = $2`,
        [joinerId, normalized]
      );
      await client.query('COMMIT');
      return { organizationId: row.organization_id, newRole };
    } catch (e) {
      await client.query('ROLLBACK');
      throw e;
    } finally {
      client.release();
    }
  }

  async removeMember(
    actorId: string,
    organizationId: string,
    targetUserId: string,
  ): Promise<{ id: string; role: UserRole; organizationId: string }> {
    if (actorId === targetUserId) {
      throw new OrgError('cannot remove yourself', 400);
    }
    const db = requirePg();
    const client = await db.connect();
    try {
      await client.query('BEGIN');
      const target = await client.query<{
        id: string;
        role: string;
        organization_id: string;
      }>(
        `SELECT id, role, organization_id FROM users WHERE id = $1 FOR UPDATE`,
        [targetUserId]
      );
      if (target.rowCount === 0 || target.rows[0].organization_id !== organizationId) {
        // Treat cross-org as not-found so we don't leak the user's existence.
        throw new OrgError('member not found in your org', 404);
      }
      if (target.rows[0].role === UserRole.FOREMAN) {
        throw new OrgError('cannot remove a foreman', 403);
      }

      // Symmetric reversal of acceptInvite: move back to own org-of-one, drop
      // to solo. The `u.role <> 'solo'` filter in /api/crew/positions then
      // hides them from the (former) foreman's map.
      await client.query(
        `UPDATE users
            SET organization_id = id,
                role = $2,
                updated_at = NOW()
          WHERE id = $1`,
        [targetUserId, UserRole.SOLO]
      );
      await client.query(
        `UPDATE crew_positions
            SET organization_id = user_id
          WHERE user_id = $1`,
        [targetUserId]
      );
      await client.query('COMMIT');
      return { id: targetUserId, role: UserRole.SOLO, organizationId: targetUserId };
    } catch (e) {
      await client.query('ROLLBACK');
      throw e;
    } finally {
      client.release();
    }
  }

  async listMembers(organizationId: string): Promise<OrgMember[]> {
    const db = requirePg();
    const r = await db.query<OrgMember>(
      `SELECT id, email, display_name AS "displayName", role
         FROM users
        WHERE organization_id = $1
        ORDER BY display_name`,
      [organizationId]
    );
    return r.rows;
  }
}

export const organizationInviteService = new OrganizationInviteService();
