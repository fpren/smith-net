/**
 * REPORT OUTPUT
 * =============
 *
 * Responsibility: Export, download, share/attach
 * Tech: File system / share API
 */

import { ReportOutput as IReportOutput, RenderedReport } from './types';
import * as fs from 'fs';
import * as path from 'path';

export class ReportOutput implements IReportOutput {

  /**
   * EXPORT REPORT
   * Save report to file system
   */
  async export(report: RenderedReport, destination: string): Promise<void> {
    try {
      // Ensure destination directory exists
      const dir = path.dirname(destination);
      if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
      }

      // Write file
      fs.writeFileSync(destination, report.content);

      console.log(`[ReportOutput] Exported ${report.format} report to: ${destination}`);
    } catch (error) {
      console.error('[ReportOutput] Export failed:', error);
      throw new Error(`Failed to export report: ${error}`);
    }
  }

  /**
   * DOWNLOAD REPORT
   * Prepare report for HTTP download response
   */
  async download(report: RenderedReport): Promise<void> {
    // This would typically be used in an HTTP response context
    // For now, save to a temporary downloads directory
    const downloadsDir = path.join(process.cwd(), 'downloads');
    const filePath = path.join(downloadsDir, report.filename);

    await this.export(report, filePath);

    console.log(`[ReportOutput] Prepared ${report.format} report for download: ${report.filename}`);
  }

  /**
   * SHARE REPORT
   * Send report to recipients via email or other sharing mechanisms
   */
  async share(report: RenderedReport, recipients: string[]): Promise<void> {
    try {
      // TODO: Implement actual sharing mechanism (email, cloud storage, etc.)
      // For now, simulate sharing by logging

      console.log(`[ReportOutput] Sharing ${report.format} report "${report.filename}" with:`);
      recipients.forEach(recipient => {
        console.log(`  - ${recipient}`);
      });

      // Simulate sharing delay
      await new Promise(resolve => setTimeout(resolve, 100));

      // TODO: Implement actual sharing logic:
      // - Email attachment
      // - Cloud storage upload and link sharing
      // - Direct messaging integration
      // - BLE mesh distribution for offline recipients

    } catch (error) {
      console.error('[ReportOutput] Share failed:', error);
      throw new Error(`Failed to share report: ${error}`);
    }
  }

  /**
   * BATCH EXPORT
   * Export multiple reports at once
   */
  async batchExport(reports: RenderedReport[], baseDestination: string): Promise<void> {
    const timestamp = Date.now();
    const batchDir = path.join(baseDestination, `batch_${timestamp}`);

    for (const report of reports) {
      const filePath = path.join(batchDir, report.filename);
      await this.export(report, filePath);
    }

    console.log(`[ReportOutput] Batch exported ${reports.length} reports to: ${batchDir}`);
  }

  /**
   * GET EXPORT FORMATS
   * Return available export formats for a report
   */
  getAvailableFormats(): string[] {
    return ['pdf', 'html', 'xlsx'];
  }

  /**
   * VALIDATE EXPORT DESTINATION
   * Check if destination is writable
   */
  validateDestination(destination: string): boolean {
    try {
      const dir = path.dirname(destination);
      fs.accessSync(dir, fs.constants.W_OK);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * CLEANUP OLD EXPORTS
   * Remove exports older than specified days
   */
  async cleanupOldExports(directory: string, olderThanDays: number = 30): Promise<void> {
    try {
      const files = fs.readdirSync(directory);
      const cutoffTime = Date.now() - (olderThanDays * 24 * 60 * 60 * 1000);

      let cleanedCount = 0;
      for (const file of files) {
        const filePath = path.join(directory, file);
        const stats = fs.statSync(filePath);

        if (stats.mtime.getTime() < cutoffTime) {
          fs.unlinkSync(filePath);
          cleanedCount++;
        }
      }

      console.log(`[ReportOutput] Cleaned up ${cleanedCount} old export files`);
    } catch (error) {
      console.error('[ReportOutput] Cleanup failed:', error);
    }
  }

  /**
   * GET EXPORT STATISTICS
   * Return statistics about exports
   */
  getExportStats(directory: string): { totalFiles: number; totalSize: number; formats: Record<string, number> } {
    try {
      const files = fs.readdirSync(directory);
      let totalSize = 0;
      const formats: Record<string, number> = {};

      for (const file of files) {
        const filePath = path.join(directory, file);
        const stats = fs.statSync(filePath);

        totalSize += stats.size;

        const ext = path.extname(file).toLowerCase();
        formats[ext] = (formats[ext] || 0) + 1;
      }

      return {
        totalFiles: files.length,
        totalSize,
        formats
      };
    } catch {
      return { totalFiles: 0, totalSize: 0, formats: {} };
    }
  }
}

// Export singleton instance
export const reportOutput = new ReportOutput();
