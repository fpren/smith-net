import { useState, useEffect, FormEvent } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { expensesClient } from '../../api/expensesClient';
import type { Expense } from '../../api/expensesClient';
import { useExpensesStore } from '../../stores/expensesStore';
import { useToast } from '../../hooks/useToast';

const CATEGORY_SUGGESTIONS = [
  'material', 'permit_fee', 'fuel', 'subcontractor', 'equipment_rental', 'other',
];

interface Props {
  open: boolean;
  jobId: string;
  editing: Expense | null;
  onClose: () => void;
}

export function AddExpenseModal({ open, jobId, editing, onClose }: Props) {
  const toast = useToast();
  const [category, setCategory] = useState('');
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('0');
  const [vendor, setVendor] = useState('');
  const [expenseDate, setExpenseDate] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (editing) {
      setCategory(editing.category);
      setDescription(editing.description);
      setAmount(String(editing.amount));
      setVendor(editing.vendor ?? '');
      setExpenseDate(editing.expenseDate ?? '');
      setNotes(editing.notes ?? '');
    } else {
      setCategory(''); setDescription(''); setAmount('0');
      setVendor(''); setExpenseDate(''); setNotes('');
    }
  }, [editing, open]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!category.trim() || !description.trim() || busy) return;
    setBusy(true);
    const payload = {
      category: category.trim(),
      description: description.trim(),
      amount: Number(amount) || 0,
      vendor: vendor.trim() || undefined,
      expenseDate: expenseDate.trim() || undefined,
      notes: notes.trim() || undefined,
    };
    const r = editing
      ? await expensesClient.update(editing.id, payload)
      : await expensesClient.create({ jobId, ...payload });
    setBusy(false);
    if (r.ok && 'queued' in r) {
      toast.info('Saved offline — will sync when back online');
      onClose();
    } else if (r.ok) {
      useExpensesStore.getState().upsert(jobId, r.expense);
      toast.info(editing ? 'Expense updated' : 'Expense added');
      onClose();
    } else {
      toast.error(r.error || 'Failed to save expense');
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={editing ? 'Edit expense' : 'Add expense'}>
      <form onSubmit={handleSubmit} className="w-full sm:w-[420px] max-w-full flex flex-col gap-2 font-mono text-sm">
        <input value={category} onChange={(e) => setCategory(e.target.value)}
          list="expense-category-suggestions" placeholder="Category" autoFocus required
          className="bg-console-bg border border-console-border rounded px-2 py-1 text-console-text focus:border-console-accent outline-none" />
        <datalist id="expense-category-suggestions">
          {CATEGORY_SUGGESTIONS.map((c) => <option key={c} value={c} />)}
        </datalist>
        <input value={description} onChange={(e) => setDescription(e.target.value)}
          placeholder="Description" required
          className="bg-console-bg border border-console-border rounded px-2 py-1 text-console-text focus:border-console-accent outline-none" />
        <div className="flex gap-2">
          <input value={amount} onChange={(e) => setAmount(e.target.value)}
            type="number" min="0" step="0.01" placeholder="amount"
            className="flex-1 bg-console-bg border border-console-border rounded px-2 py-1 text-console-text focus:border-console-accent outline-none" />
          <input value={expenseDate} onChange={(e) => setExpenseDate(e.target.value)}
            type="date" placeholder="date"
            className="flex-1 bg-console-bg border border-console-border rounded px-2 py-1 text-console-text focus:border-console-accent outline-none" />
        </div>
        <input value={vendor} onChange={(e) => setVendor(e.target.value)}
          placeholder="Vendor (optional)"
          className="bg-console-bg border border-console-border rounded px-2 py-1 text-console-text focus:border-console-accent outline-none" />
        <textarea value={notes} onChange={(e) => setNotes(e.target.value)}
          placeholder="Notes (optional)" rows={2}
          className="bg-console-bg border border-console-border rounded px-2 py-1 text-console-text focus:border-console-accent outline-none" />
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={busy}>Save</Button>
        </div>
      </form>
    </Modal>
  );
}
