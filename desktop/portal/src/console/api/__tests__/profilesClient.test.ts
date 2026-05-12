import { describe, it, expect } from 'vitest';
import { profilesClient } from '../profilesClient';

describe('profilesClient', () => {
  it('search returns matching profiles', async () => {
    const result = await profilesClient.search('alice');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.profiles).toHaveLength(1);
      expect(result.profiles[0].email).toBe('alice@example.com');
    }
  });

  it('search returns 400 when query is too short', async () => {
    const result = await profilesClient.search('a');
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.status).toBe(400);
      expect(result.code).toBe('validation');
    }
  });
});
