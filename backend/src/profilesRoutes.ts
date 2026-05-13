// backend/src/profilesRoutes.ts
import { Router, Response } from 'express';
import { authenticateToken, userStore, AuthenticatedRequest } from './auth';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { validateQuery } from './middleware/validate';
import { ProfileQuery } from './schemas/profiles';
import { pg, isPgEnabled } from './db';

export const profilesRouter = Router();

profilesRouter.use(authenticateToken, requireConsoleTier);

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

profilesRouter.get('/crew', async (req: AuthenticatedRequest, res: Response) => {
  const foremanId = req.user!.id;
  if (!isPgEnabled() || !pg) {
    return res.json({ crew: [] });
  }
  try {
    const { rows } = await pg.query(
      `SELECT DISTINCT
         p.id, p.email, p.display_name, p.role,
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
