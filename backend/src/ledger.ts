import { v4 as uuidv4 } from 'uuid';
import { SummaryArtifact, LedgerEntry } from './types';
import { validateSealing, validateAmendment, computeHash } from './ledgerAuthority';
import { pg, isPgEnabled } from './db';

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[Ledger] Postgres client not initialized');
  return pg;
}

export async function seal(
  artifact: SummaryArtifact, actorUuid: string
): Promise<LedgerEntry | { error: string }> {
  const db = requirePg();
  const validation = validateSealing(artifact);
  if (!validation.valid) return { error: validation.message };

  const hash = computeHash(artifact);
  const entry: LedgerEntry = {
    id: uuidv4(), artifactSerial: artifact.serial, artifactId: artifact.id,
    sha256Hash: hash, actorUuid, sealedAt: Date.now(),
  };

  await db.query(
    `INSERT INTO ledger_entries
       (id, artifact_serial, artifact_id, sha256_hash, blockchain_ref, actor_uuid, sealed_at)
     VALUES ($1, $2, $3, $4, $5, $6, to_timestamp($7/1000.0))`,
    [entry.id, entry.artifactSerial, entry.artifactId, entry.sha256Hash, entry.blockchainRef || null, entry.actorUuid, entry.sealedAt]
  );

  return entry;
}

export async function amend(
  newArtifact: SummaryArtifact, priorEntryId: string, actorUuid: string
): Promise<LedgerEntry | { error: string }> {
  const db = requirePg();
  const { rows: priorRows } = await db.query(`SELECT * FROM ledger_entries WHERE id = $1`, [priorEntryId]);
  if (priorRows.length === 0) return { error: 'Prior Ledger entry not found' };

  const priorEntry = mapLedgerRow(priorRows[0]);
  const validation = validateAmendment(newArtifact, priorEntry);
  if (!validation.valid) return { error: validation.message };

  const hash = computeHash(newArtifact);
  const newEntry: LedgerEntry = {
    id: uuidv4(), artifactSerial: newArtifact.serial, artifactId: newArtifact.id,
    sha256Hash: hash, actorUuid, supersedes: priorEntryId, sealedAt: Date.now(),
  };

  await db.query(`UPDATE ledger_entries SET superseded_by = $1 WHERE id = $2`, [newEntry.id, priorEntryId]);

  await db.query(
    `INSERT INTO ledger_entries
       (id, artifact_serial, artifact_id, sha256_hash, actor_uuid, supersedes, sealed_at)
     VALUES ($1, $2, $3, $4, $5, $6, to_timestamp($7/1000.0))`,
    [newEntry.id, newEntry.artifactSerial, newEntry.artifactId, newEntry.sha256Hash, newEntry.actorUuid, newEntry.supersedes, newEntry.sealedAt]
  );

  return newEntry;
}

export async function getLedgerEntry(id: string): Promise<LedgerEntry | null> {
  const db = requirePg();
  const { rows } = await db.query(`SELECT * FROM ledger_entries WHERE id = $1`, [id]);
  return rows.length ? mapLedgerRow(rows[0]) : null;
}

export async function getLatestForArtifact(artifactSerial: string): Promise<LedgerEntry | null> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM ledger_entries WHERE artifact_serial = $1 AND superseded_by IS NULL LIMIT 1`,
    [artifactSerial]
  );
  return rows.length ? mapLedgerRow(rows[0]) : null;
}

function mapLedgerRow(row: any): LedgerEntry {
  return {
    id: row.id, artifactSerial: row.artifact_serial, artifactId: row.artifact_id,
    sha256Hash: row.sha256_hash, blockchainRef: row.blockchain_ref,
    actorUuid: row.actor_uuid, supersedes: row.supersedes,
    supersededBy: row.superseded_by, sealedAt: new Date(row.sealed_at).getTime(),
  };
}
