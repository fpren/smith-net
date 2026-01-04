/**
 * Smith Net API
 * Control + Gateway endpoints for desktop portal and automation
 */

import { Router, Request, Response } from 'express';
import { channelRegistry } from './channelRegistry';
import { messageStore } from './messageStore';
import { presenceManager } from './presenceManager';
import { gatewayManager } from './gatewayManager';
import { wsHandler } from './wsHandler';
import { planSynthesisService } from './planSynthesis';
import { outputGeneratorService } from './outputGenerator';
import { archiveService } from './archiveService';
import { planAuthority } from './planAuthority';
import { reportAssembler } from './reportAssembler';
import { reportRenderer } from './reportRenderer';
import { reportOutput } from './reportOutput';
import { autoPlanCreator } from './autoPlanCreator';
import { claudeResolve, localFallbackResolve, EnhancedJobData } from './resolver/claudeResearch';
// Legacy import kept for reference - now using Claude AI instead of browser scraping
// import { playwrightResolve } from './resolver/playwrightSearch';
import { 
  CreateChannelRequest, 
  InjectMessageRequest, 
  Message,
  AccessRequestPayload,
  AccessResponsePayload,
  UpdateChannelAccessPayload,
  UpdateChannelVisibilityPayload,
  Engagement,
  Plan,
  Proposal,
  PlanSummary,
  PlanSnapshot,
  Report,
  Invoice,
  CreateEngagementRequest,
  CreatePlanRequest,
  CreateProposalRequest,
  ConfirmProposalRequest,
  CreatePlanSummaryRequest,
  ConfirmSummaryRequest,
  GenerateOutputRequest
} from './types';
import { v4 as uuidv4 } from 'uuid';

export const apiRouter = Router();

// ════════════════════════════════════════════════════════════════════
// CHANNELS
// ════════════════════════════════════════════════════════════════════

/**
 * Create a new channel
 */
apiRouter.post('/channels', (req: Request, res: Response) => {
  const { name, type, memberIds, visibility, requiresApproval } = req.body as CreateChannelRequest;

  if (!name || !type) {
    return res.status(400).json({ error: 'name and type required' });
  }

  // Get creator from header (simplified auth)
  const creatorId = req.headers['x-user-id'] as string || 'anonymous';

  const channel = channelRegistry.create(
    name, 
    type, 
    creatorId, 
    memberIds,
    visibility || 'public',
    requiresApproval || false
  );

  // Broadcast channel creation
  wsHandler.broadcastChannelEvent('channel_created', channel);

  res.status(201).json(channel);
});

/**
 * List all channels
 */
apiRouter.get('/channels', (req: Request, res: Response) => {
  const userId = req.headers['x-user-id'] as string;
  
  const channels = userId 
    ? channelRegistry.listForUser(userId)
    : channelRegistry.list();

  res.json(channels);
});

/**
 * Get channel by ID
 */
apiRouter.get('/channels/:id', (req: Request, res: Response) => {
  const channel = channelRegistry.get(req.params.id);
  
  if (!channel) {
    return res.status(404).json({ error: 'Channel not found' });
  }

  res.json(channel);
});

/**
 * Update channel
 */
apiRouter.patch('/channels/:id', (req: Request, res: Response) => {
  const channel = channelRegistry.update(req.params.id, req.body);
  
  if (!channel) {
    return res.status(404).json({ error: 'Channel not found' });
  }

  wsHandler.broadcastChannelEvent('channel_updated', channel);
  res.json(channel);
});

/**
 * Delete channel
 */
apiRouter.delete('/channels/:id', (req: Request, res: Response) => {
  const success = channelRegistry.delete(req.params.id);
  
  if (!success) {
    return res.status(404).json({ error: 'Channel not found' });
  }

  wsHandler.broadcastChannelEvent('channel_deleted', { id: req.params.id });
  res.status(204).send();
});

// ════════════════════════════════════════════════════════════════════
// ACCESS CONTROL (Active Directory-style)
// ════════════════════════════════════════════════════════════════════

/**
 * Request access to a private channel
 */
apiRouter.post('/channels/:id/access/request', (req: Request, res: Response) => {
  const channelId = req.params.id;
  const userId = req.headers['x-user-id'] as string;

  if (!userId) {
    return res.status(401).json({ error: 'User ID required' });
  }

  const success = channelRegistry.requestAccess(channelId, userId);
  
  if (!success) {
    return res.status(400).json({ error: 'Cannot request access to this channel' });
  }

  const channel = channelRegistry.get(channelId);
  wsHandler.broadcastChannelEvent('channel_updated', channel);
  
  res.json({ status: 'pending', message: 'Access request submitted' });
});

/**
 * Respond to access request (approve/deny)
 */
apiRouter.post('/channels/:id/access/respond', (req: Request, res: Response) => {
  const channelId = req.params.id;
  const managerId = req.headers['x-user-id'] as string;
  const { requesterId, approve } = req.body as AccessResponsePayload;

  if (!managerId) {
    return res.status(401).json({ error: 'Manager ID required' });
  }

  const success = channelRegistry.respondToAccessRequest(channelId, requesterId, managerId, approve);
  
  if (!success) {
    return res.status(403).json({ error: 'Not authorized to manage this channel' });
  }

  const channel = channelRegistry.get(channelId);
  wsHandler.broadcastChannelEvent('channel_updated', channel);
  
  res.json({ status: approve ? 'approved' : 'denied' });
});

/**
 * Update user access (allow/block)
 */
apiRouter.post('/channels/:id/access/user', (req: Request, res: Response) => {
  const channelId = req.params.id;
  const managerId = req.headers['x-user-id'] as string;
  const { userId, allow } = req.body as UpdateChannelAccessPayload;

  if (!managerId) {
    return res.status(401).json({ error: 'Manager ID required' });
  }

  const success = channelRegistry.updateUserAccess(channelId, userId, managerId, allow);
  
  if (!success) {
    return res.status(403).json({ error: 'Not authorized to manage this channel' });
  }

  const channel = channelRegistry.get(channelId);
  wsHandler.broadcastChannelEvent('channel_updated', channel);
  
  res.json({ status: allow ? 'allowed' : 'blocked' });
});

/**
 * Update channel visibility
 */
apiRouter.post('/channels/:id/visibility', (req: Request, res: Response) => {
  const channelId = req.params.id;
  const managerId = req.headers['x-user-id'] as string;
  const { visibility, requiresApproval } = req.body as UpdateChannelVisibilityPayload;

  if (!managerId) {
    return res.status(401).json({ error: 'Manager ID required' });
  }

  const success = channelRegistry.updateVisibility(channelId, managerId, visibility, requiresApproval);
  
  if (!success) {
    return res.status(403).json({ error: 'Not authorized to manage this channel' });
  }

  const channel = channelRegistry.get(channelId);
  wsHandler.broadcastChannelEvent('channel_updated', channel);
  
  res.json(channel);
});

/**
 * Get access status for current user
 */
apiRouter.get('/channels/:id/access/status', (req: Request, res: Response) => {
  const channelId = req.params.id;
  const userId = req.headers['x-user-id'] as string;

  if (!userId) {
    return res.status(401).json({ error: 'User ID required' });
  }

  const status = channelRegistry.getAccessStatus(channelId, userId);
  res.json({ status });
});

/**
 * Get pending access requests for a channel (for managers)
 */
apiRouter.get('/channels/:id/access/pending', (req: Request, res: Response) => {
  const channelId = req.params.id;
  const managerId = req.headers['x-user-id'] as string;

  const channel = channelRegistry.get(channelId);
  
  if (!channel) {
    return res.status(404).json({ error: 'Channel not found' });
  }

  // Only creator can see pending requests
  if (channel.creatorId !== managerId) {
    return res.status(403).json({ error: 'Not authorized' });
  }

  res.json({ pendingRequests: channel.pendingRequests });
});

// ════════════════════════════════════════════════════════════════════
// MESSAGES
// ════════════════════════════════════════════════════════════════════

/**
 * Get messages for a channel
 */
