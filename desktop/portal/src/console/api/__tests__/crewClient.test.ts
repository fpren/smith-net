// desktop/portal/src/console/api/__tests__/crewClient.test.ts
import { describe, it, expect } from 'vitest';
import { crewClient } from '../crewClient';

describe('crewClient', () => {
  it('getRoster returns crew array', async () => {
    const result = await crewClient.getRoster();
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.crew).toHaveLength(2);
      expect(result.crew[0].displayName).toBe('Alice');
      expect(result.crew[0].activeJob).not.toBeNull();
      expect(result.crew[1].activeJob).toBeNull();
    }
  });
});
