/**
 * Phase 4 Slice 3: extracted from api.ts.
 *
 * Proposals — two sibling routers:
 *   proposalsRouter        — authenticated, mounted under /api/proposals
 *   proposalPublicRouter   — public, mounted at /p/:uuid (re-exported by api.ts)
 *
 * authenticateToken is applied by the parent (api.ts) for the authed router.
 */

import { Router, Request, Response } from 'express';
import fs from 'fs';
import path from 'path';
import { proposalService } from './proposals';

export const proposalsRouter = Router();
export const proposalPublicRouter = Router();

// ════════════════════════════════════════════════════════════════
// AUTHENTICATED PROPOSAL ROUTES
// ════════════════════════════════════════════════════════════════

// POST /api/proposals — contractor creates a new proposal
proposalsRouter.post('/proposals', async (req: Request, res: Response) => {
  try {
    const result = await proposalService.createProposal(req.body);
    if (!result) return res.status(500).json({ error: 'Failed to create proposal' });
    res.status(201).json(result);
  } catch (err) {
    res.status(500).json({ error: 'Proposal creation failed' });
  }
});

// POST /api/proposals/:uuid/revoke — contractor revokes a proposal
proposalsRouter.post('/proposals/:uuid/revoke', async (req: Request, res: Response) => {
  try {
    const ok = await proposalService.revoke(req.params.uuid);
    if (!ok) return res.status(404).json({ error: 'Proposal not found or could not be revoked' });
    res.json({ status: 'revoked' });
  } catch (err) {
    res.status(500).json({ error: 'Revoke failed' });
  }
});

// ════════════════════════════════════════════════════════════════
// PUBLIC PROPOSAL ROUTES (client-facing, no auth)
// ════════════════════════════════════════════════════════════════

const PROPOSAL_TEMPLATE_PATH = path.join(__dirname, 'templates', 'proposal.html');

function loadTemplate(): string {
  try {
    return fs.readFileSync(PROPOSAL_TEMPLATE_PATH, 'utf8');
  } catch {
    return '<h1>Template not found</h1>';
  }
}

