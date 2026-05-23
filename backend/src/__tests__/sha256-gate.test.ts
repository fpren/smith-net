import * as crypto from 'crypto';
import { sha256HexGated } from '../sha256Gate';
import { initSmithCore } from '../core/smithCore';

describe('sha256HexGated', () => {
  afterEach(() => { delete process.env.SMITHCORE_ENABLED; });

  // Declared first: runs before initSmithCore() is ever called in this file, so
  // the ROM is genuinely not ready and the helper must degrade to node crypto.
  it('degrades to node crypto when the flag is ON but the ROM is not ready', () => {
    process.env.SMITHCORE_ENABLED = '1';
    const d = Buffer.from('not ready yet', 'utf8');
    expect(sha256HexGated(d)).toBe(crypto.createHash('sha256').update(d).digest('hex'));
  });

  it('equals node crypto with the flag OFF (legacy path)', () => {
    const d = Buffer.from('hello world', 'utf8');
    expect(sha256HexGated(d)).toBe(crypto.createHash('sha256').update(d).digest('hex'));
  });

  it('equals node crypto with the flag ON + ROM ready', async () => {
    await initSmithCore();
    process.env.SMITHCORE_ENABLED = '1';
    const d = crypto.randomBytes(100);
    expect(sha256HexGated(d)).toBe(crypto.createHash('sha256').update(d).digest('hex'));
  });
});
