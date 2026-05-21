# SmithNet Core -- the "ROM"

A single deterministic C core compiled to ONE `smithcore.wasm` that every host
(Node backend, Android, iOS, Pi, browser) loads **unchanged**. The host provides
display / IO / time / keys; it never edits the ROM. Borrowed from the Game Boy
model: one ROM, runs on any compatible host, the host never changes the ROM.

## Why

The determinism moat (Intent -> SummaryArtifact -> Ledger, SHA256-sealed,
NFR-D1..D5) requires the SAME bytes out for the same bytes in on every device.
Logic that used to be hand-written twice -- `backend/src/vectorClock.ts` and
`android/.../data/VectorClock.kt` -- now has one implementation here, so the
cross-platform Ledger cannot drift.

## Invariants (do not break)

- No floating point anywhere (NaN/rounding diverge per host). Integers / fixed-point only.
- Explicit little-endian wire encoding for all multi-byte integers.
- Single-threaded, pure function of inputs. No time, no RNG, no UUID, no keys, no host imports (zero-import wasm).

## Layout

```
core/
  include/smithcore.h     ABI + canonical vector-clock wire format
  src/sha256.c            bundled SHA-256 (no host crypto dependency)
  src/vclock.c            merge / compare / canonicalize
  src/smithcore.c         exported ABI + bump arena + mem*
  build.sh                wasi-sdk clang -> dist/smithcore.wasm (+ .sha256 stamp)
  dist/smithcore.wasm     the ROM (committed; bytes are pinned)
```

## Build

Needs a wasm-capable clang (Apple clang has no wasm32 target). Use wasi-sdk:

```sh
# one-time: install a wasi-sdk and point WASI_SDK at it
export WASI_SDK=$HOME/.smithnet-toolchain/wasi-sdk-33.0-arm64-macos
cd core && ./build.sh
```

`build.sh` writes `dist/smithcore.wasm`, records its sha256 in
`dist/smithcore.wasm.sha256` (the ROM version stamp), and copies the SAME bytes
into each shell that bundles it (currently `android/app/src/main/assets/`).

## Hosts

| Host | Binding | Runtime |
|---|---|---|
| Node backend | `backend/src/core/smithCore.ts` | built-in `WebAssembly` |
| Android | `core/SmithCore.kt` + `cpp/smithcore_jni.cpp` | WAMR fast-interp (embedded) |
| iOS / Pi | (greenfield) | WAMR interpreter |
| Browser/portal | (M5) | built-in `WebAssembly` |

### Vendoring WAMR (Android/iOS native runtime)

The Android JNI bridge builds a **stub** until WAMR is vendored, so the project
still links and `SmithCore.kt` falls back to the legacy Kotlin vclock:

```sh
git clone --depth 1 https://github.com/bytecodealliance/wasm-micro-runtime \
  android/app/src/main/cpp/wamr
```

CMake then defines `SMITHCORE_WAMR`, builds WAMR in fast-interpreter mode
(iOS-parity, no JIT), and the real bridge activates.

## Verify

- Backend parity gate (the merge gate; runs in CI):
  `cd backend && npx jest smithcore-parity`
  Asserts ROM merge/compare == legacy TS over golden + 2000 fuzz pairs, ROM
  SHA-256 == Node crypto, and the loaded wasm hash == the recorded stamp.
- Android on-device parity (runs once WAMR is vendored; skips otherwise):
  `SmithCoreParityTest` (androidTest).

## Rollout

Enable per host once the ROM is loaded:
- Backend: `SMITHCORE_ENABLED=1` (server calls `initSmithCore()` at boot).
- Android: `SmithCore.initFromAssets(context)` at start, then `VectorClock.useSmithCore = true`.

Both are readiness-gated: if the ROM is missing/old, hosts degrade to the
legacy path (proven identical by the parity gate) instead of failing.

## Status

- M1 (this pilot): vector clock + SHA-256 through the ROM; backend wired + green.
- Next: M2 ledger/audit hash, M3 mesh + packed structs, M4 entitlements bitmask, M5 portal/iOS/Pi shells.
