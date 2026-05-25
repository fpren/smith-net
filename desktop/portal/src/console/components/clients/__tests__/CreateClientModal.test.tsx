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

  it('edits a client (calls update) and upserts the store', async () => {
    const existing = {
      id: 'client-1', ownerId: 'f-1', name: 'Old Name', email: null, phone: null,
      address: null, company: null, notes: null,
      createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
    };
    const onCreated = vi.fn();
    render(<CreateClientModal open onClose={() => {}} onCreated={onCreated} editing={existing as any} />);
    fireEvent.change(screen.getByLabelText(/name/i), { target: { value: 'New Name' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));
    await waitFor(() => expect(onCreated).toHaveBeenCalled());
    // PATCH handler echoes the name back
    expect(onCreated.mock.calls[0][0].name).toBe('New Name');
    expect(useClientsStore.getState().clients.some((c) => c.id === 'client-1')).toBe(true);
  });
});
