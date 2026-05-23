import { z } from 'zod';
import { FOUNDER_BONUS_IDS } from '../founderSeatService';

// Spread to a mutable tuple: z.enum rejects a readonly `as const` array.
export const ReserveFounderSeatBody = z.object({
  bonusId: z.enum([...FOUNDER_BONUS_IDS] as [string, ...string[]]),
}).strict();
export type ReserveFounderSeatBody = z.infer<typeof ReserveFounderSeatBody>;
