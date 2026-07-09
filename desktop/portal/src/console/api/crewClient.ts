// desktop/portal/src/console/api/crewClient.ts
import { httpCall } from './httpCall';

export type CrewRole = 'solo' | 'team' | 'lead' | 'foreman' | 'enterprise' | 'admin';
export type CrewActiveJobStatus = 'planned' | 'in_progress' | 'complete' | 'cancelled';

export interface CrewActiveJob {
  id: string;
  title: string;
  status: CrewActiveJobStatus;
}

export interface CrewEntry {
  id: string;
  email: string;
  displayName: string;
  role: CrewRole;
  activeJob: CrewActiveJob | null;
}

export type CrewResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string; code?: string };

interface RosterResp { crew: CrewEntry[] }

export const crewClient = {
  getRoster: async (): Promise<CrewResult<RosterResp>> => {
    const r = await httpCall<RosterResp>('/api/profiles/crew');
    if (!r.ok) {
      return { ok: false, status: r.status, error: r.error, code: r.body?.code };
    }
    return { ok: true, ...r.data };
  },
};
