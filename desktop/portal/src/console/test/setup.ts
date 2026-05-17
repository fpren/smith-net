import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './msw-server';

// jsdom doesn't implement URL.createObjectURL — stub it so maplibre-gl's
// module-level worker setup doesn't crash the test environment.
if (typeof window !== 'undefined' && !window.URL.createObjectURL) {
  window.URL.createObjectURL = () => '';
}

// jsdom + msw don't speak ws://, so wsClient.connect() against
// ws://localhost:3030 would trip msw's "error on unhandled request" guard.
// None of the route/component tests need a live WS — they seed the store
// directly — so install a no-op WebSocket that never resolves or errors.
class NoopWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;
  readyState = 0;
  onopen?: () => void;
  onmessage?: (ev: { data: string }) => void;
  onclose?: () => void;
  onerror?: (e: unknown) => void;
  constructor(public url: string) {}
  send(_: string) {}
  close() {
    this.readyState = 3;
  }
}
Object.defineProperty(globalThis, 'WebSocket', {
  value: NoopWebSocket,
  writable: true,
  configurable: true,
});

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
