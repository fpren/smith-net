import { adaptLayout, Surface } from './surface';
import { DemoJob } from './sampleJob';
import { AdaptiveCard } from './AdaptiveCard';
import { AppShell } from './AppShell';

// Dispatch on the surface's render mode: small -> a single feature (work card),
// large -> the whole app (nav + feature modules). adaptLayout decides; this only
// routes to the matching renderer.

interface Props {
  surface: Surface;
  job: DemoJob;
}

export function AdaptiveSurface({ surface, job }: Props) {
  const plan = adaptLayout(surface);
  if (plan.mode === 'app') return <AppShell surface={surface} />;
  return <AdaptiveCard surface={surface} job={job} />;
}
