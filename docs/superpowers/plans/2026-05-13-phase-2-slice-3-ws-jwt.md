# Phase 2 Slice 3 — WS JWT Hard Cutover

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the client-supplied `userId` in the WS `type:'auth'` message with JWT cookie validation on the HTTP upgrade. The connection is rejected (401) before WebSocket frames flow if the cookie is missing or invalid. Closes audit weak point #4.

**Architecture:** Switch from `new WebSocketServer({ server })` to `new WebSocketServer({ noServer: true })` plus a custom `server.on('upgrade')` handler. The upgrade handler parses the `Cookie` header for `smithnet_access`, verifies the JWT with `jsonwebtoken`, looks up the user via `usersService`, and either calls `wss.handleUpgrade(...)` (attaching identity to the ws as a property) or writes a 401 and destroys the socket. The `wsHandler.initialize()` connection handler reads the identity from the ws property (set by the upgrade handler) and runs the post-auth setup (channel subscriptions, presence update) immediately — no more `type:'auth'` message round-trip. The `case 'auth':` branch is deleted from `handleMessage`. `handleAuth` is deleted. Clients that send `type:'auth'` get a soft warning back, but the connection already has identity from JWT so the message is a no-op.

**Tech Stack:** Node + `ws` (WebSocket library) + `jsonwebtoken` + `cookie` parser + Jest

**Prerequisites:**
- Slices 1 and 2 shipped (tags `phase-2-slice-1`, `phase-2-slice-2`). 124/124 tests passing.
- `usersService` from Slice 1 is the live user store.
- `pino` + request-context middleware from Slice 2 are wired.
- `DATABASE_URL` is set and `psql` is on PATH.
- Working branch is `feat/relay-hetzner-postgres`.

