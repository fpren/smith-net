import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { Input } from '../Input';

describe('Input', () => {
  it('renders with label', () => {
    render(<Input label="Email" />);
    expect(screen.getByText('Email')).toBeInTheDocument();
    expect(screen.getByRole('textbox')).toBeInTheDocument();
  });

  it('forwards value + onChange', async () => {
    const onChange = vi.fn();
    render(<Input label="Email" value="" onChange={onChange} />);
    await userEvent.type(screen.getByRole('textbox'), 'a');
    expect(onChange).toHaveBeenCalled();
  });

  it('shows error message when error prop is set', () => {
    render(<Input label="Email" error="bad email" />);
    expect(screen.getByText('bad email')).toBeInTheDocument();
  });

  it('uses type=password when type is password', () => {
    render(<Input label="Pwd" type="password" />);
    expect(screen.getByLabelText('Pwd')).toHaveAttribute('type', 'password');
  });
});
