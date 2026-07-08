import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ExpensesTable } from '../ExpensesTable';
import { useExpensesStore } from '../../../stores/expensesStore';
import { expensesClient } from '../../../api/expensesClient';
import type { Expense } from '../../../api/expensesClient';

function exp(id: string, category: string, description: string, amount: number, expenseDate: string | null = null): Expense {
  return {
    id, jobId: 'j1', category, description, amount,
    vendor: null, notes: null, expenseDate,
    createdAt: '2026-05-26T10:00:00Z', updatedAt: '2026-05-26T10:00:00Z',
  };
}

describe('ExpensesTable', () => {
  beforeEach(() => useExpensesStore.getState().clear());

  it('renders empty state when no expenses', () => {
    render(<ExpensesTable jobId="j1" />);
    expect(screen.getByText(/no expenses yet/i)).toBeInTheDocument();
  });

  it('renders rows with category/description/amount and a subtotal', () => {
    useExpensesStore.getState().setForJob('j1', [
      exp('a', 'permit_fee', 'Electrical permit', 175.50, '2026-05-20'),
      exp('b', 'fuel', 'Gas', 40.00),
    ]);
    render(<ExpensesTable jobId="j1" />);
    expect(screen.getByText('Electrical permit')).toBeInTheDocument();
    expect(screen.getByText('Gas')).toBeInTheDocument();
    expect(screen.getByText('permit_fee')).toBeInTheDocument();
    expect(screen.getByText('fuel')).toBeInTheDocument();
    // Subtotal: 175.50 + 40.00 = 215.50
    expect(screen.getByText(/Expenses: \$215\.50/)).toBeInTheDocument();
  });
});

describe('ExpensesTable delete confirmation', () => {
  beforeEach(() => useExpensesStore.getState().clear());

  it('expense delete asks for confirmation first', async () => {
    useExpensesStore.getState().setForJob('j1', [exp('a', 'permit_fee', 'Electrical permit', 175.5)]);
    const deleteSpy = vi.spyOn(expensesClient, 'delete');

    render(<ExpensesTable jobId="j1" />);

    fireEvent.click(screen.getByRole('button', { name: 'Delete expense' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(deleteSpy).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(deleteSpy).toHaveBeenCalledTimes(1));
    expect(deleteSpy).toHaveBeenCalledWith('a');
  });

  it('cancel does not delete the expense', () => {
    useExpensesStore.getState().setForJob('j1', [exp('a', 'permit_fee', 'Electrical permit', 175.5)]);
    const deleteSpy = vi.spyOn(expensesClient, 'delete');

    render(<ExpensesTable jobId="j1" />);

    fireEvent.click(screen.getByRole('button', { name: 'Delete expense' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(deleteSpy).not.toHaveBeenCalled();
    expect(screen.getByText('Electrical permit')).toBeInTheDocument();
  });
});
