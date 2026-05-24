import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { http, HttpResponse } from 'msw';
import { ClockButton } from '../ClockButton';
import { server } from '../../../test/msw-server';

describe('ClockButton', () => {
  it('renders OFF CLOCK + [Clock in] when /api/shifts/current returns null', async () => {
    server.use(http.get('/api/shifts/current', () => HttpResponse.json({ shift: null })));
    render(<ClockButton />);
    await waitFor(() => expect(screen.getByText(/clock in/i)).toBeInTheDocument());
    expect(screen.getByLabelText(/Clock in/i)).toBeInTheDocument();
  });

  it('renders ON CLOCK + [Clock out] when shift is open', async () => {
    server.use(
      http.get('/api/shifts/current', () =>
        HttpResponse.json({
          shift: { id: 's-1', userId: 'u-1', startedAt: '2026-05-17T00:00:00Z', endedAt: null, source: 'web' },
        }),
      ),
    );
    render(<ClockButton />);
    await waitFor(() => expect(screen.getByText(/clock out/i)).toBeInTheDocument());
  });

  it('clicking [Clock in] hits /api/shifts/start and flips to ON CLOCK', async () => {
    // Start the test with the shift closed; the start handler swaps the
    // GET handler to return an open shift on the post-action refresh.
    server.use(http.get('/api/shifts/current', () => HttpResponse.json({ shift: null })));
    render(<ClockButton />);
    await waitFor(() => expect(screen.getByText(/clock in/i)).toBeInTheDocument());

    server.use(
      http.post('/api/shifts/start', () => {
        // After this resolves the hook re-fetches; swap GET to "open" state.
        server.use(
          http.get('/api/shifts/current', () =>
            HttpResponse.json({
              shift: { id: 's-1', userId: 'u-1', startedAt: '2026-05-17T00:00:00Z', endedAt: null, source: 'web' },
            }),
          ),
        );
        return HttpResponse.json(
          { shift: { id: 's-1', userId: 'u-1', startedAt: '2026-05-17T00:00:00Z', endedAt: null, source: 'web' } },
        );
      }),
    );

    fireEvent.click(screen.getByLabelText(/Clock in/i));
    await waitFor(() => expect(screen.getByText(/clock out/i)).toBeInTheDocument());
  });

  it('surfaces a toast on a failed start', async () => {
    server.use(http.get('/api/shifts/current', () => HttpResponse.json({ shift: null })));
    server.use(
      http.post('/api/shifts/start', () =>
        HttpResponse.json({ error: 'already on clock' }, { status: 409 }),
      ),
    );
    render(<ClockButton />);
    await waitFor(() => expect(screen.getByText(/clock in/i)).toBeInTheDocument());
    fireEvent.click(screen.getByLabelText(/Clock in/i));
    // Stays as Clock in because the start failed.
    await waitFor(() => expect(screen.getByText(/clock in/i)).toBeInTheDocument());
  });
});
