/**
 * C-05: Data Retention Core - Audit Logging System
 * 
 * Immutable audit trail for security-critical actions.
 * Logs are append-only and cannot be modified.
 */

import fs from 'fs';
import path from 'path';
import { pg, isPgEnabled } from './db';
import { requestLogger } from './log';
import { enqueue } from './queue/queue';

// ════════════════════════════════════════════════════════════════════
// AUDIT ACTIONS
// ════════════════════════════════════════════════════════════════════

export enum AuditAction {
  // Authentication
  USER_REGISTER = 'user.register',
  USER_LOGIN = 'user.login',
  USER_LOGIN_FAILED = 'user.login_failed',
  USER_LOGOUT = 'user.logout',
  USER_PROFILE_UPDATE = 'user.profile_update',
  USER_ROLE_CHANGE = 'user.role_change',
  USER_DEACTIVATED = 'user.deactivated',
  TOKEN_REFRESH = 'token.refresh',
  
  // Messaging
  MESSAGE_SENT = 'message.sent',
  MESSAGE_DELETED = 'message.deleted',
  MESSAGE_MEDIA_UPLOAD = 'message.media_upload',
  
  // Channels
  CHANNEL_CREATED = 'channel.created',
  CHANNEL_DELETED = 'channel.deleted',
  CHANNEL_CLEARED = 'channel.cleared',
  CHANNEL_MEMBER_ADDED = 'channel.member_added',
  CHANNEL_MEMBER_REMOVED = 'channel.member_removed',

  // Jobs
  JOB_CREATED = 'job.created',
  JOB_UPDATED = 'job.updated',
  JOB_STATUS_CHANGED = 'job.status_changed',
  JOB_CREW_ASSIGNED = 'job.crew_assigned',
  JOB_CREW_UNASSIGNED = 'job.crew_unassigned',
  JOB_GEOCODED = 'job.geocoded',

  // Crew tracking (Phase 3.5)
  SHIFT_STARTED = 'shift.started',
  SHIFT_ENDED = 'shift.ended',
  LOCATION_REPORTED = 'location.reported',

  // Gateway / Mesh
  GATEWAY_CONNECTED = 'gateway.connected',
  GATEWAY_DISCONNECTED = 'gateway.disconnected',
  MESH_MESSAGE_RELAYED = 'mesh.message_relayed',
  
  // Admin
  ADMIN_ACTION = 'admin.action',
  CONFIG_CHANGE = 'config.change',
  DATA_EXPORT = 'data.export',
  DATA_PURGE = 'data.purge',
  
  // Security
  SECURITY_ALERT = 'security.alert',
  PERMISSION_DENIED = 'security.permission_denied',
  RATE_LIMIT_EXCEEDED = 'security.rate_limit',
}

// ════════════════════════════════════════════════════════════════════
// AUDIT ENTRY
// ════════════════════════════════════════════════════════════════════

export interface AuditEntry {
  id: string;
  timestamp: number;
  action: AuditAction;
  actorId: string;        // User who performed action (or 'system')
  targetId?: string;      // Target user/channel/message if applicable
  metadata: Record<string, any>;
  ip?: string;
  userAgent?: string;
  /** SHA256 of the previous entry's checksum + this entry's body. null for the first entry. */
  prevChecksum: string | null;
  /** SHA256(prevChecksum + body). */
  checksum: string;
}

// ════════════════════════════════════════════════════════════════════
// RETENTION POLICIES
// ════════════════════════════════════════════════════════════════════

export interface RetentionPolicy {
  name: string;
  actions: AuditAction[];
  retentionDays: number;
  compressAfterDays?: number;
}

const DEFAULT_POLICIES: RetentionPolicy[] = [
  {
    name: 'security',
    actions: [
      AuditAction.USER_LOGIN,
      AuditAction.USER_LOGIN_FAILED,
      AuditAction.SECURITY_ALERT,
      AuditAction.PERMISSION_DENIED,
      AuditAction.USER_ROLE_CHANGE,
    ],
    retentionDays: 365, // 1 year
  },
  {
    name: 'admin',
    actions: [
      AuditAction.ADMIN_ACTION,
      AuditAction.CONFIG_CHANGE,
      AuditAction.DATA_EXPORT,
      AuditAction.DATA_PURGE,
    ],
    retentionDays: 730, // 2 years
  },
  {
    name: 'messaging',
    actions: [
      AuditAction.MESSAGE_SENT,
      AuditAction.MESSAGE_DELETED,
      AuditAction.CHANNEL_CREATED,
      AuditAction.CHANNEL_DELETED,
    ],
    retentionDays: 90, // 90 days
  },
  {
    name: 'default',
    actions: [], // Catches everything else
    retentionDays: 30, // 30 days
  },
];

