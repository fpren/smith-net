import { describe, it, expect } from 'vitest';
import { groupMessages } from '../messageGrouping';
import type { Message } from '../../../../types';

const msg = (id: string, sender: string, ts: number): Message => ({
  id, channelId: 'c', senderId: sender, senderName: sender, content: id, timestamp: ts, origin: 'online',
});

describe('groupMessages', () => {
  it('groups same sender within 7 minutes', () => {
    const out = groupMessages([msg('a', 'u1', 0), msg('b', 'u1', 6 * 60_000)]);
    expect(out.map((r) => r.firstOfGroup)).toEqual([true, false]);
  });

  it('breaks on sender change, on >7min gap, and on day change', () => {
    const dayMs = 24 * 60 * 60 * 1000;
    const out = groupMessages([
      msg('a', 'u1', 0), msg('b', 'u2', 1000),
      msg('c', 'u2', 1000 + 7 * 60_000 + 1), msg('d', 'u2', dayMs + 1000),
    ]);
    expect(out.map((r) => r.firstOfGroup)).toEqual([true, true, true, true]);
  });

  it('returns an empty array for an empty input', () => {
    expect(groupMessages([])).toEqual([]);
  });

  it('keeps grouping at exactly the 7-minute boundary (420_000 ms)', () => {
    const out = groupMessages([msg('a', 'u1', 0), msg('b', 'u1', 420_000)]);
    expect(out.map((r) => r.firstOfGroup)).toEqual([true, false]);
  });

  it('breaks just past the 7-minute boundary', () => {
    const out = groupMessages([msg('a', 'u1', 0), msg('b', 'u1', 420_001)]);
    expect(out.map((r) => r.firstOfGroup)).toEqual([true, true]);
  });

  it('preserves message and order in the returned rows', () => {
    const a = msg('a', 'u1', 0);
    const b = msg('b', 'u1', 1000);
    const out = groupMessages([a, b]);
    expect(out.map((r) => r.message)).toEqual([a, b]);
  });
});
