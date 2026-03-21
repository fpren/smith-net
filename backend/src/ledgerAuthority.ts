import { SummaryArtifact, LedgerEntry, LedgerAuthorityResult } from './types';
import * as crypto from 'crypto';

export function validateSealing(artifact: SummaryArtifact): LedgerAuthorityResult {
  if (!artifact.serial) {
    return { valid: false, message: 'Artifact must have a valid serial number' };
  }
  if (!artifact.id) {
    return { valid: false, message: 'Artifact must have a valid ID' };
  }
  const hash = computeHash(artifact);
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

export function computeHash(artifact: SummaryArtifact): string {
  const canonical = JSON.stringify({
    serial: artifact.serial,
    intentVersionId: artifact.intentVersionId,
    scopeStatement: artifact.scopeStatement,
    workPerformed: artifact.workPerformed,
    laborRecorded: artifact.laborRecorded,
    totalHours: artifact.totalHours,
    totalCost: artifact.totalCost,
    jobIds: artifact.jobIds.sort(),
    timeEntryIds: artifact.timeEntryIds.sort(),
  });
  return crypto.createHash('sha256').update(canonical).digest('hex');
}
