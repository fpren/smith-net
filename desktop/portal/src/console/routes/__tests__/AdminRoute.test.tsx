import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AdminRoute } from '../AdminRoute';
import { useAdminHealthStore } from '../../stores/adminHealthStore';

describe('AdminRoute', () => {
  beforeEach(() => { useAdminHealthStore.getState().clear(); });

  it('renders workers + queue tables once polled data lands', async () => {
    render(<MemoryRouter><AdminRoute /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText(/12345@host/)).toBeInTheDocument());
    expect(screen.getByText(/geocode, audit_flush, email/)).toBeInTheDocument();

    // Queue rollup — both kind:state combos visible.
    // "audit_flush" appears in worker kinds AND queue kind — getAllByText
    // for that one. "succeeded" is queue-only.
    expect(screen.getAllByText(/audit_flush/).length).toBeGreaterThan(1);
    expect(screen.getByText('succeeded')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();

    // Oldest queued card
    expect(screen.getByText(/oldest queued/i)).toBeInTheDocument();
  });
});
