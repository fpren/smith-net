import { ReactNode, useEffect, useRef } from 'react';

interface SmithDialogProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  /** Destructive dialogs cannot be dismissed by tapping outside. */
  destructive?: boolean;
}

export function SmithDialog({ open, onClose, title, children, footer, destructive = false }: SmithDialogProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const restoreRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) return;
    restoreRef.current = document.activeElement as HTMLElement | null;
    // Focus the first focusable control, else the panel itself.
    const panel = panelRef.current;
    const first = panel?.querySelector<HTMLElement>(
      '[data-autofocus], button, [href], input, select, textarea',
    );
    (first ?? panel)?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      restoreRef.current?.focus?.();
    };
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div
      data-testid="sn-dialog-backdrop"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={destructive ? undefined : onClose}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        tabIndex={-1}
        className="w-full max-w-md rounded-sn-card bg-sn-bg-panel text-sn-ink shadow-sn-md outline-none"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-5 pt-4 pb-2 font-semibold">{title}</div>
        <div className="px-5 pb-4 text-sm">{children}</div>
        {footer && <div className="flex justify-end gap-2 px-5 pb-4">{footer}</div>}
      </div>
    </div>
  );
}

interface ConfirmDialogProps {
  open: boolean;
  title: ReactNode;
  body: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({ open, title, body, confirmLabel, cancelLabel = 'Cancel', onConfirm, onCancel }: ConfirmDialogProps) {
  return (
    <SmithDialog
      open={open}
      onClose={onCancel}
      title={title}
      destructive
      footer={
        <>
          <button
            type="button"
            data-autofocus
            onClick={onCancel}
            className="rounded-sn-input px-4 py-2 font-data text-sm text-sn-ink-muted hover:text-sn-ink transition-opacity duration-sn-fast"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="rounded-sn-input px-4 py-2 font-data text-sm bg-sn-status-error text-white hover:opacity-90 transition-opacity duration-sn-fast"
          >
            {confirmLabel}
          </button>
        </>
      }
    >
      {body}
    </SmithDialog>
  );
}
