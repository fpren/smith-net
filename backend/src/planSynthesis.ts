/**
 * Plan Synthesis Service
 * Reads jobs, time entries, notes, and chat to create plan summaries
 */

import { Plan, PlanSummary, TimeEntry, Message } from './types';
import { messageStore } from './messageStore';

export class PlanSynthesisService {

  /**
   * Synthesize a plan summary from all available data
   */
  async synthesizePlan(plan: Plan, useAI: boolean = false): Promise<PlanSummary> {
    // Read all time entries for this plan
    const timeEntries = await this.getTimeEntriesForPlan(plan);

    // Read all messages/chat for context
    const messages = await this.getMessagesForPlan(plan);

    // Read job details (would integrate with jobboard service)
    const jobs = await this.getJobsForPlan(plan);

    // Calculate totals
    const totalHours = this.calculateTotalHours(timeEntries);
    const totalCost = this.calculateTotalCost(timeEntries); // Would need rate information

    // Generate work performed summary
    const workPerformed = this.summarizeWorkPerformed(jobs, timeEntries, messages);

    // Generate challenges (if any)
    const challenges = this.identifyChallenges(timeEntries, messages);

    // Generate recommendations
    const recommendations = this.generateRecommendations(timeEntries, messages);

    // Generate executive summary
    let executiveSummary: string;
    if (useAI) {
      executiveSummary = await this.generateAIExecutiveSummary(plan, workPerformed, challenges, recommendations);
    } else {
      executiveSummary = this.generateBasicExecutiveSummary(plan, totalHours, workPerformed.length);
    }

    return {
      id: '', // Will be set by caller
      planId: plan.id,
      title: `Summary: ${plan.name}`,
      executiveSummary,
      workPerformed,
      challenges,
      recommendations,
      totalHours,
      totalCost,
      createdBy: useAI ? 'ai' : 'system',
      createdAt: Date.now(),
      status: 'draft'
    };
  }

  /**
   * Get all time entries associated with a plan
   */
  private async getTimeEntriesForPlan(plan: Plan): Promise<TimeEntry[]> {
    // TODO: Integrate with time tracking service
    // For now, return mock data based on plan.jobIds and plan.timeEntryIds
    const mockTimeEntries: TimeEntry[] = [];

    // Mock some time entries based on plan data
    plan.timeEntryIds.forEach((entryId, index) => {
      mockTimeEntries.push({
        id: entryId,
        userId: 'worker_' + (index % 3 + 1),
        userName: 'Worker ' + (index % 3 + 1),
        clockInTime: Date.now() - (8 * 60 * 60 * 1000) - (index * 24 * 60 * 60 * 1000),
        clockOutTime: Date.now() - (index * 24 * 60 * 60 * 1000),
        durationMinutes: 480, // 8 hours
        jobId: plan.jobIds[index % plan.jobIds.length],
        entryType: 'regular',
        source: 'manual',
        createdAt: Date.now() - (index * 24 * 60 * 60 * 1000),
        immutableHash: 'mock_hash',
        notes: [],
        status: 'approved'
      });
    });

    return mockTimeEntries;
  }

  /**
   * Get all messages/chat associated with a plan
   */
  private async getMessagesForPlan(plan: Plan): Promise<Message[]> {
    // Get messages from channels related to this plan's jobs
    // This would need to be enhanced to properly link messages to plans
    const allMessages: Message[] = [];

    // For each job, try to find a related channel and get messages
    plan.jobIds.forEach(jobId => {
      // Mock: assume each job has a channel named after it
      const channel = messageStore.findByName(jobId);
      if (channel) {
        const jobMessages = messageStore.getForChannel(channel.id, 50);
        allMessages.push(...jobMessages);
      }
    });

    return allMessages;
  }

  /**
   * Get job details for a plan
   */
  private async getJobsForPlan(plan: Plan): Promise<any[]> {
    // TODO: Integrate with jobboard service
    // For now, return mock job data
    return plan.jobIds.map(jobId => ({
      id: jobId,
      title: `Job ${jobId}`,
      description: `Work performed for job ${jobId}`,
      status: 'done'
    }));
  }

  /**
   * Calculate total hours from time entries
   */
  private calculateTotalHours(timeEntries: TimeEntry[]): number {
    return timeEntries.reduce((total, entry) => {
      return total + (entry.durationMinutes || 0);
    }, 0) / 60; // Convert to hours
  }

  /**
   * Calculate total cost from time entries
   */
  private calculateTotalCost(timeEntries: TimeEntry[]): number {
    // TODO: Would need hourly rates per user/job
    // For now, assume $50/hour average
    const hourlyRate = 50;
    return this.calculateTotalHours(timeEntries) * hourlyRate;
  }

