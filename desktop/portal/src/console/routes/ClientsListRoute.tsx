// desktop/portal/src/console/routes/ClientsListRoute.tsx
import { useState } from 'react';
import { Outlet, useMatch } from 'react-router-dom';
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
  // Independent path match (not useParams — the :id param belongs to the
  // nested child route, which isn't in this component's own route context).
  const idActive = Boolean(useMatch('/console/clients/:id'));
  const shown = query.trim()
    ? clients.filter((c) => {
        const q = query.trim().toLowerCase();
        return c.name.toLowerCase().includes(q) || (c.company ?? '').toLowerCase().includes(q);
      })
    : clients;

  // Precedence: loading -> error (no cached data to fall back on) -> empty -> data.
  let listContent: JSX.Element;
  if (isLoadingList && clients.length === 0) {
    listContent = <LoadingState label="Loading clients" />;
  } else if (listStale && clients.length === 0) {
    listContent = (
      <div className="flex flex-col items-center mt-24 gap-4">
        <ErrorState message="Couldn't load clients." onRetry={reload} />
      </div>
    );
  } else {
    listContent = (
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

  return (
    <div className="xl:grid xl:grid-cols-[minmax(0,1fr)_420px] xl:gap-6 xl:h-full">
      <div className={
        // The list column is an independent scroll region in BOTH states --
        // flipping scroll modes on selection would reset the list's scroll
        // position the moment you open the item you scrolled to find.
        idActive ? 'hidden xl:block xl:overflow-y-auto xl:min-h-0' : 'xl:overflow-y-auto xl:min-h-0'
      }>
        {listContent}
      </div>
      <div
        className={
          idActive
            ? 'block xl:overflow-y-auto xl:min-h-0 xl:border-l xl:border-sn-line xl:pl-6'
            : 'hidden xl:block xl:overflow-y-auto xl:min-h-0 xl:border-l xl:border-sn-line xl:pl-6'
        }
      >
        {idActive ? <Outlet /> : <EmptyState title="Select a client" />}
      </div>
    </div>
  );
}
