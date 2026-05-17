import { create } from 'zustand';

export type ConsoleRole = 'solo' | 'team' | 'lead' | 'foreman' | 'enterprise' | 'admin';

export interface ConsoleUser {
  id: string;
  email: string;
  displayName: string;
  role: ConsoleRole;
  emailVerified: boolean;
}

interface AuthState {
  user: ConsoleUser | null;
  setUser: (u: ConsoleUser) => void;
  clear: () => void;
  isAuthenticated: () => boolean;
  hasConsoleAccess: () => boolean;
  hasForemanTier: () => boolean;
}

// Foreman-tier: can manage crews + see Crew roster + manage org. Other
// authenticated roles (solo, team, lead) get the worker subset (Map, Jobs,
// Comm) via per-route guards. The full upgrade-required wall is gone.
const FOREMAN_TIER_ROLES: ConsoleRole[] = ['foreman', 'enterprise', 'admin'];

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  setUser: (u) => set({ user: u }),
  clear: () => set({ user: null }),
  isAuthenticated: () => get().user !== null,
  // Everyone authenticated can use the console. Per-route gates handle the
  // foreman-only (RequireForemanTier) and admin-only (RequireAdmin) surfaces.
  hasConsoleAccess: () => get().user !== null,
  hasForemanTier: () => {
    const user = get().user;
    return user !== null && FOREMAN_TIER_ROLES.includes(user.role);
  },
}));