function esc(s: any): string {
  if (s == null) return '';
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function fmt(n: any): string {
  const num = parseFloat(n);
  if (isNaN(num)) return '0.00';
  return num.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

function renderProposal(proposal: any): string {
  const tpl = loadTemplate();

  const tasks: string[] = Array.isArray(proposal.tasks) ? proposal.tasks : [];
  const materials: any[] = Array.isArray(proposal.materials) ? proposal.materials : [];
  const equipment: string[] = Array.isArray(proposal.equipment) ? proposal.equipment : [];

  const status = proposal.status || 'pending';
  const statusLabel: Record<string, string> = {
    pending: 'Pending Review',
    approved: 'Approved',
    declined: 'Changes Requested',
    revoked: 'Revoked',
    expired: 'Expired'
  };

  const tasksHtml = tasks.map((t) => `<li>${esc(t)}</li>`).join('\n');
  const materialsHtml = materials.map((m) =>
    `<tr><td>${esc(m.name)}</td><td>${esc(m.quantity)}</td><td>${esc(m.unit)}</td><td>$${fmt(m.unitCost)}</td><td>$${fmt(m.totalCost)}</td></tr>`
  ).join('\n');
  const equipmentHtml = equipment.map((e) => `<li>${esc(e)}</li>`).join('\n');

  const isResponded = status === 'approved' || status === 'declined';
  const isExpired = !!proposal.expired || status === 'expired';

  let html = tpl
    .replace(/\{\{CONTRACTOR_NAME\}\}/g, esc(proposal.contractor_name))
    .replace(/\{\{CONTRACTOR_PHONE\}\}/g, esc(proposal.contractor_phone))
    .replace(/\{\{CONTRACTOR_LICENSE\}\}/g, esc(proposal.contractor_license))
    .replace(/\{\{CLIENT_NAME\}\}/g, esc(proposal.client_name))
    .replace(/\{\{CLIENT_ADDRESS\}\}/g, esc(proposal.client_address))
    .replace(/\{\{SCOPE\}\}/g, esc(proposal.scope))
    .replace(/\{\{STATUS\}\}/g, esc(status))
    .replace(/\{\{STATUS_LABEL\}\}/g, esc(statusLabel[status] || status))
    .replace(/\{\{LABOR_HOURS\}\}/g, esc(proposal.labor_hours))
    .replace(/\{\{LABOR_RATE\}\}/g, fmt(proposal.labor_rate))
    .replace(/\{\{LABOR_COST\}\}/g, fmt(proposal.labor_cost))
    .replace(/\{\{MATERIALS_COST\}\}/g, fmt(proposal.materials_cost))
    .replace(/\{\{TOTAL_COST\}\}/g, fmt(proposal.total_cost))
    .replace(/\{\{TASKS_HTML\}\}/g, tasksHtml)
    .replace(/\{\{MATERIALS_HTML\}\}/g, materialsHtml)
    .replace(/\{\{EQUIPMENT_HTML\}\}/g, equipmentHtml);

  // Conditional blocks
  html = html
    .replace(/\{\{#HAS_TASKS\}\}([\s\S]*?)\{\{\/HAS_TASKS\}\}/g, tasks.length > 0 ? '$1' : '')
    .replace(/\{\{#HAS_MATERIALS\}\}([\s\S]*?)\{\{\/HAS_MATERIALS\}\}/g, materials.length > 0 ? '$1' : '')
    .replace(/\{\{#HAS_EQUIPMENT\}\}([\s\S]*?)\{\{\/HAS_EQUIPMENT\}\}/g, equipment.length > 0 ? '$1' : '')
    .replace(/\{\{#SHOW_RESPOND\}\}([\s\S]*?)\{\{\/SHOW_RESPOND\}\}/g, (!isResponded && !isExpired) ? '$1' : '')
    .replace(/\{\{#SHOW_APPROVED\}\}([\s\S]*?)\{\{\/SHOW_APPROVED\}\}/g, status === 'approved' ? '$1' : '')
    .replace(/\{\{#SHOW_DECLINED\}\}([\s\S]*?)\{\{\/SHOW_DECLINED\}\}/g, status === 'declined' ? '$1' : '')
    .replace(/\{\{#SHOW_EXPIRED\}\}([\s\S]*?)\{\{\/SHOW_EXPIRED\}\}/g, isExpired ? '$1' : '');

  return html;
}

// GET /p/:uuid — renders proposal HTML page (public, no auth)
proposalPublicRouter.get('/:uuid', async (req: Request, res: Response) => {
  const proposal = await proposalService.getByUuid(req.params.uuid);
  if (!proposal) {
    return res.status(404).send('<h2 style="font-family:sans-serif;padding:40px">Proposal not found or has been revoked.</h2>');
  }
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.send(renderProposal(proposal));
});

// POST /p/:uuid/respond — client approves or requests changes (public, no auth)
// TODO: Add rate limiting (e.g. express-rate-limit) in production to prevent abuse.
// One response per IP per UUID is recommended.
proposalPublicRouter.post('/:uuid/respond', async (req: Request, res: Response) => {
  const { action, clientName, notes } = req.body as { action: string; clientName: string; notes?: string };

  if (!action || !clientName) {
    return res.status(400).json({ error: 'action and clientName are required' });
  }

  if (action !== 'approve' && action !== 'decline') {
    return res.status(400).json({ error: 'action must be "approve" or "decline"' });
  }

  const ok = await proposalService.respond(req.params.uuid, action, clientName, notes);
  if (!ok) {
    return res.status(400).json({ error: 'Unable to record response. Check your name matches the proposal exactly, or the proposal may have expired.' });
  }

  res.json({ status: action === 'approve' ? 'approved' : 'declined' });
});
