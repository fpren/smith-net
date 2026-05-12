import { Response, NextFunction } from 'express';
import { AuthenticatedRequest, UserRole } from '../auth';

export const CONSOLE_ROLES: UserRole[] = [UserRole.FOREMAN, UserRole.ENTERPRISE, UserRole.ADMIN];

export function requireConsoleTier(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  if (!req.user) {
    return res.status(401).json({ error: 'Authentication required' });
  }
  if (!CONSOLE_ROLES.includes(req.user.role)) {
    return res.status(403).json({
      error: 'Console access requires Advanced tier',
      code: 'tier_required',
      currentRole: req.user.role,
    });
  }
  next();
}
