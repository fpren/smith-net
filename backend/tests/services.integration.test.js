/**
 * DB-backed service integration tests.
 * Requires DATABASE_URL set and the migrations applied.
 *
 *   node --test backend/tests/services.integration.test.js
 */

const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');

// Allow running from repo root or backend/
process.env.DATABASE_URL = process.env.DATABASE_URL
  || 'postgres://smith:ci-test-password@localhost:5432/smithnet';

// Load compiled dist modules so we exercise production code paths.
const BACKEND = path.resolve(__dirname, '..', 'dist');
const { pg } = require(path.join(BACKEND, 'db'));
const {
  createIntent,
  proposeIntent,
  confirmIntent,
} = require(path.join(BACKEND, 'intentService'));
const { seal, getLedgerEntry } = require(path.join(BACKEND, 'ledger'));
const { proposalService } = require(path.join(BACKEND, 'proposals'));
const { invoiceLinkService } = require(path.join(BACKEND, 'invoiceLinks'));
const {
  createMessage,
  publish,
  getHistory,
} = require(path.join(BACKEND, 'messageBus'));

test.after(async () => {
  if (pg) await pg.end();
});

test('intentService: create → propose → confirm round-trip', async () => {
  const creator = `u-${Date.now()}`;
  const result = await createIntent('do stuff', [creator], creator, []);
  assert.ok(!('error' in result), 'create should succeed');
  const { intent, version } = result;
  assert.strictEqual(version.status, 'draft');

  const proposed = await proposeIntent(version.id);
  assert.ok(!('error' in proposed));
  assert.strictEqual(proposed.status, 'proposed');

  const confirmed = await confirmIntent(version.id, creator);
  assert.ok(!('error' in confirmed));
  assert.strictEqual(confirmed.status, 'confirmed');
});

test('ledger: seal artifact + retrieve', async () => {
  const artifact = {
    id: require('crypto').randomUUID(),
    serial: `TEST-${Date.now()}`,
    intentVersionId: require('crypto').randomUUID(),
    scopeStatement: 'test scope',
    workPerformed: ['one'],
    laborRecorded: ['one'],
    materialsUsed: [],
    contextualNotes: [],
    totalHours: 1,
    totalCost: 55,
    jobIds: [],
    timeEntryIds: [],
    chatMessageIds: [],
    createdAt: Date.now(),
  };
  const entry = await seal(artifact, 'tester');
  assert.ok(!('error' in entry), 'seal should succeed');
  assert.ok(entry.id);
  const fetched = await getLedgerEntry(entry.id);
  assert.ok(fetched);
  assert.strictEqual(fetched.artifactSerial, artifact.serial);
});

test('proposalService: create + retrieve', async () => {
  const res = await proposalService.createProposal({
    jobId: 'job-1',
    contractorName: 'Smith Electric',
    contractorPhone: '555-0100',
    contractorLicense: 'ELEC-001',
    clientName: 'Test Client',
    clientAddress: '123 Main',
    scope: 'install 6 outlets',
    tasks: ['rough-in'],
    materials: [{ name: 'outlet', quantity: 6, unit: 'ea', unitCost: 4.5, totalCost: 27 }],
    equipment: [],
    laborHours: 3,
    laborRate: 85,
    laborCost: 255,
    materialsCost: 27,
    totalCost: 282,
  });
  assert.ok(res?.uuid, 'create returned uuid');
  const fetched = await proposalService.getByUuid(res.uuid);
  assert.ok(fetched);
  assert.strictEqual(fetched.client_name, 'Test Client');
});

test('invoiceLinkService: create + retrieve', async () => {
  const res = await invoiceLinkService.createInvoiceLink({
    jobId: 'job-1',
    contractorName: 'Smith Electric',
    clientName: 'Test Client',
    hoursWorked: 3,
    hourlyRate: 85,
    laborCost: 255,
    materialsCost: 27,
    totalDue: 282,
  });
  assert.ok(res?.uuid);
  const fetched = await invoiceLinkService.getByUuid(res.uuid);
  assert.ok(fetched);
  assert.strictEqual(Number(fetched.total_due), 282);
});

test('messageBus: publish persists + getHistory returns it', async () => {
  const channelId = `itest-${Date.now()}`;
  const msg = createMessage(channelId, 'u1', 'User One', 'hi', 'ip');
  publish(msg);
  // publish is fire-and-forget; give it a moment
  await new Promise((r) => setTimeout(r, 200));
  const history = await getHistory(channelId, 10);
  assert.ok(history.length >= 1, 'expected message in history');
  assert.strictEqual(history[history.length - 1].content, 'hi');
});
