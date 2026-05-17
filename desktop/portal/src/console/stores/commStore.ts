// desktop/portal/src/console/stores/commStore.ts
import { create } from 'zustand';
import type { Channel, Message } from '../../types';

interface CommState {
  channels: Channel[];
  selectedChannelId: string | null;
  messagesByChannel: Record<string, Message[]>;
  isLoadingChannels: boolean;
  isLoadingMessages: boolean;
  isStaleChannels: boolean;
  isStaleMessages: boolean;

  setChannels: (channels: Channel[]) => void;
  selectChannel: (id: string | null) => void;
  setMessages: (channelId: string, msgs: Message[]) => void;
  appendMessage: (msg: Message) => void;
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
  isLoadingChannels: false,
  isLoadingMessages: false,
  isStaleChannels: false,
  isStaleMessages: false,

  setChannels: (channels) => set({ channels, isStaleChannels: false }),
  selectChannel: (selectedChannelId) => set({ selectedChannelId }),
  setMessages: (channelId, msgs) =>
    set((s) => ({
      messagesByChannel: { ...s.messagesByChannel, [channelId]: msgs },
      isStaleMessages: false,
    })),
  appendMessage: (msg) =>
    set((s) => {
      const existing = s.messagesByChannel[msg.channelId] ?? [];
      if (existing.some((m) => m.id === msg.id)) return {};
      const next = [...existing, msg].sort((a, b) => a.timestamp - b.timestamp);
      return {
        messagesByChannel: { ...s.messagesByChannel, [msg.channelId]: next },
      };
    }),
  markLoadingChannels: (isLoadingChannels) => set({ isLoadingChannels }),
  markLoadingMessages: (isLoadingMessages) => set({ isLoadingMessages }),
  markStaleChannels: (isStaleChannels) => set({ isStaleChannels }),
  markStaleMessages: (isStaleMessages) => set({ isStaleMessages }),
  clear: () =>
    set({
      channels: [],
      selectedChannelId: null,
      messagesByChannel: {},
      isLoadingChannels: false,
      isLoadingMessages: false,
      isStaleChannels: false,
      isStaleMessages: false,
    }),
}));
