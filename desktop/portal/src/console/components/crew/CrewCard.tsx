// desktop/portal/src/console/components/crew/CrewCard.tsx
import { Badge } from '../ui/Badge';
import { AvailabilityDot } from './AvailabilityDot';
import type { CrewEntry } from '../../api/crewClient';

export function CrewCard({ entry }: { entry: CrewEntry }) {
  const availability = entry.activeJob !== null ? 'busy' : 'free';
  return (
    <div className="grid grid-cols-[1.5ch_1fr_20ch_8ch_1fr] gap-3 items-center px-3 py-2 border-b border-console-border text-sm font-mono">
      <AvailabilityDot availability={availability} />
      <span className="text-console-text">{entry.displayName}</span>
      <span className="text-console-text-muted truncate">{entry.email}</span>
      <Badge tone="default">{entry.role}</Badge>
      <span className="text-console-text-muted truncate">
        {entry.activeJob ? `on ${entry.activeJob.title}` : 'idle'}
      </span>
    </div>
  );
}
