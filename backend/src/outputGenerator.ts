/**
 * Output Generation Service
 * Creates reports and invoices from plan data
 */

import { Plan, PlanSummary, Report, Invoice, InvoiceLineItem, GenerateOutputRequest, TimeEntry } from './types';
import { v4 as uuidv4 } from 'uuid';

export class OutputGeneratorService {

  /**
   * Generate outputs based on the request
   */
  async generateOutputs(
    plan: Plan,
    summary: PlanSummary,
    request: GenerateOutputRequest
  ): Promise<{ report?: Report; invoice?: Invoice }> {
    const results: { report?: Report; invoice?: Invoice } = {};

    // Get time entries for detailed billing
    const timeEntries = await this.getTimeEntriesForPlan(plan);

    // Generate report if requested
    if (request.outputType === 'report_only' || request.outputType === 'report_and_invoice') {
      results.report = await this.generateReport(plan, summary, timeEntries, request.reportOptions);
    }

    // Generate invoice if requested
    if (request.outputType === 'invoice_only' || request.outputType === 'report_and_invoice') {
      results.invoice = await this.generateInvoice(plan, summary, timeEntries, request.invoiceOptions);
    }

    return results;
  }

  /**
   * Generate a narrative report
   */
  private async generateReport(
    plan: Plan,
    summary: PlanSummary,
    timeEntries: TimeEntry[],
    options?: GenerateOutputRequest['reportOptions']
  ): Promise<Report> {
    let content = `# ${plan.name} - Work Report\n\n`;

    // Executive Summary
    content += `## Executive Summary\n\n`;
    content += `${summary.executiveSummary}\n\n`;

    // Work Performed
    content += `## Work Performed\n\n`;
    summary.workPerformed.forEach((item, index) => {
      content += `${index + 1}. ${item}\n`;
    });
    content += `\n`;

    // Time Summary
    const userBreakdown = this.groupTimeByUser(timeEntries);
    content += `## Time Summary\n\n`;
    content += `Total Hours: ${summary.totalHours.toFixed(2)}\n\n`;
    content += `### Time by Worker\n\n`;
    userBreakdown.forEach(({ userName, hours, entries }) => {
      content += `- **${userName}**: ${hours.toFixed(2)} hours (${entries} entries)\n`;
    });
    content += `\n`;

    // Include pricing if requested
    if (options?.includePricing && summary.totalCost) {
      content += `## Financial Summary\n\n`;
      content += `Total Cost: $${summary.totalCost.toFixed(2)}\n\n`;
    }

    // Challenges and Recommendations
    if (summary.challenges && summary.challenges.length > 0) {
      content += `## Challenges Encountered\n\n`;
      summary.challenges.forEach((challenge, index) => {
        content += `${index + 1}. ${challenge}\n`;
      });
      content += `\n`;
    }

    if (summary.recommendations && summary.recommendations.length > 0) {
      content += `## Recommendations\n\n`;
      summary.recommendations.forEach((recommendation, index) => {
        content += `${index + 1}. ${recommendation}\n`;
      });
      content += `\n`;
    }

    // Footer
    content += `---\n*Report generated on ${new Date().toLocaleDateString()}*`;

    return {
      id: uuidv4(),
      planId: plan.id,
      title: `${plan.name} - Work Report`,
      content,
      totalHours: options?.includePricing ? summary.totalHours : undefined,
      createdAt: Date.now(),
      createdBy: 'system',
      archived: false
    };
  }

  /**
   * Generate an invoice
   */
  private async generateInvoice(
    plan: Plan,
    summary: PlanSummary,
    timeEntries: TimeEntry[],
    options?: GenerateOutputRequest['invoiceOptions']
  ): Promise<Invoice> {
    const lineItems = this.generateInvoiceLineItems(timeEntries, options);

    const subtotal = lineItems.reduce((sum, item) => sum + item.amount, 0);
    const tax = options?.taxRate ? subtotal * (options.taxRate / 100) : 0;
    const total = subtotal + tax;

    const dueDate = options?.dueDate || Date.now() + (30 * 24 * 60 * 60 * 1000); // 30 days from now

    return {
      id: uuidv4(),
      planId: plan.id,
      title: `Invoice: ${plan.name}`,
      clientName: plan.description || 'Client', // TODO: Get from engagement
      lineItems,
      subtotal,
      tax,
      total,
      dueDate,
      status: 'draft',
      createdAt: Date.now(),
      createdBy: 'system',
      archived: false
    };
  }

