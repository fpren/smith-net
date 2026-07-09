// desktop/portal/src/console/components/invoices/InvoiceCard.tsx
import { Link } from 'react-router-dom';
import type { Invoice } from '../../api/invoicesClient';
import { InvoiceStatusBadge } from './InvoiceStatusBadge';

const fmtMoney = (n: number) =>
  n.toLocaleString('en-US', { style: 'currency', currency: 'USD' });

export function InvoiceCard({ invoice }: { invoice: Invoice }) {
  return (
    <Link
      to={`/console/invoices/${invoice.id}`}
      className="block border-b border-sn-line last:border-b-0 px-3 py-2 hover:bg-sn-bg-panel font-mono"
    >
      <div className="flex flex-col gap-1 md:flex-row md:items-center md:justify-between text-sm">
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-sn-ink whitespace-nowrap">{invoice.invoiceNumber}</span>
          <span className="text-sn-ink-muted truncate">{invoice.clientName ?? '(no client)'}</span>
        </div>
        <div className="flex items-center gap-2 md:gap-3">
          <span className="text-sn-ink tabular-nums">{fmtMoney(invoice.totalDue)}</span>
          <InvoiceStatusBadge status={invoice.status} />
        </div>
      </div>
    </Link>
  );
}
