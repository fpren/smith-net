/**
 * Canonical Channel Registry
 * Source of truth for channel identity across mesh and online
 */

import { v4 as uuidv4 } from 'uuid';
import { Channel } from './types';

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
  create(name: string, type: Channel['type'], creatorId: string, memberIds?: string[]): Channel {
    const id = uuidv4();
    const meshHash = this.computeMeshHash(id);

    const channel: Channel = {
      id,
      name,
      type,
      creatorId,
      createdAt: Date.now(),
      memberIds: memberIds || [creatorId],
      isArchived: false,
      isDeleted: false,
      meshHash,
    };

    this.channels.set(id, channel);
    this.meshHashIndex.set(meshHash, id);

    console.log(`[ChannelRegistry] Created: ${name} (${id}) meshHash=${meshHash}`);
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
   * List channels for a specific user
   * For broadcast channels, everyone has access
   */
  listForUser(userId: string): Channel[] {
    return this.list().filter(c => 
      c.type === 'broadcast' || c.memberIds.includes(userId)
    );
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
