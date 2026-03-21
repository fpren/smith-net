import { v4 as uuidv4 } from 'uuid';
import { IntentVersion, SummaryArtifact } from './types';
import { validateSynthesisInputs, validateArtifact } from './synthesisAuthority';
import { supabase } from './supabase';

let artifactSequence = 0;

function generateSerial(): string {
  artifactSequence++;
  const date = new Date();
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  return `ART-${y}-${m}-${String(artifactSequence).padStart(4, '0')}`;
}

export async function synthesize(
  intentVersion: IntentVersion,
  jobIds: string[],
  timeEntryIds: string[],
  approvedChatMessageIds: string[] = []
): Promise<SummaryArtifact | { error: string }> {
  const inputValidation = validateSynthesisInputs(intentVersion, jobIds, timeEntryIds);
  if (!inputValidation.valid) return { error: inputValidation.message };

  // Assemble facts (mock — real implementation queries Jobs/Time/Messages tables)
  const workPerformed = jobIds.map((id, i) => `Job ${i + 1} (${id.substring(0, 8)}): completed`);
  const laborRecorded = timeEntryIds.map((id, i) => `Time entry ${i + 1} (${id.substring(0, 8)}): recorded`);
  const materialsUsed: string[] = [];
  const contextualNotes: string[] = [];
  const totalHours = timeEntryIds.length * 2;
  const totalCost = totalHours * 55;

  const artifact: SummaryArtifact = {
    id: uuidv4(),
    serial: generateSerial(),
    intentVersionId: intentVersion.id,
    scopeStatement: intentVersion.scopeStatement,
    workPerformed, laborRecorded, materialsUsed, contextualNotes,
    totalHours, totalCost, jobIds, timeEntryIds,
    chatMessageIds: approvedChatMessageIds,
    createdAt: Date.now(),
  };

  const outputValidation = validateArtifact(artifact);
  if (!outputValidation.valid) return { error: outputValidation.message };

  const { error } = await supabase.from('summary_artifacts').insert({
    id: artifact.id, serial: artifact.serial, intent_version_id: artifact.intentVersionId,
    scope_statement: artifact.scopeStatement, work_performed: artifact.workPerformed,
    labor_recorded: artifact.laborRecorded, materials_used: artifact.materialsUsed,
    contextual_notes: artifact.contextualNotes, total_hours: artifact.totalHours,
    total_cost: artifact.totalCost, job_ids: artifact.jobIds,
    time_entry_ids: artifact.timeEntryIds, chat_message_ids: artifact.chatMessageIds,
    created_at: new Date(artifact.createdAt).toISOString(),
  });

  if (error) return { error: error.message };
  return artifact;
}

export async function getArtifact(artifactId: string): Promise<SummaryArtifact | null> {
  const { data, error } = await supabase
    .from('summary_artifacts').select('*').eq('id', artifactId).single();
  if (error || !data) return null;
  return mapArtifactRow(data);
}

export async function getArtifactBySerial(serial: string): Promise<SummaryArtifact | null> {
  const { data, error } = await supabase
    .from('summary_artifacts').select('*').eq('serial', serial).single();
  if (error || !data) return null;
  return mapArtifactRow(data);
}

function mapArtifactRow(row: any): SummaryArtifact {
  return {
    id: row.id, serial: row.serial, intentVersionId: row.intent_version_id,
    scopeStatement: row.scope_statement, workPerformed: row.work_performed || [],
    laborRecorded: row.labor_recorded || [], materialsUsed: row.materials_used || [],
    contextualNotes: row.contextual_notes || [], totalHours: Number(row.total_hours),
    totalCost: Number(row.total_cost), jobIds: row.job_ids || [],
    timeEntryIds: row.time_entry_ids || [], chatMessageIds: row.chat_message_ids || [],
    createdAt: new Date(row.created_at).getTime(),
  };
}
