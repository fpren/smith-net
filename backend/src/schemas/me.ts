import { z } from 'zod';

export const StartTrialBody = z.object({ tier: z.enum(['solo', 'advanced']) }).strict();
export type StartTrialBody = z.infer<typeof StartTrialBody>;
