// @vitest-environment node
import { describe, it, expect, beforeAll } from 'vitest';
import { fileURLToPath } from 'node:url';
import { webcrypto } from 'node:crypto';
import * as fs from 'node:fs';
import { instantiate, isSmithCoreReady, sha256 } from './smithCore';

// Test-only filesystem + crypto (Vitest runs in Node, not the browser).
// Paths resolve from this file via import.meta.url so they do not depend on
// the process cwd. Describe blocks below test the wasm runtime by injecting
// romBytes() via instantiate(), never fetch().
const wasmUrl = new URL('../../../public/smithcore.wasm', import.meta.url);
const stampUrl = new URL('../../../../../core/dist/smithcore.wasm.sha256', import.meta.url);
const romBytes = () => new Uint8Array(fs.readFileSync(fileURLToPath(wasmUrl)));

const toHex = (b: Uint8Array) =>
  Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
const refSha256 = async (b: Uint8Array) =>
  toHex(new Uint8Array(await webcrypto.subtle.digest('SHA-256', b as unknown as NodeJS.BufferSource)));

beforeAll(async () => {
  await instantiate(romBytes());
});

describe('ROM identity', () => {
  it('portal wasm matches the recorded ROM hash stamp', async () => {
    const stamp = fs
      .readFileSync(fileURLToPath(stampUrl), 'utf8')
      .trim()
      .split(/\s+/)[0];
    expect(await refSha256(romBytes())).toBe(stamp);
  });
});

describe('host load + ABI', () => {
  it('instantiates and reports ready', () => {
    expect(isSmithCoreReady()).toBe(true);
  });
});

describe('sha256 parity vs WebCrypto', () => {
  const lengths = [0, 1, 55, 56, 57, 63, 64, 65, 127, 128, 1000];
  it.each(lengths)('matches the reference for a %d-byte buffer', async (len) => {
    const data = new Uint8Array(len);
    for (let i = 0; i < len; i++) data[i] = (i * 31 + 7) & 0xff;
    expect(toHex(sha256(data))).toBe(await refSha256(data));
  });
});
