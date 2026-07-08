import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TaskList } from '../TaskList';
import { useTasksStore } from '../../../stores/tasksStore';
import { tasksClient, type Task } from '../../../api/tasksClient';

function task(id: string, title: string): Task {
  return {
    id,
    jobId: 'j1',
    title,
    status: 'pending',
    sortOrder: 0,
    createdBy: null,
    createdAt: '2026-05-11T11:00:00Z',
    updatedAt: '2026-05-11T11:00:00Z',
    completedAt: null,
  };
}

describe('TaskList delete confirmation', () => {
  beforeEach(() => useTasksStore.getState().clear());

  it('task delete asks for confirmation first', async () => {
    useTasksStore.getState().setTasks('j1', [task('t1', 'Demo wall')]);
    const deleteSpy = vi.spyOn(tasksClient, 'delete');

    render(<TaskList jobId="j1" />);

    fireEvent.click(screen.getByRole('button', { name: 'Delete task' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(deleteSpy).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(deleteSpy).toHaveBeenCalledTimes(1);
    expect(deleteSpy).toHaveBeenCalledWith('t1');
  });

  it('cancel does not delete the task', () => {
    useTasksStore.getState().setTasks('j1', [task('t1', 'Demo wall')]);
    const deleteSpy = vi.spyOn(tasksClient, 'delete');

    render(<TaskList jobId="j1" />);

    fireEvent.click(screen.getByRole('button', { name: 'Delete task' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(deleteSpy).not.toHaveBeenCalled();
    expect(screen.getByText('Demo wall')).toBeInTheDocument();
  });
});
