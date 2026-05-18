/**
 * /api/invoices routes — happy paths, tenant isolation, tier gate.
 * Mirrors tasks-routes.test.ts in shape.
 */

import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { invoicesRouter } from '../invoicesRoutes';
import { authenticateToken, generateTokens, UserRole } from '../auth';
import { createUserAndProfile } from '../jobsService';
import { pg, isPgEnabled } from '../db';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api', authenticateToken, invoicesRouter);
  return app;
}

async function createForemanAndLogin(suffix: string) {
  const email = `foreman-inv-${suffix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@example.com`;
  const user = await createUserAndProfile({
    email,
    password: 'password123',
    displayName: `Foreman ${suffix}`,
    role: UserRole.FOREMAN,
  });
  const { accessToken } = await generateTokens(user);
  return { id: user.id, token: accessToken };
}

async function createSoloAndLogin(suffix: string) {
  const email = `solo-inv-${suffix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@example.com`;
  const user = await createUserAndProfile({
    email,
    password: 'password123',
    displayName: `Solo ${suffix}`,
    role: UserRole.SOLO,
  });
  const { accessToken } = await generateTokens(user);
  return { id: user.id, token: accessToken };
}

afterEach(async () => {
  if (!isPgEnabled() || !pg) return;
  await pg.query(`DELETE FROM invoice_line_items`);
  await pg.query(`DELETE FROM invoices`);
  await pg.query(`DELETE FROM profiles WHERE email LIKE 'foreman-inv-%' OR email LIKE 'solo-inv-%'`);
  await pg.query(`DELETE FROM users WHERE email LIKE 'foreman-inv-%' OR email LIKE 'solo-inv-%'`);
});

afterAll(async () => { await pg?.end(); });

