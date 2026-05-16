import { useAdminHealth } from '../hooks/useAdminHealth';
import { useAdminHealthStore } from '../stores/adminHealthStore';
import type { WorkerHeartbeat } from '../api/adminHealthClient';

function ageBadge(ageSec: number): { label: string; tone: 'ok' | 'warn' | 'danger' } {
  if (ageSec < 120) return { label: '[OK]', tone: 'ok' };
  if (ageSec < 300) return { label: '[STALE]', tone: 'warn' };
  return { label: '[DOWN]', tone: 'danger' };
}

function toneClass(tone: 'ok' | 'warn' | 'danger'): string {
  if (tone === 'ok') return 'text-console-ok';
  if (tone === 'warn') return 'text-console-warn';
  return 'text-console-danger';
}

function WorkerRow({ w }: { w: WorkerHeartbeat }) {
  const badge = ageBadge(w.ageSec);
  return (
    <tr className="border-t border-console-border">
      <td className="px-3 py-2 text-console-text">{w.workerId}</td>
      <td className="px-3 py-2 text-console-text-muted">{w.kinds.join(', ')}</td>
      <td className={`px-3 py-2 ${toneClass(badge.tone)}`}>
        {badge.label} {w.ageSec}s
      </td>
    </tr>
  );
}

export function AdminRoute() {
  useAdminHealth();
  const data = useAdminHealthStore((s) => s.data);
  const isLoading = useAdminHealthStore((s) => s.isLoading);
  const isStale = useAdminHealthStore((s) => s.isStale);
  const lastFetchedAt = useAdminHealthStore((s) => s.lastFetchedAt);

  return (
    <div className="font-mono">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-console-text text-lg">Admin / Health</h1>
        <span className="text-console-text-muted text-xs">
          {isStale ? '[OFFLINE] last fetch failed' : isLoading ? 'refreshing...' : lastFetchedAt
            ? `last refresh ${new Date(lastFetchedAt).toLocaleTimeString()}`
            : ''}
        </span>
      </div>

      {!data ? (
        <div className="text-console-text-muted text-sm">Loading health...</div>
      ) : (
        <>
          <section className="mb-6">
            <h2 className="text-console-text-muted text-xs uppercase tracking-wide mb-2">Workers</h2>
            {data.workers.length === 0 ? (
              <div className="text-console-danger text-sm">
                [DOWN] No workers heartbeating. Check that `npm run worker` is running.
              </div>
            ) : (
              <table className="w-full text-sm border border-console-border">
                <thead className="bg-console-surface">
                  <tr>
                    <th className="text-left px-3 py-2 text-console-text-muted">Worker</th>
                    <th className="text-left px-3 py-2 text-console-text-muted">Kinds</th>
                    <th className="text-left px-3 py-2 text-console-text-muted">Age</th>
                  </tr>
                </thead>
                <tbody>
                  {data.workers.map((w) => <WorkerRow key={w.workerId} w={w} />)}
                </tbody>
              </table>
            )}
          </section>

          <section className="mb-6">
            <h2 className="text-console-text-muted text-xs uppercase tracking-wide mb-2">Queue</h2>
            {data.queue.byKindState.length === 0 ? (
              <div className="text-console-text-muted text-sm">No background_jobs rows.</div>
            ) : (
              <table className="w-full text-sm border border-console-border">
                <thead className="bg-console-surface">
                  <tr>
                    <th className="text-left px-3 py-2 text-console-text-muted">Kind</th>
                    <th className="text-left px-3 py-2 text-console-text-muted">State</th>
                    <th className="text-left px-3 py-2 text-console-text-muted">Count</th>
                  </tr>
                </thead>
                <tbody>
                  {data.queue.byKindState.map((r) => (
                    <tr key={`${r.kind}:${r.state}`} className="border-t border-console-border">
                      <td className="px-3 py-2 text-console-text">{r.kind}</td>
                      <td className={`px-3 py-2 ${
                        r.state === 'dead' ? 'text-console-danger'
                        : r.state === 'failed' ? 'text-console-warn'
                        : 'text-console-text-muted'
                      }`}>{r.state}</td>
                      <td className="px-3 py-2 text-console-text">{r.count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>

          <section className="grid grid-cols-2 gap-4">
            <div className="border border-console-border p-3">
              <div className="text-console-text-muted text-xs uppercase tracking-wide mb-1">Oldest queued</div>
              {data.queue.oldestQueued ? (
                <div className="text-sm">
                  <span className="text-console-text">{data.queue.oldestQueued.kind}</span>
                  <span className="text-console-text-muted"> &middot; {data.queue.oldestQueued.ageSec}s</span>
                </div>
              ) : <div className="text-console-text-muted text-sm">none</div>}
            </div>
            <div className="border border-console-border p-3">
              <div className="text-console-text-muted text-xs uppercase tracking-wide mb-1">Oldest running</div>
              {data.queue.oldestRunning ? (
                <div className="text-sm">
                  <span className="text-console-text">{data.queue.oldestRunning.kind}</span>
                  <span className="text-console-text-muted"> &middot; {data.queue.oldestRunning.ageSec}s</span>
                </div>
              ) : <div className="text-console-text-muted text-sm">none</div>}
            </div>
          </section>
        </>
      )}
    </div>
  );
}
