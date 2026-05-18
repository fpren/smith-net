// desktop/portal/src/console/layouts/BottomTabBar.tsx
//
// Mobile-only (`md:hidden`) fixed bottom-tab nav. Carries the same
// destinations as AppHeader's inline nav (which hides under md). Mirrors
// the Android dashboard pattern (`[Home] [Jobs] [Comm] [Plan]`) so the
// portal navigates like the app on a phone.
import { NavLink } from 'react-router-dom';
import { useAuthStore } from '../auth/authStore';

interface TabLinkProps {
  to: string;
  label: string;
  end?: boolean;
}

function TabLink({ to, label, end }: TabLinkProps) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        'flex-1 flex items-center justify-center py-2 text-xs font-mono uppercase tracking-wide border-t-2 ' +
        (isActive
          ? 'text-console-accent border-console-accent'
          : 'text-console-text-muted border-transparent hover:text-console-accent')
      }
    >
      [{label}]
    </NavLink>
  );
}

export function BottomTabBar() {
  const user = useAuthStore((s) => s.user);
  const hasForemanTier = useAuthStore((s) => s.hasForemanTier);
  if (!user) return null;

  return (
    <nav
      className="md:hidden fixed inset-x-0 bottom-0 z-10 flex items-stretch bg-console-surface border-t border-console-border h-14"
      aria-label="Primary navigation"
    >
      {hasForemanTier() && <TabLink to="/console" label="Map" end />}
      {hasForemanTier() && <TabLink to="/console/jobs" label="Jobs" />}
      {hasForemanTier() && <TabLink to="/console/invoices" label="Invoices" />}
      {hasForemanTier() && <TabLink to="/console/crew" label="Crew" />}
      <TabLink to="/console/comm" label="Comm" />
      {user.role === 'admin' && <TabLink to="/console/admin" label="Admin" />}
    </nav>
  );
}
