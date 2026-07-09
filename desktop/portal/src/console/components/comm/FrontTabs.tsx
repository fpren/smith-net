// desktop/portal/src/console/components/comm/FrontTabs.tsx
// Switches the left zone between the three "fronts".

export type CommFront = 'activity' | 'incoming' | 'people';

interface Props {
  front: CommFront;
  onChange: (f: CommFront) => void;
  incomingCount: number;
}

const TABS: { key: CommFront; label: string }[] = [
  { key: 'activity', label: 'activity' },
  { key: 'incoming', label: 'incoming' },
  { key: 'people', label: 'people' },
];

export function FrontTabs({ front, onChange, incomingCount }: Props) {
  return (
    <div className="comm-surface flex gap-1 px-2 py-2 border-b border-sn-line">
      {TABS.map((t) => (
        <button
          key={t.key}
          type="button"
          onClick={() => onChange(t.key)}
          className={
            'flex-1 rounded-full font-commmono text-[11px] py-1.5 transition-colors ' +
            (front === t.key
              ? 'bg-sn-accent text-sn-ink-on-accent'
              : 'text-sn-ink-muted hover:text-sn-accent border border-sn-line')
          }
        >
          {t.label}
          {t.key === 'incoming' && incomingCount > 0 && (
            <span className={'ml-1 rounded-full px-1 ' + (front === t.key ? 'bg-sn-ink-on-accent/25' : 'bg-sn-accent text-sn-ink-on-accent')}>
              {incomingCount}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}
