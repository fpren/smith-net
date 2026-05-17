// desktop/portal/src/console/auth/RequireForemanTier.tsx
//
// Per-route gate for surfaces that don't make sense for individual workers
// (Crew roster + management). Non-foreman roles get redirected to /console
// (Map). Mirrors RequireAdmin in shape and intent.
//
// Backend already filters /api/profiles/crew etc. by role, so this guard
// is a UX layer — it prevents a worker from landing on an empty Crew page
// they have no use for.

import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore } from './authStore';

interface Props {
  children: ReactNode;
}

export function RequireForemanTier({ children }: Props) {
  const hasForemanTier = useAuthStore((s) => s.hasForemanTier);
  if (!hasForemanTier()) {
    return <Navigate to="/console" replace />;
  }
  return <>{children}</>;
}
