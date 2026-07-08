// v1 name kept as a thin alias over SmithDialog (Design System v2).
// Form modals are non-destructive: backdrop click still closes them.
import { ReactNode } from 'react';
import { SmithDialog } from './SmithDialog';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
}

export function Modal({ open, onClose, title, children }: ModalProps) {
  return (
    <SmithDialog open={open} onClose={onClose} title={title} size="lg">
      {children}
    </SmithDialog>
  );
}
