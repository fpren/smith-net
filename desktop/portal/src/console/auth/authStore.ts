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
}

const CONSOLE_ROLES: ConsoleRole[] = ['foreman', 'enterprise', 'admin'];

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  setUser: (u) => set({ user: u }),
  clear: () => set({ user: null }),
  isAuthenticated: () => get().user !== null,
  hasConsoleAccess: () => {
    const user = get().user;
    return user !== null && CONSOLE_ROLES.includes(user.role);
  },
}));
