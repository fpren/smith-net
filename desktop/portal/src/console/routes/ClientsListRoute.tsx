// desktop/portal/src/console/routes/ClientsListRoute.tsx
import { useState } from 'react';
import { Button } from '../components/ui/Button';
import { ClientCard } from '../components/clients/ClientCard';
import { CreateClientModal } from '../components/clients/CreateClientModal';
import { useClientsPolling } from '../hooks/useClientsPolling';
import { useClientsStore } from '../stores/clientsStore';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/StateViews';

export function ClientsListRoute() {
  const { reload } = useClientsPolling('list');
  const clients = useClientsStore((s) => s.clients);
  const isLoadingList = useClientsStore((s) => s.isLoadingList);
  const listStale = useClientsStore((s) => s.listStale);
  const [showCreate, setShowCreate] = useState(false);
  const [query, setQuery] = useState('');
  const shown = query.trim()
    ? clients.filter((c) => {
        const q = query.trim().toLowerCase();
        return c.name.toLowerCase().includes(q) || (c.company ?? '').toLowerCase().includes(q);
      })
    : clients;

  // Precedence: loading -> error (no cached data to fall back on) -> empty -> data.
  if (isLoadingList && clients.length === 0) {
    return <LoadingState label="Loading clients" />;
  }

  if (listStale && clients.length === 0) {
    return (
      <div className="flex flex-col items-center mt-24 gap-4">
        <ErrorState message="Couldn't load clients." onRetry={reload} />
      </div>
    );
  }

  return (
    <div className="font-mono">
      <div className="flex flex-col items-start gap-2 md:flex-row md:items-center md:justify-between mb-4">
        <h1 className="text-sn-ink text-lg">Clients</h1>
        <Button onClick={() => setShowCreate(true)}>+ Create client</Button>
      </div>
      <input
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search by name"
        className="w-full mb-3 bg-sn-bg-base border border-sn-line rounded px-2 py-1 text-sm font-mono text-sn-ink focus:border-sn-accent outline-none"
      />
      {listStale && (
        <ErrorState message="Couldn't refresh — showing cached data." onRetry={reload} />
      )}
      {shown.length === 0
        ? <EmptyState title="No clients." />
        : <div className="border border-sn-line">{shown.map((c) => <ClientCard key={c.id} client={c} />)}</div>}
      <CreateClientModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={() => setShowCreate(false)} />
    </div>
  );
}
