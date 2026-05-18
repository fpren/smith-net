/**
 * /api/invoices — Android idempotency tests.
 * Mirrors invoices-routes.test.ts in shape.
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
  const email = `foreman-andinv-${suffix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@example.com`;
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
  const email = `solo-andinv-${suffix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@example.com`;
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
  await pg.query(`DELETE FROM profiles WHERE email LIKE 'foreman-andinv-%' OR email LIKE 'solo-andinv-%'`);
  await pg.query(`DELETE FROM users    WHERE email LIKE 'foreman-andinv-%' OR email LIKE 'solo-andinv-%'`);
});

afterAll(async () => { await pg?.end(); });

describeDb('POST /api/invoices — summary jsonb', () => {
  const app = buildApp();

  it('round-trips a summary blob unchanged', async () => {
    const f = await createForemanAndLogin('sum-rt');
    const summary = {
      mode: 'ENTERPRISE',
      from: { name: 'Jane', business: 'Acme Trades', trade: 'Foreman' },
      crew: [
        { name: 'Bob', role: 'Journeyman', totalHours: 8.5 },
        { name: 'Sue', role: 'Apprentice', totalHours: 4.0 },
      ],
      dailyBreakdown: [
        { day: 1, totalHours: 7.5, activities: 'Framing south wall' },
      ],
      meshPresence: '97.2% average',
      efficiencyScore: 93,
    };

    const created = await request(app)
      .post('/api/invoices')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ idempotencyKey: 'sum-test-1', clientName: 'BigCo', summary });
    expect([200, 201]).toContain(created.status);

    const fetched = await request(app)
      .get(`/api/invoices/${created.body.invoice.id}`)
      .set('Authorization', `Bearer ${f.token}`);
    expect(fetched.status).toBe(200);
    expect(fetched.body.invoice.summary).toEqual(summary);
  });

  it('accepts a missing summary (null on read)', async () => {
    const f = await createForemanAndLogin('sum-null');
    const created = await request(app)
      .post('/api/invoices')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ idempotencyKey: 'no-sum', clientName: 'Foo' });
    expect(created.status).toBe(201);
    expect(created.body.invoice.summary).toBeNull();
  });
});

describeDb('POST /api/invoices — idempotency', () => {
  const app = buildApp();

  it('returns the same row for a repeated idempotencyKey', async () => {
    const f = await createForemanAndLogin('idem-rep');
    const idemKey = 'fixed-uuid-aaaaaaaaaaaaaaaaaaaaaaa';
    const body = { idempotencyKey: idemKey, clientName: 'Acme Roofing', clientEmail: 'ops@acme.com', notes: 'first' };

    const first = await request(app).post('/api/invoices').set('Authorization', `Bearer ${f.token}`).send(body);
    expect([200, 201]).toContain(first.status);

    const second = await request(app).post('/api/invoices').set('Authorization', `Bearer ${f.token}`).send({ ...body, notes: 'second' });
    expect(second.status).toBe(200);
    expect(second.body.invoice.id).toBe(first.body.invoice.id);
    expect(second.body.invoice.notes).toBe('first');
  });

  it('a different idempotencyKey produces a new row', async () => {
    const f = await createForemanAndLogin('idem-diff');
    const a = await request(app).post('/api/invoices').set('Authorization', `Bearer ${f.token}`).send({ idempotencyKey: 'key-a', clientName: 'A Co' });
    const b = await request(app).post('/api/invoices').set('Authorization', `Bearer ${f.token}`).send({ idempotencyKey: 'key-b', clientName: 'B Co' });
    expect(a.status).toBe(201);
    expect(b.status).toBe(201);
    expect(a.body.invoice.id).not.toBe(b.body.invoice.id);
  });
});

describeDb('POST /api/invoices — solo tier', () => {
  const app = buildApp();

  it('allows a solo user to create an invoice in their org-of-one', async () => {
    const s = await createSoloAndLogin('post');
    const created = await request(app)
      .post('/api/invoices')
      .set('Authorization', `Bearer ${s.token}`)
      .send({ idempotencyKey: 'solo-1', clientName: 'Direct Client' });
    expect(created.status).toBe(201);
    expect(created.body.invoice.invoiceNumber).toMatch(/^INV-\d{4}-\d{4}$/);
  });

  it('solo GET /api/invoices returns only their own org', async () => {
    const s = await createSoloAndLogin('list');
    const f = await createForemanAndLogin('cross');

    await request(app)
      .post('/api/invoices')
      .set('Authorization', `Bearer ${s.token}`)
      .send({ idempotencyKey: 'solo-only', clientName: 'Solo Client' });

    await request(app)
      .post('/api/invoices')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ idempotencyKey: 'foreman-only', clientName: 'Foreman Client' });

    const soloList = await request(app)
      .get('/api/invoices')
      .set('Authorization', `Bearer ${s.token}`);
    expect(soloList.status).toBe(200);
    const names = soloList.body.invoices.map((i: any) => i.clientName);
    expect(names).toContain('Solo Client');
    expect(names).not.toContain('Foreman Client');
  });
});
