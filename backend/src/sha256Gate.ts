import * as crypto from 'crypto';
import { isSmithCoreReady, sha256 as romSha256 } from './core/smithCore';

/**
 * SHA-256 -> lowercase hex. Routes through the SmithCore ROM when
 * SMITHCORE_ENABLED=1 and the ROM is loaded; otherwise node crypto. Both are
 * byte-identical (proven by the M1 parity gate), so the output never depends on
 * the path -- the flag is a pure rollout lever, not a behavior change.
 */
export function sha256HexGated(data: Buffer): string {
  if (process.env.SMITHCORE_ENABLED === '1' && isSmithCoreReady()) {
    return romSha256(data).toString('hex');
  }
  return crypto.createHash('sha256').update(data).digest('hex');
}
