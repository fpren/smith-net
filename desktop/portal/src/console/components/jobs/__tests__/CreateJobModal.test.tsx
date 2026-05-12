import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { CreateJobModal } from '../CreateJobModal';

describe('CreateJobModal', () => {
  it('renders title, location, description fields when open', () => {
    render(<MemoryRouter><CreateJobModal open onClose={vi.fn()} onCreated={vi.fn()} /></MemoryRouter>);
    expect(screen.getByLabelText(/title/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/location/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create/i })).toBeInTheDocument();
  });

  it('does not render when open=false', () => {
    render(<MemoryRouter><CreateJobModal open={false} onClose={vi.fn()} onCreated={vi.fn()} /></MemoryRouter>);
    expect(screen.queryByLabelText(/title/i)).not.toBeInTheDocument();
  });

  it('shows inline error when title is empty and submit clicked', async () => {
    render(<MemoryRouter><CreateJobModal open onClose={vi.fn()} onCreated={vi.fn()} /></MemoryRouter>);
    await userEvent.click(screen.getByRole('button', { name: /create/i }));
    expect(await screen.findByText(/title is required/i)).toBeInTheDocument();
  });

  it('submits and calls onCreated then onClose on success', async () => {
    const onClose = vi.fn();
    const onCreated = vi.fn();
    render(<MemoryRouter><CreateJobModal open onClose={onClose} onCreated={onCreated} /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText(/title/i), 'Brand new');
    await userEvent.type(screen.getByLabelText(/location/i), 'X');
    await userEvent.click(screen.getByRole('button', { name: /create/i }));
    await waitFor(() => {
      expect(onCreated).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
    const createdJob = onCreated.mock.calls[0][0];
    expect(createdJob.title).toBe('Brand new');
  });
});
