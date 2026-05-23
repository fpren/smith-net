import * as fs from 'fs';
import * as path from 'path';
import { SummaryArtifact } from '../types';
import { initSmithCore, ledgerEncode } from '../core/smithCore';
import { packLedgerInput, encodeLedgerArtifactV2Local, ledgerHashV2 } from '../ledgerCanonical';

const goldenPath = path.resolve(__dirname, '../../../core/testdata/ledger-golden.json');
const golden = JSON.parse(fs.readFileSync(goldenPath, 'utf8'));

function artifactFrom(o: any): SummaryArtifact {
  return { id: 'x', createdAt: 0, ...o } as SummaryArtifact;
}

beforeAll(async () => { await initSmithCore(); });

describe('M3a: C ledger encoder parity', () => {
  const prevFlag = process.env.SMITHCORE_ENABLED;

  it('C sc_ledger_encode reproduces every golden vector (bytes + hash)', () => {
    process.env.SMITHCORE_ENABLED = '1';
    try {
      for (const v of golden.vectors) {
        const a = artifactFrom(v.artifact);
        const cBytes = ledgerEncode(packLedgerInput(a));
        expect(`${v.label}:${cBytes.toString('hex')}`).toBe(`${v.label}:${v.canonicalHex}`);
        expect(`${v.label}:${ledgerHashV2(a)}`).toBe(`${v.label}:${v.hashHex}`);
      }
    } finally {
      if (prevFlag === undefined) delete process.env.SMITHCORE_ENABLED;
      else process.env.SMITHCORE_ENABLED = prevFlag;
    }
  });

  it('C encode == host fallback over golden + randomized fuzz', () => {
    for (const v of golden.vectors) {
      const a = artifactFrom(v.artifact);
      expect(ledgerEncode(packLedgerInput(a)).equals(encodeLedgerArtifactV2Local(a))).toBe(true);
    }
    // Deterministic PRNG so any failure reproduces.
    let seed = 0x12345678;
    const rng = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 0x100000000; };
    const pool = ['a', 'ab', 'abc', 'cafe', 'café', '日本', 'm1', 'zz', 'té-1', 'job-1', 'job-2', '\u{10000}'];
    const pick = () => pool[Math.floor(rng() * pool.length)];
    const arr = () => Array.from({ length: Math.floor(rng() * 4) }, pick);
    for (let i = 0; i < 500; i++) {
      const a = artifactFrom({
        serial: pick(), intentVersionId: pick(), scopeStatement: pick(),
        workPerformed: arr(), laborRecorded: arr(), materialsUsed: arr(), contextualNotes: arr(),
        totalCost: Math.floor(rng() * 1e7) / 100, totalHours: Math.floor(rng() * 1e5) / 100,
        jobIds: arr(), timeEntryIds: arr(), chatMessageIds: arr(),
      });
      expect(ledgerEncode(packLedgerInput(a)).equals(encodeLedgerArtifactV2Local(a))).toBe(true);
    }
  });
});
