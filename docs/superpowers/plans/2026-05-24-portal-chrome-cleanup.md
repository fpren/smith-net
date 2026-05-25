# Portal Chrome Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trim the mobile bottom nav from 7 to 3 role-gated tabs (Home/Jobs/Comm), give the clock its own container, and move Admin off both navs to an admin-only entry behind the gear.

**Architecture:** Pure portal chrome change. `BottomTabBar` keeps Home/Jobs/Comm (preserving the existing `hasForemanTier()` gate on Jobs); `AppHeader` drops the Admin inline link; `ConsoleShell`'s sub-header puts the clock in its own labeled container; `SettingsRoute` gains an admin-only "Admin console" row to `/console/admin` (which keeps its `RequireAdmin` guard). No access rules, no backend, change.

**Tech Stack:** React 18 + TS + zustand + Vitest (jsdom) + React Testing Library.

**Spec:** `docs/superpowers/specs/2026-05-24-portal-chrome-cleanup-design.md`

---

## File structure (locked)

| File | Change |
|---|---|
| `desktop/portal/src/console/layouts/BottomTabBar.tsx` | 3 tabs (Home/Jobs/Comm); drop Map/Invoices/Crew/Admin. |
| `desktop/portal/src/console/layouts/__tests__/BottomTabBar.test.tsx` | Rewrite assertions for the trimmed set. |
| `desktop/portal/src/console/layouts/AppHeader.tsx` | Remove the Admin inline nav link. |
| `desktop/portal/src/console/routes/SettingsRoute.tsx` | Add an admin-only "Admin console" row. |
| `desktop/portal/src/console/routes/__tests__/SettingsRoute.test.tsx` | New: admin row shows only for `role==='admin'`. |
| `desktop/portal/src/console/ConsoleShell.tsx` | Clock in its own container. |
| `desktop/portal/src/console/__tests__/ConsoleShell.test.tsx` | Add an assertion for the clock container. |

**Conventions:** commands from `desktop/portal/`. Stage ONLY the files each task names -- never `git add -A`/`.`. Commit trailer `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`. No emoji.

---

### Task 1: Trim the mobile bottom nav to Home/Jobs/Comm

**Files:**
- Modify: `desktop/portal/src/console/layouts/BottomTabBar.tsx`
- Test: `desktop/portal/src/console/layouts/__tests__/BottomTabBar.test.tsx`

- [ ] **Step 1: Rewrite the test for the new set**

Replace the body of `desktop/portal/src/console/layouts/__tests__/BottomTabBar.test.tsx` with:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { BottomTabBar } from '../BottomTabBar';
import { useAuthStore } from '../../auth/authStore';

describe('BottomTabBar', () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  it('renders nothing when no user is authenticated', () => {
    const { container } = render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders Home/Jobs/Comm for a foreman -- no Map/Invoices/Crew/Admin', () => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'f@x.com', displayName: 'F', role: 'foreman', emailVerified: true,
    });
    render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /Home/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Jobs/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Comm/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Map/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Invoices/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Crew/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Admin/ })).not.toBeInTheDocument();
  });

  it('renders Home/Comm only for a solo user (Jobs is foreman-gated)', () => {
    useAuthStore.getState().setUser({
      id: 'u-solo', email: 's@x.com', displayName: 'S', role: 'solo', emailVerified: true,
    });
    render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /Home/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Comm/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Jobs/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Map/ })).not.toBeInTheDocument();
  });

  it('shows no Admin tab even for an admin (admin lives behind the gear)', () => {
    useAuthStore.getState().setUser({
      id: 'a1', email: 'a@x.com', displayName: 'A', role: 'admin', emailVerified: true,
    });
    render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    expect(screen.queryByRole('link', { name: /Admin/ })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Jobs/ })).toBeInTheDocument(); // admin has foreman tier
    expect(screen.getByRole('link', { name: /Comm/ })).toBeInTheDocument();
  });

  it('uses md:hidden so the bar is hidden on desktop', () => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'f@x.com', displayName: 'F', role: 'foreman', emailVerified: true,
    });
    render(<MemoryRouter><BottomTabBar /></MemoryRouter>);
    const nav = screen.getByRole('navigation', { name: /primary navigation/i });
    expect(nav.className).toMatch(/md:hidden/);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- BottomTabBar`
Expected: FAIL -- the foreman case expects Map/Crew ABSENT, but the current bar still renders them.

- [ ] **Step 3: Trim the bar**

In `desktop/portal/src/console/layouts/BottomTabBar.tsx`, replace the `<nav>...</nav>` body (the list of `TabLink`s) so it reads exactly:

```tsx
    <nav
      className="md:hidden fixed inset-x-0 bottom-0 z-10 flex items-stretch bg-console-surface border-t border-console-border h-14"
      aria-label="Primary navigation"
    >
      <TabLink to="/console/home" label="Home" />
      {hasForemanTier() && <TabLink to="/console/jobs" label="Jobs" />}
      <TabLink to="/console/comm" label="Comm" />
    </nav>
