import { ReactNode, useEffect } from 'react';
import { AppHeader } from './layouts/AppHeader';
import { BottomTabBar } from './layouts/BottomTabBar';
import { ShiftClock } from './components/header/ShiftClock';
import { useAuthStore } from './auth/authStore';
import { initSmithCore, isSmithCoreReady } from './core/smithCore';
import { useOfflinePersistence } from './offline/useOfflinePersistence';
import { registerOutboxDrain } from './offline/outbox';

interface Props {
  children: ReactNode;
}

export function ConsoleShell({ children }: Props) {
  const user = useAuthStore((s) => s.user);
  useOfflinePersistence();

  // Load the deterministic ROM once when the console mounts. Soft-fail: no UI
  // consumes it yet (SP1 foundation), so a missing/old wasm just stays not-ready.
  useEffect(() => {
    void initSmithCore().then(() => {
      if (isSmithCoreReady()) console.info('[smithcore] ROM ready (ABI 3)');
    });
  }, []);

  // W6: replay queued offline writes on reconnect / focus / startup.
  useEffect(() => registerOutboxDrain(), []);

  return (
    <div className="h-screen flex flex-col font-mono">
      <AppHeader />
      {user && (
        <div className="border-b border-sn-line bg-sn-bg-panel px-4 py-2 flex items-center gap-3">
          <ShiftClock />
        </div>
      )}
      {/* pb-20 keeps content above the 56px BottomTabBar on mobile; resets
          to the original p-6 padding at md+ where the bar is hidden. */}
      <main className="flex-1 min-h-0 overflow-y-auto p-6 pb-20 md:pb-6">{children}</main>
      <BottomTabBar />
    </div>
  );
}
