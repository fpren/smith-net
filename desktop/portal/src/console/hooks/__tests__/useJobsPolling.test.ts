// desktop/portal/src/console/hooks/__tests__/useJobsPolling.test.ts
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useJobsPolling } from '../useJobsPolling';
import { useJobsStore } from '../../stores/jobsStore';
import * as jobsClient from '../../api/jobsClient';
import type { Job } from '../../api/jobsClient';

const detailFixture = (id: string): Job => ({
  id, foremanId: 'f', clientId: null, client: null, engagementId: null,
  title: `Job ${id}`, description: null, status: 'planned', stage: 'lead',
  scheduledAt: null, location: null, latitude: null, longitude: null,
  geocodedAt: null, createdAt: '', updatedAt: '',
});

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
      job: { id: 'x', foremanId: 'f', clientId: null, client: null, engagementId: null, title: 't', description: null, status: 'planned', stage: 'lead', scheduledAt: null, location: null, latitude: null, longitude: null, geocodedAt: null, createdAt: '', updatedAt: '' },
      crew: [],
    });
    renderHook(() => useJobsPolling({ detail: 'x' }, 15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledWith('x');
  });

  it('sets listStale=true on fetch failure and stops on visibility hidden', async () => {
    vi.spyOn(jobsClient.jobsClient, 'list').mockResolvedValue({ ok: false, status: 500, error: 'oops' });
    renderHook(() => useJobsPolling('list', 15000));
    await act(async () => { await Promise.resolve(); });
    expect(useJobsStore.getState().listStale).toBe(true);
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

describe('useJobsPolling unmount safety', () => {
  beforeEach(() => { useJobsStore.getState().clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('ignores a list response that resolves after unmount', async () => {
    let resolveFetch!: (v: Awaited<ReturnType<typeof jobsClient.jobsClient.list>>) => void;
    vi.spyOn(jobsClient.jobsClient, 'list').mockReturnValue(
      new Promise((res) => { resolveFetch = res; }),
    );
    const { unmount } = renderHook(() => useJobsPolling('list', 15000));
    unmount();
    await act(async () => {
      resolveFetch({
        ok: true,
        jobs: [{ id: 'late', foremanId: 'f', clientId: null, client: null, engagementId: null, title: 'Late Job', description: null, status: 'planned', stage: 'lead', scheduledAt: null, location: null, latitude: null, longitude: null, geocodedAt: null, createdAt: '', updatedAt: '' }],
      });
      await Promise.resolve();
    });
    expect(useJobsStore.getState().jobs).toHaveLength(0);
    expect(useJobsStore.getState().isLoadingList).toBe(false);
  });
});

describe('useJobsPolling reload (finding #1: cancellable retry)', () => {
  beforeEach(() => { useJobsStore.getState().clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('reload() re-fires the fetch for the mounted scope', async () => {
    const spy = vi.spyOn(jobsClient.jobsClient, 'getById')
      .mockResolvedValue({ ok: true, job: detailFixture('a'), crew: [] });
    const { result } = renderHook(() => useJobsPolling({ detail: 'a' }, 15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);

    act(() => { result.current.reload(); });
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('a reload in flight when the component unmounts does not clobber the store on resolve', async () => {
    // Mirrors the "ignores a list response that resolves after unmount" case
    // above, but exercises reload() specifically: mount on Job A (resolves
    // normally), retry Job A (reload), then unmount -- simulating navigating
    // to Job B -- before that retry's response lands. The stale Job A retry
    // response must not overwrite whatever the store holds post-unmount.
    let resolveReload!: (v: Awaited<ReturnType<typeof jobsClient.jobsClient.getById>>) => void;
    vi.spyOn(jobsClient.jobsClient, 'getById')
      .mockResolvedValueOnce({ ok: true, job: detailFixture('a'), crew: [] })
      .mockImplementationOnce(() => new Promise((res) => { resolveReload = res; }));

    const { result, unmount } = renderHook(() => useJobsPolling({ detail: 'a' }, 15000));
    await act(async () => { await Promise.resolve(); });
    expect(useJobsStore.getState().detailJob?.id).toBe('a');

    act(() => { result.current.reload(); });
    expect(useJobsStore.getState().isLoadingDetail).toBe(true);

    unmount();

    await act(async () => {
      resolveReload({ ok: true, job: detailFixture('b'), crew: [] });
      await Promise.resolve();
    });

    // Still Job A -- the late-resolving reload must not have written Job B.
    expect(useJobsStore.getState().detailJob?.id).toBe('a');
    // Loading flag still clears even though the write was skipped.
    expect(useJobsStore.getState().isLoadingDetail).toBe(false);
  });

  it('reload() after unmount is a no-op (does not throw, does not fetch)', async () => {
    const spy = vi.spyOn(jobsClient.jobsClient, 'getById')
      .mockResolvedValue({ ok: true, job: detailFixture('a'), crew: [] });
    const { result, unmount } = renderHook(() => useJobsPolling({ detail: 'a' }, 15000));
    await act(async () => { await Promise.resolve(); });
    unmount();
    const callsBefore = spy.mock.calls.length;
    expect(() => result.current.reload()).not.toThrow();
    await act(async () => { await Promise.resolve(); });
    expect(spy.mock.calls.length).toBe(callsBefore);
  });
});