apiRouter.get('/channels/:id/messages', (req: Request, res: Response) => {
  const { id } = req.params;
  const limit = parseInt(req.query.limit as string) || 100;
  const before = req.query.before ? parseInt(req.query.before as string) : undefined;

  const messages = messageStore.getForChannel(id, limit, before);
  res.json(messages);
});

/**
 * Clear all messages in a channel
 */
apiRouter.delete('/channels/:id/messages', (req: Request, res: Response) => {
  const { id } = req.params;
  
  messageStore.clearChannel(id);
  
  // Broadcast clear event to all online clients
  wsHandler.broadcastChannelEvent('channel_cleared', { channelId: id });
  
  console.log(`[API] Cleared messages for channel: ${id}`);
  res.status(204).send();
});

/**
 * Delete a single message (for "Delete for everyone")
 * Only the message sender or dashboard admin can delete.
 */
apiRouter.delete('/messages/:messageId', (req: Request, res: Response) => {
  const { messageId } = req.params;
  const requesterId = req.headers['x-user-id'] as string;
  
  const deleted = messageStore.deleteMessage(messageId, requesterId);
  
  if (!deleted) {
    return res.status(404).json({ error: 'Message not found or unauthorized' });
  }
  
  // Broadcast deletion to all online clients
  wsHandler.broadcastChannelEvent('message_deleted', { messageId });
  
  console.log(`[API] Deleted message ${messageId} by ${requesterId}`);
  res.status(204).send();
});

/**
 * SMART SEND - Unified message endpoint
 * 
 * Automatically routes messages:
 * 1. Always stores and broadcasts to online clients
 * 2. Automatically injects to mesh if a gateway relay is connected
 *    (so mesh-only users underground can receive it)
 */
apiRouter.post('/messages/inject', (req: Request, res: Response) => {
  let { channelId, content, meshOnly } = req.body as InjectMessageRequest & { meshOnly?: boolean };
  const senderId = req.headers['x-user-id'] as string || 'system';
  const senderName = req.headers['x-user-name'] as string || 'System';

  if (!channelId || !content) {
    return res.status(400).json({ error: 'channelId and content required' });
  }

  // Resolve channel name to UUID if needed (phones send "general", we need UUID)
  if (!channelId.includes('-')) {
    const channel = channelRegistry.findByName(channelId);
    if (channel) {
      console.log(`[API] Resolved channel "${channelId}" -> ${channel.id}`);
      channelId = channel.id;
    } else {
      console.log(`[API] Unknown channel name: ${channelId}`);
      return res.status(404).json({ error: `Channel not found: ${channelId}` });
    }
  }

  // Determine if we should inject to mesh
  const hasRelay = gatewayManager.hasConnectedRelay();
  const shouldInjectToMesh = hasRelay && !meshOnly; // Always inject if relay available, unless meshOnly=false
  
  // Create message with appropriate origin marker
  const origin = shouldInjectToMesh ? 'online+mesh' : 'online';
  const message = messageStore.add(
    channelId,
    senderId,
    senderName,
    content,
    origin
  );

  // Broadcast to online clients
  wsHandler.broadcastToChannel(channelId, message);

  // Automatically inject into mesh if relay available
  // This ensures mesh-only users (underground) always receive messages
  let injectedToMesh = 0;
  if (shouldInjectToMesh) {
    injectedToMesh = gatewayManager.broadcastToRelays(message);
    console.log(`[SmartSend] Auto-injected to ${injectedToMesh} mesh relay(s)`);
  }

  res.status(201).json({ 
    ...message, 
    meshInjected: injectedToMesh > 0,
    relayCount: injectedToMesh
  });
});

// ════════════════════════════════════════════════════════════════════
// SYNC
// ════════════════════════════════════════════════════════════════════

/**
 * Get sync info for a reconnecting client.
 * Returns channel clear timestamps so client can purge old messages.
 */
apiRouter.get('/sync', (_req: Request, res: Response) => {
  res.json({
    channelClearedAt: messageStore.getAllClearTimestamps(),
    serverTime: Date.now()
  });
});

// ════════════════════════════════════════════════════════════════════
// PRESENCE
// ════════════════════════════════════════════════════════════════════

/**
 * Get all presence data (for Android app polling)
 * Returns users in a format the app expects
 */
apiRouter.get('/presence', (_req: Request, res: Response) => {
  const onlineUsers = presenceManager.getOnline();
  
  // Format for Android app
  res.json({
    users: onlineUsers.map(p => ({
      userId: p.userId,
      userName: p.userName,
      timestamp: p.lastSeen,
      status: p.status,
      connectionType: p.connectionType
    })),
    count: onlineUsers.length,
    serverTime: Date.now()
  });
});

/**
 * Send presence heartbeat (for Android app)
 * Called periodically to announce user is online
 */
apiRouter.post('/presence', (req: Request, res: Response) => {
  const { userId, userName, timestamp } = req.body;
  
  if (!userId || !userName) {
    return res.status(400).json({ error: 'userId and userName required' });
  }
  
  // Update presence
  const presence = presenceManager.update(
    userId,
    userName,
    'online',
    'mobile' // Android app
  );
  
  console.log(`[Presence] Heartbeat from ${userName} (${userId})`);
  
  // Broadcast presence update to WebSocket clients
  wsHandler.broadcastPresence(presence);
  
  res.status(200).json({ 
    success: true, 
    presence,
    onlineCount: presenceManager.getOnline().length
  });
});

/**
 * Get online users only
 */
apiRouter.get('/presence/online', (_req: Request, res: Response) => {
  res.json(presenceManager.getOnline());
});

// ════════════════════════════════════════════════════════════════════
// GATEWAY
// ════════════════════════════════════════════════════════════════════

/**
 * Get gateway status
 */
apiRouter.get('/gateway/status', (_req: Request, res: Response) => {
  const relays = gatewayManager.getAll();
  const hasRelay = relays.length > 0;

  res.json({
    mode: hasRelay ? 'gateway' : 'online',
    relayConnected: hasRelay,
    relays,
    lastMeshActivity: relays.length > 0 
      ? Math.max(...relays.map(r => r.lastActivity))
      : undefined,
  });
});

/**
 * List connected relays
 */
apiRouter.get('/gateway/relays', (_req: Request, res: Response) => {
  res.json(gatewayManager.getAll());
});

/**
 * Disconnect a specific relay (admin control from dashboard)
 */
apiRouter.delete('/gateway/relays/:relayId', (req: Request, res: Response) => {
  const { relayId } = req.params;
  
  const relay = gatewayManager.get(relayId);
  if (!relay) {
    return res.status(404).json({ error: 'Relay not found' });
  }
  
  // Force disconnect the relay
  gatewayManager.forceDisconnect(relayId);
  
  console.log(`[API] Admin force-disconnected relay: ${relay.name} (${relayId})`);
  res.json({ success: true, disconnected: relay.name });
});

/**
 * Inject message via gateway
 */
apiRouter.post('/gateway/inject', (req: Request, res: Response) => {
  const { channelId, content } = req.body;
  const senderId = req.headers['x-user-id'] as string || 'system';
  const senderName = req.headers['x-user-name'] as string || 'System';

  if (!channelId || !content) {
    return res.status(400).json({ error: 'channelId and content required' });
  }

  if (!gatewayManager.hasConnectedRelay()) {
    return res.status(503).json({ error: 'No gateway relay connected' });
  }

  // Create message
  const message: Message = {
    id: uuidv4(),
    channelId,
    senderId,
    senderName,
    content,
    timestamp: Date.now(),
    origin: 'gateway',
  };

  // Store
  messageStore.add(channelId, senderId, senderName, content, 'gateway');

  // Broadcast online
  wsHandler.broadcastToChannel(channelId, message);

  // Inject to mesh
  const injected = gatewayManager.broadcastToRelays(message);

  res.status(201).json({ 
    message, 
    injectedToRelays: injected 
  });
});

// ════════════════════════════════════════════════════════════════════
// PLAN RESOLVER — TEST ⧉ Compile Path (Playwright-based)
// ════════════════════════════════════════════════════════════════════

