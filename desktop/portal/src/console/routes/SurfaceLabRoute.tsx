import { SurfaceLabContent } from '../components/surface-lab/SurfaceLabContent';

// Public, standalone demo route (no auth). Mounted at /surface-lab.
export function SurfaceLabRoute() {
  return (
    <div className="min-h-screen bg-sn-bg-base text-sn-ink p-6">
      <SurfaceLabContent />
    </div>
  );
}
