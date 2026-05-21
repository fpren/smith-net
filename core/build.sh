#!/usr/bin/env bash
#
# build.sh -- compile the SmithNet C core to ONE smithcore.wasm (the ROM).
#
# Output is byte-identical regardless of which host will run it. We record the
# wasm's sha256 as the ROM version stamp; CI fails any shell that ships a
# smithcore.wasm whose hash differs from dist/smithcore.wasm.sha256 (the host
# must never change the ROM).
#
# Requires a wasm-capable clang + wasm-ld. Apple's clang does NOT have a wasm32
# target, so point WASI_SDK at a wasi-sdk install (or put one on PATH):
#   export WASI_SDK=$HOME/.smithnet-toolchain/wasi-sdk-33.0-arm64-macos
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"

# Resolve a wasm clang: explicit $WASI_SDK, a default cache, or PATH.
CLANG=""
if [[ -n "${WASI_SDK:-}" && -x "$WASI_SDK/bin/clang" ]]; then
  CLANG="$WASI_SDK/bin/clang"
else
  for d in "$HOME"/.smithnet-toolchain/wasi-sdk-*; do
    [[ -x "$d/bin/clang" ]] && CLANG="$d/bin/clang" && break
  done
fi
if [[ -z "$CLANG" ]] && command -v clang >/dev/null 2>&1 && clang --print-targets 2>/dev/null | grep -qi wasm32; then
  CLANG="clang"
fi
if [[ -z "$CLANG" ]]; then
  echo "ERROR: no wasm-capable clang found." >&2
  echo "Install wasi-sdk and set WASI_SDK, e.g.:" >&2
  echo "  https://github.com/WebAssembly/wasi-sdk/releases" >&2
  exit 1
fi

mkdir -p dist

# Freestanding wasm: no libc, no entry point, no host imports. -fno-builtin so
# clang does not assume a libc; we provide mem* ourselves.
"$CLANG" \
  --target=wasm32-unknown-unknown \
  -O2 \
  -nostdlib \
  -ffreestanding \
  -fno-builtin \
  -fvisibility=hidden \
  -Wall -Wextra -Werror \
  -I include \
  -Wl,--no-entry \
  -Wl,--export-dynamic \
  -Wl,--strip-all \
  src/sha256.c src/vclock.c src/smithcore.c \
  -o dist/smithcore.wasm

SHA="$(shasum -a 256 dist/smithcore.wasm | awk '{print $1}')"
echo "$SHA  smithcore.wasm" > dist/smithcore.wasm.sha256

SIZE="$(wc -c < dist/smithcore.wasm | tr -d ' ')"
echo "[+] built dist/smithcore.wasm (${SIZE} bytes)"
echo "[+] ROM sha256: ${SHA}"

# Propagate the SAME ROM bytes into every shell that bundles it. The host must
# never change the ROM, so we copy, never regenerate per target.
sync_rom() {
  local dest_dir="$1"
  if [[ -d "$(dirname "$dest_dir")" ]]; then
    mkdir -p "$dest_dir"
    cp dist/smithcore.wasm "$dest_dir/smithcore.wasm"
    echo "[+] synced ROM -> $dest_dir/smithcore.wasm"
  fi
}
sync_rom "../android/app/src/main/assets"
# Future shells (M5): portal public dir, iOS bundle, Pi host -- add here.
