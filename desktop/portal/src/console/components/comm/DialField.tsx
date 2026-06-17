// desktop/portal/src/console/components/comm/DialField.tsx
// Paste or type a SmithNet public id to open (or create) a DM. This is the
// portal's entry to the cross-org DM feature (POST /api/dm).

import { useState } from 'react';
import { commClient } from '../../api/commClient';
import { useCommStore } from '../../stores/commStore';
import { useToastStore } from '../../stores/toastStore';
import { normalizePublicId } from './commHelpers';

export function DialField() {
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  const addChannel = useCommStore((s) => s.addChannel);
  const select = useCommStore((s) => s.selectChannel);
  const pushToast = useToastStore((s) => s.push);

  const id = normalizePublicId(value);
  const valid = id !== null;

  async function open() {
    if (!id || busy) return;
    setBusy(true);
    const r = await commClient.createDm(id);
    setBusy(false);
    if (r.ok) {
      addChannel(r.channel);
      select(r.channel.id);
      setValue('');
    } else {
      pushToast({ message: r.error || 'Could not open DM', tone: 'error', duration: 3000 });
    }
  }

  return (
    <div>
      <div className="text-[10px] tracking-[0.15em] text-console-text-dim font-commmono">
        DIAL A SMITHNET ID
      </div>
      <input
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => { if (e.key === 'Enter') open(); }}
        placeholder="paste or type id"
        spellCheck={false}
        autoCapitalize="characters"
        className="mt-1.5 w-full rounded-lg bg-console-bg border border-console-accent/70 px-3 py-2 text-center font-commmono tracking-[0.18em] text-console-text outline-none focus:border-console-accent placeholder:tracking-normal placeholder:text-console-text-dim"
      />
      <button
        type="button"
        onClick={open}
        disabled={!valid || busy}
        className="mt-2 w-full rounded-full bg-console-accent text-white font-commsans font-semibold text-sm py-2 transition-opacity disabled:opacity-40 hover:opacity-90"
      >
        {busy ? '…' : 'open conversation'}
      </button>
    </div>
  );
}
