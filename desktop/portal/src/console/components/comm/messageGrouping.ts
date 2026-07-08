// desktop/portal/src/console/components/comm/messageGrouping.ts
//
// Pure grouping function for MessageRow rendering. A message starts a new
// group (renders avatar + sender name + time header) when any of:
//   - it's the first message in the list
//   - the sender changed from the previous message
//   - more than 7 minutes (420_000 ms) elapsed since the previous message
//   - the calendar day changed from the previous message
import type { Message } from '../../../types';

const GROUP_GAP_MS = 420_000; // 7 minutes

export interface GroupedRow {
  message: Message;
  firstOfGroup: boolean;
}

function isSameCalendarDay(a: number, b: number): boolean {
  const da = new Date(a);
  const db = new Date(b);
  return (
    da.getFullYear() === db.getFullYear() &&
    da.getMonth() === db.getMonth() &&
    da.getDate() === db.getDate()
  );
}

export function groupMessages(messages: Message[]): GroupedRow[] {
  return messages.map((message, index) => {
    if (index === 0) {
      return { message, firstOfGroup: true };
    }
    const prev = messages[index - 1];
    const senderChanged = prev.senderId !== message.senderId;
    const gapExceeded = message.timestamp - prev.timestamp > GROUP_GAP_MS;
    const dayChanged = !isSameCalendarDay(prev.timestamp, message.timestamp);
    return { message, firstOfGroup: senderChanged || gapExceeded || dayChanged };
  });
}
