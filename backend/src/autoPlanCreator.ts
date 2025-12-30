/**
 * AUTO PLAN CREATOR
 * =================
 *
 * For small-project flow: Creates Plan automatically from collected facts
 * when jobs are complete and synthesis is requested.
 *
 * System Law: Plan is not skipped. Plan is deferred.
 * Plan is auto-instantiated at finalization using collected facts.
 */

import {
  Plan,
  Engagement,
  TimeEntry,
  PlanSerial,
  PlanValidationResult,
  LedgerTransaction
} from './types';
import { planAuthority } from './planAuthority';
import { v4 as uuidv4 } from 'uuid';

export class AutoPlanCreator {

  /**
   * CREATE PLAN FROM COLLECTED FACTS
   * Small-project flow: Auto-create Plan when synthesis is requested
   */
  async createPlanFromFacts(
    jobIds: string[],
    timeEntryIds: string[],
    clientUuid: string,
    workerUuids: string[],
    foremanUuid?: string,
    engagementName?: string,
    latitude?: number,
    longitude?: number,
    verticalUnitId?: string,
    environmentalContext?: string
  ): Promise<{ plan: Plan; validation: PlanValidationResult }> {

    // Create implicit engagement if none exists
    const engagement: Engagement = {
      id: uuidv4(),
      name: engagementName || 'Auto-generated Small Project',
      intent: 'Small project execution - manual job entry',
      createdBy: 'system',
      createdAt: Date.now(),
      status: 'active'
    };

    // Validate that all preconditions are met (jobs closed, time closed, etc.)
    const validation = await planAuthority.validatePlanCreation(engagement, {
      name: engagement.name,
      intent: 'Small project execution',
      jobIds,
      timeEntryIds,
      clientUuid,
      workerUuids,
      foremanUuid,
      latitude,
      longitude,
      verticalUnitId,
      environmentalContext
    });

    if (!validation.valid) {
      return { plan: null as any, validation };
    }

    // Create the Plan with auto-generated serial
    const plan: Plan = {
      id: uuidv4(),
      engagementId: engagement.id,
      name: engagement.name,
      description: `Auto-generated plan for small project: ${jobIds.length} jobs, ${timeEntryIds.length} time entries`,
      phase: 'draft', // Will transition through phases
      createdAt: Date.now(),
      updatedAt: Date.now(),
      jobIds,
      timeEntryIds,
      outputs: []
    };

    // Attach the validated serial and ledger info
    if (validation.planSerial) {
      plan.serial = validation.planSerial;
    }

    return { plan, validation };
  }

  /**
   * TRANSITION AUTO PLAN THROUGH PHASES
   * Small-project flow: Move from draft → summary_ready → finalized
   */
  async transitionAutoPlan(plan: Plan, targetPhase: 'summary_ready' | 'finalized'): Promise<Plan> {

    const updatedPlan = { ...plan };

    if (targetPhase === 'summary_ready') {
      // Validate that jobs are complete and time is closed
      // This would normally be checked by Plan Authority
      updatedPlan.phase = 'summary_ready';
    }

    if (targetPhase === 'finalized') {
      // Final validation before locking
      const finalValidation = await planAuthority.validatePlanFinalization(plan, plan.summary!);
      if (!finalValidation.valid) {
        throw new Error(`Plan finalization failed: ${finalValidation.rejectionReason}`);
      }
      updatedPlan.phase = 'finalized';
    }

    updatedPlan.updatedAt = Date.now();
    return updatedPlan;
  }

  /**
   * GENERATE SMALL PROJECT INTENT
   * Based on job and time facts, infer the project intent
   */
  generateProjectIntent(jobCount: number, timeEntryCount: number): string {
    if (jobCount === 1 && timeEntryCount <= 3) {
      return 'Single job execution';
    }
    if (jobCount <= 3 && timeEntryCount <= 10) {
      return 'Small project execution';
    }
    return 'Project execution';
  }

  /**
   * VALIDATE SMALL PROJECT ELIGIBILITY
   * System Law: Small projects skip early PLAN but never skip PLAN itself
   */
  validateSmallProjectEligibility(jobIds: string[], timeEntryIds: string[]): boolean {
    // Small project criteria (adjustable but must be defined)
    const maxJobs = 5;
    const maxTimeEntries = 20;

    return jobIds.length <= maxJobs && timeEntryIds.length <= maxTimeEntries;
  }

  /**
   * CREATE ENGAGEMENT FROM FACTS
   * For small projects without explicit engagement
   */
  createEngagementFromFacts(jobIds: string[], timeEntryIds: string[]): Engagement {
    const intent = this.generateProjectIntent(jobIds.length, timeEntryIds.length);

    return {
      id: uuidv4(),
      name: `Small Project - ${jobIds.length} Jobs`,
      description: `Auto-generated engagement for small project execution`,
      intent,
      createdBy: 'system',
      createdAt: Date.now(),
      status: 'active'
    };
  }

  /**
   * EXTRACT ACTORS FROM TIME ENTRIES
   * For small projects, infer actors from time records
   */
  extractActorsFromTimeEntries(timeEntries: TimeEntry[]): {
    workerUuids: string[];
    foremanUuid?: string;
  } {
    const workerUuids = [...new Set(timeEntries.map(te => te.userId))];

    // For small projects, assume first worker can be foreman if no explicit foreman
    // This is a simplification - in real system, roles would be explicit
    const foremanUuid = workerUuids.length > 0 ? workerUuids[0] : undefined;

    return { workerUuids, foremanUuid };
  }

  /**
   * VALIDATE AUTO PLAN TRANSITION
   * Ensure all required facts exist before auto-creating Plan
   */
  async validateAutoPlanTransition(jobIds: string[], timeEntryIds: string[]): Promise<{
    valid: boolean;
    reason?: string;
  }> {

    // All jobs must be complete
    // All time entries must be closed
    // All preconditions from Plan Authority must pass

    const mockEngagement: Engagement = {
      id: 'temp',
      name: 'Validation Check',
      intent: 'Pre-flight check',
      createdBy: 'system',
      createdAt: Date.now(),
      status: 'active'
    };

    const validation = await planAuthority.validatePlanCreation(mockEngagement, {
      name: 'Validation Check',
      intent: 'Pre-flight check',
      jobIds,
      timeEntryIds,
      clientUuid: 'system', // Placeholder
      workerUuids: ['system'], // Placeholder
    });

    if (!validation.valid) {
      return { valid: false, reason: validation.rejectionReason };
    }

    return { valid: true };
  }
}

// Export singleton instance
export const autoPlanCreator = new AutoPlanCreator();
