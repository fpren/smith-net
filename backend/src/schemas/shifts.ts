/**
 * Clock parity: zod schemas for /api/shifts/{start,end}. .strict() rejects
 * unknown fields (mass-assignment defense).
 */
import { z } from 'zod';

export const ENTRY_TYPES = ['regular', 'overtime', 'break', 'travel', 'on_call'] as const;

export const StartShiftBody = z
  .object({
    source: z.enum(['android', 'web', 'admin']).optional(),
    entryType: z.enum(ENTRY_TYPES).optional(),
    jobId: z.string().uuid().optional(),
    jobTitle: z.string().trim().min(1).max(200).optional(),
    taskId: z.string().uuid().optional(),
    taskTitle: z.string().trim().min(1).max(200).optional(),
  })
  .strict();
export type StartShiftBody = z.infer<typeof StartShiftBody>;

export const EndShiftBody = z
  .object({
    reason: z.string().trim().min(1).max(500).optional(),
  })
  .strict();
export type EndShiftBody = z.infer<typeof EndShiftBody>;
