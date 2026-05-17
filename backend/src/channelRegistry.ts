/**
 * Canonical Channel Registry
 * Source of truth for channel identity across mesh and online
 */

import { v4 as uuidv4 } from 'uuid';
import { Channel, ChannelVisibility } from './types';
import { pg, isPgEnabled } from './db';
import { requestLogger } from './log';

class ChannelRegistry {
  private channels: Map<string, Channel> = new Map();
  private meshHashIndex: Map<number, string> = new Map();

  /**
   * Compute 2-byte mesh hash from channel ID (matches Android)
   */
  private computeMeshHash(channelId: string): number {
    let hash = 0;
    for (let i = 0; i < channelId.length; i++) {
      hash = ((hash << 5) - hash + channelId.charCodeAt(i)) | 0;
    }
    return hash & 0x7FFF; // Keep positive, 15 bits
  }

  private async persistChannel(channel: Channel): Promise<void> {
    if (!isPgEnabled() || !pg) return; // dev-mode fallback: in-memory only
    await pg.query(
      `INSERT INTO channels (
         id, name, type, visibility, creator_id,
         member_ids, allowed_users, blocked_users, pending_requests,
         requires_approval, is_archived, is_deleted, mesh_hash, updated_at
       ) VALUES (
         $1, $2, $3, $4, $5,
         $6::jsonb, $7::jsonb, $8::jsonb, $9::jsonb,
         $10, $11, $12, $13, NOW()
       )
       ON CONFLICT (id) DO UPDATE SET
         name              = EXCLUDED.name,
         type              = EXCLUDED.type,
         visibility        = EXCLUDED.visibility,
         creator_id        = EXCLUDED.creator_id,
         member_ids        = EXCLUDED.member_ids,
         allowed_users     = EXCLUDED.allowed_users,
         blocked_users     = EXCLUDED.blocked_users,
         pending_requests  = EXCLUDED.pending_requests,
         requires_approval = EXCLUDED.requires_approval,
         is_archived       = EXCLUDED.is_archived,
         is_deleted        = EXCLUDED.is_deleted,
         updated_at        = NOW()`,
      [
        channel.id, channel.name, channel.type, channel.visibility, channel.creatorId,
        JSON.stringify(channel.memberIds),
        JSON.stringify(channel.allowedUsers),
        JSON.stringify(channel.blockedUsers),
        JSON.stringify(channel.pendingRequests),
        channel.requiresApproval, channel.isArchived, channel.isDeleted, channel.meshHash,
      ]
    );
  }

  /**
   * Create a new channel with canonical ID
   */
  async create(
    name: string,
    type: Channel['type'],
    creatorId: string,
    memberIds?: string[],
    visibility: ChannelVisibility = 'public',
    requiresApproval: boolean = false
  ): Promise<Channel> {
    const id = uuidv4();
    const meshHash = this.computeMeshHash(id);

    const channel: Channel = {
      id,
      name,
      type,
      visibility,
      creatorId,
      createdAt: Date.now(),
      memberIds: memberIds || [creatorId],
      allowedUsers: [],
      blockedUsers: [],
      pendingRequests: [],
      requiresApproval,
      isArchived: false,
      isDeleted: false,
      meshHash,
    };

    this.channels.set(id, channel);
    this.meshHashIndex.set(meshHash, id);
    await this.persistChannel(channel);

    requestLogger().info({ event: 'channel_created', id, name, visibility, meshHash }, 'channel created');
    return channel;
  }

  /**
   * Get channel by canonical ID
   */
  get(id: string): Channel | undefined {
    return this.channels.get(id);
  }

  /**
   * Get channel by mesh hash
   */
  getByMeshHash(hash: number): Channel | undefined {
    const id = this.meshHashIndex.get(hash);
    return id ? this.channels.get(id) : undefined;
  }

  /**
   * Find channel by name (case-insensitive)
   */
  findByName(name: string): Channel | undefined {
    const lowerName = name.toLowerCase();
    for (const channel of this.channels.values()) {
      if (channel.name.toLowerCase() === lowerName && !channel.isDeleted) {
        return channel;
      }
    }
    return undefined;
  }

  /**
   * List all visible channels
   */
  list(): Channel[] {
    return Array.from(this.channels.values())
      .filter(c => !c.isDeleted && !c.isArchived);
  }

  /**
   * List channels for a specific user (respects visibility permissions)
   */
  listForUser(userId: string): Channel[] {
    return this.list().filter(c => this.canUserAccess(c, userId) || this.canUserSeeInList(c, userId));
  }

