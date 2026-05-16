// desktop/portal/src/console/components/crew/CrewCard.tsx
import { Chip } from '../ui/Chip';
import { Avatar } from '../ui/Avatar';
import { AvailabilityDot } from './AvailabilityDot';
import { accentForId, colorForRole } from '../../lib/utils';
import type { CrewEntry } from '../../api/crewClient';
import type { CrewPosition } from '../../api/crewPositionsClient';

function formatAgo(recordedAt: string): string {
  const ms = Date.now() - new Date(recordedAt).getTime();
  const s = Math.max(0, Math.floor(ms / 1000));
  if (s < 60) return `${s}s ago`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  const d = Math.floor(h / 24);
  return `${d}d ago`;
}

interface Props {
  entry: CrewEntry;
  position?: CrewPosition | null;
}

export function CrewCard({ entry, position }: Props) {
  const availability = entry.activeJob !== null ? 'busy' : 'free';
  const onShift = !!position;
  return (
    <div className="grid grid-cols-[1.5ch_22px_1fr_20ch_8ch_1fr_16ch] gap-3 items-center px-3 py-2 border-b border-console-border text-sm font-mono">
      <AvailabilityDot availability={availability} />
      <Avatar name={entry.displayName} color={accentForId(entry.id)} size={22} />
      <span className="text-console-text">{entry.displayName}</span>
      <span className="text-console-text-muted truncate">{entry.email}</span>
      <Chip label={entry.role.toUpperCase()} color={colorForRole(entry.role)} xs />
      <span className="text-console-text-muted truncate">
        {entry.activeJob ? `on ${entry.activeJob.title}` : 'idle'}
      </span>
      <span
        className={onShift ? 'text-console-ok' : 'text-console-text-muted'}
        title={onShift ? `${position!.latitude.toFixed(4)}, ${position!.longitude.toFixed(4)}` : 'off shift'}
      >
        {onShift ? `[ON] ${formatAgo(position!.recordedAt)}` : '—'}
      </span>
    </div>
  );
}
