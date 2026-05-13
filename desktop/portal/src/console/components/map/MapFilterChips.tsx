// desktop/portal/src/console/components/map/MapFilterChips.tsx
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

export type FilterMode = 'active' | 'all';

interface Props {
  mode: FilterMode;
  onChange: (mode: FilterMode) => void;
}

const BASE = 'px-3 py-1 text-xs font-mono border';
const ACTIVE = 'bg-console-accent text-white border-console-accent';
const INACTIVE = 'bg-console-surface text-console-text border-console-border hover:bg-console-bg';

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
