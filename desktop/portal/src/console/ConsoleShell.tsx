import { ReactNode } from 'react';
import { AppHeader } from './layouts/AppHeader';
import { ShareLocationToggle } from './components/header/ShareLocationToggle';
import { useAuthStore } from './auth/authStore';

interface Props {
  children: ReactNode;
}

export function ConsoleShell({ children }: Props) {
  const user = useAuthStore((s) => s.user);

  return (
    <div className="h-screen flex flex-col font-mono">
      <AppHeader />
      {user && (
        <div className="border-b border-console-border bg-console-surface px-4 py-1 flex justify-end">
          <ShareLocationToggle />
        </div>
      )}
      <main className="flex-1 min-h-0 overflow-y-auto p-6">{children}</main>
    </div>
  );
}
