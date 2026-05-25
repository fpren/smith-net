import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { SettingsRoute } from '../SettingsRoute';
import { useAuthStore } from '../../auth/authStore';

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
