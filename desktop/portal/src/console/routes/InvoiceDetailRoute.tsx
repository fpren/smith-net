// desktop/portal/src/console/routes/InvoiceDetailRoute.tsx
import { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useInvoicesPolling } from '../hooks/useInvoicesPolling';
import { useInvoicesStore } from '../stores/invoicesStore';
import { invoicesClient, type InvoiceStatus } from '../api/invoicesClient';
import { useToastStore } from '../stores/toastStore';
import { InvoiceStatusBadge } from '../components/invoices/InvoiceStatusBadge';
import { LineItemRow } from '../components/invoices/LineItemRow';
import { AddLineItemInput } from '../components/invoices/AddLineItemInput';
import { LoadingState, ErrorState } from '../components/ui/StateViews';

const fmtMoney = (n: number) =>
  n.toLocaleString('en-US', { style: 'currency', currency: 'USD' });

const ACTION_BUTTONS: { from: InvoiceStatus; to: InvoiceStatus; label: string }[] = [
  { from: 'draft',  to: 'issued', label: 'Issue'    },
  { from: 'issued', to: 'sent',   label: 'Mark sent'},
  { from: 'sent',   to: 'paid',   label: 'Mark paid'},
  { from: 'issued', to: 'paid',   label: 'Mark paid'},
  { from: 'viewed', to: 'paid',   label: 'Mark paid'},
];

function StatusActions({ status, onChange }: { status: InvoiceStatus; onChange: (s: InvoiceStatus) => void }) {
  const possible = ACTION_BUTTONS.filter((a) => a.from === status);
  const showCancel = status !== 'paid' && status !== 'cancelled';
  return (
    <div className="flex flex-wrap gap-2">
      {possible.map((a) => (
        <button
          key={a.to}
          type="button"
          onClick={() => onChange(a.to)}
          className="px-2 py-1 text-xs uppercase tracking-wide text-sn-accent border border-sn-accent hover:bg-sn-accent hover:text-sn-ink-on-accent transition-colors font-mono"
        >
          [{a.label}]
        </button>
      ))}
      {showCancel && (
        <button
          type="button"
          onClick={() => onChange('cancelled')}
          className="px-2 py-1 text-xs uppercase tracking-wide text-sn-ink-muted border border-sn-line hover:text-sn-status-error hover:border-sn-status-error transition-colors font-mono"
        >
          [Cancel]
        </button>
      )}
    </div>
  );
}

export function InvoiceDetailRoute() {
  const { id } = useParams<{ id: string }>();
  const { reload } = useInvoicesPolling({ detail: id ?? '' });
  const invoice = useInvoicesStore((s) => s.detailInvoice);
  const lineItems = useInvoicesStore((s) => s.detailLineItems);
  const upsertInvoice = useInvoicesStore((s) => s.upsertInvoice);
  const detailStale = useInvoicesStore((s) => s.detailStale);
  const pushToast = useToastStore((s) => s.push);

  useEffect(() => {
    return () => {
      // Reset detail when leaving the route to avoid stale paint on return.
      useInvoicesStore.getState().setDetail(null as any, []);
    };
  }, []);

  // Precedence: loading -> error (detailStale, with retry) -> not-loaded -> data.
  if (!invoice || invoice.id !== id) {
    if (detailStale) {
      return <ErrorState message="Couldn't load this invoice." onRetry={reload} />;
    }
    return <LoadingState label="Loading invoice" />;
  }

  const editable = invoice.status !== 'paid' && invoice.status !== 'cancelled';

  async function flipStatus(next: InvoiceStatus) {
    const r = await invoicesClient.setStatus(invoice!.id, next);
    if (r.ok) {
      upsertInvoice(r.invoice);
    } else {
      pushToast({ message: r.error || 'Status change failed', tone: 'error', duration: 3000 });
    }
  }

  return (
    <div className="font-mono">
      <Link to="/console/invoices" className="text-sn-accent text-sm">back to invoices</Link>

      <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between mt-2">
        <div className="flex items-center gap-3">
          <h1 className="text-sn-ink text-lg">{invoice.invoiceNumber}</h1>
          <InvoiceStatusBadge status={invoice.status} />
        </div>
        <StatusActions status={invoice.status} onChange={flipStatus} />
      </div>

      <dl className="text-sm grid grid-cols-[12ch_1fr] gap-y-1 mt-4">
        <dt className="text-sn-ink-muted">client</dt><dd>{invoice.clientName ?? '—'}</dd>
        <dt className="text-sn-ink-muted">email</dt> <dd>{invoice.clientEmail ?? '—'}</dd>
        <dt className="text-sn-ink-muted">issued</dt><dd>{new Date(invoice.issueDate).toLocaleDateString()}</dd>
        <dt className="text-sn-ink-muted">due</dt>   <dd>{invoice.dueDate ? new Date(invoice.dueDate).toLocaleDateString() : '—'}</dd>
      </dl>

      <div className="mt-8">
        <h2 className="text-sn-ink-muted text-xs uppercase tracking-wide mb-2">Line items ({lineItems.length})</h2>
        <div className="border border-sn-line">
          {lineItems.length === 0 ? (
            <div className="px-3 py-2 text-sn-ink-muted text-sm">No line items yet.</div>
          ) : (
            lineItems.map((li) => <LineItemRow key={li.id} item={li} readOnly={!editable} />)
          )}
        </div>
        {editable && <AddLineItemInput invoiceId={invoice.id} />}
      </div>

      <div className="mt-6 w-full md:max-w-md md:ml-auto text-sm">
        <div className="flex justify-between border-b border-sn-line py-1">
          <span className="text-sn-ink-muted">Subtotal</span>
          <span className="tabular-nums">{fmtMoney(invoice.subtotal)}</span>
        </div>
        <div className="flex justify-between border-b border-sn-line py-1">
          <span className="text-sn-ink-muted">Tax ({(invoice.taxRate * 100).toFixed(2)}%)</span>
          <span className="tabular-nums">{fmtMoney(invoice.taxAmount)}</span>
        </div>
        <div className="flex justify-between py-1 text-sn-ink font-semibold">
          <span>Total due</span>
          <span className="tabular-nums">{fmtMoney(invoice.totalDue)}</span>
        </div>
      </div>
    </div>
  );
}
