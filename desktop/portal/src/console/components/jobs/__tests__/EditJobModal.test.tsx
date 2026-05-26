import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { EditJobModal } from '../EditJobModal';
import { useJobsStore } from '../../../stores/jobsStore';
import { useClientsStore } from '../../../stores/clientsStore';
import type { Job } from '../../../api/jobsClient';

const job: Job = {
  id: 'job-1', foremanId: 'user-1', clientId: null, client: null, engagementId: null,
  title: 'Old title', description: null, status: 'planned', stage: 'lead',
  scheduledAt: null, location: 'Old location',
  latitude: null, longitude: null, geocodedAt: null,
  createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
};

describe('EditJobModal', () => {
  beforeEach(() => {
    useJobsStore.getState().clear();
    useClientsStore.getState().clear();
  });

  it('pre-fills the title and saves an edit (PATCH) into the store', async () => {
    const onClose = vi.fn();
    render(<EditJobModal open onClose={onClose} job={job} />);

    const titleInput = screen.getByLabelText(/title/i) as HTMLInputElement;
    expect(titleInput.value).toBe('Old title');

    fireEvent.change(titleInput, { target: { value: 'Renamed job' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    // The MSW PATCH handler echoes the title back; the modal upserts the result.
    const stored = useJobsStore.getState().jobs.find((j) => j.id === 'job-1');
    expect(stored?.title).toBe('Renamed job');
  });
});
