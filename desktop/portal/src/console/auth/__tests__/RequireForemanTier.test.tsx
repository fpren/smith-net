import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { RequireForemanTier } from '../RequireForemanTier';
import { useAuthStore } from '../authStore';

function Protected() {
  return <div>foreman-only content</div>;
}
function MapStub() {
  return <div>map (redirected)</div>;
}

function renderWithRouter(initial: string) {
  return render(
    <MemoryRouter initialEntries={[initial]}>
      <Routes>
        <Route path="/console" element={<MapStub />} />
        <Route
          path="/console/crew"
          element={
            <RequireForemanTier>
              <Protected />
            </RequireForemanTier>
          }
        />
      </Routes>
    </MemoryRouter>
  );
}

describe('RequireForemanTier', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it('renders children for a foreman', () => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'f@x.com', displayName: 'F', role: 'foreman', emailVerified: true,
    });
    renderWithRouter('/console/crew');
    expect(screen.getByText('foreman-only content')).toBeInTheDocument();
  });

  it('renders children for enterprise and admin', () => {
    useAuthStore.getState().setUser({
      id: 'u-ent', email: 'e@x.com', displayName: 'E', role: 'enterprise', emailVerified: true,
    });
    renderWithRouter('/console/crew');
    expect(screen.getByText('foreman-only content')).toBeInTheDocument();
  });

  it('redirects a worker (solo) back to /console', () => {
    useAuthStore.getState().setUser({
      id: 'u-solo', email: 's@x.com', displayName: 'S', role: 'solo', emailVerified: true,
    });
    renderWithRouter('/console/crew');
    expect(screen.queryByText('foreman-only content')).not.toBeInTheDocument();
    expect(screen.getByText('map (redirected)')).toBeInTheDocument();
  });

  it('redirects team_member and team_lead too', () => {
    useAuthStore.getState().setUser({
      id: 'u-team', email: 't@x.com', displayName: 'T', role: 'team', emailVerified: true,
    });
    renderWithRouter('/console/crew');
    expect(screen.queryByText('foreman-only content')).not.toBeInTheDocument();

    useAuthStore.getState().setUser({
      id: 'u-lead', email: 'l@x.com', displayName: 'L', role: 'lead', emailVerified: true,
    });
    renderWithRouter('/console/crew');
    expect(screen.queryByText('foreman-only content')).not.toBeInTheDocument();
  });
});
