/**
 * Smith Net API
 * Control + Gateway endpoints for desktop portal and automation
 */

import { Router, Request, Response } from 'express';
import fs from 'fs';
import path from 'path';
import { proposalService } from './proposals';
import { channelRegistry } from './channelRegistry';
import { messageStore } from './messageStore';
import { presenceManager } from './presenceManager';
import { gatewayManager } from './gatewayManager';
import { wsHandler } from './wsHandler';
import { createMessage, publish } from './messageBus';
import { reportAssembler } from './reportAssembler';
import { reportRenderer } from './reportRenderer';
import { reportOutput } from './reportOutput';
import { createIntent, proposeIntent, confirmIntent, createNewVersion, autoGenerateIntent } from './intentService';
import { validateIntentCreation } from './intentAuthority';
import { synthesize, getArtifact } from './synthesizer';
import { validateSynthesisInputs } from './synthesisAuthority';
import { seal, amend, getLedgerEntry } from './ledger';
import {
  CreateChannelRequest,
  InjectMessageRequest,
  Message,
  AccessRequestPayload,
  AccessResponsePayload,
  UpdateChannelAccessPayload,
  UpdateChannelVisibilityPayload,
  Engagement,
  CreateEngagementRequest,
  PlanSnapshot
} from './types';
import { v4 as uuidv4 } from 'uuid';
import { invoiceLinkService } from './invoiceLinks';
import { wageDataService } from './wageData';
import { authenticateToken, AuthenticatedRequest } from './auth';

export const apiRouter = Router();

// All /api/* routes below require a valid JWT.
// Public routes (/api/auth/*, /api/admin/*, /api/health) are mounted BEFORE this router in server.ts.
// Per F1.1 (PRD): replaces legacy `X-User-Id` header-based identity with JWT.
apiRouter.use(authenticateToken);

// ════════════════════════════════════════════════════════════════════
// CHANNELS
// ════════════════════════════════════════════════════════════════════

/**
 * Create a new channel
 */
apiRouter.post('/channels', async (req: Request, res: Response) => {
  const { name, type, memberIds, visibility, requiresApproval } = req.body as CreateChannelRequest;

  if (!name || !type) {
    return res.status(400).json({ error: 'name and type required' });
  }

  // F1.1: identity from JWT (authenticateToken middleware applied at router level).
  const creatorId = (req as AuthenticatedRequest).user!.id;

  const channel = await channelRegistry.create(
    name,
    type,
    creatorId,
    memberIds,
    visibility || 'public',
    requiresApproval || false
  );

  // Broadcast channel creation
  wsHandler.broadcastChannelEvent('channel_created', channel);

  // Pick up members already connected so they receive DMs/private channels
  // without needing to reconnect.
  await wsHandler.refreshAllSubscriptions();

  res.status(201).json(channel);
});

/**
 * List all channels
 */
