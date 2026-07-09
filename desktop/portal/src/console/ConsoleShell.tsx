import { ReactNode, useEffect } from 'react';
import { SmithRail } from './layouts/SmithRail';
import { TopStrip } from './layouts/TopStrip';
import { BottomTabBar } from './layouts/BottomTabBar';
import { initSmithCore, isSmithCoreReady } from './core/smithCore';
import { useOfflinePersistence } from './offline/useOfflinePersistence';
import { registerOutboxDrain } from './offline/outbox';

interface Props {
  children: ReactNode;
}

export function ConsoleShell({ children }: Props) {
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
    <div className="h-screen flex font-mono">
      <SmithRail />
      <div className="flex-1 flex flex-col min-w-0">
        <TopStrip />
        {/* pb-20 keeps content above the 56px BottomTabBar below lg; resets
            to the original p-6 padding at lg+ where SmithRail replaces it
            and the bar is hidden. */}
        <main className="flex-1 min-h-0 overflow-y-auto p-6 pb-20 lg:pb-6">{children}</main>
        <BottomTabBar />
      </div>
    </div>
  );
}
