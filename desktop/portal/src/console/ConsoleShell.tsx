import { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge } from './components/ui/Badge';
import { Button } from './components/ui/Button';
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
    <div className="min-h-screen flex flex-col font-mono">
      <header className="border-b border-console-border bg-console-surface flex items-center justify-between px-6 py-3">
        <div className="flex items-center gap-3">
          <span className="text-console-accent text-sm tracking-wide">SMITH NET / CONSOLE</span>
        </div>
        <div className="flex items-center gap-3 text-sm">
          {user && (
            <>
              <span className="text-console-text">{user.displayName}</span>
              <Badge tone="ok">{user.role}</Badge>
              <Button variant="secondary" onClick={onLogout}>Log out</Button>
            </>
          )}
        </div>
      </header>
      <div className="flex flex-1">
        <nav className="w-48 border-r border-console-border bg-console-surface p-4 text-sm text-console-text-muted">
          <div className="uppercase tracking-wide text-xs mb-2">Nav</div>
          <div className="text-console-text-muted/60">{'(routes coming soon)'}</div>
        </nav>
        <main className="flex-1 p-6">{children}</main>
      </div>
    </div>
  );
}
