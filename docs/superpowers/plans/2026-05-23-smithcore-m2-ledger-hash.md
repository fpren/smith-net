# SmithCore M2 — Ledger / Audit Hash Through the ROM — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route the ledger seal hash and the audit-chain checksum through the SmithCore ROM's SHA-256, upgrading the ledger seal to a versioned, drift-proof canonical byte encoding proven identical across the TS backend and the Kotlin client.

**Architecture:** A new host-side canonical encoder (`encodeLedgerArtifactV2`) emits explicit length-prefixed little-endian bytes with a self-describing `SMC` header; those bytes are hashed via a gated ROM/node SHA-256 helper (`sha256HexGated`). New seals stamp `hash_version = 2`; verify dispatches on the stored version so legacy (v1) entries keep verifying. The audit chain is a pure primitive swap (ROM sha == node sha → no chain break). A committed golden-vector fixture gates both the backend Jest encoder and a mirrored Kotlin encoder.

**Tech Stack:** TypeScript (Node/Express, raw `pg`, Jest), C/WASM ROM (reused, no new C), Kotlin (Android, JUnit instrumented test), SQL migration.

**Spec:** `docs/superpowers/specs/2026-05-23-smithcore-m2-ledger-hash-design.md`

**Conventions:** No emoji anywhere (ASCII tokens only). Run backend tests from `backend/`. Commit after every green step.

---

## File Structure

Create:
- `backend/src/sha256Gate.ts` — `sha256HexGated(data: Buffer): string` (ROM-if-enabled-else-node; identical output).
- `backend/src/ledgerCanonical.ts` — `encodeLedgerArtifactV2`, `ledgerHashV2` (the v2 canonical encoder).
- `backend/migrations/020_ledger_hash_version.sql` — add `hash_version` column.
- `backend/scripts/gen-ledger-golden.ts` — generates the golden fixture from the TS encoder.
- `core/testdata/ledger-golden.json` — committed golden vectors (the `roms.sha1` analog).
- `android/app/src/androidTest/assets/ledger-golden.json` — byte-identical copy (drift-guarded).
- `backend/src/__tests__/sha256-gate.test.ts` — gate identity.
- `backend/src/__tests__/ledger-hash.test.ts` — encoder/v1/v2/dispatch/verify/golden/drift.
- `android/app/src/main/java/com/guildofsmiths/trademesh/core/LedgerCanon.kt` — Kotlin mirror + input holder.
- `android/app/src/androidTest/java/com/guildofsmiths/trademesh/core/LedgerCanonParityTest.kt` — cross-host proof.

Modify:
- `backend/src/ledgerAuthority.ts` — split `computeHash` → `computeHashV1`/`computeHashV2`/`computeHashForVersion`; add `verifyHash`; update `validateSealing`.
- `backend/src/ledger.ts` — `seal`/`amend` compute v2 + stamp `hash_version`; `mapLedgerRow` reads it; add `verifyLedgerEntry`.
- `backend/src/types.ts` — add `hashVersion?: number` to `LedgerEntry`.
- `backend/src/auditLog.ts` — `generateChecksum` → `sha256HexGated`.
- `backend/src/workers/auditFlushWorker.ts` — `computeHash` → `sha256HexGated`.
- `backend/src/workers/runner.ts` — `initSmithCore()` at boot.
- `backend/src/phase0Routes.ts` — add `GET /ledger/verify/:id` (+ SECURITY_ALERT on mismatch).
- `core/README.md` — M2 status line.

---

## Task 1: Gated SHA-256 helper (`sha256Gate.ts`)

**Files:**
- Create: `backend/src/sha256Gate.ts`
- Test: `backend/src/__tests__/sha256-gate.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
// backend/src/__tests__/sha256-gate.test.ts
import * as crypto from 'crypto';
import { sha256HexGated } from '../sha256Gate';
import { initSmithCore } from '../core/smithCore';

describe('sha256HexGated', () => {
  afterEach(() => { delete process.env.SMITHCORE_ENABLED; });

  it('equals node crypto with the flag OFF (legacy path)', () => {
    delete process.env.SMITHCORE_ENABLED;
    const d = Buffer.from('hello world', 'utf8');
    expect(sha256HexGated(d)).toBe(crypto.createHash('sha256').update(d).digest('hex'));
  });

  it('equals node crypto with the flag ON + ROM ready', async () => {
    await initSmithCore();
    process.env.SMITHCORE_ENABLED = '1';
    const d = crypto.randomBytes(100);
    expect(sha256HexGated(d)).toBe(crypto.createHash('sha256').update(d).digest('hex'));
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && npx jest sha256-gate -v`
Expected: FAIL — `Cannot find module '../sha256Gate'`.

