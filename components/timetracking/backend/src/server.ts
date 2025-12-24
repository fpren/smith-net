/**
 * C-12: Time Tracking Backend Server
 * Immutable time logging for Smith Net
 */

import express from 'express';
import cors from 'cors';
import jwt from 'jsonwebtoken';
import { timeStore } from './timeStore';
import {
  UserContext,
  ClockInRequest,
  ManualEntryRequest,
} from './types';

const PORT = process.env.PORT || 3002;
const JWT_SECRET = process.env.JWT_SECRET || 'smith-net-dev-secret-change-in-production';

const app = express();
app.use(cors());
app.use(express.json());

// ════════════════════════════════════════════════════════════════════
// AUTH MIDDLEWARE
// ════════════════════════════════════════════════════════════════════

interface AuthRequest extends express.Request {
  user?: UserContext;
}

function authenticateToken(
  req: AuthRequest,
  res: express.Response,
  next: express.NextFunction
) {
  const authHeader = req.headers.authorization;
  const token = authHeader?.startsWith('Bearer ') ? authHeader.slice(7) : null;

  if (!token) {
    return res.status(401).json({ error: 'No token provided' });
  }

  try {
    const payload = jwt.verify(token, JWT_SECRET) as any;
    req.user = {
      userId: payload.userId,
      email: payload.email,
      displayName: payload.displayName || payload.email,
      role: payload.role,
      organizationId: payload.organizationId,
    };
    next();
  } catch (e) {
    return res.status(401).json({ error: 'Invalid token' });
  }
}

// ════════════════════════════════════════════════════════════════════
// ROOT
// ════════════════════════════════════════════════════════════════════

app.get('/', (_req, res) => {
  res.json({
    name: 'Smith Net Time Tracking',
    version: '1.0.0',
    component: 'C-12',
    description: 'Immutable time logging with tamper protection',
    endpoints: {
      status: '/api/status',
      clockIn: 'POST /api/clock-in',
      clockOut: 'POST /api/clock-out',
      entries: '/api/entries',
      summary: '/api/summary',
    },
  });
});

// ════════════════════════════════════════════════════════════════════
// CLOCK IN / OUT
// ════════════════════════════════════════════════════════════════════

// Get current status
app.get('/api/status', authenticateToken, (req: AuthRequest, res) => {
  const user = req.user!;
  const isClockedIn = timeStore.isUserClockedIn(user.userId);
  const activeEntry = timeStore.getActiveEntry(user.userId);

  res.json({
    isClockedIn,
    activeEntry,
    currentTime: Date.now(),
  });
});

// Clock in
app.post('/api/clock-in', authenticateToken, (req: AuthRequest, res) => {
  const user = req.user!;
  const request: ClockInRequest = req.body;

  const result = timeStore.clockIn(user.userId, user.displayName, request, 'manual');

  if ('error' in result) {
    return res.status(400).json({ error: result.error });
  }

  res.status(201).json({ entry: result });
});

// Clock out
app.post('/api/clock-out', authenticateToken, (req: AuthRequest, res) => {
  const user = req.user!;
  const { note } = req.body;

  const result = timeStore.clockOut(user.userId, note);

  if ('error' in result) {
    return res.status(400).json({ error: result.error });
  }

  res.json({ entry: result });
});

// ════════════════════════════════════════════════════════════════════
// ENTRIES API
// ════════════════════════════════════════════════════════════════════

// Get my entries
app.get('/api/entries', authenticateToken, (req: AuthRequest, res) => {
  const user = req.user!;
  const { date, jobId, limit } = req.query;

  let entries = timeStore.getEntriesByUser(user.userId);

  if (date) {
    entries = entries.filter(e => {
      const entryDate = new Date(e.clockInTime).toISOString().split('T')[0];
      return entryDate === date;
    });
  }

  if (jobId) {
    entries = entries.filter(e => e.jobId === jobId);
  }

  if (limit) {
    entries = entries.slice(0, parseInt(limit as string, 10));
  }

  res.json({ entries });
});

