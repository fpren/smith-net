import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { MapFilterChips } from '../MapFilterChips';

describe('MapFilterChips', () => {
  it('renders both options', () => {
    render(<MapFilterChips mode="active" onChange={vi.fn()} />);
    expect(screen.getByRole('button', { name: /active only/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /all/i })).toBeInTheDocument();
  });

  it('clicking "all" calls onChange("all")', async () => {
    const onChange = vi.fn();
    render(<MapFilterChips mode="active" onChange={onChange} />);
    await userEvent.click(screen.getByRole('button', { name: /^all$/i }));
    expect(onChange).toHaveBeenCalledWith('all');
  });

  it('clicking "active only" calls onChange("active")', async () => {
    const onChange = vi.fn();
    render(<MapFilterChips mode="all" onChange={onChange} />);
    await userEvent.click(screen.getByRole('button', { name: /active only/i }));
    expect(onChange).toHaveBeenCalledWith('active');
  });
});
