import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ConsoleShell } from '../ConsoleShell';
import { useAuthStore } from '../auth/authStore';

describe('ConsoleShell', () => {
  beforeEach(() => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'f@x.com', displayName: 'Foreman F', role: 'foreman', emailVerified: true,
    });
  });

  it('renders the user display name and role badge', () => {
    render(<MemoryRouter><ConsoleShell><div>child</div></ConsoleShell></MemoryRouter>);
    expect(screen.getByText('Foreman F')).toBeInTheDocument();
    expect(screen.getAllByText(/foreman/i).length).toBeGreaterThan(0);
  });

  it('renders children in the main pane', () => {
    render(<MemoryRouter><ConsoleShell><div>child content</div></ConsoleShell></MemoryRouter>);
    expect(screen.getByText('child content')).toBeInTheDocument();
  });

  it('logout button clears the authStore', async () => {
    render(<MemoryRouter><ConsoleShell><div>x</div></ConsoleShell></MemoryRouter>);
    await userEvent.click(screen.getByRole('button', { name: /log out/i }));
    await waitFor(() => expect(useAuthStore.getState().user).toBeNull());
  });

  it('renders the BottomTabBar (mobile nav) when authenticated', () => {
    render(<MemoryRouter><ConsoleShell><div>x</div></ConsoleShell></MemoryRouter>);
    expect(screen.getByRole('navigation', { name: /primary navigation/i })).toBeInTheDocument();
  });
});
