import { describe, it, expect, beforeEach } from 'vitest';
import { useAuthStore, type ConsoleUser } from '../authStore';

const fakeUser: ConsoleUser = {
  id: 'u1',
  email: 'f@example.com',
  displayName: 'Foreman',
  role: 'foreman',
  emailVerified: true,
};

describe('authStore', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it('starts with no user and isAuthenticated=false', () => {
    const s = useAuthStore.getState();
    expect(s.user).toBeNull();
    expect(s.isAuthenticated()).toBe(false);
  });

  it('setUser puts the user in state and isAuthenticated becomes true', () => {
    useAuthStore.getState().setUser(fakeUser);
    const s = useAuthStore.getState();
    expect(s.user).toEqual(fakeUser);
    expect(s.isAuthenticated()).toBe(true);
  });

  it('clear removes the user', () => {
    useAuthStore.getState().setUser(fakeUser);
    useAuthStore.getState().clear();
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('hasConsoleAccess returns true for foreman, enterprise, admin', () => {
    const set = (role: ConsoleUser['role']) =>
      useAuthStore.getState().setUser({ ...fakeUser, role });
    set('foreman');
    expect(useAuthStore.getState().hasConsoleAccess()).toBe(true);
    set('enterprise');
    expect(useAuthStore.getState().hasConsoleAccess()).toBe(true);
    set('admin');
    expect(useAuthStore.getState().hasConsoleAccess()).toBe(true);
  });

  it('hasConsoleAccess returns false for solo, team, lead', () => {
    const set = (role: ConsoleUser['role']) =>
      useAuthStore.getState().setUser({ ...fakeUser, role });
    set('solo');
    expect(useAuthStore.getState().hasConsoleAccess()).toBe(false);
    set('team');
    expect(useAuthStore.getState().hasConsoleAccess()).toBe(false);
    set('lead');
    expect(useAuthStore.getState().hasConsoleAccess()).toBe(false);
  });
});
