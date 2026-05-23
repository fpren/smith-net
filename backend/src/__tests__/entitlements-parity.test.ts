import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';
import { initSmithCore, entitlementsEncode } from '../core/smithCore';
import {
  packEntitlementsInput, encodeEntitlementsRecordLocal, entitlementsHash,
  CAPS_BY_TIER, TIER_CODE,
} from '../entitlements';
import { roleToTier, resolveEntitlements } from '../tierResolver';

const goldenPath = path.resolve(__dirname, '../../../core/testdata/entitlements-golden.json');
const androidGoldenPath = path.resolve(
  __dirname, '../../../android/app/src/androidTest/assets/entitlements-golden.json');
const golden = JSON.parse(fs.readFileSync(goldenPath, 'utf8'));

beforeAll(async () => { await initSmithCore(); });

describe('M4: entitlements bitmask + hash parity', () => {
  const prevFlag = process.env.SMITHCORE_ENABLED;
  afterAll(() => {
    if (prevFlag === undefined) delete process.env.SMITHCORE_ENABLED;
    else process.env.SMITHCORE_ENABLED = prevFlag;
  });

  it('CAPS_BY_TIER matches the spec bitmasks', () => {
    expect(CAPS_BY_TIER.open).toBe(0);
    expect(CAPS_BY_TIER.solo).toBe(3);
    expect(CAPS_BY_TIER.advanced).toBe(15);
    expect(CAPS_BY_TIER.enterprise).toBe(63);
  });

  it('C core encode == golden recordHex == host fallback; hash == golden == node', () => {
    process.env.SMITHCORE_ENABLED = '1';
    try {
      for (const v of golden.vectors) {
        const c = entitlementsEncode(packEntitlementsInput(v.tierCode, v.bitmask));
        expect(`${v.tier}:${c.toString('hex')}`).toBe(`${v.tier}:${v.recordHex}`);
        expect(c.equals(encodeEntitlementsRecordLocal(v.tierCode, v.bitmask))).toBe(true);
        expect(`${v.tier}:${entitlementsHash(v.tierCode, v.bitmask)}`).toBe(`${v.tier}:${v.hashHex}`);
        expect(crypto.createHash('sha256').update(Buffer.from(v.recordHex, 'hex')).digest('hex'))
          .toBe(v.hashHex);
      }
    } finally {
      if (prevFlag === undefined) delete process.env.SMITHCORE_ENABLED;
      else process.env.SMITHCORE_ENABLED = prevFlag;
    }
  });

  it('roleToTier maps every role (+ unknown -> open)', () => {
    expect(roleToTier('solo')).toBe('solo');
    expect(roleToTier('team')).toBe('solo');
    expect(roleToTier('lead')).toBe('advanced');
    expect(roleToTier('foreman')).toBe('advanced');
    expect(roleToTier('enterprise')).toBe('enterprise');
    expect(roleToTier('admin')).toBe('enterprise');
    expect(roleToTier('nope')).toBe('open');
  });

  it('resolveEntitlements composes tier + bitmask + hash (tier-based)', () => {
    const e = resolveEntitlements('advanced');
    expect(e.tier).toBe('advanced');
    expect(e.bitmask).toBe(15);
    expect(e.entitlementsHash).toBe(entitlementsHash(TIER_CODE.advanced, 15));
  });

  it('drift guard: android golden copy is byte-identical', () => {
    expect(fs.readFileSync(goldenPath).equals(fs.readFileSync(androidGoldenPath))).toBe(true);
  });
});
