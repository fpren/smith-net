import { ButtonHTMLAttributes } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

// A small pill-shaped action/toggle button -- the matching shape for inline
// actions (replaces the older "[bracket]" text buttons). `active` fills it with
// the accent (selected state); `tone='ok'` is the confirm/positive variant.
interface PillProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  active?: boolean;
  tone?: 'default' | 'ok';
}

export function Pill({ active = false, tone = 'default', className, children, ...rest }: PillProps) {
  return (
    <button
      type="button"
      className={twMerge(
        clsx(
          'rounded-full border px-3 py-1 font-mono text-[11px] whitespace-nowrap transition-colors disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-sn-accent',
          active
            ? 'bg-sn-accent text-sn-ink-on-accent border-sn-accent'
            : tone === 'ok'
              ? 'border-sn-status-online text-sn-status-online hover:bg-sn-status-online hover:text-sn-bg-base'
              : 'border-sn-line text-sn-ink-muted hover:text-sn-accent hover:border-sn-accent',
          className,
        ),
      )}
      {...rest}
    >
      {children}
    </button>
  );
}
