import { z } from 'zod';

export const CreateMaterialBody = z.object({
  jobId: z.string().uuid(),
  name: z.string().min(1).max(200),
  notes: z.string().max(2000).optional(),
  quantity: z.number().nonnegative().optional(),
  unit: z.string().min(1).max(20).optional(),
  unitCost: z.number().nonnegative().optional(),
  vendor: z.string().max(200).optional(),
}).strict();
export type CreateMaterialBody = z.infer<typeof CreateMaterialBody>;

export const UpdateMaterialBody = z.object({
  name: z.string().min(1).max(200).optional(),
  notes: z.string().max(2000).nullable().optional(),
  quantity: z.number().nonnegative().optional(),
  unit: z.string().min(1).max(20).optional(),
  unitCost: z.number().nonnegative().optional(),
  vendor: z.string().max(200).nullable().optional(),
  checked: z.boolean().optional(),
}).strict();
export type UpdateMaterialBody = z.infer<typeof UpdateMaterialBody>;
