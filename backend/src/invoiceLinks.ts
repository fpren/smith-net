/**
 * Invoice Links Service
 * Creates shareable invoice web pages for clients
 */

import { pg, isPgEnabled } from './db';

export interface InvoiceMaterial {
  name: string;
  quantity: number;
  unit: string;
  unitCost: number;
  totalCost: number;
}

export interface InvoiceLinkData {
  jobId: string;
  contractorName?: string;
  contractorPhone?: string;
  contractorLicense?: string;
  clientName?: string;
  clientAddress?: string;
  workSummary?: string;
  hoursWorked?: number;
  hourlyRate?: number;
  laborCost?: number;
  materials?: InvoiceMaterial[];
  materialsCost?: number;
  totalDue?: number;
  paymentInfo?: string;
}

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[InvoiceLinks] Postgres client not initialized');
  return pg;
}

export class InvoiceLinkService {
  async createInvoiceLink(data: InvoiceLinkData): Promise<{ uuid: string } | null> {
    const db = requirePg();
    try {
      const { rows } = await db.query(
        `INSERT INTO invoice_links
           (job_id, contractor_name, contractor_phone, contractor_license,
            client_name, client_address, work_summary,
            hours_worked, hourly_rate, labor_cost,
            materials, materials_cost, total_due,
            payment_info, status)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11::jsonb,$12,$13,$14,'unpaid')
         RETURNING uuid`,
        [
          data.jobId, data.contractorName ?? null, data.contractorPhone ?? null, data.contractorLicense ?? null,
          data.clientName ?? null, data.clientAddress ?? null, data.workSummary ?? null,
          data.hoursWorked ?? 0, data.hourlyRate ?? 0, data.laborCost ?? 0,
          JSON.stringify(data.materials ?? []), data.materialsCost ?? 0, data.totalDue ?? 0,
          data.paymentInfo ?? null,
        ]
      );
      return rows.length ? { uuid: rows[0].uuid } : null;
    } catch (e) {
      console.error('[InvoiceLinks] Create error:', e);
      return null;
    }
  }

  async getByUuid(uuid: string): Promise<any | null> {
    const db = requirePg();
    const { rows } = await db.query(`SELECT * FROM invoice_links WHERE uuid = $1`, [uuid]);
    return rows.length ? rows[0] : null;
  }
}

export const invoiceLinkService = new InvoiceLinkService();
