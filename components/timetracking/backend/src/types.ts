/**
 * C-12: Time Tracking Types
 * Immutable time logging with tamper protection
 */

// ════════════════════════════════════════════════════════════════════
// TIME ENTRY (Immutable once created)
// ════════════════════════════════════════════════════════════════════

export interface TimeEntry {
  id: string;
  userId: string;
  userName: string;
  
  // Clock times
  clockInTime: number;
  clockOutTime?: number;
  
  // Duration (calculated)
  durationMinutes?: number;
  
  // Context
  jobId?: string;
  jobTitle?: string;
  projectId?: string;
  location?: string;
  
  // Entry metadata
  entryType: EntryType;
  source: EntrySource;
  
  // Immutability
  createdAt: number;
  immutableHash: string;    // SHA256 for tamper detection
  
  // Notes (can be appended, not edited)
  notes: TimeNote[];
  
  // Status
  status: EntryStatus;
  approvedBy?: string;
  approvedAt?: number;
}

export type EntryType = 
  | 'regular'        // Normal work hours
  | 'overtime'       // Overtime hours
  | 'break'          // Break time
  | 'travel'         // Travel time
  | 'on_call';       // On-call hours

export type EntrySource = 
  | 'manual'         // User manually entered
  | 'geofence'       // GPS/geofence auto-detected (C-13)
  | 'beacon'         // BLE beacon auto-detected
  | 'mesh';          // Mesh network presence

export type EntryStatus = 
  | 'active'         // Currently clocked in
  | 'completed'      // Clocked out
  | 'pending_review' // Awaiting approval
  | 'approved'       // Approved by foreman
  | 'disputed';      // Under dispute

// ════════════════════════════════════════════════════════════════════
// TIME NOTES (Append-only)
// ════════════════════════════════════════════════════════════════════

export interface TimeNote {
  id: string;
  text: string;
  addedBy: string;
  addedAt: number;
  type: 'note' | 'correction' | 'dispute' | 'approval';
}

// ════════════════════════════════════════════════════════════════════
// SHIFT / DAILY SUMMARY
// ════════════════════════════════════════════════════════════════════

export interface DailySummary {
  date: string;           // YYYY-MM-DD
  userId: string;
  entries: TimeEntry[];
  totalMinutes: number;
  regularMinutes: number;
  overtimeMinutes: number;
  breakMinutes: number;
}

export interface WeeklySummary {
  weekStart: string;      // YYYY-MM-DD (Monday)
  userId: string;
  dailySummaries: DailySummary[];
  totalMinutes: number;
  regularMinutes: number;
  overtimeMinutes: number;
}

// ════════════════════════════════════════════════════════════════════
// USER CONTEXT
// ════════════════════════════════════════════════════════════════════

export interface UserContext {
  userId: string;
  email: string;
  displayName: string;
  role: string;
  organizationId?: string;
}

// ════════════════════════════════════════════════════════════════════
// API REQUESTS
// ════════════════════════════════════════════════════════════════════

export interface ClockInRequest {
  jobId?: string;
  jobTitle?: string;
  projectId?: string;
  location?: string;
  entryType?: EntryType;
  note?: string;
}

export interface ClockOutRequest {
  note?: string;
}

export interface AddNoteRequest {
  entryId: string;
  text: string;
  type?: 'note' | 'correction' | 'dispute';
}

export interface ManualEntryRequest {
  clockInTime: number;
  clockOutTime: number;
  jobId?: string;
  jobTitle?: string;
  projectId?: string;
  location?: string;
  entryType?: EntryType;
  note?: string;
}