/**
 * RESOLVE PLAN TEXT WITH PLAYWRIGHT (TEST ⧉ only)
 * 
 * USED ONLY BY: TEST ⧉ compile path in Android app
 * NEVER USED BY: Regular COMPILE button
 * 
 * BEHAVIOR:
 * - One short-lived browser session per request
 * - Max 5 seconds execution
 * - Any error returns null (no exceptions)
 * 
 * OUTPUT FORMAT:
 * {
 *   scope: string,
 *   tasks: string[],
 *   assumptions: string[],
 *   notes: string[],
 *   detectedKeywords: string[]
 * }
 * 
 * EXTRACTS ONLY: inspection, safety, phases
 * DISCARDS: pricing, ads, opinions
 */
apiRouter.post('/plan/resolve', async (req: Request, res: Response) => {
  const { input } = req.body;

  if (!input || typeof input !== 'string') {
    return res.json({ success: false, result: null });
  }

  console.log(`[API] /plan/resolve called with ${input.length} chars`);
  const startTime = Date.now();

  // Call Claude AI resolver - returns full job data or null
  // Falls back to local knowledge base if Claude is unavailable
  let result = await claudeResolve(input);
  
  // If Claude fails, use local fallback
  if (result === null) {
    console.log(`[API] Claude resolver failed, using local fallback`);
    result = await localFallbackResolve(input);
  }

  const elapsed = Date.now() - startTime;
  console.log(`[API] /plan/resolve completed in ${elapsed}ms, success: ${result !== null}`);

  if (result === null) {
    // Any error returns null - client falls back to local compile
    return res.json({ success: false, result: null });
  }

  // Convert to GOSPLAN canvas text for injection
  const canvasText = formatAsGosplanCanvasText(result);

  // #region agent log
  const fs = require('fs');
  const logPath = 'c:\\Users\\sonic\\CascadeProjects\\ble-mesh-multiplatform\\.cursor\\debug.log';
  try {
    const entry = JSON.stringify({ 
      location: 'api.ts:/plan/resolve', 
      message: 'Sending response to client', 
      data: { 
        success: true, 
        materialsCount: result.materials?.length || 0,
        tasksCount: result.tasks?.length || 0,
        canvasTextLength: canvasText?.length || 0,
        jobTitle: result.jobTitle?.substring(0, 50)
      }, 
      timestamp: Date.now(), 
      hypothesisId: 'BACKEND' 
    }) + '\n';
    fs.appendFileSync(logPath, entry);
  } catch (e) {}
  // #endregion
  
  res.json({
    success: true,
    result,          // Full EnhancedJobData object
    canvasText,      // GOSPLAN formatted text for canvas
    jobData: result  // Explicit job data for transfer
  });
});

/**
 * Format enhanced job data as GOSPLAN canvas-ready text
 * 
 * This generates the full GOSPLAN template format that can be:
 * 1. Displayed in the canvas for review
 * 2. Compiled by the PlannerCompiler
 * 3. Transferred to Job Board with all data
 */
function formatAsGosplanCanvasText(job: EnhancedJobData): string {
  const lines: string[] = [];
  const now = new Date().toISOString().split('T')[0];
  
  // ══════════════════════════════════════════════════════════════
  // HEADER
  // ══════════════════════════════════════════════════════════════
  lines.push('# PLAN');
  lines.push('');
  lines.push('## JobHeader');
  lines.push(`Job Title:      ${job.jobTitle}`);
  lines.push(`Client:         ${job.clientName || '[Client Name]'}`);
  lines.push(`Location:       ${job.location || '[Location]'}`);
  lines.push(`Job Type:       ${job.jobType}`);
  lines.push(`Primary Trade:  ${job.primaryTrade}`);
  lines.push(`Urgency:        ${job.urgency.charAt(0).toUpperCase() + job.urgency.slice(1)}`);
  lines.push(`Created:        ${now}`);
  lines.push(`Crew Size:      ${job.crewSize} workers`);
  lines.push(`Est. Duration:  ${job.estimatedDays} days`);
  lines.push('');
  
  // ══════════════════════════════════════════════════════════════
  // SCOPE
  // ══════════════════════════════════════════════════════════════
  lines.push('## Scope');
  lines.push(job.scope);
  lines.push('');
  job.scopeDetails.forEach(detail => {
    lines.push(`- ${detail}`);
  });
  lines.push('');
  
  // ══════════════════════════════════════════════════════════════
  // ASSUMPTIONS
  // ══════════════════════════════════════════════════════════════
  if (job.assumptions.length > 0) {
    lines.push('## Assumptions');
    job.assumptions.forEach(a => lines.push(`- ${a}`));
    lines.push('');
  }
  
  // ══════════════════════════════════════════════════════════════
  // TASKS (Execution Checklist)
  // ══════════════════════════════════════════════════════════════
  lines.push('## Tasks');
  job.tasks.forEach((task, i) => {
    lines.push(`${i + 1}. ${task}`);
  });
  lines.push('');
  
  // ══════════════════════════════════════════════════════════════
  // MATERIALS
  // ══════════════════════════════════════════════════════════════
  lines.push('## Materials');
  job.materials.forEach(mat => {
    const qty = mat.quantity ? `${mat.quantity} ${mat.unit || 'ea'}` : '';
    const cost = mat.estimatedCost ? `@ $${mat.estimatedCost.toFixed(2)}` : '';
    lines.push(`- ${qty ? qty + ' ' : ''}${mat.name} ${cost}`.trim());
  });
  lines.push('');
  
  // ══════════════════════════════════════════════════════════════
  // LABOR
  // ══════════════════════════════════════════════════════════════
  lines.push('## Labor');
  job.labor.forEach(lab => {
    lines.push(`- ${lab.hours}h ${lab.role} @ $${lab.rate}/hr = $${(lab.hours * lab.rate).toFixed(2)}`);
  });
  lines.push('');
  
  // ══════════════════════════════════════════════════════════════
  // FINANCIAL SNAPSHOT
  // ══════════════════════════════════════════════════════════════
  lines.push('## Financial');
  lines.push(`Est. Labor:     $${job.estimatedLaborCost.toFixed(2)}`);
  lines.push(`Est. Materials: $${job.estimatedMaterialCost.toFixed(2)}`);
  lines.push(`Est. Total:     $${job.estimatedTotal.toFixed(2)}`);
  lines.push(`Deposit Req:    ${job.depositRequired}`);
  lines.push(`Warranty:       ${job.warranty}`);
  lines.push('');
  
  // ══════════════════════════════════════════════════════════════
  // PHASES / TIMELINE
  // ══════════════════════════════════════════════════════════════
  lines.push('## Phases');
  job.phases.forEach(phase => {
    lines.push(`${phase.order}. [${phase.name}] ${phase.description}`);
  });
  lines.push('');
  
  // ══════════════════════════════════════════════════════════════
  // SAFETY & CODE
  // ══════════════════════════════════════════════════════════════
  if (job.safetyRequirements.length > 0 || job.codeRequirements.length > 0) {
    lines.push('## Safety');
    job.safetyRequirements.forEach(s => lines.push(`- ${s}`));
    if (job.permitRequired) {
      lines.push('- PERMIT REQUIRED before work begins');
    }
    if (job.inspectionRequired) {
      lines.push('- INSPECTION REQUIRED at completion');
    }
    lines.push('');
    
    lines.push('## Code');
    job.codeRequirements.forEach(c => lines.push(`- ${c}`));
    lines.push('');
  }
  
  // ══════════════════════════════════════════════════════════════
  // EXCLUSIONS
  // ══════════════════════════════════════════════════════════════
  if (job.exclusions.length > 0) {
    lines.push('## Exclusions');
    job.exclusions.forEach(e => lines.push(`- ${e}`));
    lines.push('');
  }
  
  // ══════════════════════════════════════════════════════════════
  // NOTES
  // ══════════════════════════════════════════════════════════════
  lines.push('## Notes');
  job.notes.forEach(n => lines.push(`- ${n}`));
  lines.push(`- Detected keywords: ${job.detectedKeywords.slice(0, 5).join(', ')}`);
  lines.push(`- Research sources: ${job.researchSources.join(', ')}`);
  lines.push('');
  
  // ══════════════════════════════════════════════════════════════
  // SUMMARY LINE
  // ══════════════════════════════════════════════════════════════
  lines.push('## Summary');
  lines.push(`${job.jobType} job${job.location ? ' in ' + job.location : ''}. ${job.tasks.length} tasks, ${job.materials.length} materials. Est. ${job.estimatedDays} days, $${job.estimatedTotal.toFixed(2)} total.`);
  
  return lines.join('\n');
}

