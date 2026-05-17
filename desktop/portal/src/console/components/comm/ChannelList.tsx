// desktop/portal/src/console/components/comm/ChannelList.tsx
import type { Channel } from '../../../types';

interface Props {
  channels: Channel[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export function ChannelList({ channels, selectedId, onSelect }: Props) {
  if (channels.length === 0) {
    return (
      <div className="px-3 py-4 text-console-text-muted text-sm font-mono">
        No channels yet.
      </div>
    );
  }

  const sorted = [...channels].sort((a, b) => b.createdAt - a.createdAt);

  return (
    <ul className="font-mono">
      {sorted.map((ch) => {
        const isSelected = selectedId === ch.id;
        return (
          <li key={ch.id}>
            <button
              type="button"
              onClick={() => onSelect(ch.id)}
              className={
                'w-full text-left px-3 py-2 border-l-2 flex items-center justify-between text-sm transition-colors ' +
                (isSelected
                  ? 'border-l-console-accent text-console-accent bg-console-surface'
                  : 'border-l-transparent text-console-text hover:text-console-accent hover:bg-console-surface')
              }
            >
              <span className="truncate">{ch.name}</span>
              <span className="text-xs text-console-text-muted ml-2 uppercase">
                {ch.type === 'dm' ? 'dm' : ch.isArchived ? 'arch' : ''}
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
