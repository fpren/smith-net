import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { LoadingState, EmptyState, ErrorState } from '../StateViews';

describe('StateViews', () => {
  describe('LoadingState', () => {
    it('renders with default label', () => {
      render(<LoadingState />);
      expect(screen.getByText('Loading')).toBeInTheDocument();
    });

    it('renders with custom label', () => {
      render(<LoadingState label="Fetching data" />);
      expect(screen.getByText('Fetching data')).toBeInTheDocument();
    });

    it('has role="status" for accessibility', () => {
      const { container } = render(<LoadingState />);
      expect(container.querySelector('[role="status"]')).toBeInTheDocument();
    });

    it('has aria-live="polite"', () => {
      const { container } = render(<LoadingState />);
      expect(container.querySelector('[aria-live="polite"]')).toBeInTheDocument();
    });

    it('does not contain console- classes', () => {
      const { container } = render(<LoadingState />);
      expect(container.innerHTML).not.toMatch(/console-/);
    });
  });

  describe('EmptyState', () => {
    it('renders title', () => {
      render(<EmptyState title="No items" />);
      expect(screen.getByText('No items')).toBeInTheDocument();
    });

    it('renders optional hint', () => {
      render(<EmptyState title="No items" hint="Try searching" />);
      expect(screen.getByText('Try searching')).toBeInTheDocument();
    });

    it('does not render hint when not provided', () => {
      render(<EmptyState title="No items" />);
      expect(screen.queryByText(/hint/i)).not.toBeInTheDocument();
    });

    it('renders optional action node', () => {
      const actionButton = <button>Create</button>;
      render(<EmptyState title="No items" action={actionButton} />);
      expect(screen.getByRole('button', { name: 'Create' })).toBeInTheDocument();
    });

    it('does not contain console- classes', () => {
      const { container } = render(<EmptyState title="No items" hint="hint" action={<div>action</div>} />);
      expect(container.innerHTML).not.toMatch(/console-/);
    });
  });

  describe('ErrorState', () => {
    it('renders with default message', () => {
      render(<ErrorState />);
      expect(screen.getByText("[x] Couldn't load this.")).toBeInTheDocument();
    });

    it('renders with custom message', () => {
      render(<ErrorState message="Failed to connect" />);
      expect(screen.getByText('[x] Failed to connect')).toBeInTheDocument();
    });

    it('has role="alert" for accessibility', () => {
      const { container } = render(<ErrorState />);
      expect(container.querySelector('[role="alert"]')).toBeInTheDocument();
    });

    it('renders RETRY button when onRetry is provided', () => {
      render(<ErrorState onRetry={() => {}} />);
      expect(screen.getByRole('button', { name: 'RETRY' })).toBeInTheDocument();
    });

    it('does not render RETRY button when onRetry is not provided', () => {
      render(<ErrorState />);
      expect(screen.queryByRole('button', { name: 'RETRY' })).not.toBeInTheDocument();
    });

    it('calls onRetry when RETRY button is clicked', async () => {
      const onRetry = vi.fn();
      render(<ErrorState onRetry={onRetry} />);
      await userEvent.click(screen.getByRole('button', { name: 'RETRY' }));
      expect(onRetry).toHaveBeenCalledOnce();
    });

    it('does not contain console- classes', () => {
      const { container } = render(<ErrorState message="Error" onRetry={() => {}} />);
      expect(container.innerHTML).not.toMatch(/console-/);
    });
  });
});
