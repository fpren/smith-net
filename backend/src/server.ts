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
import { WebSocketServer } from 'ws';
import http from 'http';
import path from 'path';
import { apiRouter } from './api';
import { authRouter } from './authRoutes';
import { wsHandler } from './wsHandler';
import { channelRegistry } from './channelRegistry';
import { mediaRouter, IMAGES_DIR, VOICE_DIR, FILES_DIR, cleanupOldMedia } from './mediaHandler';
import { auditLog, AuditAction } from './auditLog';
import { llm } from './llmInterface';

const PORT = process.env.PORT || 3000;

// Create Express app
const app = express();

// CORS - Allow requests from anywhere (mobile apps, web clients)
app.use(cors({
  origin: '*',
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'X-User-Id', 'X-User-Name']
}));

app.use(express.json());

// Mount Auth API (C-01, C-02)
app.use('/api/auth', authRouter);

// Mount API
app.use('/api', apiRouter);

// Mount Media API
app.use('/api/media', mediaRouter);

// Serve static media files
app.use('/media/images', express.static(IMAGES_DIR));
app.use('/media/voice', express.static(VOICE_DIR));
app.use('/media/files', express.static(FILES_DIR));

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
