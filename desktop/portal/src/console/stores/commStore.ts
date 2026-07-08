// desktop/portal/src/console/stores/commStore.ts
import { create } from 'zustand';
import type { Channel, Message, Presence } from '../../types';

const TYPING_TTL_MS = 5_000;

interface TypingEntry {
  name: string;
  expiresAt: number;
}

interface CommState {
  channels: Channel[];
  selectedChannelId: string | null;
  messagesByChannel: Record<string, Message[]>;
  // userId → name+expiry, keyed by channelId. Auto-expires; the WS hook runs
  // sweepTyping() on an interval to evict stale entries.
  typingByChannel: Record<string, Record<string, TypingEntry>>;
  // messageId → set of userIds who have read it.
  readByMessage: Record<string, Set<string>>;
  // userId → latest presence (online/away/offline). Fed by the WS presence_update.
  presenceByUser: Record<string, Presence>;
  // channelId → count of unread incoming messages (reset when selected).
  unreadByChannel: Record<string, number>;
  // channelId → most recent message (for the activity-feed preview + sort).
  lastMessageByChannel: Record<string, Message>;
  isLoadingChannels: boolean;
  isLoadingMessages: boolean;
  isStaleChannels: boolean;
  isStaleMessages: boolean;

  setChannels: (channels: Channel[]) => void;
  selectChannel: (id: string | null) => void;
  setMessages: (channelId: string, msgs: Message[]) => void;
  appendMessage: (msg: Message) => void;
  updateMessage: (channelId: string, messageId: string, patch: Partial<Message>) => void;
  addChannel: (channel: Channel) => void;
  removeChannel: (channelId: string) => void;
  clearChannelMessages: (channelId: string) => void;
  removeMessage: (channelId: string, messageId: string) => void;
  setTyping: (channelId: string, userId: string, userName: string, isTyping: boolean) => void;
  sweepTyping: () => void;
  markRead: (messageId: string, readBy: string) => void;
  setPresence: (list: Presence[]) => void;
  clearUnread: (channelId: string) => void;
  markLoadingChannels: (b: boolean) => void;
  markLoadingMessages: (b: boolean) => void;
  markStaleChannels: (b: boolean) => void;
  markStaleMessages: (b: boolean) => void;
  clear: () => void;
}

