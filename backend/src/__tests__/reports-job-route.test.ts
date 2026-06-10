import express from 'express';
import request from 'supertest';
import { reportsRouter } from '../reportsRoutes';

// reportsRouter carries no auth of its own (api.ts applies authenticateToken at
// the parent), so it can be mounted bare here -- no DB/token needed.
function buildApp() {
  const app = express();
  app.use(express.json());
  app.use('/api', reportsRouter);
  return app;
}

function binaryParser(res: any, cb: (err: Error | null, body: Buffer) => void) {
  res.setEncoding('binary');
  let data = '';
  res.on('data', (chunk: string) => { data += chunk; });
  res.on('end', () => cb(null, Buffer.from(data, 'binary')));
}

const BODY = {
  jobTitle: '200A Panel Upgrade',
  contractorName: 'Maria R',
  clientName: 'Tony B',
  workSummary: 'Replaced panel, ran feeder.',
  laborHours: 12, laborRate: 85, laborCost: 1020,
  materials: [{ name: '200A Panel', quantity: 1, unit: 'ea', unitCost: 420, total: 420 }],
  materialsCost: 420,
  expenses: [{ category: 'permit_fee', description: 'City permit', amount: 150, vendor: 'NYC DOB', date: '2026-06-01' }],
  expensesTotal: 150,
  taxRate: 8.25, taxAmount: 130.43, total: 1720.43,
};

describe('POST /api/reports/job', () => {
  const app = buildApp();

  it('renders a real PDF (default format)', async () => {
    const res = await request(app)
      .post('/api/reports/job')
      .send(BODY)
      .buffer()
      .parse(binaryParser);
    expect(res.status).toBe(200);
    expect(res.headers['content-type']).toContain('application/pdf');
    expect(res.headers['content-disposition']).toContain('.pdf');
    expect(res.body.slice(0, 4).toString()).toBe('%PDF');
  });

  it('renders a real XLSX with ?format=xlsx', async () => {
    const res = await request(app)
      .post('/api/reports/job?format=xlsx')
      .send(BODY)
      .buffer()
      .parse(binaryParser);
    expect(res.status).toBe(200);
    expect(res.headers['content-type']).toContain('spreadsheetml');
    expect(res.headers['content-disposition']).toContain('.xlsx');
    // XLSX is a zip; magic bytes "PK".
    expect(res.body.slice(0, 2).toString()).toBe('PK');
  });

  it('still renders from a sparse body (defaults fill in)', async () => {
    const res = await request(app)
      .post('/api/reports/job')
      .send({ jobTitle: 'Minimal' })
      .buffer()
      .parse(binaryParser);
    expect(res.status).toBe(200);
    expect(res.body.slice(0, 4).toString()).toBe('%PDF');
  });
});
