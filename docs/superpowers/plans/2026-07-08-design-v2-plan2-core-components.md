# Design System v2 — Plan 2: Core Components Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the v2 component set on both platforms — theme machinery, SmithDialog/ConfirmDialog (killing all 29 Material AlertDialogs on Android and the unsafe delete flows on web), SmithSheet, and re-skin the portal's shared primitives to `sn-` tokens.

**Architecture:** Web components move from hardcoded `console-*` classes to CSS-var-backed `sn-*` classes (dark-ready); a `themeStore` sets `data-theme` on the root (toggle UI ships in Plan 4). Android gets a `LocalSmithColors` CompositionLocal resolving `Tokens2.Light/Dark` (forced light until Plans 4-5) and non-Material dialog/sheet composables built on `androidx.compose.ui.window.Dialog`. Screen-by-screen visual sweep is Plan 4 — this plan only touches shared components and the dialog/sheet/confirm call sites.

**Tech Stack:** React 18 + Zustand + Tailwind 3.4 + Vitest/RTL/MSW (portal); Jetpack Compose + Tokens2 (Android, JDK 17 for gradle: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-08-design-system-v2-design.md`. Colors ONLY via tokens (`sn-*` classes / `--sn-*` vars on web, `Tokens2` via `LocalSmithColors` on Android). No new raw hex anywhere.
- Accent discipline: cobalt (`sn-accent`) acts, amber (`sn-attention`) warns/attends, `sn-status-error` for destructive/danger. Never swap jobs.
- Destructive confirmations: no outside-tap dismiss, explicit cancel button. Escape = cancel is allowed.
- No Material widgets in new code (no Material3 AlertDialog/ModalBottomSheet/Button/TextField). `androidx.compose.ui.window.Dialog` is NOT Material and is the allowed base.
- No emoji. Glyphs per `design/GLYPHS.md` only.
- Commit style: `type(scope): summary`, body ends `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- v1 `console-*` Tailwind colors and `ConsoleTheme` object stay untouched — screens still consume them until Plan 4.
- Android verification commands need `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`.
- Repo root: `/Users/fegensprenelon/smith-net`. Portal commands from `desktop/portal`; Android from `android`.

---

### Task 1: Web theme machinery

**Files:**
- Create: `desktop/portal/src/console/stores/themeStore.ts`
- Modify: `desktop/portal/src/main.tsx` (call `initTheme()` before render)
- Test: `desktop/portal/src/console/stores/__tests__/themeStore.test.ts`

**Interfaces:**
- Produces: `useThemeStore` with `{ theme: 'light' | 'dark' | 'system'; setTheme(t): void }`; `initTheme(): void` applies persisted choice at boot. Setting theme writes `data-theme="light"|"dark"` on `document.documentElement` (removes the attribute for `'system'`, letting the CSS `prefers-color-scheme` fallback in `tokens.css` rule) and persists to `localStorage['sn-theme']`.
- Note: no visible toggle ships in this plan (the v1 screens are not dark-safe until Plan 4); this is machinery + tests only.

- [ ] **Step 1: Write the failing test**

`desktop/portal/src/console/stores/__tests__/themeStore.test.ts`:

```ts
import { describe, it, expect, beforeEach } from 'vitest';
import { useThemeStore, initTheme } from '../themeStore';

describe('themeStore', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    useThemeStore.setState({ theme: 'system' });
  });

  it('defaults to system (no data-theme attribute)', () => {
    initTheme();
    expect(document.documentElement.getAttribute('data-theme')).toBeNull();
  });

  it('setTheme(dark) stamps the attribute and persists', () => {
    useThemeStore.getState().setTheme('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem('sn-theme')).toBe('dark');
  });

  it('setTheme(system) removes the attribute', () => {
    useThemeStore.getState().setTheme('dark');
    useThemeStore.getState().setTheme('system');
    expect(document.documentElement.getAttribute('data-theme')).toBeNull();
  });

  it('initTheme applies a persisted choice', () => {
    localStorage.setItem('sn-theme', 'light');
    initTheme();
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(useThemeStore.getState().theme).toBe('light');
  });
});
```

- [ ] **Step 2: Run to verify FAIL**

Run: `cd desktop/portal && npx vitest run src/console/stores/__tests__/themeStore.test.ts`
Expected: FAIL — cannot resolve `../themeStore`

- [ ] **Step 3: Implement `themeStore.ts`**

```ts
// Theme machinery for Design System v2. Stamps data-theme on <html> so the
// generated --sn-* vars in styles/tokens.css switch palettes. 'system' defers
// to the CSS prefers-color-scheme fallback. No UI exposes this yet (Plan 4).
import { create } from 'zustand';

export type ThemeChoice = 'light' | 'dark' | 'system';
const STORAGE_KEY = 'sn-theme';

function apply(choice: ThemeChoice): void {
  const root = document.documentElement;
  if (choice === 'system') root.removeAttribute('data-theme');
  else root.setAttribute('data-theme', choice);
}

interface ThemeState {
  theme: ThemeChoice;
  setTheme: (t: ThemeChoice) => void;
}

export const useThemeStore = create<ThemeState>((set) => ({
  theme: 'system',
  setTheme: (t) => {
    apply(t);
    try { localStorage.setItem(STORAGE_KEY, t); } catch { /* private mode */ }
    set({ theme: t });
  },
}));

export function initTheme(): void {
  let stored: string | null = null;
  try { stored = localStorage.getItem(STORAGE_KEY); } catch { /* private mode */ }
  const choice: ThemeChoice =
    stored === 'light' || stored === 'dark' ? stored : 'system';
  apply(choice);
  useThemeStore.setState({ theme: choice });
}
```

- [ ] **Step 4: Wire into boot** — in `desktop/portal/src/main.tsx`, add below the existing imports:

```ts
import { initTheme } from './console/stores/themeStore';

initTheme();
```

(Place the `initTheme()` call before the `createRoot(...)` render call.)

- [ ] **Step 5: Verify PASS + full suite**

Run: `cd desktop/portal && npx vitest run src/console/stores/__tests__/themeStore.test.ts && npx vitest run`
Expected: 4 new tests PASS; full suite green (401+)

- [ ] **Step 6: Commit**

```bash
git add desktop/portal/src/console/stores/themeStore.ts \
  desktop/portal/src/console/stores/__tests__/themeStore.test.ts \
  desktop/portal/src/main.tsx
git commit -m "feat(portal): theme store stamps data-theme for sn token switching

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Web SmithDialog + ConfirmDialog (Modal becomes a wrapper)

**Files:**
- Create: `desktop/portal/src/console/components/ui/SmithDialog.tsx`
- Modify: `desktop/portal/src/console/components/ui/Modal.tsx` (thin re-export wrapper; its 7 form callers keep compiling unchanged)
- Test: `desktop/portal/src/console/components/ui/__tests__/SmithDialog.test.tsx`

**Interfaces:**
- Consumes: `sn-*` Tailwind classes (Task-independent; preset landed in Plan 1).
- Produces:
  - `SmithDialog({ open, onClose, title, children, footer?, destructive? })` — Escape closes (always, it is explicit); backdrop click closes ONLY when `destructive` is false; panel gets `role="dialog"` + `aria-modal`; focus moves into the panel on open and returns on close.
  - `ConfirmDialog({ open, title, body, confirmLabel, cancelLabel?, onConfirm, onCancel })` — always `destructive` behavior; confirm button styled `bg-sn-status-error`; cancel is a ghost button and receives initial focus (safe default).
  - `Modal` (existing name) re-exported as a `SmithDialog` alias with the same v1 prop shape.

- [ ] **Step 1: Write the failing test**

`desktop/portal/src/console/components/ui/__tests__/SmithDialog.test.tsx`:

```tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { SmithDialog, ConfirmDialog } from '../SmithDialog';

describe('SmithDialog', () => {
  it('renders title and children when open', () => {
    render(<SmithDialog open onClose={() => {}} title="Add client">
      <div>form body</div>
    </SmithDialog>);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Add client')).toBeInTheDocument();
    expect(screen.getByText('form body')).toBeInTheDocument();
  });

  it('renders nothing when closed', () => {
    render(<SmithDialog open={false} onClose={() => {}} title="t">x</SmithDialog>);
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('backdrop click closes a non-destructive dialog', () => {
    const onClose = vi.fn();
    render(<SmithDialog open onClose={onClose} title="t">x</SmithDialog>);
    fireEvent.click(screen.getByTestId('sn-dialog-backdrop'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('backdrop click does NOT close a destructive dialog', () => {
    const onClose = vi.fn();
    render(<SmithDialog open onClose={onClose} title="t" destructive>x</SmithDialog>);
    fireEvent.click(screen.getByTestId('sn-dialog-backdrop'));
    expect(onClose).not.toHaveBeenCalled();
  });

  it('Escape closes (explicit cancel), even destructive', () => {
    const onClose = vi.fn();
    render(<SmithDialog open onClose={onClose} title="t" destructive>x</SmithDialog>);
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

describe('ConfirmDialog', () => {
  it('confirm fires onConfirm; cancel fires onCancel; backdrop is inert', () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(<ConfirmDialog open title="Delete job?" body="This cannot be undone."
      confirmLabel="Delete" onConfirm={onConfirm} onCancel={onCancel} />);
    fireEvent.click(screen.getByTestId('sn-dialog-backdrop'));
    expect(onCancel).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('cancel button has initial focus (safe default)', () => {
    render(<ConfirmDialog open title="t" body="b" confirmLabel="Delete"
      onConfirm={() => {}} onCancel={() => {}} />);
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();
  });
});
```

- [ ] **Step 2: Run to verify FAIL**

Run: `cd desktop/portal && npx vitest run src/console/components/ui/__tests__/SmithDialog.test.tsx`
Expected: FAIL — cannot resolve `../SmithDialog`

- [ ] **Step 3: Implement `SmithDialog.tsx`**

```tsx
import { ReactNode, useEffect, useRef } from 'react';

interface SmithDialogProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  /** Destructive dialogs cannot be dismissed by tapping outside. */
  destructive?: boolean;
}

export function SmithDialog({ open, onClose, title, children, footer, destructive = false }: SmithDialogProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const restoreRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) return;
    restoreRef.current = document.activeElement as HTMLElement | null;
    // Focus the first focusable control, else the panel itself.
    const panel = panelRef.current;
    const first = panel?.querySelector<HTMLElement>(
      '[data-autofocus], button, [href], input, select, textarea',
    );
    (first ?? panel)?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      restoreRef.current?.focus?.();
    };
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div
      data-testid="sn-dialog-backdrop"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={destructive ? undefined : onClose}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        tabIndex={-1}
        className="w-full max-w-md rounded-sn-card bg-sn-bg-panel text-sn-ink shadow-sn-md outline-none"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-5 pt-4 pb-2 font-semibold">{title}</div>
        <div className="px-5 pb-4 text-sm">{children}</div>
        {footer && <div className="flex justify-end gap-2 px-5 pb-4">{footer}</div>}
      </div>
    </div>
  );
}

interface ConfirmDialogProps {
  open: boolean;
  title: ReactNode;
  body: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({ open, title, body, confirmLabel, cancelLabel = 'Cancel', onConfirm, onCancel }: ConfirmDialogProps) {
  return (
    <SmithDialog
      open={open}
      onClose={onCancel}
      title={title}
      destructive
      footer={
        <>
          <button
            type="button"
            data-autofocus
            onClick={onCancel}
            className="rounded-sn-input px-4 py-2 font-data text-sm text-sn-ink-muted hover:text-sn-ink transition-opacity duration-sn-fast"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="rounded-sn-input px-4 py-2 font-data text-sm bg-sn-status-error text-white hover:opacity-90 transition-opacity duration-sn-fast"
          >
            {confirmLabel}
          </button>
        </>
      }
    >
      {body}
    </SmithDialog>
  );
}
```

- [ ] **Step 4: Rewrite `Modal.tsx` as a wrapper** (replace the file's implementation, keep its exact export name and prop shape so the 7 form callers compile unchanged):

```tsx
// v1 name kept as a thin alias over SmithDialog (Design System v2).
// Form modals are non-destructive: backdrop click still closes them.
import { ReactNode } from 'react';
import { SmithDialog } from './SmithDialog';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
}

export function Modal({ open, onClose, title, children }: ModalProps) {
  return (
    <SmithDialog open={open} onClose={onClose} title={title}>
      {children}
    </SmithDialog>
  );
}
```

- [ ] **Step 5: Verify PASS + full suite (Modal callers must stay green)**

Run: `cd desktop/portal && npx vitest run src/console/components/ui/__tests__/SmithDialog.test.tsx && npx vitest run`
Expected: 7 new tests PASS; full suite green. If any Modal-caller test asserted backdrop-dismiss or styling classes, update those assertions to the new markup (report which in the task report).

- [ ] **Step 6: Commit**

```bash
git add desktop/portal/src/console/components/ui/SmithDialog.tsx \
  desktop/portal/src/console/components/ui/Modal.tsx \
  desktop/portal/src/console/components/ui/__tests__/SmithDialog.test.tsx
git commit -m "feat(portal): SmithDialog + ConfirmDialog; Modal becomes v2 alias

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Web destructive-confirm migration

**Files:**
- Modify: `desktop/portal/src/console/routes/ClientDetailRoute.tsx` (kill `window.confirm`, line ~78)
- Modify: `desktop/portal/src/console/components/comm/MessageList.tsx` (delete has NO confirm today, `doDelete` ~line 77)
- Modify: `desktop/portal/src/console/components/tasks/TaskList.tsx` (`doDelete` ~line 34)
- Modify: `desktop/portal/src/console/components/invoices/LineItemRow.tsx` (`doDelete` ~line 16)
- Test: extend the co-located test file for each component (create `__tests__/<Name>.test.tsx` beside it if none exists)

**Interfaces:**
- Consumes: `ConfirmDialog` from Task 2.
- Produces: every destructive delete in the portal goes through ConfirmDialog. Uniform copy pattern: title `Delete <thing>?`, body states consequence, confirm label `Delete`.

The pattern (identical in all four sites — worked example for ClientDetailRoute):

- [ ] **Step 1: Write/extend the failing test** — for each component, add a test asserting: clicking the delete affordance does NOT call the delete API/store action immediately, a dialog appears (`getByRole('dialog')`), clicking `Delete` inside it performs the action, clicking `Cancel` does not. Example for MessageList (adapt selectors to the existing test file's setup):

```tsx
it('message delete asks for confirmation first', async () => {
  // render list with one own-message per this file's existing fixtures
  fireEvent.click(screen.getByText('[x]'));
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  // store/API untouched until confirm:
  expect(removeMessageSpy).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
  expect(removeMessageSpy).toHaveBeenCalledTimes(1);
});
```

- [ ] **Step 2: Run new tests to verify FAIL**
- [ ] **Step 3: Implement the pattern in each component** — worked example, `ClientDetailRoute.tsx`:

Replace:
```tsx
if (!window.confirm('Delete this client?')) return;
```
with state + dialog:
```tsx
const [confirmingDelete, setConfirmingDelete] = useState(false);
// delete button onClick: () => setConfirmingDelete(true)
// alongside the existing JSX:
<ConfirmDialog
  open={confirmingDelete}
  title="Delete this client?"
  body="Their jobs and invoices keep the record, but the client entry is removed."
  confirmLabel="Delete"
  onConfirm={() => { setConfirmingDelete(false); void doDelete(); }}
  onCancel={() => setConfirmingDelete(false)}
/>
```
Also change the delete trigger from `variant="secondary"` to `variant="danger"` (Button already supports it; it has zero users today).

For `MessageList.tsx`, the confirming state holds the pending message id (`const [confirmingId, setConfirmingId] = useState<string | null>(null)`); `[x]` sets it; ConfirmDialog title `Delete message?`, body `It disappears for everyone in the channel.`. Same shape for TaskList (`Delete task?`) and LineItemRow (`Remove line item?`, confirm label `Remove`).

- [ ] **Step 4: Run tests to verify PASS + full suite green**
- [ ] **Step 5: Commit**

```bash
git add -A desktop/portal/src/console
git commit -m "feat(portal): all destructive deletes gated by ConfirmDialog

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Web primitives re-skin to sn tokens

**Files:**
- Modify: `desktop/portal/src/console/components/ui/Button.tsx`, `Input.tsx`, `Chip.tsx`, `Card.tsx`, `Badge.tsx`, `Pill.tsx`, `Toast.tsx`, `Avatar.tsx`, `ProgressBar.tsx`, `SectionHeader.tsx`
- Test: existing co-located tests updated where class assertions change

**Interfaces:**
- Consumes: `sn-*` classes and `--sn-*` vars.
- Produces: every shared primitive renders from tokens (dark-ready). Public prop APIs DO NOT change (20 Button callers etc. must not need edits).

Class mapping (apply mechanically; keep structure/props identical):
- `bg-console-accent` → `bg-sn-accent`; `text-console-text` → `text-sn-ink`; `text-console-text-muted` → `text-sn-ink-muted`; `border-console-border` → `border-sn-line`; `bg-console-surface` → `bg-sn-bg-panel`; `bg-console-bg` → `bg-sn-bg-base`; `console-danger` → `sn-status-error`; `console-ok` → `sn-status-online`; `console-warn` → `sn-attention`.
- Buttons/inputs keep `rounded-full` (equals `rounded-sn-input`); cards move to `rounded-sn-card shadow-sn-sm`.
- `ProgressBar.tsx` and `SectionHeader.tsx` reference UNDEFINED vars (`var(--color-surface)`, `var(--color-text-dim)`) — replace with `var(--sn-bg-panel)` and `var(--sn-ink-muted)`.
- `Avatar.tsx` inline styles: `var(--console-surface, #FAFAF8)` → `var(--sn-bg-panel)`; `var(--font-mono)` stays (defined in index.css).
- `Toast.tsx` tones: info → `bg-sn-bg-panel text-sn-ink border-sn-line`, error → `border-sn-status-error text-sn-status-error`.
- Focus states: every interactive primitive gains `focus-visible:outline focus-visible:outline-2 focus-visible:outline-sn-accent` (closes the audit's missing-focus-ring finding for shared components).

- [ ] **Step 1: For each file, update classes per the mapping** (no prop/API changes)
- [ ] **Step 2: Update any test assertions that pinned old class strings** (report each)
- [ ] **Step 3: Verify** — `cd desktop/portal && npx vitest run && npm run build`; then `grep -rn "console-" src/console/components/ui/` must return ZERO hits (the ui/ dir is the first console-free zone)
- [ ] **Step 4: Commit**

```bash
git add desktop/portal/src/console/components/ui desktop/portal/src/console/components/ui/__tests__ 2>/dev/null || git add -A desktop/portal/src/console/components/ui
git commit -m "feat(portal): shared ui primitives re-skinned to sn tokens + focus rings

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Android theme provider + Smith primitives

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/theme2/SmithTheme.kt`
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/theme2/SmithButton.kt`
- Test: `android/app/src/test/java/com/guildofsmiths/trademesh/ui/theme2/SmithThemeTest.kt`

**Interfaces:**
- Consumes: `Tokens2` (generated, Plan 1) and `ConsoleTheme.inter` / `ConsoleTheme.jetBrainsMono` font families.
- Produces:
  - `SmithColors` data class mirroring the token names (`bgBase, bgPanel, bgSunken, line, ink, inkMuted, accent, attention, statusOnline, statusError, overlay, inkOnAccent`). (`overlay` and `inkOnAccent` were added to tokens.json in Task 2's fix round — scrims and on-accent text are tokens too.)
  - `LocalSmithColors: CompositionLocal<SmithColors>` and `SmithTheme(darkEnabled: Boolean = false, content)` — resolves Dark only when `darkEnabled && isSystemInDarkTheme()`; darkEnabled stays false at every call site in this plan (v1 screens are not dark-safe until Plans 4-5).
  - `SmithButton(text, onClick, variant: SmithButtonVariant = Primary, enabled = true, modifier)` with variants `Primary` (accent fill, white text), `Ghost` (transparent, inkMuted text), `Danger` (statusError fill, white text). Pill shape (999.dp corner), Inter Medium 14sp, no Material.

- [ ] **Step 1: Write the failing JVM test**

`SmithThemeTest.kt`:

```kotlin
package com.guildofsmiths.trademesh.ui.theme2

import com.guildofsmiths.trademesh.ui.Tokens2
import org.junit.Assert.assertEquals
import org.junit.Test

class SmithThemeTest {
    @Test
    fun lightColorsMirrorTokens() {
        val c = smithColorsFor(dark = false)
        assertEquals(Tokens2.Light.BgBase, c.bgBase)
        assertEquals(Tokens2.Light.Accent, c.accent)
        assertEquals(Tokens2.Light.StatusError, c.statusError)
    }

    @Test
    fun darkColorsMirrorTokens() {
        val c = smithColorsFor(dark = true)
        assertEquals(Tokens2.Dark.BgBase, c.bgBase)
        assertEquals(Tokens2.Dark.Accent, c.accent)
    }
}
```

- [ ] **Step 2: Run to verify FAIL** — `cd android && export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home && ./gradlew :app:testDebugUnitTest --tests "*SmithThemeTest*" --console=plain -q` → compilation error (unresolved `smithColorsFor`)

- [ ] **Step 3: Implement `SmithTheme.kt`**

```kotlin
package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.guildofsmiths.trademesh.ui.Tokens2

/** Resolved v2 palette. Mirrors design/tokens.json via generated Tokens2. */
data class SmithColors(
    val bgBase: Color,
    val bgPanel: Color,
    val bgSunken: Color,
    val line: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val attention: Color,
    val statusOnline: Color,
    val statusError: Color,
    val overlay: Color,
    val inkOnAccent: Color,
)

fun smithColorsFor(dark: Boolean): SmithColors = if (dark) SmithColors(
    bgBase = Tokens2.Dark.BgBase, bgPanel = Tokens2.Dark.BgPanel,
    bgSunken = Tokens2.Dark.BgSunken, line = Tokens2.Dark.Line,
    ink = Tokens2.Dark.Ink, inkMuted = Tokens2.Dark.InkMuted,
    accent = Tokens2.Dark.Accent, attention = Tokens2.Dark.Attention,
    statusOnline = Tokens2.Dark.StatusOnline, statusError = Tokens2.Dark.StatusError,
    overlay = Tokens2.Dark.Overlay, inkOnAccent = Tokens2.Dark.InkOnAccent,
) else SmithColors(
    bgBase = Tokens2.Light.BgBase, bgPanel = Tokens2.Light.BgPanel,
    bgSunken = Tokens2.Light.BgSunken, line = Tokens2.Light.Line,
    ink = Tokens2.Light.Ink, inkMuted = Tokens2.Light.InkMuted,
    accent = Tokens2.Light.Accent, attention = Tokens2.Light.Attention,
    statusOnline = Tokens2.Light.StatusOnline, statusError = Tokens2.Light.StatusError,
    overlay = Tokens2.Light.Overlay, inkOnAccent = Tokens2.Light.InkOnAccent,
)

val LocalSmithColors = staticCompositionLocalOf { smithColorsFor(dark = false) }

/**
 * v2 theme provider. darkEnabled stays false until screens are token-clean
 * (Plans 4-5): components must be dark-READY without flipping the app dark.
 */
@Composable
fun SmithTheme(darkEnabled: Boolean = false, content: @Composable () -> Unit) {
    val colors = smithColorsFor(dark = darkEnabled && isSystemInDarkTheme())
    CompositionLocalProvider(LocalSmithColors provides colors, content = content)
}
```

- [ ] **Step 4: Implement `SmithButton.kt`**

```kotlin
package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.ui.ConsoleTheme

enum class SmithButtonVariant { Primary, Ghost, Danger }

@Composable
fun SmithButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SmithButtonVariant = SmithButtonVariant.Primary,
    enabled: Boolean = true,
) {
    val colors = LocalSmithColors.current
    val (bg, fg) = when (variant) {
        SmithButtonVariant.Primary -> colors.accent to colors.inkOnAccent
        SmithButtonVariant.Ghost -> Color.Transparent to colors.inkMuted
        SmithButtonVariant.Danger -> colors.statusError to colors.inkOnAccent
    }
    Text(
        text = text,
        style = TextStyle(
            fontFamily = ConsoleTheme.inter,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = if (enabled) fg else fg.copy(alpha = 0.5f),
        ),
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
```

(Note: `androidx.compose.material3.Text` is the one Material import the codebase already treats as allowed everywhere — it is a text primitive, not a widget; ConsoleTheme uses it throughout.)

- [ ] **Step 5: Verify PASS** — same gradle test command → test passes; then `./gradlew :app:compileDebugKotlin --console=plain -q` clean
- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/theme2 \
  android/app/src/test/java/com/guildofsmiths/trademesh/ui/theme2
git commit -m "feat(android): SmithTheme provider (Tokens2, dark-ready) + SmithButton

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Android SmithDialog + SmithSheet

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/theme2/SmithDialog.kt`
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/theme2/SmithSheet.kt`

**Interfaces:**
- Consumes: `LocalSmithColors`, `SmithButton` (Task 5), `androidx.compose.ui.window.Dialog` (non-Material base).
- Produces:
  - `SmithDialog(title, onDismiss, destructive = false, sizeFraction: Pair<Float,Float>? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit)` — `Dialog(properties = DialogProperties(dismissOnClickOutside = !destructive, dismissOnBackPress = true))`; panel = `bgPanel`, 20.dp corners, title in Inter SemiBold 16sp, `sizeFraction` (e.g. `0.95f to 0.9f`) for the preview/detail dialogs that currently size themselves.
  - `SmithConfirmDialog(title, body, confirmText, onConfirm, onDismiss, confirmIsDanger = true)` — built on SmithDialog with `destructive = true`; Ghost cancel ("CANCEL") + Danger/Primary confirm.
  - `SmithSheet(onDismiss, content)` — non-Material bottom sheet: full-screen scrim (`colors.overlay`, click = dismiss), content panel aligned to bottom, `bgPanel`, top corners 20.dp, slides in with `animateFloatAsState`-free simple `AnimatedVisibility(slideInVertically)` capped at 250ms tween. No drag gesture in v1.

- [ ] **Step 1: Implement both files** (complete code below)

`SmithDialog.kt`:

```kotlin
package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.guildofsmiths.trademesh.ui.ConsoleTheme

@Composable
fun SmithDialog(
    title: String,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    sizeFraction: Pair<Float, Float>? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSmithColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = !destructive,
            usePlatformDefaultWidth = sizeFraction == null,
        ),
    ) {
        var panel = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.bgPanel)
        if (sizeFraction != null) {
            panel = panel
                .fillMaxWidth(sizeFraction.first)
                .fillMaxHeight(sizeFraction.second)
        } else {
            panel = panel.fillMaxWidth()
        }
        Column(modifier = panel.padding(20.dp)) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = ConsoleTheme.inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = colors.ink,
                ),
            )
            Spacer(modifier = Modifier.padding(top = 10.dp))
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Spacer(modifier = Modifier.weight(1f))
                actions()
            }
        }
    }
}

@Composable
fun SmithConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmIsDanger: Boolean = true,
) {
    val colors = LocalSmithColors.current
    SmithDialog(
        title = title,
        onDismiss = onDismiss,
        destructive = true,
        actions = {
            SmithButton(text = "CANCEL", onClick = onDismiss, variant = SmithButtonVariant.Ghost)
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            SmithButton(
                text = confirmText,
                onClick = onConfirm,
                variant = if (confirmIsDanger) SmithButtonVariant.Danger else SmithButtonVariant.Primary,
            )
        },
    ) {
        Text(
            text = body,
            style = TextStyle(
                fontFamily = ConsoleTheme.inter,
                fontSize = 14.sp,
                color = colors.inkMuted,
            ),
        )
    }
}
```

`SmithSheet.kt`:

```kotlin
package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp

/**
 * Non-Material bottom sheet: scrim + bottom-aligned panel inside a Dialog
 * window so it layers above everything. 250ms slide per the motion tokens.
 * No drag-to-dismiss in v1 — scrim tap or back dismisses.
 */
@Composable
fun SmithSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSmithColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.overlay)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(animationSpec = tween(250)) { it },
                exit = slideOutVertically(animationSpec = tween(200)) { it },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(colors.bgPanel)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .padding(20.dp),
                    content = content,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Compile + unit tests** — `cd android && export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --console=plain -q`
Expected: BUILD SUCCESSFUL
- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/theme2
git commit -m "feat(android): SmithDialog/SmithConfirmDialog + SmithSheet (non-Material)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: Android dialog migration — batch 1 (comm + settings, sites 1-9)

**Files:**
- Modify: `ui/NewConversationScreen.kt` (sites at ~431, ~506, ~622 — Input dialogs)
- Modify: `ui/SettingsScreen.kt` (~1423, ~1466, ~1633 — destructive confirms)
- Modify: `ui/ChatListScreen.kt` (~282 — destructive confirm)
- Modify: `ui/ConversationScreen.kt` (~787 — destructive confirm)
- Modify: `ui/ChannelListScreen.kt` (~397 — destructive confirm)
(all under `android/app/src/main/java/com/guildofsmiths/trademesh/`)

**Interfaces:**
- Consumes: `SmithDialog` / `SmithConfirmDialog` / `SmithButton` (Tasks 5-6). Wrap nothing in SmithTheme yet — `LocalSmithColors` default IS the light palette, so call sites work without a provider.
- Produces: zero `AlertDialog(` in these 5 files.

Worked example A — destructive confirm (`SettingsScreen.kt` ~1423, "Leave team"). Replace the whole `AlertDialog(...)` expression:

```kotlin
// BEFORE (shape):
AlertDialog(
    onDismissRequest = { showLeaveConfirm = false },
    containerColor = ConsoleTheme.surface,
    title = { Text("LEAVE TEAM?", style = ConsoleTheme.captionBold) },
    text = { Text("You lose access to team jobs and chat.", style = ConsoleTheme.body) },
    confirmButton = { Text("[LEAVE]", style = ConsoleTheme.action.copy(color = ConsoleTheme.danger), modifier = Modifier.clickable { onLeave() }) },
    dismissButton = { Text("[CANCEL]", style = ConsoleTheme.action, modifier = Modifier.clickable { showLeaveConfirm = false }) },
)

// AFTER:
SmithConfirmDialog(
    title = "Leave team?",
    body = "You lose access to team jobs and chat.",
    confirmText = "LEAVE",
    onConfirm = { onLeave() },
    onDismiss = { showLeaveConfirm = false },
)
```

Worked example B — input/form dialog (`NewConversationScreen.kt` ~431, "ADD CLIENT"). Keep the existing field composables (`DialogField(...)` etc.) as the content block:

```kotlin
SmithDialog(
    title = "Add client",
    onDismiss = { showAddClient = false },
    actions = {
        SmithButton(text = "CANCEL", onClick = { showAddClient = false }, variant = SmithButtonVariant.Ghost)
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        SmithButton(text = "ADD", onClick = { submitClient() })
    },
) {
    DialogField(label = "NAME", value = name, onValueChange = { name = it })
    DialogField(label = "PHONE", value = phone, onValueChange = { phone = it })
    // ...existing fields move here unchanged
}
```

Per-file mechanics: adapt each site's existing state variable names, field composables, and callbacks — the two examples define the mapping (`title` → sentence case per v2 copy; `confirmButton` clickable Text → SmithButton; `text = {}` → content block). Remove now-unused `AlertDialog` imports. Where a file's `@Preview` uses `MaterialTheme { }` (ChatListScreen.kt:934, SettingsScreen.kt:2094, ChannelListScreen.kt:614, ConversationScreen.kt:1332), delete the wrapper (previews render fine without it).

- [ ] **Step 1: Migrate all 9 sites per the worked examples**
- [ ] **Step 2: Verify zero AlertDialog remains in the batch** — `grep -n "AlertDialog(" ui/NewConversationScreen.kt ui/SettingsScreen.kt ui/ChatListScreen.kt ui/ConversationScreen.kt ui/ChannelListScreen.kt` (from the package dir) → no output
- [ ] **Step 3: Compile + unit tests** (gradle commands as Task 6 Step 2) — BUILD SUCCESSFUL
- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui
git commit -m "feat(android): comm+settings dialogs migrated to SmithDialog (batch 1/3)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 8: Android dialog migration — batch 2 (jobboard + timetracking, sites 19-27)

**Files:**
- Modify: `ui/jobboard/JobBoardScreen.kt` (~335 confirm, ~606 destructive, ~641 destructive, ~733 detail `sizeFraction = 0.95f to 0.9f`, ~1362 confirm, ~1396 input, ~1606 detail `0.95f to 0.55f`)
- Modify: `ui/timetracking/TimeTrackingScreen.kt` (~358 input `0.9f to 0.8f`, ~539 input `0.9f to 0.75f`)

**Interfaces:**
- Consumes: same as Task 7. Detail/preview dialogs pass `sizeFraction` matching their current `fillMaxWidth(x).fillMaxHeight(y)` modifiers (read each site; the fractions listed above are from the current code).
- Produces: zero `AlertDialog(` in these 2 files.

- [ ] **Step 1: Migrate all 9 sites** (confirms → SmithConfirmDialog; inputs/details → SmithDialog with existing content; keep testTags — `solo_e2e_*` tags MUST survive on the same logical controls, the Maestro suite depends on them)
- [ ] **Step 2: Verify** — grep zero `AlertDialog(` in both files; gradle compile + unit tests BUILD SUCCESSFUL
- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui
git commit -m "feat(android): jobboard+timetracking dialogs to SmithDialog (batch 2/3)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 9: Android migration — batch 3 (remaining sites + sheets + Material refs)

**Files:**
- Modify: `ui/clients/ClientsScreen.kt` (~181 input), `ui/plan/IntentComponents.kt` (~82 input, ~344 detail), `ui/jobpipeline/JobPipelineScreen.kt` (~597 menu, ~679 input, ~779 input, ~893 picker), `ui/supply/SupplyScreen.kt` (~330 picker, ~407 input), `ui/proposal/ProposalPreviewDialog.kt` (~42 preview `0.98f to 0.95f`), `ui/invoice/InvoiceScreen.kt` (~39 preview `0.98f to 0.95f`)
- Modify: `ui/expenses/InvoicePreviewBottomSheet.kt` (ModalBottomSheet ~66 → SmithSheet), `ui/expenses/JobExpenseDetailScreen.kt` (ModalBottomSheet ~328 → SmithSheet)
- Modify: `MainActivity.kt:261` (`MaterialTheme.colorScheme.background` → `ConsoleTheme.background`), `ui/components/LeftSidebar.kt` (4 `colorScheme` refs → ConsoleTheme equivalents: `surfaceVariant`→`ConsoleTheme.surface`, `primary`→`ConsoleTheme.accent`, `onSurface`→`ConsoleTheme.text`)
- Modify: remaining `@Preview` MaterialTheme wrappers — `ui/WelcomeScreen.kt:160`, `ui/BeaconListScreen.kt:366`, `ui/CreateBeaconScreen.kt:178`, `ui/PeersScreen.kt:298`, `ui/CreateChannelScreen.kt:326` (delete wrapper)

**Interfaces:**
- Consumes: Tasks 5-6 components. The three fully-qualified `androidx.compose.material3.AlertDialog(` sites (ClientsScreen, SupplyScreen x2) migrate identically.
- Produces: `grep -rn "AlertDialog(" app/src/main` → ZERO. `grep -rn "ModalBottomSheet(" app/src/main` → ZERO. `grep -rn "MaterialTheme" app/src/main` → only `ui/theme/Theme.kt` (the app-level wrapper stays until Plan 4/5 confirms nothing Material remains under it).

- [ ] **Step 1: Migrate the 11 dialog sites** per Task 7's worked examples (pickers/menus are SmithDialog whose content is the existing option rows; keep `testTag`s)
- [ ] **Step 2: Replace the two ModalBottomSheet usages with SmithSheet** — existing sheet body composables move into SmithSheet's content unchanged; `rememberModalBottomSheetState` and its imports are removed; visibility stays governed by the same `if (showSheet)` state that gates it today
- [ ] **Step 3: Fix the colorScheme refs and delete the 5 preview wrappers**
- [ ] **Step 4: Verify the three greps above + gradle compile + full unit tests** — BUILD SUCCESSFUL, greps clean
- [ ] **Step 5: Commit**

```bash
git add android/app/src/main
git commit -m "feat(android): final dialog batch + SmithSheet swap; Material refs purged (3/3)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 10: Whole-plan verification gates

**Files:**
- Modify: `.github/workflows/portal-ci.yml` — none needed (already gates portal)
- No new files; this is a verification-only task.

- [ ] **Step 1: Portal** — `cd desktop/portal && npx vitest run && npm run build` → all green; `grep -rn "console-" src/console/components/ui/` → zero; `grep -rn "window.confirm" src/` → zero
- [ ] **Step 2: Android** — gradle compile + full `testDebugUnitTest` (JDK 17); `grep -rn "AlertDialog(\|ModalBottomSheet(" app/src/main` → zero; `grep -rn "MaterialTheme" app/src/main | grep -v "ui/theme/Theme.kt"` → zero
- [ ] **Step 3: Tokens** — `node scripts/gen-tokens.mjs --check` → up to date
- [ ] **Step 4: Report** the full gate results in the task report (no commit unless a gate fix was needed — if one was, it goes in its owning task's file scope with a `fix(...)` commit)

---

## Self-Review

- Spec coverage (Plan 2 scope = spec section 6 + the dialog/confirm parts of section 9): SmithDialog/ConfirmDialog web (T2) + Android (T6); Material AlertDialog purge — all 29 scouted sites split across T7 (9) + T8 (9) + T9 (11); SmithSheet + the 2 ModalBottomSheet swaps (T6/T9); SmithButton Android (T5); portal primitives on tokens + focus rings (T4); theme machinery (T1); destructive-confirm rule enforced on web's 4 unsafe sites (T3). MessageRow/Composer/UnreadBadge/NewDivider are Plan 3 (comm mechanics) by design; EmptyState/LoadingState/ErrorState primitives are Plan 4's opening task, where their consumers land.
- Site count check: scout found 29 AlertDialogs. T7 covers 9 (sites 1-9), T8 covers 9 (sites 19-27), T9 covers 11 (sites 10-18, 28, 29). 9+9+11 = 29. ✓
- Placeholders: none — full component code given; migrations reference two complete worked examples plus per-site variant/fraction mappings.
- Type consistency: `SmithButtonVariant` enum used by T5/T6/T7; `smithColorsFor(dark:)` defined T5, tested T5, consumed T6; `sizeFraction: Pair<Float,Float>?` defined T6, used T8/T9; web `ConfirmDialog` props defined T2, consumed T3.