describeDb('/api/invoices routes', () => {
  const app = buildApp();

  it('POST /api/invoices creates an invoice with INV-YYYY-0001 in the caller\'s org', async () => {
    const f = await createForemanAndLogin('post');
    const res = await request(app)
      .post('/api/invoices')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ clientName: 'Acme Roofing', clientEmail: 'ops@acme.com' });
    expect(res.status).toBe(201);
    const inv = res.body.invoice;
    expect(inv.invoiceNumber).toMatch(/^INV-\d{4}-0001$/);
    expect(inv.organizationId).toBe(f.id);
    expect(inv.status).toBe('draft');
    expect(Number(inv.subtotal)).toBe(0);
    expect(Number(inv.totalDue)).toBe(0);
  });

  it('Adding line items recomputes subtotal, tax, total', async () => {
    const f = await createForemanAndLogin('lines');
    const created = await request(app)
      .post('/api/invoices')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ clientName: 'X' });
    const id = created.body.invoice.id;

    // tax rate 0.0825
    await request(app).patch(`/api/invoices/${id}`).set('Authorization', `Bearer ${f.token}`).send({ taxRate: 0.0825 });

    // Labor 4h × $85
    await request(app)
      .post(`/api/invoices/${id}/line-items`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ description: 'Labor', quantity: 4, unit: 'hr', rate: 85, category: 'labor' });
    // Materials lot × $230
    await request(app)
      .post(`/api/invoices/${id}/line-items`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ description: 'Materials', quantity: 1, unit: 'lot', rate: 230, category: 'materials' });

    const got = await request(app).get(`/api/invoices/${id}`).set('Authorization', `Bearer ${f.token}`);
    expect(got.status).toBe(200);
    expect(got.body.lineItems).toHaveLength(2);
    // Subtotal: 4 × 85 + 1 × 230 = 570
    expect(Number(got.body.invoice.subtotal)).toBe(570);
    // 570 × 0.0825 = 47.025 → rounded to 47.03
    expect(Number(got.body.invoice.taxAmount)).toBe(47.03);
    expect(Number(got.body.invoice.totalDue)).toBe(617.03);
  });

  it('Foreman B sees only their own invoices, not Foreman A\'s', async () => {
    const a = await createForemanAndLogin('xa');
    const b = await createForemanAndLogin('xb');
    await request(app).post('/api/invoices').set('Authorization', `Bearer ${a.token}`).send({ clientName: 'A-client' });
    await request(app).post('/api/invoices').set('Authorization', `Bearer ${b.token}`).send({ clientName: 'B-client' });

    const listB = await request(app).get('/api/invoices').set('Authorization', `Bearer ${b.token}`);
    expect(listB.status).toBe(200);
    expect(listB.body.invoices).toHaveLength(1);
    expect(listB.body.invoices[0].clientName).toBe('B-client');
  });

  it('Foreman B fetching A\'s invoice by id returns 404 (no leak)', async () => {
    const a = await createForemanAndLogin('xidA');
    const b = await createForemanAndLogin('xidB');
    const created = await request(app)
      .post('/api/invoices')
      .set('Authorization', `Bearer ${a.token}`)
      .send({ clientName: 'A' });
    const id = created.body.invoice.id;
    const res = await request(app).get(`/api/invoices/${id}`).set('Authorization', `Bearer ${b.token}`);
    expect(res.status).toBe(404);
  });

  it('Foreman B cannot PATCH or DELETE A\'s invoice', async () => {
    const a = await createForemanAndLogin('xpA');
    const b = await createForemanAndLogin('xpB');
    const id = (await request(app).post('/api/invoices').set('Authorization', `Bearer ${a.token}`).send({})).body.invoice.id;

    const p = await request(app).patch(`/api/invoices/${id}`).set('Authorization', `Bearer ${b.token}`).send({ clientName: 'hijack' });
    expect(p.status).toBe(404);
    const d = await request(app).delete(`/api/invoices/${id}`).set('Authorization', `Bearer ${b.token}`);
    expect(d.status).toBe(404);
  });

  it('Foreman B cannot delete A\'s line item', async () => {
    const a = await createForemanAndLogin('xlA');
    const b = await createForemanAndLogin('xlB');
    const inv = (await request(app).post('/api/invoices').set('Authorization', `Bearer ${a.token}`).send({})).body.invoice;
    const item = (await request(app)
      .post(`/api/invoices/${inv.id}/line-items`)
      .set('Authorization', `Bearer ${a.token}`)
      .send({ description: 'X', rate: 1 })).body.lineItem;

    const res = await request(app).delete(`/api/line-items/${item.id}`).set('Authorization', `Bearer ${b.token}`);
    expect(res.status).toBe(404);
  });

  it('Solo worker can post to /api/invoices (no longer tier-gated)', async () => {
    const solo = await createSoloAndLogin('s');
    const list = await request(app).get('/api/invoices').set('Authorization', `Bearer ${solo.token}`);
    expect(list.status).toBe(200);
    expect(list.body.invoices).toEqual([]);

    const create = await request(app).post('/api/invoices').set('Authorization', `Bearer ${solo.token}`).send({ clientName: 'Foo' });
    expect(create.status).toBe(201);
    expect(create.body.invoice.invoiceNumber).toMatch(/^INV-\d{4}-0001$/);
  });

  it('PATCH /api/invoices/:id/status flips status', async () => {
    const f = await createForemanAndLogin('flow');
    const id = (await request(app).post('/api/invoices').set('Authorization', `Bearer ${f.token}`).send({})).body.invoice.id;

    const issued = await request(app).patch(`/api/invoices/${id}/status`).set('Authorization', `Bearer ${f.token}`).send({ status: 'issued' });
    expect(issued.body.invoice.status).toBe('issued');

    const paid = await request(app).patch(`/api/invoices/${id}/status`).set('Authorization', `Bearer ${f.token}`).send({ status: 'paid' });
    expect(paid.body.invoice.status).toBe('paid');
  });

  it('Soft delete: deleted invoices are hidden from list and GET returns 404', async () => {
    const f = await createForemanAndLogin('del');
    const id = (await request(app).post('/api/invoices').set('Authorization', `Bearer ${f.token}`).send({})).body.invoice.id;

    await request(app).delete(`/api/invoices/${id}`).set('Authorization', `Bearer ${f.token}`);
    const get = await request(app).get(`/api/invoices/${id}`).set('Authorization', `Bearer ${f.token}`);
    expect(get.status).toBe(404);
    const list = await request(app).get('/api/invoices').set('Authorization', `Bearer ${f.token}`);
    expect(list.body.invoices).toEqual([]);
  });

  it('Updating a line item recomputes totals', async () => {
    const f = await createForemanAndLogin('lineu');
    const inv = (await request(app).post('/api/invoices').set('Authorization', `Bearer ${f.token}`).send({})).body.invoice;
    const item = (await request(app).post(`/api/invoices/${inv.id}/line-items`).set('Authorization', `Bearer ${f.token}`).send({ description: 'X', quantity: 2, rate: 50 })).body.lineItem;

    let got = await request(app).get(`/api/invoices/${inv.id}`).set('Authorization', `Bearer ${f.token}`);
    expect(Number(got.body.invoice.subtotal)).toBe(100);

    await request(app).patch(`/api/line-items/${item.id}`).set('Authorization', `Bearer ${f.token}`).send({ quantity: 5 });
    got = await request(app).get(`/api/invoices/${inv.id}`).set('Authorization', `Bearer ${f.token}`);
    expect(Number(got.body.invoice.subtotal)).toBe(250);
  });

  it('Deleting a line item recomputes totals', async () => {
    const f = await createForemanAndLogin('lined');
    const inv = (await request(app).post('/api/invoices').set('Authorization', `Bearer ${f.token}`).send({})).body.invoice;
    const a = (await request(app).post(`/api/invoices/${inv.id}/line-items`).set('Authorization', `Bearer ${f.token}`).send({ description: 'A', rate: 100 })).body.lineItem;
    await request(app).post(`/api/invoices/${inv.id}/line-items`).set('Authorization', `Bearer ${f.token}`).send({ description: 'B', rate: 200 });

    let got = await request(app).get(`/api/invoices/${inv.id}`).set('Authorization', `Bearer ${f.token}`);
    expect(Number(got.body.invoice.subtotal)).toBe(300);

    await request(app).delete(`/api/line-items/${a.id}`).set('Authorization', `Bearer ${f.token}`);
    got = await request(app).get(`/api/invoices/${inv.id}`).set('Authorization', `Bearer ${f.token}`);
    expect(Number(got.body.invoice.subtotal)).toBe(200);
    expect(got.body.lineItems).toHaveLength(1);
  });
});
