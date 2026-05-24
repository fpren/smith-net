import { AdaptiveDashboard } from '../components/adaptive-home/AdaptiveDashboard';

// Authenticated /console/home: the adaptive surface on real data. Fills the
// ConsoleShell <main> content area so it can measure + re-fit to its container.
export function AdaptiveHomeRoute() {
  return (
    <div className="h-full">
      <AdaptiveDashboard />
    </div>
  );
}