- [ ] **Step 3: Write minimal implementation**

```ts
// backend/src/sha256Gate.ts
import * as crypto from 'crypto';
import { isSmithCoreReady, sha256 as romSha256 } from './core/smithCore';

/**
 * SHA-256 -> lowercase hex. Routes through the SmithCore ROM when
 * SMITHCORE_ENABLED=1 and the ROM is loaded; otherwise node crypto. Both are
 * byte-identical (proven by the M1 parity gate), so the output never depends on
 * the path -- the flag is a pure rollout lever, not a behavior change.
 */
export function sha256HexGated(data: Buffer): string {
  if (process.env.SMITHCORE_ENABLED === '1' && isSmithCoreReady()) {
    return romSha256(data).toString('hex');
  }
  return crypto.createHash('sha256').update(data).digest('hex');
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && npx jest sha256-gate -v`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/sha256Gate.ts backend/src/__tests__/sha256-gate.test.ts
git commit -m "feat(core): gated ROM/node SHA-256 helper (M2)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: v2 canonical encoder (`ledgerCanonical.ts`)

**Files:**
- Create: `backend/src/ledgerCanonical.ts`
- Test: `backend/src/__tests__/ledger-hash.test.ts` (encoder section)

- [ ] **Step 1: Write the failing test**

```ts
// backend/src/__tests__/ledger-hash.test.ts
import * as crypto from 'crypto';
import { SummaryArtifact } from '../types';
import { encodeLedgerArtifactV2, ledgerHashV2 } from '../ledgerCanonical';
import { initSmithCore, sha256 as romSha256 } from '../core/smithCore';

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

  it('ledgerHashV2 == ROM sha == node sha over the canonical bytes', async () => {
    await initSmithCore();
    process.env.SMITHCORE_ENABLED = '1';
    const a = sampleArtifact();
    const bytes = encodeLedgerArtifactV2(a);
    const node = crypto.createHash('sha256').update(bytes).digest('hex');
    expect(romSha256(bytes).toString('hex')).toBe(node);
    expect(ledgerHashV2(a)).toBe(node);
    delete process.env.SMITHCORE_ENABLED;
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && npx jest ledger-hash -v`
Expected: FAIL — `Cannot find module '../ledgerCanonical'`.

- [ ] **Step 3: Write minimal implementation**

```ts
// backend/src/ledgerCanonical.ts
import { SummaryArtifact } from './types';
import { sha256HexGated } from './sha256Gate';

const ABI = 0x01;
const FORMAT_V2 = 0x02;

function encStr(s: string): Buffer {
  const b = Buffer.from(s, 'utf8');
  const len = Buffer.allocUnsafe(4);
  len.writeUInt32LE(b.length, 0);
  return Buffer.concat([len, b]);
}
function encStrArray(arr: string[]): Buffer {
  const count = Buffer.allocUnsafe(4);
  count.writeUInt32LE(arr.length, 0);
  return Buffer.concat([count, ...arr.map(encStr)]);
}
function encI64(v: bigint): Buffer {
  const b = Buffer.allocUnsafe(8);
  b.writeBigInt64LE(v, 0);
  return b;
}
function sortedByUtf8(arr: string[]): string[] {
  return [...arr].sort((a, b) =>
    Buffer.compare(Buffer.from(a, 'utf8'), Buffer.from(b, 'utf8')));
}

/**
 * v2 canonical encoding of a SummaryArtifact (see the M2 design spec).
 * Self-describing header "SMC" + abi + format, then fixed field order; strings
 * length-prefixed UTF-8, arrays count-prefixed, money/hours as integer minor
 * units (no floating point in the hashed bytes), id sets sorted by utf-8 bytes.
 */
export function encodeLedgerArtifactV2(a: SummaryArtifact): Buffer {
  const header = Buffer.from([0x53, 0x4d, 0x43, ABI, FORMAT_V2]); // "SMC"
  const cents = BigInt(Math.round(a.totalCost * 100));
  const centihours = BigInt(Math.round(a.totalHours * 100));
  return Buffer.concat([
    header,
    encStr(a.serial),
    encStr(a.intentVersionId),
    encStr(a.scopeStatement),
    encStrArray(a.workPerformed),
    encStrArray(a.laborRecorded),
    encStrArray(a.materialsUsed),
    encStrArray(a.contextualNotes),
    encI64(cents),
    encI64(centihours),
    encStrArray(sortedByUtf8(a.jobIds)),
    encStrArray(sortedByUtf8(a.timeEntryIds)),
    encStrArray(sortedByUtf8(a.chatMessageIds)),
  ]);
}

export function ledgerHashV2(a: SummaryArtifact): string {
  return sha256HexGated(encodeLedgerArtifactV2(a));
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && npx jest ledger-hash -v`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/ledgerCanonical.ts backend/src/__tests__/ledger-hash.test.ts
git commit -m "feat(core): v2 ledger canonical encoder -> ROM SHA-256 (M2)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: v1/v2 split + dispatcher + verifyHash (`ledgerAuthority.ts`)

