import { ReactNode, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../auth/authStore';
import { authClient } from '../auth/authClient';
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
    <div className="font-mono text-[11px] uppercase tracking-wide text-console-text-muted mb-2 mt-6 first:mt-0">
      {children}
    </div>
  );
}

function Row({ children, onClick }: { children: ReactNode; onClick?: () => void }) {
  return (
    <div
      onClick={onClick}
      className={
        'bg-console-surface border border-console-border rounded p-3 ' +
        (onClick ? 'cursor-pointer hover:border-console-accent transition-colors' : '')
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
  const [switching, setSwitching] = useState(false);
  // Team
  const [invite, setInvite] = useState<{ code: string; expiresAt: string } | null>(null);
  const [genBusy, setGenBusy] = useState(false);
  const [joinCode, setJoinCode] = useState('');
  const [joinBusy, setJoinBusy] = useState(false);

  if (!user) return null;

  const dirty = name.trim() !== '' && name.trim() !== user.displayName;
  const currentMode: 'solo' | 'foreman' = user.role === 'solo' ? 'solo' : 'foreman';

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
      <h1 className="text-console-text text-lg mb-2">Settings</h1>

      {/* PROFILE */}
      <SectionHeader>Profile</SectionHeader>
      <Row>
        <div className="flex items-center gap-4 mb-3">
          <Avatar name={user.displayName} color={accentForId(user.id)} size={56} />
          <div className="flex flex-col gap-1.5">
            <span className="text-console-text text-base">{user.displayName}</span>
            <span>
              <Chip label={user.role.toUpperCase()} color={colorForRole(user.role)} xs />
            </span>
          </div>
        </div>
        <p className="text-console-text-muted text-xs mb-3">
          Avatar is generated from your name. Photo upload is coming soon.
        </p>
        <label className="block text-xs text-console-text-muted mb-1">Display name</label>
        <div className="flex items-center gap-2">
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') saveName();
            }}
            className="flex-1 bg-console-bg border border-console-border rounded px-3 py-2 text-sm text-console-text focus:border-console-accent outline-none"
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
                <span className={selected ? 'text-console-accent' : 'text-console-text-muted'}>
                  {selected ? '(●)' : '(○)'}
                </span>
                <div className="flex flex-col">
                  <span className={selected ? 'text-console-accent text-sm' : 'text-console-text text-sm'}>
                    {o.title}
                  </span>
                  <span className="text-console-text-muted text-xs">{o.desc}</span>
                </div>
              </div>
            </Row>
          );
        })}
      </div>
      <p className="text-console-text-muted text-xs mt-2">
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
              <div className="flex items-center justify-between bg-console-bg border border-console-border rounded px-3 py-2">
                <span className="text-console-text text-sm tracking-widest">{invite.code}</span>
                <Pill onClick={copyCode}>copy</Pill>
              </div>
              <p className="text-console-text-muted text-xs mt-1">
                Expires {invite.expiresAt.slice(0, 10)}. One-time use.
              </p>
            </div>
          )}
          <p className="text-console-text-muted text-xs mt-2">Share the code with a crew member to add them.</p>
        </Row>
      ) : (
        <Row>
          <label className="block text-xs text-console-text-muted mb-1">Join a team</label>
          <div className="flex items-center gap-2">
            <input
              value={joinCode}
              onChange={(e) => setJoinCode(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') joinTeam();
              }}
              placeholder="invite code"
              className="flex-1 bg-console-bg border border-console-border rounded px-3 py-2 text-sm text-console-text focus:border-console-accent outline-none"
            />
            <Button onClick={joinTeam} disabled={joinBusy || !joinCode.trim()}>
              {joinBusy ? 'Joining…' : 'Join'}
            </Button>
          </div>
          <p className="text-console-text-muted text-xs mt-2">Enter a foreman's invite code to join their team.</p>
        </Row>
      )}

      {/* LOCATION SHARING */}
      <SectionHeader>Location sharing</SectionHeader>
      <Row>
        <div className="flex flex-col gap-2">
          <ShareLocationToggle />
          <span className="text-console-text-muted text-xs">
            Powers clock-in geofence validation and crew presence on the map.
          </span>
        </div>
      </Row>

      {/* ABOUT */}
      <SectionHeader>About</SectionHeader>
      <Row>
        <div className="text-console-text text-sm">Smith Net — Console</div>
        <div className="text-console-text-muted text-xs">Guild of Smiths</div>
      </Row>

      {/* ADMIN (admin only -- the advanced console lives behind the gear) */}
      {user.role === 'admin' && (
        <>
          <SectionHeader>Admin</SectionHeader>
          <Row onClick={() => navigate('/console/admin')}>
            <div className="flex items-center justify-between">
              <span className="text-console-text text-sm">Admin console</span>
              <span className="text-console-text-muted">{'>'}</span>
            </div>
          </Row>
        </>
      )}

      {/* ACCOUNT */}
      <SectionHeader>Account</SectionHeader>
      <Row>
        <div className="flex items-center justify-between py-1">
          <span className="text-console-text-muted text-sm">Email</span>
          <span className="text-console-text text-sm">{user.email}</span>
        </div>
        <div className="flex items-center justify-between py-1 mt-1 border-t border-console-border pt-2">
          <span className="text-console-text-muted text-sm">Email verified</span>
          <span className="text-console-text text-sm">{user.emailVerified ? 'yes' : 'no'}</span>
        </div>
      </Row>
      <div className="mt-2">
        <Row onClick={logout}>
          <div className="flex items-center justify-between">
            <span className="text-console-text text-sm">Sign out</span>
            <span className="text-console-text-muted">{'>'}</span>
          </div>
        </Row>
      </div>
    </div>
  );
}
