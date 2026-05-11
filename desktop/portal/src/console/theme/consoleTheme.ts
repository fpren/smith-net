/**
 * Console design tokens — keep in sync with tailwind.config.js colors.
 * Components reference these tokens via Tailwind classes (e.g. `bg-console-surface`)
 * OR via this object when they need the raw value (rare — usually for inline styles).
 */

export const consoleTheme = {
  colors: {
    bg: '#F4F2EE',
    surface: '#FAFAF8',
    border: '#E8E4DE',
    text: '#2A2520',
    textMuted: '#5C5347',
    accent: '#9A6F2E',
    ok: '#5A8C76',
    warn: '#8C5A2E',
    danger: '#8C3A3A',
  },
  glyphs: {
    online: '((+))',
    offline: '(( ))',
    error: '[ERR]',
    ok: '[OK]',
    warn: '[!]',
    arrow: '->',
    bullet: '*',
  },
  spacing: {
    xs: '0.25rem',
    sm: '0.5rem',
    md: '1rem',
    lg: '1.5rem',
    xl: '2rem',
  },
} as const;

export type ConsoleTheme = typeof consoleTheme;
