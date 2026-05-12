// desktop/portal/src/console/api/profilesClient.ts
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
    const res = await fetch(`/api/profiles?q=${encodeURIComponent(q)}`, {
      credentials: 'include',
    });
    if (!res.ok) {
      const errBody = await res.json().catch(() => ({ error: res.statusText }));
      return {
        ok: false,
        status: res.status,
        error: errBody.error || 'Request failed',
        details: errBody.details,
        code: errBody.code,
      };
    }
    const data = (await res.json()) as SearchResp;
    return { ok: true, ...data };
  },
};
