import { IntentVersion, SummaryArtifact, SynthesisAuthorityResult } from './types';

export function validateSynthesisInputs(
  intentVersion: IntentVersion,
  jobIds: string[],
  timeEntryIds: string[]
): SynthesisAuthorityResult {
  if (intentVersion.status !== 'confirmed') {
    return { valid: false, message: `Intent version must be confirmed. Current: ${intentVersion.status}` };
  }
  if (jobIds.length === 0) {
    return { valid: false, message: 'At least one closed Job ID is required' };
  }
  if (timeEntryIds.length === 0) {
    return { valid: false, message: 'At least one closed Time entry ID is required' };
  }
  return { valid: true, message: 'Synthesis inputs validated' };
}

export function validateArtifact(artifact: SummaryArtifact): SynthesisAuthorityResult {
  if (!artifact.serial) {
    return { valid: false, message: 'Artifact must have a serial number' };
  }
  if (!artifact.scopeStatement || artifact.scopeStatement.trim().length === 0) {
    return { valid: false, message: 'Artifact must contain a scope statement' };
  }
  if (!artifact.intentVersionId) {
    return { valid: false, message: 'Artifact must reference an Intent version' };
  }
  if (artifact.jobIds.length === 0) {
    return { valid: false, message: 'Artifact must reference explicit Job IDs' };
  }
  if (artifact.timeEntryIds.length === 0) {
    return { valid: false, message: 'Artifact must reference explicit Time entry IDs' };
  }
  return { valid: true, message: 'Artifact validated', artifact };
}
