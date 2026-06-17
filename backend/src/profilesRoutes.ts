// backend/src/profilesRoutes.ts
import { Router, Response } from 'express';
import { authenticateToken, userStore, AuthenticatedRequest } from './auth';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { validateQuery } from './middleware/validate';
import { ProfileQuery } from './schemas/profiles';
import { pg, isPgEnabled } from './db';

export const profilesRouter = Router();

// All directory routes require a valid token. The console-tier gate is applied
// only to /crew (the foreman roster) -- directory search / lookup / teammates
// are available to every authenticated user (W3 parity decision).
profilesRouter.use(authenticateToken);

/** Shared row -> API shape for the pg-backed directory endpoints. */
function mapProfile(r: any) {
  return {
    id: r.id,
    email: r.email,
    displayName: r.display_name,
    role: r.role,
    publicId: r.public_id ?? null,
    avatarUrl: r.avatar_url ?? null,
    organizationId: r.organization_id ?? null,
  };
}

// Name/email people search. Directory info only (name / email / role) -- the
// org-fenced data (jobs, invoices, time) lives behind its own services, so this
// is intentionally cross-org for people discovery, matching the prior behavior.
// Available to every authenticated user now (tier gate moved to /crew).
profilesRouter.get('/', validateQuery(ProfileQuery), async (req: AuthenticatedRequest, res: Response) => {
  const q = ((req.query as unknown) as ProfileQuery).q;
  const needle = q.toLowerCase();
  const all = await userStore.getAllUsers();
  const matches = all
    .filter((u) =>
      u.isActive &&
      (u.email.toLowerCase().includes(needle) || u.displayName.toLowerCase().includes(needle))
    )
    .slice(0, 20)
    .map((u) => ({
      id: u.id,
      email: u.email,
      displayName: u.displayName,
      role: u.role,
    }));
  res.json({ profiles: matches });
});

// The caller's own profile. toPublicUser() (auth.ts) intentionally does not
// carry public_id / avatar_url — widening it touches every auth flow — so the
// comm surface fetches them here to render the user's own id card + photo.
profilesRouter.get('/me', async (req: AuthenticatedRequest, res: Response) => {
  const selfId = req.user!.id;
  if (!isPgEnabled() || !pg) return res.json({ profile: null });
  try {
    const { rows } = await pg.query(
      `SELECT id, email, display_name, role, public_id, avatar_url, organization_id
         FROM profiles WHERE id = $1 LIMIT 1`,
      [selfId]
    );
    res.json({ profile: rows[0] ? mapProfile(rows[0]) : null });
  } catch (e: any) {
    console.error('[Profiles] me error:', e.message);
    res.status(500).json({ error: 'Failed to load profile' });
  }
});

// Lookup by shared 8-char public handle. Intentionally cross-org: a public_id is
// how you add someone you don't already share an org with.
profilesRouter.get('/lookup', async (req: AuthenticatedRequest, res: Response) => {
  const publicId = String((req.query as any).publicId ?? '').toUpperCase();
  if (!/^[A-Z0-9]{8}$/.test(publicId)) {
    return res.status(400).json({ error: 'publicId must be 8 alphanumeric characters' });
  }
  if (!isPgEnabled() || !pg) return res.json({ profile: null });
  try {
    const { rows } = await pg.query(
      `SELECT id, email, display_name, role, public_id, avatar_url, organization_id
         FROM profiles WHERE public_id = $1 LIMIT 1`,
      [publicId]
    );
    res.json({ profile: rows[0] ? mapProfile(rows[0]) : null });
  } catch (e: any) {
    console.error('[Profiles] lookup error:', e.message);
    res.status(500).json({ error: 'Lookup failed' });
  }
});

// Everyone in the caller's organization (excluding self).
profilesRouter.get('/teammates', async (req: AuthenticatedRequest, res: Response) => {
  const orgId = req.user!.organizationId;
  const selfId = req.user!.id;
  if (!isPgEnabled() || !pg) return res.json({ profiles: [] });
  try {
    const { rows } = await pg.query(
      `SELECT id, email, display_name, role, public_id, avatar_url, organization_id
         FROM profiles
        WHERE organization_id = $1 AND id <> $2
        ORDER BY display_name
        LIMIT 50`,
      [orgId, selfId]
    );
    res.json({ profiles: rows.map(mapProfile) });
  } catch (e: any) {
    console.error('[Profiles] teammates error:', e.message);
    res.status(500).json({ error: 'Failed to load teammates' });
  }
});

// Foreman crew roster -- console tier only.
profilesRouter.get('/crew', requireConsoleTier, async (req: AuthenticatedRequest, res: Response) => {
  const foremanId = req.user!.id;
  if (!isPgEnabled() || !pg) {
    return res.json({ crew: [] });
  }
  try {
    const { rows } = await pg.query(
      `SELECT DISTINCT
         p.id, p.email, p.display_name, p.role, p.avatar_url,
         ij.id AS active_job_id, ij.title AS active_job_title, ij.status AS active_job_status
       FROM profiles p
       INNER JOIN job_crew jc ON jc.profile_id = p.id
       INNER JOIN jobs j ON j.id = jc.job_id AND j.foreman_id = $1
       LEFT JOIN LATERAL (
         SELECT j2.id, j2.title, j2.status
         FROM jobs j2
         INNER JOIN job_crew jc2 ON jc2.job_id = j2.id AND jc2.profile_id = p.id
         WHERE j2.foreman_id = $1 AND j2.status = 'in_progress'
         ORDER BY j2.updated_at DESC
         LIMIT 1
       ) ij ON true
       ORDER BY p.display_name`,
      [foremanId]
    );
    res.json({
      crew: rows.map((r) => ({
        id: r.id,
        email: r.email,
        displayName: r.display_name,
        role: r.role,
        avatarUrl: r.avatar_url ?? null,
        activeJob: r.active_job_id
          ? { id: r.active_job_id, title: r.active_job_title, status: r.active_job_status }
          : null,
      })),
    });
  } catch (e: any) {
    console.error('[Profiles] crew roster error:', e.message);
    res.status(500).json({ error: 'Failed to load crew roster' });
  }
});

console.log('[Profiles] routes initialized');
