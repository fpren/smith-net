import { Card } from '../components/ui/Card';
import { useAuthStore } from '../auth/authStore';

export function PlaceholderConsoleRoute() {
  const user = useAuthStore((s) => s.user);
  if (!user) return null;

  return (
    <Card title="Console — Foundation Ship" className="max-w-2xl">
      <p className="text-sm mb-4">Welcome, {user.displayName}. The chassis is up.</p>
      <dl className="text-sm grid grid-cols-[10ch_1fr] gap-y-1">
        <dt className="text-console-text-muted">email</dt>
        <dd>{user.email}</dd>
        <dt className="text-console-text-muted">role</dt>
        <dd className="uppercase">{user.role}</dd>
        <dt className="text-console-text-muted">verified</dt>
        <dd>{user.emailVerified ? 'yes' : 'no'}</dd>
      </dl>
      <p className="text-xs text-console-text-muted mt-6">
        Next: backend endpoint gap-fill (Plan 2), then WS + Job Board (Plan 3).
      </p>
    </Card>
  );
}
