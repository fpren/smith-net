import { v4 as uuidv4 } from 'uuid';
import { IntentVersion, SummaryArtifact } from './types';
import { validateSynthesisInputs, validateArtifact } from './synthesisAuthority';
import { supabase } from './supabase';

async function generateSerial(): Promise<string> {
  const { data, error } = await supabase
    .from('summary_artifacts')
    .select('serial')
    .order('created_at', { ascending: false })
    .limit(1);

  let seq = 1;
  if (!error && data && data.length > 0) {
    const lastSerial = data[0].serial;
    const lastNum = parseInt(lastSerial.split('-').pop() || '0', 10);
    seq = lastNum + 1;
  }

  const date = new Date();
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  return `ART-${y}-${m}-${String(seq).padStart(4, '0')}`;
}

export async function synthesize(
  intentVersion: IntentVersion,
  jobIds: string[],
  timeEntryIds: string[],
  approvedChatMessageIds: string[] = []
): Promise<SummaryArtifact | { error: string }> {
  const inputValidation = validateSynthesisInputs(intentVersion, jobIds, timeEntryIds);
  if (!inputValidation.valid) return { error: inputValidation.message };

  // Query real job data
  const { data: jobRows } = await supabase
    .from('jobs')
    .select('id, title, description, status')
    .in('id', jobIds);

  const workPerformed = (jobRows || []).map(j =>
    `${j.title || 'Untitled job'}: ${j.status || 'completed'}${j.description ? ' — ' + j.description : ''}`
  );

  // Query real time entry data
  const { data: timeRows } = await supabase
    .from('time_entries')
    .select('id, user_id, duration_minutes, job_id')
    .in('id', timeEntryIds);

  const totalMinutes = (timeRows || []).reduce((sum, t) => sum + (t.duration_minutes || 0), 0);
  const totalHours = Math.round((totalMinutes / 60) * 100) / 100;

  const laborRecorded = (timeRows || []).map(t =>
    `${t.user_id?.substring(0, 8) || 'unknown'}: ${t.duration_minutes || 0} min on job ${t.job_id?.substring(0, 8) || 'unlinked'}`
  );

  // Query materials by job IDs
  const { data: materialRows } = await supabase
    .from('materials')
    .select('name, quantity, unit_cost, job_id')
    .in('job_id', jobIds);

  const materialsUsed = (materialRows || []).map(m =>
    `${m.name}: ${m.quantity} × $${m.unit_cost || 0}`
  );

  const materialCost = (materialRows || []).reduce((sum, m) =>
    sum + ((m.quantity || 0) * (m.unit_cost || 0)), 0
  );

  // Query approved chat messages
  const contextualNotes: string[] = [];
  if (approvedChatMessageIds.length > 0) {
    const { data: chatRows } = await supabase
      .from('message_bus_messages')
      .select('content, sender_name')
      .in('id', approvedChatMessageIds);

    for (const msg of chatRows || []) {
      contextualNotes.push(`${msg.sender_name}: ${msg.content}`);
    }
  }

  // Calculate total cost: labor ($55/hr default) + materials
  const laborCost = totalHours * 55;
  const totalCost = Math.round((laborCost + materialCost) * 100) / 100;

  const artifact: SummaryArtifact = {
    id: uuidv4(),
    serial: await generateSerial(),
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
