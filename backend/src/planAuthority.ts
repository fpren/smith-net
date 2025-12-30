/**
 * PLAN AUTHORITY — MASTER VALIDATION SYSTEM
 * ==========================================
 *
 * ROLE: Plan Authority
 * OBJECTIVE: Validate and enforce system law for Plan creation and finalization
 *
 * This system enforces absolute immutability and data integrity.
 * No suggestions, no assistance, no improvisation — only validation.
 */

import crypto from 'crypto';
import { v4 as uuidv4 } from 'uuid';
import {
  Plan,
  PlanSummary,
  TimeEntry,
  Engagement,
  PlanSerial,
  PlanValidationResult,
  LedgerTransaction,
  ActorBlock
} from './types';

export type ValidationRule =
  | 'PRECONDITION_1_JOBS_CLOSED'
  | 'PRECONDITION_2_TIME_CLOSED'
  | 'PRECONDITION_3_NO_MUTABILITY'
  | 'PRECONDITION_4_CLOCKOUT_NOTES_IMMUTABLE'
  | 'PRECONDITION_5_CHAT_READONLY'
  | 'PRECONDITION_6_SERIALS_CANONICAL'
  | 'PRECONDITION_7_HASHES_COMPUTED'
  | 'PRECONDITION_8_LEDGER_WRITTEN'
  | 'PRECONDITION_9_LEDGER_VERIFIABLE'
  | 'CONTENT_1_INTENT_CLEAR'
  | 'CONTENT_2_SCOPE_EXPLICIT'
  | 'CONTENT_3_SUMMARY_DERIVED'
  | 'CONTENT_4_ATTRIBUTION_UUID'
  | 'CONTENT_5_SERIAL_GENERATED'
  | 'CONTENT_6_CRYPTO_COMMITMENT'
  | 'FINALIZATION_1_STATE_TRANSITION'
  | 'FINALIZATION_2_IMMUTABILITY'
  | 'FINALIZATION_3_NO_EDITS'
  | 'FINALIZATION_4_NEW_PLAN_REQUIRED'
  | 'FINALIZATION_5_OUTPUT_ELIGIBLE'
  | 'OUTPUT_1_AUTHORIZED_TYPES'
  | 'OUTPUT_2_NO_PLAN_MODIFICATION'
  | 'OUTPUT_3_SNAPSHOT_ONLY'
  | 'ARCHIVE_1_APPEND_ONLY'
  | 'ARCHIVE_2_NO_DELETION'
  | 'ARCHIVE_3_ADDITIVE_SUPERSESSION'
  | 'ARCHIVE_4_SERIAL_REFERENCE';

export class PlanAuthority {

  /**
   * VALIDATE PLAN CREATION REQUEST
   * Returns binary validation result - no suggestions or assistance
   */
  async validatePlanCreation(
    engagement: Engagement,
    planRequest: {
      name: string;
      intent: string;
      jobIds: string[];
      timeEntryIds: string[];
      clientUuid: string;
      workerUuids: string[];
      foremanUuid?: string;
      latitude?: number;
      longitude?: number;
      verticalUnitId?: string;
      environmentalContext?: string;
    }
  ): Promise<PlanValidationResult> {

    // PRECONDITION VALIDATION
    const preconditionCheck = await this.validatePreconditions(planRequest.jobIds, planRequest.timeEntryIds);
    if (!preconditionCheck.valid) {
      return {
        valid: false,
        rejectionReason: preconditionCheck.rule,
        message: `PLAN REJECTED — SYSTEM LAW VIOLATION: ${preconditionCheck.rule}`
      };
    }

    // CONTENT VALIDATION
    const contentCheck = await this.validatePlanContent(planRequest);
    if (!contentCheck.valid) {
      return {
        valid: false,
        rejectionReason: contentCheck.rule,
        message: `PLAN REJECTED — SYSTEM LAW VIOLATION: ${contentCheck.rule}`
      };
    }

    // SERIAL GENERATION
    const planSerial = this.generatePlanSerial(planRequest, engagement);

    // CRYPTOGRAPHIC COMMITMENT
    const ledgerTx = await this.createLedgerCommitment(planSerial);

    return {
      valid: true,
      planSerial,
      ledgerTransaction: ledgerTx,
      message: "PLAN VALIDATED — READY FOR FINALIZATION"
    };
  }

  /**
   * VALIDATE PLAN FINALIZATION
   */
  async validatePlanFinalization(plan: Plan, summary: PlanSummary): Promise<PlanValidationResult> {

    // FINALIZATION RULES VALIDATION
    const finalizationCheck = this.validateFinalizationRules(plan, summary);
    if (!finalizationCheck.valid) {
      return {
        valid: false,
        rejectionReason: finalizationCheck.rule,
        message: `PLAN REJECTED — SYSTEM LAW VIOLATION: ${finalizationCheck.rule}`
      };
    }

    return {
      valid: true,
      message: "PLAN VALIDATED — READY FOR FINALIZATION"
    };
  }

