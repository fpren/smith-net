// desktop/portal/src/console/components/expenses/ExpensesTable.tsx
import { useEffect, useState } from 'react';
import { expensesClient } from '../../api/expensesClient';
import type { Expense } from '../../api/expensesClient';
import { useExpensesStore } from '../../stores/expensesStore';
import { useToast } from '../../hooks/useToast';
import { Button } from '../ui/Button';
import { AddExpenseModal } from './AddExpenseModal';

const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const EMPTY: Expense[] = [];  // stable reference -- avoids getSnapshot loop

export function ExpensesTable({ jobId }: { jobId: string }) {
  const items = useExpensesStore((s) => s.byJob[jobId] ?? EMPTY);
  const toast = useToast();
  const [showAdd, setShowAdd] = useState(false);
  const [editing, setEditing] = useState<Expense | null>(null);

  useEffect(() => {
    expensesClient.listForJob(jobId).then((r) => {
      if (r.ok) useExpensesStore.getState().setForJob(jobId, r.expenses);
    });
  }, [jobId]);

  async function del(e: Expense) {
    if (!window.confirm(`Delete "${e.description}"?`)) return;
    const r = await expensesClient.delete(e.id);
    if (r.ok) { useExpensesStore.getState().remove(jobId, e.id); toast.info('Expense deleted'); }
    else toast.error(r.error || 'Failed to delete');
  }

  const subtotal = items.reduce((s, e) => s + e.amount, 0);

  return (
    <section className="font-mono mb-4">
      <header className="flex items-center justify-between mb-2">
        <h2 className="text-console-text text-sm uppercase tracking-wider">Expenses ({items.length})</h2>
        <Button variant="secondary" onClick={() => { setEditing(null); setShowAdd(true); }}>+ Add expense</Button>
      </header>
      {items.length === 0 ? (
        <div className="text-console-text-muted text-sm py-2">No expenses yet.</div>
      ) : (
        <div className="border border-console-border overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-console-text-muted border-b border-console-border">
                <th className="px-3 py-2 font-normal">Category</th>
                <th className="px-3 py-2 font-normal">Description</th>
                <th className="px-3 py-2 font-normal text-right">Amount</th>
                <th className="px-3 py-2 font-normal">Vendor</th>
                <th className="px-3 py-2 font-normal">Date</th>
                <th className="px-3 py-2 font-normal text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {items.map((e) => (
                <tr key={e.id} className="border-b border-console-border last:border-b-0">
                  <td className="px-3 py-2 text-console-text">{e.category}</td>
                  <td className="px-3 py-2 text-console-text">{e.description}</td>
                  <td className="px-3 py-2 text-console-text tabular-nums text-right">{USD.format(e.amount)}</td>
                  <td className="px-3 py-2 text-console-text-muted">{e.vendor ?? '-'}</td>
                  <td className="px-3 py-2 text-console-text-muted">{e.expenseDate ?? '-'}</td>
                  <td className="px-3 py-2 text-right whitespace-nowrap">
                    <button onClick={() => { setEditing(e); setShowAdd(true); }} className="text-xs text-console-text-muted hover:text-console-text mr-2">[edit]</button>
                    <button onClick={() => del(e)} className="text-xs text-console-text-muted hover:text-console-warn">[delete]</button>
                  </td>
                </tr>
              ))}
              <tr className="border-t-2 border-console-border">
                <td colSpan={6} className="px-3 py-2 text-right text-console-text font-bold tabular-nums">Expenses: {USD.format(subtotal)}</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
      <AddExpenseModal
        open={showAdd}
        onClose={() => setShowAdd(false)}
        jobId={jobId}
        editing={editing}
      />
    </section>
  );
}