// ════════════════════════════════════════════════════════════════════
// PLAN MANAGEMENT SYSTEM
// ════════════════════════════════════════════════════════════════════

// ENGAGEMENTS
apiRouter.post('/engagements', (req: Request, res: Response) => {
  const { name, description, clientName, location, intent } = req.body as CreateEngagementRequest;

  if (!name || !intent) {
    return res.status(400).json({ error: 'name and intent required' });
  }

  const creatorId = req.headers['x-user-id'] as string || 'anonymous';

  const engagement: Engagement = {
    id: uuidv4(),
    name,
    description,
    clientName,
    location,
    createdBy: creatorId,
    createdAt: Date.now(),
    status: 'active',
    intent
  };

  // TODO: Store in database
  console.log('[API] Created engagement:', engagement.id);

  res.status(201).json(engagement);
});

apiRouter.get('/engagements', (req: Request, res: Response) => {
  // TODO: Fetch from database
  res.json([]);
});

apiRouter.get('/engagements/:id', (req: Request, res: Response) => {
  const { id } = req.params;
  // TODO: Fetch from database
  res.status(404).json({ error: 'Engagement not found' });
});

// PLANS
apiRouter.post('/plans', (req: Request, res: Response) => {
  const { engagementId, name, description, flowType } = req.body as CreatePlanRequest;

  if (!engagementId || !name || !flowType) {
    return res.status(400).json({ error: 'engagementId, name, and flowType required' });
  }

  const creatorId = req.headers['x-user-id'] as string || 'anonymous';

  const plan: Plan = {
    id: uuidv4(),
    engagementId,
    name,
    description,
    phase: flowType === 'standard' ? 'draft' : 'active', // Skip proposal for small projects
    createdAt: Date.now(),
    updatedAt: Date.now(),
    jobIds: [],
    timeEntryIds: [],
    outputs: []
  };

  // TODO: Store in database
  console.log('[API] Created plan:', plan.id, 'flow:', flowType);

  res.status(201).json(plan);
});

apiRouter.get('/plans', (req: Request, res: Response) => {
  // TODO: Fetch from database
  res.json([]);
});

apiRouter.get('/plans/:id', (req: Request, res: Response) => {
  const { id } = req.params;
  // TODO: Fetch from database
  res.status(404).json({ error: 'Plan not found' });
});

apiRouter.patch('/plans/:id', (req: Request, res: Response) => {
  const { id } = req.params;
  // TODO: Update plan in database
  res.status(404).json({ error: 'Plan not found' });
});

// PROPOSALS (Standard Flow)
apiRouter.post('/plans/:planId/proposals', (req: Request, res: Response) => {
  const { planId } = req.params;
  const { title, description, scope, estimatedHours, estimatedCost } = req.body as CreateProposalRequest;

  if (!title || !description || !scope) {
    return res.status(400).json({ error: 'title, description, and scope required' });
  }

  const creatorId = req.headers['x-user-id'] as string || 'anonymous';

  const proposal: Proposal = {
    id: uuidv4(),
    planId,
    title,
    description,
    scope,
    estimatedHours,
    estimatedCost,
    createdBy: creatorId,
    createdAt: Date.now(),
    status: 'draft'
  };

  // TODO: Store proposal and update plan phase to 'proposal_pending'
  console.log('[API] Created proposal for plan:', planId);

  res.status(201).json(proposal);
});

apiRouter.post('/proposals/:id/confirm', (req: Request, res: Response) => {
  const { id } = req.params;
  const { approved, notes } = req.body as ConfirmProposalRequest;

  const confirmerId = req.headers['x-user-id'] as string || 'anonymous';

  // TODO: Update proposal status and plan phase to 'active' if approved
  console.log('[API] Proposal', id, approved ? 'approved' : 'rejected', 'by', confirmerId);

  res.json({
    status: approved ? 'approved' : 'rejected',
    confirmedAt: Date.now(),
    confirmedBy: confirmerId
  });
});

// SYNTHESIS PHASE (When jobs are complete)
apiRouter.post('/plans/:id/synthesize', async (req: Request, res: Response) => {
  const { id } = req.params;
  const { useAI, customSummary } = req.body as CreatePlanSummaryRequest;

  const creatorId = req.headers['x-user-id'] as string || 'anonymous';

  try {
    // TODO: Fetch actual plan from database
    const mockPlan: Plan = {
      id,
      engagementId: 'mock-engagement',
      name: 'Mock Plan',
      phase: 'jobs_complete',
      createdAt: Date.now(),
      updatedAt: Date.now(),
      jobIds: ['job1', 'job2'],
      timeEntryIds: ['time1', 'time2', 'time3'],
      outputs: []
    };

    let summary: PlanSummary;

    if (customSummary) {
      // Use custom summary provided by user
      summary = {
        id: uuidv4(),
        planId: id,
        title: customSummary.title,
        executiveSummary: customSummary.executiveSummary,
        workPerformed: customSummary.workPerformed,
        challenges: customSummary.challenges || [],
        recommendations: customSummary.recommendations || [],
        totalHours: 0, // TODO: Calculate from actual data
        totalCost: 0, // TODO: Calculate from actual data
        createdBy: creatorId,
        createdAt: Date.now(),
        status: 'draft'
      };
    } else {
      // Generate summary from data
      summary = await planSynthesisService.synthesizePlan(mockPlan, useAI);
      summary.id = uuidv4();
      summary.createdBy = creatorId;
    }

    // TODO: Save summary to database
    // TODO: Update plan phase to 'summary_ready'

    console.log('[API] Created summary for plan:', id, useAI ? '(AI-assisted)' : '(manual)');

    res.status(201).json(summary);
  } catch (error) {
    console.error('[API] Synthesis error:', error);
    res.status(500).json({ error: 'Failed to generate plan summary' });
  }
});

apiRouter.post('/summaries/:id/confirm', (req: Request, res: Response) => {
  const { id } = req.params;
  const { approved, notes } = req.body as ConfirmSummaryRequest;

  const confirmerId = req.headers['x-user-id'] as string || 'anonymous';

  // TODO: Update summary status and plan phase to 'output_pending' if approved
  console.log('[API] Summary', id, approved ? 'confirmed' : 'rejected', 'by', confirmerId);

  res.json({
    status: approved ? 'confirmed' : 'locked',
    confirmedAt: Date.now(),
    confirmedBy: confirmerId
  });
});

// OUTPUT GENERATION
apiRouter.post('/plans/:id/generate-output', async (req: Request, res: Response) => {
  const { id } = req.params;
  const { outputType, reportOptions, invoiceOptions } = req.body as GenerateOutputRequest;

  const generatorId = req.headers['x-user-id'] as string || 'anonymous';

  try {
    // TODO: Fetch actual plan and summary from database
    const mockPlan: Plan = {
      id,
      engagementId: 'mock-engagement',
      name: 'Mock Plan',
      phase: 'summary_ready',
      createdAt: Date.now(),
      updatedAt: Date.now(),
      jobIds: ['job1', 'job2'],
      timeEntryIds: ['time1', 'time2', 'time3'],
      outputs: []
    };

    const mockSummary: PlanSummary = {
      id: 'mock-summary',
      planId: id,
      title: 'Mock Summary',
      executiveSummary: 'Project completed successfully',
      workPerformed: ['Task 1 completed', 'Task 2 completed'],
      challenges: [],
      recommendations: [],
      totalHours: 24,
      totalCost: 1200,
      createdBy: 'system',
      createdAt: Date.now(),
      status: 'confirmed'
    };

    // Generate the requested outputs
    const outputs = await outputGeneratorService.generateOutputs(mockPlan, mockSummary, {
      planId: id,
      outputType,
      reportOptions,
      invoiceOptions
    });

    // TODO: Save outputs to database
    // TODO: Update plan with output references
    // TODO: Create immutable snapshot

    const result = {
      outputType,
      generatedAt: Date.now(),
      generatedBy: generatorId,
      planId: id,
      outputs: {} as any
    };

    if (outputs.report) {
      result.outputs.reportId = outputs.report.id;
      console.log('[API] Generated report:', outputs.report.id);
    }

    if (outputs.invoice) {
      result.outputs.invoiceId = outputs.invoice.id;
      console.log('[API] Generated invoice:', outputs.invoice.id);
    }

    // TODO: Update plan phase to 'output_generated'

    res.json(result);
  } catch (error) {
    console.error('[API] Output generation error:', error);
    res.status(500).json({ error: 'Failed to generate outputs' });
  }
});

