# Design System v2 — Plan 1: Tokens Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the single-source token system (North Cobalt palette, light+dark), its two generators (Tailwind preset for the portal, Kotlin object for Android), the font pipeline (Inter/JetBrains Mono/Syne), and fix the portal's dark-body and PWA-color bugs.

**Architecture:** `design/tokens.json` is the only place color/shape/motion values live. A zero-dependency Node script (`scripts/gen-tokens.mjs`) emits three artifacts: a CSS-custom-properties file + Tailwind preset for the portal, and `Tokens2.kt` for Android. A `--check` mode fails CI when generated output drifts from the source. v1 theme values stay untouched (screens still use them until Plans 2-5 migrate); v2 tokens are additive in this plan.

**Tech Stack:** Node 20 (no new deps for the generator), Tailwind 3.4, Vitest, @fontsource (Inter, JetBrains Mono, Syne), Jetpack Compose, Gradle.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-08-design-system-v2-design.md`. Palette is North Cobalt exactly as written there — do not adjust hex values.
- No emoji anywhere (code, commits, docs). ASCII/Unicode glyph tokens only.
- Light is the default theme; dark activates via `[data-theme="dark"]` or `prefers-color-scheme` (web).
- Accent discipline: cobalt = actions/brand, amber = attention. Never introduce other accent hexes.
- Commit style: `type(scope): summary`, end body with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Do not modify v1 values in `tailwind.config.js` `console-*` colors or `ConsoleTheme.kt` existing constants — later plans migrate consumers; this plan only adds v2.
- Portal working dir: `desktop/portal`. Android working dir: `android`. Repo root: `/Users/fegensprenelon/smith-net`.

---

### Task 1: Token source of truth + glyph registry

**Files:**
- Create: `design/tokens.json`
- Create: `design/GLYPHS.md`

**Interfaces:**
- Produces: `design/tokens.json` — the exact JSON below; every later task reads it. Key paths: `color.light.*`, `color.dark.*`, `radius.*`, `spacing[]`, `type.*`, `motion.*`, `shadow.*`.

- [ ] **Step 1: Write `design/tokens.json`**

```json
{
  "$schema": "internal: Smith Net design tokens v2 (North Cobalt). Source of truth. Run scripts/gen-tokens.mjs after edits.",
  "color": {
    "light": {
      "bgBase": "#F7F8FA",
      "bgPanel": "#FFFFFF",
      "bgSunken": "#EEF1F5",
      "line": "#E2E6EC",
      "ink": "#1C2128",
      "inkMuted": "#7A8290",
      "accent": "#2F5FE8",
      "attention": "#E8590C",
      "statusOnline": "#3E9B4F",
      "statusError": "#D64545"
    },
    "dark": {
      "bgBase": "#14171C",
      "bgPanel": "#1D2129",
      "bgSunken": "#0E1013",
      "line": "#2B303A",
      "ink": "#E9ECF1",
      "inkMuted": "#8A93A3",
      "accent": "#6B8CFF",
      "attention": "#FF8A3D",
      "statusOnline": "#63C76F",
      "statusError": "#FF6B6B"
    }
  },
  "shadow": {
    "light": {
      "sm": "0 1px 3px rgba(28,33,40,0.10)",
      "md": "0 2px 8px rgba(28,33,40,0.10)"
    },
    "dark": {
      "sm": "0 1px 3px rgba(0,0,0,0.45)",
      "md": "0 2px 8px rgba(0,0,0,0.45)"
    }
  },
  "radius": { "card": 20, "bubble": 14, "input": 999, "ops": 0 },
  "spacing": [4, 8, 12, 16, 24, 32],
  "type": {
    "scale": [12, 13, 14, 16, 20, 24, 30],
    "fonts": {
      "display": "Syne",
      "ui": "Inter",
      "data": "JetBrains Mono"
    }
  },
  "motion": {
    "durationFastMs": 200,
    "durationBaseMs": 250,
    "easing": "cubic-bezier(.2,.8,.2,1)"
  }
}
```

- [ ] **Step 2: Verify it parses**

Run: `node -e "JSON.parse(require('fs').readFileSync('design/tokens.json','utf8')); console.log('OK')"`
Expected: `OK`

- [ ] **Step 3: Write `design/GLYPHS.md`**

```markdown
# Smith Net Glyph Registry (v2)