  /**
   * Generate invoice line items from time entries
   */
  private generateInvoiceLineItems(
    timeEntries: TimeEntry[],
    options?: GenerateOutputRequest['invoiceOptions']
  ): InvoiceLineItem[] {
    const lineItems: InvoiceLineItem[] = [];

    if (options?.lineItems) {
      // Use custom line items provided
      return options.lineItems.map(item => ({
        ...item,
        id: item.id || uuidv4()
      }));
    }

    // Generate line items from time entries
    const jobGroups = new Map<string, TimeEntry[]>();

    // Group time entries by job
    timeEntries.forEach(entry => {
      const jobId = entry.jobId || 'general';
      if (!jobGroups.has(jobId)) {
        jobGroups.set(jobId, []);
      }
      jobGroups.get(jobId)!.push(entry);
    });

    // Create line items for each job
    jobGroups.forEach((entries, jobId) => {
      const totalHours = entries.reduce((sum, entry) => sum + (entry.durationMinutes || 0) / 60, 0);
      const hourlyRate = this.getHourlyRate(jobId); // TODO: Get actual rates
      const amount = totalHours * hourlyRate;

      lineItems.push({
        id: uuidv4(),
        description: `Work on ${jobId} - ${totalHours.toFixed(2)} hours`,
        quantity: Math.round(totalHours * 100) / 100, // Round to 2 decimal places
        rate: hourlyRate,
        amount: Math.round(amount * 100) / 100,
        jobId,
        timeEntryIds: entries.map(e => e.id)
      });
    });

    return lineItems;
  }

  /**
   * Get time entries for a plan
   */
  private async getTimeEntriesForPlan(plan: Plan): Promise<TimeEntry[]> {
    // TODO: Integrate with actual time tracking service
    // For now, return mock data
    const mockTimeEntries: TimeEntry[] = [];

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
        status: 'approved',
        clockOutContext: {
          type: index % 2 === 0 ? 'worker_note' : 'ai_summary',
          content: index % 2 === 0
            ? 'Completed assigned tasks for the day'
            : 'AI Summary: Productive work session with focus on core deliverables',
          generatedAt: Date.now() - (index * 24 * 60 * 60 * 1000),
          generatedBy: index % 2 === 0 ? 'worker_' + (index % 3 + 1) : 'ai',
          isImmutable: true
        }
      });
    });

    return mockTimeEntries;
  }

  /**
   * Group time entries by user for reporting
   */
  private groupTimeByUser(timeEntries: TimeEntry[]): Array<{ userName: string; hours: number; entries: number }> {
    const userGroups = new Map<string, { hours: number; entries: number }>();

    timeEntries.forEach(entry => {
      const current = userGroups.get(entry.userName) || { hours: 0, entries: 0 };
      userGroups.set(entry.userName, {
        hours: current.hours + (entry.durationMinutes || 0) / 60,
        entries: current.entries + 1
      });
    });

    return Array.from(userGroups.entries()).map(([userName, data]) => ({
      userName,
      hours: data.hours,
      entries: data.entries
    }));
  }

  /**
   * Get hourly rate for a job (mock implementation)
   */
  private getHourlyRate(jobId: string): number {
    // TODO: Get actual rates from job or user configuration
    const rates: Record<string, number> = {
      'job1': 50,
      'job2': 60,
      'general': 45
    };
    return rates[jobId] || 45;
  }
}

// Export singleton instance
export const outputGeneratorService = new OutputGeneratorService();
