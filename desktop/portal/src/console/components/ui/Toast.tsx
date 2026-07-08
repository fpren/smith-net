import { useEffect } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';
import { useToastStore, ToastEntry, ToastTone } from '../../stores/toastStore';

const TONE_CLASSES: Record<ToastTone, string> = {
  info: 'bg-sn-bg-panel text-sn-ink border-sn-line',
  error: 'bg-sn-bg-panel border-sn-status-error text-sn-status-error',
};

function ToastItem({ toast }: { toast: ToastEntry }) {
  const dismiss = useToastStore((s) => s.dismiss);
  useEffect(() => {
    const id = setTimeout(() => dismiss(toast.id), toast.duration);
    return () => clearTimeout(id);
  }, [toast.id, toast.duration, dismiss]);

  return (
    <div
      role="status"
      className={twMerge(
        clsx(
          'border px-4 py-2 font-mono text-sm flex items-start gap-3 min-w-[280px] max-w-[480px]',
          TONE_CLASSES[toast.tone]
        )
      )}
    >
      <span className="flex-1">{toast.message}</span>
      <button
        aria-label="Dismiss"
        className="text-sn-ink-muted hover:text-sn-ink font-mono focus-visible:outline focus-visible:outline-2 focus-visible:outline-sn-accent"
        onClick={() => dismiss(toast.id)}
      >
        [x]
      </button>
    </div>
  );
}

export function ToastContainer() {
  const toasts = useToastStore((s) => s.toasts);
  if (toasts.length === 0) return null;
  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
      {toasts.map((t) => (
        <ToastItem key={t.id} toast={t} />
      ))}
    </div>
  );
}
