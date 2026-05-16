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
  afterAll(async () => {
    await cleanShiftsAndPositions();
    await pg?.end();
  });

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
