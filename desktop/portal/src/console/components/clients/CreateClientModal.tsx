// desktop/portal/src/console/components/clients/CreateClientModal.tsx
import { useState, useEffect, FormEvent } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { useToast } from '../../hooks/useToast';
import { clientsClient } from '../../api/clientsClient';
import { useClientsStore } from '../../stores/clientsStore';
import type { Client } from '../../api/clientsClient';

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated?: (c: Client) => void;
  editing?: Client | null;
}

export function CreateClientModal({ open, onClose, onCreated, editing }: Props) {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [company, setCompany] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [nameError, setNameError] = useState<string | null>(null);
  const toast = useToast();

  useEffect(() => {
    if (open) {
      setName(editing?.name ?? '');
      setEmail(editing?.email ?? '');
      setPhone(editing?.phone ?? '');
      setAddress(editing?.address ?? '');
      setCompany(editing?.company ?? '');
      setNotes(editing?.notes ?? '');
      setNameError(null);
    }
  }, [open, editing]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setNameError(null);
    if (!name.trim()) {
      setNameError('Name is required');
      return;
    }
    setBusy(true);
    const payload = {
      name: name.trim(),
      ...(email.trim() ? { email: email.trim() } : {}),
      ...(phone.trim() ? { phone: phone.trim() } : {}),
      ...(address.trim() ? { address: address.trim() } : {}),
      company: company.trim() || undefined,
      notes: notes.trim() || undefined,
    };
    const r = editing
      ? await clientsClient.update(editing.id, payload)
      : await clientsClient.create(payload);
    setBusy(false);
    if (!r.ok) {
      toast.error(r.error || 'Failed to save client');
      return;
    }
    useClientsStore.getState().upsertClient(r.client);
    onCreated?.(r.client);
    onClose();
  }

  return (
    <Modal open={open} onClose={onClose} title={editing ? 'Edit Client' : 'Create Client'}>
      <form onSubmit={onSubmit} className="flex flex-col gap-3 w-full sm:w-[420px] max-w-full">
        <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} error={nameError ?? undefined} />
        <Input label="Email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Input label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} />
        <Input label="Address" value={address} onChange={(e) => setAddress(e.target.value)} />
        <Input label="Company" value={company} onChange={(e) => setCompany(e.target.value)} />
        <label className="flex flex-col gap-1 font-mono text-sm">
          <span className="text-sn-ink-muted text-xs uppercase tracking-wide">Notes</span>
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            className="bg-sn-bg-base border border-sn-line px-3 py-2 text-sn-ink focus:outline-none focus:border-sn-accent font-mono"
            rows={4}
          />
        </label>
        <div className="flex gap-2 justify-end mt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button type="submit" disabled={busy}>{editing ? 'Save' : 'Create'}</Button>
        </div>
      </form>
    </Modal>
  );
}
