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
import rateLimit from 'express-rate-limit';
import { WebSocketServer } from 'ws';
import http from 'http';
import path from 'path';
import fs from 'fs';
import { apiRouter, proposalPublicRouter } from './api';
import { authRouter } from './authRoutes';
import adminRouter from './adminRoutes';
import { wsHandler } from './wsHandler';
import { channelRegistry } from './channelRegistry';
import { mediaRouter, IMAGES_DIR, VOICE_DIR, FILES_DIR, cleanupOldMedia } from './mediaHandler';
import { auditLog, AuditAction } from './auditLog';
import { llm } from './llmInterface';
import { reconcile, acceptClientMessages } from './reconciliationEngine';
import { invoiceLinkService } from './invoiceLinks';

const PORT = process.env.PORT || 3000;

// Create Express app
const app = express();

// CORS - Allow requests from anywhere (mobile apps, web clients)
// F1.1: removed legacy X-User-Id, X-User-Name from allowedHeaders. Identity = JWT only.
// (CORS allowlist tightening to specific origins is F1.2's scope.)
app.use(cors({
  origin: '*',
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization']
}));

app.use(express.json());

// Trust X-Forwarded-For when behind Tailscale Funnel / reverse proxy so rate limits
// bucket by the originating IP, not the proxy's loopback address.
app.set('trust proxy', 1);

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
      ws: 'ws://localhost:3000',
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
  
  // Log server start
  auditLog.log(AuditAction.ADMIN_ACTION, 'system', { action: 'server_start', port: PORT });
});

// Schedule media cleanup every hour
setInterval(cleanupOldMedia, 60 * 60 * 1000);

// Schedule audit log cleanup daily
setInterval(() => auditLog.cleanupOldEntries(), 24 * 60 * 60 * 1000);
