/**
 * Phase 4 Slice 3: extracted from api.ts.
 *
 * Channels CRUD + access control + messages + smart-send inject + /sync.
 * authenticateToken is applied by the parent (api.ts).
 */

import { Router, Request, Response, NextFunction } from 'express';
import { channelRegistry } from './channelRegistry';
import { notificationService } from './notificationService';
import { messageStore } from './messageStore';
import { gatewayManager } from './gatewayManager';
import { wsHandler } from './wsHandler';
import { createMessage, publish } from './messageBus';
import { AuthenticatedRequest } from './auth';
import { pg, isPgEnabled } from './db';
import {
  CreateChannelRequest,
  InjectMessageRequest,
  AccessResponsePayload,
  UpdateChannelAccessPayload,
  UpdateChannelVisibilityPayload
} from './types';

export const channelsRouter = Router();

/**
 * Channel access guard for message read/write/clear. Mirrors the visibility
 * rules in channelRegistry.listForUser:
 *   - dm: members only (works cross-org — that's the one sanctioned exception)
 *   - everything else: same org AND canUserAccess
 * Returns the channel on success; responds 404 and returns null otherwise.
 * 404 (not 403) so an outsider can't probe which channel ids exist.
 */
function requireChannelAccess(req: Request, res: Response, channelId: string) {
  const channel = channelRegistry.get(channelId);
  const auth = (req as AuthenticatedRequest).user!;
  if (!channel) {
    res.status(404).json({ error: 'Channel not found' });
    return null;
  }
  if (channel.type === 'dm') {
    if (!channel.memberIds.includes(auth.id)) {
      res.status(404).json({ error: 'Channel not found' });
      return null;
    }
    return channel;
  }
  if (channel.organizationId && auth.organizationId && channel.organizationId !== auth.organizationId) {
    res.status(404).json({ error: 'Channel not found' });
    return null;
  }
  if (!channelRegistry.canUserAccess(channel, auth.id)) {
    res.status(404).json({ error: 'Channel not found' });
    return null;
  }
  return channel;
}

// ════════════════════════════════════════════════════════════════════
// CHANNELS
// ════════════════════════════════════════════════════════════════════

