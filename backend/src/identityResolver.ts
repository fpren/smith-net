/**
 * IDENTITY RESOLVER — Identity Unification System
 * ================================================
 *
 * PURPOSE:
 * Enforce identity unification so that transport-specific identities
 * (online account, BLE UUID) converge into ONE canonical author per message.
 *
 * CORE PRINCIPLES:
 * 1. There is ONE canonical message per human action
 * 2. There is ONE canonical MessageID per message
 * 3. Transport-specific identifiers MUST NOT create new authors
 * 4. BLE UUIDs are NOT authors; they are carrier identifiers
 * 5. Author identity is logical, not transport-bound
 *
 * IDENTITY MODEL:
 * - AuthorID: Logical user identity (the human/account)
 * - DeviceID/MeshUUID: Physical transport identity (the carrier)
 * - MessageID: Deterministic ID based on AuthorID + Lamport timestamp + counter + payload
 */

import crypto from 'crypto';

export interface AuthorIdentity {
  authorId: string;          // Canonical user ID
  displayName: string;        // User's display name
  accountId?: string;         // Online account ID (if authenticated)
  deviceIds: string[];        // Known device/mesh UUIDs for this author
}

export interface MessageIdentity {
  messageId: string;          // Deterministic canonical ID
  authorId: string;           // Canonical author (logical)
  deviceId?: string;          // Transport device ID (metadata only)
  lamportTimestamp: number;   // Logical clock
  localCounter: number;       // Author's local message counter
  contentHash: string;        // Hash of message payload
}

export class IdentityResolver {

  // Map DeviceID/MeshUUID → AuthorID
  private deviceToAuthor: Map<string, string> = new Map();

  // Map AuthorID → AuthorIdentity
  private authors: Map<string, AuthorIdentity> = new Map();

  // Track message counters per author
  private authorCounters: Map<string, number> = new Map();

  // Track known MessageIDs to prevent duplicates
  private knownMessages: Set<string> = new Set();

  // Map temporary offline IDs to real author IDs (for later merge)
  private tempToRealAuthor: Map<string, string> = new Map();

  /**
   * Resolve a sender to their canonical AuthorID
   *
   * @param senderId - Could be account ID, device UUID, or temp ID
   * @param senderName - Display name (used for new author creation)
   * @param deviceId - Optional device/mesh UUID (for mapping)
   * @param isOnline - Whether this is from online or mesh transport
   */
  resolveAuthor(
    senderId: string,
    senderName: string,
    deviceId?: string,
    isOnline: boolean = true
  ): string {

    // Case 1: Online with account ID → use account ID as AuthorID
    if (isOnline && this.authors.has(senderId)) {
      // Link device if provided
      if (deviceId) {
        this.linkDeviceToAuthor(deviceId, senderId);
      }
      return senderId;
    }

    // Case 2: Device ID is known → map to existing AuthorID
    if (deviceId && this.deviceToAuthor.has(deviceId)) {
      const authorId = this.deviceToAuthor.get(deviceId)!;
      return authorId;
    }

    // Case 3: New offline device → create temporary AuthorID
    if (!isOnline && deviceId) {
      const tempAuthorId = `temp_${deviceId}`;

      // Check if this temp ID was already resolved
      if (this.tempToRealAuthor.has(tempAuthorId)) {
        return this.tempToRealAuthor.get(tempAuthorId)!;
      }

      // Create temporary author
      this.createAuthor(tempAuthorId, senderName, undefined, [deviceId]);
      this.deviceToAuthor.set(deviceId, tempAuthorId);

      return tempAuthorId;
    }

    // Case 4: Online account not yet seen → create new author
    if (isOnline) {
      this.createAuthor(senderId, senderName, senderId, deviceId ? [deviceId] : []);
      if (deviceId) {
        this.linkDeviceToAuthor(deviceId, senderId);
      }
      return senderId;
    }

    // Fallback: use senderId as-is
    return senderId;
  }

