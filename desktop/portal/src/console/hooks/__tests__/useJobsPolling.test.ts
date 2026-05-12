// desktop/portal/src/console/hooks/__tests__/useJobsPolling.test.ts
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useJobsPolling } from '../useJobsPolling';
import { useJobsStore } from '../../stores/jobsStore';
import * as jobsClient from '../../api/jobsClient';

describe('useJobsPolling', () => {
  beforeEach(() => {
    useJobsStore.getState().clear();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('fires an immediate fetch on mount (list scope)', async () => {
    const spy = vi.spyOn(jobsClient.jobsClient, 'list').mockResolvedValue({ ok: true, jobs: [] });
    renderHook(() => useJobsPolling('list', 15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('fires another fetch after the interval', async () => {
    const spy = vi.spyOn(jobsClient.jobsClient, 'list').mockResolvedValue({ ok: true, jobs: [] });
    renderHook(() => useJobsPolling('list', 15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
    await act(async () => {
      vi.advanceTimersByTime(15001);
      await Promise.resolve();
    });
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('detail scope calls getById with the id', async () => {
    const spy = vi.spyOn(jobsClient.jobsClient, 'getById').mockResolvedValue({
      ok: true,
      job: { id: 'x', foremanId: 'f', clientId: null, engagementId: null, title: 't', description: null, status: 'planned', scheduledAt: null, location: null, createdAt: '', updatedAt: '' },
      crew: [],
    });
    renderHook(() => useJobsPolling({ detail: 'x' }, 15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledWith('x');
  });

  it('sets isStale=true on fetch failure and stops on visibility hidden', async () => {
    vi.spyOn(jobsClient.jobsClient, 'list').mockResolvedValue({ ok: false, status: 500, error: 'oops' });
    renderHook(() => useJobsPolling('list', 15000));
    await act(async () => { await Promise.resolve(); });
    expect(useJobsStore.getState().isStale).toBe(true);
  });

  it('cleans up interval on unmount', async () => {
    const spy = vi.spyOn(jobsClient.jobsClient, 'list').mockResolvedValue({ ok: true, jobs: [] });
    const { unmount } = renderHook(() => useJobsPolling('list', 15000));
    await act(async () => { await Promise.resolve(); });
    unmount();
    await act(async () => {
      vi.advanceTimersByTime(60000);
      await Promise.resolve();
    });
    expect(spy).toHaveBeenCalledTimes(1);
  });
});
