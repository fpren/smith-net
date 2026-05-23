import { z } from 'zod';

export const CreateInvoiceBody = z.object({
  clientName:     z.string().trim().max(200).optional(),
  clientEmail:    z.string().trim().email().max(200).optional(),
  dueDate:        z.string().datetime().optional(),
  notes:          z.string().trim().max(5000).optional(),
  idempotencyKey: z.string().trim().min(1).max(128).optional(),
  summary:        z.unknown().optional(),
  taxRate:        z.number().min(0).max(1).optional(),
}).strict();
export type CreateInvoiceBody = z.infer<typeof CreateInvoiceBody>;

export const UpdateInvoiceBody = z.object({
  clientName:  z.string().trim().max(200).nullable().optional(),
  clientEmail: z.string().trim().email().max(200).nullable().optional(),
  dueDate:     z.string().datetime().nullable().optional(),
  taxRate:     z.number().min(0).max(1).optional(),   // 0 → 1 (i.e. 100%)
  notes:       z.string().trim().max(5000).nullable().optional(),
}).strict();
export type UpdateInvoiceBody = z.infer<typeof UpdateInvoiceBody>;

export const SetStatusBody = z.object({
  status: z.enum(['draft', 'issued', 'sent', 'viewed', 'paid', 'overdue', 'disputed', 'cancelled']),
}).strict();
export type SetStatusBody = z.infer<typeof SetStatusBody>;

export const AddLineItemBody = z.object({
  description:  z.string().trim().min(1).max(500),
  quantity:     z.number().positive().max(1_000_000).optional(),
  unit:         z.string().trim().min(1).max(20).optional(),
  rate:         z.number().min(0).max(1_000_000),
  category:     z.enum(['labor', 'materials', 'travel', 'change_order', 'other']).optional(),
  clientItemId: z.string().trim().min(1).max(128).optional(),
}).strict();
export type AddLineItemBody = z.infer<typeof AddLineItemBody>;

export const UpdateLineItemBody = z.object({
  description: z.string().trim().min(1).max(500).optional(),
  quantity:    z.number().positive().max(1_000_000).optional(),
  unit:        z.string().trim().min(1).max(20).optional(),
  rate:        z.number().min(0).max(1_000_000).optional(),
  category:    z.enum(['labor', 'materials', 'travel', 'change_order', 'other']).optional(),
  sortOrder:   z.number().int().min(0).optional(),
}).strict();
export type UpdateLineItemBody = z.infer<typeof UpdateLineItemBody>;

export const SendInvoiceBody = z.object({}).strict();
export type SendInvoiceBody = z.infer<typeof SendInvoiceBody>;
