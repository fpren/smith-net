import { useAdminHealth } from '../hooks/useAdminHealth';
import { useAdminHealthStore } from '../stores/adminHealthStore';
import type { WorkerHeartbeat } from '../api/adminHealthClient';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/StateViews';

function ageBadge(ageSec: number): { label: string; tone: 'ok' | 'warn' | 'danger' } {
  if (ageSec < 120) return { label: '[OK]', tone: 'ok' };
  if (ageSec < 300) return { label: '[STALE]', tone: 'warn' };
  return { label: '[DOWN]', tone: 'danger' };
}

function toneClass(tone: 'ok' | 'warn' | 'danger'): string {
  if (tone === 'ok') return 'text-sn-status-online';
  if (tone === 'warn') return 'text-sn-attention';
  return 'text-sn-status-error';
}

function WorkerRow({ w }: { w: WorkerHeartbeat }) {
  const badge = ageBadge(w.ageSec);
  return (
    <tr className="border-t border-sn-line">
      <td className="px-3 py-2 text-sn-ink">{w.workerId}</td>
      <td className="px-3 py-2 text-sn-ink-muted">{w.kinds.join(', ')}</td>
      <td className={`px-3 py-2 ${toneClass(badge.tone)}`}>
        {badge.label} {w.ageSec}s
      </td>
    </tr>
  );
}

export function AdminRoute() {
  const { reload } = useAdminHealth();
  const data = useAdminHealthStore((s) => s.data);
  const isLoading = useAdminHealthStore((s) => s.isLoading);
  const isStale = useAdminHealthStore((s) => s.isStale);
  const lastFetchedAt = useAdminHealthStore((s) => s.lastFetchedAt);

  // Precedence: loading -> error (no cached data to fall back on) -> data
  // (with an inline stale banner when a later poll fails) -- same shape as
  // CrewRoute/JobsCard etc.
  if (isLoading && !data) {
    return <LoadingState label="Loading health" />;
  }

  if (isStale && !data) {
    return (
      <div className="flex flex-col items-center mt-24 gap-4">
        <ErrorState message="Couldn't load admin health." onRetry={reload} />
      </div>
    );
  }

  return (
    <div className="font-mono">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-sn-ink text-lg">Admin / Health</h1>
        <span className="text-sn-ink-muted text-xs">
          {isLoading ? 'refreshing...' : lastFetchedAt
            ? `last refresh ${new Date(lastFetchedAt).toLocaleTimeString()}`
            : ''}
        </span>
      </div>

      {isStale && (
        <ErrorState message="Couldn't refresh health — showing cached data." onRetry={reload} />
      )}

      {data && (
        <>
          <section className="mb-6">
            <h2 className="text-sn-ink-muted text-xs uppercase tracking-wide mb-2">Workers</h2>
            {data.workers.length === 0 ? (
              <EmptyState
                title="[DOWN] No workers heartbeating."
                hint="Check that `npm run worker` is running."
              />
            ) : (
              <table className="w-full text-sm border border-sn-line">
                <thead className="bg-sn-bg-panel">
                  <tr>
                    <th className="text-left px-3 py-2 text-sn-ink-muted">Worker</th>
                    <th className="text-left px-3 py-2 text-sn-ink-muted">Kinds</th>
                    <th className="text-left px-3 py-2 text-sn-ink-muted">Age</th>
                  </tr>
                </thead>
                <tbody>
                  {data.workers.map((w) => <WorkerRow key={w.workerId} w={w} />)}
                </tbody>
              </table>
            )}
          </section>

          <section className="mb-6">
            <h2 className="text-sn-ink-muted text-xs uppercase tracking-wide mb-2">Queue</h2>
            {data.queue.byKindState.length === 0 ? (
              <EmptyState title="No background_jobs rows." />
            ) : (
              <table className="w-full text-sm border border-sn-line">
                <thead className="bg-sn-bg-panel">
                  <tr>
                    <th className="text-left px-3 py-2 text-sn-ink-muted">Kind</th>
                    <th className="text-left px-3 py-2 text-sn-ink-muted">State</th>
                    <th className="text-left px-3 py-2 text-sn-ink-muted">Count</th>
                  </tr>
                </thead>
                <tbody>
                  {data.queue.byKindState.map((r) => (
                    <tr key={`${r.kind}:${r.state}`} className="border-t border-sn-line">
                      <td className="px-3 py-2 text-sn-ink">{r.kind}</td>
                      <td className={`px-3 py-2 ${
                        r.state === 'dead' ? 'text-sn-status-error'
                        : r.state === 'failed' ? 'text-sn-attention'
                        : 'text-sn-ink-muted'
                      }`}>{r.state}</td>
                      <td className="px-3 py-2 text-sn-ink">{r.count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>

          <section className="grid grid-cols-2 gap-4">
            <div className="border border-sn-line p-3">
              <div className="text-sn-ink-muted text-xs uppercase tracking-wide mb-1">Oldest queued</div>
              {data.queue.oldestQueued ? (
                <div className="text-sm">
                  <span className="text-sn-ink">{data.queue.oldestQueued.kind}</span>
                  <span className="text-sn-ink-muted"> &middot; {data.queue.oldestQueued.ageSec}s</span>
                </div>
              ) : <div className="text-sn-ink-muted text-sm">none</div>}
            </div>
            <div className="border border-sn-line p-3">
              <div className="text-sn-ink-muted text-xs uppercase tracking-wide mb-1">Oldest running</div>
              {data.queue.oldestRunning ? (
                <div className="text-sm">
                  <span className="text-sn-ink">{data.queue.oldestRunning.kind}</span>
                  <span className="text-sn-ink-muted"> &middot; {data.queue.oldestRunning.ageSec}s</span>
                </div>
              ) : <div className="text-sn-ink-muted text-sm">none</div>}
            </div>
          </section>
        </>
      )}
    </div>
  );
}
