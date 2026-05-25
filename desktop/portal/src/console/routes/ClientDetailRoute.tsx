// desktop/portal/src/console/routes/ClientDetailRoute.tsx
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { ClientContactLines } from '../components/clients/ClientContactLines';
import { CreateClientModal } from '../components/clients/CreateClientModal';
import { useClientsPolling } from '../hooks/useClientsPolling';
import { useClientsStore } from '../stores/clientsStore';
import { clientsClient } from '../api/clientsClient';
import { useToast } from '../hooks/useToast';

export function ClientDetailRoute() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  useClientsPolling({ detail: id ?? '' });
  const client = useClientsStore((s) => s.detailClient);
  const jobs = useClientsStore((s) => s.detailJobs);
  const [showEdit, setShowEdit] = useState(false);

  async function onDelete() {
    if (!client) return;
    if (!window.confirm('Delete this client?')) return;
    const r = await clientsClient.remove(client.id);
    if (!r.ok) {
      toast.error((r as any).error || 'Failed to delete client');
      return;
    }
    useClientsStore.getState().removeClient(client.id);
    navigate('/console/clients');
  }

  if (!client || client.id !== id) {
    return <div className="text-console-text-muted">Loading...</div>;
  }

  return (
    <div className="font-mono">
      <Link to="/console/clients" className="text-console-accent text-sm">back to clients</Link>
      <div className="flex items-center justify-between mt-2 mb-4">
        <h1 className="text-console-text text-lg">{client.name}</h1>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={onDelete}>Delete</Button>
          <Button onClick={() => setShowEdit(true)}>Edit</Button>
        </div>
      </div>
      <ClientContactLines client={client} />
      <div className="mt-6">
        <div className="text-xs uppercase tracking-wide text-console-text-muted mb-2">Jobs ({jobs.length})</div>
        {jobs.length === 0
          ? <div className="text-console-text-muted text-sm">No jobs for this client.</div>
          : <div className="border border-console-border">
              {jobs.map((j: any) => (
                <Link key={j.id} to={`/console/jobs/${j.id}`}
                  className="flex items-center justify-between px-3 py-2 border-b border-console-border hover:bg-console-surface">
                  <span className="truncate">{j.title}</span>
                  <span className="text-console-text-muted text-xs shrink-0">{j.status}</span>
                </Link>
              ))}
            </div>}
      </div>
      <CreateClientModal open={showEdit} onClose={() => setShowEdit(false)} editing={client} />
    </div>
  );
}
