import { supabase } from './supabase';

export interface ProposalData {
  jobId: string;
  contractorName: string;
  contractorPhone: string;
  contractorLicense: string;
  clientName: string;
  clientAddress: string;
  scope: string;
  tasks: string[];
  materials: Array<{ name: string; quantity: number; unit: string; unitCost: number; totalCost: number }>;
  equipment: string[];
  laborHours: number;
  laborRate: number;
  laborCost: number;
  materialsCost: number;
  totalCost: number;
}

export class ProposalService {
  async createProposal(data: ProposalData): Promise<{ uuid: string } | null> {
    const { data: result, error } = await supabase
      .from('proposals')
      .insert({
        job_id: data.jobId,
        contractor_name: data.contractorName,
        contractor_phone: data.contractorPhone,
        contractor_license: data.contractorLicense,
        client_name: data.clientName,
        client_address: data.clientAddress,
        scope: data.scope,
        tasks: data.tasks,
        materials: data.materials,
        equipment: data.equipment,
        labor_hours: data.laborHours,
        labor_rate: data.laborRate,
        labor_cost: data.laborCost,
        materials_cost: data.materialsCost,
        total_cost: data.totalCost,
        status: 'pending'
      })
      .select('uuid')
      .single();

    if (error) return null;
    return { uuid: result.uuid };
  }

  async getByUuid(uuid: string): Promise<any | null> {
    const { data, error } = await supabase
      .from('proposals')
      .select('*')
      .eq('uuid', uuid)
      .single();

    if (error || !data) return null;
    if (data.status === 'revoked') return null;
    if (new Date(data.expires_at) < new Date()) return { ...data, expired: true };
    return data;
  }

  async respond(uuid: string, action: 'approve' | 'decline', clientName: string, notes?: string): Promise<boolean> {
    const proposal = await this.getByUuid(uuid);
    if (!proposal || proposal.expired) return false;

    // Verify client name matches (case-insensitive)
    if (proposal.client_name.toLowerCase().trim() !== clientName.toLowerCase().trim()) {
      return false;
    }

    const { error } = await supabase
      .from('proposals')
      .update({
        status: action === 'approve' ? 'approved' : 'declined',
        client_response: action,
        client_notes: notes || null,
        updated_at: new Date().toISOString()
      })
      .eq('uuid', uuid);

    return !error;
  }

  async revoke(uuid: string): Promise<boolean> {
    const { error } = await supabase
      .from('proposals')
      .update({ status: 'revoked', updated_at: new Date().toISOString() })
      .eq('uuid', uuid);
    return !error;
  }
}

export const proposalService = new ProposalService();