**Files:**
- Modify: `backend/src/ledgerAuthority.ts`
- Test: `backend/src/__tests__/ledger-hash.test.ts` (append authority section)

- [ ] **Step 1: Write the failing test (append to ledger-hash.test.ts)**

```ts
import { LedgerEntry } from '../types';
import {
  computeHashV1, computeHashV2, computeHashForVersion, verifyHash,
} from '../ledgerAuthority';

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
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && npx jest ledger-hash -t "hash versioning" -v`
Expected: FAIL — `computeHashV1` is not exported.

- [ ] **Step 3: Edit `ledgerAuthority.ts`**

Replace the `computeHash` function (currently the only export named that) and update `validateSealing`. Add the imports and the new exports.

```ts
// at top, add:
import { ledgerHashV2 } from './ledgerCanonical';

// validateSealing: change the line `const hash = computeHash(artifact);`
//   to `const hash = computeHashV2(artifact);`

// replace `export function computeHash(...)` with the block below:
/** v1 legacy hash (float-bearing canonical JSON). Frozen so pre-M2 entries
 *  keep verifying byte-for-byte. Do NOT change this function. */
export function computeHashV1(artifact: SummaryArtifact): string {
  const canonical = JSON.stringify({
    serial: artifact.serial,
    intentVersionId: artifact.intentVersionId,
    scopeStatement: artifact.scopeStatement,
    workPerformed: artifact.workPerformed,
    laborRecorded: artifact.laborRecorded,
    totalHours: artifact.totalHours,
    totalCost: artifact.totalCost,
    jobIds: artifact.jobIds.sort(),
    timeEntryIds: artifact.timeEntryIds.sort(),
  });
  return crypto.createHash('sha256').update(canonical).digest('hex');
}

/** v2 hash: canonical byte encoding through the ROM SHA-256. */
export function computeHashV2(artifact: SummaryArtifact): string {
  return ledgerHashV2(artifact);
}

export function computeHashForVersion(artifact: SummaryArtifact, version: number): string {
  if (version === 1) return computeHashV1(artifact);
  if (version === 2) return computeHashV2(artifact);
  throw new Error(`unknown ledger hash_version ${version}`);
}

export interface LedgerVerifyResult {
  valid: boolean;
  expected: string;
  actual: string;
  hashVersion: number;
}

/** Pure tamper check: recompute the entry's hash under its stored version. */
export function verifyHash(entry: LedgerEntry, artifact: SummaryArtifact): LedgerVerifyResult {
  const hashVersion = entry.hashVersion ?? 1;
  const actual = computeHashForVersion(artifact, hashVersion);
  return { valid: entry.sha256Hash === actual, expected: entry.sha256Hash, actual, hashVersion };
}
```

- [ ] **Step 4: Fix the importer in `ledger.ts`**

`ledger.ts` imports `computeHash` — update its import line to use the v2 function (full wiring happens in Task 4, but the import must compile now):

```ts
// change: import { validateSealing, validateAmendment, computeHash } from './ledgerAuthority';
// to:
import { validateSealing, validateAmendment, computeHashV2 } from './ledgerAuthority';
// and change both `computeHash(...)` call sites in seal()/amend() to `computeHashV2(...)`
```

- [ ] **Step 5: Run tests + typecheck**

Run: `cd backend && npx jest ledger-hash -v && npx tsc --noEmit -p tsconfig.json`
Expected: PASS; no type errors. (If `tsc` flags `computeHash` used elsewhere, run `grep -rn "computeHash\b" src` and update those call sites to `computeHashV2`.)

- [ ] **Step 6: Commit**