  /**
   * VALIDATE OUTPUT AUTHORIZATION
   */
  validateOutputAuthorization(
    plan: Plan,
    outputType: 'report_only' | 'invoice_only' | 'report_and_invoice'
  ): PlanValidationResult {

    // OUTPUT AUTHORIZATION VALIDATION
    if (!['report_only', 'invoice_only', 'report_and_invoice'].includes(outputType)) {
      return {
        valid: false,
        rejectionReason: 'OUTPUT_1_AUTHORIZED_TYPES',
        message: 'PLAN REJECTED — SYSTEM LAW VIOLATION: OUTPUT_1_AUTHORIZED_TYPES'
      };
    }

    if (plan.phase !== 'archived') {
      return {
        valid: false,
        rejectionReason: 'OUTPUT_3_SNAPSHOT_ONLY',
        message: 'PLAN REJECTED — SYSTEM LAW VIOLATION: OUTPUT_3_SNAPSHOT_ONLY'
      };
    }

    return {
      valid: true,
      message: "PLAN VALIDATED — READY FOR FINALIZATION"
    };
  }

  // ════════════════════════════════════════════════════════════════════
  // PRECONDITION VALIDATION
  // ════════════════════════════════════════════════════════════════════

  private async validatePreconditions(jobIds: string[], timeEntryIds: string[]): Promise<{ valid: boolean; rule?: ValidationRule }> {

    // 1. All referenced Jobs exist and are closed
    const jobsCheck = await this.validateJobsClosed(jobIds);
    if (!jobsCheck) return { valid: false, rule: 'PRECONDITION_1_JOBS_CLOSED' };

    // 2. All referenced Time entries exist and are closed
    const timeCheck = await this.validateTimeEntriesClosed(timeEntryIds);
    if (!timeCheck) return { valid: false, rule: 'PRECONDITION_2_TIME_CLOSED' };

    // 3. No referenced Job or Time entry is mutable
    const mutabilityCheck = await this.validateNoMutability(jobIds, timeEntryIds);
    if (!mutabilityCheck) return { valid: false, rule: 'PRECONDITION_3_NO_MUTABILITY' };

    // 4. All clock-out notes are captured and immutable
    const clockoutCheck = await this.validateClockoutNotesImmutable(timeEntryIds);
    if (!clockoutCheck) return { valid: false, rule: 'PRECONDITION_4_CLOCKOUT_NOTES_IMMUTABLE' };

    // 5. Any chat or AI context is read-only and non-authoritative
    const chatCheck = await this.validateChatReadonly(jobIds);
    if (!chatCheck) return { valid: false, rule: 'PRECONDITION_5_CHAT_READONLY' };

    // 6. Every referenced record has a canonical serial
    const serialCheck = await this.validateCanonicalSerials(jobIds, timeEntryIds);
    if (!serialCheck) return { valid: false, rule: 'PRECONDITION_6_SERIALS_CANONICAL' };

    // 7. Every referenced serial has been cryptographically hashed
    const hashCheck = await this.validateCryptographicHashes(jobIds, timeEntryIds);
    if (!hashCheck) return { valid: false, rule: 'PRECONDITION_7_HASHES_COMPUTED' };

    // 8. Every referenced hash has been written to the ledger
    const ledgerWriteCheck = await this.validateLedgerWrites(jobIds, timeEntryIds);
    if (!ledgerWriteCheck) return { valid: false, rule: 'PRECONDITION_8_LEDGER_WRITTEN' };

    // 9. Ledger transaction references are available for verification
    const ledgerVerifyCheck = await this.validateLedgerVerification(jobIds, timeEntryIds);
    if (!ledgerVerifyCheck) return { valid: false, rule: 'PRECONDITION_9_LEDGER_VERIFIABLE' };

    return { valid: true };
  }

  // ════════════════════════════════════════════════════════════════════
  // CONTENT VALIDATION
  // ════════════════════════════════════════════════════════════════════

  private async validatePlanContent(planRequest: any): Promise<{ valid: boolean; rule?: ValidationRule }> {

    // 1. Plan Intent - Clear statement of purpose
    if (!planRequest.intent || !['report', 'invoice', 'both'].includes(planRequest.intent)) {
      return { valid: false, rule: 'CONTENT_1_INTENT_CLEAR' };
    }

    // 2. Scope Reference - Explicit lists
    if (!planRequest.jobIds || !planRequest.timeEntryIds ||
        planRequest.jobIds.length === 0 || planRequest.timeEntryIds.length === 0) {
      return { valid: false, rule: 'CONTENT_2_SCOPE_EXPLICIT' };
    }

    // 3. Summary Narrative - Will be validated during synthesis
    // This is checked during finalization when summary is provided

    // 4. Actor Attribution - UUIDs only
    if (!planRequest.clientUuid || !planRequest.workerUuids ||
        planRequest.workerUuids.length === 0) {
      return { valid: false, rule: 'CONTENT_4_ATTRIBUTION_UUID' };
    }

    // 5. Serial Assignment - Generated during validation
    // 6. Cryptographic Commitment - Created during validation

    return { valid: true };
  }

