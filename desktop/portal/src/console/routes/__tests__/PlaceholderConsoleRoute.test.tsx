import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { PlaceholderConsoleRoute } from '../PlaceholderConsoleRoute';
import { useAuthStore } from '../../auth/authStore';

describe('PlaceholderConsoleRoute', () => {
  beforeEach(() => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'f@x.com', displayName: 'F', role: 'foreman', emailVerified: true,
    });
  });

  it('greets the user by display name', () => {
    render(<PlaceholderConsoleRoute />);
    expect(screen.getByText(/welcome, F/i)).toBeInTheDocument();
  });

  it('shows the user email and role', () => {
    render(<PlaceholderConsoleRoute />);
    expect(screen.getByText(/f@x.com/i)).toBeInTheDocument();
    expect(screen.getByText(/foreman/i)).toBeInTheDocument();
  });
});
