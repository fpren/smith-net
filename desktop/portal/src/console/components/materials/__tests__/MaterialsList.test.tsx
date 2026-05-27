import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MaterialsList } from '../MaterialsList';
import { useMaterialsStore } from '../../../stores/materialsStore';
import type { Material } from '../../../api/materialsClient';

function mat(id: string, name: string, qty = 1, unitCost = 0, checked = false): Material {
  return {
    id, jobId: 'j1', name, notes: null,
    checked, checkedAt: checked ? '2026-05-26T11:00:00Z' : null,
    quantity: qty, unit: 'ea', unitCost, vendor: null,
    createdAt: '2026-05-26T10:00:00Z', updatedAt: '2026-05-26T10:00:00Z',
  };
}

describe('MaterialsList', () => {
  beforeEach(() => useMaterialsStore.getState().clear());

  it('renders empty state when no materials', () => {
    render(<MaterialsList jobId="j1" />);
    expect(screen.getByText(/no materials yet/i)).toBeInTheDocument();
  });

  it('renders materials with line costs and subtotal', () => {
    useMaterialsStore.getState().setForJob('j1', [
      mat('a', '10/2 Romex', 50, 0.85),
      mat('b', 'Box of staples', 1, 4.50),
    ]);
    render(<MaterialsList jobId="j1" />);
    expect(screen.getByText('10/2 Romex')).toBeInTheDocument();
    expect(screen.getByText('Box of staples')).toBeInTheDocument();
    // Subtotal: 50 * 0.85 + 1 * 4.50 = 47.00
    expect(screen.getByText(/Materials: \$47\.00/)).toBeInTheDocument();
  });

  it('toggling the checkbox calls update and reflects in store', async () => {
    useMaterialsStore.getState().setForJob('j1', [mat('a', 'X', 1, 0)]);
    render(<MaterialsList jobId="j1" />);
    const cb = screen.getByRole('checkbox');
    fireEvent.click(cb);
    await waitFor(() => {
      const m = useMaterialsStore.getState().byJob['j1']?.[0];
      expect(m?.checked).toBe(true);
    });
  });
});