```bash
git add backend/src/ledgerAuthority.ts backend/src/ledger.ts backend/src/__tests__/ledger-hash.test.ts
git commit -m "feat(core): split ledger hash into v1/v2 + version dispatch + verifyHash (M2)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: hash_version column + seal/amend stamping + verifyLedgerEntry

**Files:**
- Modify: `backend/src/types.ts` (LedgerEntry), `backend/src/ledger.ts`
- Create: `backend/migrations/020_ledger_hash_version.sql`

- [ ] **Step 1: Add the column to the `LedgerEntry` type**

In `backend/src/types.ts`, inside `interface LedgerEntry`, add after `sealedAt: number;`:

```ts
  hashVersion?: number;   // ledger hash format; 1 = legacy, 2 = ROM canonical (M2)
```

- [ ] **Step 2: Write the migration**

```sql
-- backend/migrations/020_ledger_hash_version.sql
-- M2: per-entry ledger hash format version. Existing rows are v1 (legacy
-- float-JSON canonicalization); new seals are v2 (ROM canonical byte encoding).
ALTER TABLE ledger_entries
  ADD COLUMN IF NOT EXISTS hash_version SMALLINT NOT NULL DEFAULT 1;
```

- [ ] **Step 3: Wire seal/amend to stamp v2 and map the column**

In `backend/src/ledger.ts`:

In `seal()`, change the INSERT to include `hash_version`:

```ts
  await db.query(
    `INSERT INTO ledger_entries
       (id, artifact_serial, artifact_id, sha256_hash, blockchain_ref, actor_uuid, sealed_at, hash_version)
     VALUES ($1, $2, $3, $4, $5, $6, to_timestamp($7/1000.0), 2)`,
    [entry.id, entry.artifactSerial, entry.artifactId, entry.sha256Hash, entry.blockchainRef || null, entry.actorUuid, entry.sealedAt]
  );
```

In `amend()`, change the INSERT similarly:

```ts
  await db.query(
    `INSERT INTO ledger_entries
       (id, artifact_serial, artifact_id, sha256_hash, actor_uuid, supersedes, sealed_at, hash_version)
     VALUES ($1, $2, $3, $4, $5, $6, to_timestamp($7/1000.0), 2)`,
    [newEntry.id, newEntry.artifactSerial, newEntry.artifactId, newEntry.sha256Hash, newEntry.actorUuid, newEntry.supersedes, newEntry.sealedAt]
  );
```

In `mapLedgerRow()`, add to the returned object:

```ts
    hashVersion: row.hash_version ?? 1,
```

- [ ] **Step 4: Add `verifyLedgerEntry` to `ledger.ts`**

Add imports at the top and the function at the end:

```ts
import { getArtifact } from './synthesizer';
import { verifyHash, LedgerVerifyResult } from './ledgerAuthority';

/** Recompute a sealed entry's hash from its artifact and compare (tamper check). */
export async function verifyLedgerEntry(
  id: string
): Promise<LedgerVerifyResult | { error: string }> {
  const entry = await getLedgerEntry(id);
  if (!entry) return { error: 'Ledger entry not found' };
  const artifact = await getArtifact(entry.artifactId);
  if (!artifact) return { error: 'Sealed artifact not found' };
  return verifyHash(entry, artifact);
}
```

(`verifyHash` may already be imported via the Task 3 import line; keep a single import statement from `./ledgerAuthority`.)

- [ ] **Step 5: Typecheck**

Run: `cd backend && npx tsc --noEmit -p tsconfig.json`
Expected: no errors.

- [ ] **Step 6: Apply the migration (when a database is available)**

Run: `psql "$DATABASE_URL" -f backend/migrations/020_ledger_hash_version.sql`
Expected: `ALTER TABLE`. (Idempotent via `IF NOT EXISTS`. Skip if no DB in this environment; the unit tests do not touch pg.)

- [ ] **Step 7: Commit**

```bash
git add backend/src/types.ts backend/src/ledger.ts backend/migrations/020_ledger_hash_version.sql
git commit -m "feat(core): stamp hash_version=2 on seal/amend + verifyLedgerEntry (M2)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: version-aware verify endpoint (`/api/ledger/verify/:id`)

**Files:**
- Modify: `backend/src/phase0Routes.ts`

- [ ] **Step 1: Add the route**

Update the ledger import line and add the audit import near the other imports:

```ts
// change: import { seal, amend, getLedgerEntry } from './ledger';
import { seal, amend, getLedgerEntry, verifyLedgerEntry } from './ledger';
import { auditLog, AuditAction } from './auditLog';
```

Add the route next to the other `/ledger/...` routes (place it BEFORE `phase0Router.get('/ledger/:id', ...)` so `verify` is not captured by the `:id` param):

