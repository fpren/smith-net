// desktop/portal/src/console/components/clients/ClientContactLines.tsx
import type { Client } from '../../api/clientsClient';

export function ClientContactLines({ client }: { client: Client }) {
  return (
    <dl className="text-sm grid grid-cols-[10ch_1fr] gap-y-1 font-mono">
      <dt className="text-console-text-muted">phone</dt>
      <dd>{client.phone ? <a className="text-console-accent" href={`tel:${client.phone}`}>{client.phone}</a> : '-'}</dd>
      <dt className="text-console-text-muted">email</dt>
      <dd>{client.email ? <a className="text-console-accent" href={`mailto:${client.email}`}>{client.email}</a> : '-'}</dd>
      <dt className="text-console-text-muted">address</dt>
      <dd>{client.address
        ? <a className="text-console-accent" href={`https://maps.google.com/?q=${encodeURIComponent(client.address)}`} target="_blank" rel="noreferrer">{client.address}</a>
        : '-'}</dd>
      <dt className="text-console-text-muted">company</dt>
      <dd>{client.company ?? '-'}</dd>
    </dl>
  );
}
