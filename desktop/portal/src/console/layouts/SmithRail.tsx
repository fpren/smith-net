// desktop/portal/src/console/layouts/SmithRail.tsx
//
// Desktop/lg+ primary navigation. Replaces AppHeader's inline nav row with a
// fixed w-16 icon rail down the left edge (Android-app cadence: short mono
// labels, a pill for the active tab). Below lg the rail is hidden and
// BottomTabBar ("mobile navigation") carries the same destinations.
//
// Role membership mirrors the old AppHeader nav exactly:
//   everyone      -> HO / CLK / COM
//   foreman-tier  -> + MAP / JOB / CLI / INV / CRW  (hasForemanRole())
//   admin only    -> + ADM (role === 'admin', gear stays separate)
import { NavLink, useNavigate } from 'react-router-dom';
import { cn, accentForId } from '../lib/utils';
import { Avatar } from '../components/ui/Avatar';
import { authClient } from '../auth/authClient';
import { useAuthStore } from '../auth/authStore';

const TAB_BASE =
  'flex items-center justify-center h-9 w-9 mx-auto rounded-md text-[10px] font-data ' +
  'font-semibold uppercase tracking-wide transition-colors cursor-pointer shrink-0 ' +
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sn-accent focus-visible:ring-offset-1';

function RailTab({
  to,
  label,
  title,
  end,
}: {
  to: string;
  label: string;
  title: string;
  end?: boolean;
}) {
  return (
    <NavLink
      to={to}
      end={end}
      title={title}
      // The visible label is a mono abbreviation; the accessible name must be
      // the full word (title alone does not set the accessible name).
      aria-label={title}
      className={({ isActive }) =>
        cn(
          TAB_BASE,
          isActive ? 'bg-sn-accent text-sn-ink-on-accent' : 'text-sn-ink-muted hover:text-sn-accent'
        )
      }
    >
      {label}
    </NavLink>
  );
}

export function SmithRail() {
  const user = useAuthStore((s) => s.user);
  const clear = useAuthStore((s) => s.clear);
  const hasForemanRole = useAuthStore((s) => s.hasForemanRole);
  const navigate = useNavigate();

  if (!user) return null;

  async function onLogout() {
    await authClient.logout();
    clear();
    navigate('/console/login');
  }

  return (
    <div className="hidden lg:flex lg:flex-col w-16 shrink-0 items-center gap-1 py-3 bg-sn-bg-panel border-r border-sn-line">
      <div
        className="text-sn-accent text-sm font-bold mb-2"
        style={{ fontFamily: 'var(--font-display)', letterSpacing: '0.04em' }}
        aria-hidden="true"
      >
        SN
      </div>

      <nav aria-label="primary navigation" className="flex flex-col items-center gap-1 w-full">
        <RailTab to="/console/home" label="HO" title="Home" />
        <RailTab to="/console/time" label="CLK" title="Clock" />
        {hasForemanRole() && <RailTab to="/console" label="MAP" title="Map" end />}
        {hasForemanRole() && <RailTab to="/console/jobs" label="JOB" title="Jobs" />}
        {hasForemanRole() && <RailTab to="/console/clients" label="CLI" title="Clients" />}
        {hasForemanRole() && <RailTab to="/console/invoices" label="INV" title="Invoices" />}
        {hasForemanRole() && <RailTab to="/console/crew" label="CRW" title="Crew" />}
        <RailTab to="/console/comm" label="COM" title="Comm" />
        {user.role === 'admin' && <RailTab to="/console/admin" label="ADM" title="Admin" />}
      </nav>

      <div className="flex-1" />

      <NavLink
        to="/console/settings"
        title="Settings"
        aria-label="Settings"
        className={({ isActive }) =>
          cn(
            'flex items-center justify-center h-9 w-9 mx-auto rounded-md transition-colors ' +
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sn-accent focus-visible:ring-offset-1',
            isActive ? 'text-sn-accent' : 'text-sn-ink-muted hover:text-sn-accent'
          )
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

      <div className="my-1" title={user.displayName}>
        <Avatar
          name={user.displayName}
          color={accentForId(user.id)}
          size={26}
          statusColor="var(--sn-status-online)"
        />
      </div>

      <button
        type="button"
        onClick={onLogout}
        aria-label="Log out"
        title="Log out"
        className={cn(
          'flex items-center justify-center h-8 w-8 mx-auto rounded-md text-sn-ink-muted font-data ' +
            'hover:text-sn-accent transition-colors',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sn-accent focus-visible:ring-offset-1'
        )}
      >
        [&gt;]
      </button>
    </div>
  );
}
