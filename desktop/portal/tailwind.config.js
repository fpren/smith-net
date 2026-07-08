/** @type {import('tailwindcss').Config} */
module.exports = {
  presets: [require('./tailwind.tokens.cjs')],
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        'console-bg': '#F4F2EE',
        'console-surface': '#FAFAF8',
        'console-border': '#E8E4DE',
        'console-text': '#2A2520',
        'console-text-muted': '#5C5347',
        'console-accent': '#9A6F2E',
        'console-ok': '#5A8C76',
        'console-warn': '#8C5A2E',
        'console-danger': '#8C3A3A',
      },
      fontFamily: {
        // Visual lift (2026-05-16): ported from dashboard module.
        // Body: Inter. Display: Syne. Mono: JetBrains Mono.
        // Self-hosted via fontsource (Task 4, 2026-07-08).
        sans: ['Inter', 'system-ui', 'sans-serif'],
        display: ['Syne', 'Inter', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
    },
  },
  plugins: [],
};
