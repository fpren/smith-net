import { ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { Badge } from './components/ui/Badge';
import { Button } from './components/ui/Button';
import { ShareLocationToggle } from './components/header/ShareLocationToggle';
import { authClient } from './auth/authClient';
import { useAuthStore } from './auth/authStore';

interface Props {
  children: ReactNode;
}

export function ConsoleShell({ children }: Props) {
  const user = useAuthStore((s) => s.user);
  const clear = useAuthStore((s) => s.clear);
  const navigate = useNavigate();

  async function onLogout() {
    await authClient.logout();
    clear();
    navigate('/console/login');
  }

  return (
    <div className="h-screen flex flex-col font-mono">
      <header className="border-b border-console-border bg-console-surface flex items-center justify-between px-6 py-3">
        <div className="flex items-center gap-3">
          <span className="text-console-accent text-sm tracking-wide">SMITH NET / CONSOLE</span>
        </div>
        <div className="flex items-center gap-3 text-sm">
          {user && <ShareLocationToggle />}
          {user && (
            <>
              <span className="text-console-text">{user.displayName}</span>
              <Badge tone="ok">{user.role}</Badge>
              <Button variant="secondary" onClick={onLogout}>Log out</Button>
            </>
          )}
        </div>
      </header>
      <div className="flex flex-1 min-h-0">
        <nav className="w-48 border-r border-console-border bg-console-surface p-4 text-sm text-console-text-muted overflow-y-auto">
          <div className="uppercase tracking-wide text-xs mb-2">Nav</div>
          <NavLink
            to="/console"
            end
            className={({ isActive }) =>
              `block px-2 py-1 ${isActive ? 'text-console-accent' : 'text-console-text hover:text-console-accent'}`
            }
          >
            Map
          </NavLink>
          <NavLink
            to="/console/jobs"
            className={({ isActive }) =>
              `block px-2 py-1 ${isActive ? 'text-console-accent' : 'text-console-text hover:text-console-accent'}`
            }
          >
            Jobs
          </NavLink>
          <NavLink
            to="/console/crew"
            className={({ isActive }) =>
              `block px-2 py-1 ${isActive ? 'text-console-accent' : 'text-console-text hover:text-console-accent'}`
            }
          >
            Crew
          </NavLink>
        </nav>
        <main className="flex-1 min-h-0 overflow-y-auto p-6">{children}</main>
      </div>
    </div>
  );
}
