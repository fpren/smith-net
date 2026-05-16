/**
 * Phase 4 Slice 3: extracted from api.ts.
 *
 * Operational/mesh endpoints: presence + gateway + /health + /metrics
 * + /refresh-subscriptions. authenticateToken is applied by the parent.
 *
 * Note: this is distinct from /api/admin/health (Phase 4 Slice 1's
 * worker/queue rollup, in healthRoutes.ts). This file's /health is the
 * lightweight liveness/uptime/connection-count endpoint used by the
 * desktop portal + Android.
 */

import { Router, Request, Response } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { presenceManager } from './presenceManager';
import { gatewayManager } from './gatewayManager';
import { wsHandler } from './wsHandler';
import { channelRegistry } from './channelRegistry';
import { messageStore } from './messageStore';
import { AuthenticatedRequest } from './auth';
import { Message } from './types';

export const presenceGatewayRouter = Router();

const SERVER_START_MS = Date.now();

// ════════════════════════════════════════════════════════════════════
// PRESENCE
// ════════════════════════════════════════════════════════════════════

// Get all presence data (for Android app polling).
presenceGatewayRouter.get('/presence', (_req: Request, res: Response) => {
  const onlineUsers = presenceManager.getOnline();
  res.json({
    users: onlineUsers.map((p) => ({
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

// Send presence heartbeat (for Android app).
presenceGatewayRouter.post('/presence', (req: Request, res: Response) => {
  const { userId, userName } = req.body;

  if (!userId || !userName) {
    return res.status(400).json({ error: 'userId and userName required' });
  }

  const presence = presenceManager.update(userId, userName, 'online', 'mobile');
  console.log(`[Presence] Heartbeat from ${userName} (${userId})`);
  wsHandler.broadcastPresence(presence);

  res.status(200).json({
    success: true,
    presence,
    onlineCount: presenceManager.getOnline().length
  });
});

// Get online users only.
presenceGatewayRouter.get('/presence/online', (_req: Request, res: Response) => {
  res.json(presenceManager.getOnline());
});

// ════════════════════════════════════════════════════════════════════
// GATEWAY
// ════════════════════════════════════════════════════════════════════

presenceGatewayRouter.get('/gateway/status', (_req: Request, res: Response) => {
  const relays = gatewayManager.getAll();
  const hasRelay = relays.length > 0;

  res.json({
    mode: hasRelay ? 'gateway' : 'online',
    relayConnected: hasRelay,
    relays,
    lastMeshActivity: relays.length > 0
      ? Math.max(...relays.map((r) => r.lastActivity))
      : undefined,
  });
});

presenceGatewayRouter.get('/gateway/relays', (_req: Request, res: Response) => {
  res.json(gatewayManager.getAll());
});

presenceGatewayRouter.delete('/gateway/relays/:relayId', async (req: Request, res: Response) => {
  const { relayId } = req.params;

  const relay = gatewayManager.get(relayId);
  if (!relay) {
    return res.status(404).json({ error: 'Relay not found' });
  }

  await gatewayManager.forceDisconnect(relayId);

  console.log(`[API] Admin force-disconnected relay: ${relay.name} (${relayId})`);
  res.json({ success: true, disconnected: relay.name });
});

presenceGatewayRouter.post('/gateway/inject', (req: Request, res: Response) => {
  const { channelId, content } = req.body;
  const auth = (req as AuthenticatedRequest).user!;
  const senderId = auth.id;
  const senderName = auth.displayName || auth.email || 'User';

  if (!channelId || !content) {
    return res.status(400).json({ error: 'channelId and content required' });
  }

  if (!gatewayManager.hasConnectedRelay()) {
    return res.status(503).json({ error: 'No gateway relay connected' });
  }

  const message: Message = {
    id: uuidv4(),
    channelId,
    senderId,
    senderName,
    content,
    timestamp: Date.now(),
    origin: 'gateway',
  };

  messageStore.add(channelId, senderId, senderName, content, 'gateway');
  wsHandler.broadcastToChannel(channelId, message);
  const injected = gatewayManager.broadcastToRelays(message);

  res.status(201).json({
    message,
    injectedToRelays: injected
  });
});

// ════════════════════════════════════════════════════════════════════
// HEALTH + METRICS (lightweight; see /api/admin/health for queue/worker rollup)
// ════════════════════════════════════════════════════════════════════

presenceGatewayRouter.get('/health', (_req: Request, res: Response) => {
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
presenceGatewayRouter.get('/metrics', async (_req: Request, res: Response) => {
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

// Force refresh all WebSocket client subscriptions.
// Call after creating channels when clients were already connected.
presenceGatewayRouter.post('/refresh-subscriptions', async (_req: Request, res: Response) => {
  await wsHandler.refreshAllSubscriptions();
  res.json({
    success: true,
    message: 'Subscriptions refreshed for all connected clients',
    clientCount: wsHandler.getClientCount()
  });
});
