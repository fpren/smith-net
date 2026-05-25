import { render, screen, act } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { formatElapsed, ShiftClock } from '../ShiftClock';

const h = vi.hoisted(() => ({
  state: { onClock: false, startedAt: null as string | null, busy: false, toggle: vi.fn() },
}));
vi.mock('../useShiftToggle', () => ({ useShiftToggle: () => h.state }));

describe('formatElapsed', () => {
  it('formats elapsed seconds as zero-padded HH:MM:SS', () => {
    expect(formatElapsed(0)).toBe('00:00:00');
    expect(formatElapsed(5)).toBe('00:00:05');
    expect(formatElapsed(65)).toBe('00:01:05');
    expect(formatElapsed(3661)).toBe('01:01:01');
    expect(formatElapsed(-10)).toBe('00:00:00');
  });
});

describe('ShiftClock', () => {
  it('shows only the clock-in pill when off the clock', () => {
    h.state = { onClock: false, startedAt: null, busy: false, toggle: vi.fn() };
    render(<ShiftClock />);
    expect(screen.getByRole('button', { name: /clock in/i })).toBeInTheDocument();
    expect(screen.queryByLabelText('shift elapsed')).not.toBeInTheDocument();
  });

  it('shows a live count-up timer when on the clock', () => {
    const startedAt = new Date(Date.now() - 3661_000).toISOString(); // ~1h 1m 1s ago
    h.state = { onClock: true, startedAt, busy: false, toggle: vi.fn() };
    render(<ShiftClock />);
    expect(screen.getByLabelText('shift elapsed')).toHaveTextContent(/^01:01:0\d$/);
    expect(screen.getByRole('button', { name: /clock out/i })).toBeInTheDocument();
  });

  it('counts up one second at a time from clock-in, like the APK', () => {
    vi.useFakeTimers();
    try {
      const t0 = new Date('2026-01-01T10:00:00.000Z');
      vi.setSystemTime(t0);
      h.state = { onClock: true, startedAt: t0.toISOString(), busy: false, toggle: vi.fn() };
      render(<ShiftClock />);
      const shown = () => screen.getByLabelText('shift elapsed').textContent;

      // starts at zero the instant we are on the clock
      expect(shown()).toBe('00:00:00');
      // ticks every second
      act(() => vi.advanceTimersByTime(1000));
      expect(shown()).toBe('00:00:01');
      act(() => vi.advanceTimersByTime(4000));
      expect(shown()).toBe('00:00:05');
      // rolls minutes/hours correctly over a longer run
      act(() => vi.advanceTimersByTime(3_600_000));
      expect(shown()).toBe('01:00:05');
    } finally {
      vi.useRealTimers();
    }
  });
});
