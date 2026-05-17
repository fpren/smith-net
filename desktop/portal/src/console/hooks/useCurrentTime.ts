// desktop/portal/src/console/hooks/useCurrentTime.ts
//
// Wall-clock tick. Updates state once per second. Used by AppHeader's
// big clock display. Cheap; no API calls.

import { useEffect, useState } from 'react';

export interface CurrentTime {
  hh: string;
  mm: string;
  ss: string;
}

function snapshot(): CurrentTime {
  const d = new Date();
  return {
    hh: String(d.getHours()).padStart(2, '0'),
    mm: String(d.getMinutes()).padStart(2, '0'),
    ss: String(d.getSeconds()).padStart(2, '0'),
  };
}

export function useCurrentTime(): CurrentTime {
  const [now, setNow] = useState<CurrentTime>(snapshot);
  useEffect(() => {
    const id = setInterval(() => setNow(snapshot()), 1000);
    return () => clearInterval(id);
  }, []);
  return now;
}
