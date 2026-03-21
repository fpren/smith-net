import { IntentVersion, IntentAuthorityResult } from './types';

export function validateIntentCreation(
  scopeStatement: string,
  parties: string[]
): IntentAuthorityResult {
  if (!scopeStatement || scopeStatement.trim().length === 0) {
    return { valid: false, message: 'Scope statement is required' };
  }
  if (!parties || parties.length === 0) {
    return { valid: false, message: 'At least one party UUID is required' };
  }
  return { valid: true, message: 'Intent creation validated' };
}

export function validateIntentConfirmation(
  version: IntentVersion,
  confirmerId: string
): IntentAuthorityResult {
  if (version.status !== 'proposed') {
    return { valid: false, message: `Cannot confirm Intent in status: ${version.status}. Must be 'proposed'.` };
  }
  if (!confirmerId) {
    return { valid: false, message: 'Confirmation requires a human actor UUID. AI cannot confirm Intent.' };
  }
  return { valid: true, message: 'Intent confirmation validated', intentVersion: version };
}

export function validateIntentVersion(
  newVersion: Partial<IntentVersion>,
  priorVersion: IntentVersion
): IntentAuthorityResult {
  if (!newVersion.supersedes || newVersion.supersedes !== priorVersion.id) {
    return { valid: false, message: 'New version must reference the prior version it supersedes' };
  }
  if (priorVersion.status === 'superseded') {
    return { valid: false, message: 'Cannot supersede an already-superseded version' };
  }
  return { valid: true, message: 'Intent version chain validated' };
}
