import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CreateClientModal } from '../CreateClientModal';
import { useClientsStore } from '../../../stores/clientsStore';

describe('CreateClientModal', () => {
  beforeEach(() => useClientsStore.getState().clear());

  it('creates a client and calls onCreated with it', async () => {
    const onCreated = vi.fn();
    render(<CreateClientModal open onClose={() => {}} onCreated={onCreated} />);
    fireEvent.change(screen.getByLabelText(/name/i), { target: { value: 'Brand new' } });
    fireEvent.click(screen.getByRole('button', { name: /create/i }));
    await waitFor(() => expect(onCreated).toHaveBeenCalled());
    expect(onCreated.mock.calls[0][0].id).toBe('new-client-id');
    expect(useClientsStore.getState().clients[0].id).toBe('new-client-id');
  });
});
