import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ShareLocationToggle } from '../ShareLocationToggle';
import { useShareLocationStore } from '../../../stores/shareLocationStore';

const startMock = vi.fn();
const stopMock = vi.fn();

vi.mock('../../../hooks/useShareLocation', () => ({
  useShareLocation: () => ({ start: startMock, stop: stopMock }),
}));

describe('ShareLocationToggle', () => {
  beforeEach(() => {
    useShareLocationStore.getState().reset();
    startMock.mockReset();
    stopMock.mockReset();
  });

  it('shows OFF by default and calls start when clicked', async () => {
    render(<ShareLocationToggle />);
    expect(screen.getByRole('button')).toHaveTextContent(/share location.*off/i);
    await userEvent.click(screen.getByRole('button'));
    expect(startMock).toHaveBeenCalled();
  });

  it('shows ON when sharing and calls stop when clicked', async () => {
    useShareLocationStore.getState().setSharing(true, 'shift-1');
    render(<ShareLocationToggle />);
    expect(screen.getByRole('button')).toHaveTextContent(/share location.*on/i);
    await userEvent.click(screen.getByRole('button'));
    expect(stopMock).toHaveBeenCalled();
  });

  it('displays error when present', () => {
    useShareLocationStore.getState().setError('Permission denied');
    render(<ShareLocationToggle />);
    expect(screen.getByText(/permission denied/i)).toBeInTheDocument();
  });

  it('button is disabled while transitioning', () => {
    useShareLocationStore.getState().setTransitioning(true);
    render(<ShareLocationToggle />);
    expect(screen.getByRole('button')).toBeDisabled();
  });
});
