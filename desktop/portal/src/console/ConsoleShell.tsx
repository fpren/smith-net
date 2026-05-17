import { ReactNode } from 'react';
import { AppHeader } from './layouts/AppHeader';
import { BottomTabBar } from './layouts/BottomTabBar';
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
      {/* pb-20 keeps content above the 56px BottomTabBar on mobile; resets
          to the original p-6 padding at md+ where the bar is hidden. */}
      <main className="flex-1 min-h-0 overflow-y-auto p-6 pb-20 md:pb-6">{children}</main>
      <BottomTabBar />
    </div>
  );
}
