/**
 * Phase 4 Slice 3: extracted from api.ts.
 *
 * Engagements + invoices + invoice-links + wages.
 * authenticateToken is applied by the parent (api.ts).
 */

import { Router, Request, Response } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { invoiceLinkService } from './invoiceLinks';
import { wageDataService } from './wageData';
import { AuthenticatedRequest } from './auth';
import { Engagement, CreateEngagementRequest } from './types';

export const engagementsInvoicesRouter = Router();

// ENGAGEMENTS
engagementsInvoicesRouter.post('/engagements', (req: Request, res: Response) => {
  const { name, description, clientName, location, intent } = req.body as CreateEngagementRequest;

  if (!name || !intent) {
    return res.status(400).json({ error: 'name and intent required' });
  }

  const creatorId = (req as AuthenticatedRequest).user!.id;

  const engagement: Engagement = {
    id: uuidv4(),
    name,
    description,
    clientName,
    location,
    createdBy: creatorId,
    createdAt: Date.now(),
    status: 'active',
    intent
  };

  // TODO: Store in database
  console.log('[API] Created engagement:', engagement.id);

  res.status(201).json(engagement);
});

engagementsInvoicesRouter.get('/engagements', (_req: Request, res: Response) => {
  // TODO: Fetch from database
  res.json([]);
});

engagementsInvoicesRouter.get('/engagements/:id', (_req: Request, res: Response) => {
  // TODO: Fetch from database
  res.status(404).json({ error: 'Engagement not found' });
});

// INVOICES
engagementsInvoicesRouter.get('/invoices', (_req: Request, res: Response) => {
  // TODO: Fetch invoices from database
  res.json([]);
});

engagementsInvoicesRouter.get('/invoices/:id', (_req: Request, res: Response) => {
  // TODO: Fetch invoice from database
  res.status(404).json({ error: 'Invoice not found' });
});

engagementsInvoicesRouter.patch('/invoices/:id/status', (req: Request, res: Response) => {
  const { id } = req.params;
  const { status } = req.body;

  // TODO: Update invoice status
  console.log('[API] Updated invoice', id, 'status to:', status);

  res.json({ status: 'updated' });
});

// INVOICE LINKS
engagementsInvoicesRouter.post('/invoice-links', async (req: Request, res: Response) => {
  try {
    const result = await invoiceLinkService.createInvoiceLink(req.body);
    if (!result) {
      return res.status(500).json({ error: 'Failed to create invoice link' });
    }
    res.status(201).json(result);
  } catch (err) {
    res.status(500).json({ error: 'Failed to create invoice link' });
  }
});

// WAGES — Get BLS wage data by zip code and SOC code
// GET /api/wages?zip=78701&soc=47-2111
engagementsInvoicesRouter.get('/wages', async (req: Request, res: Response) => {
  const { zip, soc } = req.query as { zip?: string; soc?: string };

  if (!zip || !soc) {
    return res.status(400).json({ error: 'zip and soc query parameters are required' });
  }

  try {
    const result = await wageDataService.getWageByZipAndTrade(zip, soc);
    if (!result) {
      return res.status(404).json({ error: 'No wage data found for this zip/trade combination' });
    }
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: 'Failed to retrieve wage data' });
  }
});