  /**
   * Check if user can access a channel
   */
  canUserAccess(channel: Channel, userId: string): boolean {
    // Creator always has access
    if (channel.creatorId === userId) return true;

    // Blocked users never have access
    if (channel.blockedUsers.includes(userId)) return false;

    switch (channel.visibility) {
      case 'public':
        // Public channels respect type-based access
        if (channel.type === 'broadcast') return true;
        if (channel.type === 'dm') return channel.memberIds.includes(userId);
        return channel.memberIds.length === 0 || channel.memberIds.includes(userId);

      case 'private':
        // Private channels require membership
        return channel.memberIds.includes(userId);

      case 'restricted':
        // Restricted channels only allow specific users
        return channel.allowedUsers.includes(userId);

      default:
        return false;
    }
  }

  /**
   * Whether a non-member should still see a channel in their list (for the
   * "discoverable, join-by-request" affordance). Membership-based access is
   * already handled by canUserAccess in listForUser, so this method only
   * needs to cover the discovery case.
   *
   * Previously this returned `true` for ANY `public` channel, which leaked
   * unrelated users' channels across the tenant boundary (a public channel
   * created by user-A appeared in user-B's list even when user-B was not a
   * member and shared no org with user-A). Public visibility now only matters
   * inside `canUserAccess` (open `memberIds.length === 0` channels + explicit
   * membership); discovery is reserved for private channels that explicitly
   * opt in via `requiresApproval`.
   */
  canUserSeeInList(channel: Channel, userId: string): boolean {
    if (this.canUserAccess(channel, userId)) return true;
    return channel.visibility === 'private'
        && channel.requiresApproval
        && !channel.blockedUsers.includes(userId);
  }

  /**
   * Request access to a private channel
   */
  async requestAccess(channelId: string, userId: string): Promise<boolean> {
    const channel = this.channels.get(channelId);
    if (!channel) return false;

    // Can't request if already have access or blocked
    if (this.canUserAccess(channel, userId)) return false;
    if (channel.blockedUsers.includes(userId)) return false;
    if (channel.pendingRequests.includes(userId)) return false;

    // Can only request for private channels with approval
    if (channel.visibility !== 'private' || !channel.requiresApproval) return false;

    channel.pendingRequests.push(userId);
    await this.persistChannel(channel);
    requestLogger().info({ event: 'channel_access_requested', channelId: channel.id, channelName: channel.name, userId }, 'channel access requested');
    return true;
  }

  /**
   * Respond to access request (approve/deny)
   */
  async respondToAccessRequest(channelId: string, requesterId: string, managerId: string, approve: boolean): Promise<boolean> {
    const channel = this.channels.get(channelId);
    if (!channel) return false;

    // Only creator can approve/deny
    if (channel.creatorId !== managerId) return false;

    // Remove from pending
    channel.pendingRequests = channel.pendingRequests.filter(id => id !== requesterId);

    if (approve) {
      // Add to members
      if (!channel.memberIds.includes(requesterId)) {
        channel.memberIds.push(requesterId);
      }
      requestLogger().info({ event: 'channel_access_approved', channelId: channel.id, channelName: channel.name, requesterId, managerId }, 'channel access approved');
    } else {
      requestLogger().info({ event: 'channel_access_denied', channelId: channel.id, channelName: channel.name, requesterId, managerId }, 'channel access denied');
    }

    await this.persistChannel(channel);
    return true;
  }

  /**
   * Update user access (allow/block)
   */
  async updateUserAccess(channelId: string, userId: string, managerId: string, allow: boolean): Promise<boolean> {
    const channel = this.channels.get(channelId);
    if (!channel) return false;

    // Only creator can manage access
    if (channel.creatorId !== managerId) return false;

    if (allow) {
      // Add to allowed, remove from blocked
      if (!channel.allowedUsers.includes(userId)) {
        channel.allowedUsers.push(userId);
      }
      channel.blockedUsers = channel.blockedUsers.filter(id => id !== userId);
      requestLogger().info({ event: 'channel_user_allowed', channelId: channel.id, channelName: channel.name, userId, managerId }, 'channel user allowed');
    } else {
      // Add to blocked, remove from allowed/members
      if (!channel.blockedUsers.includes(userId)) {
        channel.blockedUsers.push(userId);
      }
      channel.allowedUsers = channel.allowedUsers.filter(id => id !== userId);
      channel.memberIds = channel.memberIds.filter(id => id !== userId);
      requestLogger().info({ event: 'channel_user_blocked', channelId: channel.id, channelName: channel.name, userId, managerId }, 'channel user blocked');
    }

    await this.persistChannel(channel);
    return true;
  }

  /**
   * Update channel visibility
   */
  async updateVisibility(channelId: string, managerId: string, visibility: ChannelVisibility, requiresApproval: boolean = false): Promise<boolean> {
    const channel = this.channels.get(channelId);
    if (!channel) return false;

    // Only creator can change visibility
    if (channel.creatorId !== managerId) return false;

    channel.visibility = visibility;
    channel.requiresApproval = requiresApproval;
    await this.persistChannel(channel);
    requestLogger().info({ event: 'channel_visibility_updated', channelId: channel.id, channelName: channel.name, visibility, requiresApproval, managerId }, 'channel visibility updated');
    return true;
  }

