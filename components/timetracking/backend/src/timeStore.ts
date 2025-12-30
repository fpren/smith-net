/**
 * C-12: Time Store
 * Immutable time entry storage with tamper detection
 */

import crypto from 'crypto';
import { v4 as uuidv4 } from 'uuid';
import {
  TimeEntry,
  TimeNote,
  EntryType,
  EntrySource,
  EntryStatus,
  DailySummary,
  WeeklySummary,
  ClockInRequest,
  ClockOutRequest,
  ClockOutContext,
  ManualEntryRequest,
} from './types';

class TimeStore {
  private entries: Map<string, TimeEntry> = new Map();
  private activeEntries: Map<string, string> = new Map(); // userId -> entryId

  // ════════════════════════════════════════════════════════════════════
  // IMMUTABILITY HELPERS
  // ════════════════════════════════════════════════════════════════════

  private generateHash(entry: Omit<TimeEntry, 'immutableHash' | 'notes' | 'status' | 'approvedBy' | 'approvedAt'>): string {
    const data = JSON.stringify({
      id: entry.id,
      userId: entry.userId,
      clockInTime: entry.clockInTime,
      clockOutTime: entry.clockOutTime,
      jobId: entry.jobId,
      entryType: entry.entryType,
      source: entry.source,
      createdAt: entry.createdAt,
      clockOutContext: entry.clockOutContext, // Include clock-out context in hash
    });
    return crypto.createHash('sha256').update(data).digest('hex');
  }

  verifyIntegrity(entry: TimeEntry): boolean {
    const expectedHash = this.generateHash(entry);
    return entry.immutableHash === expectedHash;
  }

  // ════════════════════════════════════════════════════════════════════
  // CLOCK IN / OUT
  // ════════════════════════════════════════════════════════════════════

  clockIn(
    userId: string,
    userName: string,
    request: ClockInRequest,
    source: EntrySource = 'manual'
  ): TimeEntry | { error: string } {
    // Check if already clocked in
    const activeEntryId = this.activeEntries.get(userId);
    if (activeEntryId) {
      return { error: 'Already clocked in. Clock out first.' };
    }

    const entry: TimeEntry = {
      id: uuidv4(),
      userId,
      userName,
      clockInTime: Date.now(),
      clockOutTime: undefined,
      durationMinutes: undefined,
      jobId: request.jobId,
      jobTitle: request.jobTitle,
      projectId: request.projectId,
      location: request.location,
      entryType: request.entryType || 'regular',
      source,
      createdAt: Date.now(),
      immutableHash: '', // Will be set below
      notes: [],
      status: 'active',
    };

    // Generate immutable hash
    entry.immutableHash = this.generateHash(entry);

    // Add initial note if provided
    if (request.note) {
      entry.notes.push({
        id: uuidv4(),
        text: request.note,
        addedBy: userId,
        addedAt: Date.now(),
        type: 'note',
      });
    }

    this.entries.set(entry.id, entry);
    this.activeEntries.set(userId, entry.id);

    console.log(`[TimeStore] Clock IN: ${userName} at ${new Date(entry.clockInTime).toLocaleTimeString()}`);
    return entry;
  }

  clockOut(userId: string, note?: string): TimeEntry | { error: string } {
    const activeEntryId = this.activeEntries.get(userId);
    if (!activeEntryId) {
      return { error: 'Not clocked in.' };
    }

    const entry = this.entries.get(activeEntryId);
    if (!entry) {
      this.activeEntries.delete(userId);
      return { error: 'Entry not found.' };
    }

    // Update entry (clock out time is mutable until set)
    entry.clockOutTime = Date.now();
    entry.durationMinutes = Math.round((entry.clockOutTime - entry.clockInTime) / 60000);
    entry.status = 'completed';

    // Regenerate hash with clock out time
    entry.immutableHash = this.generateHash(entry);

    // Add clock out note
    if (note) {
      entry.notes.push({
        id: uuidv4(),
        text: note,
        addedBy: userId,
        addedAt: Date.now(),
        type: 'note',
      });
    }

    this.entries.set(entry.id, entry);
    this.activeEntries.delete(userId);

    console.log(`[TimeStore] Clock OUT: ${entry.userName} - ${entry.durationMinutes} minutes`);
    return entry;
  }

