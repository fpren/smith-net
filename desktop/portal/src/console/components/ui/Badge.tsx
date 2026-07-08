import { HTMLAttributes } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

type Tone = 'default' | 'ok' | 'warn' | 'danger';

interface Props extends HTMLAttributes<HTMLSpanElement> {
  tone?: Tone;
}

const TONE_CLASSES: Record<Tone, string> = {
  default: 'bg-sn-bg-panel text-sn-ink border-sn-line',
  ok: 'bg-sn-bg-panel text-sn-status-online border-sn-status-online',
  warn: 'bg-sn-bg-panel text-sn-attention border-sn-attention',
  danger: 'bg-sn-bg-panel text-sn-status-error border-sn-status-error',
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
