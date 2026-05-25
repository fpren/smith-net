/*
 * ledgerCanonical.ts -- pure byte packers for the SmithCore ROM browser host.
 * Mirrors backend/src/ledgerCanonical.ts (packLedgerInput) but uses
 * DataView/TextEncoder instead of Node Buffer. No wasm here. The packed buffer
 * is the input to sc_ledger_encode (see core/include/smithcore.h): v2 field
 * order WITHOUT the header, with the three id arrays left UNSORTED (the core
 * sorts them). The single Math.round is the host's one allowed float op; the
 * core is float-free.
 */

export interface LedgerArtifactInput {
  serial: string;
  intentVersionId: string;
  scopeStatement: string;
  workPerformed: string[];
  laborRecorded: string[];
  materialsUsed: string[];
  contextualNotes: string[];
  totalCost: number;
  totalHours: number;
  jobIds: string[];
  timeEntryIds: string[];
  chatMessageIds: string[];
}

const _utf8 = new TextEncoder();

function concat(parts: Uint8Array[]): Uint8Array {
  let n = 0;
  for (const p of parts) n += p.length;
  const out = new Uint8Array(n);
  let off = 0;
  for (const p of parts) {
    out.set(p, off);
    off += p.length;
  }
  return out;
}

function u32le(v: number): Uint8Array {
  const b = new Uint8Array(4);
  new DataView(b.buffer).setUint32(0, v >>> 0, true);
  return b;
}

function encStr(s: string): Uint8Array {
  const bytes = _utf8.encode(s);
  return concat([u32le(bytes.length), bytes]);
}

function encStrArray(arr: string[]): Uint8Array {
  return concat([u32le(arr.length), ...arr.map(encStr)]);
}

function encI64(v: bigint): Uint8Array {
  const b = new Uint8Array(8);
  new DataView(b.buffer).setBigInt64(0, v, true);
  return b;
}

/** Pack a SummaryArtifact-shaped value into the sc_ledger_encode input buffer. */
export function packLedgerInput(a: LedgerArtifactInput): Uint8Array {
  const cents = BigInt(Math.round(a.totalCost * 100));
  const centihours = BigInt(Math.round(a.totalHours * 100));
  return concat([
    encStr(a.serial),
    encStr(a.intentVersionId),
    encStr(a.scopeStatement),
    encStrArray(a.workPerformed),
    encStrArray(a.laborRecorded),
    encStrArray(a.materialsUsed),
    encStrArray(a.contextualNotes),
    encI64(cents),
    encI64(centihours),
    encStrArray(a.jobIds),
    encStrArray(a.timeEntryIds),
    encStrArray(a.chatMessageIds),
  ]);
}

/** Pack the entitlements record input: [u8 tierCode][u32 bitmask LE]. */
export function packEntitlements(tierCode: number, bitmask: number): Uint8Array {
  const out = new Uint8Array(5);
  out[0] = tierCode & 0xff;
  new DataView(out.buffer).setUint32(1, bitmask >>> 0, true);
  return out;
}
