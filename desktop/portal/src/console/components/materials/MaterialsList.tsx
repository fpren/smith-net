// desktop/portal/src/console/components/materials/MaterialsList.tsx
import { useEffect, useState } from 'react';
import { materialsClient } from '../../api/materialsClient';
import type { Material } from '../../api/materialsClient';
import { useMaterialsStore } from '../../stores/materialsStore';
import { useToast } from '../../hooks/useToast';
import { Button } from '../ui/Button';
import { ConfirmDialog } from '../ui/SmithDialog';
import { AddMaterialModal } from './AddMaterialModal';

const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const EMPTY: Material[] = [];

export function MaterialsList({ jobId }: { jobId: string }) {
  const items = useMaterialsStore((s) => s.byJob[jobId] ?? EMPTY);
  const toast = useToast();
  const [showAdd, setShowAdd] = useState(false);
  const [editing, setEditing] = useState<Material | null>(null);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);

  useEffect(() => {
    materialsClient.listForJob(jobId).then((r) => {
      if (r.ok) useMaterialsStore.getState().setForJob(jobId, r.materials);
    });
  }, [jobId]);

  const upsert = (item: Material) => useMaterialsStore.getState().upsert(jobId, item);
  const remove = (id: string) => useMaterialsStore.getState().remove(jobId, id);

  async function toggle(m: Material) {
    const prev = m.checked;
    upsert({ ...m, checked: !prev });
    const r = await materialsClient.update(m.id, { checked: !prev });
    if (r.ok) {
      upsert(r.material);
    } else {
      upsert(m); // rollback
      toast.error(r.error || 'Failed to update');
    }
  }

  async function doDelete(id: string) {
    const r = await materialsClient.delete(id);
    if (r.ok) { remove(id); toast.info('Material deleted'); }
    else toast.error(r.error || 'Failed to delete');
  }

  const subtotal = items.reduce((s, m) => s + m.quantity * m.unitCost, 0);

  return (
    <section className="font-mono mb-4">
      <header className="flex items-center justify-between mb-2">
        <h2 className="text-console-text text-sm uppercase tracking-wider">Materials ({items.filter((m) => m.checked).length}/{items.length})</h2>
        <Button variant="secondary" onClick={() => { setEditing(null); setShowAdd(true); }}>+ Add material</Button>
      </header>
      {items.length === 0 ? (
        <div className="text-console-text-muted text-sm py-2">No materials yet.</div>
      ) : (
        <div className="border border-console-border">
          {items.map((m) => (
            <div key={m.id} className="flex items-center gap-2 px-3 py-2 border-b border-console-border last:border-b-0 text-sm">
              <input
                type="checkbox"
                checked={m.checked}
                onChange={() => toggle(m)}
                className="cursor-pointer"
                aria-label={`Toggle ${m.name}`}
              />
              <div className="flex-1 min-w-0">
                <div className={m.checked ? 'line-through text-console-text-muted' : 'text-console-text'}>{m.name}</div>
                <div className="text-xs text-console-text-muted">
                  {m.quantity} {m.unit} @ {USD.format(m.unitCost)}
                  {m.vendor ? ` - ${m.vendor}` : ''}
                </div>
              </div>
              <div className="text-console-text tabular-nums">{USD.format(m.quantity * m.unitCost)}</div>
              <button onClick={() => { setEditing(m); setShowAdd(true); }} className="text-xs text-console-text-muted hover:text-console-text">[edit]</button>
              <button onClick={() => setConfirmingId(m.id)} aria-label="Delete material" className="text-xs text-console-text-muted hover:text-console-warn">[delete]</button>
            </div>
          ))}
          <div className="px-3 py-2 text-right text-console-text font-bold border-t-2 border-console-border">
            Materials: {USD.format(subtotal)}
          </div>
        </div>
      )}
      <AddMaterialModal
        open={showAdd}
        onClose={() => setShowAdd(false)}
        jobId={jobId}
        editing={editing}
      />
      <ConfirmDialog
        open={confirmingId !== null}
        title="Delete material?"
        confirmLabel="Delete"
        body="It's removed from this job's materials list."
        onConfirm={() => {
          const id = confirmingId;
          setConfirmingId(null);
          if (id) void doDelete(id);
        }}
        onCancel={() => setConfirmingId(null)}
      />
    </section>
  );
}