Glyphs are the icon language of the comm surface. They render ONLY in
JetBrains Mono, inside a fixed-width cell (1.3em web / 1.3.em-equivalent
Compose width), baseline-aligned with adjacent text. New iconography on the
comm surface must be added here first. Other surfaces may use Lucide line
icons (1.5px stroke, ink-muted default) where no glyph exists.

| Glyph | Meaning          | Allowed contexts                          | Never                              |
|-------|------------------|-------------------------------------------|------------------------------------|
| `●`   | online/present   | presence dots, avatar corner, counts       | as a bullet in body copy           |
| `○`   | offline/away     | presence dots, avatar corner               | as a decorative ring               |
| `[▣]` | photo attachment | message rows, attachment chips, notifs     | outside media contexts             |
| `[▶]` | voice/playable   | message rows, attachment chips, notifs     | as a generic "go" affordance       |
| `[≡]` | file attachment  | message rows, attachment chips, notifs     | as a menu/hamburger                |
| `>`   | composer prompt  | composer leading position only             | headings, breadcrumbs              |
| `←`   | back             | screen headers, sheet headers              | inline in sentences                |
| `↵`   | send             | composer trailing action                   | anywhere else                      |
| `▾`   | disclosure       | expanders, org switcher                    | sort indicators (use ops tables)   |

Presence colors come from tokens: statusOnline / inkMuted (offline).
Attention states use the amber `attention` token, never a new hex.
```

- [ ] **Step 4: Commit**

```bash
git add design/tokens.json design/GLYPHS.md
git commit -m "feat(design): v2 token source (North Cobalt) + glyph registry

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Token generator with drift check

**Files:**
- Create: `scripts/gen-tokens.mjs`
- Create (generated): `desktop/portal/src/styles/tokens.css`
- Create (generated): `desktop/portal/tailwind.tokens.cjs`
- Create (generated): `android/app/src/main/java/com/guildofsmiths/trademesh/ui/Tokens2.kt`
- Test: `desktop/portal/src/__tests__/tokens.test.ts`

**Interfaces:**
- Consumes: `design/tokens.json` (Task 1 shape).
- Produces:
  - CSS variables named `--sn-<kebab>` (e.g. `--sn-bg-base`, `--sn-ink-muted`, `--sn-shadow-sm`) on `:root` (light), `[data-theme="dark"]`, and dark `prefers-color-scheme` fallback.
  - Tailwind preset exporting colors `sn-bg-base`, `sn-bg-panel`, `sn-bg-sunken`, `sn-line`, `sn-ink`, `sn-ink-muted`, `sn-accent`, `sn-attention`, `sn-status-online`, `sn-status-error`; `boxShadow.sn-sm/sn-md`; `borderRadius` `sn-card` 20px / `sn-bubble` 14px / `sn-input` 999px; `fontFamily` `sans:[Inter...]`, `data:[JetBrains Mono...]`, `display:[Syne...]`.
  - Kotlin `object Tokens2` with nested `Light`/`Dark` color objects (val names = UpperCamel of JSON keys, `Color(0xFF......)`), plus `RadiusCard/RadiusBubble/RadiusOps` Dp and `DurationFastMs/DurationBaseMs` Int.
  - CLI: `node scripts/gen-tokens.mjs` writes files; `node scripts/gen-tokens.mjs --check` exits 1 with a diff message if any output is stale.

- [ ] **Step 1: Write the failing test**