```ts
// VERIFY: recompute a sealed entry's hash and compare (tamper detection).
phase0Router.get('/ledger/verify/:id', async (req: Request, res: Response) => {
  const result = await verifyLedgerEntry(req.params.id);
  if ('error' in result) return res.status(404).json(result);
  if (!result.valid) {
    await auditLog.log(AuditAction.SECURITY_ALERT, 'system', {
      reason: 'ledger_verify_mismatch',
      entryId: req.params.id,
      expected: result.expected,
      actual: result.actual,
      hashVersion: result.hashVersion,
    });
  }
  return res.status(200).json(result);
});
```

- [ ] **Step 2: Typecheck**

Run: `cd backend && npx tsc --noEmit -p tsconfig.json`
Expected: no errors.

- [ ] **Step 3: Manual smoke test (when a DB + a sealed entry exist)**

Run (replace `<id>` with a real ledger entry id):
```bash
cd backend && SMITHCORE_ENABLED=1 npm run dev &   # then, in another shell:
curl -s localhost:3000/api/ledger/verify/<id> | jq
```
Expected: `{ "valid": true, "expected": "...", "actual": "...", "hashVersion": 2 }`.
(Skip if no DB in this environment — the verify logic is unit-tested via `verifyHash` in Task 3.)

- [ ] **Step 4: Commit**

```bash
git add backend/src/phase0Routes.ts
git commit -m "feat(core): version-aware GET /api/ledger/verify/:id with tamper audit (M2)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: route the audit chain through the ROM

**Files:**
- Modify: `backend/src/auditLog.ts`, `backend/src/workers/auditFlushWorker.ts`

- [ ] **Step 1: Edit `auditLog.ts` `generateChecksum`**

Replace the body of `generateChecksum` (keep the body envelope unchanged) so the final hash goes through the gate:

```ts
  private generateChecksum(entry: Omit<AuditEntry, 'checksum'>): string {
    const body = JSON.stringify({
      id: entry.id,
      timestamp: entry.timestamp,
      action: entry.action,
      actorId: entry.actorId,
      targetId: entry.targetId,
      metadata: entry.metadata,
    });
    const seed = (entry.prevChecksum ?? '') + body;
    return sha256HexGated(Buffer.from(seed, 'utf8'));
  }
```

Add the import at the top of `auditLog.ts`:

```ts
import { sha256HexGated } from './sha256Gate';
```

(Remove the now-unused `const crypto = require('crypto');` line inside the old function.)

- [ ] **Step 2: Edit `workers/auditFlushWorker.ts` `computeHash`**

Replace its hashing line (currently `return crypto.createHash('sha256').update((prev ?? '') + body).digest('hex');`) with:

```ts
  return sha256HexGated(Buffer.from((prev ?? '') + body, 'utf8'));
```

Add the import at the top:

```ts
import { sha256HexGated } from '../sha256Gate';
```

(Leave the `body` construction untouched so the envelope still matches `auditLog.generateChecksum`.)

- [ ] **Step 3: Run the audit chain test (must stay green — proves identical output)**

Run: `cd backend && npx jest auditChain -v`
Expected: PASS — output is byte-identical to node crypto, so the existing chain assertions still hold.

- [ ] **Step 4: Run it again with the ROM path active**

Run: `cd backend && SMITHCORE_ENABLED=1 npx jest auditChain sha256-gate -v`
Expected: PASS. (Confirms the chain is identical whether the ROM or node computes it.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/auditLog.ts backend/src/workers/auditFlushWorker.ts
git commit -m "feat(core): route audit-chain checksum through the ROM SHA-256 (M2)

Identical bytes to node crypto -> existing chain stays valid, no version.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: initialize the ROM in the worker process

**Files:**
- Modify: `backend/src/workers/runner.ts`

- [ ] **Step 1: Inspect current boot**

Run: `cd backend && sed -n '1,40p' src/workers/runner.ts`
Expected: see the worker entry point (the `tsx src/workers/runner.ts` target).

- [ ] **Step 2: Add ROM init at boot**

At the top of `runner.ts` add the import:

```ts
import { initSmithCore } from '../core/smithCore';
```

In the worker's startup sequence (before it begins draining jobs — mirror how `server.ts` awaits `initSmithCore()` at boot), add:

```ts
  try {
    await initSmithCore();
    console.log('[worker] smithcore ROM loaded');
  } catch (e) {
    console.warn('[worker] smithcore ROM not loaded; audit hashing falls back to node crypto', e);
  }
