import type { ReactNode } from 'react';
import { clsx } from 'clsx';

export function LoadingState({ label = 'Loading' }: { label?: string }): JSX.Element {
  return (
    <div
      className="flex flex-col items-center justify-center py-10 gap-4"
      role="status"
      aria-live="polite"
    >
      <div
        className="w-5 h-5 border-2 border-sn-line border-t-sn-accent animate-spin motion-reduce:animate-none"
        aria-hidden="true"
      />
      <span className={clsx('font-data text-xs uppercase text-sn-ink-muted')}>
        {label}
      </span>
    </div>
  );
}

export function EmptyState({
  title,
  hint,
  action,
}: {
  title: string;
  hint?: string;
  action?: ReactNode;
}): JSX.Element {
  return (
    <div className="flex flex-col items-center justify-center py-12 gap-4">
      <span className="text-sm text-sn-ink-muted">{title}</span>
      {hint && <span className="text-xs text-sn-ink-muted/70">{hint}</span>}
      {action && <div>{action}</div>}
    </div>
  );
}

export function ErrorState({
  message = "Couldn't load this.",
  onRetry,
}: {
  message?: string;
  onRetry?: () => void;
}): JSX.Element {
  return (
    <div
      className="flex flex-col items-center justify-center py-10 gap-4"
      role="alert"
    >
      <span className={clsx('font-data text-xs text-sn-attention')}>
        [x] {message}
      </span>
      {onRetry && (
        <button
          onClick={onRetry}
          className={clsx(
            'font-mono text-xs uppercase px-3 py-1.5 bg-transparent border border-sn-line text-sn-ink hover:bg-sn-bg-panel transition-colors',
            'focus-visible:outline focus-visible:outline-2 focus-visible:outline-sn-accent'
          )}
        >
          RETRY
        </button>
      )}
    </div>
  );
}
