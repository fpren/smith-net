// desktop/portal/src/console/components/invoices/InvoiceStatusBadge.tsx
import type { InvoiceStatus } from '../../api/invoicesClient';

const TONE: Record<InvoiceStatus, string> = {
  draft:     'text-sn-ink-muted border-sn-line',
  issued:    'text-sn-accent border-sn-accent',
  sent:      'text-sn-accent border-sn-accent',
  viewed:    'text-sn-accent border-sn-accent',
  paid:      'text-sn-status-online border-sn-status-online',
  overdue:   'text-sn-attention border-sn-attention',
  disputed:  'text-sn-status-error border-sn-status-error',
  cancelled: 'text-sn-ink-muted border-sn-line line-through',
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
