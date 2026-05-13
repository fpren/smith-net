import { baseLogger, requestLogger, withRequestContext } from '../log';

describe('log.ts', () => {
  it('requestLogger returns base when no context active', () => {
    const log = requestLogger();
    expect(log).toBe(baseLogger);
  });

  it('withRequestContext attaches req_id, actor_id, route to child', () => {
    const child = withRequestContext(
      { req_id: 'r-2', actor_id: 'u-2', route: 'POST /y' },
      () => requestLogger()
    );
    const bindings = (child as any).bindings();
    expect(bindings.req_id).toBe('r-2');
    expect(bindings.actor_id).toBe('u-2');
    expect(bindings.route).toBe('POST /y');
  });

  it('child loggers nested inside the same context share bindings', () => {
    withRequestContext({ req_id: 'r-1', route: 'GET /x' }, () => {
      const a = requestLogger();
      const b = requestLogger();
      const aBind = (a as any).bindings();
      const bBind = (b as any).bindings();
      expect(aBind.req_id).toBe('r-1');
      expect(bBind.req_id).toBe('r-1');
    });
  });
});
