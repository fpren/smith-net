/**
 * Phase 2 Slice 2: structured logging via pino. AsyncLocalStorage carries
 * per-request context so every log line emitted inside a request handler
 * automatically gets req_id / actor_id / route bindings.
 *
 * Use `requestLogger()` inside handler code. Use `baseLogger` for module-
 * level startup logs that have no request context.
 */

import pino, { Logger } from 'pino';
import { AsyncLocalStorage } from 'async_hooks';

export interface RequestContext {
  req_id: string;
  actor_id?: string;
  route?: string;
}

const als = new AsyncLocalStorage<RequestContext>();

export const baseLogger: Logger = pino({
  level: process.env.LOG_LEVEL ?? 'info',
  base: undefined, // omit pid/hostname noise
  timestamp: () => `,"time":"${new Date().toISOString()}"`,
});

export function requestLogger(): Logger {
  const ctx = als.getStore();
  return ctx ? baseLogger.child(ctx) : baseLogger;
}

export function withRequestContext<T>(ctx: RequestContext, fn: () => T): T {
  return als.run(ctx, fn);
}
