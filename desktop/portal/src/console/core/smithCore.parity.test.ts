// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { fileURLToPath } from 'node:url';
import { webcrypto } from 'node:crypto';
import * as fs from 'node:fs';

// Test-only filesystem + crypto (Vitest runs in Node). The module under test
// stays browser-pure: we read the wasm bytes here and inject them via
// instantiate(), never fetch(). Paths resolve from this file via import.meta.url
// so they do not depend on the process cwd.
const wasmUrl = new URL('../../../public/smithcore.wasm', import.meta.url);
const stampUrl = new URL('../../../../../core/dist/smithcore.wasm.sha256', import.meta.url);
const romBytes = () => new Uint8Array(fs.readFileSync(fileURLToPath(wasmUrl)));

const toHex = (b: Uint8Array) =>
  Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
const refSha256 = async (b: Uint8Array) =>
  toHex(new Uint8Array(await webcrypto.subtle.digest('SHA-256', b)));

describe('ROM identity', () => {
  it('portal wasm matches the recorded ROM hash stamp', async () => {
    const stamp = fs
      .readFileSync(fileURLToPath(stampUrl), 'utf8')
      .trim()
      .split(/\s+/)[0];
    expect(await refSha256(romBytes())).toBe(stamp);
  });
});
