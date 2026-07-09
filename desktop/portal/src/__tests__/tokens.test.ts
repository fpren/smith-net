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

  it('tailwind preset carries the ops radius token', () => {
    const preset = readFileSync(resolve(repo, 'desktop/portal/tailwind.tokens.cjs'), 'utf8');
    expect(preset).toContain(`'sn-ops': '${tokens.radius.ops}px'`);
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

describe('avatar palette', () => {
  it('tokens.css exposes theme-invariant avatar vars', () => {
    const css = readFileSync(resolve(__dirname, '../styles/tokens.css'), 'utf8');
    expect(css).toContain('--sn-avatar-a1: #2F5FE8');
    expect(css).toContain('--sn-avatar-a6: #C2417E');
  });

  it('tailwind preset exposes avatar colors', () => {
    const preset = readFileSync(resolve(repo, 'desktop/portal/tailwind.tokens.cjs'), 'utf8');
    expect(preset).toContain("'sn-avatar-a1'");
    expect(preset).toContain("'sn-avatar-a6'");
  });

  it('Tokens2.kt carries the Avatar object and AvatarPalette list', () => {
    const kt = readFileSync(
      resolve(repo, 'android/app/src/main/java/com/guildofsmiths/trademesh/ui/Tokens2.kt'), 'utf8');
    expect(kt).toContain('object Avatar');
    expect(kt).toContain('Color(0xFF2F5FE8)');
    expect(kt).toContain('Color(0xFFC2417E)');
    expect(kt).toContain('val AvatarPalette = listOf(');
  });
});

describe('portal wiring', () => {
  it('index.html boots light — no v1 dark body', () => {
    const html = readFileSync(resolve(repo, 'desktop/portal/index.html'), 'utf8');
    expect(html).not.toContain('#0a0a0a');
    expect(html).toContain(tokens.color.light.bgBase);
    expect(html).toContain(tokens.color.light.ink);
    expect(html).toContain(tokens.color.dark.bgBase);
    expect(html).toContain(tokens.color.dark.ink);
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