  /**
   * Summarize work performed from jobs, time, and messages
   */
  private summarizeWorkPerformed(jobs: any[], timeEntries: TimeEntry[], messages: Message[]): string[] {
    const workItems: string[] = [];

    // Add job completions
    jobs.forEach(job => {
      if (job.status === 'done') {
        workItems.push(`Completed ${job.title}: ${job.description}`);
      }
    });

    // Add time-based summaries
    const userHours = new Map<string, number>();
    timeEntries.forEach(entry => {
      const current = userHours.get(entry.userName) || 0;
      userHours.set(entry.userName, current + (entry.durationMinutes || 0) / 60);
    });

    userHours.forEach((hours, userName) => {
      workItems.push(`${userName} contributed ${hours.toFixed(1)} hours of work`);
    });

    // Add key messages/insights
    const keyMessages = messages
      .filter(msg => msg.content.toLowerCase().includes('complete') ||
                     msg.content.toLowerCase().includes('finish') ||
                     msg.content.toLowerCase().includes('issue') ||
                     msg.content.toLowerCase().includes('problem'))
      .slice(0, 3); // Limit to 3 key messages

    keyMessages.forEach(msg => {
      workItems.push(`Communication: ${msg.senderName} noted "${msg.content.substring(0, 100)}..."`);
    });

    return workItems;
  }

  /**
   * Identify challenges from time entries and messages
   */
  private identifyChallenges(timeEntries: TimeEntry[], messages: Message[]): string[] {
    const challenges: string[] = [];

    // Look for overtime entries as potential challenges
    const overtimeEntries = timeEntries.filter(entry => entry.entryType === 'overtime');
    if (overtimeEntries.length > 0) {
      challenges.push(`${overtimeEntries.length} overtime entries recorded, indicating potential scheduling challenges`);
    }

    // Look for blocked time entries
    const blockedEntries = timeEntries.filter(entry => entry.entryType === 'break' && entry.status === 'pending_review');
    if (blockedEntries.length > 0) {
      challenges.push(`${blockedEntries.length} break requests pending approval`);
    }

    // Look for problem-related messages
    const problemMessages = messages.filter(msg =>
      msg.content.toLowerCase().includes('problem') ||
      msg.content.toLowerCase().includes('issue') ||
      msg.content.toLowerCase().includes('delay') ||
      msg.content.toLowerCase().includes('difficulty')
    );

    if (problemMessages.length > 0) {
      challenges.push(`${problemMessages.length} communications mentioned problems or issues`);
    }

    return challenges;
  }

  /**
   * Generate recommendations based on data analysis
   */
  private generateRecommendations(timeEntries: TimeEntry[], messages: Message[]): string[] {
    const recommendations: string[] = [];

    // Analyze work patterns
    const regularHours = timeEntries.filter(e => e.entryType === 'regular').length;
    const overtimeHours = timeEntries.filter(e => e.entryType === 'overtime').length;

    if (overtimeHours > regularHours * 0.2) { // More than 20% overtime
      recommendations.push('Consider adjusting project timeline to reduce overtime requirements');
    }

    // Check for communication patterns
    const totalMessages = messages.length;
    if (totalMessages < timeEntries.length * 0.5) { // Less than 0.5 messages per time entry
      recommendations.push('Increase communication frequency to improve coordination');
    }

    // Check for break patterns
    const breakEntries = timeEntries.filter(e => e.entryType === 'break').length;
    if (breakEntries < timeEntries.length * 0.1) { // Less than 10% break entries
      recommendations.push('Ensure adequate break time is scheduled for worker wellbeing');
    }

    return recommendations;
  }

  /**
   * Generate basic executive summary
   */
  private generateBasicExecutiveSummary(plan: Plan, totalHours: number, workItemsCount: number): string {
    return `Project "${plan.name}" has been completed with ${totalHours.toFixed(1)} total hours worked across ${workItemsCount} major work items. All assigned tasks have been finished and time entries closed.`;
  }

  /**
   * Generate AI-powered executive summary
   */
  private async generateAIExecutiveSummary(
    plan: Plan,
    workPerformed: string[],
    challenges: string[],
    recommendations: string[]
  ): Promise<string> {
    // TODO: Integrate with LLM service for intelligent summary generation
    // For now, return a more sophisticated template-based summary

    let summary = `Project "${plan.name}" has been successfully completed. `;

    if (workPerformed.length > 0) {
      summary += `Key accomplishments include ${workPerformed.length} major deliverables. `;
    }

    if (challenges.length > 0) {
      summary += `The project encountered ${challenges.length} notable challenges that were addressed. `;
    }

    if (recommendations.length > 0) {
      summary += `For future similar projects, ${recommendations.length} recommendations have been identified to improve efficiency.`;
    }

    return summary;
  }
}

// Export singleton instance
export const planSynthesisService = new PlanSynthesisService();
