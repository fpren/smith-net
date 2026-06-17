// desktop/portal/src/console/hooks/useDirectory.ts
// Fetches the caller's teammates once and exposes them as a list + by-id map.
// Used to enrich DM headers/rows with real photos + ids for same-org peers
// (cross-org peers gracefully fall back to initials). Module-cached.

import { useEffect, useState } from 'react';
import { commClient, type Profile } from '../api/commClient';

let cache: Profile[] | null = null;
const listeners = new Set<(p: Profile[]) => void>();
let inFlight = false;

export interface Directory {
  list: Profile[];
  byId: Record<string, Profile>;
}

function toDirectory(list: Profile[]): Directory {
  const byId: Record<string, Profile> = {};
  for (const p of list) byId[p.id] = p;
  return { list, byId };
}

export function useDirectory(): Directory {
  const [list, setList] = useState<Profile[]>(cache ?? []);

  useEffect(() => {
    listeners.add(setList);
    if (!cache && !inFlight) {
      inFlight = true;
      commClient.listTeammates().then((r) => {
        inFlight = false;
        if (r.ok) {
          cache = r.profiles;
          listeners.forEach((l) => l(r.profiles));
        }
      });
    }
    return () => { listeners.delete(setList); };
  }, []);

  return toDirectory(list);
}
