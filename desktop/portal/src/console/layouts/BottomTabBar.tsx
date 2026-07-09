// desktop/portal/src/console/layouts/BottomTabBar.tsx
//
// Mobile-only (`lg:hidden`) fixed bottom-tab nav -- "mobile navigation",
// distinct from SmithRail's "primary navigation" so the two landmarks
// never collide in the accessibility tree (RTL renders both regardless of
// viewport). Carries the same destinations as SmithRail's tabs. Mirrors
// the Android dashboard pattern (`[Home] [Jobs] [Comm]`) so the
// portal navigates like the app on a phone.
import { NavLink } from 'react-router-dom';
import { useAuthStore } from '../auth/authStore';

interface TabLinkProps {
  to: string;
  label: string;
}

function TabLink({ to, label }: TabLinkProps) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        'flex-1 flex items-center justify-center py-2 text-xs font-mono uppercase tracking-wide border-t-2 ' +
        (isActive
          ? 'text-sn-accent border-sn-accent'
          : 'text-sn-ink-muted border-transparent hover:text-sn-accent')
      }
    >
      [{label}]
    </NavLink>
  );
}

export function BottomTabBar() {
  const user = useAuthStore((s) => s.user);
  const hasForemanRole = useAuthStore((s) => s.hasForemanRole);
  if (!user) return null;

  return (
    <nav
      className="lg:hidden fixed inset-x-0 bottom-0 z-10 flex items-stretch bg-sn-bg-panel border-t border-sn-line h-14"
      aria-label="mobile navigation"
    >
      <TabLink to="/console/home" label="Home" />
      <TabLink to="/console/time" label="Clock" />
      {hasForemanRole() && <TabLink to="/console/jobs" label="Jobs" />}
      {hasForemanRole() && <TabLink to="/console/clients" label="Clients" />}
      <TabLink to="/console/comm" label="Comm" />
      {/* Below lg the rail (gear/avatar/logout) is hidden -- Settings is the
          only path to account actions, incl. Sign out, so it must be a tab. */}
      <TabLink to="/console/settings" label="Set" />
    </nav>
  );
}
