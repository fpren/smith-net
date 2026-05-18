// desktop/portal/src/console/components/invoices/CreateInvoiceModal.tsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { invoicesClient } from '../../api/invoicesClient';
import { useInvoicesStore } from '../../stores/invoicesStore';
import { useToastStore } from '../../stores/toastStore';

interface Props {
  open: boolean;
  onClose: () => void;
}

export function CreateInvoiceModal({ open, onClose }: Props) {
  const [clientName, setClientName] = useState('');
  const [clientEmail, setClientEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();
  const upsert = useInvoicesStore((s) => s.upsertInvoice);
  const pushToast = useToastStore((s) => s.push);

  if (!open) return null;

  async function submit() {
    setSubmitting(true);
    const r = await invoicesClient.create({
      clientName: clientName.trim() || undefined,
      clientEmail: clientEmail.trim() || undefined,
    });
    setSubmitting(false);
    if (r.ok) {
      upsert(r.invoice);
      onClose();
      navigate(`/console/invoices/${r.invoice.id}`);
    } else {
      pushToast({ message: r.error || 'Failed to create invoice', tone: 'error', duration: 3000 });
    }
  }

  return (
    <div className="fixed inset-0 z-20 flex items-center justify-center bg-black/40 p-4" role="dialog">
      <div className="w-full max-w-md bg-console-bg border border-console-border p-4 font-mono">
        <h2 className="text-console-text text-lg mb-3">New invoice</h2>
        <label className="block text-xs text-console-text-muted mb-1">Client name</label>
        <input
          type="text"
          value={clientName}
          onChange={(e) => setClientName(e.target.value)}
          className="w-full bg-transparent border border-console-border px-2 py-1 text-sm text-console-text mb-3 focus:outline-none focus:border-console-accent"
          placeholder="Acme Roofing"
        />
        <label className="block text-xs text-console-text-muted mb-1">Client email (optional)</label>
        <input
          type="email"
          value={clientEmail}
          onChange={(e) => setClientEmail(e.target.value)}
          className="w-full bg-transparent border border-console-border px-2 py-1 text-sm text-console-text mb-4 focus:outline-none focus:border-console-accent"
          placeholder="ops@acme.com"
        />
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="px-3 py-1 text-xs uppercase tracking-wide text-console-text-muted border border-console-border disabled:opacity-50"
          >
            [Cancel]
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={submitting}
            className="px-3 py-1 text-xs uppercase tracking-wide text-console-accent border border-console-accent hover:bg-console-accent hover:text-console-bg disabled:opacity-50"
          >
            {submitting ? '[Creating…]' : '[Create]'}
          </button>
        </div>
      </div>
    </div>
  );
}
