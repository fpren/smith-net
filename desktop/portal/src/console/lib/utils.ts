// desktop/portal/src/console/lib/utils.ts
//
// Visual-lift helpers — ported from the dashboard module so its UI
// primitives work in the console.

import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/** Tailwind class merger. */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

/** First letter(s) from a name. "James Park" -> "JP", "Alice" -> "A". */
export function initials(name: string): string {
  if (!name) return '';
  return name
    .split(/\s+/)
    .map((p) => p[0])
    .filter(Boolean)
    .join('')
    .slice(0, 2)
    .toUpperCase();
}

/** Darken a hex color for gradient endpoints. Returns rgb(...) string. */
export function darkenHex(hex: string, amount = 28): string {
  const h = hex.replace('#', '');
  const r = Math.max(0, parseInt(h.slice(0, 2), 16) - amount);
  const g = Math.max(0, parseInt(h.slice(2, 4), 16) - amount);
  const b = Math.max(0, parseInt(h.slice(4, 6), 16) - amount);
  return `rgb(${r},${g},${b})`;
}

/** Pseudo-random but deterministic accent color from a string id. Used by
 *  Avatar so each user gets a stable color without a backend field. */
const ACCENT_PALETTE = [
  '#9A6F2E', // gold (accent)
  '#5A8C76', // sage (ok)
  '#8C5A2E', // sienna (warn)
  '#8C3A3A', // brick (danger)
  '#6B4F8C', // muted purple
  '#3A6E8C', // dusty blue
  '#8C7E3A', // olive
];

export function accentForId(id: string): string {
  let hash = 0;
  for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) | 0;
  return ACCENT_PALETTE[Math.abs(hash) % ACCENT_PALETTE.length];
}
