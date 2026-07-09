// desktop/portal/src/console/api/profilesClient.ts
import { httpCall } from './httpCall';

export type ProfileRole = 'solo' | 'team' | 'lead' | 'foreman' | 'enterprise' | 'admin';

export interface ProfileMatch {
  id: string;
  email: string;
  displayName: string;
  role: ProfileRole;
}

export type ProfilesResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string; details?: any; code?: string };

interface SearchResp { profiles: ProfileMatch[] }

export const profilesClient = {
  search: async (q: string): Promise<ProfilesResult<SearchResp>> => {
    const r = await httpCall<SearchResp>(`/api/profiles?q=${encodeURIComponent(q)}`);
    if (!r.ok) {
      const errBody = r.body ?? {};
      return {
        ok: false,
        status: r.status,
        error: r.error,
        details: errBody.details,
        code: errBody.code,
      };
    }
    return { ok: true, ...r.data };
  },
};
