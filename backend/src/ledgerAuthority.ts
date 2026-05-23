import { SummaryArtifact, LedgerEntry, LedgerAuthorityResult } from './types';
import * as crypto from 'crypto';
import { ledgerHashV2 } from './ledgerCanonical';

export function validateSealing(artifact: SummaryArtifact): LedgerAuthorityResult {
  if (!artifact.serial) {
    return { valid: false, message: 'Artifact must have a valid serial number' };
  }
  if (!artifact.id) {
    return { valid: false, message: 'Artifact must have a valid ID' };
  }
  const hash = computeHashV2(artifact);
  if (!hash) {
    return { valid: false, message: 'Failed to compute SHA-256 hash for artifact' };
  }
  return { valid: true, message: 'Sealing validated' };
}

export function validateAmendment(
  newArtifact: SummaryArtifact, priorEntry: LedgerEntry
): LedgerAuthorityResult {
  if (!priorEntry.id) {
    return { valid: false, message: 'Prior Ledger entry must exist' };
  }
  if (priorEntry.supersededBy) {
    return { valid: false, message: 'Prior entry already superseded — cannot amend twice' };
  }
  return { valid: true, message: 'Amendment validated' };
}

/** v1 legacy hash (float-bearing canonical JSON). Frozen so pre-M2 entries
 *  keep verifying byte-for-byte. Do NOT change this function. */
export function computeHashV1(artifact: SummaryArtifact): string {
  const canonical = JSON.stringify({
    serial: artifact.serial,
    intentVersionId: artifact.intentVersionId,
    scopeStatement: artifact.scopeStatement,
    workPerformed: artifact.workPerformed,
    laborRecorded: artifact.laborRecorded,
    totalHours: artifact.totalHours,
    totalCost: artifact.totalCost,
    jobIds: [...artifact.jobIds].sort(),
    timeEntryIds: [...artifact.timeEntryIds].sort(),
  });
  return crypto.createHash('sha256').update(canonical).digest('hex');
}

/** v2 hash: canonical byte encoding through the ROM SHA-256. */
export function computeHashV2(artifact: SummaryArtifact): string {
  return ledgerHashV2(artifact);
}

export function computeHashForVersion(artifact: SummaryArtifact, version: number): string {
  if (version === 1) return computeHashV1(artifact);
  if (version === 2) return computeHashV2(artifact);
  throw new Error(`unknown ledger hash_version ${version}`);
}

export interface LedgerVerifyResult {
  valid: boolean;
  expected: string;
  actual: string;
  hashVersion: number;
}

/** Pure tamper check: recompute the entry's hash under its stored version. */
export function verifyHash(entry: LedgerEntry, artifact: SummaryArtifact): LedgerVerifyResult {
  const hashVersion = entry.hashVersion ?? 1;
  const actual = computeHashForVersion(artifact, hashVersion);
  return { valid: entry.sha256Hash === actual, expected: entry.sha256Hash, actual, hashVersion };
}
