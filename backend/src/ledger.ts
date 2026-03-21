import { v4 as uuidv4 } from 'uuid';
import { SummaryArtifact, LedgerEntry } from './types';
import { validateSealing, validateAmendment, computeHash } from './ledgerAuthority';
import { supabase } from './supabase';

export async function seal(
  artifact: SummaryArtifact, actorUuid: string
): Promise<LedgerEntry | { error: string }> {
  const validation = validateSealing(artifact);
  if (!validation.valid) return { error: validation.message };

  const hash = computeHash(artifact);
  const entry: LedgerEntry = {
    id: uuidv4(), artifactSerial: artifact.serial, artifactId: artifact.id,
    sha256Hash: hash, actorUuid, sealedAt: Date.now(),
  };

  const { error } = await supabase.from('ledger_entries').insert({
    id: entry.id, artifact_serial: entry.artifactSerial, artifact_id: entry.artifactId,
    sha256_hash: entry.sha256Hash, blockchain_ref: entry.blockchainRef,
    actor_uuid: entry.actorUuid, sealed_at: new Date(entry.sealedAt).toISOString(),
  });

  if (error) return { error: error.message };
  return entry;
}

export async function amend(
  newArtifact: SummaryArtifact, priorEntryId: string, actorUuid: string
): Promise<LedgerEntry | { error: string }> {
  const { data: priorData, error: fetchErr } = await supabase
    .from('ledger_entries').select('*').eq('id', priorEntryId).single();
  if (fetchErr || !priorData) return { error: 'Prior Ledger entry not found' };

  const priorEntry = mapLedgerRow(priorData);
  const validation = validateAmendment(newArtifact, priorEntry);
  if (!validation.valid) return { error: validation.message };

  const hash = computeHash(newArtifact);
  const newEntry: LedgerEntry = {
    id: uuidv4(), artifactSerial: newArtifact.serial, artifactId: newArtifact.id,
    sha256Hash: hash, actorUuid, supersedes: priorEntryId, sealedAt: Date.now(),
  };

  await supabase.from('ledger_entries')
    .update({ superseded_by: newEntry.id }).eq('id', priorEntryId);

  const { error } = await supabase.from('ledger_entries').insert({
    id: newEntry.id, artifact_serial: newEntry.artifactSerial, artifact_id: newEntry.artifactId,
    sha256_hash: newEntry.sha256Hash, actor_uuid: newEntry.actorUuid,
    supersedes: newEntry.supersedes, sealed_at: new Date(newEntry.sealedAt).toISOString(),
  });

  if (error) return { error: error.message };
  return newEntry;
}

export async function getLedgerEntry(id: string): Promise<LedgerEntry | null> {
  const { data, error } = await supabase
    .from('ledger_entries').select('*').eq('id', id).single();
  if (error || !data) return null;
  return mapLedgerRow(data);
}

export async function getLatestForArtifact(artifactSerial: string): Promise<LedgerEntry | null> {
  const { data, error } = await supabase
    .from('ledger_entries').select('*')
    .eq('artifact_serial', artifactSerial).is('superseded_by', null).single();
  if (error || !data) return null;
  return mapLedgerRow(data);
}

function mapLedgerRow(row: any): LedgerEntry {
  return {
    id: row.id, artifactSerial: row.artifact_serial, artifactId: row.artifact_id,
    sha256Hash: row.sha256_hash, blockchainRef: row.blockchain_ref,
    actorUuid: row.actor_uuid, supersedes: row.supersedes,
    supersededBy: row.superseded_by, sealedAt: new Date(row.sealed_at).getTime(),
  };
}
