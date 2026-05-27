import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { AddMaterialModal } from '../AddMaterialModal';
import { useMaterialsStore } from '../../../stores/materialsStore';

describe('AddMaterialModal', () => {
  beforeEach(() => useMaterialsStore.getState().clear());

  it('submits create with defaults', async () => {
    render(<AddMaterialModal open jobId="j1" editing={null} onClose={() => {}} />);
    fireEvent.change(screen.getByPlaceholderText(/material name/i), { target: { value: '12-gauge wire' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));
    await waitFor(() => {
      const m = useMaterialsStore.getState().byJob['j1']?.[0];
      expect(m?.name).toBe('12-gauge wire');
    });
  });

  it('prefills when editing', () => {
    const editing = {
      id: 'm1', jobId: 'j1', name: 'Pre', notes: 'n',
      checked: false, checkedAt: null,
      quantity: 5, unit: 'ft', unitCost: 1.25, vendor: 'V',
      createdAt: '', updatedAt: '',
    };
    render(<AddMaterialModal open jobId="j1" editing={editing} onClose={() => {}} />);
    expect(screen.getByDisplayValue('Pre')).toBeInTheDocument();
    expect(screen.getByDisplayValue('5')).toBeInTheDocument();
    expect(screen.getByDisplayValue('ft')).toBeInTheDocument();
    expect(screen.getByDisplayValue('1.25')).toBeInTheDocument();
  });
});
