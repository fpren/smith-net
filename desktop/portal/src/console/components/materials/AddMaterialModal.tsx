import { useState, useEffect, FormEvent } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { materialsClient } from '../../api/materialsClient';
import type { Material } from '../../api/materialsClient';
import { useMaterialsStore } from '../../stores/materialsStore';
import { useToast } from '../../hooks/useToast';

const UNIT_SUGGESTIONS = ['ea', 'ft', 'lot', 'hr', 'gal', 'bag', 'box'];

interface Props {
  open: boolean;
  jobId: string;
  editing: Material | null;
  onClose: () => void;
}

export function AddMaterialModal({ open, jobId, editing, onClose }: Props) {
  const upsert = useMaterialsStore((s) => s.upsert);
  const toast = useToast();
  const [name, setName] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [unit, setUnit] = useState('ea');
  const [unitCost, setUnitCost] = useState('0');
  const [vendor, setVendor] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (editing) {
      setName(editing.name);
      setQuantity(String(editing.quantity));
      setUnit(editing.unit);
      setUnitCost(String(editing.unitCost));
      setVendor(editing.vendor ?? '');
      setNotes(editing.notes ?? '');
    } else {
      setName(''); setQuantity('1'); setUnit('ea');
      setUnitCost('0'); setVendor(''); setNotes('');
    }
  }, [editing, open]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim() || busy) return;
    setBusy(true);
    const payload = {
      name: name.trim(),
      quantity: Number(quantity) || 0,
      unit: unit.trim() || 'ea',
      unitCost: Number(unitCost) || 0,
      vendor: vendor.trim() || undefined,
      notes: notes.trim() || undefined,
    };
    const r = editing
      ? await materialsClient.update(editing.id, payload)
      : await materialsClient.create({ jobId, ...payload });
    setBusy(false);
    if (r.ok) {
      upsert(jobId, r.material);
      toast.info(editing ? 'Material updated' : 'Material added');
      onClose();
    } else {
      toast.error(r.error || 'Failed to save material');
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={editing ? 'Edit material' : 'Add material'}>
      <form onSubmit={handleSubmit} className="w-full sm:w-[420px] max-w-full flex flex-col gap-2 font-mono text-sm">
        <input value={name} onChange={(e) => setName(e.target.value)}
          placeholder="Material name" autoFocus required
          className="bg-sn-bg-base border border-sn-line rounded px-2 py-1 text-sn-ink focus:border-sn-accent outline-none" />
        <div className="flex gap-2">
          <input value={quantity} onChange={(e) => setQuantity(e.target.value)}
            type="number" min="0" step="0.01" placeholder="qty"
            className="w-24 bg-sn-bg-base border border-sn-line rounded px-2 py-1 text-sn-ink focus:border-sn-accent outline-none" />
          <input value={unit} onChange={(e) => setUnit(e.target.value)}
            list="unit-suggestions" placeholder="unit"
            className="w-24 bg-sn-bg-base border border-sn-line rounded px-2 py-1 text-sn-ink focus:border-sn-accent outline-none" />
          <datalist id="unit-suggestions">
            {UNIT_SUGGESTIONS.map((u) => <option key={u} value={u} />)}
          </datalist>
          <input value={unitCost} onChange={(e) => setUnitCost(e.target.value)}
            type="number" min="0" step="0.01" placeholder="unit cost"
            className="flex-1 bg-sn-bg-base border border-sn-line rounded px-2 py-1 text-sn-ink focus:border-sn-accent outline-none" />
        </div>
        <input value={vendor} onChange={(e) => setVendor(e.target.value)}
          placeholder="Vendor (optional)"
          className="bg-sn-bg-base border border-sn-line rounded px-2 py-1 text-sn-ink focus:border-sn-accent outline-none" />
        <textarea value={notes} onChange={(e) => setNotes(e.target.value)}
          placeholder="Notes (optional)" rows={2}
          className="bg-sn-bg-base border border-sn-line rounded px-2 py-1 text-sn-ink focus:border-sn-accent outline-none" />
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={busy}>Save</Button>
        </div>
      </form>
    </Modal>
  );
}
