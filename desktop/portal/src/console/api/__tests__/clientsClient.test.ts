import { describe, it, expect } from 'vitest';
import { clientsClient } from '../clientsClient';

describe('clientsClient', () => {
  it('list returns clients', async () => {
    const r = await clientsClient.list();
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.clients[0].name).toBe('Test Client');
  });
  it('create returns the new client on 201', async () => {
    const r = await clientsClient.create({ name: 'Brand new' });
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.client.id).toBe('new-client-id');
  });
});
