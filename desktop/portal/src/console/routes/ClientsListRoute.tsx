// desktop/portal/src/console/routes/ClientsListRoute.tsx
import { useState } from 'react';
import { Button } from '../components/ui/Button';
import { ClientCard } from '../components/clients/ClientCard';
import { CreateClientModal } from '../components/clients/CreateClientModal';
import { useClientsPolling } from '../hooks/useClientsPolling';
import { useClientsStore } from '../stores/clientsStore';

export function ClientsListRoute() {
  useClientsPolling('list');
  const clients = useClientsStore((s) => s.clients);
  const isStale = useClientsStore((s) => s.isStale);
  const [showCreate, setShowCreate] = useState(false);
  const [query, setQuery] = useState('');
  const shown = query.trim()
    ? clients.filter((c) => c.name.toLowerCase().includes(query.trim().toLowerCase()))
    : clients;

  return (
    <div className="font-mono">
      <div className="flex flex-col items-start gap-2 md:flex-row md:items-center md:justify-between mb-4">
        <h1 className="text-console-text text-lg">Clients</h1>
        <Button onClick={() => setShowCreate(true)}>+ Create client</Button>
      </div>
      <input
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search by name"
        className="w-full mb-3 bg-console-bg border border-console-border rounded px-2 py-1 text-sm font-mono text-console-text focus:border-console-accent outline-none"
      />
      {isStale && (
        <div className="bg-console-surface border border-console-warn text-console-warn px-3 py-1 text-xs mb-3">
          [OFFLINE] Couldn't refresh - showing cached data
        </div>
      )}
      {shown.length === 0
        ? <div className="text-console-text-muted text-sm">No clients.</div>
        : <div className="border border-console-border">{shown.map((c) => <ClientCard key={c.id} client={c} />)}</div>}
      <CreateClientModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={() => setShowCreate(false)} />
    </div>
  );
}