```

Leave the `TabLink` component, imports, and the `if (!user) return null;` guard unchanged. (The removed `user.role === 'admin'` branch was the only other use of nothing else -- `user` is still used by the guard, `hasForemanTier` by the Jobs gate.)

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- BottomTabBar`
Expected: PASS (5 tests). Then `npx tsc --noEmit` -> 0 errors.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/layouts/BottomTabBar.tsx desktop/portal/src/console/layouts/__tests__/BottomTabBar.test.tsx
git commit -m "$(cat <<'EOF'
feat(portal): trim mobile bottom nav to Home/Jobs/Comm (dashboard-hub model)

Map/Invoices/Crew reachable via the Home dashboard cards; Admin removed
from the bar (moves behind the gear). Preserves the foreman-tier gate on
Jobs.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Move Admin behind the gear (header link out, Settings row in)

**Files:**
- Modify: `desktop/portal/src/console/layouts/AppHeader.tsx`
- Modify: `desktop/portal/src/console/routes/SettingsRoute.tsx`
- Test: `desktop/portal/src/console/routes/__tests__/SettingsRoute.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `desktop/portal/src/console/routes/__tests__/SettingsRoute.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { SettingsRoute } from '../SettingsRoute';
import { useAuthStore } from '../../auth/authStore';

const seed = (role: 'admin' | 'foreman') =>
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
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- SettingsRoute`
Expected: FAIL -- the admin case finds no "Admin console" text (row not added yet).

- [ ] **Step 3: Add the admin-only row + remove the header link**

In `desktop/portal/src/console/routes/SettingsRoute.tsx`, insert this block immediately before the `{/* ACCOUNT */}` comment (the `navigate` helper is already in scope via `useNavigate`):

```tsx
      {/* ADMIN (admin only -- the advanced console lives behind the gear) */}
      {user.role === 'admin' && (
        <>
          <SectionHeader>Admin</SectionHeader>
          <Row onClick={() => navigate('/console/admin')}>
            <div className="flex items-center justify-between">
              <span className="text-console-text text-sm">Admin console</span>
              <span className="text-console-text-muted">{'>'}</span>
            </div>
          </Row>
        </>
      )}

```

In `desktop/portal/src/console/layouts/AppHeader.tsx`, delete this line from the inline `<nav>`:

```tsx
        {user.role === 'admin' && <NavButton to="/console/admin" label="Admin" />}
```

(After removal `user.role` is still used by the role `Chip`, and `NavButton` by the other nav items -- no unused symbols.)

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- SettingsRoute`
Expected: PASS (2 tests). Then `npx tsc --noEmit` -> 0 errors.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/layouts/AppHeader.tsx desktop/portal/src/console/routes/SettingsRoute.tsx desktop/portal/src/console/routes/__tests__/SettingsRoute.test.tsx
git commit -m "$(cat <<'EOF'
feat(portal): move Admin behind the gear -- admin-only Settings row

Removes the Admin link from both navs; admins reach the (RequireAdmin)
console via Settings. Restricts the advanced surface from prominence.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Give the clock its own container

