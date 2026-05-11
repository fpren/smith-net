import { ReactNode, useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { authClient } from './authClient';
import { useAuthStore } from './authStore';
import { Card } from '../components/ui/Card';

interface Props {
  children: ReactNode;
}

type HydrationState = 'pending' | 'done';

export function RequireAuth({ children }: Props) {
  const user = useAuthStore((s) => s.user);
  const hasConsoleAccess = useAuthStore((s) => s.hasConsoleAccess);
  const setUser = useAuthStore((s) => s.setUser);
  const [hydration, setHydration] = useState<HydrationState>(user ? 'done' : 'pending');

  useEffect(() => {
    if (user) {
      setHydration('done');
      return;
    }
    let cancelled = false;
    authClient.me().then((result) => {
      if (cancelled) return;
      if (result.ok) setUser(result.user);
      setHydration('done');
    });
    return () => {
      cancelled = true;
    };
  }, [user, setUser]);

  if (hydration === 'pending') {
    return <div className="font-mono text-console-text-muted p-8">Loading...</div>;
  }

  if (!user) {
    return <Navigate to="/console/login" replace />;
  }

  if (!hasConsoleAccess()) {
    return (
      <Card title="Upgrade Required" className="max-w-md mx-auto mt-16">
        <p className="text-sm">
          The Console requires Advanced or Enterprise tier. Your current role is{' '}
          <span className="uppercase">{user.role}</span>.
        </p>
      </Card>
    );
  }

  return <>{children}</>;
}
