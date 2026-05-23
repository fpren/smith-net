import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';
import { SummaryArtifact } from '../src/types';
import { encodeLedgerArtifactV2 } from '../src/ledgerCanonical';

const base = { id: 'x', createdAt: 0 }; // id/createdAt are not hashed; present for the type
const inputs: Array<{ label: string; a: SummaryArtifact }> = [
  { label: 'empty', a: { ...base, serial: '', intentVersionId: '', scopeStatement: '', workPerformed: [], laborRecorded: [], materialsUsed: [], contextualNotes: [], totalHours: 0, totalCost: 0, jobIds: [], timeEntryIds: [], chatMessageIds: [] } },
  { label: 'simple', a: { ...base, serial: 'SA-001', intentVersionId: 'iv-1', scopeStatement: 'Fix sink', workPerformed: ['replaced trap'], laborRecorded: ['u1: 30 min'], materialsUsed: ['P-trap'], contextualNotes: ['note'], totalHours: 0.5, totalCost: 27.5, jobIds: ['job-2', 'job-1'], timeEntryIds: ['te-1'], chatMessageIds: [] } },
  { label: 'utf8', a: { ...base, serial: 'SA-café', intentVersionId: 'iv-é', scopeStatement: 'café ☕🔥', workPerformed: ['日本語'], laborRecorded: ['naïve'], materialsUsed: ['Москва'], contextualNotes: ['café'], totalHours: 1.25, totalCost: 100.1, jobIds: ['café', 'ab'], timeEntryIds: ['té-1'], chatMessageIds: ['m1'] } },
  { label: 'big', a: { ...base, serial: 'SA-BIG', intentVersionId: 'iv-9', scopeStatement: 'big', workPerformed: [], laborRecorded: [], materialsUsed: [], contextualNotes: [], totalHours: 1234.56, totalCost: 1234567.89, jobIds: [], timeEntryIds: [], chatMessageIds: [] } },
];

const vectors = inputs.map(({ label, a }) => {
  const bytes = encodeLedgerArtifactV2(a);
  return {
    label,
    artifact: {
      serial: a.serial, intentVersionId: a.intentVersionId, scopeStatement: a.scopeStatement,
      workPerformed: a.workPerformed, laborRecorded: a.laborRecorded, materialsUsed: a.materialsUsed,
      contextualNotes: a.contextualNotes, totalCost: a.totalCost, totalHours: a.totalHours,
      jobIds: a.jobIds, timeEntryIds: a.timeEntryIds, chatMessageIds: a.chatMessageIds,
    },
    canonicalHex: bytes.toString('hex'),
    hashHex: crypto.createHash('sha256').update(bytes).digest('hex'),
  };
});

const json = JSON.stringify({ vectors }, null, 2) + '\n';
const outPath = path.resolve(__dirname, '../../core/testdata/ledger-golden.json');
const androidPath = path.resolve(
  __dirname, '../../android/app/src/androidTest/assets/ledger-golden.json');
for (const p of [outPath, androidPath]) {
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, json);
}
console.log(`wrote ${vectors.length} vectors to:\n  ${outPath}\n  ${androidPath}`);
