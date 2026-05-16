import type { CrewPosition } from '../../api/crewPositionsClient';

export function createCrewMarkerElement(p: CrewPosition): HTMLDivElement {
  const el = document.createElement('div');
  el.className = 'crew-marker';
  el.textContent = (p.displayName?.[0] ?? 'C').toUpperCase();
  el.setAttribute('data-user-id', p.userId);
  el.setAttribute('aria-label', `crew ${p.displayName} at ${p.recordedAt}`);
  el.title = `${p.displayName} — ${new Date(p.recordedAt).toLocaleTimeString()}`;
  return el;
}
