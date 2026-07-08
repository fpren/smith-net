import { describe, it, expect, beforeEach } from 'vitest';
import { useThemeStore, initTheme } from '../themeStore';

describe('themeStore', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    useThemeStore.setState({ theme: 'system' });
  });

  it('defaults to system (no data-theme attribute)', () => {
    initTheme();
    expect(document.documentElement.getAttribute('data-theme')).toBeNull();
  });

  it('setTheme(dark) stamps the attribute and persists', () => {
    useThemeStore.getState().setTheme('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem('sn-theme')).toBe('dark');
  });

  it('setTheme(system) removes the attribute', () => {
    useThemeStore.getState().setTheme('dark');
    useThemeStore.getState().setTheme('system');
    expect(document.documentElement.getAttribute('data-theme')).toBeNull();
  });

  it('initTheme applies a persisted choice', () => {
    localStorage.setItem('sn-theme', 'light');
    initTheme();
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(useThemeStore.getState().theme).toBe('light');
  });
});
