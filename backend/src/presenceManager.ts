/**
 * Presence Manager
 * Tracks online users and their connection status
 */

import { Presence } from './types';

class PresenceManager {
  private presence: Map<string, Presence> = new Map();
  private readonly staleTimeout = 60_000; // 1 minute

  /**
   * Update user presence
   */
  update(
    userId: string,
    userName: string,
    status: Presence['status'],
    connectionType: Presence['connectionType']
  ): Presence {
    const p: Presence = {
      userId,
      userName,
      status,
      lastSeen: Date.now(),
      connectionType,
    };
    this.presence.set(userId, p);
    return p;
  }

  /**
   * Mark user as offline
   */
  setOffline(userId: string): void {
    const p = this.presence.get(userId);
    if (p) {
      p.status = 'offline';
      p.lastSeen = Date.now();
    }
  }

  /**
   * Get presence for a user
   */
  get(userId: string): Presence | undefined {
    return this.presence.get(userId);
  }

  /**
   * Get all online users
   */
  getOnline(): Presence[] {
    const now = Date.now();
    return Array.from(this.presence.values()).filter(
      p => p.status !== 'offline' && now - p.lastSeen < this.staleTimeout
    );
  }

  /**
   * Get all presence data
   */
  getAll(): Presence[] {
    return Array.from(this.presence.values());
  }

  /**
   * Clean up stale entries
   */
  cleanup(): number {
    const now = Date.now();
    let removed = 0;
    
    for (const [id, p] of this.presence) {
      if (p.status === 'offline' && now - p.lastSeen > this.staleTimeout * 10) {
        this.presence.delete(id);
        removed++;
      }
    }
    
    return removed;
  }
}

export const presenceManager = new PresenceManager();
