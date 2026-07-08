// desktop/portal/src/console/components/map/MapFilterChips.tsx
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

export type FilterMode = 'active' | 'all';

interface Props {
  mode: FilterMode;
  onChange: (mode: FilterMode) => void;
}

const BASE = 'rounded-full px-3 py-1 text-xs font-mono border';
const ACTIVE = 'bg-sn-accent text-sn-ink-on-accent border-sn-accent';
const INACTIVE = 'bg-sn-bg-panel text-sn-ink border-sn-line hover:bg-sn-bg-base';

export function MapFilterChips({ mode, onChange }: Props) {
  return (
    <div className="flex gap-2">
      <button
        type="button"
        onClick={() => onChange('active')}
        className={twMerge(clsx(BASE, mode === 'active' ? ACTIVE : INACTIVE))}
      >
        active only
      </button>
      <button
        type="button"
        onClick={() => onChange('all')}
        className={twMerge(clsx(BASE, mode === 'all' ? ACTIVE : INACTIVE))}
      >
        all
      </button>
    </div>
  );
}
