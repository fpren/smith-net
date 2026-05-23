/**
 * Phase 4 Slice 3: extracted from api.ts.
 *
 * Phase 0 dead-code routes: intents, synthesize, ledger, small-project flow.
 * Slated for a keep-or-kill decision in a subsequent Phase 4 slice; isolating
 * them in their own file makes that decision a single-file flip.
 *
 * authenticateToken is applied by the parent (api.ts).
 */

import { Router, Request, Response } from 'express';
import { createIntent, proposeIntent, confirmIntent, createNewVersion, autoGenerateIntent } from './intentService';
import { validateIntentCreation } from './intentAuthority';
import { synthesize, getArtifact } from './synthesizer';
import { validateSynthesisInputs } from './synthesisAuthority';
import { seal, amend, getLedgerEntry, verifyLedgerEntry } from './ledger';
import { auditLog, AuditAction } from './auditLog';
import { requireEntitlement } from './middleware/requireEntitlement';

export const phase0Router = Router();

// INTENT AUTHORITY + INTENTS

phase0Router.post('/intent-authority/validate-creation', (req: Request, res: Response) => {
  const { scopeStatement, parties } = req.body;
  const result = validateIntentCreation(scopeStatement, parties);
  res.json(result);
});

phase0Router.post('/intents', requireEntitlement('plan_compiler', 'plan_compiler'), async (req: Request, res: Response) => {
  try {
    const { scopeStatement, parties, createdBy, intendedJobIds } = req.body;
    const result = await createIntent(scopeStatement, parties, createdBy, intendedJobIds);
    if ('error' in result) return res.status(400).json(result);
    res.status(201).json(result);
  } catch (err) {
    res.status(500).json({ error: 'Failed to create intent' });
  }
});

phase0Router.post('/intents/:intentId/versions/:versionId/propose', requireEntitlement('plan_compiler', 'plan_compiler'), async (req: Request, res: Response) => {
  try {
    const result = await proposeIntent(req.params.versionId);
    if ('error' in result) return res.status(400).json(result);
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: 'Failed to propose intent' });
  }
});

phase0Router.post('/intents/:intentId/versions/:versionId/confirm', requireEntitlement('plan_compiler', 'plan_compiler'), async (req: Request, res: Response) => {
  try {
    const { confirmerId } = req.body;
    const result = await confirmIntent(req.params.versionId, confirmerId);
    if ('error' in result) return res.status(400).json(result);
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: 'Failed to confirm intent' });
  }
});

phase0Router.post('/intents/:intentId/versions', requireEntitlement('plan_compiler', 'plan_compiler'), async (req: Request, res: Response) => {
  try {
    const { priorVersionId, scopeStatement, parties, intendedJobIds, createdBy } = req.body;
    const result = await createNewVersion(
      req.params.intentId, priorVersionId, scopeStatement, parties, intendedJobIds, createdBy
    );
    if ('error' in result) return res.status(400).json(result);
    res.status(201).json(result);
  } catch (err) {
    res.status(500).json({ error: 'Failed to create new version' });
  }
});

// SYNTHESIS AUTHORITY + SYNTHESIZER

phase0Router.post('/synthesis-authority/validate-inputs', (req: Request, res: Response) => {
  const { intentVersion, jobIds, timeEntryIds } = req.body;
  const result = validateSynthesisInputs(intentVersion, jobIds, timeEntryIds);
  res.json(result);
});

phase0Router.post('/synthesize', requireEntitlement('plan_compiler', 'plan_compiler'), async (req: Request, res: Response) => {
  try {
    const { intentVersion, jobIds, timeEntryIds, approvedChatMessageIds } = req.body;
    const result = await synthesize(intentVersion, jobIds, timeEntryIds, approvedChatMessageIds);
    if ('error' in result) return res.status(400).json(result);
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: 'Synthesis failed' });
  }
});

phase0Router.get('/artifacts/:id', async (req: Request, res: Response) => {
  const artifact = await getArtifact(req.params.id);
  if (!artifact) return res.status(404).json({ error: 'Artifact not found' });
  res.json(artifact);
});

