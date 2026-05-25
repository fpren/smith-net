import { ReactNode, useEffect } from 'react';
import { AppHeader } from './layouts/AppHeader';
import { BottomTabBar } from './layouts/BottomTabBar';
import { ShareLocationToggle } from './components/header/ShareLocationToggle';
import { ClockButton } from './components/header/ClockButton';
import { useAuthStore } from './auth/authStore';
import { initSmithCore, isSmithCoreReady } from './core/smithCore';
import { useCurrentTime } from './hooks/useCurrentTime';
import { useOfflinePersistence } from './offline/useOfflinePersistence';

interface Props {
  children: ReactNode;
}

export function ConsoleShell({ children }: Props) {
  const user = useAuthStore((s) => s.user);
  const { hh, mm, ss } = useCurrentTime();
  useOfflinePersistence();

  // Load the deterministic ROM once when the console mounts. Soft-fail: no UI
  // consumes it yet (SP1 foundation), so a missing/old wasm just stays not-ready.
  useEffect(() => {
    void initSmithCore().then(() => {
      if (isSmithCoreReady()) console.info('[smithcore] ROM ready (ABI 3)');
    });
  }, []);

  return (
    <div className="h-screen flex flex-col font-mono">
      <AppHeader />
      {user && (
        <div className="border-b border-console-border bg-console-surface px-4 py-2 flex items-center justify-between gap-3">
          {/* Clock in its own container (APK-style shift module) */}
          <div
            role="group"
            aria-label="shift"
            className="flex items-center gap-3 bg-console-bg border border-console-border rounded-md px-3 py-1.5"
          >
            <span className="text-console-text text-sm tabular-nums" style={{ fontFamily: 'var(--font-mono)' }}>
              {hh}:{mm}
              <span className="text-console-text-muted text-xs tabular-nums">:{ss}</span>
            </span>
            <span className="text-console-border" aria-hidden="true">|</span>
            <ClockButton />
          </div>
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
