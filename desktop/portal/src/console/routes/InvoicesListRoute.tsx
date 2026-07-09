// desktop/portal/src/console/routes/InvoicesListRoute.tsx
import { useState } from 'react';
import { Outlet, useMatch } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { InvoiceCard } from '../components/invoices/InvoiceCard';
import { CreateInvoiceModal } from '../components/invoices/CreateInvoiceModal';
import { useInvoicesPolling } from '../hooks/useInvoicesPolling';
import { useInvoicesStore } from '../stores/invoicesStore';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/StateViews';
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
    <div className="border border-sn-line mb-3">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center justify-between px-3 py-2 bg-sn-bg-panel text-sn-ink-muted text-xs uppercase tracking-wide font-mono"
      >
        <span>{label} ({invoices.length})</span>
        <span>{open ? '[-]' : '[+]'}</span>
      </button>
      {open && (
        <div>
          {invoices.length === 0 && <div className="px-3 py-2 text-sn-ink-muted text-sm">—</div>}
          {invoices.map((i) => <InvoiceCard key={i.id} invoice={i} />)}
        </div>
      )}
    </div>
  );
}

export function InvoicesListRoute() {
  const { reload } = useInvoicesPolling('list');
  const invoices = useInvoicesStore((s) => s.invoices);
  const isLoadingList = useInvoicesStore((s) => s.isLoadingList);
  const listStale = useInvoicesStore((s) => s.listStale);
  const [showCreate, setShowCreate] = useState(false);
  // Independent path match (not useParams — the :id param belongs to the
  // nested child route, which isn't in this component's own route context).
  const idActive = Boolean(useMatch('/console/invoices/:id'));

  const byStatus = (st: InvoiceStatus) => invoices.filter((i) => i.status === st);

  // Precedence: loading -> error (no cached data to fall back on) -> empty -> data.
  let listContent: JSX.Element;
  if (isLoadingList && invoices.length === 0) {
    listContent = <LoadingState label="Loading invoices" />;
  } else if (listStale && invoices.length === 0) {
    listContent = (
      <div className="flex flex-col items-center mt-24 gap-4">
        <ErrorState message="Couldn't load invoices." onRetry={reload} />
      </div>
    );
  } else if (invoices.length === 0) {
    listContent = (
      <div className="flex flex-col items-center mt-24 gap-4 font-mono">
        <EmptyState
          title="No invoices yet"
          action={<Button onClick={() => setShowCreate(true)}>Create your first invoice</Button>}
        />
        <CreateInvoiceModal open={showCreate} onClose={() => setShowCreate(false)} />
      </div>
    );
  } else {
    listContent = (
      <div className="font-mono">
        <div className="flex flex-col items-start gap-2 md:flex-row md:items-center md:justify-between mb-4">
          <h1 className="text-sn-ink text-lg">Invoices</h1>
          <Button onClick={() => setShowCreate(true)}>+ New invoice</Button>
        </div>
        {listStale && (
          <ErrorState message="Couldn't refresh — showing cached data." onRetry={reload} />
        )}
        {STATUS_SECTIONS.map((s) => (
          <Section key={s.status} label={s.label} invoices={byStatus(s.status)} defaultOpen={s.defaultOpen} />
        ))}
        <CreateInvoiceModal open={showCreate} onClose={() => setShowCreate(false)} />
      </div>
    );
  }

  return (
    <div className="xl:grid xl:grid-cols-[minmax(0,1fr)_420px] xl:gap-6 xl:h-full">
      <div className={
        // The list column is an independent scroll region in BOTH states --
        // flipping scroll modes on selection would reset the list's scroll
        // position the moment you open the item you scrolled to find.
        idActive ? 'hidden xl:block xl:overflow-y-auto xl:min-h-0' : 'xl:overflow-y-auto xl:min-h-0'
      }>
        {listContent}
      </div>
      <div
        className={
          idActive
            ? 'block xl:overflow-y-auto xl:min-h-0 xl:border-l xl:border-sn-line xl:pl-6'
            : 'hidden xl:block xl:overflow-y-auto xl:min-h-0 xl:border-l xl:border-sn-line xl:pl-6'
        }
      >
        {idActive ? <Outlet /> : <EmptyState title="Select an invoice" />}
      </div>
    </div>
  );
}