```

(If `runner.ts`'s top level is not already `async`, wrap the startup in an `async function main() { ... } main();` as `server.ts` does. Match the existing style in `server.ts`.)

- [ ] **Step 3: Typecheck + boot smoke (optional, needs env)**

Run: `cd backend && npx tsc --noEmit -p tsconfig.json`
Expected: no errors.
Optional: `cd backend && SMITHCORE_ENABLED=1 npm run worker` → log shows `[worker] smithcore ROM loaded` (Ctrl-C to stop).

- [ ] **Step 4: Commit**

```bash
git add backend/src/workers/runner.ts
git commit -m "feat(core): load the ROM in the worker so audit flush hashes through it (M2)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: golden fixture + generator + drift guard

**Files:**
- Create: `backend/scripts/gen-ledger-golden.ts`, `core/testdata/ledger-golden.json`
- Test: `backend/src/__tests__/ledger-hash.test.ts` (append golden section)

- [ ] **Step 1: Write the generator**

```ts
// backend/scripts/gen-ledger-golden.ts
import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';
import { SummaryArtifact } from '../src/types';
import { encodeLedgerArtifactV2 } from '../src/ledgerCanonical';

const base = { id: 'x', createdAt: 0 }; // id/createdAt are not hashed; present for the type
const inputs: Array<{ label: string; a: SummaryArtifact }> = [
  { label: 'empty', a: { ...base, serial: '', intentVersionId: '', scopeStatement: '', workPerformed: [], laborRecorded: [], materialsUsed: [], contextualNotes: [], totalHours: 0, totalCost: 0, jobIds: [], timeEntryIds: [], chatMessageIds: [] } },
  { label: 'simple', a: { ...base, serial: 'SA-001', intentVersionId: 'iv-1', scopeStatement: 'Fix sink', workPerformed: ['replaced trap'], laborRecorded: ['u1: 30 min'], materialsUsed: ['P-trap'], contextualNotes: ['note'], totalHours: 0.5, totalCost: 27.5, jobIds: ['job-2', 'job-1'], timeEntryIds: ['te-1'], chatMessageIds: [] } },
  { label: 'utf8', a: { ...base, serial: 'SA-coffee', intentVersionId: 'iv-e', scopeStatement: 'cafe ☕', workPerformed: ['cle'], laborRecorded: [], materialsUsed: ['naive'], contextualNotes: [], totalHours: 1.25, totalCost: 100.1, jobIds: ['cafe', 'ab'], timeEntryIds: [], chatMessageIds: ['m1'] } },
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

const outPath = path.resolve(__dirname, '../../core/testdata/ledger-golden.json');
fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, JSON.stringify({ vectors }, null, 2) + '\n');
console.log(`wrote ${vectors.length} vectors to ${outPath}`);
```

- [ ] **Step 2: Generate the fixture + the Android copy**

```bash
cd backend && npx tsx scripts/gen-ledger-golden.ts
mkdir -p android/app/src/androidTest/assets   # run from repo root
cp core/testdata/ledger-golden.json android/app/src/androidTest/assets/ledger-golden.json
```
Expected: `wrote 4 vectors ...`, and both JSON files exist.

- [ ] **Step 3: Append the golden + drift-guard tests (ledger-hash.test.ts)**

```ts
import * as fs from 'fs';
import * as path from 'path';

describe('golden vectors (the roms.sha1 analog)', () => {
  const coreGoldenPath = path.resolve(__dirname, '../../../core/testdata/ledger-golden.json');
  const androidGoldenPath = path.resolve(
    __dirname, '../../../android/app/src/androidTest/assets/ledger-golden.json');

  it('encoder + hash reproduce every committed golden vector', () => {
    const golden = JSON.parse(fs.readFileSync(coreGoldenPath, 'utf8'));
    for (const v of golden.vectors) {
      const a = { id: 'x', createdAt: 0, ...v.artifact } as SummaryArtifact;
      expect(encodeLedgerArtifactV2(a).toString('hex')).toBe(v.canonicalHex);
      expect(crypto.createHash('sha256').update(Buffer.from(v.canonicalHex, 'hex')).digest('hex'))
        .toBe(v.hashHex);
    }
  });

  it('the Android golden copy is byte-identical (drift guard)', () => {
    const core = fs.readFileSync(coreGoldenPath);
    const android = fs.readFileSync(androidGoldenPath);
    expect(core.equals(android)).toBe(true);
  });
});
```

- [ ] **Step 4: Run tests**

Run: `cd backend && npx jest ledger-hash -v`
Expected: PASS (encoder + golden + drift guard all green).

- [ ] **Step 5: Commit**

```bash
git add backend/scripts/gen-ledger-golden.ts core/testdata/ledger-golden.json \
  android/app/src/androidTest/assets/ledger-golden.json backend/src/__tests__/ledger-hash.test.ts
git commit -m "test(core): committed golden ledger vectors + drift guard (M2)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Kotlin v2 encoder mirror (`LedgerCanon.kt`)

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/core/LedgerCanon.kt`

