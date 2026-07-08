import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MessageRow } from '../MessageRow';
import { useToastStore } from '../../../stores/toastStore';
import type { Message } from '../../../../types';

function msg(overrides: Partial<Message> = {}): Message {
  return {
    id: 'm1',
    channelId: 'c1',
    senderId: 'u1',
    senderName: 'Ada Lovelace',
    content: 'hello there',
    timestamp: Date.now(),
    origin: 'online',
    ...overrides,
  };
}

const noop = () => {};

describe('MessageRow', () => {
  it('renders avatar and sender name when firstOfGroup', () => {
    render(
      <ul>
        <MessageRow message={msg()} firstOfGroup mine={false} seenByOthers={0} onDelete={noop} onRetry={noop} />
      </ul>
    );
    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument();
    expect(screen.getByLabelText('Ada Lovelace')).toBeInTheDocument(); // Avatar aria-label
  });

  it('omits avatar and sender name when not firstOfGroup', () => {
    render(
      <ul>
        <MessageRow message={msg()} firstOfGroup={false} mine={false} seenByOthers={0} onDelete={noop} onRetry={noop} />
      </ul>
    );
    expect(screen.queryByText('Ada Lovelace')).toBeNull();
  });

  it('every row is left-aligned (no items-end in the container class)', () => {
    const { container } = render(
      <ul>
        <MessageRow message={msg()} firstOfGroup mine onDelete={noop} onRetry={noop} seenByOthers={0} />
      </ul>
    );
    const row = container.querySelector('li');
    expect(row).not.toBeNull();
    expect(row!.className).not.toMatch(/items-end/);
    expect(row!.className).not.toMatch(/flex-row-reverse/);
  });

  it('FAILED shows "FAILED · RETRY" and clicking calls onRetry', () => {
    const onRetry = vi.fn();
    render(
      <ul>
        <MessageRow
          message={msg({ status: 'failed' })}
          firstOfGroup
          mine
          seenByOthers={0}
          onDelete={noop}
          onRetry={onRetry}
        />
      </ul>
    );
    const retryBtn = screen.getByText('FAILED · RETRY');
    fireEvent.click(retryBtn);
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('renders SEEN when seenByOthers > 0 and message is mine', () => {
    render(
      <ul>
        <MessageRow message={msg({ status: 'sent' })} firstOfGroup mine seenByOthers={1} onDelete={noop} onRetry={noop} />
      </ul>
    );
    expect(screen.getByText('SEEN')).toBeInTheDocument();
  });

  it('renders SENT when settled, not seen, and mine', () => {
    render(
      <ul>
        <MessageRow message={msg({ status: 'sent' })} firstOfGroup mine seenByOthers={0} onDelete={noop} onRetry={noop} />
      </ul>
    );
    expect(screen.getByText('SENT')).toBeInTheDocument();
  });

  it('renders PENDING when pending and mine', () => {
    render(
      <ul>
        <MessageRow message={msg({ status: 'pending' })} firstOfGroup mine seenByOthers={0} onDelete={noop} onRetry={noop} />
      </ul>
    );
    expect(screen.getByText('PENDING')).toBeInTheDocument();
  });

  it('never renders status microcopy for messages that are not mine', () => {
    render(
      <ul>
        <MessageRow message={msg({ status: 'failed' })} firstOfGroup mine={false} seenByOthers={0} onDelete={noop} onRetry={noop} />
      </ul>
    );
    expect(screen.queryByText('FAILED · RETRY')).toBeNull();
    expect(screen.queryByText('SENT')).toBeNull();
  });

  it('shows mesh chip when origin is mesh', () => {
    render(
      <ul>
        <MessageRow message={msg({ origin: 'mesh' })} firstOfGroup mine={false} seenByOthers={0} onDelete={noop} onRetry={noop} />
      </ul>
    );
    expect(screen.getByText('mesh')).toBeInTheDocument();
  });

  it('shows mesh chip when origin is gateway', () => {
    render(
      <ul>
        <MessageRow message={msg({ origin: 'gateway' })} firstOfGroup mine={false} seenByOthers={0} onDelete={noop} onRetry={noop} />
      </ul>
    );
    expect(screen.getByText('mesh')).toBeInTheDocument();
  });

  it('omits mesh chip for pure online origin', () => {
    render(
      <ul>
        <MessageRow message={msg({ origin: 'online' })} firstOfGroup mine={false} seenByOthers={0} onDelete={noop} onRetry={noop} />
      </ul>
    );
    expect(screen.queryByText('mesh')).toBeNull();
  });

  it('omits mesh chip when origin is online+mesh (online message also relayed outward via gateway)', () => {
    render(
      <ul>
        <MessageRow message={msg({ origin: 'online+mesh' })} firstOfGroup mine={false} seenByOthers={0} onDelete={noop} onRetry={noop} />
      </ul>
    );
    expect(screen.queryByText('mesh')).toBeNull();
  });

  it('renders image media as an img with the media url', () => {
    render(
      <ul>
        <MessageRow
          message={msg({ media: { type: 'image', url: 'https://example.com/photo.png' } })}
          firstOfGroup
          mine={false}
          seenByOthers={0}
          onDelete={noop}
          onRetry={noop}
        />
      </ul>
    );
    const img = screen.getByRole('img') as HTMLImageElement;
    expect(img.src).toBe('https://example.com/photo.png');
  });

  it('renders voice media as a glyph link with duration', () => {
    render(
      <ul>
        <MessageRow
          message={msg({ media: { type: 'voice', url: 'https://example.com/v.mp3', duration: 12 } })}
          firstOfGroup
          mine={false}
          seenByOthers={0}
          onDelete={noop}
          onRetry={noop}
        />
      </ul>
    );
    const link = screen.getByText(/\[▶\] 12s/);
    expect(link.closest('a')).toHaveAttribute('href', 'https://example.com/v.mp3');
    expect(link.closest('a')).toHaveAttribute('rel', 'noreferrer noopener');
  });

  it('renders file media as a glyph link with filename', () => {
    render(
      <ul>
        <MessageRow
          message={msg({ media: { type: 'file', url: 'https://example.com/f.pdf', filename: 'contract.pdf' } })}
          firstOfGroup
          mine={false}
          seenByOthers={0}
          onDelete={noop}
          onRetry={noop}
        />
      </ul>
    );
    expect(screen.getByText(/\[≡\] contract\.pdf/)).toBeInTheDocument();
  });

  it('copy button writes message content to the clipboard and shows success toast', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    const originalClipboard = navigator.clipboard;
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    });

    const pushToast = vi.spyOn(useToastStore.getState(), 'push');

    render(
      <ul>
        <MessageRow message={msg({ content: 'copy me' })} firstOfGroup mine={false} seenByOthers={0} onDelete={noop} onRetry={noop} />
      </ul>
    );
    fireEvent.click(screen.getByText('copy'));

    await waitFor(() => {
      expect(writeText).toHaveBeenCalledWith('copy me');
      expect(pushToast).toHaveBeenCalledWith({
        message: 'Copied',
        tone: 'info',
        duration: 2000,
      });
    });

    pushToast.mockRestore();
    Object.defineProperty(navigator, 'clipboard', {
      value: originalClipboard,
      configurable: true,
    });
  });

  it('copy button falls back to textarea when navigator.clipboard is undefined', async () => {
    const originalClipboard = navigator.clipboard;
    Object.defineProperty(navigator, 'clipboard', {
      value: undefined,
      configurable: true,
    });

    // Mock document.execCommand
    const execCommand = vi.fn();
    Object.defineProperty(document, 'execCommand', {
      value: execCommand,
      configurable: true,
    });

    const pushToast = vi.spyOn(useToastStore.getState(), 'push');

    render(
      <ul>
        <MessageRow message={msg({ content: 'fallback copy' })} firstOfGroup mine={false} seenByOthers={0} onDelete={noop} onRetry={noop} />
      </ul>
    );
    fireEvent.click(screen.getByText('copy'));

    await waitFor(() => {
      expect(execCommand).toHaveBeenCalledWith('copy');
      expect(pushToast).toHaveBeenCalledWith({
        message: 'Copied',
        tone: 'info',
        duration: 2000,
      });
    });

    pushToast.mockRestore();
    Object.defineProperty(document, 'execCommand', {
      value: undefined,
      configurable: true,
    });
    Object.defineProperty(navigator, 'clipboard', {
      value: originalClipboard,
      configurable: true,
    });
  });

  it('delete button only renders when mine, and calls onDelete with the message id', () => {
    const onDelete = vi.fn();
    const { rerender } = render(
      <ul>
        <MessageRow message={msg()} firstOfGroup mine={false} seenByOthers={0} onDelete={onDelete} onRetry={noop} />
      </ul>
    );
    expect(screen.queryByLabelText('Delete message')).toBeNull();

    rerender(
      <ul>
        <MessageRow message={msg()} firstOfGroup mine seenByOthers={0} onDelete={onDelete} onRetry={noop} />
      </ul>
    );
    fireEvent.click(screen.getByLabelText('Delete message'));
    expect(onDelete).toHaveBeenCalledWith('m1');
  });

  it('toolbar retry button only renders when status is failed', () => {
    const onRetry = vi.fn();
    const { rerender } = render(
      <ul>
        <MessageRow message={msg({ status: 'sent' })} firstOfGroup mine seenByOthers={0} onDelete={noop} onRetry={onRetry} />
      </ul>
    );
    expect(screen.queryByRole('button', { name: 'retry' })).toBeNull();

    rerender(
      <ul>
        <MessageRow message={msg({ status: 'failed' })} firstOfGroup mine seenByOthers={0} onDelete={noop} onRetry={onRetry} />
      </ul>
    );
    fireEvent.click(screen.getByRole('button', { name: 'retry' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
