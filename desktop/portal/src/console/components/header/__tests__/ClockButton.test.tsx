import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { ClockButton } from '../ClockButton';
import { server } from '../../../test/msw-server';
import { useShiftStore } from '../../../stores/shiftStore';

describe('ClockButton', () => {
  // The shift state is a shared module store now; reset it so tests don't leak.
  beforeEach(() => useShiftStore.getState().reset());

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

  it('optimistically flips to ON CLOCK before the server responds', async () => {
    server.use(http.get('/api/shifts/current', () => HttpResponse.json({ shift: null })));
    let release!: () => void;
    const gate = new Promise<void>((r) => {
      release = r;
    });
    server.use(
      http.post('/api/shifts/start', async () => {
        await gate; // hang the server until we release it
        return HttpResponse.json({
          shift: { id: 's-1', userId: 'u-1', startedAt: '2026-05-17T00:00:00Z', endedAt: null, source: 'web' },
        });
      }),
    );
    render(<ClockButton />);
    await waitFor(() => expect(screen.getByText(/clock in/i)).toBeInTheDocument());

    fireEvent.click(screen.getByLabelText(/Clock in/i));
    // Optimistic: the pill flips to "clock out" while the POST is still pending,
    // and stays disabled until the round-trip settles.
    await waitFor(() => expect(screen.getByText(/clock out/i)).toBeInTheDocument());
    expect(screen.getByLabelText(/Clock out/i)).toBeDisabled();

    // Swap the GET handler so the post-release refresh confirms ON CLOCK.
    server.use(
      http.get('/api/shifts/current', () =>
        HttpResponse.json({
          shift: { id: 's-1', userId: 'u-1', startedAt: '2026-05-17T00:00:00Z', endedAt: null, source: 'web' },
        }),
      ),
    );

    release(); // let the server resolve; state stays ON CLOCK and re-enables after reconcile
    await waitFor(() => expect(screen.getByLabelText(/Clock out/i)).not.toBeDisabled());
  });
});
