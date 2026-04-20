import { pg, isPgEnabled } from './db';

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

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[Proposals] Postgres client not initialized');
  return pg;
}

export class ProposalService {
  async createProposal(data: ProposalData): Promise<{ uuid: string } | null> {
    const db = requirePg();
    try {
      const { rows } = await db.query(
        `INSERT INTO proposals
           (job_id, contractor_name, contractor_phone, contractor_license,
            client_name, client_address, scope,
            tasks, materials, equipment,
            labor_hours, labor_rate, labor_cost,
            materials_cost, total_cost, status)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb,$9::jsonb,$10::jsonb,$11,$12,$13,$14,$15,'pending')
         RETURNING uuid`,
        [
          data.jobId, data.contractorName, data.contractorPhone, data.contractorLicense,
          data.clientName, data.clientAddress, data.scope,
          JSON.stringify(data.tasks), JSON.stringify(data.materials), JSON.stringify(data.equipment),
          data.laborHours, data.laborRate, data.laborCost,
          data.materialsCost, data.totalCost,
        ]
      );
      return rows.length ? { uuid: rows[0].uuid } : null;
    } catch (e) {
      console.error('[Proposals] createProposal error:', e);
      return null;
    }
  }

  async getByUuid(uuid: string): Promise<any | null> {
    const db = requirePg();
    const { rows } = await db.query(`SELECT * FROM proposals WHERE uuid = $1`, [uuid]);
    if (rows.length === 0) return null;
    const data = rows[0];
    if (data.status === 'revoked') return null;
    if (data.expires_at && new Date(data.expires_at) < new Date()) return { ...data, expired: true };
    return data;
  }

  async respond(uuid: string, action: 'approve' | 'decline', clientName: string, notes?: string): Promise<boolean> {
    const db = requirePg();
    const proposal = await this.getByUuid(uuid);
    if (!proposal || proposal.expired) return false;
    if (proposal.client_name.toLowerCase().trim() !== clientName.toLowerCase().trim()) return false;

    const status = action === 'approve' ? 'approved' : 'declined';
    await db.query(
      `UPDATE proposals
          SET status = $1, client_response = $2, client_notes = $3, updated_at = NOW()
        WHERE uuid = $4`,
      [status, action, notes || null, uuid]
    );
    return true;
  }

  async revoke(uuid: string): Promise<boolean> {
    const db = requirePg();
    const { rowCount } = await db.query(
      `UPDATE proposals SET status = 'revoked', updated_at = NOW() WHERE uuid = $1`,
      [uuid]
    );
    return (rowCount ?? 0) > 0;
  }
}

export const proposalService = new ProposalService();
