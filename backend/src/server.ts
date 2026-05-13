/**
 * Smith Net Backend Server
 * Online messaging + Gateway control plane
 * 
 * Phase 0 Components:
 * - C-01: Authentication & Identity
 * - C-02: Role Engine
 * - C-03: Schema & Boundary Engine
 * - C-04: Vendor-Neutral LLM Interface
 * - C-05: Data Retention Core
 */

import express from 'express';
import cors from 'cors';
import cookieParser from 'cookie-parser';
import rateLimit from 'express-rate-limit';
import { WebSocketServer } from 'ws';
import http from 'http';
import path from 'path';
import fs from 'fs';
import { apiRouter, proposalPublicRouter } from './api';
import { authRouter } from './authRoutes';
import adminRouter from './adminRoutes';
import { jobsRouter } from './jobsRoutes';
import { profilesRouter } from './profilesRoutes';
import { wsHandler } from './wsHandler';
import { channelRegistry } from './channelRegistry';
import { mediaRouter, IMAGES_DIR, VOICE_DIR, FILES_DIR, cleanupOldMedia } from './mediaHandler';
import { auditLog, AuditAction } from './auditLog';
import { llm } from './llmInterface';
import { reconcile, acceptClientMessages } from './reconciliationEngine';
import { invoiceLinkService } from './invoiceLinks';
import { v4 as uuidv4 } from 'uuid';
import { withRequestContext } from './log';

const PORT = process.env.PORT || 3030;

// Create Express app
const app = express();

// CORS - allowlisted origins only.
// F1.1: removed legacy X-User-Id / X-User-Name. F1.2: replaced `origin: '*'` with allowlist.
//
// Current clients:
//   - Android app → Tailscale Funnel (native HTTP, no Origin header → allowed via `if (!origin)`)
//   - Desktop portal → relative `/api` (served same-origin, no cross-origin call)
//
// No production browser origin exists yet. When a real portal domain is registered,
// add it via the CORS_ALLOWED_ORIGINS env var (comma-separated) — no code deploy needed.
const ALLOWED_ORIGINS: RegExp[] = [];

// Env-driven allowlist: literal origins like "https://portal.example.com,https://staging.example.com"
const envOrigins = (process.env.CORS_ALLOWED_ORIGINS || '')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean);
for (const origin of envOrigins) {
  // Escape regex metachars, anchor, push
  const escaped = origin.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  ALLOWED_ORIGINS.push(new RegExp(`^${escaped}$`));
}

if (process.env.NODE_ENV !== 'production') {
  ALLOWED_ORIGINS.push(/^http:\/\/localhost(:\d+)?$/);
  ALLOWED_ORIGINS.push(/^http:\/\/127\.0\.0\.1(:\d+)?$/);
  ALLOWED_ORIGINS.push(/^http:\/\/192\.168\.\d+\.\d+(:\d+)?$/); // device-on-LAN dev
}

app.use(cors({
  origin: (origin, cb) => {
    // No Origin header → native app, server-to-server, or curl. Allow.
    if (!origin) return cb(null, true);
    if (ALLOWED_ORIGINS.some((r) => r.test(origin))) return cb(null, true);
    console.warn('[cors] rejected origin', { origin });
    return cb(new Error('CORS: origin not allowed'));
  },
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization'],
}));

// F1.5: explicit body limit. Express default is 100kb, which is fine for
// every current endpoint; the explicit 512kb headroom covers proposal/invoice
// payloads with embedded materials lists. Anything bigger is media — that
// path uses multipart/form-data, not JSON, and is handled by mediaRouter.
app.use(express.json({ limit: '512kb' }));
app.use(cookieParser());

// Trust X-Forwarded-For when behind Tailscale Funnel / reverse proxy so rate limits
// bucket by the originating IP, not the proxy's loopback address.
app.set('trust proxy', 1);

// Phase 2 Slice 2: structured-log request context. Every request gets a
// req_id (echoed in response header). Routes inside the chain emit logs
// with req_id/route bindings via requestLogger().
app.use((req, res, next) => {
  const req_id = (req.headers['x-request-id'] as string) || uuidv4();
  res.setHeader('x-request-id', req_id);
  const route = `${req.method} ${req.path}`;
  // actor_id is unknown here — auth middleware can re-enter the context
  // later. Phase 2 interim: req_id + route are the minimum viable.
  withRequestContext({ req_id, route }, () => next());
});

