import crypto from 'crypto';

// Excludes 0/O/1/I/L to avoid hand-typed confusion. 32 symbols, 8 chars
// = 32^8 ≈ 1.1e12 distinct codes — plenty for short-lived single-use invites.
const ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

export function generateInviteCode(length: number = 8): string {
  let out = '';
  for (let i = 0; i < length; i++) {
    out += ALPHABET[crypto.randomInt(0, ALPHABET.length)];
  }
  return out;
}
