/**
 * Phase 4 Slice 3: extracted from api.ts.
 *
 * Reports: basic CRUD + assemble/render/download/share/generate pipeline.
 * authenticateToken is applied by the parent (api.ts).
 */

import { Router, Request, Response } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { reportAssembler } from './reportAssembler';
import { reportRenderer } from './reportRenderer';
import { reportOutput } from './reportOutput';
import { PlanSnapshot } from './types';
import { normalizeJobReport, renderJobReportPdf, renderJobReportXlsx } from './jobReport';

export const reportsRouter = Router();

// ════════════════════════════════════════════════════════════════════
// PER-JOB REPORT (W5) — client POSTs denormalized data, server returns a
// real PDF (pdfkit) or Excel workbook (exceljs). ?format=pdf|xlsx (default pdf).
// ════════════════════════════════════════════════════════════════════
reportsRouter.post('/reports/job', async (req: Request, res: Response) => {
  const data = normalizeJobReport(req.body);
  const format = String(req.query.format ?? 'pdf').toLowerCase();
  const safe = (data.jobTitle.replace(/[^A-Za-z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 60)) || 'job';
  try {
    if (format === 'xlsx') {
      const buf = await renderJobReportXlsx(data);
      res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
      res.setHeader('Content-Disposition', `attachment; filename="${safe}-report.xlsx"`);
      return res.send(buf);
    }
    const buf = await renderJobReportPdf(data);
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename="${safe}-report.pdf"`);
    return res.send(buf);
  } catch (e: any) {
    console.error('[Reports] job report render failed:', e.message);
    res.status(500).json({ error: 'Failed to render report' });
  }
});

// Basic CRUD

reportsRouter.get('/reports', (_req: Request, res: Response) => {
  // TODO: Fetch reports from database
  res.json([]);
});

reportsRouter.get('/reports/:id', (_req: Request, res: Response) => {
  // TODO: Fetch report from database
  res.status(404).json({ error: 'Report not found' });
});

// ════════════════════════════════════════════════════════════════════
// REPORT GENERATION PIPELINE
// ════════════════════════════════════════════════════════════════════

// ASSEMBLE REPORT FROM PLAN SNAPSHOT
reportsRouter.post('/reports/assemble', async (req: Request, res: Response) => {
  const { planId } = req.body;

  if (!planId) {
    return res.status(400).json({ error: 'planId is required' });
  }

  try {
    // TODO: Fetch actual archived plan snapshot
    const mockSnapshot: PlanSnapshot = {
      id: uuidv4(),
      planId,
      snapshotType: 'output',
      data: {
        id: planId,
        summary: {
          executiveSummary: 'Project completed successfully',
          workPerformed: ['Task 1', 'Task 2'],
          totalHours: 16,
          totalCost: 800
        }
      } as any,
      jobs: [
        { id: 'job1', title: 'Job 1', description: 'Work on job 1', status: 'done' }
      ],
      timeEntries: [
        {
          id: 'time1',
          userName: 'Worker 1',
          durationMinutes: 480,
          jobId: 'job1',
          clockOutContext: { type: 'worker_note', content: 'Completed work', generatedAt: Date.now(), generatedBy: 'worker1', isImmutable: true }
        }
      ],
      messages: [],
      createdAt: Date.now(),
      immutableHash: 'mock_hash'
    };

    const structuredReport = reportAssembler.assembleFromPlanSnapshot(mockSnapshot);

    res.json({
      success: true,
      reportModel: structuredReport
    });
  } catch (error) {
    console.error('[ReportAssembler] Assembly failed:', error);
    res.status(500).json({ error: 'Failed to assemble report' });
  }
});

// RENDER REPORT
reportsRouter.post('/reports/render', async (req: Request, res: Response) => {
  const { reportModel, format } = req.body;

  if (!reportModel || !format) {
    return res.status(400).json({ error: 'reportModel and format are required' });
  }

  if (!['pdf', 'html', 'xlsx'].includes(format)) {
    return res.status(400).json({ error: 'Invalid format. Supported: pdf, html, xlsx' });
  }

  try {
    const renderedReport = reportRenderer.render(reportModel, format);

    res.json({
      success: true,
      renderedReport: {
        id: renderedReport.id,
        format: renderedReport.format,
        filename: renderedReport.filename,
        metadata: renderedReport.metadata,
        contentLength: renderedReport.content.length
      }
    });
  } catch (error) {
    console.error('[ReportRenderer] Rendering failed:', error);
    res.status(500).json({ error: 'Failed to render report' });
  }
});

// DOWNLOAD REPORT
reportsRouter.post('/reports/download', async (req: Request, res: Response) => {
  const { renderedReport } = req.body;

  if (!renderedReport) {
    return res.status(400).json({ error: 'renderedReport is required' });
  }

  try {
    await reportOutput.download(renderedReport);

    res.json({
      success: true,
      message: `Report prepared for download: ${renderedReport.filename}`,
      downloadUrl: `/downloads/${renderedReport.filename}` // Mock URL
    });
  } catch (error) {
    console.error('[ReportOutput] Download failed:', error);
    res.status(500).json({ error: 'Failed to prepare download' });
  }
});

// SHARE REPORT
reportsRouter.post('/reports/share', async (req: Request, res: Response) => {
  const { renderedReport, recipients } = req.body;

  if (!renderedReport || !recipients || !Array.isArray(recipients)) {
    return res.status(400).json({ error: 'renderedReport and recipients array are required' });
  }

  try {
    await reportOutput.share(renderedReport, recipients);

    res.json({
      success: true,
      message: `Report shared with ${recipients.length} recipients`,
      recipients
    });
  } catch (error) {
    console.error('[ReportOutput] Share failed:', error);
    res.status(500).json({ error: 'Failed to share report' });
  }
});

// FULL REPORT PIPELINE — Assemble + Render + Output
reportsRouter.post('/reports/generate', async (req: Request, res: Response) => {
  const { planId, format, outputAction, recipients } = req.body;

  if (!planId || !format) {
    return res.status(400).json({ error: 'planId and format are required' });
  }

  try {
    // Step 1: Get plan snapshot (mock)
    const mockSnapshot: PlanSnapshot = {
      id: uuidv4(),
      planId,
      snapshotType: 'output',
      data: {
        id: planId,
        summary: {
          executiveSummary: 'Project completed successfully',
          workPerformed: ['Task 1', 'Task 2'],
          totalHours: 16,
          totalCost: 800
        }
      } as any,
      jobs: [{ id: 'job1', title: 'Job 1', description: 'Work on job 1', status: 'done' }],
      timeEntries: [{
        id: 'time1',
        userName: 'Worker 1',
        durationMinutes: 480,
        jobId: 'job1',
        clockOutContext: { type: 'worker_note', content: 'Completed work', generatedAt: Date.now(), generatedBy: 'worker1', isImmutable: true }
      }],
      messages: [],
      createdAt: Date.now(),
      immutableHash: 'mock_hash'
    };

    const structuredReport = reportAssembler.assembleFromPlanSnapshot(mockSnapshot);
    const renderedReport = reportRenderer.render(structuredReport, format);

    let outputResult;
    switch (outputAction) {
      case 'download':
        await reportOutput.download(renderedReport);
        outputResult = { action: 'download', filename: renderedReport.filename };
        break;
      case 'share':
        if (!recipients) {
          return res.status(400).json({ error: 'recipients required for share action' });
        }
        await reportOutput.share(renderedReport, recipients);
        outputResult = { action: 'share', recipients: recipients.length };
        break;
      default:
        outputResult = { action: 'rendered', contentLength: renderedReport.content.length };
    }

    res.json({
      success: true,
      pipeline: {
        assembled: true,
        rendered: true,
        output: outputResult
      },
      report: {
        id: renderedReport.id,
        format: renderedReport.format,
        filename: renderedReport.filename
      }
    });
  } catch (error) {
    console.error('[ReportPipeline] Generation failed:', error);
    res.status(500).json({ error: 'Report generation failed' });
  }
});
