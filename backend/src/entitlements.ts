import { isSmithCoreReady, entitlementsEncode } from './core/smithCore';
import { sha256HexGated } from './sha256Gate';

export type Tier = 'open' | 'solo' | 'advanced' | 'enterprise';

// Append-only bit registry -- never renumber or reuse a freed bit.
export const ENTITLEMENT_BITS = {
  plan_compiler: 0,
  cord_state_model: 1,
  smithai_on_device: 2,
  advanced_template: 3,
  enterprise_template: 4,
  crew_multiuser: 5,
} as const;

export const TIER_CODE: Record<Tier, number> = { open: 0, solo: 1, advanced: 2, enterprise: 3 };

const B = ENTITLEMENT_BITS;
export const CAPS_BY_TIER: Record<Tier, number> = {
  open: 0,
  solo:       (1 << B.plan_compiler) | (1 << B.cord_state_model),
  advanced:   (1 << B.plan_compiler) | (1 << B.cord_state_model) | (1 << B.smithai_on_device) | (1 << B.advanced_template),
  enterprise: (1 << B.plan_compiler) | (1 << B.cord_state_model) | (1 << B.smithai_on_device) | (1 << B.advanced_template) | (1 << B.enterprise_template) | (1 << B.crew_multiuser),
};

function coreActive(): boolean {
  return process.env.SMITHCORE_ENABLED === '1' && isSmithCoreReady();
}

/** Host-packed input for sc_entitlements_encode: [u8 tierCode][u32 bitmask LE]. */
export function packEntitlementsInput(tierCode: number, bitmask: number): Buffer {
  const b = Buffer.allocUnsafe(5);
  b.writeUInt8(tierCode, 0);
  b.writeUInt32LE(bitmask >>> 0, 1);
  return b;
}

/** Host fallback / parity reference: [0x01][tierCode][u32 bitmask LE]. */
export function encodeEntitlementsRecordLocal(tierCode: number, bitmask: number): Buffer {
  const b = Buffer.allocUnsafe(6);
  b.writeUInt8(0x01, 0);
  b.writeUInt8(tierCode, 1);
  b.writeUInt32LE(bitmask >>> 0, 2);
  return b;
}

/** Canonical record: ROM-backed when enabled+ready, else host fallback (identical bytes). */
export function encodeEntitlementsRecord(tierCode: number, bitmask: number): Buffer {
  return coreActive()
    ? entitlementsEncode(packEntitlementsInput(tierCode, bitmask))
    : encodeEntitlementsRecordLocal(tierCode, bitmask);
}

export function entitlementsHash(tierCode: number, bitmask: number): string {
  return sha256HexGated(encodeEntitlementsRecord(tierCode, bitmask));
}
