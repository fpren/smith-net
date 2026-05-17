import { describe, it, expect, beforeEach } from 'vitest';
import { useTasksStore } from '../tasksStore';
import type { Task } from '../../api/tasksClient';

function task(id: string, jobId: string, sortOrder = 0, status: Task['status'] = 'pending'): Task {
  return {
    id,
    jobId,
    title: id,
    status,
    sortOrder,
    createdBy: 'u1',
    createdAt: '2026-05-11T10:00:00Z',
    updatedAt: '2026-05-11T10:00:00Z',
    completedAt: null,
  };
}

describe('tasksStore', () => {
  beforeEach(() => {
    useTasksStore.getState().clear();
  });

  it('setTasks replaces the array for the given jobId', () => {
    const s = useTasksStore.getState();
    s.setTasks('j-1', [task('t1', 'j-1'), task('t2', 'j-1', 1)]);
    expect(useTasksStore.getState().tasksByJob['j-1']).toHaveLength(2);
  });

  it('addTask appends and dedupes by id; keeps sort order', () => {
    const s = useTasksStore.getState();
    s.addTask(task('t1', 'j-1', 0));
    s.addTask(task('t2', 'j-1', 1));
    s.addTask(task('t1', 'j-1', 0)); // duplicate — dropped
    expect(useTasksStore.getState().tasksByJob['j-1'].map((t) => t.id)).toEqual(['t1', 't2']);
  });

  it('updateTask replaces the in-place entry', () => {
    const s = useTasksStore.getState();
    s.setTasks('j-1', [task('t1', 'j-1')]);
    s.updateTask({ ...task('t1', 'j-1'), status: 'done', completedAt: '2026-05-11T11:00:00Z' });
    const updated = useTasksStore.getState().tasksByJob['j-1'][0];
    expect(updated.status).toBe('done');
    expect(updated.completedAt).toBeTruthy();
  });

  it('removeTask drops the task across all job buckets', () => {
    const s = useTasksStore.getState();
    s.setTasks('j-1', [task('t1', 'j-1'), task('t2', 'j-1', 1)]);
    s.removeTask('t1');
    expect(useTasksStore.getState().tasksByJob['j-1'].map((t) => t.id)).toEqual(['t2']);
  });
});
