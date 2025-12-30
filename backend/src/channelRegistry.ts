/**
 * Canonical Channel Registry
 * Source of truth for channel identity across mesh and online
 */

import { v4 as uuidv4 } from 'uuid';
import { Channel, ChannelVisibility } from './types';

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

  /**
   * Create a new channel with canonical ID
   */
  create(
    name: string, 
    type: Channel['type'], 
    creatorId: string, 
    memberIds?: string[],
    visibility: ChannelVisibility = 'public',
    requiresApproval: boolean = false
  ): Channel {
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

    console.log(`[ChannelRegistry] Created: ${name} (${id}) visibility=${visibility} meshHash=${meshHash}`);
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
   * Check if user can see channel in listings (for private/restricted, show if can request)
   */
  canUserSeeInList(channel: Channel, userId: string): boolean {
    if (channel.visibility === 'public') return true;
    if (this.canUserAccess(channel, userId)) return true;
    // Can see if they can request access
    return channel.visibility === 'private' && channel.requiresApproval && !channel.blockedUsers.includes(userId);
  }

  /**
   * Request access to a private channel
   */
  requestAccess(channelId: string, userId: string): boolean {
    const channel = this.channels.get(channelId);
    if (!channel) return false;

    // Can't request if already have access or blocked
    if (this.canUserAccess(channel, userId)) return false;
    if (channel.blockedUsers.includes(userId)) return false;
    if (channel.pendingRequests.includes(userId)) return false;

    // Can only request for private channels with approval
    if (channel.visibility !== 'private' || !channel.requiresApproval) return false;

    channel.pendingRequests.push(userId);
    console.log(`[ChannelRegistry] Access request: ${userId} for ${channel.name}`);
    return true;
  }

  /**
   * Respond to access request (approve/deny)
   */
  respondToAccessRequest(channelId: string, requesterId: string, managerId: string, approve: boolean): boolean {
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
      console.log(`[ChannelRegistry] Access approved: ${requesterId} for ${channel.name}`);
    } else {
      console.log(`[ChannelRegistry] Access denied: ${requesterId} for ${channel.name}`);
    }

    return true;
  }

  /**
   * Update user access (allow/block)
   */
  updateUserAccess(channelId: string, userId: string, managerId: string, allow: boolean): boolean {
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
      console.log(`[ChannelRegistry] User allowed: ${userId} in ${channel.name}`);
    } else {
      // Add to blocked, remove from allowed/members
      if (!channel.blockedUsers.includes(userId)) {
        channel.blockedUsers.push(userId);
      }
      channel.allowedUsers = channel.allowedUsers.filter(id => id !== userId);
      channel.memberIds = channel.memberIds.filter(id => id !== userId);
      console.log(`[ChannelRegistry] User blocked: ${userId} from ${channel.name}`);
    }

    return true;
  }

  /**
   * Update channel visibility
   */
  updateVisibility(channelId: string, managerId: string, visibility: ChannelVisibility, requiresApproval: boolean = false): boolean {
    const channel = this.channels.get(channelId);
    if (!channel) return false;

    // Only creator can change visibility
    if (channel.creatorId !== managerId) return false;

    channel.visibility = visibility;
    channel.requiresApproval = requiresApproval;
    console.log(`[ChannelRegistry] Visibility updated: ${channel.name} to ${visibility}`);
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
  subscribeUserToChannels(userId: string): string[] {
    const channelIds: string[] = [];
    for (const channel of this.channels.values()) {
      if (!channel.isDeleted && !channel.isArchived) {
        // For broadcast channels, auto-subscribe everyone
        if (channel.type === 'broadcast') {
          if (!channel.memberIds.includes(userId)) {
            channel.memberIds.push(userId);
          }
          channelIds.push(channel.id);
        } else if (channel.memberIds.includes(userId)) {
          channelIds.push(channel.id);
        }
      }
    }
    return channelIds;
  }

  /**
   * Update channel
   */
  update(id: string, updates: Partial<Channel>): Channel | undefined {
    const channel = this.channels.get(id);
    if (!channel) return undefined;

    const updated = { ...channel, ...updates };
    this.channels.set(id, updated);
    return updated;
  }

  /**
   * Archive channel
   */
  archive(id: string): boolean {
    const channel = this.channels.get(id);
    if (!channel) return false;
    channel.isArchived = true;
    return true;
  }

  /**
   * Delete channel (soft delete)
   */
  delete(id: string): boolean {
    const channel = this.channels.get(id);
    if (!channel) return false;
    channel.isDeleted = true;
    return true;
  }

  /**
   * Add member to channel
   */
  addMember(channelId: string, userId: string): boolean {
    const channel = this.channels.get(channelId);
    if (!channel) return false;
    if (!channel.memberIds.includes(userId)) {
      channel.memberIds.push(userId);
    }
    return true;
  }

  /**
   * Remove member from channel
   */
  removeMember(channelId: string, userId: string): boolean {
    const channel = this.channels.get(channelId);
    if (!channel) return false;
    channel.memberIds = channel.memberIds.filter(id => id !== userId);
    return true;
  }

  /**
   * Initialize registry (no default channels - dashboard creates them dynamically)
   */
  initialize(): void {
    console.log(`[ChannelRegistry] Initialized (no default channels - create via dashboard)`);
  }
}

export const channelRegistry = new ChannelRegistry();
