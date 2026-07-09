import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { BottomTabBar } from '../BottomTabBar';
import { useAuthStore } from '../../auth/authStore';

describe('BottomTabBar', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it('renders nothing when no user is authenticated', () => {
    const { container } = render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders Home/Map/Jobs/Comm for a foreman -- no Invoices/Crew/Admin (Map is in Android foreman tabs)', () => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'f@x.com', displayName: 'F', role: 'foreman', emailVerified: true,
    });
    render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /Home/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Jobs/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Comm/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Map/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Invoices/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Crew/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Admin/ })).not.toBeInTheDocument();
  });

  it('renders Home/Comm only for a solo user (Jobs is foreman-gated)', () => {
    useAuthStore.getState().setUser({
      id: 'u-solo', email: 's@x.com', displayName: 'S', role: 'solo', emailVerified: true,
    });
    render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /Home/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Comm/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Jobs/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Map/ })).not.toBeInTheDocument();
  });

  it('always carries a Settings tab -- the rail (and its gear/logout) is hidden below lg', () => {
    useAuthStore.getState().setUser({
      id: 'u-solo2', email: 's2@x.com', displayName: 'S2', role: 'solo', emailVerified: true,
    });
    render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /Set/ })).toHaveAttribute('href', '/console/settings');
  });

  it('shows no Admin tab even for an admin (admin lives behind the gear)', () => {
    useAuthStore.getState().setUser({
      id: 'a1', email: 'a@x.com', displayName: 'A', role: 'admin', emailVerified: true,
    });
    render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    expect(screen.queryByRole('link', { name: /Admin/ })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Jobs/ })).toBeInTheDocument(); // admin has foreman tier
    expect(screen.getByRole('link', { name: /Comm/ })).toBeInTheDocument();
  });

  it('uses lg:hidden so the bar is hidden once SmithRail takes over at lg+', () => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'f@x.com', displayName: 'F', role: 'foreman', emailVerified: true,
    });
    render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    const nav = screen.getByRole('navigation', { name: /mobile navigation/i });
    expect(nav.className).toMatch(/lg:hidden/);
  });
});
