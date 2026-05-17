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

  it('renders 4 tabs (Map/Jobs/Crew/Comm) for a non-admin user', () => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'f@x.com', displayName: 'F', role: 'foreman', emailVerified: true,
    });
    render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /Map/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Jobs/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Crew/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Comm/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Admin/ })).not.toBeInTheDocument();
  });

  it('includes an Admin tab when the user is admin', () => {
    useAuthStore.getState().setUser({
      id: 'a1', email: 'a@x.com', displayName: 'A', role: 'admin', emailVerified: true,
    });
    render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /Admin/ })).toBeInTheDocument();
  });

  it('hides the Crew tab for a worker (solo) role', () => {
    useAuthStore.getState().setUser({
      id: 'u-solo', email: 's@x.com', displayName: 'S', role: 'solo', emailVerified: true,
    });
    render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /Map/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Jobs/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Comm/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Crew/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Admin/ })).not.toBeInTheDocument();
  });

  it('hides the Crew tab for team_member and team_lead too', () => {
    useAuthStore.getState().setUser({
      id: 'u-team', email: 't@x.com', displayName: 'T', role: 'team', emailVerified: true,
    });
    render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    expect(screen.queryByRole('link', { name: /Crew/ })).not.toBeInTheDocument();
  });

  it('uses md:hidden so the bar is hidden on desktop', () => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'f@x.com', displayName: 'F', role: 'foreman', emailVerified: true,
    });
    render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    const nav = screen.getByRole('navigation', { name: /primary navigation/i });
    expect(nav.className).toMatch(/md:hidden/);
  });
});
