import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { Modal } from '../Modal';

describe('Modal', () => {
  it('does not render when open is false', () => {
    render(<Modal open={false} onClose={() => {}} title="X">body</Modal>);
    expect(screen.queryByText('body')).not.toBeInTheDocument();
  });

  it('renders title + body when open', () => {
    render(<Modal open onClose={() => {}} title="My Modal">body</Modal>);
    expect(screen.getByText('My Modal')).toBeInTheDocument();
    expect(screen.getByText('body')).toBeInTheDocument();
  });

  it('calls onClose when backdrop clicked', async () => {
    const onClose = vi.fn();
    render(<Modal open onClose={onClose} title="X">b</Modal>);
    await userEvent.click(screen.getByTestId('sn-dialog-backdrop'));
    expect(onClose).toHaveBeenCalled();
  });

  it('does NOT call onClose when content clicked', async () => {
    const onClose = vi.fn();
    render(<Modal open onClose={onClose} title="X">b</Modal>);
    await userEvent.click(screen.getByText('b'));
    expect(onClose).not.toHaveBeenCalled();
  });

  it('regains the v1 600px width and stays height-contained', () => {
    render(<Modal open onClose={() => {}} title="X">b</Modal>);
    const panel = screen.getByRole('dialog');
    expect(panel.className).toContain('max-w-[600px]');
    expect(panel.className).toContain('max-h-[90vh]');
    expect(panel.className).toContain('overflow-y-auto');
  });
});
