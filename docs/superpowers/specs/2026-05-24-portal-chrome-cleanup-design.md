# Portal Chrome Cleanup -- Design

> Mobile nav declutter + dedicated clock container + Admin behind the gear.
> Status: design approved 2026-05-24.

**Goal:** Make the portal's mobile chrome match the APK's dashboard-hub model:
cut the 7-item bottom nav to 3 role-gated tabs (Home / Jobs / Comm), give the
clock its own container, and move Admin off both navs to an admin-only entry
behind the gear. Portal-only; no backend, no access-rule changes.

---

## 1. Context

The portal's `BottomTabBar` carries 7 destinations (Home, Map, Jobs, Invoices,
Crew, Comm, Admin) -- too many for a phone. The APK does not use a 7-tab bar at
all: it navigates from a **dashboard hub** (`DashboardScreen` with module cards),
which the portal already mirrors via the adaptive home (`AdaptiveHomeRoute` /
`AdaptiveDashboard`, whose cards already expose `[open]` links to Map / Jobs /
Invoices / Crew / Comm). So the fix is to lean on Home as the launcher and trim
the bar.

Two smaller APK-parity gaps come with it: the clock currently shares one thin bar
with the share-location toggle (the APK gives time-tracking its own module), and
Admin is a prominent tab (it should be tucked away, admin-only).

Constraints: no emoji; light/console aesthetic; this slice changes **chrome only**
-- it preserves every existing access rule (tier/role gating, `RequireAdmin`,
`RequireForemanTier`) and touches no backend.

---

## 2. Scope

### In scope (portal-only)
- `BottomTabBar`: 3 role-gated tabs (Home / Jobs / Comm); drop Map, Invoices,
  Crew, Admin from the bar.
- `AppHeader`: remove the Admin inline nav link (desktop keeps its other items).
- `ConsoleShell`: the clock in its own dedicated container, separate from
  share-location.
- `SettingsRoute`: an admin-only "Admin console" row (gear -> Settings -> Admin)
  so admins still reach `/console/admin` on mobile.
- Tests updated for the trimmed nav + the admin-only Settings row.

### Out of scope
- Work modes (#2) -- deferred to a focused follow-up (APK has 5 modes incl.
  `GENERAL_CONTRACTOR` which is not in the backend role list; needs backend
  whitelist + role-adaptive UI work).
- SmithNet AI (#5) -- separate scoped project (Phase-5, on-device, tier-gated).
- Any change to tier/role access gating (solo's thin portal access is pre-existing
  and addressed by the work-modes/tier follow-up).
- The desktop `AppHeader` fuller inline nav (kept; space allows) apart from
  removing Admin.
- No backend changes.

---

## 3. Bottom nav (mobile)

`BottomTabBar` renders, preserving the current gates:
- `[Home]` -> `/console/home` (always).
- `[Jobs]` -> `/console/jobs` (only when `hasForemanTier()`, unchanged gate).
- `[Comm]` -> `/console/comm` (always).

Removed from the bar: Map (`/console`), Invoices, Crew, Admin. These remain
reachable from the Home dashboard cards (Map / Invoices / Crew via their existing
`[open]` links) and -- for Admin -- the gear (see §5).

Resulting sets: solo -> `Home, Comm`; foreman -> `Home, Jobs, Comm`; admin -> the
same as their role (Admin is not a tab). This is intentionally minimal; the Home
hub is the launcher for everything else.

---

## 4. Clock container

In `ConsoleShell`, the current single sub-header bar (`ClockButton` +
`ShareLocationToggle` together) becomes the clock in its own bordered container,
with share-location as a separate element on the row:

```
+-- shift ------------------------------+    ((+)) share location
| 14:32:08    (dot) ON CLOCK . clock out |
+---------------------------------------+
```

Layout only -- reuses the existing `ClockButton`, `useCurrentTime`, and
`ShareLocationToggle`. The container uses the console card idiom
(`bg-console-surface`, `border border-console-border`, rounded). Still gated by
`user` (only shown when logged in), as today.

---

## 5. Admin relocation

- Remove the Admin entry from `BottomTabBar` and the `AppHeader` inline nav.
- Add an admin-only row to `SettingsRoute` -- an "Admin console" `Row` (NavLink to
  `/console/admin`) rendered only when `user.role === 'admin'`, in the console
  idiom (matches the existing Settings `Row` pattern, no emoji, `[>]`-style affordance).
- `/console/admin` keeps its `RequireAdmin` guard. Who is admin is unchanged
  (role-based); the user being the sole admin account is what restricts it to
  them. (A hard single-identity admin gate is a separate auth change, not in
  scope.)

---

## 6. Components / files

- `desktop/portal/src/console/layouts/BottomTabBar.tsx` (modify) -- 3 tabs.
- `desktop/portal/src/console/layouts/AppHeader.tsx` (modify) -- drop the Admin
  inline `NavButton`.
- `desktop/portal/src/console/ConsoleShell.tsx` (modify) -- clock container.
- `desktop/portal/src/console/routes/SettingsRoute.tsx` (modify) -- admin-only
  "Admin console" row.

---

## 7. Testing / acceptance criteria

- **BottomTabBar:** renders exactly `Home` + `Comm` for a non-foreman user and
  `Home` + `Jobs` + `Comm` for a foreman; renders **no** Admin / Map / Invoices /
  Crew tab for any role (incl. admin).
- **Settings admin row:** the "Admin console" row appears only when
  `user.role === 'admin'`; absent otherwise; links to `/console/admin`.
- **ConsoleShell:** still renders the clock (now in its own container) when a user
  is present; existing `ConsoleShell.test.tsx` stays green.
- **Regression:** update any existing nav test that asserted the old 7-item bar;
  full `npm run test:run` green; `npx tsc --noEmit` clean.

---

## 8. Open questions

None. Nav model (3-tab dashboard-hub), clock-container placement (ConsoleShell
sub-header), and Admin relocation (Settings, admin-only, `RequireAdmin` kept) are
decided above. Work-modes and AI are explicitly deferred.
