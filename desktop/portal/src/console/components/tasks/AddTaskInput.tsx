// desktop/portal/src/console/components/tasks/AddTaskInput.tsx
import { useState, KeyboardEvent } from 'react';
import { tasksClient } from '../../api/tasksClient';
import { useTasksStore } from '../../stores/tasksStore';
import { useToastStore } from '../../stores/toastStore';

interface Props {
  jobId: string;
}

export function AddTaskInput({ jobId }: Props) {
  const [text, setText] = useState('');
  const [creating, setCreating] = useState(false);
  const addTask = useTasksStore((s) => s.addTask);
  const pushToast = useToastStore((s) => s.push);

  async function submit() {
    const trimmed = text.trim();
    if (!trimmed || creating) return;
    setCreating(true);
    const r = await tasksClient.create(jobId, trimmed);
    setCreating(false);
    if (r.ok) {
      addTask(r.task);
      setText('');
    } else {
      pushToast({ message: r.error || 'Failed to add task', tone: 'error', duration: 3000 });
    }
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      submit();
    }
  }

  return (
    <div className="mt-2 flex items-center gap-2 font-mono">
      <input
        type="text"
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={onKeyDown}
        placeholder="Add a task…"
        disabled={creating}
        className="flex-1 bg-transparent border border-console-border px-2 py-1 text-sm text-console-text placeholder-console-text-muted focus:outline-none focus:border-console-accent"
      />
      <button
        type="button"
        onClick={submit}
        disabled={creating || text.trim().length === 0}
        className="px-3 py-1 text-xs uppercase tracking-wide text-console-accent border border-console-accent disabled:opacity-40 disabled:cursor-not-allowed hover:bg-console-accent hover:text-console-bg transition-colors"
      >
        {creating ? '[Adding…]' : '[+ Add]'}
      </button>
    </div>
  );
}
