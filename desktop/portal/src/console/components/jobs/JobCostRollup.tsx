import { useMaterialsStore } from '../../stores/materialsStore';
import { useExpensesStore } from '../../stores/expensesStore';
import type { Material } from '../../api/materialsClient';
import type { Expense } from '../../api/expensesClient';

const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const EMPTY_MATERIALS: Material[] = [];
const EMPTY_EXPENSES: Expense[] = [];

export function JobCostRollup({ jobId }: { jobId: string }) {
  const materials = useMaterialsStore((s) => s.byJob[jobId] ?? EMPTY_MATERIALS);
  const expenses = useExpensesStore((s) => s.byJob[jobId] ?? EMPTY_EXPENSES);
  const mTotal = materials.reduce((s, m) => s + m.quantity * m.unitCost, 0);
  const eTotal = expenses.reduce((s, e) => s + e.amount, 0);
  const total = mTotal + eTotal;
  return (
    <section className="font-mono mb-4 border border-console-border bg-console-surface p-3">
      <div className="text-sm flex justify-between">
        <span className="text-console-text-muted">Materials:</span>
        <span className="text-console-text tabular-nums">{USD.format(mTotal)}</span>
      </div>
      <div className="text-sm flex justify-between">
        <span className="text-console-text-muted">Expenses:</span>
        <span className="text-console-text tabular-nums">{USD.format(eTotal)}</span>
      </div>
      <div className="mt-2 pt-2 border-t border-console-border text-sm flex justify-between font-bold">
        <span className="text-console-text">Job total:</span>
        <span className="text-console-accent tabular-nums">{USD.format(total)}</span>
      </div>
    </section>
  );
}