// Get single entry
app.get('/api/entries/:id', authenticateToken, (req: AuthRequest, res) => {
  const entry = timeStore.getEntry(req.params.id);
  if (!entry) {
    return res.status(404).json({ error: 'Entry not found' });
  }

  // Verify integrity
  const isValid = timeStore.verifyIntegrity(entry);

  res.json({ entry, integrityValid: isValid });
});

// Create manual entry
app.post('/api/entries/manual', authenticateToken, (req: AuthRequest, res) => {
  const user = req.user!;
  const request: ManualEntryRequest = req.body;

  if (!request.clockInTime || !request.clockOutTime) {
    return res.status(400).json({ error: 'clockInTime and clockOutTime are required' });
  }

  if (request.clockOutTime <= request.clockInTime) {
    return res.status(400).json({ error: 'clockOutTime must be after clockInTime' });
  }

  const entry = timeStore.createManualEntry(user.userId, user.displayName, request);
  res.status(201).json({ entry });
});

// Add note to entry
app.post('/api/entries/:id/notes', authenticateToken, (req: AuthRequest, res) => {
  const user = req.user!;
  const { text, type } = req.body;

  if (!text) {
    return res.status(400).json({ error: 'text is required' });
  }

  const entry = timeStore.addNote(req.params.id, user.userId, text, type || 'note');
  if (!entry) {
    return res.status(404).json({ error: 'Entry not found' });
  }

  res.json({ entry });
});

// ════════════════════════════════════════════════════════════════════
// APPROVAL (Foreman/Admin only)
// ════════════════════════════════════════════════════════════════════

app.get('/api/approvals/pending', authenticateToken, (req: AuthRequest, res) => {
  const user = req.user!;

  // Only foreman, enterprise, admin can view pending approvals
  if (!['foreman', 'enterprise', 'admin'].includes(user.role)) {
    return res.status(403).json({ error: 'Insufficient permissions' });
  }

  const pending = timeStore.getPendingApprovals();
  res.json({ entries: pending });
});

app.post('/api/entries/:id/approve', authenticateToken, (req: AuthRequest, res) => {
  const user = req.user!;

  if (!['foreman', 'enterprise', 'admin'].includes(user.role)) {
    return res.status(403).json({ error: 'Insufficient permissions' });
  }

  const entry = timeStore.approveEntry(req.params.id, user.userId);
  if (!entry) {
    return res.status(404).json({ error: 'Entry not found' });
  }

  res.json({ entry });
});

// ════════════════════════════════════════════════════════════════════
// SUMMARIES
// ════════════════════════════════════════════════════════════════════

// Daily summary
app.get('/api/summary/daily', authenticateToken, (req: AuthRequest, res) => {
  const user = req.user!;
  const date = (req.query.date as string) || new Date().toISOString().split('T')[0];

  const summary = timeStore.getDailySummary(user.userId, date);
  res.json({ summary });
});

// Weekly summary
app.get('/api/summary/weekly', authenticateToken, (req: AuthRequest, res) => {
  const user = req.user!;
  
  // Default to current week (Monday)
  let weekStart = req.query.weekStart as string;
  if (!weekStart) {
    const today = new Date();
    const day = today.getDay();
    const diff = today.getDate() - day + (day === 0 ? -6 : 1);
    weekStart = new Date(today.setDate(diff)).toISOString().split('T')[0];
  }

  const summary = timeStore.getWeeklySummary(user.userId, weekStart);
  res.json({ summary });
});

// ════════════════════════════════════════════════════════════════════
// STATS
// ════════════════════════════════════════════════════════════════════

app.get('/api/stats', authenticateToken, (_req, res) => {
  const stats = timeStore.getStats();
  res.json({ stats });
});

// ════════════════════════════════════════════════════════════════════
// START SERVER
// ════════════════════════════════════════════════════════════════════

app.listen(PORT, () => {
  console.log('════════════════════════════════════════');
  console.log('⏱️  TIME TRACKING (C-12) STARTED');
  console.log(`   HTTP: http://localhost:${PORT}`);
  console.log(`   API:  http://localhost:${PORT}/api`);
  console.log('════════════════════════════════════════');
});