// REPORTS
apiRouter.get('/reports', (req: Request, res: Response) => {
  // TODO: Fetch reports from database
  res.json([]);
});

apiRouter.get('/reports/:id', (req: Request, res: Response) => {
  const { id } = req.params;
  // TODO: Fetch report from database
  res.status(404).json({ error: 'Report not found' });
});

// INVOICES
apiRouter.get('/invoices', (req: Request, res: Response) => {
  // TODO: Fetch invoices from database
  res.json([]);
});

apiRouter.get('/invoices/:id', (req: Request, res: Response) => {
  const { id } = req.params;
  // TODO: Fetch invoice from database
  res.status(404).json({ error: 'Invoice not found' });
});

apiRouter.patch('/invoices/:id/status', (req: Request, res: Response) => {
  const { id } = req.params;
  const { status } = req.body;

  // TODO: Update invoice status
  console.log('[API] Updated invoice', id, 'status to:', status);

  res.json({ status: 'updated' });
});

// ARCHIVE
apiRouter.post('/plans/:id/archive', async (req: Request, res: Response) => {
  const { id } = req.params;
  const archiverId = req.headers['x-user-id'] as string || 'anonymous';

  try {
    // TODO: Fetch actual plan from database
    const mockPlan: Plan = {
      id,
      engagementId: 'mock-engagement',
      name: 'Mock Plan',
      phase: 'output_generated',
      createdAt: Date.now(),
      updatedAt: Date.now(),
      jobIds: ['job1', 'job2'],
      timeEntryIds: ['time1', 'time2', 'time3'],
      outputs: []
    };

    const snapshot = await archiveService.archivePlan(mockPlan, archiverId);

    // TODO: Update plan status to archived in database

    console.log('[API] Archived plan:', id, 'by:', archiverId);

    res.json({
      archived: true,
      archivedAt: snapshot.createdAt,
      archivedBy: archiverId,
      snapshotId: snapshot.id,
      immutableHash: snapshot.immutableHash
    });
  } catch (error) {
    console.error('[API] Archive error:', error);
    res.status(500).json({ error: 'Failed to archive plan' });
  }
});

apiRouter.get('/archive/plans/:id', async (req: Request, res: Response) => {
  const { id } = req.params;

  try {
    const snapshot = await archiveService.getArchivedPlan(id);
    if (!snapshot) {
      return res.status(404).json({ error: 'Archived plan not found' });
    }

    // Verify integrity before returning
    const isValid = archiveService.verifySnapshotIntegrity(snapshot);
    if (!isValid) {
      console.error('[API] Archive integrity check failed for plan:', id);
      return res.status(500).json({ error: 'Archive integrity compromised' });
    }

    res.json({
      snapshot,
      integrityVerified: true,
      immutableHash: snapshot.immutableHash
    });
  } catch (error) {
    console.error('[API] Archive retrieval error:', error);
    res.status(500).json({ error: 'Failed to retrieve archived plan' });
  }
});

// ARCHIVE EXPORT
apiRouter.get('/archive/plans/:id/export', async (req: Request, res: Response) => {
  const { id } = req.params;
  const format = (req.query.format as string) || 'json';

  try {
    const exported = await archiveService.exportArchivedPlan(id, format as any);

    res.json({
      planId: id,
      format,
      exported,
      exportedAt: Date.now()
    });
  } catch (error) {
    console.error('[API] Archive export error:', error);
    res.status(500).json({ error: 'Failed to export archived plan' });
  }
});

// ════════════════════════════════════════════════════════════════════
// SYSTEM LAW ENFORCEMENT — FLOW VALIDATION
// ════════════════════════════════════════════════════════════════════

/**
 * GET SYSTEM FLOW STATUS
 * Returns current flow state and enforces system law
 */
apiRouter.get('/system/flow-status', (req: Request, res: Response) => {
  const { engagementId, jobIds, timeEntryIds } = req.query;

  // System Law: Determine current flow state
  let flowType: 'standard' | 'small_project' | 'unknown' = 'unknown';
  let currentPhase: string = 'unknown';
  let nextAllowedActions: string[] = [];
  let systemLawViolations: string[] = [];

  try {
    // TODO: Fetch actual engagement, plans, jobs, and time entries from database
    // For now, simulate based on query parameters

    if (engagementId) {
      // Standard flow: Engagement exists
      flowType = 'standard';
      currentPhase = 'engagement_exists';

      // Check for proposal
      const hasProposal = false; // TODO: Check database
      if (hasProposal) {
        currentPhase = 'proposal_created';
        nextAllowedActions = ['confirm_proposal'];
      } else {
        nextAllowedActions = ['create_proposal', 'add_jobs_manually'];
      }

      // Check for confirmed proposal
      const proposalConfirmed = false; // TODO: Check database
      if (proposalConfirmed) {
        currentPhase = 'proposal_confirmed';
        nextAllowedActions = ['generate_jobs_from_proposal'];
      }

      // Check for jobs
      if (Array.isArray(jobIds) && jobIds.length > 0) {
        currentPhase = 'jobs_exist';
        nextAllowedActions = ['execute_jobs', 'track_time'];
      }

      // Check for time entries
      if (Array.isArray(timeEntryIds) && timeEntryIds.length > 0) {
        currentPhase = 'time_running';
        nextAllowedActions = ['clock_out', 'request_breaks'];
      }

      // Check if jobs are complete
      const jobsComplete = false; // TODO: Check if all jobs in jobIds are complete
      const timeClosed = false; // TODO: Check if all time entries are closed

      if (jobsComplete && timeClosed) {
        currentPhase = 'jobs_complete';
        nextAllowedActions = ['return_to_plan', 'create_summary'];
      }

      // Check for plan summary
      const hasSummary = false; // TODO: Check database
      if (hasSummary) {
        currentPhase = 'summary_created';
        nextAllowedActions = ['confirm_summary'];
      }

      // Check if summary is confirmed
      const summaryConfirmed = false; // TODO: Check database
      if (summaryConfirmed) {
        currentPhase = 'summary_confirmed';
        nextAllowedActions = ['generate_output', 'select_report_invoice'];
      }

      // Check for outputs generated
      const outputsGenerated = false; // TODO: Check database
      if (outputsGenerated) {
        currentPhase = 'outputs_generated';
        nextAllowedActions = ['archive'];
      }

      // Check if archived
      const archived = false; // TODO: Check database
      if (archived) {
        currentPhase = 'archived';
        nextAllowedActions = []; // Nothing - read-only forever
      }

    } else if (Array.isArray(jobIds) && jobIds.length > 0) {
      // Small project flow: Jobs exist without engagement
      const jobIdsArray = (jobIds as (string | any)[]).filter((id): id is string => typeof id === 'string');
      const timeEntryIdsArray = Array.isArray(timeEntryIds) 
        ? (timeEntryIds as (string | any)[]).filter((id): id is string => typeof id === 'string')
        : [];
      const eligible = autoPlanCreator.validateSmallProjectEligibility(jobIdsArray, timeEntryIdsArray);
      if (eligible) {
        flowType = 'small_project';
        currentPhase = 'jobs_manual_entry';

        // Check if jobs are complete and time closed
        const jobsComplete = false; // TODO: Check database
        const timeClosed = false; // TODO: Check database

        if (jobsComplete && timeClosed) {
          currentPhase = 'ready_for_auto_plan';
          nextAllowedActions = ['create_auto_plan', 'synthesize'];
        } else {
          nextAllowedActions = ['execute_jobs', 'track_time'];
        }

        // Check for auto-created plan
        const hasAutoPlan = false; // TODO: Check database
        if (hasAutoPlan) {
          currentPhase = 'auto_plan_created';
          nextAllowedActions = ['create_summary', 'confirm_summary'];
        }

        // Follow same summary → output → archive flow as standard

      } else {
        systemLawViolations.push('PROJECT_TOO_LARGE_FOR_SMALL_FLOW');
        flowType = 'unknown';
        nextAllowedActions = ['create_engagement', 'use_standard_flow'];
      }
    }

    // System Law Violations Check
    if (flowType === 'small_project') {
      // Small projects must eventually create Plan
      if (currentPhase === 'jobs_complete' && !nextAllowedActions.includes('create_auto_plan')) {
        systemLawViolations.push('SMALL_PROJECT_MUST_CREATE_PLAN');
      }
    }

    // Check for out-of-order operations
    // TODO: Add more violation checks based on system law

    res.json({
      flowType,
      currentPhase,
      nextAllowedActions,
      systemLawViolations,
      systemLaw: {
        enforced: true,
        version: 'v3',
        coreRule: 'Plan may be explicit or implicit. Plan may be early or deferred. Plan is never optional.'
      }
    });

  } catch (error) {
    console.error('[SystemLaw] Flow status check failed:', error);
    res.status(500).json({
      error: 'Failed to determine flow status',
      systemLaw: {
        enforced: true,
        version: 'v3',
        status: 'error_checking_flow'
      }
    });
  }
});

