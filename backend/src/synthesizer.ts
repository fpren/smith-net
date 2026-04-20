import { v4 as uuidv4 } from 'uuid';
import { IntentVersion, SummaryArtifact } from './types';
import { validateSynthesisInputs, validateArtifact } from './synthesisAuthority';
import { pg, isPgEnabled } from './db';

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[Synthesizer] Postgres client not initialized');
  return pg;
}

async function generateSerial(): Promise<string> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT serial FROM summary_artifacts ORDER BY created_at DESC LIMIT 1`
  );

  let seq = 1;
  if (rows.length > 0 && rows[0].serial) {
    const lastNum = parseInt(String(rows[0].serial).split('-').pop() || '0', 10);
    if (Number.isFinite(lastNum)) seq = lastNum + 1;
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
  const db = requirePg();
  const inputValidation = validateSynthesisInputs(intentVersion, jobIds, timeEntryIds);
  if (!inputValidation.valid) return { error: inputValidation.message };

  // Fetch jobs
  const { rows: jobRows } = jobIds.length
    ? await db.query(
        `SELECT id, title, description, status FROM jobs WHERE id = ANY($1::uuid[])`,
        [jobIds]
      )
    : { rows: [] };

  const workPerformed = jobRows.map((j: any) =>
    `${j.title || 'Untitled job'}: ${j.status || 'completed'}${j.description ? ' — ' + j.description : ''}`
  );

  // Fetch time entries
  const { rows: timeRows } = timeEntryIds.length
    ? await db.query(
        `SELECT id, user_id, duration_minutes, job_id FROM time_entries WHERE id = ANY($1::uuid[])`,
        [timeEntryIds]
      )
    : { rows: [] };

  const totalMinutes = timeRows.reduce((sum: number, t: any) => sum + (t.duration_minutes || 0), 0);
  const totalHours = Math.round((totalMinutes / 60) * 100) / 100;

  const laborRecorded = timeRows.map((t: any) =>
    `${t.user_id?.substring(0, 8) || 'unknown'}: ${t.duration_minutes || 0} min on job ${t.job_id?.toString().substring(0, 8) || 'unlinked'}`
  );

  // Fetch materials for these jobs
  const { rows: materialRows } = jobIds.length
    ? await db.query(
        `SELECT name, quantity, unit_cost, job_id FROM materials WHERE job_id = ANY($1::uuid[])`,
        [jobIds]
      )
    : { rows: [] };

  const materialsUsed = materialRows.map((m: any) =>
    `${m.name}: ${m.quantity} × $${m.unit_cost || 0}`
  );

  const materialCost = materialRows.reduce((sum: number, m: any) =>
    sum + (Number(m.quantity || 0) * Number(m.unit_cost || 0)), 0
  );

  // Fetch approved chat messages
  const contextualNotes: string[] = [];
  if (approvedChatMessageIds.length > 0) {
    const { rows: chatRows } = await db.query(
      `SELECT content, sender_name FROM message_bus_messages WHERE id = ANY($1::uuid[])`,
      [approvedChatMessageIds]
    );
    for (const msg of chatRows) contextualNotes.push(`${msg.sender_name}: ${msg.content}`);
  }

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

  await db.query(
    `INSERT INTO summary_artifacts
       (id, serial, intent_version_id, scope_statement,
        work_performed, labor_recorded, materials_used, contextual_notes,
        total_hours, total_cost,
        job_ids, time_entry_ids, chat_message_ids,
        created_at)
     VALUES ($1,$2,$3,$4,
             $5::jsonb,$6::jsonb,$7::jsonb,$8::jsonb,
             $9,$10,
             $11::jsonb,$12::jsonb,$13::jsonb,
             to_timestamp($14/1000.0))`,
    [
      artifact.id, artifact.serial, artifact.intentVersionId, artifact.scopeStatement,
      JSON.stringify(artifact.workPerformed), JSON.stringify(artifact.laborRecorded),
      JSON.stringify(artifact.materialsUsed), JSON.stringify(artifact.contextualNotes),
      artifact.totalHours, artifact.totalCost,
      JSON.stringify(artifact.jobIds), JSON.stringify(artifact.timeEntryIds), JSON.stringify(artifact.chatMessageIds),
      artifact.createdAt,
    ]
  );

  return artifact;
}

export async function getArtifact(artifactId: string): Promise<SummaryArtifact | null> {
  const db = requirePg();
  const { rows } = await db.query(`SELECT * FROM summary_artifacts WHERE id = $1`, [artifactId]);
  return rows.length ? mapArtifactRow(rows[0]) : null;
}

export async function getArtifactBySerial(serial: string): Promise<SummaryArtifact | null> {
  const db = requirePg();
  const { rows } = await db.query(`SELECT * FROM summary_artifacts WHERE serial = $1`, [serial]);
  return rows.length ? mapArtifactRow(rows[0]) : null;
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
