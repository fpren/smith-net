// desktop/portal/src/console/layouts/AppHeader.tsx
//
// Rich header that replaces ConsoleShell's bare top bar. Ported in spirit
// from the dashboard module's AppHeader, adapted to the data the console
// backend actually exposes:
//   - real local clock (useCurrentTime)
//   - real ON CLOCK status from /api/shifts/current (useCurrentShift)
//   - real user identity from authStore
//   - inline nav buttons (replaces the sidebar's nav)
//   - role chip + avatar
//
// Intentionally NOT mocking: team-today hours, inbox count, [+ NEW] action.
// Those need backend work not in scope for this slice.

import { NavLink, useNavigate } from 'react-router-dom';
import { Chip } from '../components/ui/Chip';
import { Avatar } from '../components/ui/Avatar';
import { authClient } from '../auth/authClient';
import { useAuthStore } from '../auth/authStore';
import { useCurrentTime } from '../hooks/useCurrentTime';
import { useCurrentShift } from '../hooks/useCurrentShift';
import { accentForId, colorForRole } from '../lib/utils';

function NavButton({ to, label, end }: { to: string; label: string; end?: boolean }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        `px-2 py-1 text-xs font-mono uppercase tracking-wide cursor-pointer transition-colors ` +
        (isActive
          ? 'text-console-accent'
          : 'text-console-text-muted hover:text-console-accent')
      }
    >
      [{label}]
    </NavLink>
  );
}

export function AppHeader() {
  const user = useAuthStore((s) => s.user);
  const clear = useAuthStore((s) => s.clear);
  const navigate = useNavigate();
  const { hh, mm, ss } = useCurrentTime();
  const { onClock } = useCurrentShift();

  async function onLogout() {
    await authClient.logout();
    clear();
    navigate('/console/login');
  }

  if (!user) {
    return (
      <header className="border-b border-console-border bg-console-surface px-6 py-3 font-mono">
        <span className="text-console-accent text-sm">SMITH NET / CONSOLE</span>
      </header>
    );
  }

  return (
    <header className="border-b border-console-border bg-console-surface px-4 py-2 flex items-center gap-4 font-mono">
      {/* Brand */}
      <div className="flex flex-col leading-tight pr-2">
        <span
          className="text-console-text text-base font-semibold"
          style={{ fontFamily: 'var(--font-display)', letterSpacing: '0.03em' }}
        >
          smith net
        </span>
        <span className="text-console-text-muted text-[10px] uppercase tracking-widest">
          guild of smiths · console
        </span>
      </div>

      {/* Inline nav */}
      <nav className="flex items-center gap-1 border-l border-console-border pl-3">
        <NavButton to="/console" label="Map" end />
        <NavButton to="/console/jobs" label="Jobs" />
        <NavButton to="/console/crew" label="Crew" />
        {user.role === 'admin' && <NavButton to="/console/admin" label="Admin" />}
      </nav>

      <div className="flex-1" />

      {/* ON CLOCK chip — real status from /api/shifts/current */}
      <Chip
        label={onClock ? '● ON CLOCK' : '○ OFF CLOCK'}
        color={onClock ? '#5A8C76' : '#8C8478'}
      />

      {/* Clock */}
      <div className="flex items-baseline gap-1 px-2">
        <span className="text-console-text text-lg tabular-nums" style={{ fontFamily: 'var(--font-mono)' }}>
          {hh}:{mm}
        </span>
        <span className="text-console-text-muted text-xs tabular-nums" style={{ fontFamily: 'var(--font-mono)' }}>
          :{ss}
        </span>
      </div>

      {/* User card */}
      <div className="flex items-center gap-2 border-l border-console-border pl-3">
        <Avatar name={user.displayName} color={accentForId(user.id)} size={28} />
        <div className="flex flex-col leading-tight">
          <span className="text-console-text text-xs font-medium">{user.displayName}</span>
          <Chip label={user.role.toUpperCase()} color={colorForRole(user.role)} xs />
        </div>
      </div>

      <button
        type="button"
        onClick={onLogout}
        className="text-xs px-2 py-1 border border-console-border text-console-text-muted hover:text-console-accent hover:border-console-accent font-mono"
      >
        Log out
      </button>
    </header>
  );
}
