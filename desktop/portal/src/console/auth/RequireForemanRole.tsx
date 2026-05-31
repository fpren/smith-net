// desktop/portal/src/console/auth/RequireForemanRole.tsx
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
  /**
   * Path to redirect non-foreman users to. Defaults to the Comm route since
   * Comm is the only surface workers can actually use today (Map and Jobs
   * are backend-gated at the tier middleware; landing on them shows broken
   * empty states). Override is provided for the index route to avoid an
   * infinite redirect when /console itself is foreman-only.
   */
  redirectTo?: string;
}

export function RequireForemanRole({ children, redirectTo = '/console/comm' }: Props) {
  const hasForemanRole = useAuthStore((s) => s.hasForemanRole);
  if (!hasForemanRole()) {
    return <Navigate to={redirectTo} replace />;
  }
  return <>{children}</>;
}