`desktop/portal/src/__tests__/tokens.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { readFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';

const repo = resolve(__dirname, '../../../..');
const tokens = JSON.parse(readFileSync(resolve(repo, 'design/tokens.json'), 'utf8'));

describe('generated tokens', () => {
  it('tokens.css exists and carries light + dark values', () => {
    const css = readFileSync(resolve(__dirname, '../styles/tokens.css'), 'utf8');
    expect(css).toContain(`--sn-bg-base: ${tokens.color.light.bgBase}`);
    expect(css).toContain(`--sn-accent: ${tokens.color.light.accent}`);
    expect(css).toContain(tokens.color.dark.bgBase);
    expect(css).toContain('[data-theme="dark"]');
    expect(css).toContain('prefers-color-scheme: dark');
  });

  it('tailwind preset maps semantic names to css vars', () => {
    const preset = readFileSync(resolve(repo, 'desktop/portal/tailwind.tokens.cjs'), 'utf8');
    expect(preset).toContain("'sn-bg-base': 'var(--sn-bg-base)'");
    expect(preset).toContain("'sn-attention': 'var(--sn-attention)'");
    expect(preset).toContain('Inter');
    expect(preset).toContain('JetBrains Mono');
  });

  it('Tokens2.kt carries both palettes as Compose colors', () => {
    const kt = readFileSync(
      resolve(repo, 'android/app/src/main/java/com/guildofsmiths/trademesh/ui/Tokens2.kt'), 'utf8');
    expect(kt).toContain('object Tokens2');
    expect(kt).toContain('Color(0xFF2F5FE8)'); // light accent
    expect(kt).toContain('Color(0xFF6B8CFF)'); // dark accent
    expect(kt).toContain('val RadiusCard = 20.dp');
  });

  it('generated files exist', () => {
    expect(existsSync(resolve(__dirname, '../styles/tokens.css'))).toBe(true);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd desktop/portal && npx vitest run src/__tests__/tokens.test.ts`
Expected: FAIL — `ENOENT ... tokens.css`

- [ ] **Step 3: Write `scripts/gen-tokens.mjs`**

```js
#!/usr/bin/env node
// Generates portal CSS vars + Tailwind preset and Android Tokens2.kt from
// design/tokens.json. --check exits 1 when outputs are stale.
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const t = JSON.parse(readFileSync(resolve(root, 'design/tokens.json'), 'utf8'));
const kebab = (s) => s.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();
const upper = (s) => s[0].toUpperCase() + s.slice(1);

function cssVars(theme) {
  const lines = Object.entries(t.color[theme]).map(([k, v]) => `  --sn-${kebab(k)}: ${v};`);
  lines.push(`  --sn-shadow-sm: ${t.shadow[theme].sm};`);
  lines.push(`  --sn-shadow-md: ${t.shadow[theme].md};`);
  return lines.join('\n');
}

const css = `/* GENERATED by scripts/gen-tokens.mjs from design/tokens.json. Do not edit. */
:root {
${cssVars('light')}
  --sn-ease: ${t.motion.easing};
  --sn-dur-fast: ${t.motion.durationFastMs}ms;
  --sn-dur-base: ${t.motion.durationBaseMs}ms;
}
:root[data-theme="dark"] {
${cssVars('dark')}
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
${cssVars('dark')}
  }
}
`;

const colorKeys = Object.keys(t.color.light);
const preset = `/* GENERATED by scripts/gen-tokens.mjs from design/tokens.json. Do not edit. */
module.exports = {
  theme: {
    extend: {
      colors: {
${colorKeys.map((k) => `        'sn-${kebab(k)}': 'var(--sn-${kebab(k)})',`).join('\n')}
      },
      boxShadow: {
        'sn-sm': 'var(--sn-shadow-sm)',
        'sn-md': 'var(--sn-shadow-md)',
      },
      borderRadius: {
        'sn-card': '${t.radius.card}px',
        'sn-bubble': '${t.radius.bubble}px',
        'sn-input': '${t.radius.input}px',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        data: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
        display: ['Syne', 'Inter', 'system-ui', 'sans-serif'],
      },
      transitionTimingFunction: { sn: '${t.motion.easing}' },
      transitionDuration: {
        'sn-fast': '${t.motion.durationFastMs}ms',
        'sn-base': '${t.motion.durationBaseMs}ms',
      },
    },
  },
};
`;

const ktColor = (hex) => `Color(0xFF${hex.slice(1).toUpperCase()})`;
const ktObj = (theme) =>
  Object.entries(t.color[theme]).map(([k, v]) => `        val ${upper(k)} = ${ktColor(v)}`).join('\n');
const kt = `package com.guildofsmiths.trademesh.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** GENERATED by scripts/gen-tokens.mjs from design/tokens.json. Do not edit. */
object Tokens2 {
    object Light {
${ktObj('light')}
    }
    object Dark {
${ktObj('dark')}
    }
    val RadiusCard = ${t.radius.card}.dp
    val RadiusBubble = ${t.radius.bubble}.dp
    val RadiusOps = ${t.radius.ops}.dp
    const val DurationFastMs = ${t.motion.durationFastMs}
    const val DurationBaseMs = ${t.motion.durationBaseMs}
}
`;

const outputs = [
  ['desktop/portal/src/styles/tokens.css', css],
  ['desktop/portal/tailwind.tokens.cjs', preset],
  ['android/app/src/main/java/com/guildofsmiths/trademesh/ui/Tokens2.kt', kt],
];

const check = process.argv.includes('--check');
let stale = false;
for (const [rel, content] of outputs) {
  const abs = resolve(root, rel);
  if (check) {
    const current = existsSync(abs) ? readFileSync(abs, 'utf8') : '';
    if (current !== content) { console.error(`[stale] ${rel}`); stale = true; }
  } else {
    mkdirSync(dirname(abs), { recursive: true });
    writeFileSync(abs, content);
    console.log(`[wrote] ${rel}`);
  }
}
if (check && stale) { console.error('Run: node scripts/gen-tokens.mjs'); process.exit(1); }
if (check) console.log('tokens up to date');
```

