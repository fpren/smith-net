import { SummaryArtifact } from './types';
import { sha256HexGated } from './sha256Gate';

const ABI = 0x01;
const FORMAT_V2 = 0x02;

/** The ledger hash format version stamped on new seals; matches FORMAT_V2 / computeHashV2. */
export const CURRENT_HASH_VERSION = 2;

function encStr(s: string): Buffer {
  const b = Buffer.from(s, 'utf8');
  const len = Buffer.allocUnsafe(4);
  len.writeUInt32LE(b.length, 0);
  return Buffer.concat([len, b]);
}
function encStrArray(arr: string[]): Buffer {
  const count = Buffer.allocUnsafe(4);
  count.writeUInt32LE(arr.length, 0);
  return Buffer.concat([count, ...arr.map(encStr)]);
}
function encI64(v: bigint): Buffer {
  const b = Buffer.allocUnsafe(8);
  b.writeBigInt64LE(v, 0);
  return b;
}
function sortedByUtf8(arr: string[]): string[] {
  return [...arr].sort((a, b) =>
    Buffer.compare(Buffer.from(a, 'utf8'), Buffer.from(b, 'utf8')));
}

/**
 * v2 canonical encoding of a SummaryArtifact (see the M2 design spec).
 * Self-describing header "SMC" + abi + format, then fixed field order; strings
 * length-prefixed UTF-8, arrays count-prefixed, money/hours as integer minor
 * units (no floating point in the hashed bytes), id sets sorted by utf-8 bytes.
 */
export function encodeLedgerArtifactV2(a: SummaryArtifact): Buffer {
  const header = Buffer.from([0x53, 0x4d, 0x43, ABI, FORMAT_V2]); // "SMC"
  // Synthesizer pre-quantizes both fields to 2 decimal places (Math.round(x*100)/100),
  // so any residual IEEE epsilon is smaller than 0.005 and Math.round resolves it to
  // the same integer on all hosts. Non-quantized inputs are not a production path.
  const cents = BigInt(Math.round(a.totalCost * 100));
  const centihours = BigInt(Math.round(a.totalHours * 100));
  return Buffer.concat([
    header,
    encStr(a.serial),
    encStr(a.intentVersionId),
    encStr(a.scopeStatement),
    encStrArray(a.workPerformed),
    encStrArray(a.laborRecorded),
    encStrArray(a.materialsUsed),
    encStrArray(a.contextualNotes),
    encI64(cents),
    encI64(centihours),
    encStrArray(sortedByUtf8(a.jobIds)),
    encStrArray(sortedByUtf8(a.timeEntryIds)),
    encStrArray(sortedByUtf8(a.chatMessageIds)),
  ]);
}

export function ledgerHashV2(a: SummaryArtifact): string {
  return sha256HexGated(encodeLedgerArtifactV2(a));
}
