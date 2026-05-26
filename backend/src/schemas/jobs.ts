import { z } from 'zod';

export const CreateJobBody = z.object({
  title:        z.string().trim().min(1).max(200),
  description:  z.string().trim().max(5000).optional(),
  scheduledAt:  z.string().datetime().optional(),
  location:     z.string().trim().max(500).optional(),
  clientId:     z.string().uuid().optional(),
  engagementId: z.string().uuid().optional(),
}).strict();
export type CreateJobBody = z.infer<typeof CreateJobBody>;

export const UpdateJobBody = z.object({
  title:       z.string().trim().min(1).max(200).optional(),
  description: z.string().trim().max(5000).optional().nullable(),
  scheduledAt: z.string().datetime().optional().nullable(),
  location:    z.string().trim().max(500).optional().nullable(),
  clientId:    z.string().uuid().optional().nullable(),
}).strict();
export type UpdateJobBody = z.infer<typeof UpdateJobBody>;

export const StatusChangeBody = z.object({
  status: z.enum(['planned', 'in_progress', 'complete', 'cancelled']),
}).strict();
export type StatusChangeBody = z.infer<typeof StatusChangeBody>;

export const AssignCrewBody = z.object({
  profileId: z.string().min(1).max(100),
  roleOnJob: z.enum(['crew', 'lead']).optional(),
}).strict();
export type AssignCrewBody = z.infer<typeof AssignCrewBody>;

export const StageChangeBody = z.object({
  stage: z.enum(['lead','proposal','approved','in_progress','review','invoice','closed']),
}).strict();
export type StageChangeBody = z.infer<typeof StageChangeBody>;
