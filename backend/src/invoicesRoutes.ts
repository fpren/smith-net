// backend/src/invoicesRoutes.ts
//
// Replaces the stub /api/invoices handlers that used to live in
// engagementsInvoicesRoutes.ts. Mirrors jobsRoutes.ts in shape:
// requireConsoleTier at router level (foreman+ only); every handler
// passes req.user.organizationId through to invoicesService, which
// enforces the tenant fence in every query.

import { Router, Response } from 'express';
import { AuthenticatedRequest } from './auth';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { validateBody } from './middleware/validate';
import {
  CreateInvoiceBody, UpdateInvoiceBody, SetStatusBody,
  AddLineItemBody, UpdateLineItemBody,
} from './schemas/invoices';
import * as invoicesService from './invoicesService';
import { requestLogger } from './log';

export const invoicesRouter = Router();
invoicesRouter.use(requireConsoleTier);

function org(req: AuthenticatedRequest): string | null {
  return req.user?.organizationId ?? null;
}

invoicesRouter.get('/invoices', async (req: AuthenticatedRequest, res: Response) => {
  try {
    const o = org(req);
    if (!o) return res.status(401).json({ error: 'user missing organization_id' });
    res.json({ invoices: await invoicesService.listByOrg(o) });
  } catch (e: any) {
    requestLogger().error({ event: 'invoices_list_error', err: e }, 'invoices list error');
    res.status(500).json({ error: 'Failed to list invoices' });
  }
});

invoicesRouter.post('/invoices', validateBody(CreateInvoiceBody), async (req: AuthenticatedRequest, res: Response) => {
  try {
    const o = org(req);
    if (!o) return res.status(401).json({ error: 'user missing organization_id' });
    const body = req.body as CreateInvoiceBody;
    const invoice = await invoicesService.create({
      organizationId: o,
      createdBy: req.user!.id,
      clientName: body.clientName,
      clientEmail: body.clientEmail,
      dueDate: body.dueDate ? new Date(body.dueDate) : null,
      notes: body.notes,
    });
    res.status(201).json({ invoice });
  } catch (e: any) {
    requestLogger().error({ event: 'invoices_create_error', err: e }, 'invoices create error');
    res.status(500).json({ error: 'Failed to create invoice' });
  }
});

invoicesRouter.get('/invoices/:id', async (req: AuthenticatedRequest, res: Response) => {
  try {
    const o = org(req);
    if (!o) return res.status(401).json({ error: 'user missing organization_id' });
    const got = await invoicesService.getByIdScoped(req.params.id, o);
    if (!got) return res.status(404).json({ error: 'Invoice not found' });
    res.json(got);
  } catch (e: any) {
    requestLogger().error({ event: 'invoices_get_error', err: e }, 'invoices get error');
    res.status(500).json({ error: 'Failed to load invoice' });
  }
});

invoicesRouter.patch('/invoices/:id', validateBody(UpdateInvoiceBody), async (req: AuthenticatedRequest, res: Response) => {
  try {
    const o = org(req);
    if (!o) return res.status(401).json({ error: 'user missing organization_id' });
    const body = req.body as UpdateInvoiceBody;
    const patch: Parameters<typeof invoicesService.update>[2] = {};
    if (body.clientName !== undefined)  patch.clientName  = body.clientName;
    if (body.clientEmail !== undefined) patch.clientEmail = body.clientEmail;
    if (body.dueDate !== undefined)     patch.dueDate     = body.dueDate ? new Date(body.dueDate) : null;
    if (body.taxRate !== undefined)     patch.taxRate     = body.taxRate;
    if (body.notes !== undefined)       patch.notes       = body.notes;
    const invoice = await invoicesService.update(req.params.id, o, patch);
    if (!invoice) return res.status(404).json({ error: 'Invoice not found' });
    res.json({ invoice });
  } catch (e: any) {
    requestLogger().error({ event: 'invoices_update_error', err: e }, 'invoices update error');
    res.status(500).json({ error: 'Failed to update invoice' });
  }
});

invoicesRouter.patch('/invoices/:id/status', validateBody(SetStatusBody), async (req: AuthenticatedRequest, res: Response) => {
  try {
    const o = org(req);
    if (!o) return res.status(401).json({ error: 'user missing organization_id' });
    const body = req.body as SetStatusBody;
    const invoice = await invoicesService.update(req.params.id, o, { status: body.status });
    if (!invoice) return res.status(404).json({ error: 'Invoice not found' });
    res.json({ invoice });
  } catch (e: any) {
    requestLogger().error({ event: 'invoices_status_error', err: e }, 'invoices status error');
    res.status(500).json({ error: 'Failed to update invoice status' });
  }
});

invoicesRouter.delete('/invoices/:id', async (req: AuthenticatedRequest, res: Response) => {
  try {
    const o = org(req);
    if (!o) return res.status(401).json({ error: 'user missing organization_id' });
    const ok = await invoicesService.softDelete(req.params.id, o);
    if (!ok) return res.status(404).json({ error: 'Invoice not found' });
    res.status(204).send();
  } catch (e: any) {
    requestLogger().error({ event: 'invoices_delete_error', err: e }, 'invoices delete error');
    res.status(500).json({ error: 'Failed to delete invoice' });
  }
});

// ────────────────────────────────────────────────────────────────────
// LINE ITEMS
// ────────────────────────────────────────────────────────────────────

invoicesRouter.post('/invoices/:id/line-items', validateBody(AddLineItemBody), async (req: AuthenticatedRequest, res: Response) => {
  try {
    const o = org(req);
    if (!o) return res.status(401).json({ error: 'user missing organization_id' });
    const item = await invoicesService.addLineItem(req.params.id, o, req.body as AddLineItemBody);
    if (!item) return res.status(404).json({ error: 'Invoice not found' });
    res.status(201).json({ lineItem: item });
  } catch (e: any) {
    requestLogger().error({ event: 'invoice_line_add_error', err: e }, 'invoice line add error');
    res.status(500).json({ error: 'Failed to add line item' });
  }
});

invoicesRouter.patch('/line-items/:itemId', validateBody(UpdateLineItemBody), async (req: AuthenticatedRequest, res: Response) => {
  try {
    const o = org(req);
    if (!o) return res.status(401).json({ error: 'user missing organization_id' });
    const item = await invoicesService.updateLineItem(req.params.itemId, o, req.body as UpdateLineItemBody);
    if (!item) return res.status(404).json({ error: 'Line item not found' });
    res.json({ lineItem: item });
  } catch (e: any) {
    requestLogger().error({ event: 'invoice_line_update_error', err: e }, 'invoice line update error');
    res.status(500).json({ error: 'Failed to update line item' });
  }
});

invoicesRouter.delete('/line-items/:itemId', async (req: AuthenticatedRequest, res: Response) => {
  try {
    const o = org(req);
    if (!o) return res.status(401).json({ error: 'user missing organization_id' });
    const ok = await invoicesService.deleteLineItem(req.params.itemId, o);
    if (!ok) return res.status(404).json({ error: 'Line item not found' });
    res.status(204).send();
  } catch (e: any) {
    requestLogger().error({ event: 'invoice_line_delete_error', err: e }, 'invoice line delete error');
    res.status(500).json({ error: 'Failed to delete line item' });
  }
});
