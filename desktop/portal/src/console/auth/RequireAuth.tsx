import { ReactNode, useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { authClient } from './authClient';
import { useAuthStore } from './authStore';
import { LoadingState } from '../components/ui/StateViews';

interface Props {
  children: ReactNode;
}

type HydrationState = 'pending' | 'done';

export function RequireAuth({ children }: Props) {
  const user = useAuthStore((s) => s.user);
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
    return (
      <div className="font-mono p-8">
        <LoadingState label="Loading" />
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/console/login" replace />;
  }

  return <>{children}</>;
}