// ════════════════════════════════════════════════════════════════════
// AUDIT LOG MANAGER
// ════════════════════════════════════════════════════════════════════

class AuditLogManager {
  private entries: AuditEntry[] = [];
  private logFile: string;
  private entryCounter = 0;
  private policies: RetentionPolicy[] = DEFAULT_POLICIES;
  private jsonlBuffer: AuditEntry[] = [];
  private flushTimer: NodeJS.Timeout | null = null;

  constructor() {
    // Create audit directory
    const auditDir = path.join(process.cwd(), 'audit');
    if (!fs.existsSync(auditDir)) {
      fs.mkdirSync(auditDir, { recursive: true });
    }

    // Daily log file
    const date = new Date().toISOString().split('T')[0];
    this.logFile = path.join(auditDir, `audit-${date}.jsonl`);

    // Load existing entries for today
    this.loadTodaysLog();

    requestLogger().info({ event: 'audit_log_initialized', entries: this.entries.length }, 'audit log initialized');
  }

  private loadTodaysLog() {
    try {
      if (fs.existsSync(this.logFile)) {
        const content = fs.readFileSync(this.logFile, 'utf-8');
        const lines = content.trim().split('\n').filter(line => line);
        this.entries = lines.map(line => {
          const parsed = JSON.parse(line);
          if (!('prevChecksum' in parsed)) parsed.prevChecksum = null;
          return parsed;
        });
        this.entryCounter = this.entries.length;
      }
    } catch (e) {
      requestLogger().error({ event: 'audit_log_load_failed', err: e }, 'failed to load existing audit log');
    }
  }

  private generateChecksum(entry: Omit<AuditEntry, 'checksum'>): string {
    const crypto = require('crypto');
    const body = JSON.stringify({
      id: entry.id,
      timestamp: entry.timestamp,
      action: entry.action,
      actorId: entry.actorId,
      targetId: entry.targetId,
      metadata: entry.metadata,
    });
    const seed = (entry.prevChecksum ?? '') + body;
    return crypto.createHash('sha256').update(seed).digest('hex');
  }

  /**
   * Log an audit entry (append-only).
   *
   * Enqueues an audit_flush background job so the chain hashing + INSERT
   * happen in the worker (Task 2). The caller gets back an auditId
   * immediately without waiting for the pg write.
   */
  async log(
    action: AuditAction,
    actorId: string,
    metadata: Record<string, any> = {},
    options: { targetId?: string; ip?: string; userAgent?: string } = {}
  ): Promise<{ auditId: string; queued: true }> {
    const auditId = `audit-${Date.now()}-${++this.entryCounter}`;
    const timestamp = Date.now();

    if (!isPgEnabled() || !pg) {
      // No pg available — fall back to in-memory + JSONL only (dev mode).
      this.logInMemoryOnly(auditId, timestamp, action, actorId, metadata, options);
      return { auditId, queued: true };
    }

    await enqueue({
      kind: 'audit_flush',
      dedupeKey: auditId,
      payload: {
        auditId,
        timestamp,
        action,
        actorId,
        targetId: options.targetId ?? null,
        metadata,
        ip: options.ip ?? null,
        userAgent: options.userAgent ?? null,
      },
    });
    return { auditId, queued: true };
  }

  /** No-pg path for local dev with DATABASE_URL unset. */
  private logInMemoryOnly(
    auditId: string,
    timestamp: number,
    action: AuditAction,
    actorId: string,
    metadata: Record<string, any>,
    options: { targetId?: string; ip?: string; userAgent?: string }
  ): void {
    const prevChecksum = this.entries.length > 0
      ? this.entries[this.entries.length - 1].checksum
      : null;
    const entry: Omit<AuditEntry, 'checksum'> = {
      id: auditId,
      timestamp,
      action,
      actorId,
      targetId: options.targetId,
      metadata,
      ip: options.ip,
      userAgent: options.userAgent,
      prevChecksum,
    };
    const full: AuditEntry = { ...entry, checksum: this.generateChecksum(entry) };
    this.entries.push(full);
    this.bufferJsonl(full);
  }

  /**
   * Called by the auditFlushWorker after a successful pg INSERT.
   * Mirrors the row into the in-memory cache and JSONL buffer.
   */
  bufferFromWorker(entry: AuditEntry): void {
    this.entries.push(entry);
    this.bufferJsonl(entry);
  }

  /**
   * Add entry to in-memory buffer. Lazily start a 60s flush timer on first write.
   */
  private bufferJsonl(entry: AuditEntry): void {
    this.jsonlBuffer.push(entry);
    if (!this.flushTimer) {
      this.flushTimer = setInterval(() => this.flushJsonl(), 60 * 1000);
      // Don't keep the process alive for the timer alone.
      this.flushTimer.unref();
    }
  }

