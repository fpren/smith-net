import { ReactNode, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../auth/authStore';
import { authClient } from '../auth/authClient';
import { commClient } from '../api/commClient';
import { useMyProfile, setMyProfile } from '../hooks/useMyProfile';
import { MyIdCard } from '../components/comm/MyIdCard';
import { Avatar } from '../components/ui/Avatar';
import { Chip } from '../components/ui/Chip';
import { Button } from '../components/ui/Button';
import { Pill } from '../components/ui/Pill';
import { ShareLocationToggle } from '../components/header/ShareLocationToggle';
import { accentForId, colorForRole } from '../lib/utils';
import { useToastStore } from '../stores/toastStore';

// Mirrors the Android SettingsScreen's sectioned layout (PROFILE / WORK MODE /
// LOCATION SHARING / ABOUT / ACCOUNT) rather than inventing one. Device-only
// APK sections (Mesh, Gateway, on-device SmithAI) don't apply on the web;
// Team invites / Privacy / Trade role need more backend wiring and are deferred.

function SectionHeader({ children }: { children: ReactNode }) {
  return (
    <div className="font-mono text-[11px] uppercase tracking-wide text-sn-ink-muted mb-2 mt-6 first:mt-0">
      {children}
    </div>
  );
}

function Row({ children, onClick }: { children: ReactNode; onClick?: () => void }) {
  return (
    <div
      onClick={onClick}
      className={
        'bg-sn-bg-panel border border-sn-line rounded p-3 ' +
        (onClick ? 'cursor-pointer hover:border-sn-accent transition-colors' : '')
      }
    >
      {children}
    </div>
  );
}

const WORK_MODES = [
  { mode: 'solo' as const, title: 'Solo', desc: 'Jobs, time tracking, invoicing — just for me.' },
  { mode: 'foreman' as const, title: 'Foreman', desc: 'Crew tracking, dispatch, team invoicing.' },
];

export function SettingsRoute() {
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);
  const clear = useAuthStore((s) => s.clear);
  const hasForemanRole = useAuthStore((s) => s.hasForemanRole);
  const navigate = useNavigate();
  const pushToast = useToastStore((s) => s.push);
  const [name, setName] = useState(user?.displayName ?? '');
  const [saving, setSaving] = useState(false);
  const me = useMyProfile();
  const fileRef = useRef<HTMLInputElement>(null);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [switching, setSwitching] = useState(false);
  // Team
  const [invite, setInvite] = useState<{ code: string; expiresAt: string } | null>(null);
  const [genBusy, setGenBusy] = useState(false);
  const [joinCode, setJoinCode] = useState('');
  const [joinBusy, setJoinBusy] = useState(false);

  if (!user) return null;

  const dirty = name.trim() !== '' && name.trim() !== user.displayName;
  const currentMode: 'solo' | 'foreman' = user.role === 'solo' ? 'solo' : 'foreman';

  async function onPickAvatar(file: File | undefined) {
    if (!file || uploadingAvatar) return;
    setUploadingAvatar(true);
    const r = await commClient.uploadAvatar(file);
    setUploadingAvatar(false);
    if (r.ok) {
      if (me) setMyProfile({ ...me, avatarUrl: r.avatarUrl });
      pushToast({ message: 'Photo updated', tone: 'info', duration: 2500 });
    } else {
      pushToast({ message: r.error || 'Upload failed', tone: 'error', duration: 3000 });
    }
  }

  async function saveName() {
    if (!dirty || saving) return;
    setSaving(true);
    const r = await authClient.updateProfile(name.trim());
    setSaving(false);
    if (r.ok) {
      setUser(r.user);
      pushToast({ message: 'Profile updated', tone: 'info', duration: 2500 });
    } else {
      pushToast({ message: r.error || 'Update failed', tone: 'error', duration: 3000 });
    }
  }

  async function setWorkMode(mode: 'solo' | 'foreman') {
    if (switching || mode === currentMode) return;
    setSwitching(true);
    const r = await authClient.updateWorkMode(mode);
    setSwitching(false);
    if (r.ok) {
      setUser(r.user);
      pushToast({ message: `Work mode: ${mode}`, tone: 'info', duration: 2500 });
    } else {
      pushToast({ message: r.error || 'Could not switch', tone: 'error', duration: 3000 });
    }
  }

  async function generateInvite() {
    if (genBusy) return;
    setGenBusy(true);
    const r = await authClient.createOrgInvite();
    setGenBusy(false);
    if (r.ok) setInvite({ code: r.code, expiresAt: r.expiresAt });
    else pushToast({ message: r.error || 'Could not create invite', tone: 'error', duration: 3000 });
  }

  function copyCode() {
    if (invite) {
      navigator.clipboard?.writeText(invite.code);
      pushToast({ message: 'Code copied', tone: 'info', duration: 2000 });
    }
  }

  async function joinTeam() {
    const code = joinCode.trim();
    if (!code || joinBusy) return;
    setJoinBusy(true);
    const r = await authClient.joinOrg(code);
    setJoinBusy(false);
    if (r.ok) {
      setUser(r.user);
      setJoinCode('');
      pushToast({ message: 'Joined team', tone: 'info', duration: 2500 });
    } else {
      pushToast({ message: r.error || 'Could not join', tone: 'error', duration: 3000 });
    }
  }

  async function logout() {
    await authClient.logout();
    clear();
    navigate('/console/login');
  }

  return (
    <div className="font-mono max-w-xl mx-auto pb-8">
      <h1 className="text-sn-ink text-lg mb-2">Settings</h1>

      {/* PROFILE */}
      <SectionHeader>Profile</SectionHeader>
      <Row>
        <div className="flex items-center gap-4 mb-3">
          <button
            type="button"
            onClick={() => fileRef.current?.click()}
            className="relative rounded-full focus:outline-none focus:ring-2 focus:ring-sn-accent"
            aria-label="Change photo"
            title="Change photo"
          >
            <Avatar name={user.displayName} color={accentForId(user.id)} size={56} photoUrl={me?.avatarUrl} />
            <span className="absolute -bottom-1 -right-1 bg-sn-accent text-sn-ink-on-accent rounded-full w-5 h-5 grid place-items-center text-[10px] font-mono">
              {uploadingAvatar ? '…' : '+'}
            </span>
          </button>
          <input
            ref={fileRef}
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(e) => onPickAvatar(e.target.files?.[0])}
          />
          <div className="flex flex-col gap-1.5">
            <span className="text-sn-ink text-base">{user.displayName}</span>
            <span>
              <Chip label={user.role.toUpperCase()} color={colorForRole(user.role)} xs />
            </span>
          </div>
        </div>
        <p className="text-sn-ink-muted text-xs mb-3">
          Tap your photo to upload one. Falls back to your initials.
        </p>
        {/* SmithNet id + copy / share / QR (shared with the comm dial rail). */}
        <div className="comm-surface mb-1">
          <MyIdCard />
        </div>
        <label className="block text-xs text-sn-ink-muted mb-1">Display name</label>
        <div className="flex items-center gap-2">
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') saveName();
            }}
            className="flex-1 bg-sn-bg-base border border-sn-line rounded px-3 py-2 text-sm text-sn-ink focus:border-sn-accent outline-none"
          />
          <Button onClick={saveName} disabled={!dirty || saving}>
            {saving ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </Row>

      {/* WORK MODE */}
      <SectionHeader>Work mode</SectionHeader>
      <div className="flex flex-col gap-2">
        {WORK_MODES.map((o) => {
          const selected = o.mode === currentMode;
          return (
            <Row key={o.mode} onClick={() => setWorkMode(o.mode)}>
              <div className="flex items-start gap-2">
                <span className={selected ? 'text-sn-accent' : 'text-sn-ink-muted'}>
                  {selected ? '(●)' : '(○)'}
                </span>
                <div className="flex flex-col">
                  <span className={selected ? 'text-sn-accent text-sm' : 'text-sn-ink text-sm'}>
                    {o.title}
                  </span>
                  <span className="text-sn-ink-muted text-xs">{o.desc}</span>
                </div>
              </div>
            </Row>
          );
        })}
      </div>
      <p className="text-sn-ink-muted text-xs mt-2">
        Changes your dashboard layout, permissions, and available features.
      </p>

      {/* TEAM */}
      <SectionHeader>Team</SectionHeader>
      {hasForemanRole() ? (
        <Row>
          <Button variant="secondary" onClick={generateInvite} disabled={genBusy}>
            {genBusy ? 'Generating…' : 'Generate invite code'}
          </Button>
          {invite && (
            <div className="mt-3">
              <div className="flex items-center justify-between bg-sn-bg-base border border-sn-line rounded px-3 py-2">
                <span className="text-sn-ink text-sm tracking-widest">{invite.code}</span>
                <Pill onClick={copyCode}>copy</Pill>
              </div>
              <p className="text-sn-ink-muted text-xs mt-1">
                Expires {invite.expiresAt.slice(0, 10)}. One-time use.
              </p>
            </div>
          )}
          <p className="text-sn-ink-muted text-xs mt-2">Share the code with a crew member to add them.</p>
        </Row>
      ) : (
        <Row>
          <label className="block text-xs text-sn-ink-muted mb-1">Join a team</label>
          <div className="flex items-center gap-2">
            <input
              value={joinCode}
              onChange={(e) => setJoinCode(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') joinTeam();
              }}
              placeholder="invite code"
              className="flex-1 bg-sn-bg-base border border-sn-line rounded px-3 py-2 text-sm text-sn-ink focus:border-sn-accent outline-none"
            />
            <Button onClick={joinTeam} disabled={joinBusy || !joinCode.trim()}>
              {joinBusy ? 'Joining…' : 'Join'}
            </Button>
          </div>
          <p className="text-sn-ink-muted text-xs mt-2">Enter a foreman's invite code to join their team.</p>
        </Row>
      )}

      {/* LOCATION SHARING */}
      <SectionHeader>Location sharing</SectionHeader>
      <Row>
        <div className="flex flex-col gap-2">
          <ShareLocationToggle />
          <span className="text-sn-ink-muted text-xs">
            Powers clock-in geofence validation and crew presence on the map.
          </span>
        </div>
      </Row>

      {/* ABOUT */}
      <SectionHeader>About</SectionHeader>
      <Row>
        <div className="text-sn-ink text-sm">Smith Net — Console</div>
        <div className="text-sn-ink-muted text-xs">Guild of Smiths</div>
      </Row>

      {/* ADMIN (admin only -- the advanced console lives behind the gear) */}
      {user.role === 'admin' && (
        <>
          <SectionHeader>Admin</SectionHeader>
          <Row onClick={() => navigate('/console/admin')}>
            <div className="flex items-center justify-between">
              <span className="text-sn-ink text-sm">Admin console</span>
              <span className="text-sn-ink-muted">{'>'}</span>
            </div>
          </Row>
        </>
      )}

      {/* ACCOUNT */}
      <SectionHeader>Account</SectionHeader>
      <Row>
        <div className="flex items-center justify-between py-1">
          <span className="text-sn-ink-muted text-sm">Email</span>
          <span className="text-sn-ink text-sm">{user.email}</span>
        </div>
        <div className="flex items-center justify-between py-1 mt-1 border-t border-sn-line pt-2">
          <span className="text-sn-ink-muted text-sm">Email verified</span>
          <span className="text-sn-ink text-sm">{user.emailVerified ? 'yes' : 'no'}</span>
        </div>
      </Row>
      <div className="mt-2">
        <Row onClick={logout}>
          <div className="flex items-center justify-between">
            <span className="text-sn-ink text-sm">Sign out</span>
            <span className="text-sn-ink-muted">{'>'}</span>
          </div>
        </Row>
      </div>
    </div>
  );
}