- [ ] **Step 4: Generate and verify**

Run: `node scripts/gen-tokens.mjs && node scripts/gen-tokens.mjs --check`
Expected: three `[wrote]` lines, then `tokens up to date`

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd desktop/portal && npx vitest run src/__tests__/tokens.test.ts`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add scripts/gen-tokens.mjs desktop/portal/src/styles/tokens.css \
  desktop/portal/tailwind.tokens.cjs desktop/portal/src/__tests__/tokens.test.ts \
  android/app/src/main/java/com/guildofsmiths/trademesh/ui/Tokens2.kt
git commit -m "feat(design): token generator + portal/Android outputs with drift check

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Wire the portal — preset, tokens.css, index.html fix, PWA colors

**Files:**
- Modify: `desktop/portal/tailwind.config.js`
- Modify: `desktop/portal/index.html`
- Modify: `desktop/portal/vite.config.ts` (manifest `theme_color` / `background_color`)
- Modify: `desktop/portal/src/index.css` (import tokens.css at the top)
- Test: extend `desktop/portal/src/__tests__/tokens.test.ts`

**Interfaces:**
- Consumes: `tailwind.tokens.cjs` preset, `src/styles/tokens.css` (Task 2).
- Produces: Tailwind classes `bg-sn-bg-base`, `text-sn-ink`, `shadow-sn-sm`, `rounded-sn-card`, `font-data`, etc. available to every component; `index.html` boots light; PWA colors match tokens.

- [ ] **Step 1: Extend the test (failing first)**

Append to `desktop/portal/src/__tests__/tokens.test.ts`:

```ts
describe('portal wiring', () => {
  it('index.html boots light — no v1 dark body', () => {
    const html = readFileSync(resolve(repo, 'desktop/portal/index.html'), 'utf8');
    expect(html).not.toContain('#0a0a0a');
    expect(html).toContain(tokens.color.light.bgBase);
  });

  it('tailwind.config consumes the generated preset', () => {
    const cfg = readFileSync(resolve(repo, 'desktop/portal/tailwind.config.js'), 'utf8');
    expect(cfg).toContain("require('./tailwind.tokens.cjs')");
  });

  it('PWA manifest colors come from tokens', () => {
    const vite = readFileSync(resolve(repo, 'desktop/portal/vite.config.ts'), 'utf8');
    expect(vite).toContain(`theme_color: '${tokens.color.light.accent}'`);
    expect(vite).toContain(`background_color: '${tokens.color.light.bgBase}'`);
  });
});
```

Run: `cd desktop/portal && npx vitest run src/__tests__/tokens.test.ts`
Expected: FAIL (3 new tests)

- [ ] **Step 2: Modify `tailwind.config.js`** — add the preset line above `content:` (keep every existing `console-*` value untouched for v1 consumers):

```js
/** @type {import('tailwindcss').Config} */
module.exports = {
  presets: [require('./tailwind.tokens.cjs')],
  content: [
```

- [ ] **Step 3: Fix `index.html`** — replace the `<style>` body block:

```html
    <style>
      * {
        margin: 0;
        padding: 0;
        box-sizing: border-box;
      }
      body {
        font-family: Inter, system-ui, sans-serif;
        background: #F7F8FA;
        color: #1C2128;
      }
      @media (prefers-color-scheme: dark) {
        body { background: #14171C; color: #E9ECF1; }
      }
    </style>
```

(These four literals are the boot-flash guard and are allowed raw — they must equal `color.light.bgBase/ink` and `color.dark.bgBase/ink`; the test asserts the light pair.)

- [ ] **Step 4: PWA colors in `vite.config.ts`** — inside `manifest: {`, set (replacing any existing values):

```ts
        theme_color: '#2F5FE8',
        background_color: '#F7F8FA',
```

- [ ] **Step 5: Import tokens.css** — first line of `desktop/portal/src/index.css`:

```css
@import './styles/tokens.css';
```

- [ ] **Step 6: Verify tests + build**

Run: `cd desktop/portal && npx vitest run src/__tests__/tokens.test.ts && npm run build`
Expected: PASS (7 tests), build completes with no errors

- [ ] **Step 7: Commit**

```bash
git add desktop/portal/tailwind.config.js desktop/portal/index.html \
  desktop/portal/vite.config.ts desktop/portal/src/index.css \
  desktop/portal/src/__tests__/tokens.test.ts
git commit -m "feat(portal): wire v2 tokens; fix dark-body boot flash + PWA colors

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Portal font pipeline (Inter, JetBrains Mono, Syne — self-hosted)

**Files:**
- Modify: `desktop/portal/package.json` (via npm install)
- Modify: `desktop/portal/src/index.css`

**Interfaces:**
- Consumes: `fontFamily` mapping from the preset (Task 2): `sans` = Inter, `data` = JetBrains Mono, `display` = Syne.
- Produces: fonts served from the app bundle (no external URL imports — remove any `@import url(...)` font lines found in `index.css`).

- [ ] **Step 1: Install fontsource packages**

Run: `cd desktop/portal && npm install @fontsource/inter @fontsource/jetbrains-mono @fontsource/syne`
Expected: three packages added to dependencies

- [ ] **Step 2: Swap imports in `src/index.css`** — remove every existing `@import url('https://fonts.googleapis.com/...')` line (grep first: `grep -n "fonts.googleapis" src/index.css`), then add directly below the tokens import:

```css
@import '@fontsource/inter/400.css';
@import '@fontsource/inter/500.css';
@import '@fontsource/inter/600.css';
@import '@fontsource/jetbrains-mono/400.css';
@import '@fontsource/jetbrains-mono/500.css';
@import '@fontsource/syne/600.css';
@import '@fontsource/syne/700.css';
```

- [ ] **Step 3: Verify no external font URLs remain and build passes**

Run: `cd desktop/portal && ! grep -R "fonts.googleapis" src/ index.html && npm run build`
Expected: grep finds nothing (command succeeds), build completes

- [ ] **Step 4: Run the full portal test suite (regression)**

Run: `cd desktop/portal && npx vitest run`
Expected: all existing tests + 7 token tests PASS

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/package.json desktop/portal/package-lock.json desktop/portal/src/index.css
git commit -m "feat(portal): self-host Inter/JetBrains Mono/Syne via fontsource

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Android — Inter font + Tokens2 compile verification

**Files:**
- Create: `android/app/src/main/res/font/inter_variable.ttf` (downloaded)
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConsoleTheme.kt` (add `inter` FontFamily alongside existing ones — do not remove `plexSans` yet; v1 consumers still use it)

**Interfaces:**
- Consumes: `Tokens2.kt` (Task 2) — must compile as part of the app module.
- Produces: `ConsoleTheme.inter: FontFamily` for Plans 2+ to consume; `R.font.inter_variable` resource.

- [ ] **Step 1: Download Inter variable font**

Run:
```bash
curl -L -o android/app/src/main/res/font/inter_variable.ttf \
  "https://github.com/google/fonts/raw/main/ofl/inter/Inter%5Bopsz%2Cwght%5D.ttf"
ls -la android/app/src/main/res/font/inter_variable.ttf
```
Expected: file present, ~800KB. (If the URL 404s because google/fonts moved the file, find the current path with `curl -s https://api.github.com/repos/google/fonts/contents/ofl/inter | grep '"name"'` and use that ttf.)

- [ ] **Step 2: Add the FontFamily to `ConsoleTheme.kt`** — directly below the `plexMono` val:

```kotlin
    val inter = FontFamily(
        Font(R.font.inter_variable, FontWeight.Normal),
        Font(R.font.inter_variable, FontWeight.Medium),
        Font(R.font.inter_variable, FontWeight.SemiBold),
    )
```

- [ ] **Step 3: Compile (proves Tokens2.kt + font resource are valid)**

Run: `cd android && ./gradlew :app:compileDebugKotlin --console=plain -q`
Expected: BUILD SUCCESSFUL (Tokens2.kt from Task 2 compiles in this step too)

- [ ] **Step 4: Run Android unit tests (regression)**

Run: `cd android && ./gradlew :app:testDebugUnitTest --console=plain -q`
Expected: BUILD SUCCESSFUL, no failing tests

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/res/font/inter_variable.ttf \
  android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConsoleTheme.kt
git commit -m "feat(android): bundle Inter variable font; Tokens2 compiles

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Portal CI + token drift guard

**Files:**
- Create: `.github/workflows/portal-ci.yml`

**Interfaces:**
- Consumes: `gen-tokens.mjs --check` (Task 2), portal vitest suite.
- Produces: CI gate on every PR touching `desktop/portal/**` or `design/**`. (Full no-raw-hex enforcement activates in Plan 5 when the screen sweep finishes; until then the guard is drift-only — do not add a hex grep here yet.)

- [ ] **Step 1: Write `.github/workflows/portal-ci.yml`**

```yaml
name: portal CI

on:
  push:
    branches: [master, main]
    paths:
      - 'desktop/portal/**'
      - 'design/**'
      - 'scripts/gen-tokens.mjs'
  pull_request:
    paths:
      - 'desktop/portal/**'
      - 'design/**'
      - 'scripts/gen-tokens.mjs'

jobs:
  tokens-and-tests:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: desktop/portal
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: desktop/portal/package-lock.json
      - name: Install dependencies
        run: npm ci
      - name: Token drift check
        working-directory: .
        run: node scripts/gen-tokens.mjs --check
      - name: Unit tests
        run: npx vitest run
      - name: Build
        run: npm run build
```

- [ ] **Step 2: Validate the workflow locally where possible**

Run: `node scripts/gen-tokens.mjs --check && cd desktop/portal && npx vitest run && npm run build`
Expected: `tokens up to date`, all tests PASS, build completes

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/portal-ci.yml
git commit -m "ci(portal): vitest + build + token drift gate

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

- [ ] **Step 4: Push and confirm CI is green**

Run: `git push origin master && gh run watch $(gh run list --workflow "portal CI" --limit 1 --json databaseId -q '.[0].databaseId') --exit-status`
Expected: portal CI run completes with success (backend CI will also fire if backend files changed — it should stay green)

---

## Self-Review

- Spec coverage (Plan 1 scope = workstreams 1-2): tokens.json (spec section 1-2) — Task 1/2; generators + drift guard (section 1, success criterion) — Task 2/6; index.html + PWA fix (section 10.1) — Task 3; fonts both platforms (section 3, 10.2) — Tasks 4-5; GLYPHS.md (section 4) — Task 1. Sections 5-9 (moods, components, mechanics, layout) are Plans 2-5 by design; skills rewrite is Plan 6/workstream 7.
- Placeholders: none — every step has literal file content or exact commands.
- Type consistency: CSS var names (`--sn-bg-base` pattern), Tailwind keys (`sn-bg-base`), and Kotlin names (`Tokens2.Light.BgBase`) all derive from the same JSON keys via kebab/UpperCamel rules stated in Task 2, and the tests in Task 2/3 pin exact strings.
