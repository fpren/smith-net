import { assertValidTransition, InvalidTransitionError, JobStatus } from '../jobsService';

describe('assertValidTransition', () => {
  const validTransitions: [JobStatus, JobStatus][] = [
    ['planned', 'in_progress'],
    ['planned', 'cancelled'],
    ['in_progress', 'complete'],
    ['in_progress', 'cancelled'],
  ];

  const invalidTransitions: [JobStatus, JobStatus][] = [
    ['planned', 'complete'],
    ['planned', 'planned'],
    ['in_progress', 'planned'],
    ['in_progress', 'in_progress'],
    ['complete', 'planned'],
    ['complete', 'in_progress'],
    ['complete', 'cancelled'],
    ['cancelled', 'planned'],
    ['cancelled', 'in_progress'],
    ['cancelled', 'complete'],
  ];

  it.each(validTransitions)('allows %s -> %s', (from, to) => {
    expect(() => assertValidTransition(from, to)).not.toThrow();
  });

  it.each(invalidTransitions)('rejects %s -> %s with InvalidTransitionError', (from, to) => {
    expect(() => assertValidTransition(from, to)).toThrow(InvalidTransitionError);
  });

  it('error carries from + to fields', () => {
    try {
      assertValidTransition('complete', 'planned');
      fail('should have thrown');
    } catch (e) {
      expect(e).toBeInstanceOf(InvalidTransitionError);
      expect((e as InvalidTransitionError).from).toBe('complete');
      expect((e as InvalidTransitionError).to).toBe('planned');
    }
  });
});
