// desktop/portal/src/console/components/clients/ClientCard.tsx
import { Link } from 'react-router-dom';
import type { Client } from '../../api/clientsClient';

export function ClientCard({ client }: { client: Client }) {
  return (
    <Link to={`/console/clients/${client.id}`}
      className="flex items-center justify-between px-3 py-2 border-b border-sn-line hover:bg-sn-bg-panel font-mono">
      <span className="text-sn-ink truncate">{client.name}</span>
      <span className="text-sn-accent text-xs shrink-0">[-&gt; open]</span>
    </Link>
  );
}