  clockOutWithContext(
    userId: string,
    contextType: 'worker_note' | 'ai_summary',
    content: string,
    userName: string
  ): TimeEntry | { error: string } {
    const activeEntryId = this.activeEntries.get(userId);
    if (!activeEntryId) {
      return { error: 'Not clocked in.' };
    }

    const entry = this.entries.get(activeEntryId);
    if (!entry) {
      this.activeEntries.delete(userId);
      return { error: 'Entry not found.' };
    }

    // Update entry (clock out time is mutable until set)
    entry.clockOutTime = Date.now();
    entry.durationMinutes = Math.round((entry.clockOutTime - entry.clockInTime) / 60000);
    entry.status = 'completed';

    // Create clock-out context (immutable once set)
    const clockOutContext: ClockOutContext = {
      type: contextType,
      content,
      generatedAt: Date.now(),
      generatedBy: contextType === 'ai_summary' ? 'ai' : userId,
      isImmutable: true
    };

    entry.clockOutContext = clockOutContext;

    // Regenerate hash with clock out time and context
    entry.immutableHash = this.generateHash(entry);

    this.entries.set(entry.id, entry);
    this.activeEntries.delete(userId);

    console.log(`[TimeStore] Clock OUT with ${contextType}: ${entry.userName} - ${entry.durationMinutes} minutes`);

    return entry;
  }

  // ════════════════════════════════════════════════════════════════════
  // MANUAL ENTRY (for corrections)
  // ════════════════════════════════════════════════════════════════════

  createManualEntry(
    userId: string,
    userName: string,
    request: ManualEntryRequest
  ): TimeEntry {
    const entry: TimeEntry = {
      id: uuidv4(),
      userId,
      userName,
      clockInTime: request.clockInTime,
      clockOutTime: request.clockOutTime,
      durationMinutes: Math.round((request.clockOutTime - request.clockInTime) / 60000),
      jobId: request.jobId,
      jobTitle: request.jobTitle,
      projectId: request.projectId,
      location: request.location,
      entryType: request.entryType || 'regular',
      source: 'manual',
      createdAt: Date.now(),
      immutableHash: '',
      notes: [],
      status: 'pending_review', // Manual entries need approval
    };

    entry.immutableHash = this.generateHash(entry);

    if (request.note) {
      entry.notes.push({
        id: uuidv4(),
        text: request.note,
        addedBy: userId,
        addedAt: Date.now(),
        type: 'correction',
      });
    }

    this.entries.set(entry.id, entry);
    console.log(`[TimeStore] Manual entry created: ${userName} - ${entry.durationMinutes} minutes (pending review)`);
    return entry;
  }

  // ════════════════════════════════════════════════════════════════════
  // NOTES (Append-only)
  // ════════════════════════════════════════════════════════════════════

  addNote(
    entryId: string,
    userId: string,
    text: string,
    type: 'note' | 'correction' | 'dispute' = 'note'
  ): TimeEntry | undefined {
    const entry = this.entries.get(entryId);
    if (!entry) return undefined;

    entry.notes.push({
      id: uuidv4(),
      text,
      addedBy: userId,
      addedAt: Date.now(),
      type,
    });

    this.entries.set(entryId, entry);
    return entry;
  }

  // ════════════════════════════════════════════════════════════════════
  // APPROVAL
  // ════════════════════════════════════════════════════════════════════

  approveEntry(entryId: string, approvedBy: string): TimeEntry | undefined {
    const entry = this.entries.get(entryId);
    if (!entry) return undefined;

    entry.status = 'approved';
    entry.approvedBy = approvedBy;
    entry.approvedAt = Date.now();

    entry.notes.push({
      id: uuidv4(),
      text: 'Entry approved',
      addedBy: approvedBy,
      addedAt: Date.now(),
      type: 'approval',
    });

    this.entries.set(entryId, entry);
    return entry;
  }

  // ════════════════════════════════════════════════════════════════════
  // QUERIES
  // ════════════════════════════════════════════════════════════════════

