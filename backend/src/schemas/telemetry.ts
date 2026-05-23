import { z } from 'zod';
import { ALLOWED_EVENTS, hasPiiKey } from '../telemetryService';

// Spread to a mutable tuple: z.enum rejects a readonly `as const` array.
const eventEnum = z.enum([...ALLOWED_EVENTS] as [string, ...string[]]);

export const GateHitBody = z.object({
  event: eventEnum,
  metadata: z
    .record(z.string(), z.union([z.string(), z.number(), z.boolean()]))
    .refine((m) => !hasPiiKey(m), { message: 'metadata may not contain PII keys' })
    .optional(),
}).strict();
export type GateHitBody = z.infer<typeof GateHitBody>;
