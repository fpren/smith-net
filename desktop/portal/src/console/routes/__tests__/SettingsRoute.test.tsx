import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { SettingsRoute } from '../SettingsRoute';
import { useAuthStore } from '../../auth/authStore';
import { useThemeStore } from '../../stores/themeStore';

const seed = (role: 'admin' | 'foreman' | 'enterprise') =>
  useAuthStore.getState().setUser({
    id: 'u1', email: 'x@y.com', displayName: 'X', role, emailVerified: true,
  });

describe('SettingsRoute admin entry', () => {
  beforeEach(() => useAuthStore.getState().clear());

  it('shows the Admin console row for an admin', () => {
    seed('admin');
    render(<MemoryRouter><SettingsRoute /></MemoryRouter>);
    expect(screen.getByText(/admin console/i)).toBeInTheDocument();
  });

  it('hides the Admin console row for a non-admin', () => {
    seed('foreman');
    render(<MemoryRouter><SettingsRoute /></MemoryRouter>);
    expect(screen.queryByText(/admin console/i)).not.toBeInTheDocument();
  });

  it('hides the Admin console row for an enterprise user (elevated but not admin)', () => {
    seed('enterprise');
    render(<MemoryRouter><SettingsRoute /></MemoryRouter>);
    expect(screen.queryByText(/admin console/i)).not.toBeInTheDocument();
  });
});

describe('SettingsRoute Appearance section', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
    seed('foreman');
    document.documentElement.removeAttribute('data-theme');
    useThemeStore.setState({ theme: 'system' });
  });

  it('renders an Appearance section header with LIGHT / DARK / SYSTEM options', () => {
    render(<MemoryRouter><SettingsRoute /></MemoryRouter>);
    expect(screen.getByText(/appearance/i)).toBeInTheDocument();
    expect(screen.getByText(/^light$/i)).toBeInTheDocument();
    expect(screen.getByText(/^dark$/i)).toBeInTheDocument();
    expect(screen.getByText(/^system$/i)).toBeInTheDocument();
  });

  it('clicking DARK calls setTheme and stamps data-theme="dark" on documentElement', () => {
    render(<MemoryRouter><SettingsRoute /></MemoryRouter>);
    fireEvent.click(screen.getByText(/^dark$/i));
    expect(useThemeStore.getState().theme).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('clicking SYSTEM removes the data-theme attribute', () => {
    render(<MemoryRouter><SettingsRoute /></MemoryRouter>);
    fireEvent.click(screen.getByText(/^dark$/i));
    fireEvent.click(screen.getByText(/^system$/i));
    expect(useThemeStore.getState().theme).toBe('system');
    expect(document.documentElement.getAttribute('data-theme')).toBeNull();
  });

  it('marks the selected option with the accent-fill classes and others as ghost', () => {
    render(<MemoryRouter><SettingsRoute /></MemoryRouter>);
    const darkBtn = screen.getByText(/^dark$/i);
    fireEvent.click(darkBtn);
    expect(darkBtn.className).toContain('bg-sn-accent');
    expect(darkBtn.className).toContain('text-sn-ink-on-accent');
    const lightBtn = screen.getByText(/^light$/i);
    expect(lightBtn.className).not.toContain('bg-sn-accent');
  });
});
