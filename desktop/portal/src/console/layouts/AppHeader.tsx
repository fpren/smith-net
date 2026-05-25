// desktop/portal/src/console/layouts/AppHeader.tsx
//
// Rich header that replaces ConsoleShell's bare top bar. Ported in spirit
// from the dashboard module's AppHeader, adapted to the data the console
// backend actually exposes:
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
    <header className="border-b border-console-border bg-console-surface px-4 py-2 flex items-center gap-2 sm:gap-4 font-mono">
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
        <NavButton to="/console/home" label="Home" />
        <NavButton to="/console/time" label="Clock" />
        {hasForemanTier() && <NavButton to="/console" label="Map" end />}
        {hasForemanTier() && <NavButton to="/console/jobs" label="Jobs" />}
        {hasForemanTier() && <NavButton to="/console/clients" label="Clients" />}
        {hasForemanTier() && <NavButton to="/console/invoices" label="Invoices" />}
        {hasForemanTier() && <NavButton to="/console/crew" label="Crew" />}
        <NavButton to="/console/comm" label="Comm" />
      </nav>

      <div className="flex-1" />

      {/* User card (identity) + a gear that opens Settings. Role chip hidden on
          mobile to keep the header on one tidy row. */}
      <div className="flex items-center gap-2 min-w-0 md:border-l md:border-console-border md:pl-3">
        <Avatar name={user.displayName} color={accentForId(user.id)} size={28} />
        <div className="flex flex-col leading-tight min-w-0">
          <span className="text-console-text text-xs font-medium truncate">{user.displayName}</span>
          <span className="hidden md:inline">
            <Chip label={user.role.toUpperCase()} color={colorForRole(user.role)} xs />
          </span>
        </div>
        <NavLink
          to="/console/settings"
          title="Settings"
          aria-label="Settings"
          className={({ isActive }) =>
            'ml-1 p-1 rounded transition-colors ' +
            (isActive ? 'text-console-accent' : 'text-console-text-muted hover:text-console-accent')
          }
        >
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <circle cx="12" cy="12" r="3" />
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
          </svg>
        </NavLink>
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
