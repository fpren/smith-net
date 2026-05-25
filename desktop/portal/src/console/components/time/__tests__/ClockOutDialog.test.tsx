import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ClockOutDialog } from '../ClockOutDialog';

describe('ClockOutDialog', () => {
  it('reveals a custom field only when OTHER is selected and passes the custom reason', () => {
    const onConfirm = vi.fn();
    render(<ClockOutDialog open onClose={() => {}} onConfirm={onConfirm} />);
    expect(screen.queryByPlaceholderText(/specify/i)).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('Other'));
    const custom = screen.getByPlaceholderText(/specify/i);
    fireEvent.change(custom, { target: { value: 'left site' } });
    fireEvent.click(screen.getByRole('button', { name: /clock out/i }));
    expect(onConfirm).toHaveBeenCalledWith('left site');
  });

  it('passes a preset reason label when a non-OTHER reason is chosen', () => {
    const onConfirm = vi.fn();
    render(<ClockOutDialog open onClose={() => {}} onConfirm={onConfirm} />);
    fireEvent.click(screen.getByText('Lunch Break'));
    fireEvent.click(screen.getByRole('button', { name: /clock out/i }));
    expect(onConfirm).toHaveBeenCalledWith('lunch');
  });
});
