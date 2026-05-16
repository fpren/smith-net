import { pg, isPgEnabled } from '../db';
import { crewPositionService } from '../crewPositionService';
import { createUserAndProfile } from '../jobsService';
import { UserRole } from '../auth';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function makeUser(suffix: string): Promise<string> {
  const email = `crew-pos-${suffix}-${Date.now()}@example.com`;
  const u = await createUserAndProfile({
    email,
    password: 'password123',
    displayName: `Crew ${suffix}`,
    role: UserRole.FOREMAN,
  });
  return u.id;
}

async function cleanShiftsAndPositions() {
  if (!isPgEnabled()) return;
  await pg!.query('DELETE FROM crew_positions');
  await pg!.query('DELETE FROM shifts');
}

describeDb('crewPositionService — shifts', () => {
  beforeEach(cleanShiftsAndPositions);

  it('startShift creates an open shift row', async () => {
    const userId = await makeUser('s1');
    const shift = await crewPositionService.startShift(userId, 'android');
    expect(shift.id).toBeTruthy();
    expect(shift.user_id).toBe(userId);
    expect(shift.source).toBe('android');
    expect(shift.ended_at).toBeNull();
  });

  it('startShift twice for the same user without ending throws', async () => {
    const userId = await makeUser('s2');
    await crewPositionService.startShift(userId, 'android');
    await expect(crewPositionService.startShift(userId, 'web')).rejects.toThrow();
  });

  it('endShift sets ended_at on the open shift', async () => {
    const userId = await makeUser('s3');
    const opened = await crewPositionService.startShift(userId, 'android');
    const ended = await crewPositionService.endShift(userId);
    expect(ended?.id).toBe(opened.id);
    expect(ended?.ended_at).toBeTruthy();
  });

  it('endShift returns null when no open shift exists', async () => {
    const userId = await makeUser('s4');
    const ended = await crewPositionService.endShift(userId);
    expect(ended).toBeNull();
  });

  it('getCurrentShift returns the open shift or null', async () => {
    const userId = await makeUser('s5');
    expect(await crewPositionService.getCurrentShift(userId)).toBeNull();
    const opened = await crewPositionService.startShift(userId, 'web');
    const fetched = await crewPositionService.getCurrentShift(userId);
    expect(fetched?.id).toBe(opened.id);
    await crewPositionService.endShift(userId);
    expect(await crewPositionService.getCurrentShift(userId)).toBeNull();
  });

  it('startShift after endShift creates a new shift (not the same row)', async () => {
    const userId = await makeUser('s6');
    const a = await crewPositionService.startShift(userId, 'android');
    await crewPositionService.endShift(userId);
    const b = await crewPositionService.startShift(userId, 'web');
    expect(b.id).not.toBe(a.id);
  });
});

describeDb('crewPositionService — positions', () => {
  beforeEach(cleanShiftsAndPositions);

  it('upsertPosition writes the row when an open shift exists', async () => {
    const userId = await makeUser('p1');
    await crewPositionService.startShift(userId, 'android');
    const pos = await crewPositionService.upsertPosition(userId, {
      lat: 40.7484,
      lng: -73.9856,
      accuracy_m: 12.5,
      battery_pct: 84,
    }, 'android');
    expect(pos.user_id).toBe(userId);
    expect(pos.latitude).toBeCloseTo(40.7484, 3);
    expect(pos.longitude).toBeCloseTo(-73.9856, 3);
    expect(pos.accuracy_m).toBeCloseTo(12.5, 1);
    expect(pos.battery_pct).toBe(84);
  });

  it('upsertPosition rejects when the user has no open shift', async () => {
    const userId = await makeUser('p2');
    await expect(
      crewPositionService.upsertPosition(userId, { lat: 0, lng: 0 }, 'web')
    ).rejects.toThrow(/no open shift/i);
  });

  it('upsertPosition updates the existing row (latest-only)', async () => {
    const userId = await makeUser('p3');
    await crewPositionService.startShift(userId, 'android');
    await crewPositionService.upsertPosition(userId, { lat: 1, lng: 2 }, 'android');
    await crewPositionService.upsertPosition(userId, { lat: 3, lng: 4 }, 'android');
    const count = await pg!.query("SELECT COUNT(*) FROM crew_positions WHERE user_id = $1", [userId]);
    expect(parseInt(count.rows[0].count, 10)).toBe(1);
    const row = await pg!.query("SELECT latitude, longitude FROM crew_positions WHERE user_id = $1", [userId]);
    expect(row.rows[0].latitude).toBeCloseTo(3, 5);
    expect(row.rows[0].longitude).toBeCloseTo(4, 5);
  });

  it('listOpenPositions returns rows for users with open shifts; skips others', async () => {
    const a = await makeUser('lp1');
    const b = await makeUser('lp2');
    const c = await makeUser('lp3');
    await crewPositionService.startShift(a, 'android');
    await crewPositionService.startShift(b, 'web');
    // c never starts a shift
    await crewPositionService.upsertPosition(a, { lat: 10, lng: 20 }, 'android');
    await crewPositionService.upsertPosition(b, { lat: 30, lng: 40 }, 'web');

    const list = await crewPositionService.listOpenPositions();
    const ids = list.map((p) => p.user_id);
    expect(ids).toContain(a);
    expect(ids).toContain(b);
    expect(ids).not.toContain(c);
  });

  it('listOpenPositions excludes users whose shift has ended', async () => {
    const a = await makeUser('lp4');
    await crewPositionService.startShift(a, 'android');
    await crewPositionService.upsertPosition(a, { lat: 1, lng: 2 }, 'android');
    await crewPositionService.endShift(a);

    const list = await crewPositionService.listOpenPositions();
    expect(list.map((p) => p.user_id)).not.toContain(a);
  });

  afterAll(async () => {
    await cleanShiftsAndPositions();
    await pg?.end();
  });
});