// LEDGER AUTHORITY + LEDGER

phase0Router.post('/ledger/seal', requireEntitlement('plan_compiler', 'plan_compiler'), async (req: Request, res: Response) => {
  try {
    const { artifactId, actorUuid } = req.body;
    const artifact = await getArtifact(artifactId);
    if (!artifact) return res.status(404).json({ error: 'Artifact not found' });
    const result = await seal(artifact, actorUuid);
    if ('error' in result) return res.status(400).json(result);
    res.status(201).json(result);
  } catch (err) {
    res.status(500).json({ error: 'Sealing failed' });
  }
});

phase0Router.post('/ledger/amend', requireEntitlement('plan_compiler', 'plan_compiler'), async (req: Request, res: Response) => {
  try {
    const { newArtifactId, priorEntryId, actorUuid } = req.body;
    const artifact = await getArtifact(newArtifactId);
    if (!artifact) return res.status(404).json({ error: 'Artifact not found' });
    const result = await amend(artifact, priorEntryId, actorUuid);
    if ('error' in result) return res.status(400).json(result);
    res.status(201).json(result);
  } catch (err) {
    res.status(500).json({ error: 'Amendment failed' });
  }
});

// VERIFY: recompute a sealed entry's hash and compare (tamper detection).
phase0Router.get('/ledger/verify/:id', async (req: Request, res: Response) => {
  try {
    const result = await verifyLedgerEntry(req.params.id);
    if ('error' in result) {
      // Missing entry is a 404; a missing sealed artifact for an existing entry
      // is an internal integrity violation, not a "not found".
      const status = result.error === 'Ledger entry not found' ? 404 : 500;
      return res.status(status).json(result);
    }
    if (!result.valid) {
      try {
        await auditLog.log(AuditAction.SECURITY_ALERT, 'system', {
          reason: 'ledger_verify_mismatch',
          entryId: req.params.id,
          expected: result.expected,
          actual: result.actual,
          hashVersion: result.hashVersion,
        });
      } catch (auditErr) {
        // best-effort: an audit write failure must not mask the verify result
      }
    }
    return res.status(200).json(result);
  } catch (err) {
    return res.status(500).json({ error: 'Ledger verification failed' });
  }
});

phase0Router.get('/ledger/:id', async (req: Request, res: Response) => {
  const entry = await getLedgerEntry(req.params.id);
  if (!entry) return res.status(404).json({ error: 'Ledger entry not found' });
  res.json(entry);
});

// SMALL PROJECT FLOW

phase0Router.post('/small-project/synthesize-and-generate-intent', requireEntitlement('plan_compiler', 'plan_compiler'), async (req: Request, res: Response) => {
  try {
    const { jobIds, timeEntryIds, actorUuid } = req.body;
    const intentResult = await autoGenerateIntent(jobIds, timeEntryIds, actorUuid);
    if ('error' in intentResult) return res.status(400).json(intentResult);
    res.json({
      intent: intentResult.intent,
      intentVersion: intentResult.version,
      message: 'Intent auto-generated. Confirm to proceed with sealing.'
    });
  } catch (err) {
    res.status(500).json({ error: 'Small project flow failed' });
  }
});

phase0Router.post('/small-project/confirm-and-seal', requireEntitlement('plan_compiler', 'plan_compiler'), async (req: Request, res: Response) => {
  try {
    const { intentVersionId, confirmerId, jobIds, timeEntryIds, approvedChatMessageIds } = req.body;
    const confirmed = await confirmIntent(intentVersionId, confirmerId);
    if ('error' in confirmed) return res.status(400).json(confirmed);
    const artifact = await synthesize(confirmed, jobIds, timeEntryIds, approvedChatMessageIds);
    if ('error' in artifact) return res.status(400).json(artifact);
    const ledgerEntry = await seal(artifact, confirmerId);
    if ('error' in ledgerEntry) return res.status(400).json(ledgerEntry);
    res.status(201).json({ intentVersion: confirmed, artifact, ledgerEntry });
  } catch (err) {
    res.status(500).json({ error: 'Confirm and seal failed' });
  }
});