apiRouter.get('/channels', (req: Request, res: Response) => {
  // F1.1: identity from JWT (authenticateToken middleware applied at router level).
  // Always scope to authenticated user (no fallback to "list everything" anymore).
  const userId = (req as AuthenticatedRequest).user!.id;
  const channels = channelRegistry.listForUser(userId);

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
apiRouter.patch('/channels/:id', async (req: Request, res: Response) => {
  const channel = await channelRegistry.update(req.params.id, req.body);
  
  if (!channel) {
    return res.status(404).json({ error: 'Channel not found' });
  }

  wsHandler.broadcastChannelEvent('channel_updated', channel);
  res.json(channel);
});

/**
 * Delete channel
 */
apiRouter.delete('/channels/:id', async (req: Request, res: Response) => {
  const success = await channelRegistry.delete(req.params.id);
  
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
apiRouter.post('/channels/:id/access/request', async (req: Request, res: Response) => {
  const channelId = req.params.id;
  // F1.1: identity from JWT.
  const userId = (req as AuthenticatedRequest).user!.id;

  const success = await channelRegistry.requestAccess(channelId, userId);
  
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
apiRouter.post('/channels/:id/access/respond', async (req: Request, res: Response) => {
  const channelId = req.params.id;
  // F1.1: identity from JWT.
  const managerId = (req as AuthenticatedRequest).user!.id;
  const { requesterId, approve } = req.body as AccessResponsePayload;

  const success = await channelRegistry.respondToAccessRequest(channelId, requesterId, managerId, approve);
  
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
apiRouter.post('/channels/:id/access/user', async (req: Request, res: Response) => {
  const channelId = req.params.id;
  // F1.1: identity from JWT.
  const managerId = (req as AuthenticatedRequest).user!.id;
  const { userId, allow } = req.body as UpdateChannelAccessPayload;

  const success = await channelRegistry.updateUserAccess(channelId, userId, managerId, allow);
  
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
apiRouter.post('/channels/:id/visibility', async (req: Request, res: Response) => {
  const channelId = req.params.id;
  // F1.1: identity from JWT.
  const managerId = (req as AuthenticatedRequest).user!.id;
  const { visibility, requiresApproval } = req.body as UpdateChannelVisibilityPayload;

  const success = await channelRegistry.updateVisibility(channelId, managerId, visibility, requiresApproval);
  
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
  // F1.1: identity from JWT.
  const userId = (req as AuthenticatedRequest).user!.id;

  const status = channelRegistry.getAccessStatus(channelId, userId);
  res.json({ status });
});

/**
 * Get pending access requests for a channel (for managers)
 */
apiRouter.get('/channels/:id/access/pending', (req: Request, res: Response) => {
  const channelId = req.params.id;
  // F1.1: identity from JWT.
  const managerId = (req as AuthenticatedRequest).user!.id;

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
  // F1.1: identity from JWT.
  const requesterId = (req as AuthenticatedRequest).user!.id;

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
  let { channelId, content, meshOnly, id: clientId } = req.body as InjectMessageRequest & { meshOnly?: boolean; id?: string };
  // F1.1: identity from JWT (was X-User-Id + X-User-Name).
  const auth = (req as AuthenticatedRequest).user!;
  const senderId = auth.id;
  const senderName = auth.displayName || auth.email || 'User';

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
    origin,
    undefined,
    undefined,
    clientId
  );

  // Broadcast to online clients
  wsHandler.broadcastToChannel(channelId, message);

  // Persist to MessageBus so offline clients can reconcile on reconnect
  try {
    const unified = createMessage(channelId, senderId, senderName, content, 'ip', message.id);
    publish(unified);
  } catch (err) {
    console.warn('[Inject] messageBus publish failed:', (err as Error).message);
  }

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
  // F1.1: identity from JWT (was X-User-Id + X-User-Name).
  const auth = (req as AuthenticatedRequest).user!;
  const senderId = auth.id;
  const senderName = auth.displayName || auth.email || 'User';

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
// PLAN MANAGEMENT SYSTEM
// ════════════════════════════════════════════════════════════════════

// ENGAGEMENTS
apiRouter.post('/engagements', (req: Request, res: Response) => {
  const { name, description, clientName, location, intent } = req.body as CreateEngagementRequest;

  if (!name || !intent) {
    return res.status(400).json({ error: 'name and intent required' });
  }

  // F1.1: identity from JWT.
  const creatorId = (req as AuthenticatedRequest).user!.id;

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
      id: uuidv4(),
      planId,
      snapshotType: 'output',
      data: {
        id: planId,
        summary: {
          executiveSummary: 'Project completed successfully',
          workPerformed: ['Task 1', 'Task 2'],
          totalHours: 16,
          totalCost: 800
        }
      } as any,
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
      id: uuidv4(),
      planId,
      snapshotType: 'output',
      data: {
        id: planId,
        summary: {
          executiveSummary: 'Project completed successfully',
          workPerformed: ['Task 1', 'Task 2'],
          totalHours: 16,
          totalCost: 800
        }
      } as any,
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

const SERVER_START_MS = Date.now();

apiRouter.get('/health', (_req: Request, res: Response) => {
  res.json({
    status: 'ok',
    timestamp: Date.now(),
    uptimeSeconds: Math.floor((Date.now() - SERVER_START_MS) / 1000),
    channels: channelRegistry.list().length,
    onlineUsers: presenceManager.getOnline().length,
    relays: gatewayManager.getAll().length,
    wsClients: wsHandler.getClientCount(),
  });
});

// More detailed metrics for monitoring. Cheap to serve; no DB hits by default.
apiRouter.get('/metrics', async (_req: Request, res: Response) => {
  let dbOk = false;
  let messageCount: number | null = null;
  try {
    const { pg, isPgEnabled } = await import('./db');
    if (isPgEnabled() && pg) {
      const { rows } = await pg.query(
        `SELECT (SELECT COUNT(*)::int FROM message_bus_messages) AS msgs`
      );
      messageCount = rows[0].msgs;
      dbOk = true;
    }
  } catch {
    dbOk = false;
  }
  const mem = process.memoryUsage();
  res.json({
    status: 'ok',
    timestamp: Date.now(),
    uptimeSeconds: Math.floor((Date.now() - SERVER_START_MS) / 1000),
    db: { connected: dbOk, messageBusCount: messageCount },
    connections: {
      channels: channelRegistry.list().length,
      onlineUsers: presenceManager.getOnline().length,
      relays: gatewayManager.getAll().length,
      wsClients: wsHandler.getClientCount(),
    },
    memory: {
      rssMB: Math.round(mem.rss / 1024 / 1024),
      heapUsedMB: Math.round(mem.heapUsed / 1024 / 1024),
      heapTotalMB: Math.round(mem.heapTotal / 1024 / 1024),
    },
    process: {
      pid: process.pid,
      nodeVersion: process.version,
    },
  });
});

/**
 * Force refresh all WebSocket client subscriptions
 * Call this after creating channels when clients were already connected
 */
apiRouter.post('/refresh-subscriptions', async (_req: Request, res: Response) => {
  await wsHandler.refreshAllSubscriptions();
  res.json({
    success: true,
    message: 'Subscriptions refreshed for all connected clients',
    clientCount: wsHandler.getClientCount()
  });
});

// ════════════════════════════════════════════════════════════════
// INTENT AUTHORITY + INTENTS
// ════════════════════════════════════════════════════════════════

apiRouter.post('/intent-authority/validate-creation', (req: Request, res: Response) => {
  const { scopeStatement, parties } = req.body;
  const result = validateIntentCreation(scopeStatement, parties);
  res.json(result);
});

apiRouter.post('/intents', async (req: Request, res: Response) => {
  try {
    const { scopeStatement, parties, createdBy, intendedJobIds } = req.body;
    const result = await createIntent(scopeStatement, parties, createdBy, intendedJobIds);
    if ('error' in result) return res.status(400).json(result);
    res.status(201).json(result);
  } catch (err) {
    res.status(500).json({ error: 'Failed to create intent' });
  }
});

apiRouter.post('/intents/:intentId/versions/:versionId/propose', async (req: Request, res: Response) => {
  try {
    const result = await proposeIntent(req.params.versionId);
    if ('error' in result) return res.status(400).json(result);
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: 'Failed to propose intent' });
  }
});

apiRouter.post('/intents/:intentId/versions/:versionId/confirm', async (req: Request, res: Response) => {
  try {
    const { confirmerId } = req.body;
    const result = await confirmIntent(req.params.versionId, confirmerId);
    if ('error' in result) return res.status(400).json(result);
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: 'Failed to confirm intent' });
  }
});

apiRouter.post('/intents/:intentId/versions', async (req: Request, res: Response) => {
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

// ════════════════════════════════════════════════════════════════
// SYNTHESIS AUTHORITY + SYNTHESIZER
// ════════════════════════════════════════════════════════════════

apiRouter.post('/synthesis-authority/validate-inputs', (req: Request, res: Response) => {
  const { intentVersion, jobIds, timeEntryIds } = req.body;
  const result = validateSynthesisInputs(intentVersion, jobIds, timeEntryIds);
  res.json(result);
});

apiRouter.post('/synthesize', async (req: Request, res: Response) => {
  try {
    const { intentVersion, jobIds, timeEntryIds, approvedChatMessageIds } = req.body;
    const result = await synthesize(intentVersion, jobIds, timeEntryIds, approvedChatMessageIds);
    if ('error' in result) return res.status(400).json(result);
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: 'Synthesis failed' });
  }
});

apiRouter.get('/artifacts/:id', async (req: Request, res: Response) => {
  const artifact = await getArtifact(req.params.id);
  if (!artifact) return res.status(404).json({ error: 'Artifact not found' });
  res.json(artifact);
});

// ════════════════════════════════════════════════════════════════
// LEDGER AUTHORITY + LEDGER
// ════════════════════════════════════════════════════════════════

apiRouter.post('/ledger/seal', async (req: Request, res: Response) => {
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

apiRouter.post('/ledger/amend', async (req: Request, res: Response) => {
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

apiRouter.get('/ledger/:id', async (req: Request, res: Response) => {
  const entry = await getLedgerEntry(req.params.id);
  if (!entry) return res.status(404).json({ error: 'Ledger entry not found' });
  res.json(entry);
});

// ════════════════════════════════════════════════════════════════
// SMALL PROJECT FLOW
// ════════════════════════════════════════════════════════════════

apiRouter.post('/small-project/synthesize-and-generate-intent', async (req: Request, res: Response) => {
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

apiRouter.post('/small-project/confirm-and-seal', async (req: Request, res: Response) => {
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

// ════════════════════════════════════════════════════════════════
// PROPOSALS (contractor-facing, authenticated)
// ════════════════════════════════════════════════════════════════

/**
 * POST /api/proposals — contractor creates a new proposal
 */
apiRouter.post('/proposals', async (req: Request, res: Response) => {
  try {
    const result = await proposalService.createProposal(req.body);
    if (!result) return res.status(500).json({ error: 'Failed to create proposal' });
    res.status(201).json(result);
  } catch (err) {
    res.status(500).json({ error: 'Proposal creation failed' });
  }
});

/**
 * POST /api/proposals/:uuid/revoke — contractor revokes a proposal
 */
apiRouter.post('/proposals/:uuid/revoke', async (req: Request, res: Response) => {
  try {
    const ok = await proposalService.revoke(req.params.uuid);
    if (!ok) return res.status(404).json({ error: 'Proposal not found or could not be revoked' });
    res.json({ status: 'revoked' });
  } catch (err) {
    res.status(500).json({ error: 'Revoke failed' });
  }
});

// ════════════════════════════════════════════════════════════════
// PROPOSAL PUBLIC ROUTES (client-facing, no auth)
// Exported for mounting directly on the Express app at /p/:uuid
// ════════════════════════════════════════════════════════════════

export const proposalPublicRouter = Router();

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

  const tasksHtml = tasks.map(t => `<li>${esc(t)}</li>`).join('\n');
  const materialsHtml = materials.map(m =>
    `<tr><td>${esc(m.name)}</td><td>${esc(m.quantity)}</td><td>${esc(m.unit)}</td><td>$${fmt(m.unitCost)}</td><td>$${fmt(m.totalCost)}</td></tr>`
  ).join('\n');
  const equipmentHtml = equipment.map(e => `<li>${esc(e)}</li>`).join('\n');

  const isResponded = status === 'approved' || status === 'declined';
  const isExpired   = !!proposal.expired || status === 'expired';

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
    .replace(/\{\{#SHOW_RESPOND\}\}([\s\S]*?)\{\{\/SHOW_RESPOND\}\}/g,  (!isResponded && !isExpired) ? '$1' : '')
    .replace(/\{\{#SHOW_APPROVED\}\}([\s\S]*?)\{\{\/SHOW_APPROVED\}\}/g, status === 'approved' ? '$1' : '')
    .replace(/\{\{#SHOW_DECLINED\}\}([\s\S]*?)\{\{\/SHOW_DECLINED\}\}/g, status === 'declined' ? '$1' : '')
    .replace(/\{\{#SHOW_EXPIRED\}\}([\s\S]*?)\{\{\/SHOW_EXPIRED\}\}/g,  isExpired ? '$1' : '');

  return html;
}

/**
 * GET /p/:uuid — renders proposal HTML page (public, no auth)
 */
proposalPublicRouter.get('/:uuid', async (req: Request, res: Response) => {
  const proposal = await proposalService.getByUuid(req.params.uuid);
  if (!proposal) {
    return res.status(404).send('<h2 style="font-family:sans-serif;padding:40px">Proposal not found or has been revoked.</h2>');
  }
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.send(renderProposal(proposal));
});

/**
 * POST /p/:uuid/respond — client approves or requests changes (public, no auth)
 * TODO: Add rate limiting (e.g. express-rate-limit) in production to prevent abuse.
 * One response per IP per UUID is recommended.
 */
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

// ════════════════════════════════════════════════════════════════
// INVOICE LINKS
// ════════════════════════════════════════════════════════════════

/**
 * Create a shareable invoice link
 */
apiRouter.post('/invoice-links', async (req: Request, res: Response) => {
  try {
    const result = await invoiceLinkService.createInvoiceLink(req.body);
    if (!result) {
      return res.status(500).json({ error: 'Failed to create invoice link' });
    }
    res.status(201).json(result);
  } catch (err) {
    res.status(500).json({ error: 'Failed to create invoice link' });
  }
});

// ════════════════════════════════════════════════════════════════
// WAGES
// ════════════════════════════════════════════════════════════════

/**
 * Get BLS wage data by zip code and SOC code
 * GET /api/wages?zip=78701&soc=47-2111
 */
apiRouter.get('/wages', async (req: Request, res: Response) => {
  const { zip, soc } = req.query as { zip?: string; soc?: string };

  if (!zip || !soc) {
    return res.status(400).json({ error: 'zip and soc query parameters are required' });
  }

  try {
    const result = await wageDataService.getWageByZipAndTrade(zip, soc);
    if (!result) {
      return res.status(404).json({ error: 'No wage data found for this zip/trade combination' });
    }
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: 'Failed to retrieve wage data' });
  }
});
