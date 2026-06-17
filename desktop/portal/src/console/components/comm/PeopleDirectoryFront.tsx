// desktop/portal/src/console/components/comm/PeopleDirectoryFront.tsx
// People front: browse your network and one-tap message. Segments:
//   team   -> /api/profiles/teammates (same org)
//   recent -> derived from existing DM channels (zero backend)
//   nearby -> stub (no cross-org proximity index exists yet)

import { useMemo, useState } from 'react';
import type { Channel } from '../../../types';
import type { Profile } from '../../api/commClient';
import { commClient } from '../../api/commClient';
import { useCommStore } from '../../stores/commStore';
import { useAuthStore } from '../../auth/authStore';
import { useDirectory } from '../../hooks/useDirectory';
import { useToastStore } from '../../stores/toastStore';
import { DirectoryRow } from './DirectoryRow';

type Segment = 'team' | 'recent' | 'nearby';

interface Props { channels: Channel[]; }

export function PeopleDirectoryFront({ channels }: Props) {
  const [segment, setSegment] = useState<Segment>('team');
  const [busyId, setBusyId] = useState<string | null>(null);
  const { list: team } = useDirectory();
  const self = useAuthStore((s) => s.user);
  const addChannel = useCommStore((s) => s.addChannel);
  const select = useCommStore((s) => s.selectChannel);
  const pushToast = useToastStore((s) => s.push);

  // Recent = the other member of each existing DM, as a thin Profile.
  const recent: Profile[] = useMemo(() => {
    return channels
      .filter((c) => c.type === 'dm')
      .map((c) => {
        const peerId = c.memberIds.find((m) => m !== self?.id) ?? c.id;
        const parts = c.name.split('<>').map((s) => s.trim());
        const name = parts.length === 2 ? parts.find((p) => p && p !== self?.displayName) ?? parts[1] : c.name;
        return { id: peerId, displayName: name || 'peer' } as Profile;
      });
  }, [channels, self?.id, self?.displayName]);

  async function message(p: Profile) {
    // Existing DM with this peer? Just open it.
    const existing = channels.find(
      (c) => c.type === 'dm' && c.memberIds.includes(p.id) && (self ? c.memberIds.includes(self.id) : true),
    );
    if (existing) { select(existing.id); return; }
    if (!p.publicId) {
      pushToast({ message: 'No public id for this person', tone: 'error', duration: 2500 });
      return;
    }
    setBusyId(p.id);
    const r = await commClient.createDm(p.publicId.replace(/[^A-Za-z0-9]/g, ''));
    setBusyId(null);
    if (r.ok) { addChannel(r.channel); select(r.channel.id); }
    else pushToast({ message: r.error || 'Could not open DM', tone: 'error', duration: 3000 });
  }

  const rows = segment === 'team' ? team : segment === 'recent' ? recent : [];

  return (
    <div className="comm-surface flex flex-col overflow-y-auto">
      <div className="flex gap-1 px-2 py-2">
        {(['team', 'recent', 'nearby'] as Segment[]).map((s) => (
          <button
            key={s}
            type="button"
            onClick={() => setSegment(s)}
            className={
              'rounded-full font-commmono text-[10px] px-3 py-1 transition-colors ' +
              (segment === s ? 'bg-console-accent text-white' : 'border border-console-border text-console-text-muted hover:text-console-accent')
            }
          >
            {s}
          </button>
        ))}
      </div>
      {segment === 'nearby' ? (
        <div className="px-3 py-6 text-center text-console-text-muted text-sm">Nearby peers — coming soon.</div>
      ) : rows.length === 0 ? (
        <div className="px-3 py-6 text-center text-console-text-muted text-sm">
          {segment === 'team' ? 'No teammates yet.' : 'No recent conversations.'}
        </div>
      ) : (
        rows.map((p) => <DirectoryRow key={p.id} profile={p} busy={busyId === p.id} onMessage={() => message(p)} />)
      )}
    </div>
  );
}