**Reference docs:**
- Spec: `docs/superpowers/specs/2026-05-13-phase-2-persistence-design.md` (Slice 3 section)
- Audit: `docs/smith-net-architecture-audit.md` (weak point #4)
- CLAUDE.md: no fire-and-forget — the upgrade handler awaits JWT verification + user lookup before accepting

**Known risk:** The Android client may currently connect to the WS without sending a cookie. After this slice ships, that client gets 401 on upgrade. The Android-side fix is a one-line OkHttp config to attach the `smithnet_access` cookie to the WS upgrade request. This is intentionally out of scope for Slice 3 — back-end is shippable on its own; Android catches up next sprint.

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `backend/src/server.ts` | Modify | Replace `new WebSocketServer({ server })` with `noServer:true` + custom upgrade handler |
| `backend/src/wsAuth.ts` | Create | The upgrade handler: cookie parse + JWT verify + user lookup + handleUpgrade or 401 |
| `backend/src/wsHandler.ts` | Modify | `connection` event reads identity from ws property; old `handleAuth` deleted; `case 'auth':` deleted |
| `backend/src/__tests__/wsJwtAuth.test.ts` | Create | 4 cases: valid cookie / missing / expired / refresh-only |
| `docs/smith-net-architecture-audit.md` | Modify (Task 6) | Annotate weak point #4 `[closed in slice 3, commit <SHA>]` |

---

## Task 1 — wsAuth.ts: JWT-validated upgrade handler

**Files:**
- Create: `backend/src/wsAuth.ts`
- Create: `backend/src/__tests__/wsJwtAuth.test.ts`

### Step 1: Write the failing tests

Create `backend/src/__tests__/wsJwtAuth.test.ts`:

```typescript
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

function makeAccessToken(payload: { userId: string; email: string; role: UserRole }, expiresIn = '7d'): string {
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
    // Make a JWT for a user, then delete that user. The JWT is technically
    // valid signature-wise but the lookup fails.
    const tempEmail = `ws-revoked-${Date.now()}@example.com`;
    const temp = await usersService.createUser(tempEmail, 'password123', 'Revoked', UserRole.SOLO);
    const token = makeAccessToken({ userId: temp.id, email: tempEmail.toLowerCase(), role: UserRole.SOLO });
    await pg!.query('DELETE FROM users WHERE id = $1', [temp.id]);

    const result = await connect({ cookie: `smithnet_access=${token}` });
    expect(result.closeCode).toBe(401);
  });
});
```

### Step 2: Run the test to verify it fails

```bash
export PATH=/opt/homebrew/opt/postgresql@16/bin:$PATH
export DATABASE_URL=postgres://fegensprenelon@localhost:5432/smithnet
cd backend && npx jest src/__tests__/wsJwtAuth.test.ts
```

Expected: FAIL with "Cannot find module '../wsAuth'".

### Step 3: Implement wsAuth.ts

Create `backend/src/wsAuth.ts`:

```typescript
/**
 * Phase 2 Slice 3: WebSocket upgrade auth. Validates the smithnet_access
 * JWT cookie before the WS handshake completes. On success, attaches the
 * identity to the resulting ws as a non-enumerable property. On failure,
 * writes a 401 response and destroys the socket — no WS frames flow.
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
```

### Step 4: Verify tests pass

```bash
cd backend && npx jest src/__tests__/wsJwtAuth.test.ts
```

Expected: PASS — 6 tests pass.

### Step 5: Commit

```bash
git add backend/src/wsAuth.ts backend/src/__tests__/wsJwtAuth.test.ts
git commit -m "feat(ws): JWT-validated upgrade handler (audit weak point #4)"
```

---

## Task 2 — Wire wsAuth into server.ts

**Files:**
- Modify: `backend/src/server.ts` (change WS server construction + upgrade wiring)
- Modify: `backend/src/wsHandler.ts` (add `onConnection(ws, identity)` method as the connection entry point)

### Step 1: Add onConnection method to wsHandler.ts

In `backend/src/wsHandler.ts`, add a new public method on the `WSHandler` class. This is the new entry point — called by `wsAuth.setupWsServer` after a successful upgrade. It does what the current `handleAuth` did, minus the trust-the-client step.

Add ABOVE the existing `private handleAuth(...)` method:

```typescript
/**
 * Phase 2 Slice 3 entry point. Called by wsAuth.setupWsServer after JWT
 * validation. Identity is trusted (comes from the validated JWT). Sets up
 * the post-connect state that handleAuth used to do.
 */
onConnection(ws: WebSocket, identity: { userId: string; userName: string; email: string; role: string }): void {
  const { userId, userName } = identity;

  const client: AuthenticatedClient = {
    ws,
    userId,
    userName,
    subscribedChannels: new Set(),
    isRelay: false,
    relayId: undefined,
    channelUnsubs: new Map(),
  };
  this.clients.set(ws, client);

  presenceManager.update(userId, userName, 'online', 'online');

  const channelIds = channelRegistry.subscribeUserToChannels(userId);
  for (const channelId of channelIds) {
    client.subscribedChannels.add(channelId);
    if (!client.channelUnsubs.has(channelId)) {
      const unsub = subscribe(channelId, (unifiedMsg) => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'message', data: unifiedMsg }));
        }
      });
      client.channelUnsubs.set(channelId, unsub);
    }
  }

  const channels = channelRegistry.listForUser(userId);
  this.send(ws, {
    type: 'auth_ok',
    payload: {
      userId,
      channels: channels.map(c => ({ id: c.id, name: c.name, type: c.type })),
    },
    timestamp: Date.now(),
  });
  this.broadcastPresence();

  // Wire up message + close + error handlers (previously done in the
  // wss.on('connection') callback in initialize()).
  ws.on('message', (data) => {
    try {
      const msg: WSMessage = JSON.parse(data.toString());
      this.handleMessage(ws, msg);
    } catch (e) {
      this.sendError(ws, 'Invalid message format');
    }
  });
  ws.on('close', () => { this.handleDisconnect(ws); });
  ws.on('error', (err) => {
    requestLogger().error({ event: 'ws_error', err }, 'ws error');
    this.handleDisconnect(ws);
  });

  requestLogger().info({ event: 'ws_authenticated', userId, userName, channelCount: channelIds.length }, 'ws authenticated');
}
```

Do **not** delete `handleAuth` or the `case 'auth':` branch yet — that's Task 3. The two paths coexist temporarily so the build stays green at this checkpoint.

### Step 2: Update wsHandler.initialize() to skip the on('connection') hookup

The new entry point is `onConnection(ws, identity)`, called by `wsAuth.setupWsServer`. The old `wss.on('connection')` handler did its own setup including hooking message/close/error handlers — that's now inside `onConnection`. The old `initialize()` is mostly redundant after this slice.

Replace the body of `initialize(wss)` in `backend/src/wsHandler.ts`:

```typescript
initialize(wss: WebSocketServer): void {
  this.wss = wss;

  // Keep presence alive for all connected clients every 30 seconds
  this.presenceInterval = setInterval(() => {
    this.refreshAllPresence();
  }, 30_000);

  // Subscribe to gateway messages
  gatewayManager.onMessage((message, _relayId) => {
    this.broadcastToChannel(message.channelId, message);
  });

  requestLogger().info({ event: 'ws_handler_initialized' }, 'ws handler initialized');
}
```

The `wss.on('connection', ...)` is removed — `wsAuth.setupWsServer` controls the connection lifecycle now.

### Step 3: Update server.ts to wire wsAuth

In `backend/src/server.ts`, find the WS server setup (around line 293):

```typescript
// Create WebSocket server
const wss = new WebSocketServer({ server });

// Initialize WebSocket handler
wsHandler.initialize(wss);
```

Replace with:

```typescript
import { setupWsServer } from './wsAuth';

// Create WebSocket server in noServer mode — wsAuth performs JWT validation
// on the HTTP upgrade and only then calls handleUpgrade.
const wss = new WebSocketServer({ noServer: true });
wsHandler.initialize(wss);
setupWsServer(server, wss, (ws, identity) => wsHandler.onConnection(ws, identity));
```

Put the `import { setupWsServer } from './wsAuth';` near the other imports at the top.

### Step 4: Run the full test suite

```bash
cd backend && npx jest
```

Expected: 130+ tests passing (124 prior + 6 new wsJwtAuth tests). 0 failures.

If any existing test fails due to the WS change, it's likely a test that was connecting WS without a cookie. Update such tests to either:
- (a) Skip — these tests are about a different concern and don't exercise WS auth
- (b) Mint a valid JWT and pass it as a cookie header

### Step 5: Commit

```bash
git add backend/src/server.ts backend/src/wsHandler.ts
git commit -m "feat(ws): wire JWT upgrade into server.ts + wsHandler.onConnection entry point"
```

---

## Task 3 — Delete handleAuth + case 'auth' from wsHandler

**Files:**
- Modify: `backend/src/wsHandler.ts`

### Step 1: Delete the `case 'auth':` branch

In `backend/src/wsHandler.ts`, find `handleMessage` and remove the `case 'auth':` block. Replace with nothing (the message type is no longer accepted).

Before:

```typescript
switch (msg.type) {
  case 'auth':
    this.handleAuth(ws, msg.payload as { userId: string; userName: string; isRelay?: boolean; relayId?: string });
    break;

  case 'message':
    ...
```

After:

```typescript
switch (msg.type) {
  case 'message':
    ...
```

If any clients still send `type:'auth'`, the `default:` arm hits and `sendError(ws, 'Unknown message type: auth')` fires. The connection itself is already authenticated via JWT, so this is a noisy-but-harmless message.

### Step 2: Delete the handleAuth method

Delete the entire `private handleAuth(...)` method (was around line 130-185).

### Step 3: Update gateway-connect to validate the role from JWT identity

The current `handleGatewayConnect` doesn't check any permission — anyone with an authenticated WS connection can register as a gateway relay. After Slice 3, the JWT carries `role`. Add a role check.

In `handleGatewayConnect`, near the top after extracting client:

```typescript
private handleGatewayConnect(
  ws: WebSocket,
  payload: { relayId: string; name: string; capabilities: string[] }
): void {
  const client = this.clients.get(ws);
  if (!client) {
    this.sendError(ws, 'Not authenticated');
    return;
  }

  // Phase 2 Slice 3: only admin/foreman/system can register as a gateway relay.
  // Identity comes from the validated JWT on the upgrade, so this check is
  // server-authoritative.
  const ident = (ws as any).identity as { role: string } | undefined;
  if (!ident || (ident.role !== 'admin' && ident.role !== 'foreman' && ident.role !== 'system')) {
    this.sendError(ws, 'Gateway registration requires admin/foreman role');
    return;
  }

  const { relayId, name, capabilities } = payload;
  // ...rest of existing handleGatewayConnect body
}
```

If the existing `handleGatewayConnect` has a different shape, integrate the check at the equivalent point. Use the role names that match the actual `UserRole` enum.

### Step 4: Run the full test suite

```bash
cd backend && npx jest
```

Expected: 130+ tests passing. 0 failures.

### Step 5: Commit

```bash
git add backend/src/wsHandler.ts
git commit -m "feat(ws): delete handleAuth + case 'auth'; gateway role gate"
```

---

## Task 4 — tsc clean + suppress dead-code warnings

**Files:**
- Modify: `backend/src/wsHandler.ts` (only if tsc complains about unused imports/types)

### Step 1: Run tsc

```bash
cd backend && npx tsc --noEmit
```

Expected: clean. If tsc reports an unused import or unused interface field that's now dead (e.g., `isRelay`/`relayId` no longer in any incoming message payload), either:
- Remove the unused declaration
- If it's still in `AuthenticatedClient` for tracking active gateway sessions, leave it — it's used elsewhere

Do not bulk-delete anything that has any remaining references.

### Step 2: Commit (only if changes were made)

```bash
git add backend/src/wsHandler.ts
git commit -m "chore(ws): tsc cleanup post WS-auth refactor"
```

If no changes needed, skip this commit.

---

## Task 5 — Manual smoke test: WS rejects without cookie, accepts with

**Files:** none (this is verification only)

### Step 1: Boot the backend

```bash
export PATH=/opt/homebrew/opt/postgresql@16/bin:$PATH
export DATABASE_URL=postgres://fegensprenelon@localhost:5432/smithnet
cd backend && (DATABASE_URL=$DATABASE_URL npm run dev > /tmp/p2s3-smoke.log 2>&1 &)
sleep 8
```

### Step 2: Try WS without cookie — should be 401

Use `wscat` if installed, or write a small Node script. The simplest verification is via `curl` (which doesn't speak WS but the upgrade response is HTTP):

```bash
curl -i -s \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" \
  -H "Sec-WebSocket-Version: 13" \
  http://localhost:3030/ \
  | head -5
```

Expected: `HTTP/1.1 401 Unauthorized`.

### Step 3: Register a user, get the access cookie, try WS with cookie

```bash
SMOKE_EMAIL="ws-smoke-$(date +%s)@example.com"
curl -s -X POST http://localhost:3030/api/auth/register \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"password123\",\"displayName\":\"WSSmoke\"}" > /dev/null

# Login and capture the Set-Cookie
RESPONSE=$(curl -s -i -X POST http://localhost:3030/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"password123\"}")
TOKEN=$(echo "$RESPONSE" | grep -oE 'smithnet_access=[^;]+' | head -1 | cut -d= -f2)
echo "Access token: ${TOKEN:0:30}..."

# Try WS upgrade with the cookie
curl -i -s \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" \
  -H "Sec-WebSocket-Version: 13" \
  -H "Cookie: smithnet_access=$TOKEN" \
  http://localhost:3030/ \
  | head -5
```

Expected: `HTTP/1.1 101 Switching Protocols`.

### Step 4: Stop the backend

```bash
pkill -f "tsx watch src/server.ts" 2>/dev/null
pkill -f "node.*server.ts" 2>/dev/null
sleep 2
```

### Step 5: Report what you saw

If both Step 2 (401 without cookie) and Step 3 (101 with cookie) succeeded, the slice is working end-to-end. Note this in the closeout commit message.

---

## Task 6 — Slice 3 closeout: audit annotation + tag

**Files:**
- Modify: `docs/smith-net-architecture-audit.md`

### Step 1: Annotate weak point #4

Find weak point #4 in the audit doc:

```bash
grep -n "WS\|WebSocket\|legacy auth" docs/smith-net-architecture-audit.md | head -10
```

The section is titled approximately `### 4. WS legacy auth` or similar.

Use the commit SHA from Task 1 (the `feat(ws): JWT-validated upgrade handler` commit). Confirm:

```bash
git log --oneline --grep="JWT-validated upgrade" | head -3
```

Append the annotation:

```markdown
### 4. ... [closed in slice 3, commit <SHA>]
```

### Step 2: Run the full test suite

```bash
cd backend && npx jest
```

Expected: 130+ passing.

### Step 3: Commit + tag

```bash
git add docs/smith-net-architecture-audit.md
git commit -m "chore(phase-2): close slice 3 — WS JWT hard cutover

- WS upgrade now requires a valid smithnet_access JWT cookie
- handleAuth deleted; case 'auth' removed from handleMessage
- gateway_connect requires admin/foreman/system role
- audit weak point #4 marked closed
- known Android risk: OkHttp WS client must attach cookie on upgrade"
git tag -a phase-2-slice-3 -m "Phase 2 Slice 3 — WS JWT hard cutover (audit weak point #4)"
```

### Step 4: Verify

```bash
git log --oneline phase-2-slice-2..phase-2-slice-3
git tag --list 'phase-2-*'
```

Expected: 3 tags (`phase-2-slice-1`, `-2`, `-3`); commit log shows Slice 3 commits.

---

## What slice 3 did NOT do

- Did **not** change the Android client. Android still tries to connect without a cookie until it ships the OkHttp fix. The desktop portal isn't on WS yet so it's unaffected.
- Did **not** persist `channelRegistry` or `gatewayManager`. That's Slice 4.
- Did **not** introduce per-message authorization (e.g., channel-membership checks). The JWT identity is just the upgrade gate; message authz is a separate concern.

Slice 4 plan to follow when Slice 3 is in main.
