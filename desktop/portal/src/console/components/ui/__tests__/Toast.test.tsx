import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ToastContainer } from '../Toast';
import { useToastStore } from '../../../stores/toastStore';

describe('Toast', () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] });
  });

  it('renders nothing when no toasts', () => {
    render(<ToastContainer />);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('renders an info toast pushed via store', () => {
    useToastStore.getState().push({ message: 'hello world', tone: 'info', duration: 4000 });
    render(<ToastContainer />);
    expect(screen.getByText('hello world')).toBeInTheDocument();
  });

  it('renders multiple toasts stacked', () => {
    useToastStore.getState().push({ message: 'first', tone: 'info', duration: 4000 });
    useToastStore.getState().push({ message: 'second', tone: 'error', duration: 4000 });
    render(<ToastContainer />);
    expect(screen.getByText('first')).toBeInTheDocument();
    expect(screen.getByText('second')).toBeInTheDocument();
  });

  it('dismisses a toast when its [x] is clicked', async () => {
    useToastStore.getState().push({ message: 'click me away', tone: 'info', duration: 60000 });
    render(<ToastContainer />);
    expect(screen.getByText('click me away')).toBeInTheDocument();
    const dismissBtn = screen.getByRole('button', { name: /dismiss/i });
    await userEvent.click(dismissBtn);
    expect(screen.queryByText('click me away')).not.toBeInTheDocument();
  });

  it('auto-dismisses after duration', () => {
    vi.useFakeTimers();
    useToastStore.getState().push({ message: 'auto-bye', tone: 'info', duration: 1000 });
    render(<ToastContainer />);
    expect(screen.getByText('auto-bye')).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(1100);
    });
    expect(screen.queryByText('auto-bye')).not.toBeInTheDocument();
    vi.useRealTimers();
  });

  it('caps the stack at 5 — pushing a 6th drops the oldest', () => {
    for (let i = 1; i <= 6; i++) {
      useToastStore.getState().push({ message: `toast-${i}`, tone: 'info', duration: 60000 });
    }
    render(<ToastContainer />);
    expect(useToastStore.getState().toasts.length).toBe(5);
    // newest at top of stack; toast-1 was the oldest and should have been dropped
    expect(screen.queryByText('toast-1')).not.toBeInTheDocument();
    expect(screen.getByText('toast-6')).toBeInTheDocument();
  });
});
