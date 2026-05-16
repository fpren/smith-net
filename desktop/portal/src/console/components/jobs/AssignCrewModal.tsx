import { FormEvent, useEffect, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { Chip } from '../ui/Chip';
import { profilesClient, ProfileMatch } from '../../api/profilesClient';
import { jobsClient, CrewAssignment } from '../../api/jobsClient';
import { useToast } from '../../hooks/useToast';
import { colorForRole } from '../../lib/utils';

interface Props {
  open: boolean;
  jobId: string;
  alreadyAssigned: string[];      // profile ids already assigned to this job
  onClose: () => void;
  onAssigned: (assignment: CrewAssignment) => void;
}

const DEBOUNCE_MS = 300;

export function AssignCrewModal({ open, jobId, alreadyAssigned, onClose, onAssigned }: Props) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<ProfileMatch[]>([]);
  const [selected, setSelected] = useState<ProfileMatch | null>(null);
  const [role, setRole] = useState<'crew' | 'lead'>('crew');
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  // Debounced search
  useEffect(() => {
    if (query.trim().length < 2) {
      setResults([]);
      setSearchError(null);
      return;
    }
    const id = setTimeout(async () => {
      setSearching(true);
      setSearchError(null);
      const result = await profilesClient.search(query.trim());
      setSearching(false);
      if (result.ok) {
        setResults(result.profiles);
      } else {
        setSearchError(result.error);
        setResults([]);
      }
    }, DEBOUNCE_MS);
    return () => clearTimeout(id);
  }, [query]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!selected) return;
    setSubmitError(null);
    setBusy(true);
    const result = await jobsClient.assignCrew(jobId, selected.id, role);
    setBusy(false);
    if (!result.ok) {
      if (result.code === 'duplicate_assignment') {
        setSubmitError('Already assigned to this job.');
      } else {
        toast.error(result.error || 'Failed to assign crew');
      }
      return;
    }
    onAssigned(result.assignment);
    setQuery(''); setResults([]); setSelected(null); setRole('crew');
    onClose();
  }

  return (
    <Modal open={open} onClose={onClose} title="Assign Crew">
      <form onSubmit={onSubmit} className="flex flex-col gap-3 min-w-[420px]">
        <Input
          label="Search by name or email"
          value={query}
          onChange={(e) => { setQuery(e.target.value); setSelected(null); }}
          placeholder="2+ chars"
        />
        {query.trim().length < 2 && <div className="text-console-text-muted text-xs">Type to search profiles.</div>}
        {searching && <div className="text-console-text-muted text-xs">[searching...]</div>}
        {searchError && <div className="text-console-danger text-xs">{searchError}</div>}
        <div className="flex flex-col">
          {results.map((p) => {
            const assigned = alreadyAssigned.includes(p.id);
            return (
              <button
                type="button"
                key={p.id}
                disabled={assigned}
                onClick={() => setSelected(p)}
                className={`flex items-center justify-between px-3 py-2 text-sm font-mono border-b border-console-border text-left ${
                  assigned ? 'opacity-50 cursor-not-allowed' : 'hover:bg-console-bg'
                } ${selected?.id === p.id ? 'bg-console-bg' : ''}`}
              >
                <span className="flex-1">
                  <span className="text-console-text">{p.displayName}</span>{' '}
                  <span className="text-console-text-muted">{p.email}</span>
                </span>
                <Chip label={p.role.toUpperCase()} color={colorForRole(p.role)} xs />
                {assigned && <span className="ml-2 text-console-text-muted text-xs">(already assigned)</span>}
              </button>
            );
          })}
        </div>
        {selected && (
          <>
            <label className="flex flex-col gap-1 font-mono text-sm">
              <span className="text-console-text-muted text-xs uppercase tracking-wide">Role</span>
              <select
                value={role}
                onChange={(e) => setRole(e.target.value as 'crew' | 'lead')}
                className="bg-console-bg border border-console-border px-3 py-2 text-console-text focus:outline-none focus:border-console-accent font-mono"
              >
                <option value="crew">crew</option>
                <option value="lead">lead</option>
              </select>
            </label>
            {submitError && <div className="text-console-danger text-xs">{submitError}</div>}
            <div className="flex gap-2 justify-end">
              <Button variant="secondary" type="button" onClick={onClose} disabled={busy}>Cancel</Button>
              <Button type="submit" disabled={busy}>{busy ? 'Assigning...' : 'Assign'}</Button>
            </div>
          </>
        )}
      </form>
    </Modal>
  );
}
