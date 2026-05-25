// desktop/portal/src/console/components/clients/ClientCard.tsx
import { Link } from 'react-router-dom';
import type { Client } from '../../api/clientsClient';

export function ClientCard({ client }: { client: Client }) {
  return (
    <Link to={`/console/clients/${client.id}`}
      className="flex items-center justify-between px-3 py-2 border-b border-console-border hover:bg-console-surface font-mono">
      <span className="text-console-text truncate">{client.name}</span>
      <span className="text-console-accent text-xs shrink-0">[-&gt; open]</span>
    </Link>
  );
}
