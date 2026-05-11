import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { Button } from '../Button';

describe('Button', () => {
  it('renders children', () => {
    render(<Button>Click me</Button>);
    expect(screen.getByRole('button', { name: 'Click me' })).toBeInTheDocument();
  });

  it('fires onClick when clicked', async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Click</Button>);
    await userEvent.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('is disabled when disabled prop is true', () => {
    render(<Button disabled>X</Button>);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('applies primary variant styles by default', () => {
    render(<Button>X</Button>);
    expect(screen.getByRole('button').className).toMatch(/bg-console-accent/);
  });

  it('applies secondary variant when variant=secondary', () => {
    render(<Button variant="secondary">X</Button>);
    expect(screen.getByRole('button').className).toMatch(/bg-console-surface/);
  });
});