// F1.1 deprecation guard — log if any client still sends legacy X-User-Id / X-User-Name headers.
// The headers are now ignored; identity comes from the JWT via authenticateToken middleware.
// Keep this guard for one release window, then remove the headers from CORS allowedHeaders + delete this middleware.
app.use((req, _res, next) => {
  if (req.headers['x-user-id'] || req.headers['x-user-name']) {
    console.warn('[deprecated-header]', req.method, req.path, 'received X-User-Id/X-User-Name; will be removed', {
      ip: req.ip, ua: req.get('user-agent'),
    });
  }
  next();
});

// Global rate limit on all /api/* traffic. 300 req/min/IP is plenty for a real client
// and chokes off trivial abuse before it hits application logic.
const apiLimiter = rateLimit({
  windowMs: 60_000,
  max: 300,
  standardHeaders: true,
  legacyHeaders: false,
  skip: (req) => req.path === '/api/health',
});

// Tighter limit on auth — defends against credential stuffing.
const authLimiter = rateLimit({
  windowMs: 15 * 60_000,
  max: 20,
  standardHeaders: true,
  legacyHeaders: false,
});

app.use('/api', apiLimiter);

// Mount Auth API (C-01, C-02)
app.use('/api/auth', authLimiter, authRouter);

// Mount Admin API
app.use('/api/admin', adminRouter);

// Mount Jobs API
app.use('/api/jobs', jobsRouter);

// Mount Profiles API (read-only crew search for console)
app.use('/api/profiles', profilesRouter);

// Mount API
app.use('/api', apiRouter);

// Mount public proposal pages (client-facing, no auth)
app.use('/p', proposalPublicRouter);

// Mount Media API
app.use('/api/media', mediaRouter);

// Serve static media files
app.use('/media/images', express.static(IMAGES_DIR));
app.use('/media/voice', express.static(VOICE_DIR));
app.use('/media/files', express.static(FILES_DIR));

