// backend/src/profilesRoutes.ts
import { Router, Response } from 'express';
import { authenticateToken, userStore, AuthenticatedRequest } from './auth';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { validateQuery } from './middleware/validate';
import { ProfileQuery } from './schemas/profiles';

export const profilesRouter = Router();

profilesRouter.use(authenticateToken, requireConsoleTier);

profilesRouter.get('/', validateQuery(ProfileQuery), (req: AuthenticatedRequest, res: Response) => {
  const q = ((req.query as unknown) as ProfileQuery).q;
  const needle = q.toLowerCase();
  const matches = userStore.getAllUsers()
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

console.log('[Profiles] routes initialized');