/**
 * VALIDATE ACTION AGAINST SYSTEM LAW
 * Check if a proposed action is allowed in current state
 */
apiRouter.post('/system/validate-action', (req: Request, res: Response) => {
  const { action, currentPhase, flowType, context } = req.body;

  if (!action || !currentPhase || !flowType) {
    return res.status(400).json({ error: 'action, currentPhase, and flowType required' });
  }

  // System Law: Define allowed actions per phase and flow type
  const allowedActions: Record<string, Record<string, string[]>> = {
    standard: {
      engagement_exists: ['create_proposal', 'add_jobs_manually'],
      proposal_created: ['confirm_proposal', 'edit_proposal'],
      proposal_confirmed: ['generate_jobs_from_proposal'],
      jobs_exist: ['execute_jobs', 'track_time', 'edit_jobs'],
      time_running: ['clock_out', 'request_breaks', 'edit_time'],
      jobs_complete: ['return_to_plan', 'create_summary'],
      summary_created: ['confirm_summary', 'edit_summary'],
      summary_confirmed: ['generate_output', 'select_report_invoice'],
      outputs_generated: ['archive'],
      archived: [] // Nothing allowed - read-only forever
    },
    small_project: {
      jobs_manual_entry: ['execute_jobs', 'track_time'],
      ready_for_auto_plan: ['create_auto_plan', 'synthesize'],
      auto_plan_created: ['create_summary', 'confirm_summary'],
      summary_created: ['confirm_summary', 'edit_summary'],
      summary_confirmed: ['generate_output', 'select_report_invoice'],
      outputs_generated: ['archive'],
      archived: [] // Nothing allowed - read-only forever
    }
  };

  const flowActions = allowedActions[flowType]?.[currentPhase] || [];
  const actionAllowed = flowActions.includes(action);

  // Check for system law violations
  let violations: string[] = [];

  if (!actionAllowed) {
    violations.push('ACTION_OUT_OF_ORDER');
  }

  // Additional context checks
  if (action === 'create_auto_plan' && flowType !== 'small_project') {
    violations.push('AUTO_PLAN_ONLY_FOR_SMALL_PROJECTS');
  }

  if (action === 'archive' && currentPhase !== 'outputs_generated') {
    violations.push('ARCHIVE_ONLY_AFTER_OUTPUTS');
  }

  res.json({
    action,
    allowed: actionAllowed,
    violations,
    allowedActions: flowActions,
    systemLaw: {
      enforced: true,
      version: 'v3',
      rule: actionAllowed ?
        'Action permitted in current phase' :
        'Action violates system law - operations must follow approved flow'
    }
  });
});

// ════════════════════════════════════════════════════════════════════
// SMALL PROJECT FLOW — AUTO PLAN CREATION
// ════════════════════════════════════════════════════════════════════

/**
 * VALIDATE SMALL PROJECT ELIGIBILITY
 * Check if jobs/time qualify for small project flow (skip early PLAN)
 */
apiRouter.post('/small-project/validate-eligibility', (req: Request, res: Response) => {
  const { jobIds, timeEntryIds } = req.body;

  if (!jobIds || !timeEntryIds || !Array.isArray(jobIds) || !Array.isArray(timeEntryIds)) {
    return res.status(400).json({ error: 'jobIds and timeEntryIds arrays required' });
  }

  const eligible = autoPlanCreator.validateSmallProjectEligibility(jobIds, timeEntryIds);

  res.json({
    eligible,
    criteria: {
      maxJobs: 5,
      maxTimeEntries: 20,
      actualJobs: jobIds.length,
      actualTimeEntries: timeEntryIds.length
    }
  });
});

/**
 * CREATE AUTO PLAN FROM COLLECTED FACTS
 * Small project flow: Auto-create Plan when synthesis is requested
 */
apiRouter.post('/small-project/create-auto-plan', async (req: Request, res: Response) => {
  const {
    jobIds,
    timeEntryIds,
    clientUuid,
    workerUuids,
    foremanUuid,
    engagementName,
    latitude,
    longitude,
    verticalUnitId,
    environmentalContext
  } = req.body;

  if (!jobIds || !timeEntryIds || !clientUuid || !workerUuids) {
    return res.status(400).json({ error: 'jobIds, timeEntryIds, clientUuid, and workerUuids required' });
  }

  try {
    // First validate eligibility
    const eligible = autoPlanCreator.validateSmallProjectEligibility(jobIds, timeEntryIds);
    if (!eligible) {
      return res.status(400).json({
        error: 'Project does not qualify for small project flow',
        criteria: { maxJobs: 5, maxTimeEntries: 20 }
      });
    }

    // Create auto Plan from facts
    const { plan, validation } = await autoPlanCreator.createPlanFromFacts(
      jobIds,
      timeEntryIds,
      clientUuid,
      workerUuids,
      foremanUuid,
      engagementName,
      latitude,
      longitude,
      verticalUnitId,
      environmentalContext
    );

    if (!validation.valid) {
      return res.status(400).json(validation);
    }

    // TODO: Store plan in database
    console.log('[SmallProject] Auto-created plan:', plan.id, 'for', jobIds.length, 'jobs');

    res.json({
      plan,
      validation,
      flowType: 'small_project',
      nextStep: 'summary_creation'
    });
  } catch (error) {
    console.error('[SmallProject] Auto plan creation failed:', error);
    res.status(500).json({ error: 'Failed to create auto plan' });
  }
});

/**
 * TRANSITION AUTO PLAN PHASE
 * Small project flow: Move through phases automatically
 */
apiRouter.post('/small-project/transition-phase', async (req: Request, res: Response) => {
  const { planId, targetPhase } = req.body;

  if (!planId || !targetPhase) {
    return res.status(400).json({ error: 'planId and targetPhase required' });
  }

  if (!['summary_ready', 'finalized'].includes(targetPhase)) {
    return res.status(400).json({ error: 'Invalid target phase for small project flow' });
  }

  try {
    // TODO: Fetch actual plan from database
    const mockPlan: Plan = {
      id: planId,
      engagementId: 'mock-engagement',
      name: 'Mock Small Project Plan',
      phase: 'draft',
      createdAt: Date.now(),
      updatedAt: Date.now(),
      jobIds: ['job1'],
      timeEntryIds: ['time1'],
      outputs: []
    };

    const updatedPlan = await autoPlanCreator.transitionAutoPlan(mockPlan, targetPhase);

    // TODO: Update plan in database
    console.log('[SmallProject] Transitioned plan', planId, 'to phase:', targetPhase);

    res.json({
      plan: updatedPlan,
      transition: {
        from: mockPlan.phase,
        to: targetPhase,
        timestamp: Date.now()
      }
    });
  } catch (error) {
    console.error('[SmallProject] Phase transition failed:', error);
    res.status(500).json({ error: 'Failed to transition plan phase' });
  }
});

