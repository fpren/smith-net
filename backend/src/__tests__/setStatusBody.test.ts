import { SetStatusBody } from '../schemas/invoices';

describe('SetStatusBody', () => {
  it('rejects sent (must go through the capped /send route)', () => {
    expect(SetStatusBody.safeParse({ status: 'sent' }).success).toBe(false);
  });
  it('accepts the user-settable statuses', () => {
    for (const status of ['draft', 'issued', 'viewed', 'paid', 'overdue', 'disputed', 'cancelled']) {
      expect(SetStatusBody.safeParse({ status }).success).toBe(true);
    }
  });
  it('rejects unknown status and extra keys', () => {
    expect(SetStatusBody.safeParse({ status: 'nope' }).success).toBe(false);
    expect(SetStatusBody.safeParse({ status: 'paid', foo: 1 }).success).toBe(false);
  });
});
