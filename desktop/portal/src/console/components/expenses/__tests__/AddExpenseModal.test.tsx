import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { AddExpenseModal } from '../AddExpenseModal';
import { useExpensesStore } from '../../../stores/expensesStore';

describe('AddExpenseModal', () => {
  beforeEach(() => useExpensesStore.getState().clear());

  it('submits create with defaults', async () => {
    render(<AddExpenseModal open jobId="j1" editing={null} onClose={() => {}} />);
    fireEvent.change(screen.getByPlaceholderText(/category/i), { target: { value: 'fuel' } });
    fireEvent.change(screen.getByPlaceholderText(/description/i), { target: { value: 'gas station' } });
    fireEvent.change(screen.getByPlaceholderText(/amount/i), { target: { value: '40' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));
    await waitFor(() => {
      const e = useExpensesStore.getState().byJob['j1']?.[0];
      expect(e?.category).toBe('fuel');
      expect(e?.description).toBe('gas station');
      expect(Number(e?.amount)).toBe(40);
    });
  });

  it('prefills when editing', () => {
    const editing = {
      id: 'e1', jobId: 'j1', category: 'permit_fee',
      description: 'Electrical permit', amount: 175.50,
      vendor: 'City', notes: 'n', expenseDate: '2026-05-20',
      createdAt: '', updatedAt: '',
    };
    render(<AddExpenseModal open jobId="j1" editing={editing} onClose={() => {}} />);
    expect(screen.getByDisplayValue('permit_fee')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Electrical permit')).toBeInTheDocument();
    expect(screen.getByDisplayValue('175.5')).toBeInTheDocument();
    expect(screen.getByDisplayValue('City')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2026-05-20')).toBeInTheDocument();
  });

  it('renders category suggestions in the datalist', () => {
    const { container } = render(<AddExpenseModal open jobId="j1" editing={null} onClose={() => {}} />);
    const options = container.querySelectorAll('datalist option');
    const values = Array.from(options).map((o) => o.getAttribute('value'));
    expect(values).toContain('material');
    expect(values).toContain('permit_fee');
    expect(values).toContain('fuel');
    expect(values).toContain('subcontractor');
    expect(values).toContain('equipment_rental');
    expect(values).toContain('other');
  });
});