// ── Public: Shareable Invoice Page ─────────────────────────────
app.get('/i/:uuid', async (req, res) => {
  try {
    const invoice = await invoiceLinkService.getByUuid(req.params.uuid);
    if (!invoice) {
      return res.status(404).send('<h2>Invoice not found</h2>');
    }

    const templatePath = path.join(__dirname, 'templates', 'invoice.html');
    let html = fs.readFileSync(templatePath, 'utf8');

    const fmt = (n: number | null | undefined) =>
      n != null ? Number(n).toFixed(2) : '0.00';

    const statusColorMap: Record<string, string> = {
      paid: '#38a169',
      unpaid: '#e53e3e',
      partial: '#dd6b20',
    };
    const statusColor = statusColorMap[invoice.status] ?? '#718096';

    const createdAt = invoice.created_at
      ? new Date(invoice.created_at).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })
      : '';

    // Build materials rows
    const materials: Array<{ name: string; quantity: number; unit: string; unitCost: number; totalCost: number }> =
      Array.isArray(invoice.materials) ? invoice.materials : [];
    const hasMaterials = materials.length > 0;

    const materialsRows = materials
      .map(
        (m) =>
          `<tr><td>${m.name ?? ''}</td><td>${m.quantity ?? ''}</td><td>${m.unit ?? ''}</td>` +
          `<td>$${fmt(m.unitCost)}</td><td>$${fmt(m.totalCost)}</td></tr>`
      )
      .join('\n');

    // Simple template substitution
    const replace = (key: string, value: string) => {
      html = html.split(`{{${key}}}`).join(value);
    };

    replace('contractorName', invoice.contractor_name ?? '');
    replace('contractorPhone', invoice.contractor_phone ?? '');
    replace('contractorLicense', invoice.contractor_license ?? 'N/A');
    replace('clientName', invoice.client_name ?? '');
    replace('clientAddress', invoice.client_address ?? '');
    replace('workSummary', invoice.work_summary ?? '');
    replace('hoursWorked', String(invoice.hours_worked ?? 0));
    replace('hourlyRate', fmt(invoice.hourly_rate));
    replace('laborCost', fmt(invoice.labor_cost));
    replace('materialsCost', fmt(invoice.materials_cost));
    replace('totalDue', fmt(invoice.total_due));
    replace('paymentInfo', invoice.payment_info ?? '');
    replace('status', (invoice.status ?? 'unpaid').toUpperCase());
    replace('statusColor', statusColor);
    replace('jobId', invoice.job_id ?? '');
    replace('createdAt', createdAt);
    replace('materialsRows', materialsRows);

    // Conditionally show/hide materials and payment sections
    if (hasMaterials) {
      html = html.replace(/{{#hasMaterials}}/g, '').replace(/{{\/hasMaterials}}/g, '');
    } else {
      html = html.replace(/{{#hasMaterials}}[\s\S]*?{{\/hasMaterials}}/g, '');
    }

    const hasPaymentInfo = !!(invoice.payment_info);
    if (hasPaymentInfo) {
      html = html.replace(/{{#hasPaymentInfo}}/g, '').replace(/{{\/hasPaymentInfo}}/g, '');
    } else {
      html = html.replace(/{{#hasPaymentInfo}}[\s\S]*?{{\/hasPaymentInfo}}/g, '');
    }

    res.setHeader('Content-Type', 'text/html');
    res.send(html);
  } catch (err) {
    console.error('[Invoice] Render error:', err);
    res.status(500).send('<h2>Error loading invoice</h2>');
  }
});

// Reconciliation endpoints (Phase 1 Messaging Unification)
app.post('/api/reconcile', async (req, res) => {
  try {
    const { channelId, localMessageIds, localClock } = req.body;
    const result = await reconcile({ channelId, localMessageIds, localClock });
    res.json(result);
  } catch (err) {
    console.error('[Reconcile] Error:', err);
    res.status(500).json({ error: 'Reconciliation failed' });
  }
});

app.post('/api/reconcile/push', async (req, res) => {
  try {
    const { messages } = req.body;
    await acceptClientMessages(messages);
    res.json({ accepted: messages.length });
  } catch (err) {
    console.error('[Reconcile] Push error:', err);
    res.status(500).json({ error: 'Push failed' });
  }
});

// Root endpoint
app.get('/', (_req, res) => {
  res.json({
    name: 'Smith Net Backend',
    version: '1.0.0-p0',
    phase: 'P0 Forge',
    components: {
      'C-01': 'Authentication & Identity ✓',
      'C-02': 'Role Engine ✓',
      'C-03': 'Schema & Boundary Engine ✓',
      'C-04': 'LLM Interface ✓',
      'C-05': 'Data Retention Core ✓',
    },
    endpoints: {
      auth: '/api/auth',
      api: '/api',
      ws: `ws://localhost:${PORT}`,
    },
  });
});

// Create HTTP server
const server = http.createServer(app);

// Create WebSocket server
const wss = new WebSocketServer({ server });

// Initialize WebSocket handler
wsHandler.initialize(wss);

// Initialize channel registry with defaults
channelRegistry.initialize();

// Rehydrate channels from Postgres so they survive restarts.
// Non-blocking: we kick it off, the server starts listening in parallel.
(async () => {
  try {
    const { pg, isPgEnabled } = await import('./db');
    if (!isPgEnabled() || !pg) return;
    const { rows } = await pg.query(
      `SELECT id, name, type, creator_id, created_at, is_archived, is_deleted
         FROM channels
        WHERE is_deleted = FALSE OR is_deleted IS NULL`
    );
    channelRegistry.rehydrate(rows);
  } catch (err) {
    console.warn('[Startup] Channel rehydrate skipped:', (err as Error).message);
  }
})();

// Start server
server.listen(PORT, () => {
  console.log('════════════════════════════════════════');
  console.log('🚀 SMITH NET BACKEND STARTED');
  console.log(`   Phase: P0 Forge`);
  console.log('────────────────────────────────────────');
  console.log(`   HTTP:  http://localhost:${PORT}`);
  console.log(`   WS:    ws://localhost:${PORT}`);
  console.log(`   Auth:  http://localhost:${PORT}/api/auth`);
  console.log(`   API:   http://localhost:${PORT}/api`);
  console.log(`   Media: http://localhost:${PORT}/media`);
  console.log('────────────────────────────────────────');
  console.log('   Components:');
  console.log('   ✓ C-01 Authentication & Identity');
  console.log('   ✓ C-02 Role Engine');
  console.log('   ✓ C-03 Schema & Boundary Engine');
  console.log('   ✓ C-04 LLM Interface');
  console.log('   ✓ C-05 Data Retention Core');
  console.log('════════════════════════════════════════');
  
  // Log server start (fire-and-forget — startup banner mustn't block on pg).
  auditLog
    .log(AuditAction.ADMIN_ACTION, 'system', { action: 'server_start', port: PORT })
    .catch((err) => console.error('[AuditLog] server_start audit failed (non-fatal):', err));
});

// Schedule media cleanup every hour
setInterval(cleanupOldMedia, 60 * 60 * 1000);

// Schedule audit log cleanup daily
setInterval(() => auditLog.cleanupOldEntries(), 24 * 60 * 60 * 1000);

// Flush audit log buffer on process exit
process.on('SIGTERM', () => { auditLog.flushNow(); });
process.on('SIGINT', () => { auditLog.flushNow(); });
