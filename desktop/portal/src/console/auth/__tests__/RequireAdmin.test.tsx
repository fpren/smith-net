import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { RequireAdmin } from '../RequireAdmin';
import { useAuthStore } from '../authStore';

function Wrapper({ initialPath }: { initialPath: string }) {
  return (
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/console" element={<div>map page</div>} />
        <Route path="/console/admin" element={<RequireAdmin><div>admin page</div></RequireAdmin>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('RequireAdmin', () => {
  beforeEach(() => { useAuthStore.getState().clear(); });

  it('redirects to /console for non-admin role', () => {
    useAuthStore.getState().setUser({
      id: 'u-1', email: 'f@example.com', displayName: 'F', role: 'foreman', emailVerified: true,
    });
    render(<Wrapper initialPath="/console/admin" />);
    expect(screen.getByText(/map page/)).toBeInTheDocument();
    expect(screen.queryByText(/admin page/)).toBeNull();
  });

  it('allows admin role through', () => {
    useAuthStore.getState().setUser({
      id: 'u-2', email: 'a@example.com', displayName: 'A', role: 'admin', emailVerified: true,
    });
    render(<Wrapper initialPath="/console/admin" />);
    expect(screen.getByText(/admin page/)).toBeInTheDocument();
  });

  it('redirects when there is no user', () => {
    render(<Wrapper initialPath="/console/admin" />);
    expect(screen.getByText(/map page/)).toBeInTheDocument();
  });
});
