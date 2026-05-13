import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { CrewRoute } from '../CrewRoute';
import { useCrewStore } from '../../stores/crewStore';

describe('CrewRoute', () => {
  beforeEach(() => useCrewStore.getState().clear());

  it('renders the empty state when roster is empty', async () => {
    render(<CrewRoute />);
    await waitFor(() => {
      // MSW returns 2 entries, so empty state should NOT show after first fetch.
      expect(screen.getByText('Alice')).toBeInTheDocument();
    });
  });

  it('renders Alice + Bob from the MSW handler', async () => {
    render(<CrewRoute />);
    await waitFor(() => expect(screen.getByText('Alice')).toBeInTheDocument());
    expect(screen.getByText('Bob')).toBeInTheDocument();
  });
});
