import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { LoginForm } from '../LoginForm';
import { useAuthStore } from '../authStore';

describe('LoginForm', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it('renders email + password fields and submit button', () => {
    render(<MemoryRouter><LoginForm /></MemoryRouter>);
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument();
  });

  it('submits with valid credentials and stores user', async () => {
    render(<MemoryRouter><LoginForm /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText(/email/i), 'foreman@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'password123');
    await userEvent.click(screen.getByRole('button', { name: /log in/i }));
    await waitFor(() => {
      expect(useAuthStore.getState().user?.email).toBe('foreman@example.com');
    });
  });

  it('shows error on invalid credentials', async () => {
    render(<MemoryRouter><LoginForm /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText(/email/i), 'foreman@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: /log in/i }));
    await waitFor(() => {
      expect(screen.getByText(/invalid credentials/i)).toBeInTheDocument();
    });
  });
});
