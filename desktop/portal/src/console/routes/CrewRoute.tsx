// desktop/portal/src/console/routes/CrewRoute.tsx
import { CrewCard } from '../components/crew/CrewCard';
import { useCrewRoster } from '../hooks/useCrewRoster';
import { useCrewStore } from '../stores/crewStore';

export function CrewRoute() {
  useCrewRoster();
  const roster = useCrewStore((s) => s.roster);
  const isStale = useCrewStore((s) => s.isStale);

  return (
    <div className="font-mono">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-console-text text-lg">Crew</h1>
        <span className="text-console-text-muted text-xs">{roster.length} member{roster.length === 1 ? '' : 's'}</span>
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
          {roster.map((entry) => <CrewCard key={entry.id} entry={entry} />)}
        </div>
      )}
    </div>
  );
}
