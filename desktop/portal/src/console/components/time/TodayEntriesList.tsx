import type { TimeEntryRow } from '../../api/presenceClient';
import { EmptyState } from '../ui/StateViews';

function hm(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
}
function durationHMM(startIso: string, endIso: string | null): string {
  const end = endIso ? new Date(endIso).getTime() : Date.now();
  const mins = Math.max(0, Math.floor((end - new Date(startIso).getTime()) / 60000));
  return `${Math.floor(mins / 60)}:${String(mins % 60).padStart(2, '0')}`;
}

export function TodayEntriesList({ entries }: { entries: TimeEntryRow[] }) {
  if (entries.length === 0) {
    return <EmptyState title="No entries today." />;
  }
  return (
    <div className="flex flex-col gap-1.5 font-mono text-[13px] text-sn-ink">
      {entries.map((e) => (
        <div key={e.id} className="flex items-baseline gap-2">
          <span className="tabular-nums text-sn-ink-muted shrink-0">
            {e.startedAt ? hm(e.startedAt) : '--:--'} - {e.endedAt ? hm(e.endedAt) : <span className="text-sn-status-online">NOW</span>}
          </span>
          <span className="uppercase text-[11px] shrink-0">{e.entryType}</span>
          {/* job / task / reason share the remaining space and truncate on narrow screens */}
          <span className="flex items-baseline gap-2 min-w-0 flex-1 overflow-hidden">
            {e.jobTitle && <span className="text-sn-accent truncate">@{e.jobTitle}</span>}
            {e.taskTitle && <span className="text-sn-ink-muted truncate">/ {e.taskTitle}</span>}
            {e.clockOutReason && <span className="text-sn-ink-muted text-[11px] truncate">- {e.clockOutReason}</span>}
          </span>
          {e.startedAt && (
            <span className="tabular-nums font-medium shrink-0">{durationHMM(e.startedAt, e.endedAt)}</span>
          )}
        </div>
      ))}
    </div>
  );
}
