// desktop/portal/src/console/components/invoices/AddLineItemInput.tsx
import { useState } from 'react';
import { invoicesClient, type LineCategory } from '../../api/invoicesClient';
import { useInvoicesStore } from '../../stores/invoicesStore';
import { useToastStore } from '../../stores/toastStore';

const CATEGORIES: LineCategory[] = ['labor', 'materials', 'travel', 'change_order', 'other'];

export function AddLineItemInput({ invoiceId }: { invoiceId: string }) {
  const [description, setDescription] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [rate, setRate] = useState('');
  const [category, setCategory] = useState<LineCategory>('labor');
  const [submitting, setSubmitting] = useState(false);

  const addLineItem = useInvoicesStore((s) => s.addLineItem);
  const upsertInvoice = useInvoicesStore((s) => s.upsertInvoice);
  const pushToast = useToastStore((s) => s.push);

  async function submit() {
    const desc = description.trim();
    const qty = parseFloat(quantity);
    const rt = parseFloat(rate);
    if (!desc || !Number.isFinite(qty) || qty <= 0 || !Number.isFinite(rt) || rt < 0) return;

    setSubmitting(true);
    const r = await invoicesClient.addLineItem(invoiceId, {
      description: desc,
      quantity: qty,
      rate: rt,
      category,
    });
    setSubmitting(false);
    if (r.ok) {
      addLineItem(r.lineItem);
      setDescription('');
      setQuantity('1');
      setRate('');
      // Refresh the parent invoice for updated totals.
      const fresh = await invoicesClient.getById(invoiceId);
      if (fresh.ok) upsertInvoice(fresh.invoice);
    } else {
      pushToast({ message: r.error || 'Failed to add line item', tone: 'error', duration: 3000 });
    }
  }

  return (
    // Mobile: stacked — description full width on top, then a row with
    // qty + rate, then a row with category + add. Desktop: single inline
    // row via md:contents (the inner flex wrappers promote children into
    // the parent grid).
    <div className="mt-2 font-mono flex flex-col gap-2 md:grid md:grid-cols-[1fr_5rem_5rem_8rem_auto]">
      <input
        type="text"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        placeholder="Description"
        className="w-full bg-transparent border border-sn-line px-2 py-1 text-sm text-sn-ink placeholder-sn-ink-muted focus:outline-none focus:border-sn-accent"
      />
      <div className="flex gap-2 md:contents">
        <input
          type="number"
          min="0.001"
          step="any"
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
          placeholder="qty"
          className="flex-1 md:flex-none bg-transparent border border-sn-line px-2 py-1 text-sm text-sn-ink placeholder-sn-ink-muted tabular-nums focus:outline-none focus:border-sn-accent"
        />
        <input
          type="number"
          min="0"
          step="any"
          value={rate}
          onChange={(e) => setRate(e.target.value)}
          placeholder="rate"
          className="flex-1 md:flex-none bg-transparent border border-sn-line px-2 py-1 text-sm text-sn-ink placeholder-sn-ink-muted tabular-nums focus:outline-none focus:border-sn-accent"
        />
      </div>
      <div className="flex gap-2 md:contents">
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value as LineCategory)}
          className="flex-1 md:flex-none bg-transparent border border-sn-line px-2 py-1 text-sm text-sn-ink focus:outline-none focus:border-sn-accent"
        >
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
        <button
          type="button"
          onClick={submit}
          disabled={submitting || !description.trim() || !rate}
          className="px-3 py-1 text-xs uppercase tracking-wide text-sn-accent border border-sn-accent disabled:opacity-40 disabled:cursor-not-allowed hover:bg-sn-accent hover:text-sn-ink-on-accent transition-colors whitespace-nowrap"
        >
          {submitting ? '[Adding…]' : '[+ Add line]'}
        </button>
      </div>
    </div>
  );
}
