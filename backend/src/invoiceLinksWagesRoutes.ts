/**
 * Phase 4 Slice 3: extracted from api.ts.
 *
 * Invoice-links + wages.
 * authenticateToken is applied by the parent (api.ts).
 *
 * INVOICES — moved to backend/src/invoicesRoutes.ts (migration 017).
 * The fake engagement stubs that used to live here (never persisted)
 * were removed; this router keeps only invoice-links + wages.
 */

import { Router, Request, Response } from 'express';
import { invoiceLinkService } from './invoiceLinks';
import { wageDataService } from './wageData';

export const invoiceLinksWagesRouter = Router();

// INVOICE LINKS
invoiceLinksWagesRouter.post('/invoice-links', async (req: Request, res: Response) => {
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
invoiceLinksWagesRouter.get('/wages', async (req: Request, res: Response) => {
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
