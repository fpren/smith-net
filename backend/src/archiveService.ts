/**
 * Archive Service
 * Creates immutable snapshots and manages read-only archived data
 */

import { Plan, PlanSnapshot, TimeEntry, Message } from './types';
import crypto from 'crypto';
import { v4 as uuidv4 } from 'uuid';

export class ArchiveService {

  /**
   * Create an immutable snapshot of a complete plan
   */
  async createPlanSnapshot(plan: Plan, snapshotType: 'archive' | 'output' = 'archive'): Promise<PlanSnapshot> {
    // Gather all related data
    const jobs = await this.getJobsSnapshot(plan.jobIds);
    const timeEntries = await this.getTimeEntriesSnapshot(plan.timeEntryIds);
    const messages = await this.getMessagesSnapshot(plan);

    // Create the complete data snapshot
    const snapshotData = {
      ...plan,
      // Ensure all mutable fields are set to final values
      phase: 'archived' as const,
      archivedAt: Date.now(),
      immutableHash: plan.immutableHash || this.generatePlanHash(plan)
    };

    // Create the immutable snapshot
    const snapshot: PlanSnapshot = {
      id: uuidv4(),
      planId: plan.id,
      snapshotType,
      data: snapshotData,
      jobs,
      timeEntries,
      messages,
      createdAt: Date.now(),
      immutableHash: this.generateSnapshotHash(snapshotData, jobs, timeEntries, messages)
    };

    // TODO: Save snapshot to database with immutability constraints

    console.log(`[Archive] Created ${snapshotType} snapshot for plan: ${plan.id}`);
    return snapshot;
  }

  /**
   * Verify the integrity of an archived snapshot
   */
  verifySnapshotIntegrity(snapshot: PlanSnapshot): boolean {
    const expectedHash = this.generateSnapshotHash(
      snapshot.data,
      snapshot.jobs,
      snapshot.timeEntries,
      snapshot.messages
    );
    return snapshot.immutableHash === expectedHash;
  }

  /**
   * Archive a plan (final step in the flow)
   */
  async archivePlan(plan: Plan, archiverId: string): Promise<PlanSnapshot> {
    // Create the final immutable snapshot
    const snapshot = await this.createPlanSnapshot(plan, 'archive');

    // TODO: Mark plan as archived in database
    // TODO: Set all related data to read-only mode
    // TODO: Prevent any further modifications

    console.log(`[Archive] Plan ${plan.id} archived by ${archiverId} at ${new Date().toISOString()}`);
    return snapshot;
  }

  /**
   * Get archived plan snapshot (read-only access)
   */
  async getArchivedPlan(planId: string): Promise<PlanSnapshot | null> {
    // TODO: Fetch from archive database
    // For now, return null
    return null;
  }

  /**
   * List all archived plans for an engagement
   */
  async getArchivedPlansForEngagement(engagementId: string): Promise<PlanSnapshot[]> {
    // TODO: Query archive database
    // For now, return empty array
    return [];
  }

  /**
   * Generate SHA256 hash for plan immutability
   */
  private generatePlanHash(plan: Plan): string {
    const data = JSON.stringify({
      id: plan.id,
      engagementId: plan.engagementId,
      name: plan.name,
      description: plan.description,
      phase: plan.phase,
      createdAt: plan.createdAt,
      jobIds: plan.jobIds,
      timeEntryIds: plan.timeEntryIds,
      // Include summary data if present
      summary: plan.summary ? {
        id: plan.summary.id,
        title: plan.summary.title,
        executiveSummary: plan.summary.executiveSummary,
        workPerformed: plan.summary.workPerformed,
        challenges: plan.summary.challenges,
        recommendations: plan.summary.recommendations,
        totalHours: plan.summary.totalHours,
        totalCost: plan.summary.totalCost,
        confirmedAt: plan.summary.confirmedAt
      } : null
    });
    return crypto.createHash('sha256').update(data).digest('hex');
  }

