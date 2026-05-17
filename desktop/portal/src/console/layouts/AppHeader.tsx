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
  const hasForemanTier = useAuthStore((s) => s.hasForemanTier);
  const navigate = useNavigate();
  const { hh, mm, ss } = useCurrentTime();

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
      {/* Brand — tagline hides on mobile so the row fits at 390px. */}
      <div className="flex flex-col leading-tight pr-2 flex-shrink-0">
        <span
          className="text-console-text text-base font-semibold whitespace-nowrap"
          style={{ fontFamily: 'var(--font-display)', letterSpacing: '0.03em' }}
        >
          smith net
        </span>
        <span className="hidden md:inline text-console-text-muted text-[10px] uppercase tracking-widest whitespace-nowrap">
          guild of smiths · console
        </span>
      </div>

      {/* Inline nav — hidden on mobile (replaced by BottomTabBar). */}
      <nav className="hidden md:flex items-center gap-1 border-l border-console-border pl-3">
        {hasForemanTier() && <NavButton to="/console" label="Map" end />}
        {hasForemanTier() && <NavButton to="/console/jobs" label="Jobs" />}
        {hasForemanTier() && <NavButton to="/console/crew" label="Crew" />}
        <NavButton to="/console/comm" label="Comm" />
        {user.role === 'admin' && <NavButton to="/console/admin" label="Admin" />}
      </nav>

      <div className="flex-1" />

      {/* Clock chip lives in ConsoleShell's share-location bar now so it
          works on both desktop and mobile. */}

      <div className="hidden md:flex items-baseline gap-1 px-2">
        <span className="text-console-text text-lg tabular-nums" style={{ fontFamily: 'var(--font-mono)' }}>
          {hh}:{mm}
        </span>
        <span className="text-console-text-muted text-xs tabular-nums" style={{ fontFamily: 'var(--font-mono)' }}>
          :{ss}
        </span>
      </div>

      {/* User card — role chip hidden on mobile to keep the header on one
          tidy row. Avatar + name carry identity; role is visible in desktop
          context where the chip fits comfortably. */}
      <div className="flex items-center gap-2 md:border-l md:border-console-border md:pl-3">
        <Avatar name={user.displayName} color={accentForId(user.id)} size={28} />
        <div className="flex flex-col leading-tight">
          <span className="text-console-text text-xs font-medium whitespace-nowrap">{user.displayName}</span>
          <span className="hidden md:inline">
            <Chip label={user.role.toUpperCase()} color={colorForRole(user.role)} xs />
          </span>
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
