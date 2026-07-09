// desktop/portal/src/console/layouts/TopStrip.tsx
//
// Formerly AppHeader. Slimmed down by the SmithRail restructure: primary
// nav, the gear, the avatar, and the logout button all moved to
// SmithRail (desktop/lg+ only). What's left here is what SmithRail can't
// carry -- the brand mark, the identity/role readout, and the live shift
// clock -- merged with the strip that used to sit in its own bar directly
// below AppHeader in ConsoleShell.
//
// Logged-out state stays brand-only so the login page still renders the
// same top bar it always has.

import { Chip } from '../components/ui/Chip';
import { ShiftClock } from '../components/header/ShiftClock';
import { useAuthStore } from '../auth/authStore';
import { colorForRole } from '../lib/utils';

export function TopStrip() {
  const user = useAuthStore((s) => s.user);

  if (!user) {
    return (
      <header className="border-b border-sn-line bg-sn-bg-panel px-6 py-3 font-mono">
        <span className="text-sn-accent text-sm">SMITH NET / CONSOLE</span>
      </header>
    );
  }

  return (
    <header className="border-b border-sn-line bg-sn-bg-panel px-4 py-2 flex items-center gap-2 sm:gap-4 flex-wrap font-mono">
      {/* Brand -- tagline hides on mobile so the row fits at 390px. */}
      <div className="flex flex-col leading-tight pr-2 flex-shrink-0">
        <span
          className="text-sn-ink text-base font-semibold whitespace-nowrap"
          style={{ fontFamily: 'var(--font-display)', letterSpacing: '0.03em' }}
        >
          smith net
        </span>
        <span className="hidden md:inline text-sn-ink-muted text-[10px] uppercase tracking-widest whitespace-nowrap">
          guild of smiths · console
        </span>
      </div>

      <div className="flex-1" />

      {/* Identity readout -- avatar/gear/logout now live in SmithRail. */}
      <div className="flex items-center gap-2 min-w-0">
        <span className="text-sn-ink text-xs font-medium truncate">{user.displayName}</span>
        <span className="hidden md:inline">
          <Chip label={user.role.toUpperCase()} color={colorForRole(user.role)} xs />
        </span>
      </div>

      <ShiftClock />
    </header>
  );
}
