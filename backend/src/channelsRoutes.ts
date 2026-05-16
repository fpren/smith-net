/**
 * Phase 4 Slice 3: extracted from api.ts.
 *
 * Channels CRUD + access control + messages + smart-send inject + /sync.
 * authenticateToken is applied by the parent (api.ts).
 */

import { Router, Request, Response } from 'express';
import { channelRegistry } from './channelRegistry';
import { messageStore } from './messageStore';
import { gatewayManager } from './gatewayManager';
import { wsHandler } from './wsHandler';
import { createMessage, publish } from './messageBus';
import { AuthenticatedRequest } from './auth';
import {
  CreateChannelRequest,
  InjectMessageRequest,
  AccessResponsePayload,
  UpdateChannelAccessPayload,
  UpdateChannelVisibilityPayload
} from './types';

export const channelsRouter = Router();

// ════════════════════════════════════════════════════════════════════
// CHANNELS
// ════════════════════════════════════════════════════════════════════

channelsRouter.post('/channels', async (req: Request, res: Response) => {
  const { name, type, memberIds, visibility, requiresApproval } = req.body as CreateChannelRequest;

  if (!name || !type) {
    return res.status(400).json({ error: 'name and type required' });
  }

  const creatorId = (req as AuthenticatedRequest).user!.id;

  const channel = await channelRegistry.create(
    name,
    type,
    creatorId,
    memberIds,
    visibility || 'public',
    requiresApproval || false
  );

  wsHandler.broadcastChannelEvent('channel_created', channel);
  // Pick up members already connected so they receive DMs/private channels
  // without needing to reconnect.
  await wsHandler.refreshAllSubscriptions();

  res.status(201).json(channel);
});

channelsRouter.get('/channels', (req: Request, res: Response) => {
  // Always scope to authenticated user.
  const userId = (req as AuthenticatedRequest).user!.id;
  const channels = channelRegistry.listForUser(userId);
  res.json(channels);
});

channelsRouter.get('/channels/:id', (req: Request, res: Response) => {
  const channel = channelRegistry.get(req.params.id);
  if (!channel) {
    return res.status(404).json({ error: 'Channel not found' });
  }
  res.json(channel);
});

channelsRouter.patch('/channels/:id', async (req: Request, res: Response) => {
  const channel = await channelRegistry.update(req.params.id, req.body);
  if (!channel) {
    return res.status(404).json({ error: 'Channel not found' });
  }
  wsHandler.broadcastChannelEvent('channel_updated', channel);
  res.json(channel);
});

channelsRouter.delete('/channels/:id', async (req: Request, res: Response) => {
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

channelsRouter.post('/channels/:id/access/request', async (req: Request, res: Response) => {
  const channelId = req.params.id;
  const userId = (req as AuthenticatedRequest).user!.id;

  const success = await channelRegistry.requestAccess(channelId, userId);
  if (!success) {
    return res.status(400).json({ error: 'Cannot request access to this channel' });
  }

  const channel = channelRegistry.get(channelId);
  wsHandler.broadcastChannelEvent('channel_updated', channel);

  res.json({ status: 'pending', message: 'Access request submitted' });
});

channelsRouter.post('/channels/:id/access/respond', async (req: Request, res: Response) => {
  const channelId = req.params.id;
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

channelsRouter.post('/channels/:id/access/user', async (req: Request, res: Response) => {
  const channelId = req.params.id;
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

channelsRouter.post('/channels/:id/visibility', async (req: Request, res: Response) => {
  const channelId = req.params.id;
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

channelsRouter.get('/channels/:id/access/status', (req: Request, res: Response) => {
  const channelId = req.params.id;
  const userId = (req as AuthenticatedRequest).user!.id;
  const status = channelRegistry.getAccessStatus(channelId, userId);
  res.json({ status });
});

channelsRouter.get('/channels/:id/access/pending', (req: Request, res: Response) => {
  const channelId = req.params.id;
  const managerId = (req as AuthenticatedRequest).user!.id;

  const channel = channelRegistry.get(channelId);
  if (!channel) {
    return res.status(404).json({ error: 'Channel not found' });
  }
  if (channel.creatorId !== managerId) {
    return res.status(403).json({ error: 'Not authorized' });
  }
  res.json({ pendingRequests: channel.pendingRequests });
});

// ════════════════════════════════════════════════════════════════════
// MESSAGES
// ════════════════════════════════════════════════════════════════════

channelsRouter.get('/channels/:id/messages', (req: Request, res: Response) => {
  const { id } = req.params;
  const limit = parseInt(req.query.limit as string) || 100;
  const before = req.query.before ? parseInt(req.query.before as string) : undefined;

  const messages = messageStore.getForChannel(id, limit, before);
  res.json(messages);
});

channelsRouter.delete('/channels/:id/messages', (req: Request, res: Response) => {
  const { id } = req.params;

  messageStore.clearChannel(id);
  wsHandler.broadcastChannelEvent('channel_cleared', { channelId: id });

  console.log(`[API] Cleared messages for channel: ${id}`);
  res.status(204).send();
});

// Delete a single message ("Delete for everyone"). Only the message sender or
// dashboard admin can delete.
channelsRouter.delete('/messages/:messageId', (req: Request, res: Response) => {
  const { messageId } = req.params;
  const requesterId = (req as AuthenticatedRequest).user!.id;

  const deleted = messageStore.deleteMessage(messageId, requesterId);
  if (!deleted) {
    return res.status(404).json({ error: 'Message not found or unauthorized' });
  }

  wsHandler.broadcastChannelEvent('message_deleted', { messageId });

  console.log(`[API] Deleted message ${messageId} by ${requesterId}`);
  res.status(204).send();
});

// SMART SEND — Unified message endpoint.
// Always stores + broadcasts to online clients. Auto-injects to mesh if
// a gateway relay is connected so mesh-only users (underground) receive it.
channelsRouter.post('/messages/inject', (req: Request, res: Response) => {
  let { channelId, content, meshOnly, id: clientId } = req.body as InjectMessageRequest & { meshOnly?: boolean; id?: string };
  const auth = (req as AuthenticatedRequest).user!;
  const senderId = auth.id;
  const senderName = auth.displayName || auth.email || 'User';

  if (!channelId || !content) {
    return res.status(400).json({ error: 'channelId and content required' });
  }

  // Phones send "general" by name; resolve to UUID.
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

  const hasRelay = gatewayManager.hasConnectedRelay();
  const shouldInjectToMesh = hasRelay && !meshOnly;

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

  wsHandler.broadcastToChannel(channelId, message);

  // Persist to MessageBus so offline clients can reconcile on reconnect.
  try {
    const unified = createMessage(channelId, senderId, senderName, content, 'ip', message.id);
    publish(unified);
  } catch (err) {
    console.warn('[Inject] messageBus publish failed:', (err as Error).message);
  }

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

// Get sync info for a reconnecting client. Returns channel clear timestamps
// so client can purge old messages.
channelsRouter.get('/sync', (_req: Request, res: Response) => {
  res.json({
    channelClearedAt: messageStore.getAllClearTimestamps(),
    serverTime: Date.now()
  });
});
