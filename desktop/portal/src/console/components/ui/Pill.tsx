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
          'rounded-full border px-3 py-1 font-mono text-[11px] whitespace-nowrap transition-colors disabled:opacity-50',
          active
            ? 'bg-console-accent text-white border-console-accent'
            : tone === 'ok'
              ? 'border-console-ok text-console-ok hover:bg-console-ok hover:text-console-bg'
              : 'border-console-border text-console-text-muted hover:text-console-accent hover:border-console-accent',
          className,
        ),
      )}
      {...rest}
    >
      {children}
    </button>
  );
}
