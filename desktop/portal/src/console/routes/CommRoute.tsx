// desktop/portal/src/console/routes/CommRoute.tsx
//
// Responsive shell: stacks vertically on mobile and switches to a two-pane
// side-by-side at md:+ (768px). On mobile, exactly one pane is visible at a
// time — the channel list when nothing is selected, the message pane (with
// a [← back] row) once a channel is selected. Mirrors the Android
// ConversationScreen single-pane navigation pattern.

import { useEffect, useState } from 'react';
import type { Channel } from '../../types';
import { useCommWebSocket } from '../hooks/useCommWebSocket';
import { useCommStore } from '../stores/commStore';
import { useCrewRoster } from '../hooks/useCrewRoster';
import { useCrewStore } from '../stores/crewStore';
import { commClient } from '../api/commClient';
import { Pill } from '../components/ui/Pill';
import { ChannelList } from '../components/comm/ChannelList';
import { MessageList } from '../components/comm/MessageList';
import { MessageInput } from '../components/comm/MessageInput';

export function CommRoute() {
  useCommWebSocket();
  const channels = useCommStore((s) => s.channels);
  const selectedId = useCommStore((s) => s.selectedChannelId);
  const select = useCommStore((s) => s.selectChannel);
  const addChannel = useCommStore((s) => s.addChannel);
  const isStale = useCommStore((s) => s.isStaleChannels);

  // Roster for the member picker (who to add to a group/DM).
  useCrewRoster();
  const roster = useCrewStore((s) => s.roster);

  // "New conversation" — create a channel (backend POST /api/channels) so a user
  // with no channels can actually start messaging.
  const [showNew, setShowNew] = useState(false);
  const [newName, setNewName] = useState('');
  const [newType, setNewType] = useState<Channel['type']>('group');
  const [members, setMembers] = useState<Set<string>>(new Set());
  const [creating, setCreating] = useState(false);
  const [createErr, setCreateErr] = useState<string | null>(null);

  function toggleMember(id: string) {
    setMembers((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  async function createChannel() {
    const name = newName.trim();
    if (!name || creating) return;
    setCreating(true);
    setCreateErr(null);
    const r = await commClient.createChannel({
      name,
      type: newType,
      memberIds: members.size ? [...members] : undefined,
    });
    setCreating(false);
    if (r.ok) {
      addChannel(r.channel);
      select(r.channel.id);
      setNewName('');
      setMembers(new Set());
      setShowNew(false);
    } else {
      setCreateErr(r.error || 'Could not create channel');
    }
  }

  useEffect(() => {
    if (!selectedId) return;
    let alive = true;
    commClient.listMessages(selectedId).then((r) => {
      if (alive && r.ok) {
        useCommStore.getState().setMessages(selectedId, r.messages);
      }
    });
    return () => {
      alive = false;
    };
  }, [selectedId]);

  const selectedChannel = selectedId ? channels.find((c) => c.id === selectedId) : null;

  return (
    <div className="font-mono h-full flex flex-col md:flex-row">
      <aside
        className={
          'w-full md:w-72 md:flex-shrink-0 border-b md:border-b-0 md:border-r border-console-border md:overflow-y-auto ' +
          (selectedId ? 'hidden md:block' : 'block')
        }
      >
        <div className="px-3 py-2 flex items-center justify-between">
          <span className="text-console-text-muted text-xs uppercase tracking-wide">Channels</span>
          <Pill active={showNew} onClick={() => { setShowNew((v) => !v); setCreateErr(null); }}>
            {showNew ? 'cancel' : '+ new'}
          </Pill>
        </div>
        {showNew && (
          <div className="px-3 pb-2 flex flex-col gap-2 border-b border-console-border">
            <input
              autoFocus
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') createChannel(); }}
              placeholder="channel name"
              className="bg-console-bg border border-console-border px-2 py-1 text-sm text-console-text focus:border-console-accent outline-none"
            />
            <div className="flex items-center gap-1">
              {(['group', 'broadcast', 'dm'] as const).map((t) => (
                <Pill key={t} active={newType === t} onClick={() => setNewType(t)}>
                  {t}
                </Pill>
              ))}
              <Pill
                tone="ok"
                className="ml-auto"
                onClick={createChannel}
                disabled={creating || !newName.trim()}
              >
                {creating ? '…' : 'create'}
              </Pill>
            </div>
            {/* Member picker -- who to add (memberIds). DMs/groups need people. */}
            <div className="flex flex-col gap-1">
              <span className="text-console-text-muted text-[10px] uppercase tracking-wide">
                Members{members.size > 0 ? ` (${members.size})` : ''}
              </span>
              {roster.length === 0 ? (
                <span className="text-console-text-muted text-[11px]">No crew to add yet.</span>
              ) : (
                <div className="max-h-28 overflow-y-auto flex flex-wrap gap-1">
                  {roster.map((m) => (
                    <Pill key={m.id} active={members.has(m.id)} onClick={() => toggleMember(m.id)}>
                      {m.displayName}
                    </Pill>
                  ))}
                </div>
              )}
            </div>
            {createErr && <span className="text-console-warn text-[11px]">{createErr}</span>}
          </div>
        )}
        {isStale && (
          <div className="bg-console-surface border border-console-warn text-console-warn px-3 py-1 text-xs">
            [OFFLINE] Couldn't refresh
          </div>
        )}
        <ChannelList channels={channels} selectedId={selectedId} onSelect={select} />
      </aside>
      <main className={`flex-1 flex-col min-w-0 ${selectedId ? 'flex' : 'hidden md:flex'}`}>
        {selectedId ? (
          <>
            <div className="md:hidden border-b border-console-border bg-console-surface px-3 py-2 flex items-center gap-3">
              <button
                type="button"
                onClick={() => select(null)}
                className="text-console-accent text-sm font-mono"
                aria-label="Back to channels"
              >
                [← back]
              </button>
              <span className="text-console-text-muted text-xs uppercase tracking-wide truncate">
                {selectedChannel?.name ?? 'channel'}
              </span>
            </div>
            <MessageList channelId={selectedId} />
            <MessageInput channelId={selectedId} />
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-console-text-muted text-sm">
            Select a channel to start.
          </div>
        )}
      </main>
    </div>
  );
}
