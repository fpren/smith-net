// desktop/portal/src/console/routes/CrewRoute.tsx
import { useMemo } from 'react';
import { CrewCard } from '../components/crew/CrewCard';
import { useCrewRoster } from '../hooks/useCrewRoster';
import { useCrewStore } from '../stores/crewStore';
import { useCrewPositionsPolling } from '../hooks/useCrewPositionsPolling';
import { useCrewPositionsStore } from '../stores/crewPositionsStore';
import type { CrewPosition } from '../api/crewPositionsClient';

export function CrewRoute() {
  useCrewRoster();
  useCrewPositionsPolling();
  const roster = useCrewStore((s) => s.roster);
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

  return (
    <div className="font-mono">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-console-text text-lg">Crew</h1>
        <span className="text-console-text-muted text-xs">
          {roster.length} member{roster.length === 1 ? '' : 's'}
          {onShiftCount > 0 && (
            <span className="text-console-ok"> &middot; {onShiftCount} on shift</span>
          )}
        </span>
      </div>
      {isStale && (
        <div className="bg-console-surface border border-console-warn text-console-warn px-3 py-1 text-xs mb-3">
          [OFFLINE] Couldn't refresh roster
        </div>
      )}
      {roster.length === 0 ? (
        <div className="text-console-text-muted text-sm">No crew yet — assign someone to a job first.</div>
      ) : (
        <div className="border border-console-border">
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
