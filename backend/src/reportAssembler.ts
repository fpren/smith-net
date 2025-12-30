/**
 * REPORT ASSEMBLER
 * ================
 *
 * Responsibility: Interpret plan snapshot, select report schema, map data → report sections
 * Tech: Deterministic mapping rules, no business logic
 * Output: Structured Report Model (JSON / internal AST)
 */

import {
  ReportAssembler as IReportAssembler,
  StructuredReportModel,
  ReportSection,
  ReportMetadata,
  PlanSnapshot
} from './types';
import { v4 as uuidv4 } from 'uuid';

export class ReportAssembler implements IReportAssembler {

  /**
   * ASSEMBLE FROM PLAN SNAPSHOT
   * Deterministic mapping - no business logic decisions
   */
  assembleFromPlanSnapshot(planSnapshot: PlanSnapshot): StructuredReportModel {
    const sections: ReportSection[] = [];

    // EXECUTIVE SUMMARY SECTION
    sections.push(this.createExecutiveSummarySection(planSnapshot));

    // WORK PERFORMED NARRATIVE
    sections.push(this.createWorkPerformedSection(planSnapshot));

    // TIME SUMMARY TABLE
    sections.push(this.createTimeSummarySection(planSnapshot));

    // FINANCIAL SUMMARY (if applicable)
    const financialSection = this.createFinancialSection(planSnapshot);
    if (financialSection) {
      sections.push(financialSection);
    }

    // CHALLENGES SECTION (if any)
    const challengesSection = this.createChallengesSection(planSnapshot);
    if (challengesSection) {
      sections.push(challengesSection);
    }

    // RECOMMENDATIONS SECTION (if any)
    const recommendationsSection = this.createRecommendationsSection(planSnapshot);
    if (recommendationsSection) {
      sections.push(recommendationsSection);
    }

    // METADATA SECTION
    sections.push(this.createMetadataSection(planSnapshot));

    const metadata: ReportMetadata = {
      generatedAt: Date.now(),
      generatedBy: 'system',
      planSerial: planSnapshot.data.id, // Use plan ID as serial reference
      ledgerTx: planSnapshot.immutableHash
    };

    return {
      id: uuidv4(),
      planId: planSnapshot.planId,
      sections,
      metadata
    };
  }

  private createExecutiveSummarySection(planSnapshot: PlanSnapshot): ReportSection {
    const plan = planSnapshot.data;

    return {
      id: uuidv4(),
      title: 'Executive Summary',
      type: 'narrative',
      content: {
        text: plan.summary?.executiveSummary || 'Project work completed according to plan.',
        keyMetrics: {
          totalHours: plan.summary?.totalHours || 0,
          totalJobs: plan.jobIds.length,
          totalTimeEntries: plan.timeEntryIds.length,
          timeEnvelope: {
            start: planSnapshot.data.createdAt,
            end: planSnapshot.data.updatedAt || planSnapshot.data.createdAt
          }
        }
      }
    };
  }

  private createWorkPerformedSection(planSnapshot: PlanSnapshot): ReportSection {
    const plan = planSnapshot.data;
    const jobs = planSnapshot.jobs;
    const timeEntries = planSnapshot.timeEntries;

    // Map jobs to work performed items
    const workItems = jobs.map((job: any, index: number) => ({
      jobId: job.id,
      title: job.title,
      description: job.description,
      status: job.status,
      completedAt: job.completedAt,
      associatedTime: this.calculateJobTime(job.id, timeEntries)
    }));

    return {
      id: uuidv4(),
      title: 'Work Performed',
      type: 'narrative',
      content: {
        summary: plan.summary?.workPerformed || [],
        detailedWork: workItems,
        totalJobs: jobs.length
      }
    };
  }

  private createTimeSummarySection(planSnapshot: PlanSnapshot): ReportSection {
    const timeEntries = planSnapshot.timeEntries;

    // Group by user
    const userGroups = new Map<string, any[]>();
    timeEntries.forEach((entry: any) => {
      if (!userGroups.has(entry.userName)) {
        userGroups.set(entry.userName, []);
      }
      userGroups.get(entry.userName)!.push(entry);
    });

    const tableData = Array.from(userGroups.entries()).map(([userName, entries]) => ({
      worker: userName,
      totalHours: entries.reduce((sum: number, entry: any) => sum + (entry.durationMinutes || 0) / 60, 0),
      entriesCount: entries.length,
      jobsWorked: [...new Set(entries.map((e: any) => e.jobId))].length
    }));

    return {
      id: uuidv4(),
      title: 'Time Summary',
      type: 'table',
      content: {
        headers: ['Worker', 'Total Hours', 'Entries', 'Jobs Worked'],
        rows: tableData.map(row => [
          row.worker,
          row.totalHours.toFixed(2),
          row.entriesCount.toString(),
          row.jobsWorked.toString()
        ]),
        summary: {
          totalWorkers: tableData.length,
          totalHours: tableData.reduce((sum, row) => sum + row.totalHours, 0),
          totalEntries: tableData.reduce((sum, row) => sum + row.entriesCount, 0)
        }
      }
    };
  }

  private createFinancialSection(planSnapshot: PlanSnapshot): ReportSection | null {
    const plan = planSnapshot.data;

    if (!plan.summary?.totalCost) {
      return null;
    }

    return {
      id: uuidv4(),
      title: 'Financial Summary',
      type: 'summary',
      content: {
        totalCost: plan.summary.totalCost,
        hourlyRate: plan.summary.totalCost / (plan.summary.totalHours || 1),
        breakdown: {
          labor: plan.summary.totalCost,
          expenses: 0, // TODO: Add expense tracking
          taxes: 0     // TODO: Add tax calculations
        }
      }
    };
  }

  private createChallengesSection(planSnapshot: PlanSnapshot): ReportSection | null {
    const plan = planSnapshot.data;

    if (!plan.summary?.challenges || plan.summary.challenges.length === 0) {
      return null;
    }

    return {
      id: uuidv4(),
      title: 'Challenges Encountered',
      type: 'narrative',
      content: {
        challenges: plan.summary.challenges,
        impact: 'Identified and addressed during project execution'
      }
    };
  }

  private createRecommendationsSection(planSnapshot: PlanSnapshot): ReportSection | null {
    const plan = planSnapshot.data;

    if (!plan.summary?.recommendations || plan.summary.recommendations.length === 0) {
      return null;
    }

    return {
      id: uuidv4(),
      title: 'Recommendations',
      type: 'narrative',
      content: {
        recommendations: plan.summary.recommendations,
        applicability: 'For future similar projects'
      }
    };
  }

  private createMetadataSection(planSnapshot: PlanSnapshot): ReportSection {
    return {
      id: uuidv4(),
      title: 'Report Metadata',
      type: 'summary',
      content: {
        planId: planSnapshot.planId,
        snapshotCreated: planSnapshot.createdAt,
        immutableHash: planSnapshot.immutableHash,
        recordCount: {
          jobs: planSnapshot.jobs.length,
          timeEntries: planSnapshot.timeEntries.length,
          messages: planSnapshot.messages.length
        }
      }
    };
  }

  private calculateJobTime(jobId: string, timeEntries: any[]): number {
    return timeEntries
      .filter((entry: any) => entry.jobId === jobId)
      .reduce((sum: number, entry: any) => sum + (entry.durationMinutes || 0), 0);
  }
}

// Export singleton instance
export const reportAssembler = new ReportAssembler();
