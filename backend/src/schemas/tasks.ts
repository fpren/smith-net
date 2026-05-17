import { z } from 'zod';

export const CreateTaskBody = z.object({
  jobId: z.string().uuid(),
  title: z.string().trim().min(1).max(500),
}).strict();
export type CreateTaskBody = z.infer<typeof CreateTaskBody>;

export const UpdateTaskBody = z.object({
  title:     z.string().trim().min(1).max(500).optional(),
  status:    z.enum(['pending', 'done']).optional(),
  sortOrder: z.number().int().min(0).optional(),
}).strict();
export type UpdateTaskBody = z.infer<typeof UpdateTaskBody>;
