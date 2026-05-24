import { AdaptiveDashboard } from '../components/adaptive-home/AdaptiveDashboard';

// PUBLIC preview of the real /console/home adaptive dashboard, so the re-fit can
// be tested without auth in dev. The real route is /console/home (behind
// RequireAuth). This composes the app's REAL feature screens (map / jobs / crew):
// the map renders from public tiles even with no backend; the job/crew lists read
// empty. Resize the window to watch it re-fit (multi-pane -> single screen).
export function SurfaceHomePreviewRoute() {
  return (
    <div className="h-screen w-screen flex flex-col bg-console-bg font-mono text-console-text">
      <div className="border-b border-console-border bg-console-surface px-3 py-1 text-[10px] text-console-text-muted shrink-0">
        adaptive home preview - resize to re-fit (real app screens; no backend in dev)
      </div>
      <div className="flex-1 min-h-0">
        <AdaptiveDashboard />
      </div>
    </div>
  );
}