  // ════════════════════════════════════════════════════════════════════
  // FINALIZATION VALIDATION
  // ════════════════════════════════════════════════════════════════════

  private validateFinalizationRules(plan: Plan, summary: PlanSummary): { valid: boolean; rule?: ValidationRule } {

    // 1. State transition from OPEN to CLOSED
    if (plan.phase !== 'summary_ready') {
      return { valid: false, rule: 'FINALIZATION_1_STATE_TRANSITION' };
    }

    // 2. Immutability check (summary must be confirmed)
    if (summary.status !== 'confirmed') {
      return { valid: false, rule: 'FINALIZATION_2_IMMUTABILITY' };
    }

    // 3-5. Additional finalization rules would be enforced by the system architecture

    return { valid: true };
  }

  // ════════════════════════════════════════════════════════════════════
  // SERIAL GENERATION & CRYPTOGRAPHY
  // ════════════════════════════════════════════════════════════════════

  private generatePlanSerial(
    planRequest: any,
    engagement: Engagement
  ): PlanSerial {
    const timestamp = Date.now();
    const sequence = this.generateSequenceNumber();

    const serial: PlanSerial = {
      id: uuidv4(),
      recordType: 'PLAN',
      sequenceNumber: sequence,
      timestamp,
      latitude: planRequest.latitude,
      longitude: planRequest.longitude,
      verticalUnitId: planRequest.verticalUnitId,
      timeEnvelope: {
        start: Math.min(...planRequest.timeEntryIds.map(() => timestamp)), // TODO: Get actual times
        end: Math.max(...planRequest.timeEntryIds.map(() => timestamp))
      },
      environmentalContext: planRequest.environmentalContext,
      actorBlock: {
        clientUuid: planRequest.clientUuid,
        workerUuids: planRequest.workerUuids,
        foremanUuid: planRequest.foremanUuid || null
      },
      scopeReferences: {
        jobIds: planRequest.jobIds,
        timeEntryIds: planRequest.timeEntryIds
      },
      engagementId: engagement.id
    };

    return serial;
  }

  private async createLedgerCommitment(planSerial: PlanSerial): Promise<LedgerTransaction> {
    // Compute SHA-256 hash of the plan serial
    const serialData = JSON.stringify(planSerial);
    const hash = crypto.createHash('sha256').update(serialData).digest('hex');

    // TODO: Write to actual blockchain ledger
    // For now, simulate ledger transaction
    const ledgerTx: LedgerTransaction = {
      id: uuidv4(),
      hash,
      timestamp: Date.now(),
      blockHeight: Math.floor(Date.now() / 1000), // Mock block height
      transactionId: `ledger_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      verified: true
    };

    return ledgerTx;
  }

  // ════════════════════════════════════════════════════════════════════
  // PRECONDITION CHECK IMPLEMENTATIONS
  // ════════════════════════════════════════════════════════════════════

  private async validateJobsClosed(jobIds: string[]): Promise<boolean> {
    // TODO: Check actual job status from jobboard service
    // All jobs must exist and be in 'done' status
    return true; // Mock validation
  }

  private async validateTimeEntriesClosed(timeEntryIds: string[]): Promise<boolean> {
    // TODO: Check actual time entries from time tracking service
    // All entries must have clockOutTime and be approved
    return true; // Mock validation
  }

  private async validateNoMutability(jobIds: string[], timeEntryIds: string[]): Promise<boolean> {
    // TODO: Verify no jobs or time entries have been modified since last hash
    return true; // Mock validation
  }

  private async validateClockoutNotesImmutable(timeEntryIds: string[]): Promise<boolean> {
    // TODO: Verify all time entries have immutable clock-out context
    return true; // Mock validation
  }

  private async validateChatReadonly(jobIds: string[]): Promise<boolean> {
    // TODO: Verify all related chat messages are read-only
    return true; // Mock validation
  }

  private async validateCanonicalSerials(jobIds: string[], timeEntryIds: string[]): Promise<boolean> {
    // TODO: Verify all referenced records have canonical serials
    return true; // Mock validation
  }

  private async validateCryptographicHashes(jobIds: string[], timeEntryIds: string[]): Promise<boolean> {
    // TODO: Verify all records have valid SHA-256 hashes
    return true; // Mock validation
  }

  private async validateLedgerWrites(jobIds: string[], timeEntryIds: string[]): Promise<boolean> {
    // TODO: Verify all hashes have been written to blockchain ledger
    return true; // Mock validation
  }

  private async validateLedgerVerification(jobIds: string[], timeEntryIds: string[]): Promise<boolean> {
    // TODO: Verify ledger transactions are verifiable
    return true; // Mock validation
  }

  private generateSequenceNumber(): number {
    // TODO: Implement proper sequence number generation
    // Should be atomic and unique across the system
    return Math.floor(Date.now() / 1000);
  }
}

// Export singleton instance
export const planAuthority = new PlanAuthority();
