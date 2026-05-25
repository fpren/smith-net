import type { TimeEntryRow } from '../../api/presenceClient';

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
    return <div className="text-console-text-muted text-sm">No entries today.</div>;
  }
  return (
    <div className="flex flex-col gap-1.5 font-mono text-[13px] text-console-text">
      {entries.map((e) => (
        <div key={e.id} className="flex items-baseline gap-2">
          <span className="tabular-nums text-console-text-muted">
            {e.startedAt ? hm(e.startedAt) : '--:--'} - {e.endedAt ? hm(e.endedAt) : <span className="text-console-ok">NOW</span>}
          </span>
          <span className="uppercase text-[11px]">{e.entryType}</span>
          {e.jobTitle && <span className="text-console-accent truncate">@{e.jobTitle}</span>}
          {e.clockOutReason && <span className="text-console-text-muted text-[11px] truncate">- {e.clockOutReason}</span>}
          {e.startedAt && (
            <span className="ml-auto tabular-nums font-medium">{durationHMM(e.startedAt, e.endedAt)}</span>
          )}
        </div>
      ))}
    </div>
  );
}
