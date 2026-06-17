// desktop/portal/src/console/hooks/useMyProfile.ts
// Fetches the caller's own profile (public id + avatar) — these are not on the
// auth user object, so the comm rail and Settings both read them via GET
// /api/profiles/me. Cached module-level so multiple consumers share one fetch.

import { useEffect, useState } from 'react';
import { commClient, type Profile } from '../api/commClient';

let cached: Profile | null = null;
const listeners = new Set<(p: Profile | null) => void>();
let inFlight = false;

export function setMyProfile(p: Profile | null) {
  cached = p;
  listeners.forEach((l) => l(p));
}

export function useMyProfile(): Profile | null {
  const [profile, setProfile] = useState<Profile | null>(cached);

  useEffect(() => {
    listeners.add(setProfile);
    if (!cached && !inFlight) {
      inFlight = true;
      commClient.getMe().then((r) => {
        inFlight = false;
        if (r.ok) setMyProfile(r.profile);
      });
    }
    return () => {
      listeners.delete(setProfile);
    };
  }, []);

  return profile;
}
