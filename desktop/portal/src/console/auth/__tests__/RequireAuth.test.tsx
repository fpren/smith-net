import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { RequireAuth } from '../RequireAuth';
import { useAuthStore } from '../authStore';

function Protected() {
  return <div>protected content</div>;
}

function LoginStub() {
  return <div>login page</div>;
}

function renderWithRouter(initial: string) {
  return render(
    <MemoryRouter initialEntries={[initial]}>
      <Routes>
        <Route path="/console/login" element={<LoginStub />} />
        <Route
          path="/console/*"
          element={
            <RequireAuth>
              <Protected />
            </RequireAuth>
          }
        />
      </Routes>
    </MemoryRouter>
  );
}

describe('RequireAuth', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it('shows children when user has console access', async () => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'f@x.com', displayName: 'F', role: 'foreman', emailVerified: true,
    });
    renderWithRouter('/console');
    await waitFor(() => expect(screen.getByText('protected content')).toBeInTheDocument());
  });

  it('shows children for a worker (solo) role — the upgrade-required wall is gone', async () => {
    useAuthStore.getState().setUser({
      id: 'u2', email: 's@x.com', displayName: 'S', role: 'solo', emailVerified: true,
    });
    renderWithRouter('/console');
    await waitFor(() => expect(screen.getByText('protected content')).toBeInTheDocument());
  });

  it('hydrates from /api/auth/me on mount when authStore is empty (cookie path)', async () => {
    // authStore is empty; MSW handler for /api/auth/me returns the test foreman user.
    renderWithRouter('/console');
    await waitFor(() => expect(screen.getByText('protected content')).toBeInTheDocument());
  });
});