- [ ] **Step 1: Write the Kotlin encoder + input holder**

```kotlin
// android/app/src/main/java/com/guildofsmiths/trademesh/core/LedgerCanon.kt
package com.guildofsmiths.trademesh.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The 12 hashed fields of a SummaryArtifact (id/createdAt are not hashed). */
data class LedgerArtifactInput(
    val serial: String,
    val intentVersionId: String,
    val scopeStatement: String,
    val workPerformed: List<String>,
    val laborRecorded: List<String>,
    val materialsUsed: List<String>,
    val contextualNotes: List<String>,
    val totalCost: Double,
    val totalHours: Double,
    val jobIds: List<String>,
    val timeEntryIds: List<String>,
    val chatMessageIds: List<String>,
)

/**
 * Kotlin mirror of backend/src/ledgerCanonical.ts (v2). Pure byte encoding --
 * the ROM only does the SHA-256 over these bytes -- so this runs without WAMR.
 * Proven byte-identical to the TS encoder by core/testdata/ledger-golden.json.
 */
object LedgerCanon {
    private const val ABI: Byte = 0x01
    private const val FORMAT_V2: Byte = 0x02

    fun encode(a: LedgerArtifactInput): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x53, 0x4D, 0x43, ABI, FORMAT_V2)) // "SMC", abi, format
        writeStr(out, a.serial)
        writeStr(out, a.intentVersionId)
        writeStr(out, a.scopeStatement)
        writeStrArray(out, a.workPerformed)
        writeStrArray(out, a.laborRecorded)
        writeStrArray(out, a.materialsUsed)
        writeStrArray(out, a.contextualNotes)
        writeI64(out, Math.round(a.totalCost * 100))
        writeI64(out, Math.round(a.totalHours * 100))
        writeStrArray(out, sortedByUtf8(a.jobIds))
        writeStrArray(out, sortedByUtf8(a.timeEntryIds))
        writeStrArray(out, sortedByUtf8(a.chatMessageIds))
        return out.toByteArray()
    }

    private fun u32(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    private fun writeStr(out: ByteArrayOutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        out.write(u32(b.size)); out.write(b)
    }
    private fun writeStrArray(out: ByteArrayOutputStream, arr: List<String>) {
        out.write(u32(arr.size)); for (s in arr) writeStr(out, s)
    }
    private fun writeI64(out: ByteArrayOutputStream, v: Long) {
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array())
    }
    private fun cmpBytes(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val ai = a[i].toInt() and 0xFF
            val bi = b[i].toInt() and 0xFF
            if (ai != bi) return ai - bi
        }
        return a.size - b.size
    }
    private fun sortedByUtf8(arr: List<String>): List<String> =
        arr.sortedWith { x, y -> cmpBytes(x.toByteArray(Charsets.UTF_8), y.toByteArray(Charsets.UTF_8)) }
}
```

- [ ] **Step 2: Compile the Android module**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/core/LedgerCanon.kt
git commit -m "feat(core): Kotlin v2 ledger encoder mirror (M2)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: Android cross-host parity test (`LedgerCanonParityTest.kt`)

**Files:**
- Create: `android/app/src/androidTest/java/com/guildofsmiths/trademesh/core/LedgerCanonParityTest.kt`

- [ ] **Step 1: Write the instrumented test**

```kotlin
// android/app/src/androidTest/java/com/guildofsmiths/trademesh/core/LedgerCanonParityTest.kt
package com.guildofsmiths.trademesh.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cross-host proof (Android half of M2): the Kotlin v2 encoder produces bytes
 * identical to the backend TS encoder for the committed golden vectors. The
 * encoder is pure Kotlin, so this runs without WAMR -- it does not gate on the
 * ROM being vendored.
 */
@RunWith(AndroidJUnit4::class)
class LedgerCanonParityTest {

    private fun loadGolden(): JSONObject {
        // androidTest assets are served from the *test* context, not the app.
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val bytes = ctx.assets.open("ledger-golden.json").use { it.readBytes() }
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    private fun toHex(b: ByteArray): String =
        b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun strList(o: JSONObject, name: String): List<String> {
        val a = o.getJSONArray(name)
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun inputFrom(o: JSONObject) = LedgerArtifactInput(
        serial = o.getString("serial"),
        intentVersionId = o.getString("intentVersionId"),
        scopeStatement = o.getString("scopeStatement"),
        workPerformed = strList(o, "workPerformed"),
        laborRecorded = strList(o, "laborRecorded"),
        materialsUsed = strList(o, "materialsUsed"),
        contextualNotes = strList(o, "contextualNotes"),
        totalCost = o.getDouble("totalCost"),
        totalHours = o.getDouble("totalHours"),
        jobIds = strList(o, "jobIds"),
        timeEntryIds = strList(o, "timeEntryIds"),
        chatMessageIds = strList(o, "chatMessageIds"),
    )

    @Test
    fun kotlinEncoderMatchesGoldenBytes() {
        val vectors = loadGolden().getJSONArray("vectors")
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val input = inputFrom(v.getJSONObject("artifact"))
            assertEquals(
                "vector ${v.getString("label")} canonical bytes",
                v.getString("canonicalHex"),
                toHex(LedgerCanon.encode(input)),
            )
        }
    }
}
```

