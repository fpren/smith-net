import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';
import { Tier, TIER_CODE, CAPS_BY_TIER, encodeEntitlementsRecordLocal } from '../src/entitlements';

const tiers: Tier[] = ['open', 'solo', 'advanced', 'enterprise'];
const vectors = tiers.map((tier) => {
  const tierCode = TIER_CODE[tier];
  const bitmask = CAPS_BY_TIER[tier];
  // Deliberate: use the host fallback directly so the golden is never gated on
  // SMITHCORE_ENABLED at generation time. The parity test proves the ROM path
  // produces identical bytes.
  const record = encodeEntitlementsRecordLocal(tierCode, bitmask);
  return {
    tier, tierCode, bitmask,
    recordHex: record.toString('hex'),
    hashHex: crypto.createHash('sha256').update(record).digest('hex'),
  };
});
const json = JSON.stringify({ vectors }, null, 2) + '\n';
for (const p of [
  path.resolve(__dirname, '../../core/testdata/entitlements-golden.json'),
  path.resolve(__dirname, '../../android/app/src/androidTest/assets/entitlements-golden.json'),
]) {
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, json);
}
console.log(`wrote ${vectors.length} entitlements vectors`);
