// backend/src/schemas/clients.ts
import { z } from 'zod';

export const CreateClientBody = z.object({
  name:    z.string().trim().min(1).max(200),
  email:   z.string().trim().max(200).optional(),
  phone:   z.string().trim().max(50).optional(),
  address: z.string().trim().max(500).optional(),
  company: z.string().trim().max(200).optional(),
  notes:   z.string().trim().max(5000).optional(),
}).strict();
export type CreateClientBody = z.infer<typeof CreateClientBody>;

export const UpdateClientBody = z.object({
  name:    z.string().trim().min(1).max(200).optional(),
  email:   z.string().trim().max(200).optional().nullable(),
  phone:   z.string().trim().max(50).optional().nullable(),
  address: z.string().trim().max(500).optional().nullable(),
  company: z.string().trim().max(200).optional().nullable(),
  notes:   z.string().trim().max(5000).optional().nullable(),
}).strict();
export type UpdateClientBody = z.infer<typeof UpdateClientBody>;
