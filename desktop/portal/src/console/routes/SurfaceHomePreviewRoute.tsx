import { AdaptiveDashboard } from '../components/adaptive-home/AdaptiveDashboard';

// PUBLIC preview of the real /console/home adaptive dashboard, so it can be
// tested without auth/backend in dev. The real route is /console/home (behind
// RequireAuth). Here the panels read [OFFLINE]/empty (no backend); resize the
// window to test the adaptive re-fit (grid -> single feature -> glance).
export function SurfaceHomePreviewRoute() {
  return (
    <div className="h-screen w-screen flex flex-col bg-console-bg font-mono text-console-text">
      <div className="border-b border-console-border bg-console-surface px-4 py-2 text-[11px] text-console-text-muted">
        ADAPTIVE HOME - preview. Resize the window to watch it re-fit (panel grid -&gt; single feature -&gt; glance).
        No backend in dev, so panels read [OFFLINE]/empty - structure + adaptation are what this previews.
      </div>
      <div className="flex-1 min-h-0 p-4">
        <AdaptiveDashboard />
      </div>
    </div>
  );
}
