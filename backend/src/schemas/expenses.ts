import { z } from 'zod';

const ISO_DATE = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'expected YYYY-MM-DD');

export const CreateExpenseBody = z.object({
  jobId: z.string().uuid(),
  category: z.string().min(1).max(60),
  description: z.string().min(1).max(500),
  amount: z.number().nonnegative(),
  vendor: z.string().max(200).optional(),
  notes: z.string().max(2000).optional(),
  expenseDate: ISO_DATE.optional(),
}).strict();
export type CreateExpenseBody = z.infer<typeof CreateExpenseBody>;

export const UpdateExpenseBody = z.object({
  category: z.string().min(1).max(60).optional(),
  description: z.string().min(1).max(500).optional(),
  amount: z.number().nonnegative().optional(),
  vendor: z.string().max(200).nullable().optional(),
  notes: z.string().max(2000).nullable().optional(),
  expenseDate: ISO_DATE.nullable().optional(),
}).strict();
export type UpdateExpenseBody = z.infer<typeof UpdateExpenseBody>;
