import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { RegisterForm } from '../RegisterForm';
import { useAuthStore } from '../authStore';

describe('RegisterForm', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it('renders name + email + password fields and submit button', () => {
    render(<MemoryRouter><RegisterForm /></MemoryRouter>);
    expect(screen.getByLabelText(/display name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
  });

  it('submits and stores user', async () => {
    render(<MemoryRouter><RegisterForm /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText(/display name/i), 'New User');
    await userEvent.type(screen.getByLabelText(/email/i), 'new@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'password123');
    await userEvent.click(screen.getByRole('button', { name: /create account/i }));
    await waitFor(() => {
      expect(useAuthStore.getState().user?.email).toBe('new@example.com');
    });
  });

  it('blocks submit when password is too short (client check)', async () => {
    render(<MemoryRouter><RegisterForm /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText(/display name/i), 'X');
    await userEvent.type(screen.getByLabelText(/email/i), 'x@x.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'short');
    await userEvent.click(screen.getByRole('button', { name: /create account/i }));
    expect(screen.getByText(/at least 8/i)).toBeInTheDocument();
    expect(useAuthStore.getState().user).toBeNull();
  });
});
