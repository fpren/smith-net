/** @type {import('tailwindcss').Config} */
module.exports = {
  presets: [require('./tailwind.tokens.cjs')],
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
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