- [ ] **Step 2: Run the instrumented test (needs an emulator/device)**

Run: `cd android && ./gradlew :app:connectedDebugAndroidTest --tests "*LedgerCanonParityTest"`
Expected: PASS (`kotlinEncoderMatchesGoldenBytes`). If no emulator/device is attached in this environment, compile-only is acceptable: `./gradlew :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL (the test is committed and runs in CI / on-device, mirroring the M1 `SmithCoreParityTest` posture).

- [ ] **Step 3: Commit**

```bash
git add android/app/src/androidTest/java/com/guildofsmiths/trademesh/core/LedgerCanonParityTest.kt
git commit -m "test(core): Android cross-host parity for the v2 ledger encoder (M2)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: full backend gate + README status

**Files:**
- Modify: `core/README.md`

- [ ] **Step 1: Run the entire backend parity + ledger + audit gate**

Run: `cd backend && npx jest smithcore-parity sha256-gate ledger-hash auditChain -v`
Expected: ALL PASS.

- [ ] **Step 2: Run once more with the ROM path forced on**

Run: `cd backend && SMITHCORE_ENABLED=1 npx jest smithcore-parity sha256-gate ledger-hash auditChain -v`
Expected: ALL PASS (identical results — the flag never changes a hash).

- [ ] **Step 3: Update the README status line**

In `core/README.md`, replace the `## Status` bullets with:

```markdown
- M1: vector clock + SHA-256 through the ROM; backend wired + green.
- M1.5: APK size-delta CI gate.
- M2: ledger seal (canonical v2 encoding) + audit-chain checksum hash through the
  ROM; per-entry hash_version with version-aware /api/ledger/verify; golden-vector
  parity across the TS + Kotlin encoders.
- Next: M3 mesh + packed structs, M4 entitlements bitmask, M5 portal/iOS/Pi shells.
```

- [ ] **Step 4: Commit**

```bash
git add core/README.md
git commit -m "docs(core): mark M2 (ledger/audit hash through the ROM) complete

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review (completed during planning)

**Spec coverage:** ledger v2 encoding (Task 2), header/field-order/float fix (Task 2 + golden anchor), field set (Task 2 encoder), hash_version + migration (Task 4), version-aware verify (Tasks 3-5), audit primitive swap (Task 6), worker ROM init (Task 7), flag/readiness gating (Task 1, used throughout), Kotlin mirror (Task 9), golden-vectors-as-truth-file + drift guard (Task 8), cross-host proof (Task 10), parity gate additions (Tasks 1/2/3/8/11), README (Task 11). No new C — spec honored.

**Placeholder scan:** every code/test step contains complete code; commands have expected output. DB- and device-dependent steps (migration apply, curl smoke, connected android test) include explicit "skip if unavailable" fallbacks with the unit-tested guarantee that covers them.

**Type consistency:** `sha256HexGated(Buffer)` used identically in Tasks 1/2/6; `computeHashV1`/`computeHashV2`/`computeHashForVersion`/`verifyHash`/`LedgerVerifyResult` defined in Task 3 and consumed in Tasks 4/5; `encodeLedgerArtifactV2`/`ledgerHashV2` defined in Task 2 and consumed in Tasks 3/8; `LedgerArtifactInput`/`LedgerCanon.encode` defined in Task 9 and consumed in Task 10; golden JSON shape (`{ vectors: [{ label, artifact, canonicalHex, hashHex }] }`) is identical in Tasks 8 and 10.

**Open item resolved:** `/api/ledger/verify` did not exist; it is created in Task 5 (mounted via `apiRouter.use(phase0Router)` → `/api/ledger/verify/:id`), placed before the `/ledger/:id` route to avoid param capture.
