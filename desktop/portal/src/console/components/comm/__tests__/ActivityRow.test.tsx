import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ActivityRow } from '../ActivityRow';
import type { Channel } from '../../../../types';

function channel(id: string, name = id): Channel {
  return {
    id,
    name,
    type: 'group',
    creatorId: 'u1',
    createdAt: 1700000000000,
    memberIds: ['u1'],
    isArchived: false,
    isDeleted: false,
  };
}

describe('ActivityRow unread grammar', () => {
  it('bolds the title and shows an amber badge when unread > 0', () => {
    render(<ActivityRow channel={channel('a')} selected={false} unread={3} onSelect={() => {}} />);

    const title = screen.getByText('a');
    expect(title.className).toMatch(/font-bold/);

    const badge = screen.getByText('3');
    expect(badge.className).toMatch(/bg-sn-attention/);
    expect(badge.className).toMatch(/text-sn-ink-on-accent/);
  });

  it('does not bold the title and shows no badge when unread is 0', () => {
    render(<ActivityRow channel={channel('a')} selected={false} unread={0} onSelect={() => {}} />);

    const title = screen.getByText('a');
    expect(title.className).not.toMatch(/font-bold/);
    expect(title.className).toMatch(/font-medium/);
    expect(screen.queryByText('0')).toBeNull();
  });
});
