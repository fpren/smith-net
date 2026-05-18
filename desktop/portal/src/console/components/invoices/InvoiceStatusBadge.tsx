// desktop/portal/src/console/components/invoices/InvoiceStatusBadge.tsx
import type { InvoiceStatus } from '../../api/invoicesClient';

const TONE: Record<InvoiceStatus, string> = {
  draft:     'text-console-text-muted border-console-border',
  issued:    'text-console-accent border-console-accent',
  sent:      'text-console-accent border-console-accent',
  viewed:    'text-console-accent border-console-accent',
  paid:      'text-console-ok border-console-ok',
  overdue:   'text-console-warn border-console-warn',
  disputed:  'text-console-danger border-console-danger',
  cancelled: 'text-console-text-muted border-console-border line-through',
};

export function InvoiceStatusBadge({ status }: { status: InvoiceStatus }) {
  return (
    <span
      className={
        'inline-block text-xs font-mono uppercase tracking-wide px-2 py-0.5 border ' +
        TONE[status]
      }
    >
      {status}
    </span>
  );
}
