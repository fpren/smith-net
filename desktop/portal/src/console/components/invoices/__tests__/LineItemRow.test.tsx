import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { LineItemRow } from '../LineItemRow';
import { useInvoicesStore } from '../../../stores/invoicesStore';
import { invoicesClient, type InvoiceLineItem } from '../../../api/invoicesClient';

function lineItem(id: string, description: string): InvoiceLineItem {
  return {
    id,
    invoiceId: 'inv-1',
    description,
    quantity: 1,
    unit: 'ea',
    rate: 10,
    total: 10,
    category: 'labor',
    sortOrder: 0,
    createdAt: '2026-05-11T11:00:00Z',
  };
}

describe('LineItemRow delete confirmation', () => {
  beforeEach(() => useInvoicesStore.getState().clear());

  it('line item delete asks for confirmation first', async () => {
    const item = lineItem('li1', 'Drywall patch');
    const deleteSpy = vi.spyOn(invoicesClient, 'deleteLineItem');

    render(<LineItemRow item={item} />);

    fireEvent.click(screen.getByRole('button', { name: 'Delete line item' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(deleteSpy).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));
    expect(deleteSpy).toHaveBeenCalledTimes(1);
    expect(deleteSpy).toHaveBeenCalledWith('li1');
  });

  it('cancel does not delete the line item', () => {
    const item = lineItem('li1', 'Drywall patch');
    const deleteSpy = vi.spyOn(invoicesClient, 'deleteLineItem');

    render(<LineItemRow item={item} />);

    fireEvent.click(screen.getByRole('button', { name: 'Delete line item' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(deleteSpy).not.toHaveBeenCalled();
    expect(screen.getByText('Drywall patch')).toBeInTheDocument();
  });
});
