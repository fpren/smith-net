import express from 'express';
import request from 'supertest';
import { idempotency } from '../middleware/idempotency';
import { isPgEnabled } from '../db';

// The middleware persists keys in Postgres; skip when no DB is configured.
const d = isPgEnabled() ? describe : describe.skip;

d('idempotency() middleware', () => {
  // A fresh app per test; the handler bumps a counter so we can prove it ran once.
  function buildApp() {
    const counter = { runs: 0 };
    const app = express();
    app.use(express.json());
    app.use((req: any, _res, next) => { req.user = { id: 'idem-test-user' }; next(); });
    app.post('/thing', idempotency(), (req, res) => {
      counter.runs += 1;
      res.status(201).json({ id: `row-${counter.runs}`, echo: req.body });
    });
    return { app, counter };
  }

  it('runs the handler once and returns the cached response on replay', async () => {
    const { app, counter } = buildApp();
    const key = `idem-${Date.now()}-a`;
    const r1 = await request(app).post('/thing').set('Idempotency-Key', key).send({ x: 1 });
    const r2 = await request(app).post('/thing').set('Idempotency-Key', key).send({ x: 1 });
    expect(r1.status).toBe(201);
    expect(r2.status).toBe(201);
    expect(counter.runs).toBe(1);          // handler ran exactly once
    expect(r2.body).toEqual(r1.body);      // replay returned the cached row, not a new one
    expect(r2.body.id).toBe('row-1');
  });

  it('different keys run independently', async () => {
    const { app, counter } = buildApp();
    await request(app).post('/thing').set('Idempotency-Key', `k-${Date.now()}-1`).send({});
    await request(app).post('/thing').set('Idempotency-Key', `k-${Date.now()}-2`).send({});
    expect(counter.runs).toBe(2);
  });

  it('no Idempotency-Key => handler always runs (passes through)', async () => {
    const { app, counter } = buildApp();
    await request(app).post('/thing').send({});
    await request(app).post('/thing').send({});
    expect(counter.runs).toBe(2);
  });
});
