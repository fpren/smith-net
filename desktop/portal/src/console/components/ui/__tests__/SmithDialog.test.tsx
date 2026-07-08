import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { SmithDialog, ConfirmDialog } from '../SmithDialog';

describe('SmithDialog', () => {
  it('renders title and children when open', () => {
    render(<SmithDialog open onClose={() => {}} title="Add client">
      <div>form body</div>
    </SmithDialog>);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Add client')).toBeInTheDocument();
    expect(screen.getByText('form body')).toBeInTheDocument();
  });

  it('renders nothing when closed', () => {
    render(<SmithDialog open={false} onClose={() => {}} title="t">x</SmithDialog>);
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('backdrop click closes a non-destructive dialog', () => {
    const onClose = vi.fn();
    render(<SmithDialog open onClose={onClose} title="t">x</SmithDialog>);
    fireEvent.click(screen.getByTestId('sn-dialog-backdrop'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('backdrop click does NOT close a destructive dialog', () => {
    const onClose = vi.fn();
    render(<SmithDialog open onClose={onClose} title="t" destructive>x</SmithDialog>);
    fireEvent.click(screen.getByTestId('sn-dialog-backdrop'));
    expect(onClose).not.toHaveBeenCalled();
  });

  it('Escape closes (explicit cancel), even destructive', () => {
    const onClose = vi.fn();
    render(<SmithDialog open onClose={onClose} title="t" destructive>x</SmithDialog>);
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

describe('ConfirmDialog', () => {
  it('confirm fires onConfirm; cancel fires onCancel; backdrop is inert', () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(<ConfirmDialog open title="Delete job?" body="This cannot be undone."
      confirmLabel="Delete" onConfirm={onConfirm} onCancel={onCancel} />);
    fireEvent.click(screen.getByTestId('sn-dialog-backdrop'));
    expect(onCancel).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('cancel button has initial focus (safe default)', () => {
    render(<ConfirmDialog open title="t" body="b" confirmLabel="Delete"
      onConfirm={() => {}} onCancel={() => {}} />);
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();
  });
});
