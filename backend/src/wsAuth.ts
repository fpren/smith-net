/**
 * Phase 2 Slice 3: WebSocket upgrade auth. Validates the smithnet_access
 * JWT cookie before the WS handshake completes. On success, attaches the
 * identity to the resulting ws as a property. On failure, writes a 401
 * response and destroys the socket — no WS frames flow.
 *
 * Closes audit weak point #4 (client-supplied userId on WS auth).
 */

import http from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import jwt from 'jsonwebtoken';
import { requestLogger } from './log';
import { usersService } from './usersService';
import { UserRole } from './auth';

export interface WsIdentity {
  userId: string;
  userName: string;
  email: string;
  role: UserRole;
}

const JWT_SECRET = process.env.JWT_SECRET || 'smith-net-dev-secret-change-in-production';
const ACCESS_COOKIE_NAME = 'smithnet_access';

function parseCookieHeader(header: string | undefined): Record<string, string> {
  const out: Record<string, string> = {};
  if (!header) return out;
  for (const pair of header.split(';')) {
    const trimmed = pair.trim();
    const eq = trimmed.indexOf('=');
    if (eq < 0) continue;
    const k = trimmed.slice(0, eq).trim();
    const v = trimmed.slice(eq + 1).trim();
    if (k && v) out[k] = decodeURIComponent(v);
  }
  return out;
}

function deny(socket: any, code: number, reason: string): void {
  try {
    socket.write(`HTTP/1.1 ${code} ${reason}\r\nConnection: close\r\n\r\n`);
  } catch { /* socket may already be closed */ }
  socket.destroy();
}

interface JwtAccessPayload {
  userId: string;
  email: string;
  role: UserRole;
  type: 'access' | 'refresh';
  iat?: number;
  exp?: number;
}

async function authorize(req: http.IncomingMessage): Promise<WsIdentity | null> {
  const cookies = parseCookieHeader(req.headers.cookie);
  const token = cookies[ACCESS_COOKIE_NAME];
  if (!token) return null;

  let payload: JwtAccessPayload;
  try {
    payload = jwt.verify(token, JWT_SECRET) as JwtAccessPayload;
  } catch {
    return null;
  }

  if (payload.type !== 'access') return null;
  if (!payload.userId) return null;

  const user = await usersService.getUserById(payload.userId);
  if (!user || !user.isActive) return null;

  return {
    userId: user.id,
    userName: user.displayName,
    email: user.email,
    role: user.role,
  };
}

/**
 * Wires the upgrade handler. Call once at boot:
 *
 *   const wss = new WebSocketServer({ noServer: true });
 *   setupWsServer(server, wss, (ws, identity) => wsHandler.onConnection(ws, identity));
 */
export function setupWsServer(
  server: http.Server,
  wss: WebSocketServer,
  onConnection: (ws: WebSocket, identity: WsIdentity) => void
): void {
  server.on('upgrade', (req, socket, head) => {
    authorize(req)
      .then((identity) => {
        if (!identity) {
          requestLogger().warn(
            { event: 'ws_auth_denied', reason: 'invalid_or_missing_jwt', ip: req.socket.remoteAddress },
            'ws auth denied'
          );
          deny(socket, 401, 'Unauthorized');
          return;
        }
        wss.handleUpgrade(req, socket as any, head, (ws) => {
          (ws as any).identity = identity;
          requestLogger().info(
            { event: 'ws_upgraded', userId: identity.userId, role: identity.role },
            'ws upgraded'
          );
          onConnection(ws, identity);
        });
      })
      .catch((err) => {
        requestLogger().error({ event: 'ws_auth_error', err }, 'ws auth error');
        deny(socket, 500, 'Internal Error');
      });
  });
}
