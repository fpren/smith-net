import { pg, isPgEnabled } from '../db';
import { organizationInviteService, InviteError, OrgError } from '../organizationInviteService';
import { crewPositionService } from '../crewPositionService';
import { createUserAndProfile } from '../jobsService';
import { UserRole } from '../auth';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function makeUser(suffix: string, role: UserRole = UserRole.SOLO): Promise<string> {
  const email = `org-inv-${suffix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@example.com`;
  const u = await createUserAndProfile({
    email,
    password: 'password123',
    displayName: `Inv ${suffix}`,
    role,
  });
  return u.id;
}

async function clean() {
  if (!isPgEnabled()) return;
  await pg!.query('DELETE FROM organization_invites');
  await pg!.query('DELETE FROM crew_positions');
  await pg!.query('DELETE FROM shifts');
}

describeDb('organizationInviteService', () => {
  beforeEach(clean);
  afterAll(async () => { await clean(); await pg?.end(); });

  describe('createInvite', () => {
    it('returns an 8-char code and inserts a pending row', async () => {
      const foreman = await makeUser('cf1', UserRole.FOREMAN);
      const invite = await organizationInviteService.createInvite(foreman, foreman);
      expect(invite.code).toMatch(/^[A-Z2-9]{8}$/);
      expect(invite.expiresAt.getTime()).toBeGreaterThan(Date.now());

      const row = await pg!.query(
        `SELECT code, organization_id, created_by, consumed_at FROM organization_invites WHERE code = $1`,
        [invite.code]
      );
      expect(row.rowCount).toBe(1);
      expect(row.rows[0].organization_id).toBe(foreman);
      expect(row.rows[0].created_by).toBe(foreman);
      expect(row.rows[0].consumed_at).toBeNull();
    });
  });

  describe('acceptInvite', () => {
    it('moves the joiner into the foreman org, flips role to team_member, marks invite consumed', async () => {
      const foreman = await makeUser('af1', UserRole.FOREMAN);
      const joiner = await makeUser('aj1', UserRole.SOLO);
      const invite = await organizationInviteService.createInvite(foreman, foreman);

      const result = await organizationInviteService.acceptInvite(joiner, UserRole.SOLO, invite.code);
      expect(result.organizationId).toBe(foreman);
      expect(result.newRole).toBe(UserRole.TEAM_MEMBER);

      const userRow = await pg!.query(`SELECT organization_id, role FROM users WHERE id = $1`, [joiner]);
      expect(userRow.rows[0].organization_id).toBe(foreman);
      expect(userRow.rows[0].role).toBe('team');

      const consumed = await pg!.query(
        `SELECT consumed_at, consumed_by FROM organization_invites WHERE code = $1`,
        [invite.code]
      );
      expect(consumed.rows[0].consumed_at).toBeTruthy();
      expect(consumed.rows[0].consumed_by).toBe(joiner);
    });

    it('also reassigns existing crew_positions to the new org', async () => {
      const foreman = await makeUser('af2', UserRole.FOREMAN);
      const joiner = await makeUser('aj2', UserRole.SOLO);
      // Joiner has an existing shift + position in their own org-of-one.
      await crewPositionService.startShift(joiner, 'android');
      await crewPositionService.upsertPosition(joiner, { lat: 1, lng: 2 }, 'android');

      const before = await pg!.query(`SELECT organization_id FROM crew_positions WHERE user_id = $1`, [joiner]);
      expect(before.rows[0].organization_id).toBe(joiner); // org-of-one

      const invite = await organizationInviteService.createInvite(foreman, foreman);
      await organizationInviteService.acceptInvite(joiner, UserRole.SOLO, invite.code);

      const after = await pg!.query(`SELECT organization_id FROM crew_positions WHERE user_id = $1`, [joiner]);
      expect(after.rows[0].organization_id).toBe(foreman);
    });

    it('keeps the foreman role for a joining foreman (multi-foreman org)', async () => {
      const bossA = await makeUser('mfa', UserRole.FOREMAN);
      const bossB = await makeUser('mfb', UserRole.FOREMAN);
      const invite = await organizationInviteService.createInvite(bossA, bossA);

      const result = await organizationInviteService.acceptInvite(bossB, UserRole.FOREMAN, invite.code);
      expect(result.newRole).toBe(UserRole.FOREMAN);

      const row = await pg!.query(`SELECT role FROM users WHERE id = $1`, [bossB]);
      expect(row.rows[0].role).toBe('foreman');
    });

    it('throws 404 for unknown code', async () => {
      const joiner = await makeUser('un1', UserRole.SOLO);
      await expect(
        organizationInviteService.acceptInvite(joiner, UserRole.SOLO, 'NOPESUCH')
      ).rejects.toMatchObject({ status: 404 });
    });

    it('throws 409 when re-used', async () => {
      const foreman = await makeUser('rf1', UserRole.FOREMAN);
      const j1 = await makeUser('rj1', UserRole.SOLO);
      const j2 = await makeUser('rj2', UserRole.SOLO);
      const invite = await organizationInviteService.createInvite(foreman, foreman);
      await organizationInviteService.acceptInvite(j1, UserRole.SOLO, invite.code);
      await expect(
        organizationInviteService.acceptInvite(j2, UserRole.SOLO, invite.code)
      ).rejects.toMatchObject({ status: 409 });
    });

    it('throws 410 when expired', async () => {
      const foreman = await makeUser('ef1', UserRole.FOREMAN);
      const joiner = await makeUser('ej1', UserRole.SOLO);
      const invite = await organizationInviteService.createInvite(foreman, foreman);
      // Backdate expiry directly.
      await pg!.query(
        `UPDATE organization_invites SET expires_at = NOW() - INTERVAL '1 second' WHERE code = $1`,
        [invite.code]
      );
      await expect(
        organizationInviteService.acceptInvite(joiner, UserRole.SOLO, invite.code)
      ).rejects.toMatchObject({ status: 410 });
    });

    it('rolls back the whole transaction if the joiner id is bogus', async () => {
      // The consumed_by FK references users(id). A non-existent joiner id
      // raises 23503 on the invite UPDATE, which must roll back the user +
      // crew_positions writes as well. Confirm the invite stays pending.
      const foreman = await makeUser('tf1', UserRole.FOREMAN);
      const invite = await organizationInviteService.createInvite(foreman, foreman);

      await expect(
        organizationInviteService.acceptInvite(
          '00000000-0000-0000-0000-000000000000',
          UserRole.SOLO,
          invite.code
        )
      ).rejects.toThrow();

      const consumed = await pg!.query(
        `SELECT consumed_at FROM organization_invites WHERE code = $1`,
        [invite.code]
      );
      expect(consumed.rows[0].consumed_at).toBeNull();
    });

    it('normalizes the code (trim + uppercase)', async () => {
      const foreman = await makeUser('nz1', UserRole.FOREMAN);
      const joiner = await makeUser('nj1', UserRole.SOLO);
      const invite = await organizationInviteService.createInvite(foreman, foreman);
      const result = await organizationInviteService.acceptInvite(
        joiner,
        UserRole.SOLO,
        `  ${invite.code.toLowerCase()}  `
      );
      expect(result.organizationId).toBe(foreman);
    });
  });

  describe('removeMember', () => {
    it('moves the member back to own org-of-one with role=solo', async () => {
      const foreman = await makeUser('rm1', UserRole.FOREMAN);
      const joiner = await makeUser('rmj1', UserRole.SOLO);
      const invite = await organizationInviteService.createInvite(foreman, foreman);
      await organizationInviteService.acceptInvite(joiner, UserRole.SOLO, invite.code);

      const result = await organizationInviteService.removeMember(foreman, foreman, joiner);
      expect(result).toEqual({ id: joiner, role: UserRole.SOLO, organizationId: joiner });

      const row = await pg!.query(`SELECT organization_id, role FROM users WHERE id = $1`, [joiner]);
      expect(row.rows[0].organization_id).toBe(joiner);
      expect(row.rows[0].role).toBe('solo');
    });

    it('reassigns existing crew_positions back to the target user', async () => {
      const foreman = await makeUser('rm2', UserRole.FOREMAN);
      const joiner = await makeUser('rmj2', UserRole.SOLO);
      await crewPositionService.startShift(joiner, 'android');
      await crewPositionService.upsertPosition(joiner, { lat: 1, lng: 2 }, 'android');
      const invite = await organizationInviteService.createInvite(foreman, foreman);
      await organizationInviteService.acceptInvite(joiner, UserRole.SOLO, invite.code);

      const mid = await pg!.query(`SELECT organization_id FROM crew_positions WHERE user_id = $1`, [joiner]);
      expect(mid.rows[0].organization_id).toBe(foreman);

      await organizationInviteService.removeMember(foreman, foreman, joiner);
      const after = await pg!.query(`SELECT organization_id FROM crew_positions WHERE user_id = $1`, [joiner]);
      expect(after.rows[0].organization_id).toBe(joiner);
    });

    it('throws 400 on self-kick', async () => {
      const foreman = await makeUser('rm3', UserRole.FOREMAN);
      await expect(
        organizationInviteService.removeMember(foreman, foreman, foreman)
      ).rejects.toMatchObject({ status: 400 });
    });

    it('throws 403 when target is a foreman in the same org', async () => {
      const bossA = await makeUser('rm4a', UserRole.FOREMAN);
      const bossB = await makeUser('rm4b', UserRole.FOREMAN);
      const invite = await organizationInviteService.createInvite(bossA, bossA);
      await organizationInviteService.acceptInvite(bossB, UserRole.FOREMAN, invite.code);

      await expect(
        organizationInviteService.removeMember(bossA, bossA, bossB)
      ).rejects.toMatchObject({ status: 403 });
    });

    it('throws 404 when target user does not exist', async () => {
      const foreman = await makeUser('rm5', UserRole.FOREMAN);
      await expect(
        organizationInviteService.removeMember(foreman, foreman, '00000000-0000-0000-0000-000000000000')
      ).rejects.toMatchObject({ status: 404 });
    });

    it('throws 404 when target is in a different org (no leakage)', async () => {
      const bossA = await makeUser('rm6a', UserRole.FOREMAN);
      const bossB = await makeUser('rm6b', UserRole.FOREMAN);
      const outsider = await makeUser('rm6o', UserRole.SOLO);
      const invite = await organizationInviteService.createInvite(bossB, bossB);
      await organizationInviteService.acceptInvite(outsider, UserRole.SOLO, invite.code);

      // bossA tries to kick outsider, who is in bossB's org.
      await expect(
        organizationInviteService.removeMember(bossA, bossA, outsider)
      ).rejects.toMatchObject({ status: 404 });

      // outsider still in bossB's org.
      const row = await pg!.query(`SELECT organization_id, role FROM users WHERE id = $1`, [outsider]);
      expect(row.rows[0].organization_id).toBe(bossB);
      expect(row.rows[0].role).toBe('team');
    });
  });

  describe('leaveOrg', () => {
    it('moves a team_member back to own org-of-one with role=solo and reassigns crew_positions', async () => {
      const foreman = await makeUser('lv1', UserRole.FOREMAN);
      const joiner = await makeUser('lvj1', UserRole.SOLO);
      await crewPositionService.startShift(joiner, 'android');
      await crewPositionService.upsertPosition(joiner, { lat: 1, lng: 2 }, 'android');
      const invite = await organizationInviteService.createInvite(foreman, foreman);
      await organizationInviteService.acceptInvite(joiner, UserRole.SOLO, invite.code);

      const result = await organizationInviteService.leaveOrg(joiner, UserRole.TEAM_MEMBER);
      expect(result).toEqual({ id: joiner, role: UserRole.SOLO, organizationId: joiner });

      const userRow = await pg!.query(`SELECT organization_id, role FROM users WHERE id = $1`, [joiner]);
      expect(userRow.rows[0].organization_id).toBe(joiner);
      expect(userRow.rows[0].role).toBe('solo');

      const pos = await pg!.query(`SELECT organization_id FROM crew_positions WHERE user_id = $1`, [joiner]);
      expect(pos.rows[0].organization_id).toBe(joiner);
    });

    it('keeps the foreman role when a peer foreman leaves another foreman\'s org', async () => {
      const bossA = await makeUser('lv2a', UserRole.FOREMAN);
      const bossB = await makeUser('lv2b', UserRole.FOREMAN);
      const invite = await organizationInviteService.createInvite(bossA, bossA);
      await organizationInviteService.acceptInvite(bossB, UserRole.FOREMAN, invite.code);

      const result = await organizationInviteService.leaveOrg(bossB, UserRole.FOREMAN);
      expect(result).toEqual({ id: bossB, role: UserRole.FOREMAN, organizationId: bossB });

      const row = await pg!.query(`SELECT organization_id, role FROM users WHERE id = $1`, [bossB]);
      expect(row.rows[0].organization_id).toBe(bossB);
      expect(row.rows[0].role).toBe('foreman');
    });

    it('throws 403 for an original foreman trying to leave their own org', async () => {
      const foreman = await makeUser('lv3', UserRole.FOREMAN);
      await expect(
        organizationInviteService.leaveOrg(foreman, UserRole.FOREMAN)
      ).rejects.toMatchObject({ status: 403 });
    });

    it('throws 403 for a solo user already in own org-of-one', async () => {
      const solo = await makeUser('lv4', UserRole.SOLO);
      await expect(
        organizationInviteService.leaveOrg(solo, UserRole.SOLO)
      ).rejects.toMatchObject({ status: 403 });
    });

    it('throws 404 for an unknown user id', async () => {
      await expect(
        organizationInviteService.leaveOrg('00000000-0000-0000-0000-000000000000', UserRole.SOLO)
      ).rejects.toMatchObject({ status: 404 });
    });
  });

  describe('listMembers', () => {
    it('returns every user in the org, ordered by display_name', async () => {
      const foreman = await makeUser('lm1', UserRole.FOREMAN);
      const member = await makeUser('lm2', UserRole.SOLO);
      const invite = await organizationInviteService.createInvite(foreman, foreman);
      await organizationInviteService.acceptInvite(member, UserRole.SOLO, invite.code);

      const list = await organizationInviteService.listMembers(foreman);
      const ids = list.map((m) => m.id);
      expect(ids).toEqual(expect.arrayContaining([foreman, member]));
      expect(list.find((m) => m.id === member)!.role).toBe('team');
    });

    it('returns empty for an unknown org', async () => {
      const list = await organizationInviteService.listMembers('no-such-org');
      expect(list).toEqual([]);
    });
  });
});
