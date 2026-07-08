// desktop/portal/src/console/components/invoices/LineItemRow.tsx
import { useState } from 'react';
import { invoicesClient } from '../../api/invoicesClient';
import { useInvoicesStore } from '../../stores/invoicesStore';
import { useToastStore } from '../../stores/toastStore';
import { ConfirmDialog } from '../ui/SmithDialog';
import type { InvoiceLineItem } from '../../api/invoicesClient';

const fmtMoney = (n: number) =>
  n.toLocaleString('en-US', { style: 'currency', currency: 'USD' });

export function LineItemRow({ item, readOnly }: { item: InvoiceLineItem; readOnly?: boolean }) {
  const removeLineItem = useInvoicesStore((s) => s.removeLineItem);
  const upsertInvoice = useInvoicesStore((s) => s.upsertInvoice);
  const detailInvoice = useInvoicesStore((s) => s.detailInvoice);
  const pushToast = useToastStore((s) => s.push);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  async function doDelete() {
    const r = await invoicesClient.deleteLineItem(item.id);
    if (r.ok) {
      removeLineItem(item.id);
      // Re-fetch the parent invoice so subtotal/total stay in sync.
      if (detailInvoice) {
        const fresh = await invoicesClient.getById(detailInvoice.id);
        if (fresh.ok) upsertInvoice(fresh.invoice);
      }
    } else {
      pushToast({ message: r.error || 'Failed to delete', tone: 'error', duration: 3000 });
    }
  }

  return (
    <div className="px-3 py-2 border-b border-console-border text-sm font-mono md:grid md:grid-cols-[1fr_5rem_5rem_6rem_5rem_2rem] md:gap-2 md:items-center">
      {/* Row 1 (mobile + desktop col 1): description on the left, mobile-only
          delete button on the right. */}
      <div className="flex items-start justify-between gap-2 md:contents">
        <div className="text-console-text break-words flex-1 min-w-0">{item.description}</div>
        {!readOnly && (
          <button
            type="button"
            onClick={() => setConfirmingDelete(true)}
            aria-label="Delete line item"
            className="md:order-last text-xs text-console-text-muted opacity-40 hover:opacity-100 focus:opacity-100 hover:text-console-danger focus:text-console-danger transition-opacity flex-shrink-0"
          >
            [x]
          </button>
        )}
      </div>
      {/* Row 2 (mobile only — md:contents promotes each child into grid cols). */}
      <div className="md:contents flex items-baseline gap-3 mt-1 text-xs text-console-text-muted">
        <span className="tabular-nums md:text-sm">{item.quantity} {item.unit}</span>
        <span className="tabular-nums md:text-sm">@ {fmtMoney(item.rate)}</span>
        <span className="uppercase md:text-sm">{item.category}</span>
        <span className="ml-auto md:ml-0 md:text-right text-console-text tabular-nums font-semibold md:font-normal md:text-sm">
          {fmtMoney(item.total)}
        </span>
      </div>
      <ConfirmDialog
        open={confirmingDelete}
        title="Remove line item?"
        body="The invoice total recalculates immediately."
        confirmLabel="Remove"
        onConfirm={() => { setConfirmingDelete(false); void doDelete(); }}
        onCancel={() => setConfirmingDelete(false)}
      />
    </div>
  );
}