  /**
   * Generate SHA256 hash for complete snapshot immutability
   */
  private generateSnapshotHash(
    planData: any,
    jobs: any[],
    timeEntries: any[],
    messages: any[]
  ): string {
    const data = JSON.stringify({
      plan: planData,
      jobs: jobs.map(job => ({
        id: job.id,
        title: job.title,
        description: job.description,
        status: job.status,
        completedAt: job.completedAt
      })),
      timeEntries: timeEntries.map(entry => ({
        id: entry.id,
        userId: entry.userId,
        userName: entry.userName,
        clockInTime: entry.clockInTime,
        clockOutTime: entry.clockOutTime,
        durationMinutes: entry.durationMinutes,
        jobId: entry.jobId,
        entryType: entry.entryType,
        source: entry.source,
        clockOutContext: entry.clockOutContext,
        immutableHash: entry.immutableHash
      })),
      messages: messages.map(msg => ({
        id: msg.id,
        channelId: msg.channelId,
        senderId: msg.senderId,
        senderName: msg.senderName,
        content: msg.content,
        timestamp: msg.timestamp,
        origin: msg.origin
      }))
    });
    return crypto.createHash('sha256').update(data).digest('hex');
  }

  /**
   * Get jobs snapshot data
   */
  private async getJobsSnapshot(jobIds: string[]): Promise<any[]> {
    // TODO: Integrate with jobboard service to get complete job data
    // For now, return mock data
    return jobIds.map(jobId => ({
      id: jobId,
      title: `Job ${jobId}`,
      description: `Completed work for ${jobId}`,
      status: 'done',
      completedAt: Date.now()
    }));
  }

  /**
   * Get time entries snapshot data
   */
  private async getTimeEntriesSnapshot(timeEntryIds: string[]): Promise<any[]> {
    // TODO: Integrate with time tracking service to get complete time data
    // For now, return mock data
    return timeEntryIds.map(entryId => ({
      id: entryId,
      userId: 'worker_1',
      userName: 'Worker 1',
      clockInTime: Date.now() - (8 * 60 * 60 * 1000),
      clockOutTime: Date.now(),
      durationMinutes: 480,
      jobId: 'job1',
      entryType: 'regular',
      source: 'manual',
      immutableHash: 'mock_hash',
      clockOutContext: {
        type: 'worker_note',
        content: 'Work completed successfully',
        generatedAt: Date.now(),
        generatedBy: 'worker_1',
        isImmutable: true
      }
    }));
  }

  /**
   * Get messages/chat snapshot data
   */
  private async getMessagesSnapshot(plan: Plan): Promise<any[]> {
    // TODO: Get all messages related to plan's jobs and channels
    // For now, return mock data
    return [
      {
        id: 'msg1',
        channelId: 'general',
        senderId: 'worker1',
        senderName: 'Worker 1',
        content: 'Started work on plan',
        timestamp: Date.now() - (24 * 60 * 60 * 1000),
        origin: 'online'
      },
      {
        id: 'msg2',
        channelId: 'general',
        senderId: 'foreman1',
        senderName: 'Foreman',
        content: 'Good progress, keep it up',
        timestamp: Date.now() - (12 * 60 * 60 * 1000),
        origin: 'online'
      },
      {
        id: 'msg3',
        channelId: 'general',
        senderId: 'worker1',
        senderName: 'Worker 1',
        content: 'All tasks completed',
        timestamp: Date.now(),
        origin: 'online'
      }
    ];
  }

  /**
   * Export archived data for compliance/reporting
   */
  async exportArchivedPlan(planId: string, format: 'json' | 'pdf' | 'csv' = 'json'): Promise<any> {
    const snapshot = await this.getArchivedPlan(planId);
    if (!snapshot) {
      throw new Error('Archived plan not found');
    }

    // Verify integrity before export
    if (!this.verifySnapshotIntegrity(snapshot)) {
      throw new Error('Archive integrity check failed');
    }

    switch (format) {
      case 'json':
        return snapshot;
      case 'pdf':
        // TODO: Generate PDF report
        return { type: 'pdf', data: 'mock_pdf_data' };
      case 'csv':
        // TODO: Generate CSV export
        return { type: 'csv', data: 'mock_csv_data' };
      default:
        throw new Error('Unsupported export format');
    }
  }
}

// Export singleton instance
export const archiveService = new ArchiveService();