  /**
   * Flush buffered entries to JSONL. Called by the interval timer and on shutdown.
   */
  private flushJsonl(): void {
    if (this.jsonlBuffer.length === 0) return;
    const drained = this.jsonlBuffer;
    this.jsonlBuffer = [];
    try {
      const blob = drained.map((e) => JSON.stringify(e)).join('\n') + '\n';
      fs.appendFileSync(this.logFile, blob);
    } catch (e) {
      requestLogger().error({ event: 'audit_log_flush_failed', err: e }, 'failed to flush JSONL buffer');
      // Re-queue on failure — the next interval will retry.
      this.jsonlBuffer.unshift(...drained);
    }
  }

  /**
   * Called on process shutdown so the buffer drains.
   */
  flushNow(): void {
    this.flushJsonl();
  }

  /**
   * Query audit logs with filters.
   */
  query(filters: {
    action?: AuditAction;
    actorId?: string;
    targetId?: string;
    startTime?: number;
    endTime?: number;
    limit?: number;
  }): AuditEntry[] {
    let results = [...this.entries];

    if (filters.action) {
      results = results.filter(e => e.action === filters.action);
    }
    if (filters.actorId) {
      results = results.filter(e => e.actorId === filters.actorId);
    }
    if (filters.targetId) {
      results = results.filter(e => e.targetId === filters.targetId);
    }
    if (filters.startTime) {
      results = results.filter(e => e.timestamp >= filters.startTime!);
    }
    if (filters.endTime) {
      results = results.filter(e => e.timestamp <= filters.endTime!);
    }

    // Sort by newest first
    results.sort((a, b) => b.timestamp - a.timestamp);

    if (filters.limit) {
      results = results.slice(0, filters.limit);
    }

    return results;
  }

  /**
   * Get retention policy for an action.
   */
  getRetentionPolicy(action: AuditAction): RetentionPolicy {
    for (const policy of this.policies) {
      if (policy.actions.includes(action)) {
        return policy;
      }
    }
    return this.policies[this.policies.length - 1]; // Default policy
  }

  /**
   * Clean up old entries based on retention policies.
   * Should be run periodically (e.g., daily cron).
   */
  async cleanupOldEntries(): Promise<{ deleted: number }> {
    const now = Date.now();
    let deleted = 0;

    // Process old log files in audit directory
    const auditDir = path.dirname(this.logFile);
    const files = fs.readdirSync(auditDir).filter(f => f.startsWith('audit-') && f.endsWith('.jsonl'));

    for (const file of files) {
      // Extract date from filename
      const match = file.match(/audit-(\d{4}-\d{2}-\d{2})\.jsonl/);
      if (!match) continue;

      const fileDate = new Date(match[1]).getTime();
      const ageDays = (now - fileDate) / (24 * 60 * 60 * 1000);

      // Check if file is beyond maximum retention (2 years)
      if (ageDays > 730) {
        const filePath = path.join(auditDir, file);
        fs.unlinkSync(filePath);
        requestLogger().info({ event: 'audit_log_file_deleted', file }, 'deleted old audit log file');
        deleted++;
      }
    }

    return { deleted };
  }

  /**
   * Verify integrity of audit entries using checksums.
   */
  verifyIntegrity(): { valid: number; invalid: number; entries: string[] } {
    let valid = 0;
    let invalid = 0;
    const invalidEntries: string[] = [];
    let expectedPrev: string | null = null;

    for (const entry of this.entries) {
      const { checksum, ...rest } = entry;
      if ((rest.prevChecksum ?? null) !== expectedPrev) {
        invalid++;
        invalidEntries.push(`${entry.id} (broken chain)`);
        expectedPrev = checksum;
        continue;
      }
      const expectedChecksum = this.generateChecksum(rest);
      if (checksum === expectedChecksum) {
        valid++;
      } else {
        invalid++;
        invalidEntries.push(`${entry.id} (bad hash)`);
      }
      expectedPrev = checksum;
    }

    return { valid, invalid, entries: invalidEntries };
  }

  /**
   * Get summary statistics.
   */
  getStats(): {
    totalEntries: number;
    byAction: Record<string, number>;
    oldestEntry?: number;
    newestEntry?: number;
  } {
    const byAction: Record<string, number> = {};
    
    for (const entry of this.entries) {
      byAction[entry.action] = (byAction[entry.action] || 0) + 1;
    }

    return {
      totalEntries: this.entries.length,
      byAction,
      oldestEntry: this.entries[0]?.timestamp,
      newestEntry: this.entries[this.entries.length - 1]?.timestamp,
    };
  }
}

export const auditLog = new AuditLogManager();
