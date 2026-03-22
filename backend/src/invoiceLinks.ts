/**
 * Invoice Links Service
 * Creates shareable invoice web pages for clients
 */

import { supabase } from './supabase';

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

export class InvoiceLinkService {
  async createInvoiceLink(data: InvoiceLinkData): Promise<{ uuid: string } | null> {
    const { data: result, error } = await supabase
      .from('invoice_links')
      .insert({
        job_id: data.jobId,
        contractor_name: data.contractorName,
        contractor_phone: data.contractorPhone,
        contractor_license: data.contractorLicense,
        client_name: data.clientName,
        client_address: data.clientAddress,
        work_summary: data.workSummary,
        hours_worked: data.hoursWorked ?? 0,
        hourly_rate: data.hourlyRate ?? 0,
        labor_cost: data.laborCost ?? 0,
        materials: data.materials ?? [],
        materials_cost: data.materialsCost ?? 0,
        total_due: data.totalDue ?? 0,
        payment_info: data.paymentInfo,
        status: 'unpaid',
      })
      .select('uuid')
      .single();

    if (error) {
      console.error('[InvoiceLinks] Create error:', error);
      return null;
    }

    return { uuid: result.uuid };
  }

  async getByUuid(uuid: string): Promise<any | null> {
    const { data, error } = await supabase
      .from('invoice_links')
      .select('*')
      .eq('uuid', uuid)
      .single();

    if (error || !data) return null;
    return data;
  }
}

export const invoiceLinkService = new InvoiceLinkService();