// ════════════════════════════════════════════════════════════════════
// PLAN AUTHORITY — VALIDATION SYSTEM
// ════════════════════════════════════════════════════════════════════

/**
 * VALIDATE PLAN CREATION
 * Returns binary validation - no assistance or suggestions
 */
apiRouter.post('/plan-authority/validate-creation', async (req: Request, res: Response) => {
  const {
    engagementId,
    name,
    intent,
    jobIds,
    timeEntryIds,
    clientUuid,
    workerUuids,
    foremanUuid,
    latitude,
    longitude,
    verticalUnitId,
    environmentalContext
  } = req.body;

  if (!engagementId || !name || !intent || !jobIds || !timeEntryIds ||
      !clientUuid || !workerUuids) {
    return res.status(400).json({
      valid: false,
      message: 'PLAN REJECTED — SYSTEM LAW VIOLATION: CONTENT_2_SCOPE_EXPLICIT'
    });
  }

  try {
    // TODO: Fetch actual engagement from database
    const mockEngagement: Engagement = {
      id: engagementId,
      name: 'Mock Engagement',
      intent: 'Mock intent',
      createdBy: 'system',
      createdAt: Date.now(),
      status: 'active'
    };

    const validation = await planAuthority.validatePlanCreation(mockEngagement, {
      name,
      intent,
      jobIds,
      timeEntryIds,
      clientUuid,
      workerUuids,
      foremanUuid,
      latitude,
      longitude,
      verticalUnitId,
      environmentalContext
    });

    if (!validation.valid) {
      return res.status(400).json(validation);
    }

    res.json(validation);
  } catch (error) {
    console.error('[PlanAuthority] Validation error:', error);
    res.status(500).json({
      valid: false,
      message: 'PLAN REJECTED — SYSTEM LAW VIOLATION'
    });
  }
});

/**
 * VALIDATE PLAN FINALIZATION
 */
apiRouter.post('/plan-authority/validate-finalization', async (req: Request, res: Response) => {
  const { planId, summaryId } = req.body;

  if (!planId || !summaryId) {
    return res.status(400).json({
      valid: false,
      message: 'PLAN REJECTED — SYSTEM LAW VIOLATION'
    });
  }

  try {
    // TODO: Fetch actual plan and summary from database
    const mockPlan: Plan = {
      id: planId,
      engagementId: 'mock-engagement',
      name: 'Mock Plan',
      phase: 'summary_ready',
      createdAt: Date.now(),
      updatedAt: Date.now(),
      jobIds: ['job1'],
      timeEntryIds: ['time1'],
      outputs: []
    };

    const mockSummary: PlanSummary = {
      id: summaryId,
      planId,
      title: 'Mock Summary',
      executiveSummary: 'Work completed successfully',
      workPerformed: ['Task completed'],
      challenges: [],
      recommendations: [],
      totalHours: 8,
      totalCost: 400,
      createdBy: 'system',
      createdAt: Date.now(),
      confirmedAt: Date.now(),
      confirmedBy: 'foreman',
      status: 'confirmed'
    };

    const validation = await planAuthority.validatePlanFinalization(mockPlan, mockSummary);

    if (!validation.valid) {
      return res.status(400).json(validation);
    }

    res.json(validation);
  } catch (error) {
    console.error('[PlanAuthority] Finalization validation error:', error);
    res.status(500).json({
      valid: false,
      message: 'PLAN REJECTED — SYSTEM LAW VIOLATION'
    });
  }
});

/**
 * VALIDATE OUTPUT AUTHORIZATION
 */
apiRouter.post('/plan-authority/validate-output', (req: Request, res: Response) => {
  const { planId, outputType } = req.body;

  if (!planId || !outputType) {
    return res.status(400).json({
      valid: false,
      message: 'PLAN REJECTED — SYSTEM LAW VIOLATION'
    });
  }

  try {
    // TODO: Fetch actual plan from database
    const mockPlan: Plan = {
      id: planId,
      engagementId: 'mock-engagement',
      name: 'Mock Plan',
      phase: 'archived', // Must be archived for output
      createdAt: Date.now(),
      updatedAt: Date.now(),
      jobIds: ['job1'],
      timeEntryIds: ['time1'],
      outputs: [],
      archivedAt: Date.now(),
      immutableHash: 'mock_hash'
    };

    const validation = planAuthority.validateOutputAuthorization(mockPlan, outputType);

    if (!validation.valid) {
      return res.status(400).json(validation);
    }

    res.json(validation);
  } catch (error) {
    console.error('[PlanAuthority] Output validation error:', error);
    res.status(500).json({
      valid: false,
      message: 'PLAN REJECTED — SYSTEM LAW VIOLATION'
    });
  }
});

// ════════════════════════════════════════════════════════════════════
// SETTINGS — SYSTEM CONFIGURATION (NON-EXECUTIVE)
// ════════════════════════════════════════════════════════════════════

/**
 * GET SYSTEM SETTINGS
 * System Law: Settings configure reality, they do not execute work
 */
apiRouter.get('/settings', (req: Request, res: Response) => {
  // TODO: Fetch from database with user context
  // Settings never participate in payroll, planning, or reporting logic

  res.json({
    identity: {
      userId: 'current_user',
      displayName: 'Current User',
      role: 'worker', // 'worker' | 'foreman' | 'admin'
      organizationId: 'org_123'
    },
    permissions: {
      canCreateJobs: true,
      canApproveBreaks: false,
      canFinalizePlans: false,
      canAccessArchive: true
    },
    connectivity: {
      bleMeshEnabled: true,
      onlineSyncEnabled: true,
      gatewayMode: 'hybrid', // 'online' | 'gateway' | 'hybrid'
      relayConnected: true
    },
    ai: {
      summarizationEnabled: true,
      breakRequestAssistEnabled: true,
      contextAnalysisEnabled: false
    },
    archive: {
      readOnlyAccess: true,
      exportFormats: ['pdf', 'html', 'xlsx'],
      retentionPolicy: 'forever' // System Law: Archive is forever
    },
    ui: {
      theme: 'system',
      notifications: {
        breakRequests: true,
        jobCompletions: true,
        planFinalizations: false
      }
    }
  });
});

/**
 * UPDATE SETTINGS
 * System Law: Settings never change data, only configuration
 */
apiRouter.patch('/settings', (req: Request, res: Response) => {
  const updates = req.body;

  // Validate that updates are configuration-only
  const allowedCategories = ['connectivity', 'ai', 'ui'];
  const requestedCategories = Object.keys(updates);

  const invalidCategories = requestedCategories.filter(cat => !allowedCategories.includes(cat));

  if (invalidCategories.length > 0) {
    return res.status(400).json({
      error: 'Invalid settings categories',
      allowed: allowedCategories,
      requested: invalidCategories,
      systemLaw: 'Settings configure reality, they do not execute work or change data'
    });
  }

  // TODO: Validate and store settings updates
  console.log('[Settings] Updated configuration:', updates);

  res.json({
    success: true,
    updated: updates,
    systemLaw: {
      enforced: true,
      reminder: 'Settings configure reality, they do not execute work'
    }
  });
});

/**
 * GET CONNECTIVITY STATUS
 * Infrastructure status, not workflow status
 */
apiRouter.get('/settings/connectivity', (req: Request, res: Response) => {
  // TODO: Get actual connectivity status from gateway manager

  res.json({
    bleMesh: {
      enabled: true,
      connectedPeers: 5,
      lastActivity: Date.now() - 30000,
      status: 'active'
    },
    online: {
      enabled: true,
      connected: true,
      lastSync: Date.now() - 60000,
      status: 'online'
    },
    gateway: {
      mode: 'hybrid',
      relayConnected: true,
      relayId: 'relay_001',
      capabilities: ['mesh_bridge', 'cloud_sync']
    },
    systemLaw: {
      enforced: true,
      reminder: 'Connectivity is infrastructure, not workflow'
    }
  });
});

