import { z } from 'zod';

export const ProfileQuery = z.object({
  q: z.string().trim().min(2).max(100),
}).strict();
export type ProfileQuery = z.infer<typeof ProfileQuery>;
