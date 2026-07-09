// Theme machinery for Design System v2. Stamps data-theme on <html> so the
// generated --sn-* vars in styles/tokens.css switch palettes. 'system' defers
// to the CSS prefers-color-scheme fallback. Settings' Appearance section
// (SettingsRoute.tsx) is the UI that exposes this.
import { create } from 'zustand';

export type ThemeChoice = 'light' | 'dark' | 'system';
const STORAGE_KEY = 'sn-theme';

function apply(choice: ThemeChoice): void {
  const root = document.documentElement;
  if (choice === 'system') root.removeAttribute('data-theme');
  else root.setAttribute('data-theme', choice);
}

interface ThemeState {
  theme: ThemeChoice;
  setTheme: (t: ThemeChoice) => void;
}

export const useThemeStore = create<ThemeState>((set) => ({
  theme: 'system',
  setTheme: (t) => {
    apply(t);
    try { localStorage.setItem(STORAGE_KEY, t); } catch { /* private mode */ }
    set({ theme: t });
  },
}));

export function initTheme(): void {
  let stored: string | null = null;
  try { stored = localStorage.getItem(STORAGE_KEY); } catch { /* private mode */ }
  const choice: ThemeChoice =
    stored === 'light' || stored === 'dark' ? stored : 'system';
  apply(choice);
  useThemeStore.setState({ theme: choice });
}
