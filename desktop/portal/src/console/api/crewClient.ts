// desktop/portal/src/console/api/crewClient.ts
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
    const res = await fetch('/api/profiles/crew', { credentials: 'include' });
    if (!res.ok) {
      const errBody = await res.json().catch(() => ({ error: res.statusText }));
      return { ok: false, status: res.status, error: errBody.error || 'Failed', code: errBody.code };
    }
    const data = (await res.json()) as RosterResp;
    return { ok: true, ...data };
  },
};
