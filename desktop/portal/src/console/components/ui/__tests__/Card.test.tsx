import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Card } from '../Card';

describe('Card', () => {
  it('renders children', () => {
    render(<Card><span>content</span></Card>);
    expect(screen.getByText('content')).toBeInTheDocument();
  });

  it('renders title when provided', () => {
    render(<Card title="My Card">x</Card>);
    expect(screen.getByText('My Card')).toBeInTheDocument();
  });

  it('applies surface + border classes', () => {
    const { container } = render(<Card>x</Card>);
    expect(container.firstChild).toHaveClass('bg-console-surface');
    expect(container.firstChild).toHaveClass('border');
  });
});
