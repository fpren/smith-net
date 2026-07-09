import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { SmithRail } from '../SmithRail';
import { useAuthStore } from '../../auth/authStore';

function setUser(role: 'solo' | 'foreman' | 'admin') {
  useAuthStore.getState().setUser({
    id: 'u1',
    email: 'u@x.com',
    displayName: 'Foreman F',
    role,
    emailVerified: true,
  });
}

describe('SmithRail', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it('is hidden below lg and a flex column at lg+', () => {
    setUser('solo');
    render(<MemoryRouter><SmithRail /></MemoryRouter>);
    const nav = screen.getByRole('navigation', { name: /primary navigation/i });
    // The rail's outer container carries the breakpoint toggle.
    const rail = nav.closest('[class*="hidden"]') as HTMLElement;
    expect(rail).toBeTruthy();
    expect(rail.className).toMatch(/hidden/);
    expect(rail.className).toMatch(/lg:flex/);
    expect(rail.className).toMatch(/w-16/);
  });

  it('a worker (non-foreman) sees only HO/CLK/COM', () => {
    setUser('solo');
    render(<MemoryRouter><SmithRail /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /^HO$/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^CLK$/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^COM$/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^MAP$/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^JOB$/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^CLI$/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^INV$/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^CRW$/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^ADM$/ })).not.toBeInTheDocument();
  });

  it('a foreman additionally sees MAP/JOB/CLI/INV/CRW', () => {
    setUser('foreman');
    render(<MemoryRouter><SmithRail /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /^HO$/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^CLK$/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^MAP$/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^JOB$/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^CLI$/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^INV$/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^CRW$/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^COM$/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^ADM$/ })).not.toBeInTheDocument();
  });

  it('an admin additionally sees ADM', () => {
    setUser('admin');
    render(<MemoryRouter><SmithRail /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /^ADM$/ })).toBeInTheDocument();
    // admin still has the foreman tier tabs
    expect(screen.getByRole('link', { name: /^JOB$/ })).toBeInTheDocument();
  });

  it('gives each tab a full-name title attribute', () => {
    setUser('foreman');
    render(<MemoryRouter><SmithRail /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /^HO$/ })).toHaveAttribute('title', 'Home');
    expect(screen.getByRole('link', { name: /^MAP$/ })).toHaveAttribute('title', 'Map');
    expect(screen.getByRole('link', { name: /^CRW$/ })).toHaveAttribute('title', 'Crew');
  });

  it('styles the active tab as an accent pill', () => {
    setUser('foreman');
    render(
      <MemoryRouter initialEntries={['/console/home']}>
        <SmithRail />
      </MemoryRouter>
    );
    const active = screen.getByRole('link', { name: /^HO$/ });
    expect(active.className).toMatch(/bg-sn-accent/);
    expect(active.className).toMatch(/text-sn-ink-on-accent/);

    const inactive = screen.getByRole('link', { name: /^CLK$/ });
    expect(inactive.className).not.toMatch(/bg-sn-accent/);
  });

  it('every nav tab carries a focus-visible ring class', () => {
    setUser('foreman');
    render(<MemoryRouter><SmithRail /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /^HO$/ }).className).toMatch(/focus-visible:ring/);
    expect(screen.getByRole('link', { name: /^COM$/ }).className).toMatch(/focus-visible:ring/);
  });

  it('renders the avatar with the display name as title', () => {
    setUser('foreman');
    render(<MemoryRouter><SmithRail /></MemoryRouter>);
    expect(screen.getByTitle('Foreman F')).toBeInTheDocument();
  });

  it('the nav is labeled "primary navigation"', () => {
    setUser('foreman');
    render(<MemoryRouter><SmithRail /></MemoryRouter>);
    expect(screen.getByRole('navigation', { name: /primary navigation/i })).toBeInTheDocument();
  });

  it('logout clears the authStore', async () => {
    setUser('foreman');
    render(<MemoryRouter><SmithRail /></MemoryRouter>);
    await userEvent.click(screen.getByRole('button', { name: /log out/i }));
    await waitFor(() => expect(useAuthStore.getState().user).toBeNull());
  });

  it('renders nothing when logged out', () => {
    const { container } = render(<MemoryRouter><SmithRail /></MemoryRouter>);
    expect(container).toBeEmptyDOMElement();
  });
});
