import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore } from './authStore';

interface Props {
  children: ReactNode;
}

/**
 * Stricter than RequireAuth: only the admin role passes. Foreman/enterprise
 * users redirect back to /console (the map). Mirrors the backend gate at
 * GET /api/admin/health.
 *
 * Assumes RequireAuth has already hydrated the user (we are below it in
 * the route tree).
 */
export function RequireAdmin({ children }: Props) {
  const user = useAuthStore((s) => s.user);
  if (!user || user.role !== 'admin') {
    return <Navigate to="/console" replace />;
  }
  return <>{children}</>;
}
