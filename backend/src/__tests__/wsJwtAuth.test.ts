import http from 'http';
import WebSocket from 'ws';
import jwt from 'jsonwebtoken';
import { WebSocketServer } from 'ws';
import { pg, isPgEnabled } from '../db';
import { usersService } from '../usersService';
import { UserRole } from '../auth';
import { setupWsServer } from '../wsAuth';

const describeDb = isPgEnabled() ? describe : describe.skip;

const JWT_SECRET = process.env.JWT_SECRET || 'smith-net-dev-secret-change-in-production';

function makeAccessToken(payload: { userId: string; email: string; role: UserRole }, expiresIn: any = '7d'): string {
  return jwt.sign({ ...payload, type: 'access' }, JWT_SECRET, { expiresIn });
}

describeDb('WS JWT upgrade', () => {
  let server: http.Server;
  let wss: WebSocketServer;
  let port: number;
  let userId: string;
  let userEmail: string;

  beforeAll(async () => {
    const email = `ws-jwt-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'WS', UserRole.SOLO);
    userId = u.id;
    userEmail = email.toLowerCase();

    server = http.createServer();
    wss = new WebSocketServer({ noServer: true });
    setupWsServer(server, wss, (ws, identity) => {
      ws.send(JSON.stringify({ event: 'authed', identity }));
    });
    await new Promise<void>((r) => server.listen(0, () => r()));
    port = (server.address() as any).port;
  });

  afterAll(async () => {
    wss.close();
    server.close();
    await pg?.end();
  });

  function connect(opts: { cookie?: string }): Promise<{ event?: string; identity?: any; closeCode?: number }> {
    return new Promise((resolve, reject) => {
      const headers: Record<string, string> = {};
      if (opts.cookie) headers['Cookie'] = opts.cookie;
      const ws = new WebSocket(`ws://localhost:${port}`, { headers });
      const timer = setTimeout(() => reject(new Error('timeout')), 4000);
      ws.on('message', (data) => {
        try {
          const msg = JSON.parse(data.toString());
          clearTimeout(timer);
          ws.close();
          resolve(msg);
        } catch { /* ignore */ }
      });
      ws.on('unexpected-response', (_req, res) => {
        clearTimeout(timer);
        resolve({ closeCode: res.statusCode });
      });
      ws.on('error', () => { /* unexpected-response handler covers this */ });
    });
  }

  it('rejects upgrade without smithnet_access cookie', async () => {
    const result = await connect({});
    expect(result.closeCode).toBe(401);
  });

  it('rejects upgrade with invalid JWT', async () => {
    const result = await connect({ cookie: 'smithnet_access=not-a-jwt' });
    expect(result.closeCode).toBe(401);
  });

  it('rejects upgrade with expired JWT', async () => {
    const expired = makeAccessToken({ userId, email: userEmail, role: UserRole.SOLO }, '-1s');
    const result = await connect({ cookie: `smithnet_access=${expired}` });
    expect(result.closeCode).toBe(401);
  });

  it('rejects upgrade when only refresh cookie present', async () => {
    const refresh = jwt.sign(
      { userId, email: userEmail, role: UserRole.SOLO, type: 'refresh' },
      JWT_SECRET,
      { expiresIn: '30d' }
    );
    const result = await connect({ cookie: `smithnet_refresh=${refresh}` });
    expect(result.closeCode).toBe(401);
  });

  it('accepts upgrade with valid smithnet_access cookie and emits identity', async () => {
    const token = makeAccessToken({ userId, email: userEmail, role: UserRole.SOLO });
    const result = await connect({ cookie: `smithnet_access=${token}` });
    expect(result.event).toBe('authed');
    expect(result.identity?.userId).toBe(userId);
    expect(result.identity?.email).toBe(userEmail);
    expect(result.identity?.role).toBe(UserRole.SOLO);
  });

  it('rejects upgrade for revoked user (deleted after JWT issued)', async () => {
    const tempEmail = `ws-revoked-${Date.now()}@example.com`;
    const temp = await usersService.createUser(tempEmail, 'password123', 'Revoked', UserRole.SOLO);
    const token = makeAccessToken({ userId: temp.id, email: tempEmail.toLowerCase(), role: UserRole.SOLO });
    await pg!.query('DELETE FROM users WHERE id = $1', [temp.id]);

    const result = await connect({ cookie: `smithnet_access=${token}` });
    expect(result.closeCode).toBe(401);
  });
});
