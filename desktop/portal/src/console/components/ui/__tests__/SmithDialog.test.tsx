import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { useState } from 'react';
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

  it('focus returns to the previously-focused element when the dialog closes', () => {
    function Harness() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button onClick={() => setOpen(true)}>trigger</button>
          <SmithDialog open={open} onClose={() => setOpen(false)} title="t">
            <button>inside</button>
          </SmithDialog>
        </>
      );
    }
    render(<Harness />);
    const trigger = screen.getByRole('button', { name: 'trigger' });
    trigger.focus();
    expect(trigger).toHaveFocus();

    fireEvent.click(trigger);
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    // Close by rerendering closed (simulates parent setting open=false).
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(trigger).toHaveFocus();
  });

  it('does NOT reset focus when the parent re-renders with a new onClose identity while open', () => {
    function Harness({ tick }: { tick: number }) {
      return (
        <SmithDialog open onClose={() => {}} title="t">
          <button>first</button>
          <button>second</button>
          <span data-testid="tick">{tick}</span>
        </SmithDialog>
      );
    }
    const { rerender } = render(<Harness tick={0} />);
    const second = screen.getByRole('button', { name: 'second' });
    second.focus();
    expect(second).toHaveFocus();

    // New onClose closure identity + new tick, simulating a polling re-render.
    rerender(<Harness tick={1} />);
    expect(screen.getByTestId('tick')).toHaveTextContent('1');
    expect(second).toHaveFocus();
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
