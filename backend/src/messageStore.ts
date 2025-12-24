/**
 * Message Store
 * In-memory message persistence (replace with DB in production)
 */

import { v4 as uuidv4 } from 'uuid';
import { Message, MessageOrigin } from './types';

class MessageStore {
  private messages: Map<string, Message> = new Map();
  private channelMessages: Map<string, string[]> = new Map();
  private readonly maxMessagesPerChannel = 1000;
  
  // Track when each channel was last cleared (for sync)
  private channelClearedAt: Map<string, number> = new Map();

  /**
   * Store a new message
   */
  add(
    channelId: string,
    senderId: string,
    senderName: string,
    content: string,
    origin: MessageOrigin,
    recipientId?: string,
    recipientName?: string
  ): Message {
    const message: Message = {
      id: uuidv4(),
      channelId,
      senderId,
      senderName,
      content,
      timestamp: Date.now(),
      origin,
      recipientId,
      recipientName,
    };

    this.messages.set(message.id, message);

    // Index by channel
    if (!this.channelMessages.has(channelId)) {
      this.channelMessages.set(channelId, []);
    }
    const channelMsgs = this.channelMessages.get(channelId)!;
    channelMsgs.push(message.id);

    // Prune old messages
    if (channelMsgs.length > this.maxMessagesPerChannel) {
      const removed = channelMsgs.shift();
      if (removed) this.messages.delete(removed);
    }

    console.log(`[MessageStore] Added: ${message.id.substring(0, 8)} to ${channelId} (${origin})`);
    return message;
  }

  /**
   * Get message by ID
   */
  get(id: string): Message | undefined {
    return this.messages.get(id);
  }

  /**
   * Get messages for a channel
   */
  getForChannel(channelId: string, limit = 100, before?: number): Message[] {
    const messageIds = this.channelMessages.get(channelId) || [];
    
    let messages = messageIds
      .map(id => this.messages.get(id))
      .filter((m): m is Message => m !== undefined);

    if (before) {
      messages = messages.filter(m => m.timestamp < before);
    }

    return messages
      .sort((a, b) => a.timestamp - b.timestamp)
      .slice(-limit);
  }

  /**
   * Get recent messages across all channels for a user
   */
  getRecentForUser(userId: string, limit = 50): Message[] {
    return Array.from(this.messages.values())
      .filter(m => m.senderId === userId || m.recipientId === userId)
      .sort((a, b) => b.timestamp - a.timestamp)
      .slice(0, limit);
  }

  /**
   * Clear all messages in a channel
   */
  clearChannel(channelId: string): number {
    const messageIds = this.channelMessages.get(channelId) || [];
    const clearedAt = Date.now();
    
    // Remove all messages
    for (const id of messageIds) {
      this.messages.delete(id);
    }
    
    // Clear the channel index
    this.channelMessages.set(channelId, []);
    
    // Record when this channel was cleared (for sync)
    this.channelClearedAt.set(channelId, clearedAt);
    
    console.log(`[MessageStore] Cleared ${messageIds.length} messages from channel: ${channelId} at ${clearedAt}`);
    return clearedAt;
  }
  
  /**
   * Get the timestamp when a channel was last cleared.
   * Returns 0 if never cleared.
   */
  getChannelClearedAt(channelId: string): number {
    return this.channelClearedAt.get(channelId) || 0;
  }
  
  /**
   * Get clear timestamps for all channels (for sync).
   */
  getAllClearTimestamps(): Record<string, number> {
    const result: Record<string, number> = {};
    for (const [channelId, timestamp] of this.channelClearedAt) {
      result[channelId] = timestamp;
    }
    return result;
  }
  
  /**
   * Delete a single message (for "Delete for everyone").
   * Returns true if deleted, false if not found or unauthorized.
   * 
   * @param messageId The message to delete
   * @param requesterId The user requesting deletion (must be sender or admin)
   */
  deleteMessage(messageId: string, requesterId: string): boolean {
    const message = this.messages.get(messageId);
    
    if (!message) {
      console.log(`[MessageStore] Delete failed: message ${messageId} not found`);
      return false;
    }
    
    // Only allow sender or admin to delete
    // "admin" is the dashboard/Foreman user
    if (message.senderId !== requesterId && requesterId !== 'admin') {
      console.log(`[MessageStore] Delete denied: ${requesterId} cannot delete ${message.senderId}'s message`);
      return false;
    }
    
    // Remove from messages map
    this.messages.delete(messageId);
    
    // Remove from channel index
    const channelMsgs = this.channelMessages.get(message.channelId);
    if (channelMsgs) {
      const index = channelMsgs.indexOf(messageId);
      if (index !== -1) {
        channelMsgs.splice(index, 1);
      }
    }
    
    console.log(`[MessageStore] Deleted message ${messageId} by ${requesterId}`);
    return true;
  }
}

export const messageStore = new MessageStore();
