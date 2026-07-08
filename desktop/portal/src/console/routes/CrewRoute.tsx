// desktop/portal/src/console/routes/CrewRoute.tsx
import { useMemo } from 'react';
import { CrewCard } from '../components/crew/CrewCard';
import { useCrewRoster } from '../hooks/useCrewRoster';
import { useCrewStore } from '../stores/crewStore';
import { useCrewPositionsPolling } from '../hooks/useCrewPositionsPolling';
import { useCrewPositionsStore } from '../stores/crewPositionsStore';
import type { CrewPosition } from '../api/crewPositionsClient';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/StateViews';

export function CrewRoute() {
  const { reload } = useCrewRoster();
  useCrewPositionsPolling();
  const roster = useCrewStore((s) => s.roster);
  const isLoadingRoster = useCrewStore((s) => s.isLoadingRoster);
  const isStale = useCrewStore((s) => s.isStale);
  const positions = useCrewPositionsStore((s) => s.positions);

  // O(1) lookup by userId. Foreman+ get rows; lower roles get an empty
  // list (the polling hook silences 403), and the merge degrades cleanly.
  const positionByUserId = useMemo(() => {
    const m = new Map<string, CrewPosition>();
    for (const p of positions) m.set(p.userId, p);
    return m;
  }, [positions]);

  const onShiftCount = positions.length;

  // Precedence: loading -> error (no cached data to fall back on) -> empty -> data.
  if (isLoadingRoster && roster.length === 0) {
    return <LoadingState label="Loading crew" />;
  }

  if (isStale && roster.length === 0) {
    return (
      <div className="flex flex-col items-center mt-24 gap-4">
        <ErrorState message="Couldn't load crew." onRetry={reload} />
      </div>
    );
  }

  return (
    <div className="font-mono">
      <div className="flex flex-col items-start gap-2 md:flex-row md:items-center md:justify-between mb-4">
        <h1 className="text-sn-ink text-lg">Crew</h1>
        <span className="text-sn-ink-muted text-xs">
          {roster.length} member{roster.length === 1 ? '' : 's'}
          {onShiftCount > 0 && (
            <span className="text-sn-status-online"> &middot; {onShiftCount} on shift</span>
          )}
        </span>
      </div>
      {isStale && (
        <ErrorState message="Couldn't refresh roster — showing cached data." onRetry={reload} />
      )}
      {roster.length === 0 ? (
        <EmptyState title="No crew yet — assign someone to a job first." />
      ) : (
        <div className="border border-sn-line">
          {roster.map((entry) => (
            <CrewCard
              key={entry.id}
              entry={entry}
              position={positionByUserId.get(entry.id) ?? null}
            />
          ))}
        </div>
      )}
    </div>
  );
}