  /**
   * Get access status for a user
   */
  getAccessStatus(channelId: string, userId: string): 'granted' | 'pending' | 'can_request' | 'denied' {
    const channel = this.channels.get(channelId);
    if (!channel) return 'denied';

    if (this.canUserAccess(channel, userId)) return 'granted';
    if (channel.pendingRequests.includes(userId)) return 'pending';
    if (channel.visibility === 'private' && channel.requiresApproval && !channel.blockedUsers.includes(userId)) {
      return 'can_request';
    }
    return 'denied';
  }

  /**
   * Subscribe user to all broadcast channels they should have access to
   * Returns list of channel IDs the user is now subscribed to
   */
  async subscribeUserToChannels(userId: string): Promise<string[]> {
    const channelIds: string[] = [];
    const modified: Channel[] = [];
    for (const channel of this.channels.values()) {
      if (!channel.isDeleted && !channel.isArchived) {
        // For broadcast channels, auto-subscribe everyone
        if (channel.type === 'broadcast') {
          if (!channel.memberIds.includes(userId)) {
            channel.memberIds.push(userId);
            modified.push(channel);
          }
          channelIds.push(channel.id);
        } else if (channel.memberIds.includes(userId)) {
          channelIds.push(channel.id);
        }
      }
    }
    for (const c of modified) await this.persistChannel(c);
    return channelIds;
  }

  /**
   * Update channel
   */
  async update(id: string, updates: Partial<Channel>): Promise<Channel | undefined> {
    const channel = this.channels.get(id);
    if (!channel) return undefined;

    const updated = { ...channel, ...updates };
    this.channels.set(id, updated);
    await this.persistChannel(updated);
    return updated;
  }

  /**
   * Archive channel
   */
  async archive(id: string): Promise<boolean> {
    const channel = this.channels.get(id);
    if (!channel) return false;
    channel.isArchived = true;
    await this.persistChannel(channel);
    return true;
  }

  /**
   * Delete channel (soft delete)
   */
  async delete(id: string): Promise<boolean> {
    const channel = this.channels.get(id);
    if (!channel) return false;
    channel.isDeleted = true;
    await this.persistChannel(channel);
    return true;
  }

  /**
   * Add member to channel
   */
  async addMember(channelId: string, userId: string): Promise<boolean> {
    const channel = this.channels.get(channelId);
    if (!channel) return false;
    if (!channel.memberIds.includes(userId)) {
      channel.memberIds.push(userId);
      await this.persistChannel(channel);
    }
    return true;
  }

  /**
   * Remove member from channel
   */
  async removeMember(channelId: string, userId: string): Promise<boolean> {
    const channel = this.channels.get(channelId);
    if (!channel) return false;
    const before = channel.memberIds.length;
    channel.memberIds = channel.memberIds.filter(id => id !== userId);
    if (channel.memberIds.length !== before) {
      await this.persistChannel(channel);
    }
    return true;
  }

  /**
   * Initialize registry by loading persisted channels from Postgres.
   */
  async initialize(): Promise<void> {
    if (!isPgEnabled() || !pg) {
      requestLogger().info({ event: 'channel_registry_initialized', mode: 'memory_only' }, 'channel registry initialized (no DB)');
      return;
    }
    const { rows } = await pg.query<{
      id: string; name: string; type: string; visibility: string; creator_id: string;
      member_ids: string[]; allowed_users: string[]; blocked_users: string[]; pending_requests: string[];
      requires_approval: boolean; is_archived: boolean; is_deleted: boolean; mesh_hash: number;
      created_at: Date;
    }>(
      `SELECT * FROM channels WHERE is_deleted = FALSE ORDER BY created_at ASC`
    );
    let count = 0;
    for (const row of rows) {
      const channel: Channel = {
        id: row.id,
        name: row.name,
        type: row.type as Channel['type'],
        visibility: row.visibility as ChannelVisibility,
        creatorId: row.creator_id,
        memberIds: row.member_ids ?? [],
        allowedUsers: row.allowed_users ?? [],
        blockedUsers: row.blocked_users ?? [],
        pendingRequests: row.pending_requests ?? [],
        requiresApproval: row.requires_approval,
        isArchived: row.is_archived,
        isDeleted: row.is_deleted,
        meshHash: row.mesh_hash,
        createdAt: row.created_at.getTime(),
      };
      this.channels.set(row.id, channel);
      this.meshHashIndex.set(row.mesh_hash, row.id);
      count++;
    }
    requestLogger().info({ event: 'channel_registry_initialized', count }, 'channel registry loaded from pg');
  }
}

export const channelRegistry = new ChannelRegistry();
