// desktop/portal/src/console/components/crew/AvailabilityDot.tsx
import type { Availability } from '../../stores/crewStore';

const COLOR: Record<Availability, string> = {
  free: 'bg-sn-status-online',
  busy: 'bg-sn-accent',
};

export function AvailabilityDot({ availability }: { availability: Availability }) {
  return (
    <span
      className={`inline-block w-2 h-2 rounded-full ${COLOR[availability]}`}
      aria-label={availability}
    />
  );
}
