import { describe, it, expect } from 'vitest';
import { http, HttpResponse } from 'msw';
import { invoicesClient } from '../invoicesClient';
import { server } from '../../test/msw-server';

describe('invoicesClient', () => {
  it('list returns the invoices array', async () => {
    const r = await invoicesClient.list();
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.invoices).toHaveLength(1);
      expect(r.invoices[0].invoiceNumber).toBe('INV-2026-0001');
    }
  });

  it('create returns the new invoice', async () => {
    const r = await invoicesClient.create({ clientName: 'Bob' });
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.invoice.invoiceNumber).toBe('INV-2026-0002');
      expect(r.invoice.clientName).toBe('Bob');
      expect(r.invoice.status).toBe('draft');
    }
  });

  it('getById returns invoice + lineItems', async () => {
    const r = await invoicesClient.getById('inv-1');
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.invoice.id).toBe('inv-1');
      expect(Array.isArray(r.lineItems)).toBe(true);
    }
  });

  it('addLineItem returns the line item with computed total', async () => {
    const r = await invoicesClient.addLineItem('inv-1', { description: 'Labor', quantity: 4, rate: 85, category: 'labor' });
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.lineItem.total).toBe(340);
      expect(r.lineItem.category).toBe('labor');
    }
  });

  it('setStatus flips the status', async () => {
    const r = await invoicesClient.setStatus('inv-1', 'issued');
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.invoice.status).toBe('issued');
  });

  it('delete returns ok on 204', async () => {
    const r = await invoicesClient.delete('inv-1');
    expect(r.ok).toBe(true);
  });

  it('surfaces error status on non-2xx', async () => {
    server.use(
      http.get('/api/invoices', () =>
        HttpResponse.json({ error: 'tier_required' }, { status: 403 }),
      ),
    );
    const r = await invoicesClient.list();
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.status).toBe(403);
      expect(r.error).toBe('tier_required');
    }
  });
});