export const useCommStore = create<CommState>((set) => ({
  channels: [],
  selectedChannelId: null,
  messagesByChannel: {},
  typingByChannel: {},
  readByMessage: {},
  presenceByUser: {},
  unreadByChannel: {},
  lastMessageByChannel: {},
  isLoadingChannels: false,
  isLoadingMessages: false,
  isStaleChannels: false,
  isStaleMessages: false,

  setChannels: (channels) => set({ channels, isStaleChannels: false }),
  selectChannel: (selectedChannelId) =>
    set((s) => ({
      selectedChannelId,
      // Opening a channel clears its unread count.
      unreadByChannel: selectedChannelId
        ? { ...s.unreadByChannel, [selectedChannelId]: 0 }
        : s.unreadByChannel,
    })),
  setMessages: (channelId, msgs) =>
    set((s) => {
      const last = msgs.length ? msgs[msgs.length - 1] : undefined;
      return {
        messagesByChannel: { ...s.messagesByChannel, [channelId]: msgs },
        lastMessageByChannel: last
          ? { ...s.lastMessageByChannel, [channelId]: last }
          : s.lastMessageByChannel,
        isStaleMessages: false,
      };
    }),
  appendMessage: (msg) =>
    set((s) => {
      const existing = s.messagesByChannel[msg.channelId] ?? [];
      if (existing.some((m) => m.id === msg.id)) return {};
      const next = [...existing, msg].sort((a, b) => a.timestamp - b.timestamp);
      // Count as unread unless this channel is currently open.
      const isOpen = s.selectedChannelId === msg.channelId;
      const prevUnread = s.unreadByChannel[msg.channelId] ?? 0;
      return {
        messagesByChannel: { ...s.messagesByChannel, [msg.channelId]: next },
        lastMessageByChannel: { ...s.lastMessageByChannel, [msg.channelId]: msg },
        unreadByChannel: isOpen
          ? s.unreadByChannel
          : { ...s.unreadByChannel, [msg.channelId]: prevUnread + 1 },
      };
    }),
  updateMessage: (channelId, messageId, patch) =>
    set((state) => {
      const list = state.messagesByChannel[channelId];
      if (!list?.some((m) => m.id === messageId)) return state;
      const next = list
        .map((m) => (m.id === messageId ? { ...m, ...patch } : m))
        .sort((a, b) => a.timestamp - b.timestamp);
      return { messagesByChannel: { ...state.messagesByChannel, [channelId]: next } };
    }),
  addChannel: (channel) =>
    set((s) => {
      if (s.channels.some((c) => c.id === channel.id)) return {};
      return { channels: [channel, ...s.channels] };
    }),
  removeChannel: (channelId) =>
    set((s) => {
      const { [channelId]: _dropped, ...remainingMessages } = s.messagesByChannel;
      const { [channelId]: _dt, ...remainingTyping } = s.typingByChannel;
      return {
        channels: s.channels.filter((c) => c.id !== channelId),
        messagesByChannel: remainingMessages,
        typingByChannel: remainingTyping,
        selectedChannelId: s.selectedChannelId === channelId ? null : s.selectedChannelId,
      };
    }),
  clearChannelMessages: (channelId) =>
    set((s) => ({
      messagesByChannel: { ...s.messagesByChannel, [channelId]: [] },
    })),
  removeMessage: (channelId, messageId) =>
    set((s) => {
      const existing = s.messagesByChannel[channelId];
      if (!existing) return {};
      return {
        messagesByChannel: {
          ...s.messagesByChannel,
          [channelId]: existing.filter((m) => m.id !== messageId),
        },
      };
    }),
  setTyping: (channelId, userId, userName, isTyping) =>
    set((s) => {
      const channelTyping = { ...(s.typingByChannel[channelId] ?? {}) };
      if (isTyping) {
        channelTyping[userId] = { name: userName, expiresAt: Date.now() + TYPING_TTL_MS };
      } else {
        delete channelTyping[userId];
      }
      return {
        typingByChannel: { ...s.typingByChannel, [channelId]: channelTyping },
      };
    }),
  sweepTyping: () =>
    set((s) => {
      const now = Date.now();
      let changed = false;
      const next: Record<string, Record<string, TypingEntry>> = {};
      for (const [channelId, channelTyping] of Object.entries(s.typingByChannel)) {
        const survivors: Record<string, TypingEntry> = {};
        for (const [userId, entry] of Object.entries(channelTyping)) {
          if (entry.expiresAt > now) {
            survivors[userId] = entry;
          } else {
            changed = true;
          }
        }
        next[channelId] = survivors;
      }
      return changed ? { typingByChannel: next } : {};
    }),
  markRead: (messageId, readBy) =>
    set((s) => {
      const existing = s.readByMessage[messageId] ?? new Set<string>();
      if (existing.has(readBy)) return {};
      const updated = new Set(existing);
      updated.add(readBy);
      return { readByMessage: { ...s.readByMessage, [messageId]: updated } };
    }),
  setPresence: (list) =>
    set((s) => {
      const next = { ...s.presenceByUser };
      for (const p of list) next[p.userId] = p;
      return { presenceByUser: next };
    }),
  clearUnread: (channelId) =>
    set((s) => ({ unreadByChannel: { ...s.unreadByChannel, [channelId]: 0 } })),
  markLoadingChannels: (isLoadingChannels) => set({ isLoadingChannels }),
  markLoadingMessages: (isLoadingMessages) => set({ isLoadingMessages }),
  markStaleChannels: (isStaleChannels) => set({ isStaleChannels }),
  markStaleMessages: (isStaleMessages) => set({ isStaleMessages }),
  clear: () =>
    set({
      channels: [],
      selectedChannelId: null,
      messagesByChannel: {},
      typingByChannel: {},
      readByMessage: {},
      presenceByUser: {},
      unreadByChannel: {},
      lastMessageByChannel: {},
      isLoadingChannels: false,
      isLoadingMessages: false,
      isStaleChannels: false,
      isStaleMessages: false,
    }),
}));
