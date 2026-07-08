import { HTMLAttributes, ReactNode } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

interface Props extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  title?: ReactNode;
}

export function Card({ title, className, children, ...rest }: Props) {
  return (
    <div
      className={twMerge(clsx('bg-sn-bg-panel border border-sn-line rounded-sn-card shadow-sn-sm p-4', className))}
      {...rest}
    >
      {title !== undefined && (
        <div className="font-mono text-xs uppercase tracking-wide text-sn-ink-muted mb-2">
          {title}
        </div>
      )}
      {children}
    </div>
  );
}
