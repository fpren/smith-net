import type { ReactNode } from 'react';

export function LoadingState({ label = 'Loading' }: { label?: string }): JSX.Element {
  return (
    <div
      className="flex flex-col items-center justify-center py-10 gap-4"
      role="status"
      aria-live="polite"
    >
      <div
        className="w-5 h-5 rounded-full border-2 border-sn-line border-t-sn-accent animate-spin motion-reduce:animate-none"
        aria-hidden="true"
      />
      <span className="font-data text-xs uppercase text-sn-ink-muted">{label}</span>
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
      <span className="font-data text-xs text-sn-attention">[x] {message}</span>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="rounded-sn-input px-4 py-2 font-data text-sm text-sn-ink-muted hover:text-sn-ink transition-opacity duration-sn-fast focus-visible:outline focus-visible:outline-2 focus-visible:outline-sn-accent"
        >
          RETRY
        </button>
      )}
    </div>
  );
}
