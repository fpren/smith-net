import { HTMLAttributes, ReactNode } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

interface Props extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  title?: ReactNode;
}

export function Card({ title, className, children, ...rest }: Props) {
  return (
    <div
      className={twMerge(clsx('bg-console-surface border border-console-border p-4', className))}
      {...rest}
    >
      {title !== undefined && (
        <div className="font-mono text-xs uppercase tracking-wide text-console-text-muted mb-2">
          {title}
        </div>
      )}
      {children}
    </div>
  );
}
