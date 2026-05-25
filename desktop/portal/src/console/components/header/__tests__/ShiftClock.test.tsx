import { render, screen, act } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { formatElapsed, ShiftClock } from '../ShiftClock';

const h = vi.hoisted(() => ({
  toggle: { onClock: false, startedAt: null as string | null, busy: false, toggle: vi.fn() },
  dayTotal: 0,
}));
vi.mock('../useShiftToggle', () => ({ useShiftToggle: () => h.toggle }));
vi.mock('../useDayShiftTotal', () => ({ useDayShiftTotal: () => h.dayTotal }));

describe('formatElapsed', () => {
  it('formats elapsed seconds as zero-padded HH:MM:SS', () => {
    expect(formatElapsed(0)).toBe('00:00:00');
    expect(formatElapsed(3661)).toBe('01:01:01');
    expect(formatElapsed(-10)).toBe('00:00:00');
  });
});

describe('ShiftClock', () => {
  it('off the clock: clock-in pill + day total on the right, no current-shift timer', () => {
    h.toggle = { onClock: false, startedAt: null, busy: false, toggle: vi.fn() };
    h.dayTotal = 8 * 3600;
    render(<ShiftClock />);
    expect(screen.getByRole('button', { name: /clock in/i })).toBeInTheDocument();
    expect(screen.getByLabelText('day total')).toHaveTextContent('08:00:00');
    expect(screen.queryByLabelText('shift elapsed')).not.toBeInTheDocument();
  });

  it('on the clock: current-shift timer on the left, no day total', () => {
    const startedAt = new Date(Date.now() - 3661_000).toISOString();
    h.toggle = { onClock: true, startedAt, busy: false, toggle: vi.fn() };
    h.dayTotal = 8 * 3600;
    render(<ShiftClock />);
    expect(screen.getByLabelText('shift elapsed')).toHaveTextContent(/^01:01:0\d$/);
    expect(screen.getByRole('button', { name: /clock out/i })).toBeInTheDocument();
    expect(screen.queryByLabelText('day total')).not.toBeInTheDocument();
  });

  it('on the clock: counts up one second at a time, like the APK', () => {
    vi.useFakeTimers();
    try {
      const t0 = new Date('2026-01-01T10:00:00.000Z');
      vi.setSystemTime(t0);
      h.toggle = { onClock: true, startedAt: t0.toISOString(), busy: false, toggle: vi.fn() };
      h.dayTotal = 0;
      render(<ShiftClock />);
      const shown = () => screen.getByLabelText('shift elapsed').textContent;
      expect(shown()).toBe('00:00:00');
      act(() => vi.advanceTimersByTime(1000));
      expect(shown()).toBe('00:00:01');
      act(() => vi.advanceTimersByTime(4000));
      expect(shown()).toBe('00:00:05');
    } finally {
      vi.useRealTimers();
    }
  });
});