// ════════════════════════════════════════════════════════════════════
// REPORT GENERATION PIPELINE
// ════════════════════════════════════════════════════════════════════

/**
 * ASSEMBLE REPORT FROM PLAN SNAPSHOT
 */
apiRouter.post('/reports/assemble', async (req: Request, res: Response) => {
  const { planId } = req.body;

  if (!planId) {
    return res.status(400).json({ error: 'planId is required' });
  }

  try {
    // TODO: Fetch actual archived plan snapshot
    const mockSnapshot: PlanSnapshot = {
      id: `snapshot-${planId}`,
      planId,
      snapshotType: 'output',
      data: {
        id: planId,
        engagementId: 'mock-engagement',
        name: 'Mock Plan',
        phase: 'archived',
        createdAt: Date.now(),
        updatedAt: Date.now(),
        jobIds: ['job1'],
        timeEntryIds: ['time1'],
        outputs: [],
        summary: {
          id: 'summary1',
          planId,
          title: 'Project Summary',
          executiveSummary: 'Project completed successfully',
          workPerformed: ['Task 1', 'Task 2'],
          totalHours: 16,
          totalCost: 800,
          createdBy: 'system',
          createdAt: Date.now(),
          status: 'confirmed'
        }
      },
      jobs: [
        { id: 'job1', title: 'Job 1', description: 'Work on job 1', status: 'done' }
      ],
      timeEntries: [
        {
          id: 'time1',
          userName: 'Worker 1',
          durationMinutes: 480,
          jobId: 'job1',
          clockOutContext: { type: 'worker_note', content: 'Completed work', generatedAt: Date.now(), generatedBy: 'worker1', isImmutable: true }
        }
      ],
      messages: [],
      createdAt: Date.now(),
      immutableHash: 'mock_hash'
    };

    const structuredReport = reportAssembler.assembleFromPlanSnapshot(mockSnapshot);

    res.json({
      success: true,
      reportModel: structuredReport
    });
  } catch (error) {
    console.error('[ReportAssembler] Assembly failed:', error);
    res.status(500).json({ error: 'Failed to assemble report' });
  }
});

/**
 * RENDER REPORT
 */
apiRouter.post('/reports/render', async (req: Request, res: Response) => {
  const { reportModel, format } = req.body;

  if (!reportModel || !format) {
    return res.status(400).json({ error: 'reportModel and format are required' });
  }

  if (!['pdf', 'html', 'xlsx'].includes(format)) {
    return res.status(400).json({ error: 'Invalid format. Supported: pdf, html, xlsx' });
  }

  try {
    const renderedReport = reportRenderer.render(reportModel, format);

    res.json({
      success: true,
      renderedReport: {
        id: renderedReport.id,
        format: renderedReport.format,
        filename: renderedReport.filename,
        metadata: renderedReport.metadata,
        contentLength: renderedReport.content.length
      }
    });
  } catch (error) {
    console.error('[ReportRenderer] Rendering failed:', error);
    res.status(500).json({ error: 'Failed to render report' });
  }
});

/**
 * DOWNLOAD REPORT
 */
apiRouter.post('/reports/download', async (req: Request, res: Response) => {
  const { renderedReport } = req.body;

  if (!renderedReport) {
    return res.status(400).json({ error: 'renderedReport is required' });
  }

  try {
    await reportOutput.download(renderedReport);

    res.json({
      success: true,
      message: `Report prepared for download: ${renderedReport.filename}`,
      downloadUrl: `/downloads/${renderedReport.filename}` // Mock URL
    });
  } catch (error) {
    console.error('[ReportOutput] Download failed:', error);
    res.status(500).json({ error: 'Failed to prepare download' });
  }
});

/**
 * SHARE REPORT
 */
apiRouter.post('/reports/share', async (req: Request, res: Response) => {
  const { renderedReport, recipients } = req.body;

  if (!renderedReport || !recipients || !Array.isArray(recipients)) {
    return res.status(400).json({ error: 'renderedReport and recipients array are required' });
  }

  try {
    await reportOutput.share(renderedReport, recipients);

    res.json({
      success: true,
      message: `Report shared with ${recipients.length} recipients`,
      recipients
    });
  } catch (error) {
    console.error('[ReportOutput] Share failed:', error);
    res.status(500).json({ error: 'Failed to share report' });
  }
});

/**
 * FULL REPORT PIPELINE
 * Assemble → Render → Output in one request
 */
apiRouter.post('/reports/generate', async (req: Request, res: Response) => {
  const { planId, format, outputAction, recipients } = req.body;

  if (!planId || !format) {
    return res.status(400).json({ error: 'planId and format are required' });
  }

  try {
    // Step 1: Get plan snapshot
    const mockSnapshot: PlanSnapshot = {
      id: `snapshot-${planId}`,
      planId,
      snapshotType: 'output',
      data: {
        id: planId,
        engagementId: 'mock-engagement',
        name: 'Mock Plan',
        phase: 'archived',
        createdAt: Date.now(),
        updatedAt: Date.now(),
        jobIds: ['job1'],
        timeEntryIds: ['time1'],
        outputs: [],
        summary: {
          id: 'summary1',
          planId,
          title: 'Project Summary',
          executiveSummary: 'Project completed successfully',
          workPerformed: ['Task 1', 'Task 2'],
          totalHours: 16,
          totalCost: 800,
          createdBy: 'system',
          createdAt: Date.now(),
          status: 'confirmed'
        }
      },
      jobs: [{ id: 'job1', title: 'Job 1', description: 'Work on job 1', status: 'done' }],
      timeEntries: [{
        id: 'time1',
        userName: 'Worker 1',
        durationMinutes: 480,
        jobId: 'job1',
        clockOutContext: { type: 'worker_note', content: 'Completed work', generatedAt: Date.now(), generatedBy: 'worker1', isImmutable: true }
      }],
      messages: [],
      createdAt: Date.now(),
      immutableHash: 'mock_hash'
    };

    // Step 2: Assemble
    const structuredReport = reportAssembler.assembleFromPlanSnapshot(mockSnapshot);

    // Step 3: Render
    const renderedReport = reportRenderer.render(structuredReport, format);

    // Step 4: Output action
    let outputResult;
    switch (outputAction) {
      case 'download':
        await reportOutput.download(renderedReport);
        outputResult = { action: 'download', filename: renderedReport.filename };
        break;
      case 'share':
        if (!recipients) {
          return res.status(400).json({ error: 'recipients required for share action' });
        }
        await reportOutput.share(renderedReport, recipients);
        outputResult = { action: 'share', recipients: recipients.length };
        break;
      default:
        // Just render, don't output
        outputResult = { action: 'rendered', contentLength: renderedReport.content.length };
    }

    res.json({
      success: true,
      pipeline: {
        assembled: true,
        rendered: true,
        output: outputResult
      },
      report: {
        id: renderedReport.id,
        format: renderedReport.format,
        filename: renderedReport.filename
      }
    });
  } catch (error) {
    console.error('[ReportPipeline] Generation failed:', error);
    res.status(500).json({ error: 'Report generation failed' });
  }
});

// ════════════════════════════════════════════════════════════════════
// HEALTH
// ════════════════════════════════════════════════════════════════════

apiRouter.get('/health', (_req: Request, res: Response) => {
  res.json({
    status: 'ok',
    timestamp: Date.now(),
    channels: channelRegistry.list().length,
    onlineUsers: presenceManager.getOnline().length,
    relays: gatewayManager.getAll().length,
    wsClients: wsHandler.getClientCount(),
  });
});

/**
 * Force refresh all WebSocket client subscriptions
 * Call this after creating channels when clients were already connected
 */
apiRouter.post('/refresh-subscriptions', (_req: Request, res: Response) => {
  wsHandler.refreshAllSubscriptions();
  res.json({ 
    success: true, 
    message: 'Subscriptions refreshed for all connected clients',
    clientCount: wsHandler.getClientCount()
  });
});