channelsRouter.post('/channels', async (req: Request, res: Response) => {
  const { name, type, memberIds, visibility, requiresApproval } = req.body as CreateChannelRequest;

  if (!name || !type) {
    return res.status(400).json({ error: 'name and type required' });
  }

  const auth = (req as AuthenticatedRequest).user!;
  const organizationId = auth.organizationId;
  if (!organizationId) {
    return res.status(401).json({ error: 'user missing organization_id' });
  }

  const channel = await channelRegistry.create(
    name,
    type,
    auth.id,
    organizationId,
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

// POST /api/dm { publicId } — create-or-return the direct message between the
// caller and the owner of that 8-char public id. Intentionally cross-org: this
// is how two independent solos (each an org-of-one) start talking. The channel
// is type 'dm' + private with exactly two memberIds; the DM exceptions in
// channelRegistry.listForUser and wsHandler.shouldBroadcastTo scope the
// cross-org visibility to those two members only.
channelsRouter.post('/dm', async (req: Request, res: Response) => {
  const auth = (req as AuthenticatedRequest).user!;
  if (!auth.organizationId) {
    return res.status(401).json({ error: 'user missing organization_id' });
  }
  const publicId = String((req.body as { publicId?: string })?.publicId ?? '')
    .replace(/-/g, '')
    .toUpperCase();
  if (!/^[A-Z0-9]{8}$/.test(publicId)) {
    return res.status(400).json({ error: 'publicId must be 8 alphanumeric characters' });
  }
  if (!isPgEnabled() || !pg) {
    return res.status(503).json({ error: 'directory unavailable' });
  }

  const { rows } = await pg.query(
    `SELECT id, display_name FROM profiles WHERE public_id = $1 LIMIT 1`,
    [publicId]
  );
  const other = rows[0] as { id: string; display_name: string | null } | undefined;
  if (!other) {
    return res.status(404).json({ error: 'No user with that public id' });
  }
  if (other.id === auth.id) {
    return res.status(400).json({ error: 'cannot DM yourself' });
  }

  // One DM per unordered pair: replays and "DM again" return the same channel.
  const existing = channelRegistry
    .list()
    .find(
      (c) =>
        c.type === 'dm' &&
        c.memberIds.length === 2 &&
        c.memberIds.includes(auth.id) &&
        c.memberIds.includes(other.id)
    );
  if (existing) {
    return res.json(existing);
  }

  const name = `${auth.displayName || 'dm'} <> ${other.display_name || publicId}`;
  const channel = await channelRegistry.create(
    name,
    'dm',
    auth.id,
    auth.organizationId,
    [auth.id, other.id],
    'private',
    false
  );

  wsHandler.broadcastChannelEvent('channel_created', channel);
  await wsHandler.refreshAllSubscriptions();

  res.status(201).json(channel);
});

channelsRouter.get('/channels', (req: Request, res: Response) => {
  // Always scope to authenticated user AND their org (migration 015).
  const auth = (req as AuthenticatedRequest).user!;
  if (!auth.organizationId) {
    return res.status(401).json({ error: 'user missing organization_id' });
  }
  const channels = channelRegistry.listForUser(auth.id, auth.organizationId);
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
  if (!requireChannelAccess(req, res, id)) return;
  const limit = parseInt(req.query.limit as string) || 100;
  const before = req.query.before ? parseInt(req.query.before as string) : undefined;

  const messages = messageStore.getForChannel(id, limit, before);
  res.json(messages);
});

channelsRouter.delete('/channels/:id/messages', (req: Request, res: Response) => {
  const { id } = req.params;
  if (!requireChannelAccess(req, res, id)) return;

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
channelsRouter.post('/messages/inject', async (req: Request, res: Response, next: NextFunction) => {
  // Wrapped in try/catch -> next(err): this handler is async (it awaits the N-1
  // notification producer), and Express 4 does NOT forward a rejected promise
  // from an async handler to the error middleware -- without this, a synchronous
  // throw in the body (e.g. createMessage's vclock path) would become an
  // unhandled rejection and hang the request instead of returning a clean 500.
  try {
    let { channelId, content, meshOnly, id: clientId, media } = req.body as InjectMessageRequest & { meshOnly?: boolean; id?: string };
    const auth = (req as AuthenticatedRequest).user!;
    const senderId = auth.id;
    const senderName = auth.displayName || auth.email || 'User';

    if (!channelId || (!content && !media)) {
      return res.status(400).json({ error: 'channelId and content or media required' });
    }
    // Attachment-only messages (no caption) are legitimate -- media satisfies
    // the content requirement above, but downstream code below still expects
    // a string, so normalize the missing/empty case once here.
    content = content ?? '';

    // Media attachments (Design System v2 Plan 3): url must be a local /media/
    // upload path or an absolute http(s) URL -- never javascript:, data:, etc.
    if (media) {
      const validType = ['image', 'voice', 'video', 'file'].includes(media.type);
      const validUrl = typeof media.url === 'string' && (media.url.startsWith('/media/') || /^https?:\/\//i.test(media.url));
      if (!validType || !validUrl) {
        return res.status(400).json({ error: 'invalid media' });
      }
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

    // Sender must actually have access to the target channel (member of the
    // DM, or same-org + visibility rules) — otherwise any authenticated user
    // could write into any channel by UUID.
    if (!requireChannelAccess(req, res, channelId)) return;

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
      clientId,
      media
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

    // N-1 producer: notify other channel members (best-effort; never fail the
    // send). Awaited in-request per CLAUDE.md Rule 2 (not fire-and-forget). Open
    // channels (memberIds empty) produce none -- we never fan out to a whole org.
    const ch = channelRegistry.get(channelId);
    if (ch) {
      const mediaLabel = media ? `[${media.type}]` : '';
      const preview = content
        ? (content.length > 80 ? `${content.slice(0, 77)}...` : content)
        : mediaLabel;
      const recipients = ch.memberIds.filter((m) => m !== senderId);
      await Promise.all(
        recipients.map((m) =>
          notificationService
            .create({
              userId: m,
              type: 'message',
              title: `New message in ${ch.name}`,
              body: preview,
              link: '/console/comm',
              actorId: senderId,
            })
            .catch((e) => console.warn('[Inject] notify failed for', m, (e as Error).message))
        )
      );
    }

    res.status(201).json({
      ...message,
      meshInjected: injectedToMesh > 0,
      relayCount: injectedToMesh
    });
  } catch (err) {
    next(err);
  }
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
