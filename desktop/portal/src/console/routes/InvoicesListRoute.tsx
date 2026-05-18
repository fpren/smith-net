// desktop/portal/src/console/routes/InvoicesListRoute.tsx
import { useState } from 'react';
import { Button } from '../components/ui/Button';
import { InvoiceCard } from '../components/invoices/InvoiceCard';
import { CreateInvoiceModal } from '../components/invoices/CreateInvoiceModal';
import { useInvoicesPolling } from '../hooks/useInvoicesPolling';
import { useInvoicesStore } from '../stores/invoicesStore';
import type { InvoiceStatus, Invoice } from '../api/invoicesClient';

const STATUS_SECTIONS: { status: InvoiceStatus; label: string; defaultOpen: boolean }[] = [
  { status: 'draft',     label: 'DRAFT',     defaultOpen: true  },
  { status: 'issued',    label: 'ISSUED',    defaultOpen: true  },
  { status: 'sent',      label: 'SENT',      defaultOpen: true  },
  { status: 'viewed',    label: 'VIEWED',    defaultOpen: false },
  { status: 'paid',      label: 'PAID',      defaultOpen: false },
  { status: 'overdue',   label: 'OVERDUE',   defaultOpen: true  },
  { status: 'disputed',  label: 'DISPUTED',  defaultOpen: true  },
  { status: 'cancelled', label: 'CANCELLED', defaultOpen: false },
];

function Section({ label, invoices, defaultOpen }: { label: string; invoices: Invoice[]; defaultOpen: boolean }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border border-console-border mb-3">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center justify-between px-3 py-2 bg-console-surface text-console-text-muted text-xs uppercase tracking-wide font-mono"
      >
        <span>{label} ({invoices.length})</span>
        <span>{open ? '[-]' : '[+]'}</span>
      </button>
      {open && (
        <div>
          {invoices.length === 0 && <div className="px-3 py-2 text-console-text-muted text-sm">—</div>}
          {invoices.map((i) => <InvoiceCard key={i.id} invoice={i} />)}
        </div>
      )}
    </div>
  );
}

export function InvoicesListRoute() {
  useInvoicesPolling('list');
  const invoices = useInvoicesStore((s) => s.invoices);
  const isStale = useInvoicesStore((s) => s.isStale);
  const [showCreate, setShowCreate] = useState(false);

  const byStatus = (st: InvoiceStatus) => invoices.filter((i) => i.status === st);

  if (invoices.length === 0) {
    return (
      <div className="flex flex-col items-center mt-24 gap-4 font-mono">
        <div className="text-console-text-muted">No invoices yet.</div>
        <Button onClick={() => setShowCreate(true)}>Create your first invoice</Button>
        <CreateInvoiceModal open={showCreate} onClose={() => setShowCreate(false)} />
      </div>
    );
  }

  return (
    <div className="font-mono">
      <div className="flex flex-col items-start gap-2 md:flex-row md:items-center md:justify-between mb-4">
        <h1 className="text-console-text text-lg">Invoices</h1>
        <Button onClick={() => setShowCreate(true)}>+ New invoice</Button>
      </div>
      {isStale && (
        <div className="bg-console-surface border border-console-warn text-console-warn px-3 py-1 text-xs mb-3">
          [OFFLINE] Couldn't refresh — showing cached data
        </div>
      )}
      {STATUS_SECTIONS.map((s) => (
        <Section key={s.status} label={s.label} invoices={byStatus(s.status)} defaultOpen={s.defaultOpen} />
      ))}
      <CreateInvoiceModal open={showCreate} onClose={() => setShowCreate(false)} />
    </div>
  );
}
