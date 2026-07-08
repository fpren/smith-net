/**
 * Relay integration test — confirms the happy path end-to-end.
 *
 * Runs against whatever WS URL is supplied via SMITH_RELAY_URL
 * (default: wss://ubuntu-8gb-ash-1.tail2523e7.ts.net).
 *
 *   node --test backend/tests/relay.integration.test.js
 */

const test = require('node:test');
const assert = require('node:assert');
const WebSocket = require('../node_modules/ws');

const WS_URL = process.env.SMITH_RELAY_URL || 'wss://ubuntu-8gb-ash-1.tail2523e7.ts.net';
const HTTP_URL = WS_URL.replace(/^ws/, 'http');

function collect(ws, untilType, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const frames = [];
    const t = setTimeout(() => reject(new Error(`timeout waiting for ${untilType}`)), timeoutMs);
    ws.on('message', (buf) => {
      const msg = JSON.parse(buf.toString());
      frames.push(msg);
      if (msg.type === untilType) {
        clearTimeout(t);
        resolve(frames);
      }
    });
  });
}

test('health endpoint returns ok', async () => {
  const res = await fetch(`${HTTP_URL}/api/health`);
  const body = await res.json();
  assert.strictEqual(res.status, 200);
  assert.strictEqual(body.status, 'ok');
});

async function login() {
  const password = process.env.DEFAULT_ADMIN_PASSWORD;
  const res = await fetch(`${HTTP_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email: 'admin@smithnet.local', password }),
  });
  assert.strictEqual(res.status, 200, 'admin login should succeed');
  const body = await res.json();
  assert.ok(body.accessToken, 'login returns an access token');
  return body;
}

test('WS auth + publish + ack round-trip', async () => {
  // The relay validates a JWT on the HTTP upgrade (wsAuth, Phase 2 Slice 3), so
  // identity is server-authoritative — the client no longer supplies its own
  // userId. Authenticate over REST, then connect with the bearer token.
  const { accessToken, user } = await login();
  const ws = new WebSocket(WS_URL, { headers: { Authorization: `Bearer ${accessToken}` } });
  // Attach the auth_ok collector before 'open': on a validated upgrade the server
  // emits auth_ok immediately, with no client auth frame, so we must be listening.
  const authPromise = collect(ws, 'auth_ok');
  await new Promise((res, rej) => {
    ws.once('open', res);
    ws.once('error', rej);
  });

  const afterAuth = await authPromise;
  assert.ok(afterAuth.some(m => m.type === 'auth_ok'), 'expected auth_ok');

  ws.send(JSON.stringify({
    type: 'message',
    payload: {
      channelId: 'general',
      senderId: user.id,
      senderName: user.displayName || 'Integration Tester',
      content: `itest-${Date.now()}`,
    },
    timestamp: Date.now(),
  }));

  const afterMsg = await collect(ws, 'message_ack');
  assert.ok(afterMsg.some(m => m.type === 'message_ack'), 'expected message_ack');

  ws.close();
});

test('rate limit headers present', async () => {
  const res = await fetch(`${HTTP_URL}/api/presence`);
  assert.ok(res.headers.get('ratelimit-limit'), 'expected ratelimit-limit header');
  assert.ok(res.headers.get('ratelimit-policy'), 'expected ratelimit-policy header');
});
