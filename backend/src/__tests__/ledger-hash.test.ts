import * as crypto from 'crypto';
import { SummaryArtifact, LedgerEntry } from '../types';
import { encodeLedgerArtifactV2, ledgerHashV2 } from '../ledgerCanonical';
import { initSmithCore, sha256 as romSha256 } from '../core/smithCore';
import {
  computeHashV1, computeHashV2, computeHashForVersion, verifyHash,
} from '../ledgerAuthority';

function emptyArtifact(): SummaryArtifact {
  return {
    id: 'x', createdAt: 0, serial: '', intentVersionId: '', scopeStatement: '',
    workPerformed: [], laborRecorded: [], materialsUsed: [], contextualNotes: [],
    totalHours: 0, totalCost: 0, jobIds: [], timeEntryIds: [], chatMessageIds: [],
  };
}
function sampleArtifact(): SummaryArtifact {
  return {
    id: 'a1', createdAt: 123, serial: 'SA-001', intentVersionId: 'iv-1',
    scopeStatement: 'Fix sink', workPerformed: ['replaced trap'],
    laborRecorded: ['u1: 30 min'], materialsUsed: ['P-trap'], contextualNotes: ['note'],
    totalHours: 0.5, totalCost: 27.5, jobIds: ['job-2', 'job-1'],
    timeEntryIds: ['te-1'], chatMessageIds: [],
  };
}

describe('v2 canonical encoding', () => {
  it('empty artifact encodes to the spec header + all-zero body', () => {
    // header "SMC"(534d43) + abi 01 + format 02, then 56 zero bytes (112 hex)
    expect(encodeLedgerArtifactV2(emptyArtifact()).toString('hex'))
      .toBe('534d430102' + '0'.repeat(112));
  });

  it('is deterministic across calls', () => {
    expect(encodeLedgerArtifactV2(sampleArtifact()).equals(encodeLedgerArtifactV2(sampleArtifact())))
      .toBe(true);
  });

  it('sorts id arrays by utf-8 bytes (set semantics)', () => {
    const a = sampleArtifact();
    const b = { ...a, jobIds: ['job-1', 'job-2'] }; // reverse input order
    expect(encodeLedgerArtifactV2(a).equals(encodeLedgerArtifactV2(b))).toBe(true);
  });

  it('golden vector: sampleArtifact encodes to a known hex string', () => {
    // Pins the canonical byte layout. A silent encoding regression produces different
    // bytes on both calls, so the determinism test passes but this one fails.
    // Regen: node -e "const {encodeLedgerArtifactV2}=require('./dist/ledgerCanonical'); ..."
    expect(encodeLedgerArtifactV2(sampleArtifact()).toString('hex')).toBe(
      '534d4301020600000053412d3030310400000069762d31080000004669782073696e6b' +
      '010000000d0000007265706c61636564207472617001000000' +
      '0a00000075313a203330206d696e0100000006000000502d74726170' +
      '01000000040000006e6f7465' +
      'be0a000000000000' + // cents: 2750 LE
      '3200000000000000' + // centihours: 50 LE
      '02000000050000006a6f622d31050000006a6f622d32' + // jobIds sorted
      '010000000400000074652d31' + // timeEntryIds
      '00000000'                  // chatMessageIds empty
    );
  });

  it('ledgerHashV2 == ROM sha == node sha over the canonical bytes', async () => {
    await initSmithCore();
    process.env.SMITHCORE_ENABLED = '1';
    try {
      const a = sampleArtifact();
      const bytes = encodeLedgerArtifactV2(a);
      const node = crypto.createHash('sha256').update(bytes).digest('hex');
      expect(romSha256(bytes).toString('hex')).toBe(node);
      expect(ledgerHashV2(a)).toBe(node);
    } finally {
      delete process.env.SMITHCORE_ENABLED;
    }
  });
});

describe('hash versioning', () => {
  it('computeHashV1 still matches the documented legacy algorithm', () => {
    const a = sampleArtifact();
    const canonical = JSON.stringify({
      serial: a.serial, intentVersionId: a.intentVersionId, scopeStatement: a.scopeStatement,
      workPerformed: a.workPerformed, laborRecorded: a.laborRecorded,
      totalHours: a.totalHours, totalCost: a.totalCost,
      jobIds: [...a.jobIds].sort(), timeEntryIds: [...a.timeEntryIds].sort(),
    });
    const ref = crypto.createHash('sha256').update(canonical).digest('hex');
    expect(computeHashV1(a)).toBe(ref);
  });

  it('computeHashForVersion dispatches and rejects unknown versions', () => {
    const a = sampleArtifact();
    expect(computeHashForVersion(a, 1)).toBe(computeHashV1(a));
    expect(computeHashForVersion(a, 2)).toBe(computeHashV2(a));
    expect(() => computeHashForVersion(a, 99)).toThrow(/hash_version/);
  });

  it('verifyHash validates a good entry and flags a tampered artifact', () => {
    const a = sampleArtifact();
    const entry = {
      id: 'e', artifactSerial: a.serial, artifactId: a.id,
      sha256Hash: computeHashV2(a), actorUuid: 'u', sealedAt: 0, hashVersion: 2,
    } as LedgerEntry;
    expect(verifyHash(entry, a).valid).toBe(true);
    expect(verifyHash(entry, { ...a, totalCost: a.totalCost + 1 }).valid).toBe(false);
  });

  it('verifyHash on a legacy (v1) entry recomputes under v1', () => {
    const a = sampleArtifact();
    const entry = {
      id: 'e', artifactSerial: a.serial, artifactId: a.id,
      sha256Hash: computeHashV1(a), actorUuid: 'u', sealedAt: 0, hashVersion: 1,
    } as LedgerEntry;
    expect(verifyHash(entry, a).valid).toBe(true);
  });

  it('verifyHash flags a tampered artifact for a legacy (v1) entry', () => {
    const a = sampleArtifact();
    const entry = {
      id: 'e', artifactSerial: a.serial, artifactId: a.id,
      sha256Hash: computeHashV1(a), actorUuid: 'u', sealedAt: 0, hashVersion: 1,
    } as LedgerEntry;
    expect(verifyHash(entry, { ...a, scopeStatement: a.scopeStatement + ' X' }).valid).toBe(false);
  });
});