  /**
   * Generate a deterministic MessageID
   *
   * MessageID = hash(AuthorID + Lamport + Counter + ContentHash)
   * This ensures the same semantic message never creates duplicate IDs
   */
  generateMessageId(
    authorId: string,
    content: string,
    lamportTimestamp: number
  ): MessageIdentity {

    // Get or initialize counter for this author
    const counter = this.getNextCounter(authorId);

    // Hash the content for uniqueness
    const contentHash = crypto
      .createHash('sha256')
      .update(content)
      .digest('hex')
      .substring(0, 16);

    // Generate deterministic MessageID
    const messageIdInput = `${authorId}:${lamportTimestamp}:${counter}:${contentHash}`;
    const messageId = crypto
      .createHash('sha256')
      .update(messageIdInput)
      .digest('hex')
      .substring(0, 32);

    return {
      messageId,
      authorId,
      lamportTimestamp,
      localCounter: counter,
      contentHash
    };
  }

  /**
   * Check if a MessageID already exists
   */
  messageExists(messageId: string): boolean {
    return this.knownMessages.has(messageId);
  }

  /**
   * Register a message as known (to prevent duplicates)
   */
  registerMessage(messageId: string): void {
    this.knownMessages.add(messageId);
  }

  /**
   * Merge a temporary offline author with their real online identity
   *
   * When a device connects online and we can link it to a real account:
   * 1. Update all references from tempAuthorId to realAuthorId
   * 2. Preserve original MessageIDs (no new messages created)
   * 3. Update author attribution deterministically
   */
  mergeAuthor(tempAuthorId: string, realAuthorId: string, realName: string): void {
    const tempAuthor = this.authors.get(tempAuthorId);

    if (!tempAuthor || !tempAuthorId.startsWith('temp_')) {
      console.warn(`[IdentityResolver] Cannot merge non-temp author: ${tempAuthorId}`);
      return;
    }

    // Get or create real author
    let realAuthor = this.authors.get(realAuthorId);
    if (!realAuthor) {
      realAuthor = {
        authorId: realAuthorId,
        displayName: realName,
        accountId: realAuthorId,
        deviceIds: []
      };
      this.authors.set(realAuthorId, realAuthor);
    }

    // Transfer device mappings
    for (const deviceId of tempAuthor.deviceIds) {
      this.deviceToAuthor.set(deviceId, realAuthorId);
      if (!realAuthor.deviceIds.includes(deviceId)) {
        realAuthor.deviceIds.push(deviceId);
      }
    }

    // Record merge mapping
    this.tempToRealAuthor.set(tempAuthorId, realAuthorId);

    // Remove temporary author
    this.authors.delete(tempAuthorId);

    console.log(`[IdentityResolver] Merged ${tempAuthorId} → ${realAuthorId} (${tempAuthor.deviceIds.length} devices)`);
  }

  /**
   * Link a device UUID to an author
   */
  private linkDeviceToAuthor(deviceId: string, authorId: string): void {
    this.deviceToAuthor.set(deviceId, authorId);

    const author = this.authors.get(authorId);
    if (author && !author.deviceIds.includes(deviceId)) {
      author.deviceIds.push(deviceId);
    }
  }

  /**
   * Create a new author identity
   */
  private createAuthor(
    authorId: string,
    displayName: string,
    accountId?: string,
    deviceIds: string[] = []
  ): void {
    this.authors.set(authorId, {
      authorId,
      displayName,
      accountId,
      deviceIds
    });

    console.log(`[IdentityResolver] Created author: ${authorId} (${displayName})`);
  }

  /**
   * Get and increment counter for an author
   */
  private getNextCounter(authorId: string): number {
    const current = this.authorCounters.get(authorId) || 0;
    const next = current + 1;
    this.authorCounters.set(authorId, next);
    return next;
  }

  /**
   * Get author identity
   */
  getAuthor(authorId: string): AuthorIdentity | undefined {
    return this.authors.get(authorId);
  }

  /**
   * Get all known authors
   */
  getAllAuthors(): AuthorIdentity[] {
    return Array.from(this.authors.values());
  }
}

export const identityResolver = new IdentityResolver();
