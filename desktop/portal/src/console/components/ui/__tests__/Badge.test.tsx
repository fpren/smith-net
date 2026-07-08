import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Badge } from '../Badge';

describe('Badge', () => {
  it('renders children', () => {
    render(<Badge>FOREMAN</Badge>);
    expect(screen.getByText('FOREMAN')).toBeInTheDocument();
  });

  it('applies tone-based styling — default tone', () => {
    const { container } = render(<Badge>x</Badge>);
    expect(container.firstChild).toHaveClass('bg-sn-bg-panel');
  });

  it('applies ok tone when tone=ok', () => {
    const { container } = render(<Badge tone="ok">x</Badge>);
    expect((container.firstChild as HTMLElement).className).toMatch(/text-sn-status-online/);
  });

  it('applies danger tone when tone=danger', () => {
    const { container } = render(<Badge tone="danger">x</Badge>);
    expect((container.firstChild as HTMLElement).className).toMatch(/text-sn-status-error/);
  });
});
