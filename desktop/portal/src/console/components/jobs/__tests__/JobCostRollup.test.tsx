import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { JobCostRollup } from '../JobCostRollup';
import { useMaterialsStore } from '../../../stores/materialsStore';
import { useExpensesStore } from '../../../stores/expensesStore';

describe('JobCostRollup', () => {
  beforeEach(() => {
    useMaterialsStore.getState().clear();
    useExpensesStore.getState().clear();
  });

  it('sums materials and expenses into a job total', () => {
    useMaterialsStore.getState().setForJob('j1', [
      { id: 'a', jobId: 'j1', name: 'X', notes: null, checked: false, checkedAt: null,
        quantity: 2, unit: 'ea', unitCost: 10, vendor: null,
        createdAt: '', updatedAt: '' },
    ]);
    useExpensesStore.getState().setForJob('j1', [
      { id: 'e1', jobId: 'j1', category: 'fuel', description: 'gas', amount: 30,
        vendor: null, notes: null, expenseDate: null, createdAt: '', updatedAt: '' },
    ]);
    render(<JobCostRollup jobId="j1" />);
    expect(screen.getByText('Materials:')).toBeInTheDocument();
    expect(screen.getByText('$20.00')).toBeInTheDocument();
    expect(screen.getByText('Expenses:')).toBeInTheDocument();
    expect(screen.getByText('$30.00')).toBeInTheDocument();
    expect(screen.getByText('Job total:')).toBeInTheDocument();
    expect(screen.getByText('$50.00')).toBeInTheDocument();
  });

  it('renders zeros when stores are empty', () => {
    render(<JobCostRollup jobId="j1" />);
    expect(screen.getByText('Job total:')).toBeInTheDocument();
    expect(screen.getAllByText('$0.00')).toHaveLength(3);
  });
});
