import { ReactNode } from 'react';

interface Props {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
}

export function Modal({ open, onClose, title, children }: Props) {
  if (!open) return null;

  return (
    <div
      data-testid="modal-backdrop"
      onClick={onClose}
      className="fixed inset-0 bg-black/40 flex items-center justify-center z-50"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="bg-console-surface border border-console-border p-6 min-w-[320px] max-w-[600px] font-mono"
      >
        <div className="text-xs uppercase tracking-wide text-console-text-muted mb-3">{title}</div>
        {children}
      </div>
    </div>
  );
}