**Files:**
- Modify: `desktop/portal/src/console/ConsoleShell.tsx`
- Test: `desktop/portal/src/console/__tests__/ConsoleShell.test.tsx`

- [ ] **Step 1: Add the failing test**

Append this test inside the `describe('ConsoleShell', ...)` block in `desktop/portal/src/console/__tests__/ConsoleShell.test.tsx`:

```tsx
  it('renders the shift clock in its own container', () => {
    render(<MemoryRouter><ConsoleShell><div>x</div></ConsoleShell></MemoryRouter>);
    expect(screen.getByRole('group', { name: /shift/i })).toBeInTheDocument();
  });
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- ConsoleShell`
Expected: FAIL -- no element with role `group` named "shift" exists yet.

- [ ] **Step 3: Put the clock in its own container**

In `desktop/portal/src/console/ConsoleShell.tsx`:

(a) Add the time hook import after the existing imports:

```tsx
import { useCurrentTime } from './hooks/useCurrentTime';
```

(b) Inside `ConsoleShell`, after `const user = useAuthStore((s) => s.user);`, add:

```tsx
  const { hh, mm, ss } = useCurrentTime();
```

(c) Replace the existing `{user && ( ... )}` sub-header block with:

```tsx
      {user && (
        <div className="border-b border-console-border bg-console-surface px-4 py-2 flex items-center justify-between gap-3">
          {/* Clock in its own container (APK-style shift module) */}
          <div
            role="group"
            aria-label="shift"
            className="flex items-center gap-3 bg-console-bg border border-console-border rounded-md px-3 py-1.5"
          >
            <span className="text-console-text text-sm tabular-nums" style={{ fontFamily: 'var(--font-mono)' }}>
              {hh}:{mm}
              <span className="text-console-text-muted text-xs">:{ss}</span>
            </span>
            <span className="text-console-border" aria-hidden="true">|</span>
            <ClockButton />
          </div>
          <ShareLocationToggle />
        </div>
      )}
```

(Leave the existing `initSmithCore` effect, `AppHeader`, `main`, and `BottomTabBar` unchanged.)

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- ConsoleShell`
Expected: PASS (existing tests + the new shift-container test).

- [ ] **Step 5: Full verify**

Run: `npm run test:run` then `npx tsc --noEmit` then `npm run build`
Expected: full suite green, tsc 0 errors, build succeeds.

- [ ] **Step 6: Commit**

```bash
git add desktop/portal/src/console/ConsoleShell.tsx desktop/portal/src/console/__tests__/ConsoleShell.test.tsx
git commit -m "$(cat <<'EOF'
feat(portal): give the clock its own container in the console shell

Dedicated shift container (time + clock toggle) separate from the
share-location control, mirroring the APK time-tracking module.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-review (against the spec)

**Spec coverage:**
- 3-tab role-gated bottom nav, drop Map/Invoices/Crew/Admin (spec §3) -> Task 1.
- Clock its own container (spec §4) -> Task 3.
- Admin off both navs + admin-only Settings row, `RequireAdmin` kept (spec §5) -> Task 2.
- Tests: trimmed nav, admin-only row, clock container (spec §7) -> Tasks 1, 2, 3.
- Out of scope (work-modes, AI, access-rule changes, no backend) -> none of the tasks touch them.

**Placeholder scan:** No TBD/TODO; every code step shows complete edits with exact insertion points.

**Type consistency:** `hasForemanTier()` gate reused unchanged on Jobs; `navigate` (from `useNavigate`) reused for the admin row; `useCurrentTime()` returns `{ hh, mm, ss }` (same shape `AppHeader` already uses); role values (`solo`/`foreman`/`admin`) match the `ConsoleRole` union and the existing tests. The new tests assert against accessible names that the existing `[Label]` link text and the new `role="group" aria-label="shift"` provide.

**Risk note:** `BottomTabBar.test.tsx` is rewritten (the old assertions encoded the 7-item bar). The `ConsoleShell.test.tsx` change is additive. `AppHeader` has no test; its one-line removal is covered by `tsc` + the full suite + the unchanged `ConsoleShell` tests.
