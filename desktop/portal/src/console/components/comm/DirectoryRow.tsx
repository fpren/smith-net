// desktop/portal/src/console/components/comm/DirectoryRow.tsx
import type { Profile } from '../../api/commClient';
import { Avatar } from '../ui/Avatar';
import { accentForId } from '../../lib/utils';
import { formatPublicId } from './commHelpers';

interface Props {
  profile: Profile;
  busy?: boolean;
  onMessage: () => void;
}

export function DirectoryRow({ profile, busy, onMessage }: Props) {
  return (
    <div className="flex items-center gap-2.5 px-2.5 py-2 rounded-xl mx-1 hover:bg-console-bg/60 transition-colors">
      <Avatar name={profile.displayName} color={accentForId(profile.id)} size={30} photoUrl={profile.avatarUrl} />
      <span className="flex-1 min-w-0">
        <span className="block font-commsans text-sm font-semibold text-console-text truncate">{profile.displayName}</span>
        <span className="block font-commmono text-[11px] text-console-text-dim truncate">
          {[profile.role, profile.publicId ? formatPublicId(profile.publicId) : null].filter(Boolean).join(' · ')}
        </span>
      </span>
      <button
        type="button"
        onClick={onMessage}
        disabled={busy}
        className="flex-shrink-0 rounded-full border border-console-accent text-console-accent font-commmono text-[11px] px-3 py-1 hover:bg-console-accent hover:text-white transition-colors disabled:opacity-40"
      >
        {busy ? '…' : 'message'}
      </button>
    </div>
  );
}
