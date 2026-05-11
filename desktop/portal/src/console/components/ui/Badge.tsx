import { HTMLAttributes } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

type Tone = 'default' | 'ok' | 'warn' | 'danger';

interface Props extends HTMLAttributes<HTMLSpanElement> {
  tone?: Tone;
}

const TONE_CLASSES: Record<Tone, string> = {
  default: 'bg-console-surface text-console-text border-console-border',
  ok: 'bg-console-surface text-console-ok border-console-ok',
  warn: 'bg-console-surface text-console-warn border-console-warn',
  danger: 'bg-console-surface text-console-danger border-console-danger',
};

export function Badge({ tone = 'default', className, children, ...rest }: Props) {
  return (
    <span
      className={twMerge(
        clsx(
          'inline-block border px-2 py-0.5 text-xs font-mono uppercase tracking-wide',
          TONE_CLASSES[tone],
          className
        )
      )}
      {...rest}
    >
      {children}
    </span>
  );
}