  getEntry(id: string): TimeEntry | undefined {
    return this.entries.get(id);
  }

  getActiveEntry(userId: string): TimeEntry | undefined {
    const activeId = this.activeEntries.get(userId);
    return activeId ? this.entries.get(activeId) : undefined;
  }

  isUserClockedIn(userId: string): boolean {
    return this.activeEntries.has(userId);
  }

  getEntriesByUser(userId: string): TimeEntry[] {
    return Array.from(this.entries.values())
      .filter(e => e.userId === userId)
      .sort((a, b) => b.clockInTime - a.clockInTime);
  }

  getEntriesByDate(date: string): TimeEntry[] {
    // date format: YYYY-MM-DD
    const dayStart = new Date(date).setHours(0, 0, 0, 0);
    const dayEnd = new Date(date).setHours(23, 59, 59, 999);

    return Array.from(this.entries.values())
      .filter(e => e.clockInTime >= dayStart && e.clockInTime <= dayEnd)
      .sort((a, b) => a.clockInTime - b.clockInTime);
  }

  getEntriesByJob(jobId: string): TimeEntry[] {
    return Array.from(this.entries.values())
      .filter(e => e.jobId === jobId)
      .sort((a, b) => b.clockInTime - a.clockInTime);
  }

  getPendingApprovals(): TimeEntry[] {
    return Array.from(this.entries.values())
      .filter(e => e.status === 'pending_review')
      .sort((a, b) => a.createdAt - b.createdAt);
  }

  // ════════════════════════════════════════════════════════════════════
  // SUMMARIES
  // ════════════════════════════════════════════════════════════════════

  getDailySummary(userId: string, date: string): DailySummary {
    const entries = this.getEntriesByDate(date).filter(e => e.userId === userId);

    let totalMinutes = 0;
    let regularMinutes = 0;
    let overtimeMinutes = 0;
    let breakMinutes = 0;

    for (const entry of entries) {
      const duration = entry.durationMinutes || 0;
      totalMinutes += duration;

      switch (entry.entryType) {
        case 'regular':
          regularMinutes += duration;
          break;
        case 'overtime':
          overtimeMinutes += duration;
          break;
        case 'break':
          breakMinutes += duration;
          break;
        default:
          regularMinutes += duration;
      }
    }

    return {
      date,
      userId,
      entries,
      totalMinutes,
      regularMinutes,
      overtimeMinutes,
      breakMinutes,
    };
  }

  getWeeklySummary(userId: string, weekStart: string): WeeklySummary {
    const dailySummaries: DailySummary[] = [];
    const startDate = new Date(weekStart);

    for (let i = 0; i < 7; i++) {
      const date = new Date(startDate);
      date.setDate(date.getDate() + i);
      const dateStr = date.toISOString().split('T')[0];
      dailySummaries.push(this.getDailySummary(userId, dateStr));
    }

    const totalMinutes = dailySummaries.reduce((sum, d) => sum + d.totalMinutes, 0);
    const regularMinutes = dailySummaries.reduce((sum, d) => sum + d.regularMinutes, 0);
    const overtimeMinutes = dailySummaries.reduce((sum, d) => sum + d.overtimeMinutes, 0);

    return {
      weekStart,
      userId,
      dailySummaries,
      totalMinutes,
      regularMinutes,
      overtimeMinutes,
    };
  }

  // ════════════════════════════════════════════════════════════════════
  // STATS
  // ════════════════════════════════════════════════════════════════════

  getStats(): {
    totalEntries: number;
    activeClockIns: number;
    pendingApprovals: number;
    totalHoursToday: number;
  } {
    const today = new Date().toISOString().split('T')[0];
    const todayEntries = this.getEntriesByDate(today);
    const totalMinutesToday = todayEntries.reduce((sum, e) => sum + (e.durationMinutes || 0), 0);

    return {
      totalEntries: this.entries.size,
      activeClockIns: this.activeEntries.size,
      pendingApprovals: this.getPendingApprovals().length,
      totalHoursToday: Math.round(totalMinutesToday / 60 * 10) / 10,
    };
  }
}

export const timeStore = new TimeStore();
